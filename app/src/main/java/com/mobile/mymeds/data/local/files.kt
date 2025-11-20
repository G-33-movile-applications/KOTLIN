package com.mobile.mymeds.data.local.files

import android.content.Context
import android.util.Log
import com.mobile.mymeds.models.OrderWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "LocalFileManager"

/**
 * Manager para Archivos Locales (5 PUNTOS)
 *
 * Gestiona el almacenamiento de recibos/comprobantes de pedidos en archivos JSON
 * en el directorio privado de la aplicación.
 *
 * Directorio: /data/data/com.example.mymeds/files/receipts/
 */
class LocalFileManager(private val context: Context) {

    private val receiptsDir = File(context.filesDir, "receipts")

    init {
        // Crea el directorio si no existe
        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs()
            Log.d(TAG, "📁 [Files] Directorio de recibos creado: ${receiptsDir.absolutePath}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERACIONES DE ESCRITURA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Guarda un recibo de pedido en archivo JSON
     *
     * @param order Pedido con sus ítems
     * @return File? El archivo creado o null si hubo error
     */
    suspend fun saveOrderReceipt(order: OrderWithItems): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = "receipt_${order.order.orderId}_${System.currentTimeMillis()}.json"
            val file = File(receiptsDir, fileName)

            // Genera el contenido JSON del recibo
            val receiptData = buildString {
                appendLine("{")
                appendLine("  \"orderId\": \"${order.order.orderId}\",")
                appendLine("  \"userId\": \"${order.order.userId}\",")
                appendLine("  \"date\": \"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(order.order.orderDate))}\",")
                appendLine("  \"deliveryType\": \"${order.order.deliveryType}\",")
                appendLine("  \"deliveryAddress\": \"${order.order.deliveryAddress ?: "N/A"}\",")
                appendLine("  \"status\": \"${order.order.status}\",")
                appendLine("  \"totalItems\": ${order.order.totalItems},")
                appendLine("  \"notes\": \"${order.order.notes ?: ""}\",")
                appendLine("  \"items\": [")
                order.items.forEachIndexed { index, item ->
                    appendLine("    {")
                    appendLine("      \"medicationId\": \"${item.medicationId}\",")
                    appendLine("      \"name\": \"${item.medicationName}\",")
                    appendLine("      \"dose\": \"${item.doseMg}mg\",")
                    appendLine("      \"quantity\": ${item.quantity}")
                    appendLine("    }${if (index < order.items.size - 1) "," else ""}")
                }
                appendLine("  ],")
                appendLine("  \"createdAt\": \"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(order.order.createdAt))}\",")
                appendLine("  \"syncedToFirebase\": ${order.order.syncedToFirebase},")
                appendLine("  \"firebaseOrderId\": \"${order.order.firebaseOrderId ?: ""}\"")
                appendLine("}")
            }

            // Escribe el archivo
            FileOutputStream(file).use { output ->
                output.write(receiptData.toByteArray())
            }

            Log.d(TAG, "📄 [Files] Recibo guardado exitosamente")
            Log.d(TAG, "📄 [Files] Nombre: $fileName")
            Log.d(TAG, "📄 [Files] Ruta: ${file.absolutePath}")
            Log.d(TAG, "📄 [Files] Tamaño: ${file.length()} bytes")

            file
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Files] Error guardando recibo: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERACIONES DE LECTURA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lee un recibo desde archivo JSON
     *
     * @param orderId ID del pedido
     * @return String? Contenido del archivo o null si no existe
     */
    suspend fun readOrderReceipt(orderId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val files = receiptsDir.listFiles { _, name ->
                name.startsWith("receipt_$orderId")
            }

            if (files.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ [Files] No se encontró recibo para pedido $orderId")
                return@withContext null
            }

            val content = files.first().readText()
            Log.d(TAG, "📄 [Files] Recibo leído para pedido $orderId")
            Log.d(TAG, "📄 [Files] Tamaño: ${content.length} caracteres")
            content
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Files] Error leyendo recibo: ${e.message}", e)
            null
        }
    }

    /**
     * Lista todos los recibos guardados
     *
     * @return List<File> Lista de archivos de recibos
     */
    fun getAllReceipts(): List<File> {
        return try {
            val files = receiptsDir.listFiles()?.toList() ?: emptyList()
            Log.d(TAG, "📁 [Files] Total de recibos: ${files.size}")
            files
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Files] Error listando recibos: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Obtiene información de todos los recibos
     */
    fun getReceiptsInfo(): List<Map<String, Any>> {
        return getAllReceipts().map { file ->
            mapOf(
                "name" to file.name,
                "path" to file.absolutePath,
                "size" to file.length(),
                "lastModified" to file.lastModified()
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERACIONES DE ELIMINACIÓN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Elimina un recibo
     *
     * @param orderId ID del pedido
     * @return Boolean true si se eliminó correctamente
     */
    suspend fun deleteReceipt(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val files = receiptsDir.listFiles { _, name ->
                name.startsWith("receipt_$orderId")
            }

            if (files.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ [Files] No hay recibos para eliminar del pedido $orderId")
                return@withContext false
            }

            var deleted = 0
            files.forEach { file ->
                if (file.delete()) {
                    deleted++
                }
            }

            Log.d(TAG, "🗑️ [Files] $deleted recibo(s) eliminado(s) para pedido $orderId")
            deleted > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Files] Error eliminando recibo: ${e.message}", e)
            false
        }
    }

    /**
     * Elimina todos los recibos
     *
     * @return Int Número de recibos eliminados
     */
    suspend fun deleteAllReceipts(): Int = withContext(Dispatchers.IO) {
        try {
            val files = receiptsDir.listFiles() ?: emptyArray()
            var deleted = 0

            files.forEach { file ->
                if (file.delete()) {
                    deleted++
                }
            }

            Log.d(TAG, "🗑️ [Files] $deleted recibo(s) eliminado(s)")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Files] Error eliminando todos los recibos: ${e.message}", e)
            0
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Obtiene el tamaño total de todos los recibos en bytes
     */
    fun getTotalReceiptsSize(): Long {
        return try {
            getAllReceipts().sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Verifica si existe un recibo para un pedido
     */
    fun receiptExists(orderId: Long): Boolean {
        return try {
            val files = receiptsDir.listFiles { _, name ->
                name.startsWith("receipt_$orderId")
            }
            !files.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}