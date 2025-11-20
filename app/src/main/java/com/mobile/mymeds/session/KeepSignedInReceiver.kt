package com.mobile.mymeds.session
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KeepSignedInReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_YES = "com.example.mymeds.ACTION_KEEP_SIGNED_IN_ACCEPT"
        const val ACTION_NO  = "com.example.mymeds.ACTION_KEEP_SIGNED_IN_DECLINE"
        const val NOTIF_ID   = 1001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val scope = CoroutineScope(Dispatchers.IO)
        when (intent?.action) {
            ACTION_YES -> {
                scope.launch {
                    RememberSessionPrefs.setKeepSignedIn(context, true)
                    RememberSessionPrefs.setLastLoginNow(context)
                }
            }
            ACTION_NO -> {
                scope.launch {
                    RememberSessionPrefs.setKeepSignedIn(context, false)
                    RememberSessionPrefs.clearTimestamp(context)
                }
            }
        }
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
