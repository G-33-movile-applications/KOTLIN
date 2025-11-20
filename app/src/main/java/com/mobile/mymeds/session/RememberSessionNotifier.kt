package com.mobile.mymeds.session

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import androidx.annotation.RequiresPermission

object RememberSessionNotifier {
    private const val CHANNEL_ID = "remember_session_channel"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notifyConsent(context: Context) {
        ensureChannel(context)

        val yesIntent = Intent(context, KeepSignedInReceiver::class.java).apply {
            action = KeepSignedInReceiver.ACTION_YES
        }
        val noIntent = Intent(context, KeepSignedInReceiver::class.java).apply {
            action = KeepSignedInReceiver.ACTION_NO
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val yesPending = PendingIntent.getBroadcast(context, 1, yesIntent, flags)
        val noPending  = PendingIntent.getBroadcast(context, 2, noIntent,  flags)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("¿Mantener sesión iniciada?")
            .setContentText("¿Estás de acuerdo con mantener tu sesión iniciada por 7 días?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Sí", yesPending)
            .addAction(0, "No", noPending)
            .build()

        // Android 13+ requiere permiso en runtime
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) return // no enviamos si no hay permiso (la UI pedirá permiso)
        }
        nm.notify(KeepSignedInReceiver.NOTIF_ID, notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordar sesión",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Consentimiento para mantener sesión 7 días" }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
