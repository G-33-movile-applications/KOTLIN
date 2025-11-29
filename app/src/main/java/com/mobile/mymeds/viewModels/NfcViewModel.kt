package com.mobile.mymeds.viewModels

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.data.local.room.entitites.NfcPrescriptionEntity
import com.mobile.mymeds.workers.NfcFirebaseUploader
import com.mobile.mymeds.workers.NfcSyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Representa la acción NFC exclusiva que el usuario quiere realizar.
 */
sealed class NfcActionState {
    object None : NfcActionState()
    object Read : NfcActionState()
    data class Write(val jsonData: String) : NfcActionState()
    object Wipe : NfcActionState()
}

data class UiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val status: String = "Seleccione una acción", // Campo de texto único para el estado
    val isSaving: Boolean = false,
    val pendingPrescriptions: List<NfcViewModel.NfcData> = emptyList()
)

private val Ndef.isWriteProtected: Boolean
    get() = this.canMakeReadOnly() && !this.isWritable

class NfcViewModel(private val application: Application) : AndroidViewModel(application) {

    data class NfcData(
        @SerializedName("rxId") val id: String,
        @SerializedName("patient") val patientId: String,
        @SerializedName("meds") val medications: List<NfcMedication>,
        @SerializedName("issuedAt") val issuedTimestamp: String,
        @SerializedName("signed") val isSigned: Boolean
    )

    data class NfcMedication(
        @SerializedName("drug") val drugName: String,
        @SerializedName("dose") val dose: String,
        @SerializedName("freq") val frequency: String,
        @SerializedName("days") val durationInDays: Int
    )

    private val _nfcAction = MutableStateFlow<NfcActionState>(NfcActionState.None)
    val nfcAction = _nfcAction.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private val pendingPrescriptionsInternal = mutableListOf<NfcData>()
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()
    private val offlineNfcDao = AppDatabase.getDatabase(application).nfcPrescriptionDao()

    fun init(nfcAdapter: NfcAdapter?) {
        _ui.update { it.copy(supported = nfcAdapter != null, enabled = nfcAdapter?.isEnabled == true) }
    }

    /**
     * Activa el modo de lectura.
     */
    fun startReading() {
        _nfcAction.value = NfcActionState.Read
        _ui.update { it.copy(status = "Acerque el tag para leer…") }
    }

    /**
     * Prepara la app para escribir datos en un tag.
     */
    fun prepareToWrite(json: String) {
        _nfcAction.value = NfcActionState.Write(json)
        _ui.update { it.copy(status = "Acerque el tag para escribir") }
    }

    /**
     * Prepara la app para borrar un tag.
     */
    fun prepareToWipe() {
        _nfcAction.value = NfcActionState.Wipe
        _ui.update { it.copy(status = "¡Cuidado! Acerque el tag para borrar") }
    }

    /**
     * Cancela CUALQUIER acción NFC en curso y resetea el estado.
     */
    fun cancelNfcAction() {
        if (_nfcAction.value == NfcActionState.None) return // No hacer nada si ya está cancelado

        _nfcAction.value = NfcActionState.None
        val statusText = if (pendingPrescriptionsInternal.isNotEmpty()) {
            "${pendingPrescriptionsInternal.size} en cola."
        } else {
            "Seleccione una acción."
        }
        _ui.update { it.copy(status = statusText) }
    }

    /**
     * Decide qué hacer basándose en el estado de _nfcAction.
     */
    fun onTagDiscovered(tag: Tag) {
        val currentAction = _nfcAction.value
        if (currentAction == NfcActionState.None) {
            return
        }

        when (currentAction) {
            is NfcActionState.Read -> {
                readFromTag(tag)
            }
            is NfcActionState.Write -> {
                writeToTag(tag, currentAction.jsonData) { success, msg ->
                    _ui.update { it.copy(status = msg) }
                }
            }
            is NfcActionState.Wipe -> {
                wipeTag(tag) { success, msg ->
                    _ui.update { it.copy(status = msg) }
                }
            }
            is NfcActionState.None -> { /* Ya manejado arriba */ }
        }
        if (currentAction !is NfcActionState.Read) {
            cancelNfcAction()
        }
    }

