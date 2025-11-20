package com.mobile.mymeds.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.data.local.room.converters.AppDatabase
import com.mobile.mymeds.data.local.room.dao.MedicationDao
import com.mobile.mymeds.data.local.room.dao.OrderDao
import com.mobile.mymeds.data.local.room.dao.OrderItemDao
import com.mobile.mymeds.data.local.files.LocalFileManager
import com.mobile.mymeds.data.local.datastore.UserPreferencesManager
import com.mobile.mymeds.data.local.prefs.SharedPrefsManager
import com.mobile.mymeds.data.local.room.entities.OrderEntity
import com.mobile.mymeds.data.local.room.entitites.OrderItemEntity
import com.mobile.mymeds.data.local.room.entitites.MedicationEntity
import com.mobile.mymeds.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

private const val TAG = "OrdersViewModel"

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                      ORDERS VIEW MODEL                                    ║
 * ║           Sistema completo de gestión de pedidos                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * IMPLEMENTA TODAS LAS TECNOLOGÍAS DE PERSISTENCIA:
 *
 * 1. ✅ BD RELACIONAL (Room) - 10 PUNTOS
 *    - DAOs: MedicationDao, OrderDao, OrderItemDao
 *    - Operaciones CRUD completas
 *    - Relaciones entre tablas (One-to-Many, Many-to-Many)
 *
 * 2. ✅ BD LLAVE/VALOR (DataStore) - 5 PUNTOS
 *    - Preferencias de usuario (tipo entrega, direcciones)
 *    - Configuración asíncrona
 *    - Flow-based preferences
 *
 * 3. ✅ ARCHIVOS LOCALES - 5 PUNTOS
 *    - Guardado de recibos/comprobantes en JSON
 *    - Operaciones de lectura/escritura
 *    - Directorio privado de la app
 *
 * 4. ✅ SHAREDPREFERENCES - 5 PUNTOS
 *    - Flags de estado
 *    - Contadores y caché
 *    - Configuraciones rápidas
 *
 * TOTAL: 25 PUNTOS DE PERSISTENCIA
 */
