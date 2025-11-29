package com.mobile.mymeds.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mobile.mymeds.R

object NotificationHelper {

    private const val CHANNEL_ID = "restock_channel"
    private const val CHANNEL_NAME = "Stock Disponible"
    private const val CHANNEL_DESCRIPTION = "Notificaciones cuando un medicamento vuelve a tener stock"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showRestockNotification(
        context: Context,
        medicationName: String,
        pharmacyName: String
    ) {
        // Verificar permiso para notificaciones
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("¡Stock Disponible!")
            .setContentText("'$medicationName' ha vuelto a estar disponible en '$pharmacyName'.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("'$medicationName' ha vuelto a estar disponible en '$pharmacyName'. ¡Aprovecha antes de que se agote!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)


        with(NotificationManagerCompat.from(context)) {
            val notificationId = (medicationName + pharmacyName).hashCode()
            notify(notificationId, builder.build())
        }
    }
}
