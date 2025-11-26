package com.mobile.mymeds.models

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICE STATUS
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Estados posibles de una factura durante su ciclo de vida.
 */

enum class InvoiceStatus {
    PENDING,      // Pendiente de generar
    GENERATING,   // En proceso de generación del PDF
    GENERATED,    // PDF generado localmente
    UPLOADING,    // Subiendo PDF a Firebase Storage
    COMPLETED,    // Completado y sincronizado en la nube
    ERROR;        // Error en generación o subida

    /**
     * Obtiene el texto descriptivo del estado
     */
    fun getDisplayName(): String {
        return when (this) {
            PENDING -> "Pendiente"
            GENERATING -> "Generando..."
            GENERATED -> "Generado"
            UPLOADING -> "Subiendo..."
            COMPLETED -> "Completado"
            ERROR -> "Error"
        }
    }

    /**
     * Obtiene el color asociado al estado (para UI)
     */
    fun getColorHex(): String {
        return when (this) {
            PENDING -> "#FF9800"      // Naranja
            GENERATING -> "#2196F3"   // Azul
            GENERATED -> "#4CAF50"    // Verde
            UPLOADING -> "#2196F3"    // Azul
            COMPLETED -> "#4CAF50"    // Verde
            ERROR -> "#FF5252"        // Rojo
        }
    }

    /**
     * Obtiene el emoji asociado al estado
     */
    fun getEmoji(): String {
        return when (this) {
            PENDING -> "⏳"
            GENERATING -> "⚙️"
            GENERATED -> "✅"
            UPLOADING -> "☁️"
            COMPLETED -> "✨"
            ERROR -> "❌"
        }
    }

    /**
     * Indica si el estado es final (no cambiará más)
     */
    fun isFinal(): Boolean {
        return this == COMPLETED || this == ERROR
    }

    /**
     * Indica si el estado es procesable (puede continuar)
     */
    fun isProcessable(): Boolean {
        return this == PENDING || this == GENERATING || this == UPLOADING
    }

    /**
     * Indica si se puede reintentar desde este estado
     */
    fun canRetry(): Boolean {
        return this == ERROR
    }
}