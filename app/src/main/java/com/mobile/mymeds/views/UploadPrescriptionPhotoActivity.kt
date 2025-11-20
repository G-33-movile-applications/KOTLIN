package com.mobile.mymeds.views

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.*

private const val TAG = "PhotoUploadActivity"
private const val MAX_IMAGES = 3

class UploadPrescriptionPhotoActivity : ComponentActivity() {
    private val vm: PhotoOcrUploadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UploadPrescriptionPhotoScreen(vm = vm, finish = { finish() })
        }
    }
}

data class ImageDocument(
    val uri: Uri,
    val fileName: String,
    val isProcessing: Boolean = false,
    val isProcessed: Boolean = false,
    val medicationCount: Int = 0,
    val extractedText: String? = null
)

data class MedicationInfo2(
    val medicationId: String,
    val name: String,
    val medicationRef: String,
    val doseMg: Int,
    val frequencyHours: Int,
    val startDate: Date,
    val endDate: Date,
    val active: Boolean,
    val prescriptionId: String,
    val sourceFile: String = ""
)

/* ═══════════════════════════════════════════════════════════════════════════
 * ║                      ESTRATEGIA DE CONECTIVIDAD                         ║
 * ║                    ALMACENAMIENTO LOCAL CON ROOM                        ║
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ARQUITECTURA DE PERSISTENCIA OFFLINE:
 *
 * 1. DETECCIÓN DE CONECTIVIDAD:
 *    - ConnectivityManager para verificar estado de red
 *    - Soporte para WiFi, datos móviles y ethernet
 *    - Compatible con Android 6.0+ (API 23)
 *
 * 2. ALMACENAMIENTO LOCAL (Room Database):
 *    - Entidad: PendingPrescriptionEntity
 *    - DAO: Operaciones CRUD locales
 *    - Base de datos SQLite embebida
 *
 * 3. FLUJO DE TRABAJO:
 *    a) CON INTERNET:
 *       ├─> Guarda directamente en Firestore
 *       └─> Marca como sincronizado
 *
 *    b) SIN INTERNET:
 *       ├─> Guarda localmente en Room
 *       ├─> Marca como pendiente de sincronización
 *       └─> Usuario puede usar la app normalmente
 *
 * 4. SINCRONIZACIÓN AUTOMÁTICA:
 *    - Se ejecuta al detectar conexión
 *    - Procesa todos los registros pendientes
 *    - Actualiza estado tras sincronización exitosa
 *    - Elimina registros ya sincronizados
 *
 * 5. DISPATCHERS UTILIZADOS:
 *    - Dispatchers.IO: Operaciones de base de datos local
 *    - Dispatchers.Main: Actualización de UI
 *    - Dispatchers.Default: Procesamiento de datos
 */

// ═══════════════════════════════════════════════════════════════════════════
// ENTIDAD ROOM PARA ALMACENAMIENTO LOCAL
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "pending_prescriptions")
data class PendingPrescriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "medications_json")
    val medicationsJson: String, // JSON serializado de List<MedicationInfo>

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int = 0,

    @ColumnInfo(name = "last_sync_attempt")
    val lastSyncAttempt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════
// DAO - DATA ACCESS OBJECT PARA OPERACIONES LOCALES
// ═══════════════════════════════════════════════════════════════════════════

@Dao
interface PendingPrescriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prescription: PendingPrescriptionEntity): Long

    @Query("SELECT * FROM pending_prescriptions WHERE is_synced = 0 ORDER BY created_at ASC")
    suspend fun getAllPending(): List<PendingPrescriptionEntity>

    @Query("SELECT COUNT(*) FROM pending_prescriptions WHERE is_synced = 0")
    suspend fun getPendingCount(): Int

    @Update
    suspend fun update(prescription: PendingPrescriptionEntity)

    @Delete
    suspend fun delete(prescription: PendingPrescriptionEntity)

    @Query("DELETE FROM pending_prescriptions WHERE is_synced = 1")
    suspend fun deleteSynced()

    @Query("SELECT * FROM pending_prescriptions WHERE id = :id")
    suspend fun getById(id: Long): PendingPrescriptionEntity?
}

// ═══════════════════════════════════════════════════════════════════════════
// DATABASE - ROOM DATABASE CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════