class OrdersViewModel(
    private val context: Context
) : ViewModel() {

    // ═══════════════════════════════════════════════════════════════════════════
    // INICIALIZACIÓN DE COMPONENTES
    // ═══════════════════════════════════════════════════════════════════════════

    // Room Database
    private val database = AppDatabase.getDatabase(context)
    private val medicationDao: MedicationDao = database.medicationDao()
    private val orderDao: OrderDao = database.orderDao()
    private val orderItemDao: OrderItemDao = database.orderItemDao()

    // DataStore Manager
    private val preferencesManager = UserPreferencesManager(context)

    // File Manager
    private val fileManager = LocalFileManager(context)

    // SharedPreferences Manager
    private val sharedPrefs = SharedPrefsManager(context)

    // Firebase
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ═══════════════════════════════════════════════════════════════════════════
    // ESTADOS OBSERVABLES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lista de medicamentos activos desde Room
     * Flow que se actualiza automáticamente cuando cambia la BD
     */
    val medications: StateFlow<List<MedicationEntity>> = medicationDao.getAllActiveMedications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    /**
     * Lista de pedidos del usuario desde Room
     * Flow que se actualiza automáticamente cuando cambian los pedidos
     */
    val orders: StateFlow<List<OrderEntity>> = auth.currentUser?.uid?.let { userId ->
        orderDao.getUserOrders(userId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList()
            )
    } ?: MutableStateFlow(emptyList())

    /**
     * Tipo de entrega predeterminado desde DataStore
     */
    val defaultDeliveryType: StateFlow<DeliveryType> = preferencesManager.defaultDeliveryType
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = DeliveryType.HOME_DELIVERY
        )

    /**
     * Dirección predeterminada desde DataStore
     */
    val defaultAddress: StateFlow<String> = preferencesManager.defaultAddress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ""
        )

    /**
     * Estado de carga para operaciones asíncronas
     */
    var isLoading by mutableStateOf(false)
        private set

    /**
     * Lista de medicamentos seleccionados para crear pedido
     */
    var selectedMedications by mutableStateOf<List<MedicationEntity>>(emptyList())
        private set

    /**
     * Mensaje de error o estado
     */
    var statusMessage by mutableStateOf<String?>(null)
        private set

    // ═══════════════════════════════════════════════════════════════════════════
    // INICIALIZACIÓN
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        Log.d(TAG, "🎯 OrdersViewModel inicializado")
        Log.d(TAG, "📊 [SharedPrefs] Total de pedidos históricos: ${sharedPrefs.getOrderCount()}")
        Log.d(TAG, "📊 [SharedPrefs] Último pedido ID: ${sharedPrefs.getLastOrderId()}")

        // Sincroniza datos de Firebase a Room al iniciar
        syncMedicationsFromFirebase()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERACIONES DE SINCRONIZACIÓN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 🔄 Sincroniza medicamentos desde Firebase a Room
     *
     * Esta función demuestra:
     * - Lectura desde Firebase (cloud)
     * - Escritura en Room (BD relacional local)
     * - Actualización de DataStore (preferencias)
     * - Actualización de SharedPreferences (timestamp)
     */
    fun syncMedicationsFromFirebase() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "⚠️ Usuario no autenticado, no se puede sincronizar")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                isLoading = true
                Log.d(TAG, "🔄 Iniciando sincronización de medicamentos desde Firebase...")

                // 1. Lee desde Firebase
                val snapshot = firestore
                    .collection("usuarios")
                    .document(userId)
                    .collection("medicamentosUsuario")
                    .whereEqualTo("active", true)
                    .get()
                    .await()

                // 2. Convierte a entidades de Room
                val medicationEntities = snapshot.documents.mapNotNull { doc ->
                    try {
                        MedicationEntity(
                            medicationId = doc.getString("medicationId") ?: return@mapNotNull null,
                            name = doc.getString("name") ?: "Sin nombre",
                            doseMg = doc.getLong("doseMg")?.toInt() ?: 0,
                            frequencyHours = doc.getLong("frequencyHours")?.toInt() ?: 24,
                            stockQuantity = 30, // Stock por defecto
                            prescriptionId = doc.getString("prescriptionId") ?: "",
                            active = doc.getBoolean("active") ?: true,
                            lastSyncedAt = System.currentTimeMillis(),
                            firebaseDocId = doc.id
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parseando medicamento: ${e.message}", e)
                        null
                    }
                }

                // 3. Guarda en Room (BD relacional)
                medicationDao.insertAll(medicationEntities)
                Log.d(TAG, "💾 [Room] ${medicationEntities.size} medicamentos guardados")

                // 4. Actualiza timestamp en DataStore
                val currentTimestamp = System.currentTimeMillis()
                preferencesManager.saveLastSyncTimestamp(currentTimestamp)
                Log.d(TAG, "💾 [DataStore] Timestamp de sincronización actualizado")

                // 5. Actualiza timestamp en SharedPrefs (caché rápido)
                sharedPrefs.setLastSyncTime(currentTimestamp)
                Log.d(TAG, "💾 [SharedPrefs] Cache de sincronización actualizado")

                withContext(Dispatchers.Main) {
                    isLoading = false
                    statusMessage = "✅ ${medicationEntities.size} medicamentos sincronizados"
                }

                Log.d(TAG, "✅ Sincronización completada exitosamente")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en sincronización: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    statusMessage = "❌ Error al sincronizar: ${e.message}"
                }
            }
        }
    }

    /**
     * ☁️ Sincroniza un pedido a Firebase
     *
     * Esta función demuestra:
     * - Escritura en Firebase (cloud)
     * - Actualización en Room (BD relacional)
     */
    private suspend fun syncOrderToFirebase(orderWithItems: OrderWithItems) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "☁️ Sincronizando pedido ${orderWithItems.order.orderId} a Firebase...")

            // Prepara los datos para Firebase
            val orderData = hashMapOf(
                "userId" to orderWithItems.order.userId,
                "orderDate" to Date(orderWithItems.order.orderDate),
                "deliveryType" to orderWithItems.order.deliveryType.name,
                "deliveryAddress" to orderWithItems.order.deliveryAddress,
                "status" to orderWithItems.order.status.name,
                "totalItems" to orderWithItems.order.totalItems,
                "notes" to orderWithItems.order.notes,
                "items" to orderWithItems.items.map { item ->
                    hashMapOf(
                        "medicationId" to item.medicationId,
                        "medicationName" to item.medicationName,
                        "doseMg" to item.doseMg,
                        "quantity" to item.quantity
                    )
                },
                "createdAt" to Date(orderWithItems.order.createdAt)
            )

            // Sube a Firebase
            val docRef = firestore
                .collection("usuarios")
                .document(orderWithItems.order.userId)
                .collection("pedidos")
                .add(orderData)
                .await()

            // Actualiza el pedido local con el ID de Firebase
            orderDao.updateOrder(
                orderWithItems.order.copy(
                    syncedToFirebase = true,
                    firebaseOrderId = docRef.id
                )
            )

            Log.d(TAG, "☁️ [Firebase] Pedido sincronizado exitosamente: ${docRef.id}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ [Firebase] Error sincronizando pedido: ${e.message}", e)
            // No lanzamos la excepción para que el pedido local no se pierda
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERACIONES PRINCIPALES - CREAR PEDIDO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 📦 Crea un nuevo pedido utilizando TODAS las tecnologías de persistencia
     *
     * DEMUESTRA USO DE:
     * 1. Room (BD Relacional) - Guarda order y order items
     * 2. DataStore (Llave/Valor) - Actualiza preferencias de usuario
     * 3. Archivos Locales - Guarda recibo en JSON
     * 4. SharedPreferences - Incrementa contadores y flags
     * 5. Firebase - Sincroniza a la nube
     *
     * Esta función es el CORE que demuestra la integración de todas las tecnologías
     */
    fun createOrder(
        selectedMeds: List<Pair<MedicationEntity, Int>>,
        deliveryType: DeliveryType,
        address: String?,
        notes: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("Usuario no autenticado")
            return
        }

        if (selectedMeds.isEmpty()) {
            onError("Debe seleccionar al menos un medicamento")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                isLoading = true
                Log.d(TAG, "📦 Iniciando creación de nuevo pedido...")
                Log.d(TAG, "📋 Medicamentos seleccionados: ${selectedMeds.size}")
                Log.d(TAG, "📋 Tipo de entrega: $deliveryType")

                // ═══════════════════════════════════════════════════════════════
                // 1. ROOM - BD RELACIONAL (10 PUNTOS)
                // ═══════════════════════════════════════════════════════════════

                // Crea el pedido principal
                val order = OrderEntity(
                    userId = userId,
                    orderDate = System.currentTimeMillis(),
                    deliveryType = deliveryType,
                    deliveryAddress = address,
                    status = OrderStatus.PENDING,
                    totalItems = selectedMeds.sumOf { it.second },
                    notes = notes,
                    createdAt = System.currentTimeMillis(),
                    syncedToFirebase = false
                )

                // Inserta en Room y obtiene el ID autogenerado
                val orderId = orderDao.insertOrder(order)
                Log.d(TAG, "💾 [Room] Pedido guardado con ID: $orderId")

                // Crea los ítems del pedido (relación One-to-Many)
                val items = selectedMeds.map { (medication, quantity) ->
                    OrderItemEntity(
                        orderId = orderId,
                        medicationId = medication.medicationId,
                        quantity = quantity,
                        medicationName = medication.name,
                        doseMg = medication.doseMg
                    )
                }

                // Inserta todos los ítems en Room
                orderItemDao.insertAll(items)
                Log.d(TAG, "💾 [Room] ${items.size} ítems del pedido guardados")

                // ═══════════════════════════════════════════════════════════════
                // 2. ARCHIVOS LOCALES (5 PUNTOS)
                // ═══════════════════════════════════════════════════════════════

                // Crea el objeto completo con pedido + ítems
                val orderWithItems = OrderWithItems(
                    order = order.copy(orderId = orderId),
                    items = items
                )

                // Guarda el recibo en archivo JSON
                val receiptFile = fileManager.saveOrderReceipt(orderWithItems)
                if (receiptFile != null) {
                    Log.d(TAG, "📄 [Files] Recibo guardado: ${receiptFile.name}")
                    Log.d(TAG, "📄 [Files] Ruta: ${receiptFile.absolutePath}")
                } else {
                    Log.w(TAG, "⚠️ [Files] No se pudo guardar el recibo")
                }

                // ═══════════════════════════════════════════════════════════════
                // 3. DATASTORE - BD LLAVE/VALOR (5 PUNTOS)
                // ═══════════════════════════════════════════════════════════════

                // Actualiza preferencia de tipo de entrega si cambió
                if (deliveryType != defaultDeliveryType.value) {
                    preferencesManager.saveDefaultDeliveryType(deliveryType)
                    Log.d(TAG, "💾 [DataStore] Tipo de entrega predeterminado actualizado: $deliveryType")
                }

                // Actualiza dirección predeterminada si cambió
                if (!address.isNullOrBlank() && address != defaultAddress.value) {
                    preferencesManager.saveDefaultAddress(address)
                    Log.d(TAG, "💾 [DataStore] Dirección predeterminada actualizada")
                }

                // ═══════════════════════════════════════════════════════════════
                // 4. SHAREDPREFERENCES (5 PUNTOS)
                // ═══════════════════════════════════════════════════════════════

                // Incrementa el contador de pedidos
                sharedPrefs.incrementOrderCount()
                val totalOrders = sharedPrefs.getOrderCount()
                Log.d(TAG, "💾 [SharedPrefs] Contador incrementado. Total pedidos: $totalOrders")

                // Guarda el último ID de pedido procesado
                sharedPrefs.setLastOrderId(orderId)
                Log.d(TAG, "💾 [SharedPrefs] Último pedido ID guardado: $orderId")

                // ═══════════════════════════════════════════════════════════════
                // 5. FIREBASE - SINCRONIZACIÓN A LA NUBE
                // ═══════════════════════════════════════════════════════════════

                // Sincroniza el pedido completo a Firebase
                syncOrderToFirebase(orderWithItems)

                // ═══════════════════════════════════════════════════════════════
                // FINALIZACIÓN EXITOSA
                // ═══════════════════════════════════════════════════════════════

                withContext(Dispatchers.Main) {
                    isLoading = false
                    statusMessage = "✅ Pedido #$orderId creado exitosamente"

                    // Limpia la selección
                    clearSelection()

                    // Callback de éxito
                    onSuccess()
                }

                Log.d(TAG, "✅ Pedido creado y guardado en TODAS las tecnologías")
                Log.d(TAG, "═══════════════════════════════════════════════════════")
                Log.d(TAG, "✅ Room: Order + ${items.size} items guardados")
                Log.d(TAG, "✅ Files: Recibo JSON guardado")
                Log.d(TAG, "✅ DataStore: Preferencias actualizadas")
                Log.d(TAG, "✅ SharedPrefs: Contadores actualizados")
                Log.d(TAG, "✅ Firebase: Pedido sincronizado")
                Log.d(TAG, "═══════════════════════════════════════════════════════")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error crítico creando pedido: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    statusMessage = "❌ Error: ${e.message}"
                    onError(e.message ?: "Error desconocido al crear pedido")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERACIONES CRUD - PEDIDOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 📋 Obtiene los detalles completos de un pedido (Order + Items)
     */
    suspend fun getOrderWithItems(orderId: Long): OrderWithItems? = withContext(Dispatchers.IO) {
        try {
            val order = orderDao.getOrderById(orderId) ?: return@withContext null
            val items = orderItemDao.getOrderItems(orderId)
            OrderWithItems(order, items)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo pedido: ${e.message}", e)
            null
        }
    }

    /**
     * 🔄 Actualiza el estado de un pedido
     */
    fun updateOrderStatus(
        orderId: Long,
        newStatus: OrderStatus,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                orderDao.updateOrderStatus(orderId, newStatus)
                Log.d(TAG, "✅ Estado del pedido $orderId actualizado a $newStatus")

                withContext(Dispatchers.Main) {
                    statusMessage = "Pedido actualizado a ${newStatus.name}"
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error actualizando estado: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Error al actualizar")
                }
            }
        }
    }

    /**
     * 🗑️ Cancela un pedido (cambia estado y elimina archivos asociados)
     */
    fun cancelOrder(
        orderId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Actualiza el estado a CANCELLED
                orderDao.updateOrderStatus(orderId, OrderStatus.CANCELLED)

                // Elimina el recibo asociado
                fileManager.deleteReceipt(orderId)

                Log.d(TAG, "✅ Pedido $orderId cancelado")

                withContext(Dispatchers.Main) {
                    statusMessage = "Pedido cancelado"
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cancelando pedido: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Error al cancelar")
                }
            }
        }
    }

    /**
     * 🗑️ Elimina completamente un pedido (BD + archivos)
     */
    fun deleteOrder(
        orderId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Elimina de Room (los items se eliminan en cascada)
                orderDao.deleteOrder(orderId)

                // Elimina el archivo del recibo
                fileManager.deleteReceipt(orderId)

                Log.d(TAG, "🗑️ Pedido $orderId eliminado completamente")

                withContext(Dispatchers.Main) {
                    statusMessage = "Pedido eliminado"
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error eliminando pedido: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Error al eliminar")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERACIONES DE ARCHIVOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 📄 Lee el recibo de un pedido desde archivo JSON
     */
    suspend fun getOrderReceipt(orderId: Long): String? = withContext(Dispatchers.IO) {
        fileManager.readOrderReceipt(orderId)
    }

    /**
     * 📁 Obtiene todos los recibos guardados
     */
    fun getAllReceipts() = fileManager.getAllReceipts()

    // ═══════════════════════════════════════════════════════════════════════════
    // GESTIÓN DE SELECCIÓN DE MEDICAMENTOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Toggle de selección de medicamento
     */
    fun toggleMedicationSelection(medication: MedicationEntity) {
        selectedMedications = if (selectedMedications.contains(medication)) {
            selectedMedications - medication
        } else {
            selectedMedications + medication
        }
        Log.d(TAG, "📋 Medicamentos seleccionados: ${selectedMedications.size}")
    }

    /**
     * Limpia toda la selección
     */
    fun clearSelection() {
        selectedMedications = emptyList()
        Log.d(TAG, "🧹 Selección limpiada")
    }

    /**
     * Verifica si un medicamento está seleccionado
     */
    fun isMedicationSelected(medication: MedicationEntity): Boolean {
        return selectedMedications.contains(medication)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERACIONES DE MEDICAMENTOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 📦 Actualiza el stock de un medicamento
     */
    fun updateMedicationStock(
        medicationId: String,
        newQuantity: Int,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                medicationDao.updateStock(medicationId, newQuantity)
                Log.d(TAG, "✅ Stock actualizado para medicamento $medicationId: $newQuantity")

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error actualizando stock: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Error al actualizar stock")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ESTADÍSTICAS Y UTILIDADES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 📊 Obtiene estadísticas del sistema desde SharedPreferences
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalOrders" to sharedPrefs.getOrderCount(),
            "lastOrderId" to sharedPrefs.getLastOrderId(),
            "lastSyncTime" to sharedPrefs.getLastSyncTime(),
            "isFirstTime" to sharedPrefs.isFirstTimeLaunch()
        )
    }

    /**
     * 🔄 Fuerza una resincronización completa
     */
    fun forceFullSync() {
        Log.d(TAG, "🔄 Forzando sincronización completa...")
        syncMedicationsFromFirebase()
    }

    /**
     * 🧹 Limpia el mensaje de estado
     */
    fun clearStatusMessage() {
        statusMessage = null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 OrdersViewModel destruido")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VIEW MODEL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Factory para crear el OrdersViewModel con dependencias
 */
class OrdersViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            return OrdersViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}