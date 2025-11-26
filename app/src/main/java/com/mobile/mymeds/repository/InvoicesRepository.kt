package com.mobile.mymeds.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.mobile.mymeds.models.Invoice
import com.mobile.mymeds.models.InvoiceStatus
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICES REPOSITORY
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Repositorio para operaciones CRUD de facturas en Firebase.
 * Maneja Firestore para datos y Storage para archivos PDF.
 *
 * ESTRUCTURA: usuarios/{userId}/facturas/{invoiceId}
 * STORAGE: invoices/{userId}/{invoiceId}_{timestamp}.pdf
 */

class InvoicesRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "InvoicesRepository"
        private const val COLLECTION_USERS = "usuarios"
        private const val SUBCOLLECTION_INVOICES = "facturas"
        private const val STORAGE_PATH_INVOICES = "invoices"
    }

    /**
     * Obtiene la referencia a la subcolección de facturas de un usuario
     */
    private fun getUserInvoicesCollection(userId: String) =
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_INVOICES)

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * CREAR FACTURA
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Crea una nueva factura en la subcolección del usuario
     * @param invoice Factura a crear
     * @return Result con el ID de la factura creada
     */
    suspend fun createInvoice(invoice: Invoice): Result<String> {
        return try {
            val userId = invoice.userId.ifEmpty {
                auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
            }

            Log.d(TAG, "📝 Creando factura ${invoice.invoiceNumber} en Firestore")
            Log.d(TAG, "   Ruta: usuarios/$userId/facturas/${invoice.id}")

            val invoiceData = invoice.copy(
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            ).toMap()

            val docRef = if (invoice.id.isNotEmpty()) {
                getUserInvoicesCollection(userId).document(invoice.id)
            } else {
                getUserInvoicesCollection(userId).document()
            }

            docRef.set(invoiceData).await()

            Log.d(TAG, "✅ Factura creada exitosamente con ID: ${docRef.id}")
            Result.success(docRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creando factura", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * SUBIR PDF A STORAGE
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Sube un archivo PDF a Firebase Storage
     * @param invoiceId ID de la factura
     * @param pdfFile Archivo PDF a subir
     * @param onProgress Callback de progreso (0.0 a 1.0)
     * @return Result con la URL de descarga del PDF
     */
    suspend fun uploadPdfToStorage(
        invoiceId: String,
        pdfFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            Log.d(TAG, "☁️ Subiendo PDF a Storage: ${pdfFile.name}")
            Log.d(TAG, "   Ruta: $STORAGE_PATH_INVOICES/$userId/${pdfFile.name}")

            // Referencia en Storage: invoices/{userId}/{fileName}
            val storageRef = storage.reference
                .child(STORAGE_PATH_INVOICES)
                .child(userId)
                .child(pdfFile.name)

            // Subir archivo
            val uploadTask = storageRef.putFile(Uri.fromFile(pdfFile))

            // Monitorear progreso
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat() / 100f
                onProgress?.invoke(progress)
                Log.d(TAG, "📊 Progreso de subida: ${(progress * 100).toInt()}%")
            }

            // Esperar a que termine
            uploadTask.await()

            // Obtener URL de descarga
            val downloadUrl = storageRef.downloadUrl.await().toString()

            Log.d(TAG, "✅ PDF subido exitosamente")
            Log.d(TAG, "   URL: $downloadUrl")
            Result.success(downloadUrl)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error subiendo PDF", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza la factura con la URL del PDF después de subirlo
     */
    suspend fun updateInvoiceWithPdfUrl(
        invoiceId: String,
        pdfUrl: String,
        storageRef: String
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))

            Log.d(TAG, "📝 Actualizando factura con URL del PDF")

            val updates = mapOf(
                "pdfUrl" to pdfUrl,
                "storageRef" to storageRef,
                "syncedToCloud" to true,
                "syncedAt" to System.currentTimeMillis(),
                "status" to InvoiceStatus.COMPLETED.name,
                "updatedAt" to Timestamp.now()
            )

            getUserInvoicesCollection(userId)
                .document(invoiceId)
                .update(updates)
                .await()

            Log.d(TAG, "✅ Factura actualizada con URL del PDF")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando factura", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * OBTENER FACTURAS
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Obtiene todas las facturas de un usuario desde su subcolección
     * @param userId ID del usuario
     * @return Result con lista de facturas
     */
    suspend fun getUserInvoices(userId: String): Result<List<Invoice>> {
        return try {
            Log.d(TAG, "📖 Obteniendo facturas del usuario $userId")
            Log.d(TAG, "   Ruta: usuarios/$userId/facturas")

            val snapshot = getUserInvoicesCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando factura ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "✅ ${invoices.size} facturas obtenidas")
            Result.success(invoices)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo facturas", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene una factura específica por ID
     */
    suspend fun getInvoiceById(userId: String, invoiceId: String): Result<Invoice?> {
        return try {
            Log.d(TAG, "📖 Obteniendo factura $invoiceId")

            val doc = getUserInvoicesCollection(userId)
                .document(invoiceId)
                .get()
                .await()

            if (doc.exists()) {
                val invoice = Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                Log.d(TAG, "✅ Factura obtenida")
                Result.success(invoice)
            } else {
                Log.w(TAG, "⚠️ Factura no encontrada")
                Result.success(null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo factura", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene facturas por ID de pedido
     */
    suspend fun getInvoicesByOrderId(userId: String, orderId: String): Result<List<Invoice>> {
        return try {
            Log.d(TAG, "📖 Obteniendo facturas del pedido $orderId")

            val snapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("orderId", orderId)
                .get()
                .await()

            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando factura ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "✅ ${invoices.size} facturas obtenidas para el pedido")
            Result.success(invoices)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo facturas del pedido", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * ACTUALIZAR FACTURA
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Actualiza el estado de una factura
     */
    suspend fun updateInvoiceStatus(
        userId: String,
        invoiceId: String,
        status: InvoiceStatus,
        errorMessage: String = ""
    ): Result<Unit> {
        return try {
            Log.d(TAG, "📝 Actualizando estado de factura a $status")

            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to Timestamp.now()
            )

            if (errorMessage.isNotEmpty()) {
                updates["errorMessage"] = errorMessage
            }

            getUserInvoicesCollection(userId)
                .document(invoiceId)
                .update(updates)
                .await()

            Log.d(TAG, "✅ Estado actualizado")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando estado", e)
            Result.failure(e)
        }
    }

    /**
     * Incrementa el contador de reintentos
     */
    suspend fun incrementRetryCount(userId: String, invoiceId: String): Result<Unit> {
        return try {
            val doc = getUserInvoicesCollection(userId)
                .document(invoiceId)
                .get()
                .await()

            val currentRetry = (doc.getLong("retryCount") ?: 0L).toInt()

            getUserInvoicesCollection(userId)
                .document(invoiceId)
                .update(
                    mapOf(
                        "retryCount" to (currentRetry + 1),
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error incrementando contador de reintentos", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * ELIMINAR FACTURA
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Elimina una factura (documento y archivo PDF)
     */
    suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> {
        return try {
            Log.d(TAG, "🗑️ Eliminando factura $invoiceId")

            // Obtener factura para conseguir la referencia del PDF
            val doc = getUserInvoicesCollection(userId)
                .document(invoiceId)
                .get()
                .await()

            // Eliminar PDF de Storage si existe
            val storageRef = doc.getString("storageRef")
            if (!storageRef.isNullOrEmpty()) {
                try {
                    storage.reference.child(storageRef).delete().await()
                    Log.d(TAG, "✅ PDF eliminado de Storage")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error eliminando PDF de Storage", e)
                }
            }

            // Eliminar documento de Firestore
            getUserInvoicesCollection(userId)
                .document(invoiceId)
                .delete()
                .await()

            Log.d(TAG, "✅ Factura eliminada")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error eliminando factura", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * BÚSQUEDA Y FILTROS
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Busca facturas por número
     */
    suspend fun searchInvoiceByNumber(userId: String, invoiceNumber: String): Result<List<Invoice>> {
        return try {
            Log.d(TAG, "🔍 Buscando factura por número: $invoiceNumber")

            val snapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("invoiceNumber", invoiceNumber)
                .get()
                .await()

            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    null
                }
            }

            Log.d(TAG, "✅ ${invoices.size} facturas encontradas")
            Result.success(invoices)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error buscando factura", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene facturas por estado
     */
    suspend fun getInvoicesByStatus(
        userId: String,
        status: InvoiceStatus
    ): Result<List<Invoice>> {
        return try {
            Log.d(TAG, "📖 Obteniendo facturas con estado $status")

            val snapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("status", status.name)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    null
                }
            }

            Log.d(TAG, "✅ ${invoices.size} facturas obtenidas")
            Result.success(invoices)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo facturas por estado", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene facturas pendientes de sincronizar
     */
    suspend fun getPendingSyncInvoices(userId: String): Result<List<Invoice>> {
        return try {
            Log.d(TAG, "📖 Obteniendo facturas pendientes de sincronizar")

            val snapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("syncedToCloud", false)
                .get()
                .await()

            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    null
                }
            }

            Log.d(TAG, "✅ ${invoices.size} facturas pendientes")
            Result.success(invoices)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo facturas pendientes", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * ESTADÍSTICAS
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Obtiene estadísticas de facturas del usuario
     */
    suspend fun getInvoiceStats(userId: String): Result<InvoiceStats> {
        return try {
            Log.d(TAG, "📊 Obteniendo estadísticas de facturas")

            val snapshot = getUserInvoicesCollection(userId)
                .get()
                .await()

            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    Invoice.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    null
                }
            }

            val stats = InvoiceStats(
                total = invoices.size,
                completed = invoices.count { it.status == InvoiceStatus.COMPLETED },
                pending = invoices.count { it.status == InvoiceStatus.PENDING },
                generating = invoices.count { it.status == InvoiceStatus.GENERATING },
                error = invoices.count { it.status == InvoiceStatus.ERROR },
                syncedToCloud = invoices.count { it.syncedToCloud },
                localOnly = invoices.count { !it.syncedToCloud }
            )

            Log.d(TAG, "✅ Estadísticas obtenidas: $stats")
            Result.success(stats)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo estadísticas", e)
            Result.failure(e)
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * VERIFICACIONES
     * ═══════════════════════════════════════════════════════════════════════
     */

    /**
     * Verifica si ya existe una factura para un pedido
     */
    suspend fun invoiceExistsForOrder(userId: String, orderId: String): Result<Boolean> {
        return try {
            val snapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("orderId", orderId)
                .limit(1)
                .get()
                .await()

            Result.success(!snapshot.isEmpty)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando existencia de factura", e)
            Result.failure(e)
        }
    }

    /**
     * Verifica si un número de factura ya existe
     */
    suspend fun invoiceNumberExists(userId: String, invoiceNumber: String): Result<Boolean> {
        return try {
            val snapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("invoiceNumber", invoiceNumber)
                .limit(1)
                .get()
                .await()

            Result.success(!snapshot.isEmpty)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando número de factura", e)
            Result.failure(e)
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * MODELOS AUXILIARES
 * ═══════════════════════════════════════════════════════════════════════════
 */

data class InvoiceStats(
    val total: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val generating: Int = 0,
    val error: Int = 0,
    val syncedToCloud: Int = 0,
    val localOnly: Int = 0
)