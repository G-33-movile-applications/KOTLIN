package com.mobile.mymeds.views

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking
import java.util.Date
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// ════════════════════════════════════════════════════════════════════════
// MODELOS DOMINIO
// ════════════════════════════════════════════════════════════════════════

enum class ReminderRecurrence {
    ONCE,
    DAILY,
    WEEKLY,
    CUSTOM_DAYS;

    fun label(): String = when (this) {
        ONCE -> "Una vez"
        DAILY -> "Diario"
        WEEKLY -> "Semanal"
        CUSTOM_DAYS -> "Días personalizados"
    }
}

enum class DayOfWeek(val label: String, val calendarDay: Int) {
    MONDAY("Lun", Calendar.MONDAY),
    TUESDAY("Mar", Calendar.TUESDAY),
    WEDNESDAY("Mié", Calendar.WEDNESDAY),
    THURSDAY("Jue", Calendar.THURSDAY),
    FRIDAY("Vie", Calendar.FRIDAY),
    SATURDAY("Sáb", Calendar.SATURDAY),
    SUNDAY("Dom", Calendar.SUNDAY)
}

enum class SyncStatus {
    SYNCED,
    PENDING_SYNC,
    LOCAL_ONLY;

    fun label(): String = when (this) {
        SYNCED -> "Sincronizado"
        PENDING_SYNC -> "Pendiente"
        LOCAL_ONLY -> "Local"
    }

    fun icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
        SYNCED -> Icons.Filled.CloudDone
        PENDING_SYNC -> Icons.Filled.CloudQueue
        LOCAL_ONLY -> Icons.Filled.CloudOff
    }
}

data class MedicationReminder(
    val id: String = "",
    val medicationName: String = "",
    val medicationId: String? = null,
    val time: String = "",
    val recurrence: ReminderRecurrence = ReminderRecurrence.ONCE,
    val customDays: Set<DayOfWeek> = emptySet(),
    val isActive: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val serverId: String? = null,
    val lastModifiedMillis: Long = System.currentTimeMillis(),
    val notificationsEnabled: Boolean = true
)

data class AvailableMedication(
    val id: String,
    val name: String,
    val prescriptionId: String,
    val doseMg: Int = 0,
    val frequencyHours: Int = 0
)

// ════════════════════════════════════════════════════════════════════════
// 💾 DB LOCAL RELACIONAL (ROOM)
// ════════════════════════════════════════════════════════════════════════

@Entity(tableName = "medication_reminders")
data class MedicationReminderEntity(
    @PrimaryKey val id: String,
    val medicationName: String,
    val medicationId: String?,
    val time: String,
    val recurrence: String,
    val customDays: String,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val syncStatus: String,
    val serverId: String?,
    val lastModifiedMillis: Long,
    val notificationsEnabled: Boolean
)

