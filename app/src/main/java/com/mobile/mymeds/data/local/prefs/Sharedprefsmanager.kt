package com.mobile.mymeds.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val TAG = "SharedPrefsManager"
private const val PREFS_NAME = "mymeds_prefs"

/**
 * Manager para SharedPreferences (5 PUNTOS)
 *
 * Gestiona configuraciones rápidas y flags de estado usando SharedPreferences:
 * - Flag de primera ejecución
 * - Contadores de pedidos
 * - IDs de último pedido procesado
 * - Cache de timestamps
 * - Configuraciones de UI
 */
class SharedPrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════════════
    // FLAGS DE ESTADO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Establece si es la primera vez que se ejecuta la app
     */
    fun setFirstTimeLaunch(isFirst: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_TIME_LAUNCH, isFirst).apply()
        Log.d(TAG, "💾 [SharedPrefs] Primera ejecución establecida: $isFirst")
    }

    /**
     * Verifica si es la primera vez que se ejecuta la app
     */
    fun isFirstTimeLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_TIME_LAUNCH, true)
    }

    // ═══════════════════════════════════════════════════════════════
    // CONTADORES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Incrementa el contador de pedidos realizados
     */
    fun incrementOrderCount() {
        val current = getOrderCount()
        prefs.edit().putInt(KEY_ORDER_COUNT, current + 1).apply()
        Log.d(TAG, "💾 [SharedPrefs] Contador de pedidos incrementado: ${current + 1}")
    }

    /**
     * Obtiene el contador total de pedidos realizados
     */
    fun getOrderCount(): Int {
        return prefs.getInt(KEY_ORDER_COUNT, 0)
    }

    /**
     * Resetea el contador de pedidos
     */
    fun resetOrderCount() {
        prefs.edit().putInt(KEY_ORDER_COUNT, 0).apply()
        Log.d(TAG, "🔄 [SharedPrefs] Contador de pedidos reseteado")
    }

    // ═══════════════════════════════════════════════════════════════
    // IDs Y REFERENCIAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Guarda el ID del último pedido procesado
     */
    fun setLastOrderId(orderId: Long) {
        prefs.edit().putLong(KEY_LAST_ORDER_ID, orderId).apply()
        Log.d(TAG, "💾 [SharedPrefs] Último pedido ID guardado: $orderId")
    }

    /**
     * Obtiene el ID del último pedido procesado
     */
    fun getLastOrderId(): Long {
        return prefs.getLong(KEY_LAST_ORDER_ID, 0L)
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMESTAMPS Y CACHE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Guarda el timestamp de la última sincronización
     */
    fun setLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
        Log.d(TAG, "💾 [SharedPrefs] Timestamp de sincronización: $timestamp")
    }

    /**
     * Obtiene el timestamp de la última sincronización
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    /**
     * Guarda el timestamp del último pedido creado
     */
    fun setLastOrderCreatedTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_ORDER_CREATED, timestamp).apply()
    }

    /**
     * Obtiene el timestamp del último pedido creado
     */
    fun getLastOrderCreatedTime(): Long {
        return prefs.getLong(KEY_LAST_ORDER_CREATED, 0L)
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURACIONES DE UI
    // ═══════════════════════════════════════════════════════════════

    /**
     * Establece el modo de vista (lista/grid)
     */
    fun setViewMode(mode: String) {
        prefs.edit().putString(KEY_VIEW_MODE, mode).apply()
        Log.d(TAG, "💾 [SharedPrefs] Modo de vista: $mode")
    }

    /**
     * Obtiene el modo de vista
     */
    fun getViewMode(): String {
        return prefs.getString(KEY_VIEW_MODE, VIEW_MODE_LIST) ?: VIEW_MODE_LIST
    }

    /**
     * Establece si el tema oscuro está habilitado
     */
    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        Log.d(TAG, "💾 [SharedPrefs] Modo oscuro: $enabled")
    }

    /**
     * Verifica si el tema oscuro está habilitado
     */
    fun isDarkModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    // ═══════════════════════════════════════════════════════════════
    // PREFERENCIAS DE ORDENAMIENTO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Establece el criterio de ordenamiento de pedidos
     */
    fun setOrderSortCriteria(criteria: String) {
        prefs.edit().putString(KEY_SORT_CRITERIA, criteria).apply()
        Log.d(TAG, "💾 [SharedPrefs] Criterio de ordenamiento: $criteria")
    }

    /**
     * Obtiene el criterio de ordenamiento de pedidos
     */
    fun getOrderSortCriteria(): String {
        return prefs.getString(KEY_SORT_CRITERIA, SORT_BY_DATE) ?: SORT_BY_DATE
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Limpia todas las preferencias
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "🧹 [SharedPrefs] Todas las preferencias limpiadas")
    }

    /**
     * Obtiene todas las preferencias como mapa
     */
    fun getAllPreferences(): Map<String, *> {
        return prefs.all
    }

    /**
     * Verifica si existe una preferencia
     */
    fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    /**
     * Elimina una preferencia específica
     */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
        Log.d(TAG, "🗑️ [SharedPrefs] Preferencia eliminada: $key")
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTES
    // ═══════════════════════════════════════════════════════════════

    companion object {
        // Keys
        private const val KEY_FIRST_TIME_LAUNCH = "first_time_launch"
        private const val KEY_ORDER_COUNT = "order_count"
        private const val KEY_LAST_ORDER_ID = "last_order_id"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_ORDER_CREATED = "last_order_created"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_SORT_CRITERIA = "sort_criteria"

        // View modes
        const val VIEW_MODE_LIST = "list"
        const val VIEW_MODE_GRID = "grid"

        // Sort criteria
        const val SORT_BY_DATE = "date"
        const val SORT_BY_STATUS = "status"
        const val SORT_BY_ITEMS = "items"
    }
}