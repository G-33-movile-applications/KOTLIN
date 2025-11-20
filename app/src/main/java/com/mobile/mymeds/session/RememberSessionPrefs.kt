package com.mobile.mymeds.session

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val DS_NAME = "remember_session_prefs"
private val Context.dataStore by preferencesDataStore(name = DS_NAME)

object RememberSessionPrefs {
    private val KEEP_SIGNED_IN = booleanPreferencesKey("keep_signed_in")
    private val LAST_LOGIN_TS  = longPreferencesKey("last_login_ts")

    private const val DAYS_7_MS = 7L * 24L * 60L * 60L * 1000L

    suspend fun setKeepSignedIn(context: Context, keep: Boolean) {
        context.dataStore.edit { it[KEEP_SIGNED_IN] = keep }
    }

    suspend fun setLastLoginNow(context: Context) {
        context.dataStore.edit { it[LAST_LOGIN_TS] = System.currentTimeMillis() }
    }

    suspend fun clearTimestamp(context: Context) {
        context.dataStore.edit { it.remove(LAST_LOGIN_TS) }
    }

    suspend fun shouldAutoLogin(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        val keep = prefs[KEEP_SIGNED_IN] ?: false
        val ts   = prefs[LAST_LOGIN_TS] ?: 0L
        if (!keep || ts == 0L) return false
        return (System.currentTimeMillis() - ts) <= DAYS_7_MS
    }
}