    private fun readFromTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ndef = Ndef.get(tag) ?: error("El tag no es compatible con NDEF")
                ndef.connect()
                val rawPayload = ndef.ndefMessage?.records?.firstOrNull()?.payload
                ndef.close()
                if (rawPayload != null) {
                    val jsonStartIndex = rawPayload.indexOfFirst { it.toInt().toChar() == '{' }
                    if (jsonStartIndex != -1) rawPayload.drop(jsonStartIndex).toByteArray().toString(Charsets.UTF_8) else null
                } else null
            }.onSuccess { jsonString ->
                val parsedObject = if (jsonString != null && jsonString != "{}") {
                    try { gson.fromJson(jsonString, NfcData::class.java) } catch (e: Exception) { null }
                } else { null }

                if (parsedObject != null) {
                    pendingPrescriptionsInternal.add(parsedObject)
                    withContext(Dispatchers.Main) {
                        _ui.update {
                            it.copy(
                                status = "Prescripción añadida (${pendingPrescriptionsInternal.size} en total).",
                                pendingPrescriptions = pendingPrescriptionsInternal.toList()
                            )
                        }
                        Toast.makeText(application, "Prescripción añadida a la cola", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _ui.update { it.copy(status = "El tag NFC está vacío o no es una prescripción.") }
                        Toast.makeText(application, "El tag NFC está vacío", Toast.LENGTH_LONG).show()
                    }
                }
                withContext(Dispatchers.Main) { cancelNfcAction() }
            }.onFailure { exception ->
                withContext(Dispatchers.Main) {
                    _ui.update { it.copy(status = "Error: ${exception.message}") }
                    cancelNfcAction()
                }
            }
        }
    }

    fun saveAllPendingToFirebase(currentUserId: String, onComplete: (Boolean, String) -> Unit) {
        if (pendingPrescriptionsInternal.isEmpty()) {
            onComplete(false, "No hay prescripciones pendientes para subir.")
            return
        }

        val prescriptionsToProcess = ArrayList(pendingPrescriptionsInternal)
        pendingPrescriptionsInternal.clear()
        _ui.update { it.copy(pendingPrescriptions = emptyList()) }

        if (isNetworkAvailable()) {
            Log.d("NfcViewModel", "Red disponible. Iniciando subida múltiple a Firebase.")
            uploadMultipleToFirebase(currentUserId, prescriptionsToProcess, onComplete)
        } else {
            Log.d("NfcViewModel", "Red no disponible. Guardando ${prescriptionsToProcess.size} prescripciones localmente.")
            saveMultipleLocally(currentUserId, prescriptionsToProcess, onComplete)
        }
    }

    private fun uploadMultipleToFirebase(currentUserId: String, prescriptions: List<NfcData>, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val totalToUpload = prescriptions.size
            _ui.update { it.copy(isSaving = true, status = "Iniciando subida de $totalToUpload prescripciones...") }

            val uploader = NfcFirebaseUploader()
            val jobs = mutableListOf<Job>()
            val successCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)

            prescriptions.forEach { prescription ->
                val job = launch(Dispatchers.IO) {
                    try {
                        if (prescription.patientId == currentUserId) {
                            uploader.uploadPrescription(currentUserId, prescription)
                            successCount.incrementAndGet()
                        } else {
                            Log.w("NfcViewModel", "Prescripción ${prescription.id} omitida: no pertenece al usuario.")
                            errorCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        Log.e("NfcViewModel", "Error subiendo prescripción ${prescription.id}", e)
                        errorCount.incrementAndGet()
                    }
                    withContext(Dispatchers.Main) {
                        val processedCount = successCount.get() + errorCount.get()
                        _ui.update { it.copy(status = "Procesando... $processedCount de $totalToUpload completado(s).") }
                    }
                }
                jobs.add(job)
            }

            jobs.joinAll()
            val finalMessage = "Subida finalizada. Éxitos: ${successCount.get()}, Errores: ${errorCount.get()}."
            _ui.update { it.copy(isSaving = false, status = finalMessage) }
            onComplete(true, finalMessage)
        }
    }

    private fun saveMultipleLocally(currentUserId: String, prescriptions: List<NfcData>, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _ui.update { it.copy(isSaving = true, status = "Guardando localmente...") }
                val offlineEntities = prescriptions.map { nfcData ->
                    NfcPrescriptionEntity(userId = currentUserId, nfcDataJson = gson.toJson(nfcData))
                }
                offlineNfcDao.insertAll(offlineEntities)
                scheduleSync()
                withContext(Dispatchers.Main) {
                    val message = "✅ ${prescriptions.size} prescripciones guardadas localmente. Se subirán cuando haya conexión."
                    _ui.update { it.copy(isSaving = false, status = "Guardado localmente.") }
                    onComplete(true, message)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMessage = "❌ Error al guardar localmente: ${e.message}"
                    _ui.update { it.copy(isSaving = false, status = "Error de guardado local.") }
                    onComplete(false, errorMessage)
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncRequest = OneTimeWorkRequestBuilder<NfcSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(application).enqueueUniqueWork("nfc-sync-work", ExistingWorkPolicy.REPLACE, syncRequest)
        Log.d("NfcViewModel", "NFC sync work scheduled.")
    }

    private fun writeToTag(tag: Tag, json: String, mime: String = "application/com.mobile.mymeds.prescription", onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val payload = json.toByteArray(Charsets.UTF_8)
                val rec = NdefRecord.createMime(mime, payload)
                val msg = NdefMessage(arrayOf(rec))
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    check(!ndef.isWriteProtected) { "Tag de solo lectura" }
                    check(ndef.maxSize >= msg.toByteArray().size) { "Tag sin espacio" }
                    ndef.writeNdefMessage(msg)
                    ndef.close()
                } else {
                    val fmt = NdefFormatable.get(tag) ?: error("Tag no compatible")
                    fmt.connect()
                    fmt.format(msg)
                    fmt.close()
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { onDone(true, "Escritura exitosa") }
            }.onFailure {
                withContext(Dispatchers.Main) { onDone(false, it.message ?: "Error escribiendo") }
            }
        }
    }

    private fun wipeTag(tag: Tag, onDone: (Boolean, String) -> Unit) {
        writeToTag(tag, "{}", onDone = onDone)
    }

    fun saveLastReadDataToFirebase(currentUserId: String, onComplete: (Boolean, String) -> Unit) {
        onComplete(false, "Función obsoleta. Usa el nuevo flujo de 'Subir Todo'.")
    }
}
