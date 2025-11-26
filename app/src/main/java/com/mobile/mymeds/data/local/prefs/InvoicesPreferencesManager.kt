package com.mobile.mymeds.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mobile.mymeds.models.Invoice
import com.mobile.mymeds.models.PendingInvoice

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICES PREFERENCES MANAGER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Maneja el almacenamiento local de facturas usando SharedPreferences.
 * Proporciona caché para uso offline y almacenamiento de facturas pendientes.
 */

class InvoicesPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "invoices_prefs"

        // Keys
        private const val KEY_GENERATED_INVOICES = "generated_invoices_cache"
        private const val KEY_PENDING_INVOICES = "pending_invoices_local"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_WIFI_ONLY_SYNC = "wifi_only_sync"
        private const val KEY_LAST_CLEANUP = "last_cleanup_timestamp"

        // Configuración
        private const val CACHE_MAX_AGE_DAYS = 30
        private const val MAX_PENDING_INVOICES = 50
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * FACTURAS GENERADAS (CACHE)
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Guarda una factura en caché local
     */
    fun saveGeneratedInvoice(invoice: Invoice) {
        val invoices = getGeneratedInvoices().toMutableList()

        // Remover duplicado si existe
        invoices.removeAll { it.id == invoice.id }

        // Agregar nueva
        invoices.add(invoice)

        // Guardar
        val json = gson.toJson(invoices)
        prefs.edit().putString(KEY_GENERATED_INVOICES, json).apply()
    }

    /**
     * Obtiene todas las facturas generadas desde caché
     */
    fun getGeneratedInvoices(): List<Invoice> {
        return try {
            val json = prefs.getString(KEY_GENERATED_INVOICES, null) ?: return emptyList()
            val type = object : TypeToken<List<Invoice>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Obtiene una factura específica por ID
     */
    fun getInvoiceById(invoiceId: String): Invoice? {
        return getGeneratedInvoices().find { it.id == invoiceId }
    }

    /**
     * Obtiene facturas por ID de pedido
     */
    fun getInvoicesByOrderId(orderId: String): List<Invoice> {
        return getGeneratedInvoices().filter { it.orderId == orderId }
    }

    /**
     * Actualiza una factura existente
     */
    fun updateInvoice(invoice: Invoice) {
        val invoices = getGeneratedInvoices().toMutableList()
        val index = invoices.indexOfFirst { it.id == invoice.id }

        if (index != -1) {
            invoices[index] = invoice
            val json = gson.toJson(invoices)
            prefs.edit().putString(KEY_GENERATED_INVOICES, json).apply()
        }
    }

    /**
     * Elimina una factura del caché
     */
    fun deleteInvoice(invoiceId: String) {
        val invoices = getGeneratedInvoices().toMutableList()
        invoices.removeAll { it.id == invoiceId }

        val json = gson.toJson(invoices)
        prefs.edit().putString(KEY_GENERATED_INVOICES, json).apply()
    }

    /**
     * Limpia todas las facturas del caché
     */
    fun clearGeneratedInvoices() {
        prefs.edit().remove(KEY_GENERATED_INVOICES).apply()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * FACTURAS PENDIENTES
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Guarda una factura pendiente de sincronizar
     */
    fun savePendingInvoice(pendingInvoice: PendingInvoice): Boolean {
        val pending = getPendingInvoices().toMutableList()

        // Verificar límite máximo
        if (pending.size >= MAX_PENDING_INVOICES) {
            return false
        }

        // Remover duplicado si existe
        pending.removeAll { it.orderId == pendingInvoice.orderId }

        // Agregar nueva
        pending.add(pendingInvoice)

        // Guardar
        val json = gson.toJson(pending)
        prefs.edit().putString(KEY_PENDING_INVOICES, json).apply()

        return true
    }

    /**
     * Obtiene todas las facturas pendientes
     */
    fun getPendingInvoices(): List<PendingInvoice> {
        return try {
            val json = prefs.getString(KEY_PENDING_INVOICES, null) ?: return emptyList()
            val type = object : TypeToken<List<PendingInvoice>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Obtiene el conteo de facturas pendientes
     */
    fun getPendingInvoicesCount(): Int {
        return getPendingInvoices().size
    }

    /**
     * Elimina una factura pendiente (después de sincronizar)
     */
    fun removePendingInvoice(orderId: String) {
        val pending = getPendingInvoices().toMutableList()
        pending.removeAll { it.orderId == orderId }

        val json = gson.toJson(pending)
        prefs.edit().putString(KEY_PENDING_INVOICES, json).apply()
    }

    /**
     * Limpia todas las facturas pendientes
     */
    fun clearPendingInvoices() {
        prefs.edit().remove(KEY_PENDING_INVOICES).apply()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * SINCRONIZACIÓN
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Actualiza el timestamp de última sincronización
     */
    fun updateLastSyncTimestamp() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    /**
     * Obtiene el timestamp de última sincronización
     */
    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    /**
     * Verifica si es necesario sincronizar (más de 24 horas)
     */
    fun needsSync(): Boolean {
        val lastSync = getLastSyncTimestamp()
        val dayInMillis = 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastSync) > dayInMillis
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * CONFIGURACIÓN
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Habilita/deshabilita sincronización automática
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }

    /**
     * Verifica si la sincronización automática está habilitada
     */
    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    }

    /**
     * Habilita/deshabilita sincronización solo con WiFi
     */
    fun setWifiOnlySync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_SYNC, enabled).apply()
    }

    /**
     * Verifica si está habilitada sincronización solo con WiFi
     */
    fun isWifiOnlySync(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY_SYNC, true)
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * LIMPIEZA Y MANTENIMIENTO
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Limpia facturas antiguas del caché
     */
    fun cleanupOldInvoices() {
        val invoices = getGeneratedInvoices()
        val cutoffTime = System.currentTimeMillis() - (CACHE_MAX_AGE_DAYS * 24 * 60 * 60 * 1000L)

        val filtered = invoices.filter {
            it.generatedAt > cutoffTime || it.syncedToCloud
        }

        val json = gson.toJson(filtered)
        prefs.edit().putString(KEY_GENERATED_INVOICES, json).apply()

        updateLastCleanupTimestamp()
    }

    /**
     * Verifica si es necesario hacer limpieza (más de 7 días)
     */
    fun needsCleanup(): Boolean {
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0L)
        val weekInMillis = 7 * 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastCleanup) > weekInMillis
    }

    /**
     * Actualiza timestamp de última limpieza
     */
    private fun updateLastCleanupTimestamp() {
        prefs.edit().putLong(KEY_LAST_CLEANUP, System.currentTimeMillis()).apply()
    }

    /**
     * Obtiene estadísticas del almacenamiento
     */
    fun getStorageStats(): StorageStats {
        val generated = getGeneratedInvoices()
        val pending = getPendingInvoices()

        val syncedCount = generated.count { it.syncedToCloud }
        val localOnlyCount = generated.count { !it.syncedToCloud }

        return StorageStats(
            totalGenerated = generated.size,
            syncedToCloud = syncedCount,
            localOnly = localOnlyCount,
            pendingSync = pending.size,
            lastSync = getLastSyncTimestamp(),
            needsCleanup = needsCleanup()
        )
    }

    /**
     * Limpia todos los datos (usar con precaución)
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}

/**
 * Estadísticas de almacenamiento
 */
data class StorageStats(
    val totalGenerated: Int,
    val syncedToCloud: Int,
    val localOnly: Int,
    val pendingSync: Int,
    val lastSync: Long,
    val needsCleanup: Boolean
)

/**
 * Extension function para Context
 */
fun Context.getInvoicesPrefsManager(): InvoicesPreferencesManager {
    return InvoicesPreferencesManager(this)
}