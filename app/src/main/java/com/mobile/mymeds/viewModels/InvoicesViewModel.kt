package com.mobile.mymeds.viewModels

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.data.local.prefs.InvoicesPreferencesManager
import com.mobile.mymeds.models.*
import com.mobile.mymeds.repository.InvoicesRepository
import com.mobile.mymeds.repository.OrdersRepository
import com.mobile.mymeds.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlinx.coroutines.tasks.await

private const val TAG = "InvoicesViewModel"

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICES VIEWMODEL
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ViewModel principal para la gestión de facturas.
 * Maneja generación de PDFs, sincronización con Firebase y cache local.
 *
 * MODIFICACIÓN: Permite generar facturas para pedidos en CUALQUIER estado
 */

@RequiresApi(Build.VERSION_CODES.O)
class InvoicesViewModel(
    private val context: Context
) : ViewModel() {

    // Repositorios y managers
    private val invoicesRepository = InvoicesRepository()
    private val ordersRepository = OrdersRepository()
    private val prefsManager = InvoicesPreferencesManager(context)
    private val pdfGenerator = PdfGenerator(context)
    private val invoiceNumberGenerator = InvoiceNumberGenerator(context)

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Estados de UI
    private val _userOrders = MutableStateFlow<List<MedicationOrder>>(emptyList())
    val userOrders: StateFlow<List<MedicationOrder>> = _userOrders.asStateFlow()

    private val _generatedInvoices = MutableStateFlow<List<Invoice>>(emptyList())
    val generatedInvoices: StateFlow<List<Invoice>> = _generatedInvoices.asStateFlow()

    private val _selectedOrders = MutableStateFlow<Set<String>>(emptySet())
    val selectedOrders: StateFlow<Set<String>> = _selectedOrders.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _pendingInvoicesCount = MutableStateFlow(0)
    val pendingInvoicesCount: StateFlow<Int> = _pendingInvoicesCount.asStateFlow()

    private val _generationProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val generationProgress: StateFlow<Map<String, Float>> = _generationProgress.asStateFlow()

    private val _uiState = MutableStateFlow<InvoiceUiState>(InvoiceUiState.Loading)
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "🎯 InvoicesViewModel inicializado")

        // Observar conectividad
        viewModelScope.launch {
            context.observeNetworkConnectivity().collect { connected ->
                _isConnected.value = connected
                Log.d(TAG, "🌐 Conectividad: $connected")

                if (connected) {
                    // Intentar sincronizar automáticamente
                    syncPendingInvoices()
                }
            }
        }

        // Cargar datos iniciales
        loadUserOrders()
        loadGeneratedInvoices()
        updatePendingInvoicesCount()

        // Limpieza automática si es necesario
        if (prefsManager.needsCleanup()) {
            viewModelScope.launch {
                prefsManager.cleanupOldInvoices()
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * CARGAR DATOS
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Carga TODOS los pedidos del usuario sin filtrar por estado
     * MODIFICACIÓN: Ahora permite pedidos en cualquier estado
     */
    fun loadUserOrders() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                _uiState.value = InvoiceUiState.Loading

                val result = ordersRepository.getUserOrders(userId)
                if (result.isSuccess) {
                    val orders = result.getOrNull().orEmpty()

                    // ✅ CAMBIO PRINCIPAL: No filtrar por estado
                    // Permitir TODOS los pedidos para generar facturas
                    _userOrders.value = orders
                    _uiState.value = InvoiceUiState.Success
                    Log.d(TAG, "✅ ${orders.size} pedidos cargados (todos los estados)")
                } else {
                    _uiState.value = InvoiceUiState.Error(
                        result.exceptionOrNull()?.message ?: "Error cargando pedidos"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando pedidos", e)
                _uiState.value = InvoiceUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Carga las facturas generadas (desde cache y Firebase)
     * Prioriza datos de Firebase sobre cache local
     */
    fun loadGeneratedInvoices(forceRefresh: Boolean = false) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Si hay conexión, cargar desde Firebase primero
                if (_isConnected.value || forceRefresh) {
                    val result = invoicesRepository.getUserInvoices(userId)
                    if (result.isSuccess) {
                        val cloudInvoices = result.getOrNull().orEmpty()

                        // IMPORTANTE: Limpiar cache antes de actualizar
                        // Esto elimina facturas obsoletas o con estados incorrectos
                        prefsManager.clearAll()

                        // Actualizar cache con datos reales de Firebase
                        cloudInvoices.forEach { invoice ->
                            prefsManager.saveGeneratedInvoice(invoice)
                        }

                        _generatedInvoices.value = cloudInvoices
                        Log.d(TAG, "☁️ ${cloudInvoices.size} facturas desde Firebase")
                        Log.d(TAG, "✅ Cache sincronizado con Firebase")
                    } else {
                        // Si falla Firebase, cargar desde cache como respaldo
                        val cachedInvoices = prefsManager.getGeneratedInvoices()
                        _generatedInvoices.value = cachedInvoices
                        Log.d(TAG, "📦 ${cachedInvoices.size} facturas desde cache (fallback)")
                    }
                } else {
                    // Sin conexión: cargar desde cache
                    val cachedInvoices = prefsManager.getGeneratedInvoices()
                    _generatedInvoices.value = cachedInvoices
                    Log.d(TAG, "📦 ${cachedInvoices.size} facturas desde cache (sin conexión)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando facturas", e)
                // En caso de error, intentar cargar desde cache
                try {
                    val cachedInvoices = prefsManager.getGeneratedInvoices()
                    _generatedInvoices.value = cachedInvoices
                    Log.d(TAG, "📦 ${cachedInvoices.size} facturas desde cache (error recovery)")
                } catch (cacheError: Exception) {
                    Log.e(TAG, "❌ Error cargando desde cache", cacheError)
                }
            }
        }
    }

    /**
     * Actualiza el contador de facturas pendientes
     */
    private fun updatePendingInvoicesCount() {
        _pendingInvoicesCount.value = prefsManager.getPendingInvoicesCount()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * SELECCIÓN DE PEDIDOS
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Selecciona/deselecciona un pedido
     */
    fun toggleOrderSelection(orderId: String) {
        val current = _selectedOrders.value.toMutableSet()

        if (current.contains(orderId)) {
            current.remove(orderId)
        } else {
            // Máximo 3 pedidos
            if (current.size < 3) {
                current.add(orderId)
            } else {
                Log.w(TAG, "⚠️ Máximo 3 pedidos permitidos")
            }
        }

        _selectedOrders.value = current
    }

    /**
     * Limpia la selección de pedidos
     */
    fun clearSelection() {
        _selectedOrders.value = emptySet()
    }

    /**
     * Verifica si un pedido está seleccionado
     */
    fun isOrderSelected(orderId: String): Boolean {
        return _selectedOrders.value.contains(orderId)
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * GENERACIÓN DE FACTURAS
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Genera facturas para los pedidos seleccionados usando multithreading
     */
    fun generateInvoicesForSelectedOrders(
        onComplete: (success: Int, failed: Int) -> Unit
    ) {
        val selectedOrderIds = _selectedOrders.value
        if (selectedOrderIds.isEmpty()) {
            onComplete(0, 0)
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            onComplete(0, selectedOrderIds.size)
            return
        }

        viewModelScope.launch {
            _isGenerating.value = true

            // Obtener información del usuario
            val userInfo = getUserInfo(userId)

            // Generar facturas en paralelo (multithreading)
            val results = selectedOrderIds.map { orderId ->
                async(Dispatchers.IO) {
                    generateInvoiceForOrder(orderId, userId, userInfo)
                }
            }.map { it.await() }

            val successCount = results.count { it }
            val failedCount = results.size - successCount

            _isGenerating.value = false
            _generationProgress.value = emptyMap()

            // Limpiar selección y recargar
            clearSelection()
            loadGeneratedInvoices()
            updatePendingInvoicesCount()

            onComplete(successCount, failedCount)
        }
    }

    /**
     * Genera una factura individual para un pedido
     */
    private suspend fun generateInvoiceForOrder(
        orderId: String,
        userId: String,
        userInfo: Pair<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📄 Iniciando generación de factura para pedido $orderId")
            updateProgress(orderId, 0.1f)

            // 1. Obtener el pedido
            val orderResult = ordersRepository.getOrderById(userId, orderId)
            if (orderResult.isFailure) {
                Log.e(TAG, "❌ No se pudo obtener el pedido")
                return@withContext false
            }

            val order = orderResult.getOrNull() ?: return@withContext false
            updateProgress(orderId, 0.2f)

            // 2. Crear OrderSnapshot
            val orderSnapshot = OrderSnapshot.fromMedicationOrder(
                order = order,
                userName = userInfo.first,
                userEmail = userInfo.second
            )
            updateProgress(orderId, 0.3f)

            // 3. Generar número de factura único
            val invoiceNumber = invoiceNumberGenerator.generate()
            updateProgress(orderId, 0.4f)

            // 4. Crear objeto Invoice
            val invoice = Invoice(
                id = UUID.randomUUID().toString(),
                invoiceNumber = invoiceNumber,
                orderId = orderId,
                userId = userId,
                generatedAt = System.currentTimeMillis(),
                status = InvoiceStatus.GENERATING,
                orderSnapshot = orderSnapshot
            )
            updateProgress(orderId, 0.5f)

            // 5. Generar PDF
            val pdfFile = pdfGenerator.generateInvoicePdf(invoice, orderSnapshot)
            if (pdfFile == null) {
                Log.e(TAG, "❌ Error generando PDF")
                saveFailedInvoice(invoice, "Error generando PDF")
                return@withContext false
            }
            updateProgress(orderId, 0.7f)

            // 6. Actualizar invoice con ruta local
            val updatedInvoice = invoice.copy(
                localPdfPath = pdfFile.absolutePath,
                fileSize = pdfFile.length(),
                status = InvoiceStatus.GENERATED
            )

            // 7. Guardar en cache local
            prefsManager.saveGeneratedInvoice(updatedInvoice)
            updateProgress(orderId, 0.8f)

            // 8. Si hay conexión, subir a Firebase
            if (_isConnected.value) {
                uploadInvoiceToFirebase(updatedInvoice, pdfFile)
            } else {
                // Guardar como pendiente de sincronización
                val pendingInvoice = PendingInvoice(
                    orderId = orderId,
                    orderSnapshot = orderSnapshot,
                    localPdfPath = pdfFile.absolutePath
                )
                prefsManager.savePendingInvoice(pendingInvoice)
                Log.d(TAG, "💾 Factura guardada para sincronización posterior")
            }

            updateProgress(orderId, 1.0f)
            Log.d(TAG, "✅ Factura generada exitosamente: $invoiceNumber")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generando factura para pedido $orderId", e)
            updateProgress(orderId, 0f)
            false
        }
    }

    /**
     * Sube una factura a Firebase (Firestore + Storage)
     */
    private suspend fun uploadInvoiceToFirebase(invoice: Invoice, pdfFile: java.io.File) {
        try {
            Log.d(TAG, "☁️ Subiendo factura ${invoice.invoiceNumber} a Firebase")

            // 1. Crear documento en Firestore
            val createResult = invoicesRepository.createInvoice(
                invoice.copy(status = InvoiceStatus.UPLOADING)
            )

            if (createResult.isFailure) {
                throw Exception("Error creando documento en Firestore")
            }

            val invoiceId = createResult.getOrNull()!!

            // 2. Subir PDF a Storage
            val uploadResult = invoicesRepository.uploadPdfToStorage(
                invoiceId = invoiceId,
                pdfFile = pdfFile
            ) { progress ->
                // Opcional: actualizar progreso de subida
                Log.d(TAG, "📊 Progreso de subida: ${(progress * 100).toInt()}%")
            }

            if (uploadResult.isFailure) {
                throw Exception("Error subiendo PDF a Storage")
            }

            val pdfUrl = uploadResult.getOrNull()!!

            // 3. Actualizar documento con URL del PDF
            val storageRef = "invoices/${invoice.userId}/${invoiceId}_${System.currentTimeMillis()}.pdf"
            invoicesRepository.updateInvoiceWithPdfUrl(invoiceId, pdfUrl, storageRef)

            // 4. Actualizar cache local
            val syncedInvoice = invoice.copy(
                id = invoiceId,
                pdfUrl = pdfUrl,
                storageRef = storageRef,
                syncedToCloud = true,
                syncedAt = System.currentTimeMillis(),
                status = InvoiceStatus.COMPLETED
            )
            prefsManager.saveGeneratedInvoice(syncedInvoice)

            // 5. Remover de pendientes si estaba ahí
            prefsManager.removePendingInvoice(invoice.orderId)

            Log.d(TAG, "✅ Factura sincronizada exitosamente")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error subiendo factura a Firebase", e)

            // Marcar como error pero mantener PDF local
            val errorInvoice = invoice.copy(
                status = InvoiceStatus.ERROR,
                errorMessage = e.message ?: "Error subiendo a Firebase"
            )
            prefsManager.saveGeneratedInvoice(errorInvoice)
        }
    }

    /**
     * Guarda una factura que falló en generación
     */
    private fun saveFailedInvoice(invoice: Invoice, errorMessage: String) {
        val failedInvoice = invoice.copy(
            status = InvoiceStatus.ERROR,
            errorMessage = errorMessage
        )
        prefsManager.saveGeneratedInvoice(failedInvoice)
    }

    /**
     * Actualiza el progreso de generación
     */
    private fun updateProgress(orderId: String, progress: Float) {
        val current = _generationProgress.value.toMutableMap()
        current[orderId] = progress
        _generationProgress.value = current
    }

    /**
     * Obtiene información del usuario desde Firestore
     */
    private suspend fun getUserInfo(userId: String): Pair<String, String> {
        return try {
            val userDoc = firestore.collection("usuarios")
                .document(userId)
                .get()
                .await()

            val name = userDoc.getString("nombre") ?: userDoc.getString("name") ?: ""
            val email = userDoc.getString("email") ?: ""

            Pair(name, email)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo info del usuario", e)
            Pair("", "")
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * SINCRONIZACIÓN
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Sincroniza facturas pendientes con Firebase
     */
    suspend fun syncPendingInvoices(): Pair<Int, Int> {
        val pendingInvoices = prefsManager.getPendingInvoices()
        if (pendingInvoices.isEmpty()) {
            Log.d(TAG, "✅ No hay facturas pendientes de sincronizar")
            return 0 to 0
        }

        if (!_isConnected.value) {
            Log.w(TAG, "⚠️ Sin conexión para sincronizar")
            return 0 to pendingInvoices.size
        }

        Log.d(TAG, "🔄 Sincronizando ${pendingInvoices.size} facturas pendientes")

        var successCount = 0

        pendingInvoices.forEach { pending ->
            try {
                // Buscar factura en cache local
                val cachedInvoices = prefsManager.getGeneratedInvoices()
                val invoice = cachedInvoices.find { it.orderId == pending.orderId }

                if (invoice != null && invoice.localPdfPath.isNotEmpty()) {
                    val pdfFile = java.io.File(invoice.localPdfPath)

                    if (pdfFile.exists()) {
                        uploadInvoiceToFirebase(invoice, pdfFile)
                        successCount++
                    } else {
                        Log.w(TAG, "⚠️ Archivo PDF no encontrado: ${invoice.localPdfPath}")
                        prefsManager.removePendingInvoice(pending.orderId)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sincronizando factura", e)
            }
        }

        val remainingCount = prefsManager.getPendingInvoicesCount()
        updatePendingInvoicesCount()
        loadGeneratedInvoices()

        Log.d(TAG, "✅ Sincronización: $successCount exitosas, $remainingCount pendientes")

        return successCount to remainingCount
    }

    /**
     * Sincroniza manualmente desde la UI
     */
    fun syncPendingInvoicesManually(onResult: (String) -> Unit) {
        viewModelScope.launch {
            if (!_isConnected.value) {
                onResult("📡 Sin conexión a internet")
                return@launch
            }

            onResult("🔄 Sincronizando facturas pendientes...")

            val (synced, remaining) = syncPendingInvoices()

            when {
                synced > 0 && remaining == 0 ->
                    onResult("✅ $synced factura(s) sincronizada(s)")

                synced > 0 && remaining > 0 ->
                    onResult("✅ $synced factura(s) sincronizada(s). Pendientes: $remaining")

                synced == 0 && remaining > 0 ->
                    onResult("⚠️ No se pudo sincronizar. Pendientes: $remaining")

                else ->
                    onResult("ℹ️ No hay facturas pendientes")
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * OTRAS OPERACIONES
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Elimina una factura
     */
    fun deleteInvoice(invoiceId: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            try {
                // Eliminar de Firebase si está sincronizada
                if (_isConnected.value) {
                    invoicesRepository.deleteInvoice(userId, invoiceId)
                }

                // Eliminar de cache local
                prefsManager.deleteInvoice(invoiceId)

                // Recargar lista
                loadGeneratedInvoices()

                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error eliminando factura", e)
                onComplete(false)
            }
        }
    }

    /**
     * Reintenta generar/sincronizar una factura con error
     */
    fun retryInvoice(invoice: Invoice, onComplete: (Boolean) -> Unit) {
        if (!invoice.canRetry()) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            try {
                val pdfFile = java.io.File(invoice.localPdfPath)

                if (pdfFile.exists() && _isConnected.value) {
                    uploadInvoiceToFirebase(invoice, pdfFile)
                    loadGeneratedInvoices()
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reintentando factura", e)
                onComplete(false)
            }
        }
    }

    /**
     * Limpia todas las facturas (desarrollo/testing)
     */
    fun clearAllInvoices() {
        viewModelScope.launch {
            prefsManager.clearAll()
            loadGeneratedInvoices()
            updatePendingInvoicesCount()
        }
    }

    /**
     * Sincroniza el cache local con Firebase
     * Útil cuando hay discrepancias entre cache y servidor
     */
    fun syncCacheWithFirebase(onComplete: ((Int) -> Unit)? = null) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Sincronizando cache con Firebase...")

                // 1. Obtener facturas de Firebase
                val result = invoicesRepository.getUserInvoices(userId)
                if (result.isSuccess) {
                    val cloudInvoices = result.getOrNull().orEmpty()

                    // 2. Limpiar todo el cache local
                    prefsManager.clearAll()
                    Log.d(TAG, "🗑️ Cache local limpiado")

                    // 3. Guardar facturas de Firebase en cache
                    cloudInvoices.forEach { invoice ->
                        prefsManager.saveGeneratedInvoice(invoice)
                    }

                    // 4. Actualizar UI
                    _generatedInvoices.value = cloudInvoices
                    updatePendingInvoicesCount()

                    Log.d(TAG, "✅ Cache sincronizado: ${cloudInvoices.size} facturas")
                    onComplete?.invoke(cloudInvoices.size)
                } else {
                    Log.e(TAG, "❌ Error obteniendo facturas de Firebase")
                    onComplete?.invoke(-1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sincronizando cache", e)
                onComplete?.invoke(-1)
            }
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * UI STATES
 * ═══════════════════════════════════════════════════════════════════════════
 */

sealed class InvoiceUiState {
    object Loading : InvoiceUiState()
    object Success : InvoiceUiState()
    data class Error(val message: String) : InvoiceUiState()
}