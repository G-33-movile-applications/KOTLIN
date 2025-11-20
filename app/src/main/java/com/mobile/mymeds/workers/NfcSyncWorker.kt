package com.mobile.mymeds.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.viewModels.NfcViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NfcSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).nfcPrescriptionDao()
        val pendingPrescriptions = dao.getAll()

        if (pendingPrescriptions.isEmpty()) {
            Log.d("NfcSyncWorker", "No pending prescriptions to sync. Work complete.")
            return Result.success()
        }

        Log.d("NfcSyncWorker", "Found ${pendingPrescriptions.size} prescriptions to sync.")

        var allSucceeded = true
        for (pendingRx in pendingPrescriptions) {
            try {
                val nfcData = gson.fromJson(pendingRx.nfcDataJson, NfcViewModel.NfcData::class.java)

                val uploader = NfcFirebaseUploader()
                uploader.uploadPrescription(pendingRx.userId, nfcData)

                // Si se sube exitosamente, entonces se elimina
                dao.deleteById(pendingRx.id)
                Log.d("NfcSyncWorker", "Successfully synced and deleted prescription ID: ${pendingRx.id}")

            } catch (e: Exception) {
                Log.e("NfcSyncWorker", "Failed to sync prescription ID: ${pendingRx.id}", e)
                allSucceeded = false
                // Si uno falla, se fuarda y se intenta después
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }
}

class NfcFirebaseUploader {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun uploadPrescription(userId: String, nfcData: NfcViewModel.NfcData) {
        withContext(Dispatchers.IO) {
            val userPrescriptionsCollection = firestore.collection("usuarios").document(userId).collection("prescripcionesUsuario")

            val prescriptionDocument = mapNfcDataToPrescriptionHashMap(nfcData)
            val newPrescriptionRef = userPrescriptionsCollection.add(prescriptionDocument).await()

            val medicationDocuments = mapNfcMedsToHashMapList(
                nfcData.medications,
                newPrescriptionRef.id,
                nfcData.id,
                nfcData.issuedTimestamp
            )
            val medsSubCollection = newPrescriptionRef.collection("medicamentosPrescripcion")
            val saveJobs = medicationDocuments.map { doc ->
                async { medsSubCollection.add(doc).await() }
            }
            saveJobs.awaitAll()
        }
    }

    // --- COPIED from NfcViewModel ---
    private fun mapNfcDataToPrescriptionHashMap(nfcData: NfcViewModel.NfcData): HashMap<String, Any> {
        return hashMapOf(
            "activa" to true,
            "fileName" to nfcData.id,
            "fromOCR" to false,
            "notes" to "NFC",
            "status" to "pendiente",
            "totalItems" to nfcData.medications.size,
            "uploadedAt" to Timestamp.now()
        )
    }

    // --- COPIED from NfcViewModel ---
    private suspend fun mapNfcMedsToHashMapList(
        medications: List<NfcViewModel.NfcMedication>,
        firestorePrescriptionId: String,
        nfcPrescriptionId: String,
        issuedTimestamp: String
    ): List<HashMap<String, Any>> {
        val globalMedsCollection = firestore.collection("medicamentosGlobales")

        return medications.map { nfcMed ->
            val querySnapshot = globalMedsCollection.whereEqualTo("nombre", nfcMed.drugName).limit(1).get().await()
            val medDoc = querySnapshot.documents.firstOrNull()
            val medId = medDoc?.id ?: "unknown"
            val medRef = medDoc?.reference?.path ?: "/medicamentosGlobales/unknown"
            val doseMg = nfcMed.dose.filter { it.isDigit() }.toIntOrNull() ?: 0
            val frequencyHours = nfcMed.frequency.filter { it.isDigit() }.toIntOrNull() ?: 24

            val startDate = try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                parser.parse(issuedTimestamp) ?: Date()
            } catch (e: Exception) { Date() }

            val calendar = Calendar.getInstance().apply {
                time = startDate
                add(Calendar.DAY_OF_YEAR, nfcMed.durationInDays)
            }
            val endDate = calendar.time

            hashMapOf(
                "medicationId" to medId,
                "medicationRef" to medRef,
                "name" to nfcMed.drugName,
                "doseMg" to doseMg,
                "frequencyHours" to frequencyHours,
                "startDate" to Timestamp(startDate),
                "endDate" to Timestamp(endDate),
                "createdAt" to Timestamp(Date()),
                "active" to true,
                "prescriptionId" to firestorePrescriptionId,
                "sourceFile" to "NFC Tag (ID: $nfcPrescriptionId)"
            )
        }
    }
}
