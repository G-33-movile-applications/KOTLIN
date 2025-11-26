package com.mobile.mymeds.models

import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// Nota: Asegúrate de que las clases MedicationOrder, OrderItem, CartItem y DeliveryType
// estén importadas correctamente si están en otros paquetes.
// import com.mobile.mymeds.models.other.MedicationOrder
// import com.mobile.mymeds.models.other.OrderItem
// import com.mobile.mymeds.models.other.CartItem
// import com.mobile.mymeds.models.other.DeliveryType

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICE
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class Invoice(
    val id: String = "",

    // Identificadores
    val invoiceNumber: String = "",           // Número de factura (ej: INV-202311-A1B2C3D4)
    val orderId: String = "",                 // ID del pedido asociado
    val userId: String = "",                  // ID del usuario que solicitó la factura

    // Timestamps
    val createdAt: Timestamp? = null,         // Fecha de creación
    val updatedAt: Timestamp? = null,         // Última actualización
    val generatedAt: Long = 0L,               // Timestamp de generación local
    val syncedAt: Long = 0L,                  // Timestamp de sincronización con Firebase

    // URLs y rutas
    val pdfUrl: String = "",                  // URL del PDF en Firebase Storage
    val localPdfPath: String = "",            // Ruta local del PDF
    val storageRef: String = "",              // Referencia en Firebase Storage

    // Estado
    val status: InvoiceStatus = InvoiceStatus.PENDING,
    val syncedToCloud: Boolean = false,

    // Snapshot del pedido (para uso offline)
    val orderSnapshot: OrderSnapshot? = null,

    // Metadatos
    val fileSize: Long = 0L,                  // Tamaño del archivo PDF en bytes
    val pageCount: Int = 1,                   // Número de páginas del PDF
    val errorMessage: String = "",            // Mensaje de error si falla generación/subida
    val retryCount: Int = 0,                  // Número de reintentos de sincronización

    // Información adicional
    val metadata: Map<String, Any> = emptyMap()
) {
    fun canRetry(): Boolean {
        return status == InvoiceStatus.ERROR && retryCount < 3
    }

    fun isFullySynced(): Boolean {
        return syncedToCloud && pdfUrl.isNotEmpty() && status == InvoiceStatus.COMPLETED
    }

    fun isAvailableLocally(): Boolean {
        return localPdfPath.isNotEmpty() && (status == InvoiceStatus.GENERATED || status == InvoiceStatus.COMPLETED)
    }

    fun getFileSizeFormatted(): String {
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> "${fileSize / (1024 * 1024)} MB"
        }
    }

    fun getPdfFileName(): String {
        return "invoice_${invoiceNumber.replace("-", "_")}.pdf"
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "invoiceNumber" to invoiceNumber,
            "orderId" to orderId,
            "userId" to userId,
            "createdAt" to (createdAt ?: Timestamp.now()),
            "updatedAt" to Timestamp.now(),
            "generatedAt" to generatedAt,
            "syncedAt" to syncedAt,
            "pdfUrl" to pdfUrl,
            "localPdfPath" to localPdfPath,
            "storageRef" to storageRef,
            "status" to status.name,
            "syncedToCloud" to syncedToCloud,
            "orderSnapshot" to orderSnapshot?.toMap(),
            "fileSize" to fileSize,
            "pageCount" to pageCount,
            "errorMessage" to errorMessage,
            "retryCount" to retryCount,
            "metadata" to metadata
        )
    }

    companion object {
        fun fromMap(id: String, data: Map<String, Any>): Invoice {
            return Invoice(
                id = id,
                invoiceNumber = data["invoiceNumber"] as? String ?: "",
                orderId = data["orderId"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                createdAt = data["createdAt"] as? Timestamp,
                updatedAt = data["updatedAt"] as? Timestamp,
                generatedAt = (data["generatedAt"] as? Number)?.toLong() ?: 0L,
                syncedAt = (data["syncedAt"] as? Number)?.toLong() ?: 0L,
                pdfUrl = data["pdfUrl"] as? String ?: "",
                localPdfPath = data["localPdfPath"] as? String ?: "",
                storageRef = data["storageRef"] as? String ?: "",
                status = runCatching { InvoiceStatus.valueOf(data["status"] as? String ?: "PENDING") }.getOrDefault(InvoiceStatus.PENDING),
                syncedToCloud = data["syncedToCloud"] as? Boolean ?: false,
                orderSnapshot = (data["orderSnapshot"] as? Map<String, Any>)?.let {
                    OrderSnapshot.fromMap(it)
                },
                fileSize = (data["fileSize"] as? Number)?.toLong() ?: 0L,
                pageCount = (data["pageCount"] as? Number)?.toInt() ?: 1,
                errorMessage = data["errorMessage"] as? String ?: "",
                retryCount = (data["retryCount"] as? Number)?.toInt() ?: 0,
                metadata = data["metadata"] as? Map<String, Any> ?: emptyMap()
            )
        }

        fun generateInvoiceNumber(): String {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
            val uniqueId = UUID.randomUUID().toString().substring(0, 8).uppercase()
            return "INV-$year$month-$uniqueId"
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ORDER SNAPSHOT
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class OrderSnapshot(
    val orderId: String = "",
    val orderNumber: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val pharmacyId: String = "",
    val pharmacyName: String = "",
    val pharmacyAddress: String = "",
    val pharmacyPhone: String = "",
    val items: List<OrderItemSnapshot> = emptyList(),
    val subtotal: Int = 0,
    val taxes: Int = 0,
    val deliveryFee: Int = 0,
    val totalAmount: Int = 0,
    val deliveryType: String = "",
    val deliveryAddress: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = 0L,
    val orderDate: String = "",
    val status: String = "",
    val notes: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "orderId" to orderId,
            "orderNumber" to orderNumber,
            "userId" to userId,
            "userName" to userName,
            "userEmail" to userEmail,
            "pharmacyId" to pharmacyId,
            "pharmacyName" to pharmacyName,
            "pharmacyAddress" to pharmacyAddress,
            "pharmacyPhone" to pharmacyPhone,
            "items" to items.map { it.toMap() },
            "subtotal" to subtotal,
            "taxes" to taxes,
            "deliveryFee" to deliveryFee,
            "totalAmount" to totalAmount,
            "deliveryType" to deliveryType,
            "deliveryAddress" to deliveryAddress,
            "phoneNumber" to phoneNumber,
            "createdAt" to createdAt,
            "orderDate" to orderDate,
            "status" to status,
            "notes" to notes
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): OrderSnapshot {
            return OrderSnapshot(
                orderId = data["orderId"] as? String ?: "",
                orderNumber = data["orderNumber"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                userName = data["userName"] as? String ?: "",
                userEmail = data["userEmail"] as? String ?: "",
                pharmacyId = data["pharmacyId"] as? String ?: "",
                pharmacyName = data["pharmacyName"] as? String ?: "",
                pharmacyAddress = data["pharmacyAddress"] as? String ?: "",
                pharmacyPhone = data["pharmacyPhone"] as? String ?: "",
                items = (data["items"] as? List<Map<String, Any>>)?.map {
                    OrderItemSnapshot.fromMap(it)
                } ?: emptyList(),
                subtotal = (data["subtotal"] as? Number)?.toInt() ?: 0,
                taxes = (data["taxes"] as? Number)?.toInt() ?: 0,
                deliveryFee = (data["deliveryFee"] as? Number)?.toInt() ?: 0,
                totalAmount = (data["totalAmount"] as? Number)?.toInt() ?: 0,
                deliveryType = data["deliveryType"] as? String ?: "",
                deliveryAddress = data["deliveryAddress"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                orderDate = data["orderDate"] as? String ?: "",
                status = data["status"] as? String ?: "",
                notes = data["notes"] as? String ?: ""
            )
        }

        fun fromMedicationOrder(order: MedicationOrder, userName: String = "", userEmail: String = ""): OrderSnapshot {
            return OrderSnapshot(
                orderId = order.id,
                orderNumber = order.id.take(8).uppercase(),
                userId = order.userId,
                userName = userName,
                userEmail = userEmail,
                pharmacyId = order.pharmacyId,
                pharmacyName = order.pharmacyName,
                pharmacyAddress = order.pharmacyAddress,
                pharmacyPhone = "",
                // ===== CORRECCIÓN AQUÍ =====
                // Se cambió fromCartItem a fromOrderItem para que coincida el tipo de dato.
                items = order.items.map { OrderItemSnapshot.fromOrderItem(it) },
                subtotal = order.totalAmount,
                taxes = (order.totalAmount * 0.19).toInt(), // IVA 19%
                deliveryFee = if (order.deliveryType == DeliveryType.HOME_DELIVERY) 5000 else 0,
                totalAmount = order.totalAmount + (order.totalAmount * 0.19).toInt() +
                        if (order.deliveryType == DeliveryType.HOME_DELIVERY) 5000 else 0,
                deliveryType = order.deliveryType.name,
                deliveryAddress = order.deliveryAddress,
                phoneNumber = order.phoneNumber,
                createdAt = order.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                orderDate = order.createdAt?.toDate()?.let {
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                } ?: "",
                status = order.status.name,
                notes = order.notes
            )
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ORDER ITEM SNAPSHOT
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class OrderItemSnapshot(
    val medicationId: String = "",
    val medicationName: String = "",
    val quantity: Int = 0,
    val pricePerUnit: Int = 0,
    val subtotal: Int = 0,
    val principioActivo: String = "",
    val presentacion: String = "",
    val laboratorio: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "medicationId" to medicationId,
            "medicationName" to medicationName,
            "quantity" to quantity,
            "pricePerUnit" to pricePerUnit,
            "subtotal" to subtotal,
            "principioActivo" to principioActivo,
            "presentacion" to presentacion,
            "laboratorio" to laboratorio
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): OrderItemSnapshot {
            return OrderItemSnapshot(
                medicationId = data["medicationId"] as? String ?: "",
                medicationName = data["medicationName"] as? String ?: "",
                quantity = (data["quantity"] as? Number)?.toInt() ?: 0,
                pricePerUnit = (data["pricePerUnit"] as? Number)?.toInt() ?: 0,
                subtotal = (data["subtotal"] as? Number)?.toInt() ?: 0,
                principioActivo = data["principioActivo"] as? String ?: "",
                presentacion = data["presentacion"] as? String ?: "",
                laboratorio = data["laboratorio"] as? String ?: ""
            )
        }

        fun fromCartItem(item: CartItem): OrderItemSnapshot {
            return OrderItemSnapshot(
                medicationId = item.medicationId,
                medicationName = item.medicationName,
                quantity = item.quantity,
                pricePerUnit = item.pricePerUnit,
                subtotal = item.getSubtotal(),
                principioActivo = item.principioActivo,
                presentacion = item.presentacion,
                laboratorio = item.laboratorio
            )
        }


        fun fromOrderItem(item: OrderItem): OrderItemSnapshot {
            return OrderItemSnapshot(
                medicationId = item.medicationId,
                medicationName = item.medicationName,
                quantity = item.quantity,
                pricePerUnit = item.pricePerUnit, // Asumiendo que el campo se llama 'price' en OrderItem
                subtotal = item.quantity * item.pricePerUnit,
                principioActivo = item.principioActivo ?: "",
                presentacion = item.presentacion ?: "",
                laboratorio = item.laboratorio ?: ""
            )
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PENDING INVOICE
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class PendingInvoice(
    val orderId: String,
    val orderSnapshot: OrderSnapshot,
    val timestamp: Long = System.currentTimeMillis(),
    val localPdfPath: String = "",
    val retryCount: Int = 0
)

