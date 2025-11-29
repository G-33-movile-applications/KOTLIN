package com.mobile.mymeds.data.local.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mobile.mymeds.models.DeliveryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "UserPreferencesManager"

// Extension property para DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Claves de preferencias para DataStore
 */
object PreferencesKeys {
    val DEFAULT_DELIVERY_TYPE = stringPreferencesKey("default_delivery_type")
    val DEFAULT_ADDRESS = stringPreferencesKey("default_address")
    val PHONE_NUMBER = stringPreferencesKey("phone_number")
    val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AUTO_REORDER_ENABLED = booleanPreferencesKey("auto_reorder_enabled")

    val SMART_AUTOFILL_ENABLED = booleanPreferencesKey("smart_autofill_enabled")
}

/**
 * Manager para DataStore (BD Llave/Valor) - 5 PUNTOS
 *
 * Gestiona preferencias de usuario usando DataStore Preferences:
 * - Tipo de entrega predeterminado
 * - Dirección predeterminada
 * - Configuraciones de la app
 * - Timestamps de sincronización
 */
class UserPreferencesManager(private val context: Context) {
    private val dataStore = context.dataStore

    // ═══════════════════════════════════════════════════════════════
    // LECTURA DE PREFERENCIAS (Flows observables)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tipo de entrega predeterminado
     */
    val defaultDeliveryType: Flow<DeliveryType> = dataStore.data
        .map { preferences ->
            val typeString = preferences[PreferencesKeys.DEFAULT_DELIVERY_TYPE]
                ?: DeliveryType.HOME_DELIVERY.name
            try {
                DeliveryType.valueOf(typeString)
            } catch (e: Exception) {
                DeliveryType.HOME_DELIVERY
            }
        }

    /**
     * Dirección predeterminada
     */
    val defaultAddress: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_ADDRESS] ?: ""
        }

    /**
     * Número de teléfono
     */
    val phoneNumber: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PHONE_NUMBER] ?: ""
        }

    /**
     * Última sincronización (timestamp)
     */
    val lastSyncTimestamp: Flow<Long> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
        }

    /**
     * Notificaciones habilitadas
     */
    val notificationsEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
        }

    /**
     * Auto-reorden habilitado
     */
    val autoReorderEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_REORDER_ENABLED] ?: false
        }

    /**
     * Smart Autofill habilitado
     */
    val smartAutofillEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            // Por defecto la feature esta activa
            preferences[PreferencesKeys.SMART_AUTOFILL_ENABLED] ?: true
        }

    // ═══════════════════════════════════════════════════════════════
    // ESCRITURA DE PREFERENCIAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Guarda el tipo de entrega predeterminado
     */
    suspend fun saveDefaultDeliveryType(type: DeliveryType) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_DELIVERY_TYPE] = type.name
        }
        Log.d(TAG, "💾 [DataStore] Tipo de entrega guardado: $type")
    }

    /**
     * Guarda la dirección predeterminada
     */
    suspend fun saveDefaultAddress(address: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_ADDRESS] = address
        }
        Log.d(TAG, "💾 [DataStore] Dirección guardada: $address")
    }

    /**
     * Guarda el número de teléfono
     */
    suspend fun savePhoneNumber(phone: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PHONE_NUMBER] = phone
        }
        Log.d(TAG, "💾 [DataStore] Teléfono guardado")
    }

    /**
     * Guarda el timestamp de última sincronización
     */
    suspend fun saveLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
        Log.d(TAG, "💾 [DataStore] Timestamp de sincronización actualizado: $timestamp")
    }

    /**
     * Habilita/deshabilita notificaciones
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
        Log.d(TAG, "💾 [DataStore] Notificaciones: $enabled")
    }

    /**
     * Habilita/deshabilita auto-reorden
     */
    suspend fun setAutoReorderEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_REORDER_ENABLED] = enabled
        }
        Log.d(TAG, "💾 [DataStore] Auto-reorden: $enabled")
    }

    /**
     * Habilita/deshabilita Smart Autofill
     */
    suspend fun setSmartAutofillEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_AUTOFILL_ENABLED] = enabled
        }
        Log.d(TAG, "💾 [DataStore] Smart Autofill: $enabled")
    }

    /**
     * Limpia todas las preferencias
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d(TAG, "🧹 [DataStore] Todas las preferencias limpiadas")
    }
}