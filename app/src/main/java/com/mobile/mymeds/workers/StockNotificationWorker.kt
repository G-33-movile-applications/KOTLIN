package com.mobile.mymeds.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.utils.NotificationHelper
import kotlinx.coroutines.tasks.await

class StockNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getDatabase(appContext)
    private val stockCacheDao = db.medicationStockDao()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {

        const val WORK_NAME = "StockNotificationWorker"
        private const val TAG = "StockNotificationWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "👷‍♂️ WorkManager iniciado: Verificando stock para notificaciones...")

        try {
            // Obtener items sin stock desde el caché local (room)
            val outOfStockItems = stockCacheDao.getOutOfStockItems()
            if (outOfStockItems.isEmpty()) {
                Log.d(TAG, "✅ No hay medicamentos de interés sin stock en la caché. Trabajo finalizado.")
                return Result.success()
            }

            Log.d(TAG, "ℹ️ Se encontraron ${outOfStockItems.size} items sin stock en la caché. Verificando red...")

            var notificationsSent = 0
            for (cachedItem in outOfStockItems) {
                val inventoryDocId = cachedItem.inventoryId.substringAfter("${cachedItem.pharmacyId}_")

                if (inventoryDocId.isBlank()) continue

                val inventoryDoc = firestore.collection("puntosFisicos").document(cachedItem.pharmacyId)
                    .collection("inventario").document(inventoryDocId)
                    .get().await()

                if (inventoryDoc.exists()) {
                    val currentStock = inventoryDoc.getLong("stock")?.toInt() ?: 0

                    if (currentStock > 0 && cachedItem.lastKnownStock <= 0) {
                        Log.d(TAG, "🎉 ¡STOCK ENCONTRADO! Notificando al usuario sobre ${cachedItem.medicationName}")

                        NotificationHelper.showRestockNotification(
                            applicationContext, // El worker nos da acceso al contexto
                            cachedItem.medicationName,
                            cachedItem.pharmacyName
                        )

                        notificationsSent++
                    }

                    // Actualizar cache con nueva info
                    val updatedCacheItem = cachedItem.copy(
                        lastKnownStock = currentStock,
                        lastCheckedTimestamp = System.currentTimeMillis()
                    )
                    stockCacheDao.cacheStockStatus(listOf(updatedCacheItem))
                }
            }

            Log.d(TAG, "✅ Trabajo finalizado. Se enviaron $notificationsSent notificaciones.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error durante la ejecución del worker", e)
            return Result.retry()
        }
    }
}
