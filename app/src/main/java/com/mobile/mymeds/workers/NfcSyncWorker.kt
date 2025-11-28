package com.mobile.mymeds.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.viewModels.NfcViewModel
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
    private val appContext: Context,
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

        val uploader = NfcFirebaseUploader()
        var successfulSyncCount = 0

        for (pendingRx in pendingPrescriptions) {
            try {
                val nfcData = gson.fromJson(pendingRx.nfcDataJson, NfcViewModel.NfcData::class.java)
                uploader.uploadPrescription(pendingRx.userId, nfcData)

                dao.deleteById(pendingRx.id)
                successfulSyncCount++
                Log.d("NfcSyncWorker", "Successfully synced and deleted prescription ID: ${pendingRx.id}")

            } catch (e: Exception) {
                Log.e("NfcSyncWorker", "Failed to sync prescription ID: ${pendingRx.id}. Will retry later.", e)
                return Result.retry()
            }
        }

        if (successfulSyncCount > 0) {
            showSyncSuccessNotification(successfulSyncCount)
        }

        return Result.success()
    }

    private fun showSyncSuccessNotification(count: Int) {
        val channelId = "nfc_sync_channel"
        val notificationId = 123

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sincronización de Datos"
            val descriptionText = "Notificaciones sobre la subida de datos pendientes."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Construir la notificación.
        val builder = NotificationCompat.Builder(appContext, channelId)
            // Asegúrate de tener un icono ic_cloud_done en tu carpeta res/drawable.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sincronización Completada")
            .setContentText("$count prescripción(es) se guardaron en la nube.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Mostrar la notificación.
        with(NotificationManagerCompat.from(appContext)) {
            if (ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("NfcSyncWorker", "Permission to post notifications not granted.")
                return
            }
            notify(notificationId, builder.build())
        }
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