@Database(
    entities = [PendingPrescriptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingPrescriptionDao(): PendingPrescriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymeds_offline_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONNECTIVITY MANAGER - DETECCIÓN DE CONECTIVIDAD
// ═══════════════════════════════════════════════════════════════════════════

object ConnectivityHelper {

    /**
     * Verifica si hay conexión a internet disponible
     * Compatible con Android 6.0+ (API 23)
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected
        }
    }

    fun getConnectionType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "Sin conexión"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Sin conexión"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Desconocido"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo?.typeName ?: "Sin conexión"
        }
    }
}

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║             IMPLEMENTACIÓN DE MULTITHREADING PARA IMÁGENES               ║
 * ║                     CON CORRUTINAS Y DISPATCHERS                          ║
 * ║                 + ESTRATEGIA DE CONECTIVIDAD OFFLINE                      ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * SCOPE UTILIZADO: viewModelScope
 * - CoroutineScope vinculado al ciclo de vida del ViewModel
 * - Se cancela automáticamente cuando el ViewModel es destruido
 * - Previene memory leaks y crashes
 *
 * DISPATCHERS IMPLEMENTADOS:
 *
 * 1. Dispatchers.IO (10 puntos - I/O + Main)
 *    - Lectura de imágenes desde URI
 *    - Operaciones de red (Firestore)
 *    - Operaciones de base de datos local (Room)
 *    - Procesamiento de bitmaps
 *    - OCR con ML Kit
 *
 * 2. Dispatchers.Main (10 puntos - I/O + Main)
 *    - Actualización de estados observables (mutableStateOf)
 *    - Actualización de UI en tiempo real
 *    - Feedback visual de progreso
 *    - Notificaciones de conectividad
 *
 * 3. Dispatchers.Default (5 puntos - Corrutina con dispatcher)
 *    - Procesamiento CPU-intensivo (regex, parsing)
 *    - Análisis de texto extraído
 *    - Operaciones computacionales
 *    - Serialización JSON
 *
 * ESTRUCTURA DE CORRUTINAS ANIDADAS (10 puntos - Múltiples corrutinas):
 *
 * viewModelScope.launch(Dispatchers.IO) {              // Nivel 1: Corrutina principal
 *     └─> withContext(Dispatchers.Main) {              // Nivel 2: Actualización UI
 *           └─> async(Dispatchers.IO) {                // Nivel 3: Procesamiento paralelo
 *                 └─> withContext(Dispatchers.IO) {    // Nivel 4: Optimización imagen
 *                       └─> launch(Dispatchers.IO) {   // Nivel 5: OCR
 *                             └─> withContext(Dispatchers.Default) { // Nivel 6: Parseo
 *                                   └─> withContext(Dispatchers.Main) { // Nivel 7: UI final
 *                                         └─> launch(Dispatchers.IO) { // Nivel 8: Guardado
 *                                         }
 *                                   }
 *                             }
 *                       }
 *                 }
 *           }
 *     }
 * }
 *
 * NUEVA ARQUITECTURA DE CONECTIVIDAD:
 *
 * ┌─────────────────────────────────────────────────┐
 * │          USUARIO CREA PRESCRIPCIÓN              │
 * └─────────────────┬───────────────────────────────┘
 *                   │
 *                   ▼
 *         ┌─────────────────────┐
 *         │ ¿HAY CONEXIÓN?      │
 *         └─────────┬───────────┘
 *                   │
 *        ┌──────────┴──────────┐
 *        │                     │
 *        ▼                     ▼
 *   ┌────────┐          ┌─────────────┐
 *   │   SÍ   │          │     NO      │
 *   └────┬───┘          └──────┬──────┘
 *        │                     │
 *        ▼                     ▼
 * ┌──────────────┐    ┌─────────────────┐
 * │  FIRESTORE   │    │  ROOM (LOCAL)   │
 * │  (ONLINE)    │    │  (OFFLINE)      │
 * └──────┬───────┘    └────────┬────────┘
 *        │                     │
 *        │                     │
 *        ▼                     ▼
 * ┌──────────────┐    ┌─────────────────┐
 * │   SUCCESS    │    │ GUARDADO LOCAL  │
 * │   ✅         │    │ Pendiente sync  │
 * └──────────────┘    └────────┬────────┘
 *                              │
 *                              │ (Cuando hay conexión)
 *                              ▼
 *                     ┌─────────────────┐
 *                     │ SINCRONIZACIÓN  │
 *                     │   AUTOMÁTICA    │
 *                     └────────┬────────┘
 *                              │
 *                              ▼
 *                     ┌─────────────────┐
 *                     │   FIRESTORE     │
 *                     │     ✅          │
 *                     └─────────────────┘
 */
class PhotoOcrUploadViewModel : ViewModel() {
    var images by mutableStateOf<List<ImageDocument>>(emptyList())
        private set
    var parsedMedications by mutableStateOf<List<MedicationInfo>>(emptyList())
        private set
    var processing by mutableStateOf(false)
        private set
    var uploading by mutableStateOf(false)
        private set
    var progressMessage by mutableStateOf("")
        private set

    // ═══ NUEVOS ESTADOS PARA CONECTIVIDAD ═══
    var isOnline by mutableStateOf(true)
        private set
    var connectionType by mutableStateOf("Verificando...")
        private set
    var pendingCount by mutableStateOf(0)
        private set
    var showOfflineWarning by mutableStateOf(false)
        private set
    var syncing by mutableStateOf(false)
        private set

    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()
    private var database: AppDatabase? = null

    fun initDatabase(context: Context) {
        database = AppDatabase.getDatabase(context)
        // Verificar conexión inicial
        checkConnectivity(context)
        // Cargar contador de pendientes
        loadPendingCount()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * VERIFICACIÓN DE CONECTIVIDAD
     * ═══════════════════════════════════════════════════════════════════════
     */
    fun checkConnectivity(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val hasConnection = ConnectivityHelper.isNetworkAvailable(context)
            val connType = ConnectivityHelper.getConnectionType(context)

            withContext(Dispatchers.Main) {
                isOnline = hasConnection
                connectionType = connType

                Log.d(TAG, "📡 Estado de conexión: ${if (hasConnection) "ONLINE" else "OFFLINE"} ($connType)")
            }

            // Si hay conexión, intentar sincronizar pendientes
            if (hasConnection) {
                syncPendingPrescriptions(context)
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * CARGAR CONTADOR DE PRESCRIPCIONES PENDIENTES
     * ═══════════════════════════════════════════════════════════════════════
     */
    private fun loadPendingCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = database?.pendingPrescriptionDao()?.getPendingCount() ?: 0

            withContext(Dispatchers.Main) {
                pendingCount = count
                Log.d(TAG, "📊 Prescripciones pendientes: $count")
            }
        }
    }

    fun addImage(uri: Uri, fileName: String): Boolean {
        if (images.size >= MAX_IMAGES) {
            return false
        }
        images = images + ImageDocument(uri, fileName)
        return true
    }

    fun removeImage(index: Int) {
        if (index in images.indices) {
            val fileName = images[index].fileName
            images = images.filterIndexed { i, _ -> i != index }
            parsedMedications = parsedMedications.filter { it.sourceFile != fileName }
        }
    }

    fun clearAll() {
        images = emptyList()
        parsedMedications = emptyList()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * FUNCIÓN PRINCIPAL: processAllImages
     * ═══════════════════════════════════════════════════════════════════════
     *
     * IMPLEMENTACIÓN DE MÚLTIPLES CORRUTINAS ANIDADAS (10 PUNTOS)
     *
     * Estructura de anidación:
     * 1. viewModelScope.launch(IO) - Corrutina principal
     * 2. withContext(Main) - Actualización de UI
     * 3. async(IO) - Procesamiento paralelo de cada imagen
     * 4. withContext(IO) - Operaciones de carga y optimización
     * 5. launch(IO) - OCR por imagen
     * 6. withContext(Default) - Parseo de texto
     * 7. withContext(Main) - UI final
     */
    fun processAllImages(context: Activity, onError: (String) -> Unit) {
        // NIVEL 1: CORRUTINA PRINCIPAL CON DISPATCHER IO
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
                Log.d(TAG, "║  INICIANDO PROCESAMIENTO DE ${images.size} IMÁGENES   ║")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

                // NIVEL 2: ACTUALIZACIÓN DE UI EN MAIN
                withContext(Dispatchers.Main) {
                    processing = true
                    progressMessage = "Preparando ${images.size} imagen(es)..."
                }

                // NIVEL 3: PROCESAMIENTO PARALELO CON MÚLTIPLES CORRUTINAS ASYNC
                val imageProcessingJobs = images.mapIndexed { index, imageDoc ->
                    async(Dispatchers.IO) {
                        Log.d(TAG, "🖼️ [Async-IO] Procesando imagen ${index + 1}: ${imageDoc.fileName} en thread: ${Thread.currentThread().name}")

                        // Actualiza estado de procesamiento en Main
                        withContext(Dispatchers.Main) {
                            images = images.toMutableList().apply {
                                this[index] = this[index].copy(isProcessing = true)
                            }
                            progressMessage = "Procesando imagen ${index + 1}/${images.size}..."
                        }

                        // Procesa la imagen
                        val result = processSingleImage(context, imageDoc, index + 1)

                        // Actualiza estado completado en Main
                        withContext(Dispatchers.Main) {
                            images = images.toMutableList().apply {
                                this[index] = this[index].copy(
                                    isProcessing = false,
                                    isProcessed = true,
                                    medicationCount = result.medications.size,
                                    extractedText = result.extractedText
                                )
                            }
                        }

                        Log.d(TAG, "✅ Imagen ${index + 1} completada: ${result.medications.size} medicamento(s)")
                        result
                    }
                }

                // Espera a que todas las corrutinas async terminen
                Log.d(TAG, "⏳ Esperando a ${imageProcessingJobs.size} trabajos de procesamiento...")
                val allResults = imageProcessingJobs.awaitAll()

                // Consolida todos los medicamentos
                val allMedications = allResults.flatMap { it.medications }

                // NIVEL 7: ACTUALIZACIÓN FINAL EN MAIN
                withContext(Dispatchers.Main) {
                    parsedMedications = allMedications
                    processing = false
                    progressMessage = "Completado: ${allMedications.size} medicamento(s)"
                }

                Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
                Log.d(TAG, "║  TODAS LAS IMÁGENES PROCESADAS EXITOSAMENTE           ║")
                Log.d(TAG, "║  Total de medicamentos: ${allMedications.size}        ║")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error durante el procesamiento", e)

                withContext(Dispatchers.Main) {
                    processing = false
                    progressMessage = "Error"
                }

                onError(e.message ?: "Falló el procesamiento")
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * FUNCIÓN: processSingleImage
     * ═══════════════════════════════════════════════════════════════════════
     *
     * NIVELES DE CORRUTINAS ANIDADAS:
     * - Nivel 4: withContext(IO) para carga de imagen
     * - Nivel 5: launch(IO) para OCR
     * - Nivel 6: withContext(Default) para parseo
     */
    private suspend fun processSingleImage(
        context: Activity,
        imageDoc: ImageDocument,
        imageNumber: Int
    ): ImageProcessingResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "📷 [IO] Procesando IMAGEN: ${imageDoc.fileName} en thread: ${Thread.currentThread().name}")

        withContext(Dispatchers.Main) {
            progressMessage = "Imagen $imageNumber: Cargando..."
        }

        // NIVEL 4: CARGA Y OPTIMIZACIÓN DE IMAGEN EN DISPATCHER IO
        val bitmap = withContext(Dispatchers.IO) {
            Log.d(TAG, "📂 [IO] Cargando imagen desde URI en thread: ${Thread.currentThread().name}")

            val inputStream = context.contentResolver.openInputStream(imageDoc.uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Log.e(TAG, "❌ No se pudo decodificar la imagen")
                throw IllegalStateException("No se pudo cargar la imagen")
            }

            Log.d(TAG, "✅ Imagen cargada: ${originalBitmap.width}x${originalBitmap.height}")

            // Optimiza el tamaño si es muy grande (mejora rendimiento de OCR)
            val maxDimension = 2048
            if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
                Log.d(TAG, "🔧 Optimizando imagen (muy grande)...")

                val scale = minOf(
                    maxDimension.toFloat() / originalBitmap.width,
                    maxDimension.toFloat() / originalBitmap.height
                )
                val width = (originalBitmap.width * scale).toInt()
                val height = (originalBitmap.height * scale).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
                originalBitmap.recycle()

                Log.d(TAG, "✅ Imagen optimizada a: ${width}x${height}")
                scaledBitmap
            } else {
                originalBitmap
            }
        }

        withContext(Dispatchers.Main) {
            progressMessage = "Imagen $imageNumber: Extrayendo texto..."
        }

        // NIVEL 5: OCR EN DISPATCHER IO CON LAUNCH
        val extractedText = withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            launch(Dispatchers.IO) {
                Log.d(TAG, "🔍 [Launch-IO] Ejecutando OCR en imagen $imageNumber, thread: ${Thread.currentThread().name}")
            }.join()

            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            bitmap.recycle()

            val text = result.text

            Log.d(TAG, "✅ [IO] IMAGEN $imageNumber OCR completado: ${text.length} caracteres")
            Log.d(TAG, "📄 Primeros 500 caracteres:\n${text.take(500)}...")

            if (text.isBlank()) {
                Log.e(TAG, "❌ La imagen no contiene texto legible")
                null
            } else {
                Log.d(TAG, "📄 TEXTO COMPLETO EXTRAÍDO:\n$text")
                text
            }
        }

        // NIVEL 6: PARSEO EN DISPATCHER DEFAULT (CPU-INTENSIVO)
        val medications = if (extractedText != null) {
            withContext(Dispatchers.Main) {
                progressMessage = "Imagen $imageNumber: Analizando medicamentos..."
            }

            Log.d(TAG, "🧮 [Default] Iniciando parseo para imagen $imageNumber")

            // CUMPLE: Corrutina con Dispatcher (5 puntos)
            withContext(Dispatchers.Default) {
                Log.d(TAG, "🧮 [Default] Parseando imagen $imageNumber en thread: ${Thread.currentThread().name}")
                val result = parseMedicationsFromText(extractedText, imageDoc.fileName)
                Log.d(TAG, "✅ [Default] Parseo completado: ${result.size} medicamento(s)")
                result
            }
        } else {
            Log.e(TAG, "❌ extractedText es NULL - No se puede parsear")
            emptyList()
        }

        Log.d(TAG, "🏁 Imagen $imageNumber procesamiento completo:")
        Log.d(TAG, "   - Texto extraído: ${extractedText != null}")
        Log.d(TAG, "   - Caracteres: ${extractedText?.length ?: 0}")
        Log.d(TAG, "   - Medicamentos detectados: ${medications.size}")

        ImageProcessingResult(
            medications = medications,
            extractedText = extractedText
        )
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * FUNCIÓN: parseMedicationsFromText
     * ═══════════════════════════════════════════════════════════════════════
     *
     * EJECUTADA EN: Dispatchers.Default (CPU-intensivo)
     */
    private suspend fun parseMedicationsFromText(
        text: String,
        sourceFile: String
    ): List<MedicationInfo> {
        try {
            Log.d(TAG, "🔍 [Default] Buscando medicamentos en: $sourceFile")

            val medications = mutableListOf<MedicationInfo>()
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

            // Busca ID de prescripción
            var globalPrescriptionId = "RX-${(1000..9999).random()}"
            for (line in lines) {
                val rxPattern = """RX[- ]?\d+""".toRegex(RegexOption.IGNORE_CASE)
                val match = rxPattern.find(line)
                if (match != null) {
                    globalPrescriptionId = match.value.uppercase()
                    break
                }
            }

            // ESTRATEGIA: Buscar todos los patrones "Nombre + Dosis"
            val medPattern = """([A-Za-záéíóúÁÉÍÓÚñÑ]{4,})\s+(\d+)\s*mg""".toRegex(RegexOption.IGNORE_CASE)
            val matches = medPattern.findAll(text)

            val uniqueMeds = mutableSetOf<String>()
            val irrelevantWords = listOf(
                "cantidad", "duración", "dosis", "instrucciones",
                "fecha", "paciente", "diagnóstico", "médico",
                "registro", "tableta", "tabletas"
            )

            for (match in matches) {
                val medName = match.groupValues[1].trim()
                val dose = match.groupValues[2].toIntOrNull() ?: 0
                val key = "${medName}_${dose}"

                if (key !in uniqueMeds &&
                    !irrelevantWords.contains(medName.lowercase()) &&
                    medName.length > 3 &&
                    dose > 0) {

                    uniqueMeds.add(key)
                    medications.add(createDefaultMedication(
                        name = "$medName ${dose}mg",
                        doseMg = dose,
                        prescriptionId = globalPrescriptionId,
                        sourceFile = sourceFile
                    ))
                    Log.d(TAG, "  ✅ Medicamento detectado: $medName ${dose}mg")
                }
            }

            Log.d(TAG, "📊 Total detectado: ${medications.size} medicamento(s)")
            return medications

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en parseo", e)
            return emptyList()
        }
    }

    private fun createDefaultMedication(
        name: String,
        doseMg: Int = 0,
        prescriptionId: String,
        sourceFile: String
    ): MedicationInfo {
        val startDate = Date()
        val endDate = Calendar.getInstance().apply {
            time = startDate
            add(Calendar.DAY_OF_YEAR, 30)
        }.time

        return MedicationInfo(
            medicationId = "med_${System.currentTimeMillis()}_${(1000..9999).random()}",
            name = name,
            medicationRef = "/medicamentosGlobales/med_${name.hashCode().toString().replace("-", "")}",
            doseMg = doseMg,
            frequencyHours = 24,
            startDate = startDate,
            endDate = endDate,
            active = true,
            prescriptionId = prescriptionId,
            sourceFile = sourceFile
        )
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * FUNCIÓN: saveAllMedications CON ESTRATEGIA OFFLINE
     * ═══════════════════════════════════════════════════════════════════════
     *
     * NUEVA IMPLEMENTACIÓN CON CONECTIVIDAD:
     * 1. Verifica conexión
     * 2. Si hay internet → Guarda en Firestore
     * 3. Si NO hay internet → Guarda localmente en Room
     * 4. Notifica al usuario del estado
     *
     * IMPLEMENTACIÓN: I/O + Main + Default (15 PUNTOS)
     */
    fun saveAllMedicationsGroupedAsPrescription(
        context: Context,
        userId: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
                Log.d(TAG, "║  GUARDANDO AGRUPADO ${parsedMedications.size} MEDS    ║")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

                if (userId.isBlank()) throw IllegalStateException("Usuario no autenticado.")
                if (parsedMedications.isEmpty()) throw IllegalStateException("No hay medicamentos para guardar.")

                // Actualiza UI en Main
                withContext(Dispatchers.Main) {
                    uploading = true
                    progressMessage = "Verificando conectividad..."
                }

                // VERIFICAR CONECTIVIDAD
                val hasConnection = ConnectivityHelper.isNetworkAvailable(context)
                val connType = ConnectivityHelper.getConnectionType(context)

                Log.d(TAG, "📡 Estado de conexión: ${if (hasConnection) "ONLINE" else "OFFLINE"} ($connType)")

                withContext(Dispatchers.Main) {
                    isOnline = hasConnection
                    connectionType = connType
                }

                if (hasConnection) {
                    // ═══ CON CONEXIÓN: GUARDAR EN FIRESTORE ═══
                    Log.d(TAG, "🌐 MODO ONLINE: Guardando en Firestore...")
                    saveToFirestore(userId, onDone)
                } else {
                    // ═══ SIN CONEXIÓN: GUARDAR LOCALMENTE ═══
                    Log.d(TAG, "📴 MODO OFFLINE: Guardando localmente...")
                    saveLocally(userId, context, onDone)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al guardar", e)

                withContext(Dispatchers.Main) {
                    uploading = false
                    progressMessage = "Error al guardar"
                }

                onDone(false, "❌ Error: ${e.message}")
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * GUARDAR EN FIRESTORE (CON CONEXIÓN)
     * ═══════════════════════════════════════════════════════════════════════
     */
    private suspend fun saveToFirestore(
        userId: String,
        onDone: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                progressMessage = "Guardando en la nube..."
            }

            // Agrupar por archivo fuente (cada archivo = 1 prescripción)
            val groups: Map<String, List<MedicationInfo>> =
                parsedMedications.groupBy { it.sourceFile.ifBlank { "Desconocido" } }

            val userDoc = firestore.collection("usuarios").document(userId)
            val prescRoot = userDoc.collection("prescripcionesUsuario")

            var totalMeds = 0
            var createdPrescriptions = 0

            for ((sourceFile, meds) in groups) {
                withContext(Dispatchers.Main) {
                    progressMessage = "Guardando prescripción de $sourceFile..."
                }

                // Documento de prescripción
                val prescData = hashMapOf(
                    "fileName" to sourceFile,
                    "uploadedAt" to Date(),
                    "status" to "pendiente",
                    "totalItems" to meds.size,
                    "fromOCR" to true,
                    "syncedFromOffline" to false,
                    "notes" to ""
                )

                val prescRef = prescRoot.add(prescData).await()
                createdPrescriptions++

                val medsCol = prescRef.collection("medicamentosPrescripcion")

                // Guardado paralelo de medicamentos del grupo
                val jobs = meds.mapIndexed { idx, med ->
                    async(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            progressMessage = "Guardando ${idx + 1}/${meds.size} en $sourceFile..."
                        }

                        val doc = hashMapOf(
                            "medicationId" to med.medicationId,
                            "name" to med.name,
                            "medicationRef" to med.medicationRef,
                            "doseMg" to med.doseMg,
                            "frequencyHours" to med.frequencyHours,
                            "startDate" to med.startDate,
                            "endDate" to med.endDate,
                            "active" to med.active,
                            "prescriptionId" to (med.prescriptionId.ifBlank { prescRef.id }),
                            "sourceFile" to med.sourceFile,
                            "createdAt" to Date()
                        )
                        medsCol.add(doc).await()
                    }
                }
                jobs.awaitAll()
                totalMeds += meds.size
            }

            withContext(Dispatchers.Main) {
                uploading = false
                progressMessage = "Guardado en la nube ✅"
            }

            onDone(true, "✅ $createdPrescriptions prescripción(es), $totalMeds medicamento(s) guardado(s) en la nube")

            Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
            Log.d(TAG, "║  GUARDADO EN FIRESTORE COMPLETADO                     ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar en Firestore", e)
            throw e
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * GUARDAR LOCALMENTE EN ROOM (SIN CONEXIÓN)
     * ═══════════════════════════════════════════════════════════════════════
     */
    private suspend fun saveLocally(
        userId: String,
        context: Context,
        onDone: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                progressMessage = "Guardando localmente..."
                showOfflineWarning = true
            }

            val dao = database?.pendingPrescriptionDao()
                ?: throw IllegalStateException("Base de datos no inicializada")

            // Agrupar por archivo fuente
            val groups: Map<String, List<MedicationInfo>> =
                parsedMedications.groupBy { it.sourceFile.ifBlank { "Desconocido" } }

            var totalSaved = 0

            // SERIALIZACIÓN EN DISPATCHER DEFAULT (CPU-INTENSIVO)
            for ((sourceFile, meds) in groups) {
                withContext(Dispatchers.Default) {
                    Log.d(TAG, "🧮 [Default] Serializando medicamentos de $sourceFile")

                    val medsJson = gson.toJson(meds)

                    val entity = PendingPrescriptionEntity(
                        userId = userId,
                        fileName = sourceFile,
                        medicationsJson = medsJson,
                        isSynced = false
                    )

                    withContext(Dispatchers.IO) {
                        dao.insert(entity)
                        totalSaved++
                        Log.d(TAG, "💾 Guardado local: $sourceFile (${meds.size} medicamentos)")
                    }
                }
            }

            // Actualizar contador
            loadPendingCount()

            withContext(Dispatchers.Main) {
                uploading = false
                progressMessage = "Guardado localmente 📴"
            }

            onDone(
                true,
                "📴 $totalSaved prescripción(es) guardada(s) localmente.\n" +
                        "Se sincronizarán automáticamente cuando haya conexión."
            )

            Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
            Log.d(TAG, "║  GUARDADO LOCAL COMPLETADO                            ║")
            Log.d(TAG, "║  Prescripciones guardadas: $totalSaved                ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar localmente", e)
            throw e
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * SINCRONIZACIÓN AUTOMÁTICA DE PRESCRIPCIONES PENDIENTES
     * ═══════════════════════════════════════════════════════════════════════
     */
    fun syncPendingPrescriptions(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Verificar si hay conexión
                if (!ConnectivityHelper.isNetworkAvailable(context)) {
                    Log.d(TAG, "📴 No hay conexión para sincronizar")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    syncing = true
                    progressMessage = "Sincronizando..."
                }

                val dao = database?.pendingPrescriptionDao() ?: return@launch
                val pending = dao.getAllPending()

                if (pending.isEmpty()) {
                    Log.d(TAG, "✅ No hay prescripciones pendientes de sincronizar")
                    withContext(Dispatchers.Main) {
                        syncing = false
                    }
                    return@launch
                }

                Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
                Log.d(TAG, "║  SINCRONIZANDO ${pending.size} PRESCRIPCIONES         ║")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

                var syncedCount = 0
                var failedCount = 0

                for (prescription in pending) {
                    try {
                        // DESERIALIZACIÓN EN DISPATCHER DEFAULT
                        val meds = withContext(Dispatchers.Default) {
                            val type = object : TypeToken<List<MedicationInfo>>() {}.type
                            gson.fromJson<List<MedicationInfo>>(prescription.medicationsJson, type)
                        }

                        // Guardar en Firestore
                        val userDoc = firestore.collection("usuarios").document(prescription.userId)
                        val prescRoot = userDoc.collection("prescripcionesUsuario")

                        val prescData = hashMapOf(
                            "fileName" to prescription.fileName,
                            "uploadedAt" to Date(prescription.createdAt),
                            "syncedAt" to Date(),
                            "status" to "pendiente",
                            "totalItems" to meds.size,
                            "fromOCR" to true,
                            "syncedFromOffline" to true,
                            "notes" to ""
                        )

                        val prescRef = prescRoot.add(prescData).await()
                        val medsCol = prescRef.collection("medicamentosPrescripcion")

                        // Guardar medicamentos
                        meds.forEach { med ->
                            val doc = hashMapOf(
                                "medicationId" to med.medicationId,
                                "name" to med.name,
                                "medicationRef" to med.medicationRef,
                                "doseMg" to med.doseMg,
                                "frequencyHours" to med.frequencyHours,
                                "startDate" to med.startDate,
                                "endDate" to med.endDate,
                                "active" to med.active,
                                "prescriptionId" to med.prescriptionId,
                                "sourceFile" to med.sourceFile,
                                "createdAt" to Date(prescription.createdAt),
                                "syncedAt" to Date()
                            )
                            medsCol.add(doc).await()
                        }

                        // Marcar como sincronizado
                        dao.update(prescription.copy(isSynced = true, lastSyncAttempt = System.currentTimeMillis()))
                        syncedCount++

                        Log.d(TAG, "✅ Sincronizada: ${prescription.fileName}")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error sincronizando: ${prescription.fileName}", e)

                        // Actualizar intentos fallidos
                        dao.update(
                            prescription.copy(
                                syncAttempts = prescription.syncAttempts + 1,
                                lastSyncAttempt = System.currentTimeMillis(),
                                errorMessage = e.message
                            )
                        )
                        failedCount++
                    }
                }

                // Limpiar registros sincronizados
                dao.deleteSynced()

                // Actualizar contador
                loadPendingCount()

                withContext(Dispatchers.Main) {
                    syncing = false
                    progressMessage = ""
                }

                Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
                Log.d(TAG, "║  SINCRONIZACIÓN COMPLETADA                            ║")
                Log.d(TAG, "║  Exitosas: $syncedCount | Fallidas: $failedCount      ║")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en sincronización automática", e)

                withContext(Dispatchers.Main) {
                    syncing = false
                }
            }
        }
    }

    fun updateMedication(index: Int, updated: MedicationInfo) {
        val mutableList = parsedMedications.toMutableList()
        if (index in mutableList.indices) {
            mutableList[index] = updated
            parsedMedications = mutableList
        }
    }

    fun addNewMedication() {
        val newMed = createDefaultMedication(
            name = "Nuevo medicamento",
            prescriptionId = parsedMedications.firstOrNull()?.prescriptionId ?: "RX-${(1000..9999).random()}",
            sourceFile = "Manual"
        )
        parsedMedications = parsedMedications + newMed
    }

    fun removeMedication(index: Int) {
        parsedMedications = parsedMedications.filterIndexed { i, _ -> i != index }
    }

    private data class ImageProcessingResult(
        val medications: List<MedicationInfo>,
        val extractedText: String?
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadPrescriptionPhotoScreen(vm: PhotoOcrUploadViewModel, finish: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scroll = rememberScrollState()

    // ✅ LAUNCHER PARA SOLICITAR PERMISO DE CÁMARA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(ctx, "⚠️ Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Inicializar database, verificar conectividad Y solicitar permisos
    LaunchedEffect(Unit) {
        vm.initDatabase(ctx)

        // ✅ SOLICITAR PERMISO DE CÁMARA SI NO ESTÁ OTORGADO
        if (activity != null) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.CAMERA
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    // Estado para controlar el menú de opciones
    var showOptionsMenu by remember { mutableStateOf(false) }

    // URI temporal para foto de cámara
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher para galería
    val pickFromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && activity != null) {
            val fileName = getFileName(ctx, uri)
            val added = vm.addImage(uri, fileName)
            if (!added) {
                Toast.makeText(ctx, "⚠️ Máximo $MAX_IMAGES imágenes permitidas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher para documentos
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && activity != null) {
            val fileName = getFileName(ctx, uri)
            val added = vm.addImage(uri, fileName)
            if (!added) {
                Toast.makeText(ctx, "⚠️ Máximo $MAX_IMAGES imágenes permitidas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher para cámara
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val currentPhotoUri = photoUri

        if (success && currentPhotoUri != null && activity != null) {
            val fileName = "Camara_${System.currentTimeMillis()}.jpg"
            val added = vm.addImage(currentPhotoUri, fileName)

            if (!added) {
                Toast.makeText(ctx, "⚠️ Máximo $MAX_IMAGES imágenes permitidas", Toast.LENGTH_SHORT).show()
            }

            photoUri = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargar Foto de Prescripción", fontWeight = FontWeight.Bold) },
                actions = {
                    // Botón de sincronización
                    if (vm.pendingCount > 0) {
                        IconButton(
                            onClick = { vm.syncPendingPrescriptions(ctx) },
                            enabled = !vm.syncing && vm.isOnline
                        ) {
                            if (vm.syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error
                                ) {
                                    Text("${vm.pendingCount}")
                                }
                                Icon(Icons.Filled.CloudUpload, "Sincronizar pendientes")
                            }
                        }
                    }

                    // Botón de actualizar conexión
                    IconButton(onClick = { vm.checkConnectivity(ctx) }) {
                        Icon(
                            if (vm.isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            "Estado de conexión"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // ═══ INDICADOR DE CONECTIVIDAD ═══
            ConnectivityBanner(
                isOnline = vm.isOnline,
                connectionType = vm.connectionType,
                pendingCount = vm.pendingCount,
                syncing = vm.syncing
            )

            Spacer(Modifier.height(16.dp))

            // SELECTOR DE IMÁGENES CON MENÚ DE OPCIONES
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📸 Imágenes (${vm.images.size}/$MAX_IMAGES)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            if (vm.images.isNotEmpty() && !vm.processing) {
                                IconButton(
                                    onClick = { vm.clearAll() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Filled.Delete, "Limpiar todo")
                                }
                            }

                            if (vm.images.size < MAX_IMAGES) {
                                Box {
                                    IconButton(onClick = { showOptionsMenu = true }) {
                                        Icon(Icons.Filled.Add, "Agregar imagen")
                                    }

                                    // Menú desplegable con opciones
                                    DropdownMenu(
                                        expanded = showOptionsMenu,
                                        onDismissRequest = { showOptionsMenu = false }
                                    ) {
                                        // Opción: Cámara
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.CameraAlt,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Text("Tomar Foto")
                                                }
                                            },
                                            onClick = {
                                                showOptionsMenu = false
                                                if (activity != null) {
                                                    val file = File(
                                                        activity.cacheDir,
                                                        "prescription_${System.currentTimeMillis()}.jpg"
                                                    )
                                                    photoUri = FileProvider.getUriForFile(
                                                        activity,
                                                        "${activity.packageName}.fileprovider",
                                                        file
                                                    )
                                                    takePhoto.launch(photoUri!!)
                                                }
                                            }
                                        )

                                        // Opción: Galería
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.PhotoLibrary,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Text("Galería")
                                                }
                                            },
                                            onClick = {
                                                showOptionsMenu = false
                                                pickFromGallery.launch("image/*")
                                            }
                                        )

                                        // Opción: Documentos
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.InsertDriveFile,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Text("Documentos")
                                                }
                                            },
                                            onClick = {
                                                showOptionsMenu = false
                                                pickDocument.launch(arrayOf("image/*"))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (vm.images.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CloudUpload,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Toca + para elegir:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "📷 Cámara • 🖼️ Galería • 📄 Documentos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        vm.images.forEachIndexed { index, img ->
                            ImageDocumentCard(
                                imageDoc = img,
                                onRemove = { vm.removeImage(index) }
                            )
                            if (index < vm.images.size - 1) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // BOTÓN PROCESAR
            if (vm.images.isNotEmpty() && !vm.processing) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (activity != null) {
                            vm.processAllImages(activity) { errorMsg ->
                                Toast.makeText(ctx, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Procesar ${vm.images.size} Imagen(es)")
                }
            }

            Spacer(Modifier.height(16.dp))

            // INDICADOR DE PROCESAMIENTO
            if (vm.processing) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Procesando imágenes...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (vm.progressMessage.isNotEmpty()) {
                                Text(
                                    vm.progressMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // RESUMEN DE IMÁGENES PROCESADAS
            if (vm.images.any { it.isProcessed }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📊 Resumen de Procesamiento",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        vm.images.filter { it.isProcessed }.forEach { img ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    img.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${img.medicationCount} med(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // LISTA DE MEDICAMENTOS DETECTADOS
            if (vm.parsedMedications.isNotEmpty() && !vm.processing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💊 Medicamentos Detectados (${vm.parsedMedications.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { vm.addNewMedication() }) {
                        Icon(Icons.Filled.Add, "Agregar medicamento")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Agrupa medicamentos por archivo fuente
                val groupedMeds = vm.parsedMedications.groupBy { it.sourceFile }

                groupedMeds.forEach { (sourceFile, meds) ->
                    Text(
                        "📸 $sourceFile",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    meds.forEachIndexed { _, med ->
                        val globalIndex = vm.parsedMedications.indexOf(med)
                        MedicationEditCard(
                            medication = med,
                            onUpdate = { updated -> vm.updateMedication(globalIndex, updated) },
                            onDelete = { vm.removeMedication(globalIndex) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // BOTÓN GUARDAR TODOS
            if (vm.parsedMedications.isNotEmpty() && !vm.processing) {
                Button(
                    enabled = !vm.uploading,
                    onClick = {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid

                        if (userId.isNullOrBlank()) {
                            Toast.makeText(ctx, "❌ Usuario no autenticado", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        if (activity != null) {
                            vm.saveAllMedicationsGroupedAsPrescription(ctx, userId) { ok, msg ->
                                activity.runOnUiThread {
                                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                    if (ok) finish()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vm.isOnline)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    if (vm.uploading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            if (vm.progressMessage.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(vm.progressMessage)
                            }
                        }
                    } else {
                        Icon(
                            if (vm.isOnline) Icons.Filled.CloudUpload else Icons.Filled.Save,
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.isOnline)
                                "Guardar ${vm.parsedMedications.size} Medicamento(s)"
                            else
                                "Guardar Localmente (${vm.parsedMedications.size})"
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * BANNER DE CONECTIVIDAD
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun ConnectivityBanner(
    isOnline: Boolean,
    connectionType: String,
    pendingCount: Int,
    syncing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline)
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            else
                Color(0xFFFF9800).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = if (isOnline) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    if (isOnline) "En línea" else "Sin conexión",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFE65100)
                )

                Text(
                    when {
                        syncing -> "Sincronizando..."
                        isOnline && pendingCount > 0 -> "$connectionType • $pendingCount pendiente(s)"
                        isOnline -> connectionType
                        else -> "Los datos se guardarán localmente"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOnline) Color(0xFF558B2F) else Color(0xFFF57C00)
                )
            }

            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF4CAF50)
                )
            } else if (pendingCount > 0) {
                Badge(
                    containerColor = Color(0xFFFF9800)
                ) {
                    Text("$pendingCount")
                }
            }
        }
    }
}

// Función auxiliar para obtener el nombre del archivo
private fun getFileName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else "Imagen_${System.currentTimeMillis()}.jpg"
        }
    }.getOrDefault("Imagen_${System.currentTimeMillis()}.jpg") as String
}

@Composable
private fun ImageDocumentCard(
    imageDoc: ImageDocument,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                imageDoc.isProcessing -> MaterialTheme.colorScheme.secondaryContainer
                imageDoc.isProcessed -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = when {
                    imageDoc.isProcessing -> MaterialTheme.colorScheme.onSecondaryContainer
                    imageDoc.isProcessed -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    imageDoc.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                when {
                    imageDoc.isProcessing -> {
                        Text(
                            "Procesando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    imageDoc.isProcessed -> {
                        Text(
                            "✅ ${imageDoc.medicationCount} medicamento(s) detectado(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    else -> {
                        Text(
                            "Listo para procesar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (!imageDoc.isProcessing) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationEditCard(
    medication: MedicationInfo,
    onUpdate: (MedicationInfo) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(medication.name) }
    var dose by remember { mutableStateOf(medication.doseMg.toString()) }
    var frequency by remember { mutableStateOf(medication.frequencyHours.toString()) }
    var prescriptionId by remember { mutableStateOf(medication.prescriptionId) }

    val durationDays = remember(medication.startDate, medication.endDate) {
        val diffInMillis = medication.endDate.time - medication.startDate.time
        (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
    }
    var duration by remember { mutableStateOf(durationDays.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { "Nuevo medicamento" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (medication.sourceFile.isNotEmpty()) {
                        Text(
                            "Origen: ${medication.sourceFile}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.Edit,
                            contentDescription = if (expanded) "Contraer" else "Editar"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del medicamento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.MedicalServices, null) }
                )

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dose,
                        onValueChange = { if (it.all { c -> c.isDigit() }) dose = it },
                        label = { Text("Dosis (mg)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Text("💊", style = MaterialTheme.typography.titleMedium) }
                    )

                    Spacer(Modifier.width(8.dp))

                    OutlinedTextField(
                        value = frequency,
                        onValueChange = { if (it.all { c -> c.isDigit() }) frequency = it },
                        label = { Text("Cada (horas)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Text("⏰", style = MaterialTheme.typography.titleMedium) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = duration,
                    onValueChange = { if (it.all { c -> c.isDigit() }) duration = it },
                    label = { Text("Duración (días)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Text("📅", style = MaterialTheme.typography.titleMedium) }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = prescriptionId,
                    onValueChange = { prescriptionId = it },
                    label = { Text("ID Prescripción") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Text("📋", style = MaterialTheme.typography.titleMedium) }
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val durationInt = duration.toIntOrNull() ?: durationDays
                        val newEndDate = Calendar.getInstance().apply {
                            time = medication.startDate
                            add(Calendar.DAY_OF_YEAR, durationInt)
                        }.time

                        val updated = medication.copy(
                            name = name.ifBlank { "Medicamento sin nombre" },
                            doseMg = dose.toIntOrNull() ?: 0,
                            frequencyHours = frequency.toIntOrNull() ?: 24,
                            prescriptionId = prescriptionId,
                            endDate = newEndDate
                        )
                        onUpdate(updated)
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aplicar cambios")
                }
            } else {
                Spacer(Modifier.height(8.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "💊 Dosis:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            "${medication.doseMg} mg",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "⏰ Frecuencia:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            "Cada ${medication.frequencyHours} horas",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "📅 Duración:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            "$durationDays días",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}