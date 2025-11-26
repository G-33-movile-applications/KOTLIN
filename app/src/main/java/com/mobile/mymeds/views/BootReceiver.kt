package com.mobile.mymeds.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            println("📱 Dispositivo reiniciado - reprogramando recordatorios...")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = RemindersDatabase.getInstance(context)
                    val dao = db.remindersDao()
                    val reminders = dao.getAll().map { it.toDomain() }

                    println("🔄 Encontrados ${reminders.size} recordatorios para reprogramar")

                    val repository = MedicationReminderRepository(context)

                    reminders.forEach { reminder ->
                        if (reminder.isActive && reminder.notificationsEnabled) {
                            // Reprogramar notificaciones
                            repository.scheduleNotifications(reminder)
                            println("  ✓ Reprogramado: ${reminder.medicationName}")
                        }
                    }

                    println("✅ Todos los recordatorios reprogramados")
                } catch (e: Exception) {
                    println("❌ Error reprogramando recordatorios: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}