fun MedicationReminderEntity.toDomain(): MedicationReminder {
    val days = if (customDays.isNotBlank()) {
        try {
            JSONArray(customDays).let { arr ->
                (0 until arr.length()).map {
                    DayOfWeek.valueOf(arr.getString(it))
                }.toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    } else {
        emptySet()
    }

    return MedicationReminder(
        id = id,
        medicationName = medicationName,
        medicationId = medicationId,
        time = time,
        recurrence = ReminderRecurrence.valueOf(recurrence),
        customDays = days,
        isActive = isActive,
        createdAtMillis = createdAtMillis,
        syncStatus = SyncStatus.valueOf(syncStatus),
        serverId = serverId,
        lastModifiedMillis = lastModifiedMillis,
        notificationsEnabled = notificationsEnabled
    )
}

fun MedicationReminder.toEntity(): MedicationReminderEntity {
    val daysJson = if (customDays.isNotEmpty()) {
        JSONArray(customDays.map { it.name }).toString()
    } else {
        ""
    }

    return MedicationReminderEntity(
        id = if (id.isBlank()) UUID.randomUUID().toString() else id,
        medicationName = medicationName,
        medicationId = medicationId,
        time = time,
        recurrence = recurrence.name,
        customDays = daysJson,
        isActive = isActive,
        createdAtMillis = createdAtMillis,
        syncStatus = syncStatus.name,
        serverId = serverId,
        lastModifiedMillis = lastModifiedMillis,
        notificationsEnabled = notificationsEnabled
    )
}

@Dao
interface MedicationRemindersDao {

    @Query("SELECT * FROM medication_reminders ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<MedicationReminderEntity>

    @Query("SELECT * FROM medication_reminders WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<MedicationReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: MedicationReminderEntity)

    @Update
    suspend fun update(reminder: MedicationReminderEntity)

    @Delete
    suspend fun delete(reminder: MedicationReminderEntity)

    @Query("UPDATE medication_reminders SET isActive = :isActive, syncStatus = :syncStatus, lastModifiedMillis = :lastModified WHERE id = :id")
    suspend fun updateActive(id: String, isActive: Boolean, syncStatus: String, lastModified: Long)

    @Query("UPDATE medication_reminders SET syncStatus = :status, serverId = :serverId WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, serverId: String?)
}

@Database(
    entities = [MedicationReminderEntity::class],
    version = 6,
    exportSchema = false
)
abstract class RemindersDatabase : RoomDatabase() {
    abstract fun remindersDao(): MedicationRemindersDao

    companion object {
        @Volatile
        private var INSTANCE: RemindersDatabase? = null

        fun getInstance(context: Context): RemindersDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RemindersDatabase::class.java,
                    "reminders_db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

// ════════════════════════════════════════════════════════════════════════
// 🔔 NOTIFICATION WORKER
// ════════════════════════════════════════════════════════════════════════

class ReminderNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val reminderId = inputData.getString("reminder_id") ?: return Result.failure()
        val medicationName = inputData.getString("medication_name") ?: "Medicamento"
        val time = inputData.getString("time") ?: ""

        showNotification(reminderId, medicationName, time)
        return Result.success()
    }

    private fun showNotification(reminderId: String, medicationName: String, time: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal de notificación (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "medication_reminders",
                "Recordatorios de Medicamentos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para tomar medicamentos"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir la app
        val intent = Intent(applicationContext, MedicationRemindersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Construir notificación
        val notification = NotificationCompat.Builder(applicationContext, "medication_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💊 Hora de tomar tu medicamento")
            .setContentText("$medicationName - $time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(reminderId.hashCode(), notification)
    }
}

// ════════════════════════════════════════════════════════════════════════
// 🌐 NETWORK CONNECTIVITY MONITOR
// ════════════════════════════════════════════════════════════════════════

class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            _isConnected.value = false
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            _isConnected.value = hasInternet
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isConnected.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// 💾 LRU CACHE
// ════════════════════════════════════════════════════════════════════════

class LruCacheManager(maxSize: Int = 50) {

    private val remindersCache = object : androidx.collection.LruCache<String, List<MedicationReminder>>(maxSize) {
        override fun sizeOf(key: String, value: List<MedicationReminder>): Int {
            return value.size
        }
    }

    private val remindersCacheTimestamp = mutableMapOf<String, Long>()
    private val CACHE_TTL = 5 * 60 * 1000L

    fun getReminders(userId: String): List<MedicationReminder>? {
        if (!isCacheValid(remindersCacheTimestamp[userId])) {
            remindersCache.remove(userId)
            remindersCacheTimestamp.remove(userId)
            return null
        }
        return remindersCache.get(userId)
    }

    fun putReminders(userId: String, reminders: List<MedicationReminder>) {
        remindersCache.put(userId, reminders)
        remindersCacheTimestamp[userId] = System.currentTimeMillis()
    }

    fun invalidateReminders(userId: String) {
        remindersCache.remove(userId)
        remindersCacheTimestamp.remove(userId)
    }

    private fun isCacheValid(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        return (System.currentTimeMillis() - timestamp) < CACHE_TTL
    }

    fun clearAll() {
        remindersCache.evictAll()
        remindersCacheTimestamp.clear()
    }

    fun getCacheStats(): CacheStats {
        return CacheStats(
            remindersCacheSize = remindersCache.size(),
            remindersCacheHitCount = remindersCache.hitCount(),
            remindersCacheMissCount = remindersCache.missCount()
        )
    }
}

data class CacheStats(
    val remindersCacheSize: Int,
    val remindersCacheHitCount: Int,
    val remindersCacheMissCount: Int
) {
    val remindersHitRate: Float
        get() = if (remindersCacheHitCount + remindersCacheMissCount > 0) {
            remindersCacheHitCount.toFloat() / (remindersCacheHitCount + remindersCacheMissCount)
        } else 0f
}
// ════════════════════════════════════════════════════════════════════════
// 🔥 FIREBASE REPOSITORY (NUEVA CLASE - AGREGAR AQUÍ)
// ════════════════════════════════════════════════════════════════════════

class FirebaseRemindersRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun syncReminderToFirebase(reminder: MedicationReminder): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid
                    ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

                val reminderId = reminder.serverId ?: reminder.id

                val reminderData = hashMapOf(
                    "medicamentoNombre" to reminder.medicationName,
                    "medicamentoId" to (reminder.medicationId ?: ""),
                    "hora" to reminder.time,
                    "recurrencia" to reminder.recurrence.name,
                    "diasPersonalizados" to reminder.customDays.map { it.name },
                    "activo" to reminder.isActive,
                    "notificacionesHabilitadas" to reminder.notificationsEnabled,
                    "creadoEn" to reminder.createdAtMillis,
                    "modificadoEn" to reminder.lastModifiedMillis,
                    "idLocal" to reminder.id
                )

                println("🔥 Sincronizando a Firebase: /usuarios/$userId/recordatoriosMedicamentos/$reminderId")

                firestore.collection("usuarios")
                    .document(userId)
                    .collection("recordatoriosMedicamentos")
                    .document(reminderId)
                    .set(reminderData)
                    .await()

                println("✅ Recordatorio sincronizado en Firebase con ID: $reminderId")
                Result.success(reminderId)
            } catch (e: Exception) {
                println("❌ Error sincronizando a Firebase: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun updateReminderInFirebase(reminder: MedicationReminder): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid
                    ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

                val reminderId = reminder.serverId ?: reminder.id

                val updates = hashMapOf<String, Any>(
                    "medicamentoNombre" to reminder.medicationName,
                    "medicamentoId" to (reminder.medicationId ?: ""),
                    "hora" to reminder.time,
                    "recurrencia" to reminder.recurrence.name,
                    "diasPersonalizados" to reminder.customDays.map { it.name },
                    "activo" to reminder.isActive,
                    "notificacionesHabilitadas" to reminder.notificationsEnabled,
                    "modificadoEn" to System.currentTimeMillis()
                )

                println("🔥 Actualizando en Firebase: /usuarios/$userId/recordatoriosMedicamentos/$reminderId")

                firestore.collection("usuarios")
                    .document(userId)
                    .collection("recordatoriosMedicamentos")
                    .document(reminderId)
                    .update(updates)
                    .await()

                println("✅ Recordatorio actualizado en Firebase")
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ Error actualizando en Firebase: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun getRemindersFromFirebase(): Result<List<MedicationReminder>> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid
                    ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

                println("🔥 Obteniendo recordatorios desde Firebase: /usuarios/$userId/recordatoriosMedicamentos")

                val snapshot = firestore.collection("usuarios")
                    .document(userId)
                    .collection("recordatoriosMedicamentos")
                    .get()
                    .await()

                val reminders = snapshot.documents.mapNotNull { doc ->
                    try {
                        val customDaysRaw = doc.get("diasPersonalizados") as? List<*>
                        val customDays = customDaysRaw?.mapNotNull { dayName ->
                            try {
                                DayOfWeek.valueOf(dayName.toString())
                            } catch (e: Exception) {
                                null
                            }
                        }?.toSet() ?: emptySet()

                        MedicationReminder(
                            id = doc.getString("idLocal") ?: doc.id,
                            serverId = doc.id,
                            medicationName = doc.getString("medicamentoNombre") ?: "Medicamento",
                            medicationId = doc.getString("medicamentoId"),
                            time = doc.getString("hora") ?: "08:00",
                            recurrence = try {
                                ReminderRecurrence.valueOf(
                                    doc.getString("recurrencia") ?: "DAILY"
                                )
                            } catch (e: Exception) {
                                ReminderRecurrence.DAILY
                            },
                            customDays = customDays,
                            isActive = doc.getBoolean("activo") ?: true,
                            createdAtMillis = doc.getLong("creadoEn") ?: System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED,
                            lastModifiedMillis = doc.getLong("modificadoEn") ?: System.currentTimeMillis(),
                            notificationsEnabled = doc.getBoolean("notificacionesHabilitadas") ?: true
                        )
                    } catch (e: Exception) {
                        println("⚠️ Error parseando documento ${doc.id}: ${e.message}")
                        null
                    }
                }

                println("✅ Obtenidos ${reminders.size} recordatorios desde Firebase")
                Result.success(reminders)
            } catch (e: Exception) {
                println("❌ Error obteniendo recordatorios desde Firebase: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun deleteReminderFromFirebase(reminderId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid
                    ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

                println("🔥 Eliminando de Firebase: /usuarios/$userId/recordatoriosMedicamentos/$reminderId")

                firestore.collection("usuarios")
                    .document(userId)
                    .collection("recordatoriosMedicamentos")
                    .document(reminderId)
                    .delete()
                    .await()

                println("✅ Recordatorio eliminado de Firebase")
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ Error eliminando de Firebase: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}
// ════════════════════════════════════════════════════════════════════════
// 🌐 API SERVICE
// ════════════════════════════════════════════════════════════════════════

class MedicationApiService(
    private val baseUrl: String = "https://api.example.com",
    private val lruCache: LruCacheManager = LruCacheManager()
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getRemindersFromServer(userId: String, forceRefresh: Boolean = false): Result<List<MedicationReminder>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh) {
                    val cachedReminders = lruCache.getReminders(userId)
                    if (cachedReminders != null) {
                        println("✅ LRU Cache HIT: Recordatorios obtenidos del cache para userId=$userId")
                        return@withContext Result.success(cachedReminders)
                    }
                    println("❌ LRU Cache MISS: Consultando servidor para userId=$userId")
                }

                val request = Request.Builder()
                    .url("$baseUrl/usuarios/$userId/recordatorios")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error: ${response.code}"))
                }

                val json = JSONObject(response.body?.string() ?: "{}")
                val remindersArray = json.optJSONArray("recordatorios") ?: JSONArray()

                val reminders = mutableListOf<MedicationReminder>()
                for (i in 0 until remindersArray.length()) {
                    val rem = remindersArray.getJSONObject(i)

                    val customDaysArray = rem.optJSONArray("diasPersonalizados") ?: JSONArray()
                    val customDays = mutableSetOf<DayOfWeek>()
                    for (j in 0 until customDaysArray.length()) {
                        try {
                            customDays.add(DayOfWeek.valueOf(customDaysArray.getString(j)))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    reminders.add(
                        MedicationReminder(
                            id = UUID.randomUUID().toString(),
                            serverId = rem.getString("id"),
                            medicationName = rem.getString("medicamentoNombre"),
                            medicationId = rem.optString("medicamentoId", null),
                            time = rem.getString("hora"),
                            recurrence = try {
                                ReminderRecurrence.valueOf(rem.getString("recurrencia"))
                            } catch (e: Exception) {
                                ReminderRecurrence.DAILY
                            },
                            customDays = customDays,
                            isActive = rem.optBoolean("activo", true),
                            createdAtMillis = rem.optLong("creadoEn", System.currentTimeMillis()),
                            syncStatus = SyncStatus.SYNCED,
                            lastModifiedMillis = rem.optLong("modificadoEn", System.currentTimeMillis()),
                            notificationsEnabled = rem.optBoolean("notificacionesHabilitadas", true)
                        )
                    )
                }

                lruCache.putReminders(userId, reminders)
                println("💾 LRU Cache: Guardados ${reminders.size} recordatorios para userId=$userId")

                Result.success(reminders)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun syncReminder(reminder: MedicationReminder, userId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("medicamentoNombre", reminder.medicationName)
                    put("medicamentoId", reminder.medicationId ?: "")
                    put("hora", reminder.time)
                    put("recurrencia", reminder.recurrence.name)
                    put("diasPersonalizados", JSONArray(reminder.customDays.map { it.name }))
                    put("activo", reminder.isActive)
                    put("notificacionesHabilitadas", reminder.notificationsEnabled)
                }

                val requestBody = json.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/usuarios/$userId/recordatorios")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error al sincronizar: ${response.code}"))
                }

                val responseJson = JSONObject(response.body?.string() ?: "{}")
                val serverId = responseJson.optString("id", UUID.randomUUID().toString())

                Result.success(serverId)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun updateReminder(reminder: MedicationReminder, userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("medicamentoNombre", reminder.medicationName)
                    put("medicamentoId", reminder.medicationId ?: "")
                    put("hora", reminder.time)
                    put("recurrencia", reminder.recurrence.name)
                    put("diasPersonalizados", JSONArray(reminder.customDays.map { it.name }))
                    put("activo", reminder.isActive)
                    put("notificacionesHabilitadas", reminder.notificationsEnabled)
                }

                val requestBody = json.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/usuarios/$userId/recordatorios/${reminder.serverId}")
                    .put(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error al actualizar: ${response.code}"))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// ⚙️ GLOBAL SETTINGS (DATASTORE)
// ════════════════════════════════════════════════════════════════════════

private val Context.reminderSettingsDataStore by preferencesDataStore(
    name = "reminder_settings"
)

class ReminderSettingsManager(private val context: Context) {

    private val KEY_GLOBAL_ENABLED = booleanPreferencesKey("global_enabled")
    private val KEY_USER_ID = stringPreferencesKey("user_id")
    private val KEY_DND_START = stringPreferencesKey("dnd_start")
    private val KEY_DND_END = stringPreferencesKey("dnd_end")
    private val KEY_DEFAULT_SOUND = stringPreferencesKey("default_sound")

    val globalEnabledFlow: Flow<Boolean> =
        context.reminderSettingsDataStore.data.map { prefs ->
            prefs[KEY_GLOBAL_ENABLED] ?: true
        }

    suspend fun setGlobalEnabled(enabled: Boolean) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[KEY_GLOBAL_ENABLED] = enabled
        }
    }

    suspend fun setUserId(userId: String) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
        }
    }

    suspend fun getUserId(): String? {
        val prefs = context.reminderSettingsDataStore.data.first()
        return prefs[KEY_USER_ID]
    }

    suspend fun setDoNotDisturb(start: String, end: String) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[KEY_DND_START] = start
            prefs[KEY_DND_END] = end
        }
    }

    suspend fun getDoNotDisturb(): Pair<String?, String?> {
        val prefs = context.reminderSettingsDataStore.data.first()
        return prefs[KEY_DND_START] to prefs[KEY_DND_END]
    }

    suspend fun setDefaultSound(soundId: String) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_SOUND] = soundId
        }
    }

    val defaultSoundFlow: Flow<String?> =
        context.reminderSettingsDataStore.data.map { it[KEY_DEFAULT_SOUND] }
}
// ════════════════════════════════════════════════════════════════════════
// 💾 REPOSITORIO DE RECORDATORIOS (Con logging mejorado)
// ════════════════════════════════════════════════════════════════════════

