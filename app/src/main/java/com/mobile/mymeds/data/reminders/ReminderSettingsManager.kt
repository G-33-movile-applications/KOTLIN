package com.mobile.mymeds.data.reminders

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reminderSettingsDataStore by preferencesDataStore(
    name = "reminder_settings"
)

class ReminderSettingsManager(private val context: Context) {

    private object Keys {
        val GLOBAL_ENABLED = booleanPreferencesKey("global_enabled")
        val DND_START = stringPreferencesKey("dnd_start")   // "22:00"
        val DND_END = stringPreferencesKey("dnd_end")       // "07:00"
        val DEFAULT_SOUND = stringPreferencesKey("default_sound")
    }

    val globalEnabled: Flow<Boolean> =
        context.reminderSettingsDataStore.data.map { prefs ->
            prefs[Keys.GLOBAL_ENABLED] ?: true
        }

    val doNotDisturbStart: Flow<String> =
        context.reminderSettingsDataStore.data.map { prefs ->
            prefs[Keys.DND_START] ?: "22:00"
        }

    val doNotDisturbEnd: Flow<String> =
        context.reminderSettingsDataStore.data.map { prefs ->
            prefs[Keys.DND_END] ?: "07:00"
        }

    val defaultSound: Flow<String> =
        context.reminderSettingsDataStore.data.map { prefs ->
            prefs[Keys.DEFAULT_SOUND] ?: "default"
        }

    suspend fun setGlobalEnabled(enabled: Boolean) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_ENABLED] = enabled
        }
    }

    suspend fun setDoNotDisturb(start: String, end: String) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[Keys.DND_START] = start
            prefs[Keys.DND_END] = end
        }
    }

    suspend fun setDefaultSound(sound: String) {
        context.reminderSettingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SOUND] = sound
        }
    }
}
