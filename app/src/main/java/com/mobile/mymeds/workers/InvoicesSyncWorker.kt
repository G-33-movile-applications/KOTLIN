package com.mobile.mymeds.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.mobile.mymeds.data.local.prefs.InvoicesPreferencesManager
import com.mobile.mymeds.models.InvoiceStatus
import com.mobile.mymeds.repository.InvoicesRepository
import com.mobile.mymeds.utils.ConnectivityUtils
import com.mobile.mymeds.utils.NetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICE SYNC WORKER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Worker para sincronización automática de facturas en background.
 * Se ejecuta periódicamente y cuando hay cambios en la conectividad.
 */

class InvoiceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefsManager = InvoicesPreferencesManager(context)
    private val invoicesRepository = InvoicesRepository()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "InvoiceSyncWorker"
        const val WORK_NAME = "invoice_sync_work"

        // Keys para datos
        private const val KEY_SYNC_COUNT = "sync_count"
        private const val KEY_FAILED_COUNT = "failed_count"

        /**
         * Programa la sincronización periódica de facturas
         * Se ejecuta cada 6 horas si hay conexión WiFi
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<InvoiceSyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 1,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("invoice_sync")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "✅ Sincronización periódica programada")
        }

        /**
         * Ejecuta sincronización inmediata (one-time)
         */
        fun scheduleSyncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<InvoiceSyncWorker>()
                .setConstraints(constraints)
                .addTag("invoice_sync_now")
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)

            Log.d(TAG, "🔄 Sincronización inmediata programada")
        }

        /**
         * Cancela todas las sincronizaciones programadas
         */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "❌ Sincronización cancelada")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 Iniciando InvoiceSyncWorker (intento ${runAttemptCount + 1})")

        try {
            // 1. Verificar conectividad
            if (!ConnectivityUtils.isNetworkAvailable(applicationContext)) {
                Log.w(TAG, "⚠️ Sin conexión a internet")
                return@withContext Result.retry()
            }

            // 2. Verificar usuario autenticado
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Log.w(TAG, "⚠️ Usuario no autenticado")
                return@withContext Result.failure()
            }

            // 3. Verificar si hay facturas pendientes
            val pendingInvoices = prefsManager.getPendingInvoices()
            if (pendingInvoices.isEmpty()) {
                Log.d(TAG, "✅ No hay facturas pendientes")
                return@withContext Result.success()
            }

            Log.d(TAG, "📋 ${pendingInvoices.size} facturas pendientes de sincronizar")

            // 4. Sincronizar cada factura pendiente
            var syncedCount = 0
            var failedCount = 0

            pendingInvoices.forEach { pendingInvoice ->
                try {
                    val success = syncSingleInvoice(pendingInvoice.orderId, userId)
                    if (success) {
                        syncedCount++
                        prefsManager.removePendingInvoice(pendingInvoice.orderId)
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error sincronizando factura ${pendingInvoice.orderId}", e)
                    failedCount++
                }
            }

            Log.d(TAG, "✅ Sincronización completada: $syncedCount exitosas, $failedCount fallidas")

            // 5. Actualizar timestamp de última sincronización
            if (syncedCount > 0) {
                prefsManager.updateLastSyncTimestamp()
            }

            // 6. Retornar resultado
            val outputData = workDataOf(
                KEY_SYNC_COUNT to syncedCount,
                KEY_FAILED_COUNT to failedCount
            )

            when {
                failedCount == 0 -> Result.success(outputData)
                syncedCount > 0 -> Result.success(outputData) // Parcialmente exitoso
                runAttemptCount < 3 -> Result.retry()
                else -> Result.failure(outputData)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en InvoiceSyncWorker", e)

            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Sincroniza una factura individual
     */
    private suspend fun syncSingleInvoice(orderId: String, userId: String): Boolean {
        return try {
            Log.d(TAG, "📤 Sincronizando factura para pedido $orderId")

            // 1. Buscar factura en cache local
            val cachedInvoices = prefsManager.getGeneratedInvoices()
            val invoice = cachedInvoices.find { it.orderId == orderId }

            if (invoice == null) {
                Log.w(TAG, "⚠️ Factura no encontrada en cache")
                return false
            }

            // 2. Verificar que exista el archivo PDF
            val pdfFile = File(invoice.localPdfPath)
            if (!pdfFile.exists()) {
                Log.w(TAG, "⚠️ Archivo PDF no encontrado: ${invoice.localPdfPath}")
                return false
            }

            // 3. Actualizar estado a UPLOADING
            val uploadingInvoice = invoice.copy(status = InvoiceStatus.UPLOADING)
            prefsManager.saveGeneratedInvoice(uploadingInvoice)

            // 4. Crear documento en Firestore
            val createResult = invoicesRepository.createInvoice(uploadingInvoice)
            if (createResult.isFailure) {
                Log.e(TAG, "❌ Error creando documento en Firestore")
                markInvoiceAsError(invoice, "Error creando en Firestore")
                return false
            }

            val invoiceId = createResult.getOrNull()!!

            // 5. Subir PDF a Storage
            val uploadResult = invoicesRepository.uploadPdfToStorage(
                invoiceId = invoiceId,
                pdfFile = pdfFile
            )

            if (uploadResult.isFailure) {
                Log.e(TAG, "❌ Error subiendo PDF a Storage")
                markInvoiceAsError(invoice, "Error subiendo PDF")
                return false
            }

            val pdfUrl = uploadResult.getOrNull()!!

            // 6. Actualizar documento con URL del PDF
            val storageRef = "invoices/$userId/${invoiceId}_${System.currentTimeMillis()}.pdf"
            val updateResult = invoicesRepository.updateInvoiceWithPdfUrl(
                invoiceId = invoiceId,
                pdfUrl = pdfUrl,
                storageRef = storageRef
            )

            if (updateResult.isFailure) {
                Log.e(TAG, "❌ Error actualizando factura con URL")
                markInvoiceAsError(invoice, "Error actualizando URL")
                return false
            }

            // 7. Actualizar cache local como sincronizada
            val syncedInvoice = invoice.copy(
                id = invoiceId,
                pdfUrl = pdfUrl,
                storageRef = storageRef,
                syncedToCloud = true,
                syncedAt = System.currentTimeMillis(),
                status = InvoiceStatus.COMPLETED
            )
            prefsManager.saveGeneratedInvoice(syncedInvoice)

            Log.d(TAG, "✅ Factura sincronizada exitosamente")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando factura", e)
            false
        }
    }

    /**
     * Marca una factura como error
     */
    private fun markInvoiceAsError(invoice: com.mobile.mymeds.models.Invoice, errorMessage: String) {
        val errorInvoice = invoice.copy(
            status = InvoiceStatus.ERROR,
            errorMessage = errorMessage,
            retryCount = invoice.retryCount + 1
        )
        prefsManager.saveGeneratedInvoice(errorInvoice)
    }

    /**
     * Envía notificación al usuario sobre el resultado de la sincronización
     */
    private fun sendSyncNotification(syncedCount: Int, failedCount: Int) {
        // TODO: Implementar notificación usando NotificationManager
        // Ejemplo:
        // - "✅ Se sincronizaron X facturas"
        // - "⚠️ X facturas fallaron al sincronizar"
        Log.d(TAG, "📬 Debería enviar notificación: $syncedCount exitosas, $failedCount fallidas")
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICE CLEANUP WORKER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Worker para limpieza periódica de facturas antiguas
 */

class InvoiceCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefsManager = InvoicesPreferencesManager(context)

    companion object {
        private const val TAG = "InvoiceCleanupWorker"
        const val WORK_NAME = "invoice_cleanup_work"

        /**
         * Programa la limpieza periódica (cada 7 días)
         */
        fun schedulePeriodicCleanup(context: Context) {
            val cleanupRequest = PeriodicWorkRequestBuilder<InvoiceCleanupWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .addTag("invoice_cleanup")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            Log.d(TAG, "✅ Limpieza periódica programada")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "🧹 Iniciando limpieza de facturas antiguas")

        try {
            prefsManager.cleanupOldInvoices()
            Log.d(TAG, "✅ Limpieza completada")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en limpieza", e)
            Result.failure()
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * EXTENSION FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════
 */

/**
 * Inicializa todos los workers de facturas
 */
fun Context.initializeInvoiceWorkers() {
    InvoiceSyncWorker.schedulePeriodicSync(this)
    InvoiceCleanupWorker.schedulePeriodicCleanup(this)
    Log.d("InvoiceWorkers", "✅ Workers de facturas inicializados")
}