class MedicationReminderRepository(
    private val context: Context,
    private val db: RemindersDatabase = RemindersDatabase.getInstance(context),
    private val settingsManager: ReminderSettingsManager = ReminderSettingsManager(context),
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "reminders_kv_store",
        Context.MODE_PRIVATE
    ),
    private val lruCache: LruCacheManager = LruCacheManager(),
    private val apiService: MedicationApiService = MedicationApiService(lruCache = lruCache),
    private val networkMonitor: NetworkMonitor = NetworkMonitor(context),
    private val firebaseRepo: FirebaseRemindersRepository = FirebaseRemindersRepository(), // ← AGREGAR ESTA LÍNEA
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private val dao: MedicationRemindersDao = db.remindersDao()
    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    // ──────────────────────── OBTENER MEDICAMENTOS DE PRESCRIPCIONES ─────────────────────────

    suspend fun getAvailableMedicationsFromPrescriptions(): Result<List<AvailableMedication>> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid
                    ?: return@withContext Result.failure(Exception("Usuario no autenticado"))

                println("🔍 Buscando medicamentos para usuario: $userId")

                val medications = mutableListOf<AvailableMedication>()

                // Obtener todas las prescripciones activas del usuario
                val prescriptionsSnapshot = firestore.collection("usuarios")
                    .document(userId)
                    .collection("prescripcionesUsuario")
                    .whereEqualTo("activa", true)
                    .get()
                    .await()

                println("📋 Encontradas ${prescriptionsSnapshot.documents.size} prescripciones activas")

                // Para cada prescripción, obtener sus medicamentos
                for (prescriptionDoc in prescriptionsSnapshot.documents) {
                    val prescriptionId = prescriptionDoc.id

                    val medicationsSnapshot = prescriptionDoc.reference
                        .collection("medicamentosPrescripcion")
                        .whereEqualTo("active", true)
                        .get()
                        .await()

                    println("💊 Prescripción $prescriptionId: ${medicationsSnapshot.documents.size} medicamentos")

                    for (medDoc in medicationsSnapshot.documents) {
                        val medication = AvailableMedication(
                            id = medDoc.id,
                            name = medDoc.getString("name") ?: "Medicamento",
                            prescriptionId = prescriptionId,
                            doseMg = medDoc.getLong("doseMg")?.toInt() ?: 0,
                            frequencyHours = medDoc.getLong("frequencyHours")?.toInt() ?: 0
                        )
                        medications.add(medication)
                        println("  ✓ ${medication.name} (${medication.doseMg}mg, cada ${medication.frequencyHours}h)")
                    }
                }

                println("✅ Total de medicamentos disponibles: ${medications.size}")
                Result.success(medications)
            } catch (e: Exception) {
                println("❌ Error obteniendo medicamentos: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // ──────────────────────── CRUD LOCAL (ROOM) ─────────────────────────

    suspend fun getReminders(): List<MedicationReminder> {
        return withContext(Dispatchers.IO) {
            val reminders = dao.getAll().map { it.toDomain() }
            println("📱 Recordatorios locales: ${reminders.size}")
            reminders
        }
    }

    suspend fun getRemindersFromServer(forceRefresh: Boolean = false): Result<List<MedicationReminder>> {
        return firebaseRepo.getRemindersFromFirebase()
    }

    suspend fun syncRemindersFromServer(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConnected.value) {
                    println("❌ Sin conexión a internet")
                    return@withContext Result.failure(Exception("Sin conexión"))
                }

                println("🔄 Iniciando sincronización desde Firebase...")

                val result = firebaseRepo.getRemindersFromFirebase()

                if (result.isFailure) {
                    println("❌ Error al obtener recordatorios de Firebase")
                    return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Error al obtener recordatorios"))
                }

                val serverReminders = result.getOrNull() ?: emptyList()
                println("📥 Recibidos ${serverReminders.size} recordatorios de Firebase")

                serverReminders.forEach { reminder ->
                    dao.upsert(reminder.toEntity())
                    println("  ✓ Sincronizado: ${reminder.medicationName}")
                }

                println("✅ Sincronización completada: ${serverReminders.size} recordatorios")
                Result.success(serverReminders.size)
            } catch (e: Exception) {
                println("❌ Error en sincronización: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // ──────────────────────── GESTIÓN DE CACHE LRU ─────────────────────────

    fun getCacheStats(): CacheStats {
        return lruCache.getCacheStats()
    }

    fun clearCache() {
        lruCache.clearAll()
        println("🗑️ LRU Cache: Cache limpiado completamente")
    }

    fun invalidateRemindersCache() {
        val userId = runBlocking { settingsManager.getUserId() }
        userId?.let {
            lruCache.invalidateReminders(it)
            println("🗑️ LRU Cache: Cache de recordatorios invalidado para userId=$it")
        }
    }

    // ──────────────────────── CRUD RECORDATORIOS ─────────────────────────

    suspend fun createReminder(
        medicationName: String,
        medicationId: String?,
        time: String,
        recurrence: ReminderRecurrence,
        customDays: Set<DayOfWeek>,
        notificationsEnabled: Boolean
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val reminderId = UUID.randomUUID().toString()
                val domain = MedicationReminder(
                    id = reminderId,
                    medicationName = medicationName,
                    medicationId = medicationId,
                    time = time,
                    recurrence = recurrence,
                    customDays = customDays,
                    isActive = true,
                    createdAtMillis = System.currentTimeMillis(),
                    syncStatus = if (isConnected.value) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY,
                    serverId = null,
                    lastModifiedMillis = System.currentTimeMillis(),
                    notificationsEnabled = notificationsEnabled
                )

                println("➕ Creando recordatorio: $medicationName a las $time")
                dao.upsert(domain.toEntity())

                // Programar notificaciones
                if (notificationsEnabled) {
                    println("🔔 Programando notificaciones...")
                    scheduleNotifications(domain)
                }

                // Intentar sincronizar inmediatamente si hay conexión
                if (isConnected.value) {
                    println("🔥 Sincronizando con Firebase...")
                    trySyncReminderToFirebase(domain)
                } else {
                    println("📴 Sin conexión - recordatorio marcado como LOCAL_ONLY")
                }

                println("✅ Recordatorio creado exitosamente: $reminderId")
                Result.success(reminderId)
            } catch (e: Exception) {
                println("❌ Error creando recordatorio: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun setReminderActive(reminderId: String, active: Boolean) {
        withContext(Dispatchers.IO) {
            val syncStatus = if (isConnected.value) SyncStatus.PENDING_SYNC.name else SyncStatus.LOCAL_ONLY.name
            dao.updateActive(reminderId, active, syncStatus, System.currentTimeMillis())

            println("🔄 Recordatorio $reminderId ${if (active) "activado" else "desactivado"}")

            if (active) {
                setLastActiveReminderId(reminderId)
            }

            // Cancelar o reprogramar notificaciones
            if (active) {
                val reminder = dao.getAll().find { it.id == reminderId }?.toDomain()
                reminder?.let {
                    if (it.notificationsEnabled) {
                        println("🔔 Reprogramando notificaciones...")
                        scheduleNotifications(it)
                    }
                }
            } else {
                println("🔕 Cancelando notificaciones...")
                cancelNotifications(reminderId)
            }

            if (isConnected.value) {
                syncPendingReminders()
            }

        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val reminder = dao.getAll().find { it.id == reminderId }?.toDomain()

                reminder?.let {
                    dao.delete(it.toEntity())
                    cancelNotifications(reminderId)
                    println("✅ Recordatorio eliminado localmente")

                    if (isConnected.value && it.serverId != null) {
                        val result = firebaseRepo.deleteReminderFromFirebase(it.serverId!!)
                        if (result.isSuccess) {
                            println("✅ Recordatorio eliminado de Firebase")
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ Error eliminando recordatorio: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // ────────────────────── Key/Value Store (SharedPreferences) ─────────

    fun setLastActiveReminderId(id: String) {
        prefs.edit().putString("last_active_reminder_id", id).apply()
    }

    fun getLastActiveReminderId(): String? =
        prefs.getString("last_active_reminder_id", null)

    // ──────────────────────── SINCRONIZACIÓN ─────────────────────────

    private suspend fun trySyncReminderToFirebase(reminder: MedicationReminder) {
        println("🔥 Intentando sincronizar con Firebase...")

        val result = firebaseRepo.syncReminderToFirebase(reminder)

        if (result.isSuccess) {
            val serverId = result.getOrNull()!!
            dao.updateSyncStatus(reminder.id, SyncStatus.SYNCED.name, serverId)
            invalidateRemindersCache()
            println("✅ Recordatorio sincronizado - serverId: $serverId")
        } else {
            println("❌ Error sincronizando con Firebase: ${result.exceptionOrNull()?.message}")
        }
    }

    private suspend fun tryUpdateReminderInFirebase(reminder: MedicationReminder) {
        if (reminder.serverId == null) {
            trySyncReminderToFirebase(reminder)
            return
        }

        println("🔥 Actualizando recordatorio en Firebase...")

        val result = firebaseRepo.updateReminderInFirebase(reminder)

        if (result.isSuccess) {
            dao.updateSyncStatus(reminder.id, SyncStatus.SYNCED.name, reminder.serverId)
            println("✅ Recordatorio actualizado en Firebase")
        } else {
            println("❌ Error actualizando en Firebase: ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun syncPendingReminders(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConnected.value) {
                    println("❌ Sin conexión para sincronizar pendientes")
                    return@withContext Result.failure(Exception("Sin conexión"))
                }

                val pending = dao.getPendingSync().map { it.toDomain() }
                println("🔥 Sincronizando ${pending.size} recordatorios pendientes con Firebase...")

                var syncedCount = 0

                pending.forEach { reminder ->
                    try {
                        if (reminder.serverId == null) {
                            println("  ➕ Creando en Firebase: ${reminder.medicationName}")
                            val result = firebaseRepo.syncReminderToFirebase(reminder)
                            if (result.isSuccess) {
                                val serverId = result.getOrNull()!!
                                dao.updateSyncStatus(reminder.id, SyncStatus.SYNCED.name, serverId)
                                syncedCount++
                                println("    ✓ Creado con serverId: $serverId")
                            }
                        } else {
                            println("  🔄 Actualizando en Firebase: ${reminder.medicationName}")
                            val result = firebaseRepo.updateReminderInFirebase(reminder)
                            if (result.isSuccess) {
                                dao.updateSyncStatus(reminder.id, SyncStatus.SYNCED.name, reminder.serverId)
                                syncedCount++
                                println("    ✓ Actualizado")
                            }
                        }
                    } catch (e: Exception) {
                        println("    ❌ Error: ${e.message}")
                        e.printStackTrace()
                    }
                }

                if (syncedCount > 0) {
                    invalidateRemindersCache()
                }

                println("✅ Sincronización de pendientes completada: $syncedCount/${pending.size}")
                Result.success(syncedCount)
            } catch (e: Exception) {
                println("❌ Error en sincronización de pendientes: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    // ──────────────────────── NOTIFICACIONES ─────────────────────────

    private fun scheduleNotifications(reminder: MedicationReminder) {
        val workManager = WorkManager.getInstance(context)

        // Cancelar notificaciones existentes para este recordatorio
        cancelNotifications(reminder.id)

        // Parsear hora
        val timeParts = reminder.time.split(":")
        if (timeParts.size != 2) return

        val hour = timeParts[0].toIntOrNull() ?: return
        val minute = timeParts[1].toIntOrNull() ?: return

        when (reminder.recurrence) {
            ReminderRecurrence.ONCE -> {
                scheduleOneTimeNotification(reminder, hour, minute)
            }
            ReminderRecurrence.DAILY -> {
                scheduleDailyNotification(reminder, hour, minute)
            }
            ReminderRecurrence.WEEKLY -> {
                scheduleWeeklyNotification(reminder, hour, minute)
            }
            ReminderRecurrence.CUSTOM_DAYS -> {
                scheduleCustomDaysNotification(reminder, hour, minute)
            }
        }

        println("✅ Notificaciones programadas para ${reminder.medicationName}")
    }

    private fun scheduleOneTimeNotification(reminder: MedicationReminder, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val delay = calendar.timeInMillis - System.currentTimeMillis()

        val workRequest = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "reminder_id" to reminder.id,
                    "medication_name" to reminder.medicationName,
                    "time" to reminder.time
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    private fun scheduleDailyNotification(reminder: MedicationReminder, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val delay = calendar.timeInMillis - System.currentTimeMillis()

        val workRequest = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "reminder_id" to reminder.id,
                    "medication_name" to reminder.medicationName,
                    "time" to reminder.time
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "reminder_${reminder.id}",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun scheduleWeeklyNotification(reminder: MedicationReminder, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val delay = calendar.timeInMillis - System.currentTimeMillis()

        val workRequest = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "reminder_id" to reminder.id,
                    "medication_name" to reminder.medicationName,
                    "time" to reminder.time
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "reminder_${reminder.id}",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun scheduleCustomDaysNotification(reminder: MedicationReminder, hour: Int, minute: Int) {
        reminder.customDays.forEach { day ->
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.DAY_OF_WEEK, day.calendarDay)

                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            val delay = calendar.timeInMillis - System.currentTimeMillis()

            val workRequest = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(
                7, TimeUnit.DAYS
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "reminder_id" to reminder.id,
                        "medication_name" to reminder.medicationName,
                        "time" to reminder.time
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "reminder_${reminder.id}_${day.name}",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    private fun cancelNotifications(reminderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$reminderId")

        DayOfWeek.values().forEach { day ->
            WorkManager.getInstance(context).cancelUniqueWork("reminder_${reminderId}_${day.name}")
        }
    }

    // ───────────────────── Archivos Locales: backup JSON ─────────────────

    suspend fun exportRemindersToFile(): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val current = getReminders()
                val json = buildString {
                    append("[")
                    current.forEachIndexed { index, r ->
                        append("{")
                        append("\"id\":\"${r.id}\",")
                        append("\"name\":\"${r.medicationName}\",")
                        append("\"medicationId\":\"${r.medicationId ?: ""}\",")
                        append("\"time\":\"${r.time}\",")
                        append("\"recurrence\":\"${r.recurrence.name}\",")
                        append("\"customDays\":[${r.customDays.joinToString(",") { "\"${it.name}\"" }}],")
                        append("\"isActive\":${r.isActive},")
                        append("\"syncStatus\":\"${r.syncStatus.name}\",")
                        append("\"notificationsEnabled\":${r.notificationsEnabled}")
                        append("}")
                        if (index != current.lastIndex) append(",")
                    }
                    append("]")
                }

                val file = File(context.filesDir, "reminders_backup.json")
                file.writeText(json)
                println("✅ Backup creado: ${file.absolutePath}")
                Result.success(file)
            } catch (e: IOException) {
                println("❌ Error creando backup: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    fun cleanup() {
        networkMonitor.unregister()
    }
}

// ════════════════════════════════════════════════════════════════════════
// UI STATE
// ════════════════════════════════════════════════════════════════════════

sealed class RemindersUiState {
    object Loading : RemindersUiState()
    data class Success(val reminders: List<MedicationReminder>) : RemindersUiState()
    data class Error(val message: String) : RemindersUiState()
}

// ════════════════════════════════════════════════════════════════════════
// VIEWMODEL (Actualizado con sincronización automática)
// ════════════════════════════════════════════════════════════════════════

class MedicationRemindersViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = MedicationReminderRepository(application.applicationContext)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = mutableStateOf<RemindersUiState>(RemindersUiState.Loading)
    val uiState: State<RemindersUiState> get() = _uiState

    val isConnected: StateFlow<Boolean> = repository.isConnected

    private val _cacheStats = mutableStateOf<CacheStats?>(null)
    val cacheStats: State<CacheStats?> get() = _cacheStats

    private val _availableMedications = mutableStateOf<List<AvailableMedication>>(emptyList())
    val availableMedications: State<List<AvailableMedication>> get() = _availableMedications

    init {
        // Inicializar userId para sincronización
        initializeUserId()

        loadReminders()
        loadAvailableMedications()
        syncRemindersFromServer()

        viewModelScope.launch {
            isConnected.collect { connected ->
                if (connected) {
                    syncPendingReminders()
                    syncRemindersFromServer()
                }
            }
        }
    }

    private fun initializeUserId() {
        viewModelScope.launch {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                val settingsManager = ReminderSettingsManager(getApplication())
                val storedUserId = settingsManager.getUserId()

                // Solo actualizar si cambió o no existe
                if (storedUserId != currentUserId) {
                    settingsManager.setUserId(currentUserId)
                    println("✅ UserId inicializado para sincronización: $currentUserId")
                }
            } else {
                println("⚠️ Usuario no autenticado - sincronización deshabilitada")
            }
        }
    }

    fun loadReminders() {
        viewModelScope.launch {
            try {
                _uiState.value = RemindersUiState.Loading
                val list = repository.getReminders()
                _uiState.value = RemindersUiState.Success(list)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value =
                    RemindersUiState.Error(e.message ?: "Error al cargar recordatorios")
            }
        }
    }

    fun loadAvailableMedications() {
        viewModelScope.launch {
            val result = repository.getAvailableMedicationsFromPrescriptions()
            if (result.isSuccess) {
                _availableMedications.value = result.getOrNull() ?: emptyList()
                println("✅ Cargados ${_availableMedications.value.size} medicamentos de prescripciones")
            } else {
                println("❌ Error al cargar medicamentos: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun syncRemindersFromServer() {
        viewModelScope.launch {
            if (!isConnected.value) {
                println("⚠️ Sin conexión - sincronización omitida")
                return@launch
            }

            val result = repository.syncRemindersFromServer()
            if (result.isSuccess) {
                loadReminders()
                val count = result.getOrNull() ?: 0
                println("✅ Sincronizados $count recordatorios desde el servidor")
            } else {
                println("❌ Error en sincronización: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun updateCacheStats() {
        _cacheStats.value = repository.getCacheStats()
    }

    fun clearCache() {
        repository.clearCache()
        updateCacheStats()
    }

    fun refresh(forceRefresh: Boolean = false) {
        loadReminders()
        loadAvailableMedications()
        if (isConnected.value) {
            syncRemindersFromServer()
        }
        updateCacheStats()
    }

    fun createReminder(
        medicationName: String,
        medicationId: String?,
        time: String,
        recurrence: ReminderRecurrence,
        customDays: Set<DayOfWeek>,
        notificationsEnabled: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createReminder(
                medicationName,
                medicationId,
                time,
                recurrence,
                customDays,
                notificationsEnabled
            )
            if (result.isSuccess) {
                loadReminders()
                println("✅ Recordatorio creado y programado para sincronización")
                onResult(true, null)
            } else {
                result.exceptionOrNull()?.printStackTrace()
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun toggleReminder(
        reminder: MedicationReminder,
        onError: (String) -> Unit
    ) {
        val currentState = _uiState.value
        if (currentState !is RemindersUiState.Success) return

        val updatedList = currentState.reminders.map {
            if (it.id == reminder.id) it.copy(isActive = !reminder.isActive) else it
        }
        _uiState.value = RemindersUiState.Success(updatedList)

        viewModelScope.launch {
            try {
                repository.setReminderActive(reminder.id, !reminder.isActive)
                loadReminders()
                println("✅ Estado del recordatorio actualizado")
            } catch (e: Exception) {
                e.printStackTrace()
                val revertedList = updatedList.map {
                    if (it.id == reminder.id) it.copy(isActive = reminder.isActive) else it
                }
                _uiState.value = RemindersUiState.Success(revertedList)
                onError(e.message ?: "No se pudo actualizar el recordatorio")
            }
        }
    }

    fun syncPendingReminders() {
        viewModelScope.launch {
            val result = repository.syncPendingReminders()
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                println("✅ $count recordatorios pendientes sincronizados")
                loadReminders()
            }
        }
    }

    fun deleteReminder(
        reminder: MedicationReminder,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteReminder(reminder.id)
            if (result.isSuccess) {
                loadReminders()
                onResult(true, null)
            } else {
                result.exceptionOrNull()?.printStackTrace()
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}

// ════════════════════════════════════════════════════════════════════════
// ACTIVITY (Actualizada con mejor feedback de sincronización)
// ════════════════════════════════════════════════════════════════════════

class MedicationRemindersActivity : ComponentActivity() {

    private val vm: MedicationRemindersViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val uiState by vm.uiState
            val isConnected by vm.isConnected.collectAsState()
            var showCreateDialog by remember { mutableStateOf(false) }
            var showCacheStats by remember { mutableStateOf(false) }

            // Estado de sincronización
            var isSyncing by remember { mutableStateOf(false) }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6B9BD8),
                    surface = Color.White
                )
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Recordatorios",
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    ConnectivityBadge(isConnected = isConnected)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.Filled.ArrowBack,
                                        contentDescription = "Atrás",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                if (!isConnected && uiState is RemindersUiState.Success) {
                                    val pendingCount = (uiState as RemindersUiState.Success)
                                        .reminders.count { it.syncStatus != SyncStatus.SYNCED }
                                    if (pendingCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFFFF9800)
                                        ) {
                                            Text(
                                                "$pendingCount",
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    vm.updateCacheStats()
                                    showCacheStats = true
                                }) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = "Estadísticas cache",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isSyncing = true
                                        vm.refresh(forceRefresh = true)
                                        scope.launch {
                                            kotlinx.coroutines.delay(2000)
                                            isSyncing = false
                                            snackbarHostState.showSnackbar("Sincronización completa")
                                        }
                                    }
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Refrescar",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF6B9BD8)
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = Color(0xFF6B9BD8),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Agregar recordatorio")
                        }
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { paddingValues ->
                    Box(
                        Modifier
                            .padding(paddingValues)
                            .fillMaxSize()
                            .background(Color(0xFFF5F5F5))
                    ) {
                        when (uiState) {
                            is RemindersUiState.Loading -> {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Cargando recordatorios...",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            is RemindersUiState.Error -> {
                                val msg = (uiState as RemindersUiState.Error).message
                                ErrorRemindersView(
                                    message = msg,
                                    onRetry = { vm.loadReminders() }
                                )
                            }

                            is RemindersUiState.Success -> {
                                val reminders =
                                    (uiState as RemindersUiState.Success).reminders
                                if (reminders.isEmpty()) {
                                    EmptyRemindersView()
                                } else {
                                    RemindersList(
                                        reminders = reminders,
                                        onToggle = { reminder ->
                                            vm.toggleReminder(reminder) { errorMsg ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(errorMsg)
                                                }
                                            }
                                        },
                                        onEdit = { _ ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Edición de recordatorios próximamente")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dialogo de creación
            if (showCreateDialog) {
                val medications by vm.availableMedications

                if (medications.isEmpty()) {
                    // Mostrar mensaje si no hay medicamentos
                    AlertDialog(
                        onDismissRequest = { showCreateDialog = false },
                        title = { Text("Sin medicamentos disponibles") },
                        text = {
                            Text("No tienes medicamentos en tus prescripciones activas. Puedes crear un recordatorio con un nombre personalizado o agregar primero una prescripción con medicamentos.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                // Continuar sin selector de medicamentos
                            }) {
                                Text("Crear manualmente")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                CreateReminderDialog(
                    availableMedications = medications,
                    onDismiss = { showCreateDialog = false },
                    onSave = { name, medId, time, recurrence, customDays, notificationsEnabled ->
                        vm.createReminder(
                            medicationName = name,
                            medicationId = medId,
                            time = time,
                            recurrence = recurrence,
                            customDays = customDays,
                            notificationsEnabled = notificationsEnabled
                        ) { success, errorMsg ->
                            showCreateDialog = false
                            scope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar(
                                        "✅ Recordatorio creado${if (notificationsEnabled) " y notificaciones programadas" else ""}"
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        errorMsg ?: "Error creando recordatorio"
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Diálogo de estadísticas del cache
            if (showCacheStats) {
                val stats by vm.cacheStats
                CacheStatsDialog(
                    cacheStats = stats,
                    onDismiss = { showCacheStats = false },
                    onClearCache = {
                        vm.clearCache()
                        scope.launch {
                            snackbarHostState.showSnackbar("Cache limpiado")
                        }
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun ConnectivityBadge(isConnected: Boolean) {
    Surface(
        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5252),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isConnected) "En línea" else "Sin conexión",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RemindersList(
    reminders: List<MedicationReminder>,
    onToggle: (MedicationReminder) -> Unit,
    onEdit: (MedicationReminder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reminders, key = { it.id }) { reminder ->
            ReminderCard(
                reminder = reminder,
                onToggle = { onToggle(reminder) },
                onEdit = { onEdit(reminder) }
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: MedicationReminder,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isActive) Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Medication,
                        contentDescription = null,
                        tint = if (reminder.isActive) Color(0xFF1976D2) else Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = reminder.medicationName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⏰ ${reminder.time}",
                                fontSize = 13.sp,
                                color = Color(0xFF555555)
                            )

                            if (reminder.recurrence == ReminderRecurrence.CUSTOM_DAYS && reminder.customDays.isNotEmpty()) {
                                Text(
                                    text = " • ${reminder.customDays.sortedBy { it.ordinal }.joinToString(", ") { it.label }}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF555555)
                                )
                            } else {
                                Text(
                                    text = " • ${reminder.recurrence.label()}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF555555)
                                )
                            }

                            if (reminder.notificationsEnabled) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = "Notificaciones activas",
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SyncStatusBadge(syncStatus = reminder.syncStatus)

                    Spacer(Modifier.width(8.dp))

                    if (reminder.isActive) {
                        Badge(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Activo",
                                color = Color(0xFF4CAF50),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Badge(
                            containerColor = Color(0xFFB0BEC5).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "Inactivo",
                                color = Color(0xFF607D8B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar",
                            tint = Color(0xFF455A64)
                        )
                    }

                    Switch(
                        checked = reminder.isActive,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFCFD8DC), thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            val sdf = remember {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            }
            Text(
                text = "Creado: ${sdf.format(Date(reminder.createdAtMillis))}",
                fontSize = 11.sp,
                color = Color(0xFF78909C)
            )
        }
    }
}

@Composable
private fun SyncStatusBadge(syncStatus: SyncStatus) {
    val backgroundColor = when (syncStatus) {
        SyncStatus.SYNCED -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        SyncStatus.PENDING_SYNC -> Color(0xFFFF9800).copy(alpha = 0.15f)
        SyncStatus.LOCAL_ONLY -> Color(0xFF9E9E9E).copy(alpha = 0.15f)
    }

    val textColor = when (syncStatus) {
        SyncStatus.SYNCED -> Color(0xFF4CAF50)
        SyncStatus.PENDING_SYNC -> Color(0xFFFF9800)
        SyncStatus.LOCAL_ONLY -> Color(0xFF757575)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = syncStatus.icon(),
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = syncStatus.label(),
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyRemindersView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Medication,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(50.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Aún no tienes recordatorios",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Toca el botón + para agregar uno nuevo",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ErrorRemindersView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Error al cargar recordatorios",
                color = Color(0xFFFF5252),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                color = Color(0xFFB71C1C),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateReminderDialog(
    availableMedications: List<AvailableMedication>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, ReminderRecurrence, Set<DayOfWeek>, Boolean) -> Unit
) {
    var selectedMedication by remember { mutableStateOf<AvailableMedication?>(null) }
    var customName by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(ReminderRecurrence.DAILY) }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var expanded by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo recordatorio") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector de medicamento
                if (availableMedications.isNotEmpty()) {
                    Text(
                        "Seleccionar medicamento de tus prescripciones:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF1976D2)
                    )

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMedication?.name ?: "Seleccionar medicamento",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Medicamento") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableMedications.forEach { medication ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(medication.name, fontWeight = FontWeight.SemiBold)
                                            if (medication.doseMg > 0 || medication.frequencyHours > 0) {
                                                Text(
                                                    buildString {
                                                        if (medication.doseMg > 0) append("${medication.doseMg}mg")
                                                        if (medication.doseMg > 0 && medication.frequencyHours > 0) append(" • ")
                                                        if (medication.frequencyHours > 0) append("Cada ${medication.frequencyHours}h")
                                                    },
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedMedication = medication
                                        customName = medication.name
                                        // Sugerencia automática de hora basada en frecuencia
                                        if (medication.frequencyHours > 0 && time.isBlank()) {
                                            time = "08:00" // Hora sugerida
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "O ingresa un nombre personalizado:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                OutlinedTextField(
                    value = customName,
                    onValueChange = {
                        customName = it
                        selectedMedication = null
                    },
                    label = { Text("Nombre del medicamento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Hora (HH:mm)") },
                    placeholder = { Text("Ej: 08:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = "Frecuencia",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceChip(
                            label = "Diario",
                            selected = recurrence == ReminderRecurrence.DAILY,
                            onClick = { recurrence = ReminderRecurrence.DAILY }
                        )
                        RecurrenceChip(
                            label = "Semanal",
                            selected = recurrence == ReminderRecurrence.WEEKLY,
                            onClick = { recurrence = ReminderRecurrence.WEEKLY }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceChip(
                            label = "Una vez",
                            selected = recurrence == ReminderRecurrence.ONCE,
                            onClick = { recurrence = ReminderRecurrence.ONCE }
                        )
                        RecurrenceChip(
                            label = "Días específicos",
                            selected = recurrence == ReminderRecurrence.CUSTOM_DAYS,
                            onClick = { recurrence = ReminderRecurrence.CUSTOM_DAYS }
                        )
                    }
                }

                // Selector de días personalizados
                if (recurrence == ReminderRecurrence.CUSTOM_DAYS) {
                    HorizontalDivider()

                    Text(
                        text = "Selecciona los días",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Color(0xFF555555)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DayOfWeek.values().take(4).forEach { day ->
                                DayChip(
                                    day = day,
                                    selected = selectedDays.contains(day),
                                    onClick = {
                                        selectedDays = if (selectedDays.contains(day)) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DayOfWeek.values().drop(4).forEach { day ->
                                DayChip(
                                    day = day,
                                    selected = selectedDays.contains(day),
                                    onClick = {
                                        selectedDays = if (selectedDays.contains(day)) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider()

                // Activar notificaciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = if (notificationsEnabled) Color(0xFF1976D2) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Notificaciones",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Recibir recordatorio a la hora indicada",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = if (selectedMedication != null) {
                        selectedMedication!!.name
                    } else {
                        customName.trim()
                    }

                    if (finalName.isBlank() || time.isBlank()) return@TextButton
                    if (recurrence == ReminderRecurrence.CUSTOM_DAYS && selectedDays.isEmpty()) return@TextButton

                    onSave(
                        finalName,
                        selectedMedication?.id,
                        time.trim(),
                        recurrence,
                        selectedDays,
                        notificationsEnabled
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RecurrenceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF6B9BD8) else Color(0xFFE0E0E0)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DayChip(
    day: DayOfWeek,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF6B9BD8) else Color(0xFFE0E0E0),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = day.label,
                color = if (selected) Color.White else Color.Black,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CacheStatsDialog(
    cacheStats: CacheStats?,
    onDismiss: () -> Unit,
    onClearCache: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Storage,
                    contentDescription = null,
                    tint = Color(0xFF6B9BD8)
                )
                Spacer(Modifier.width(8.dp))
                Text("Estadísticas de Cache LRU")
            }
        },
        text = {
            if (cacheStats == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                "🔔 Recordatorios",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFFF9800)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Elementos en cache: ${cacheStats.remindersCacheSize}",
                                fontSize = 12.sp
                            )
                            Text(
                                "Cache hits: ${cacheStats.remindersCacheHitCount}",
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                "Cache misses: ${cacheStats.remindersCacheMissCount}",
                                fontSize = 12.sp,
                                color = Color(0xFFFF5252)
                            )
                            Text(
                                "Hit rate: ${String.format("%.1f%%", cacheStats.remindersHitRate * 100)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                "ℹ️ El cache LRU mejora el rendimiento almacenando datos del servidor temporalmente.",
                                fontSize = 11.sp,
                                color = Color(0xFF555555)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "TTL: 5 minutos",
                                fontSize = 10.sp,
                                color = Color(0xFF777777)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearCache()
                    onDismiss()
                }
            ) {
                Text("Limpiar Cache", color = Color(0xFFFF5252))
            }
        }
    )
}