package com.mobile.mymeds.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICE NUMBER GENERATOR
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Generador de números de factura únicos con diferentes formatos.
 * Usa contador secuencial persistente en SharedPreferences.
 */

class InvoiceNumberGenerator(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("invoice_number_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COUNTER = "invoice_counter"
        private const val KEY_LAST_RESET = "last_reset_date"

        // Formatos disponibles
        const val FORMAT_STANDARD = "INV-{YEAR}{MONTH}-{SEQ}"      // INV-202411-00001
        const val FORMAT_SIMPLE = "INV-{SEQ}"                       // INV-00001
        const val FORMAT_FULL_DATE = "INV-{YEAR}{MONTH}{DAY}-{SEQ}" // INV-20241125-00001
        const val FORMAT_UUID = "INV-{YEAR}{MONTH}-{UUID}"         // INV-202411-ABC123DE
        const val FORMAT_TIMESTAMP = "INV-{TIMESTAMP}-{SEQ}"       // INV-1732579200-00001
    }

    /**
     * Genera un número de factura único con formato estándar
     * @return Número de factura (ej: INV-202411-00001)
     */
    fun generate(): String {
        return generate(FORMAT_STANDARD)
    }

    /**
     * Genera un número de factura con formato específico
     * @param format Formato a usar (constantes FORMAT_*)
     * @return Número de factura formateado
     */
    fun generate(format: String): String {
        val counter = getNextCounter()
        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR).toString()
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        val seq = String.format("%05d", counter)
        val uuid = UUID.randomUUID().toString().substring(0, 8).uppercase()
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        return format
            .replace("{YEAR}", year)
            .replace("{MONTH}", month)
            .replace("{DAY}", day)
            .replace("{SEQ}", seq)
            .replace("{UUID}", uuid)
            .replace("{TIMESTAMP}", timestamp)
    }

    /**
     * Genera un número de factura con prefijo personalizado
     * @param prefix Prefijo personalizado (ej: "FACT", "REC")
     * @return Número de factura con prefijo
     */
    fun generateWithPrefix(prefix: String): String {
        val counter = getNextCounter()
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val seq = String.format("%05d", counter)

        return "$prefix-$year$month-$seq"
    }

    /**
     * Genera un número de factura basado en fecha y hora
     * @return Número de factura con timestamp (ej: INV-20241125-143025-00001)
     */
    fun generateWithTimestamp(): String {
        val counter = getNextCounter()
        val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        val seq = String.format("%05d", counter)

        return "INV-$dateStr-$seq"
    }

    /**
     * Genera un número de factura corto para uso interno
     * @return Número corto (ej: 00001)
     */
    fun generateShort(): String {
        val counter = getNextCounter()
        return String.format("%05d", counter)
    }

    /**
     * Genera múltiples números de factura únicos
     * @param count Cantidad de números a generar
     * @param format Formato a usar
     * @return Lista de números de factura únicos
     */
    fun generateBatch(count: Int, format: String = FORMAT_STANDARD): List<String> {
        return (1..count).map { generate(format) }
    }

    /**
     * Obtiene el siguiente número de contador y lo incrementa
     */
    private fun getNextCounter(): Int {
        synchronized(this) {
            val currentDate = getCurrentDateKey()
            val lastResetDate = prefs.getString(KEY_LAST_RESET, "")

            // Reiniciar contador si cambió el mes
            val counter = if (currentDate != lastResetDate) {
                prefs.edit()
                    .putString(KEY_LAST_RESET, currentDate)
                    .putInt(KEY_COUNTER, 1)
                    .apply()
                1
            } else {
                val current = prefs.getInt(KEY_COUNTER, 0)
                val next = current + 1
                prefs.edit().putInt(KEY_COUNTER, next).apply()
                next
            }

            return counter
        }
    }

    /**
     * Obtiene la clave de fecha actual (YYYY-MM)
     */
    private fun getCurrentDateKey(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        return "$year-$month"
    }

    /**
     * Obtiene el contador actual sin incrementarlo
     */
    fun getCurrentCounter(): Int {
        return prefs.getInt(KEY_COUNTER, 0)
    }

    /**
     * Reinicia el contador manualmente (usar con precaución)
     */
    fun resetCounter() {
        synchronized(this) {
            prefs.edit()
                .putInt(KEY_COUNTER, 0)
                .putString(KEY_LAST_RESET, getCurrentDateKey())
                .apply()
        }
    }

    /**
     * Valida si un número de factura tiene formato válido
     */
    fun isValidInvoiceNumber(invoiceNumber: String): Boolean {
        // Validar formatos comunes
        val patterns = listOf(
            Regex("^INV-\\d{6}-\\d{5}$"),           // INV-202411-00001
            Regex("^INV-\\d{5}$"),                   // INV-00001
            Regex("^INV-\\d{8}-\\d{5}$"),           // INV-20241125-00001
            Regex("^INV-\\d{6}-[A-Z0-9]{8}$"),      // INV-202411-ABC123DE
            Regex("^INV-\\d+-\\d{5}$"),             // INV-1732579200-00001
            Regex("^[A-Z]+-\\d{6}-\\d{5}$")         // XXX-202411-00001
        )

        return patterns.any { it.matches(invoiceNumber) }
    }

    /**
     * Extrae el número secuencial de un número de factura
     */
    fun extractSequentialNumber(invoiceNumber: String): Int? {
        return try {
            val parts = invoiceNumber.split("-")
            parts.lastOrNull()?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene información del generador
     */
    fun getGeneratorInfo(): GeneratorInfo {
        return GeneratorInfo(
            currentCounter = getCurrentCounter(),
            lastResetDate = prefs.getString(KEY_LAST_RESET, "N/A") ?: "N/A",
            nextInvoiceNumber = generate()
        )
    }
}

/**
 * Información del generador
 */
data class GeneratorInfo(
    val currentCounter: Int,
    val lastResetDate: String,
    val nextInvoiceNumber: String
)

/**
 * Extension function para Context
 */
fun Context.getInvoiceNumberGenerator(): InvoiceNumberGenerator {
    return InvoiceNumberGenerator(this)
}