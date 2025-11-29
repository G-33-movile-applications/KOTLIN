package com.mobile.mymeds.utils

import android.content.Context
import androidx.work.*
import androidx.work.NetworkType.CONNECTED
import com.mobile.mymeds.workers.StockNotificationWorker
import java.util.concurrent.TimeUnit

object WorkScheduler {
    fun scheduleStockCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(CONNECTED)
            .build()

        // Se ejecutará aproximadamente cada 12 horas
        val repeatingRequest = PeriodicWorkRequestBuilder<StockNotificationWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StockNotificationWorker.WORK_NAME, // El nombre único del Worker
            ExistingPeriodicWorkPolicy.KEEP, // Si ya existe un trabajo con este nombre, lo mantiene y no lo reemplaza
            repeatingRequest
        )
    }
}
