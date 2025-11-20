package com.mobile.mymeds.models

import com.google.firebase.Timestamp

/**
 * ═══════════════════════════════════════════════════════════════════════
 * ORDER MODELS - MODELOS DE PEDIDOS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Estos modelos conectan el inventario de farmacias con los pedidos de usuarios
 */

/**
 * Pedido de medicamentos de un usuario
 *
 * Firebase path: /usuarios/{userId}/pedidos/{orderId}
 */
data class MedicationOrder(
    val id: String = "",
    val userId: String = "",
    val pharmacyId: String = "",
    val pharmacyName: String = "",
    val pharmacyAddress: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalAmount: Int = 0,
    val status: OrderStatus = OrderStatus.PENDING,
    val deliveryType: DeliveryType = DeliveryType.IN_PERSON_PICKUP,
    val deliveryAddress: String = "",
    val phoneNumber: String = "",
    val notes: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val estimatedDeliveryDate: Timestamp? = null
) {
    /**
     * Calcula el total del pedido
     */
    fun calculateTotal(): Int {
        return items.sumOf { it.quantity * it.pricePerUnit }
    }

    /**
     * Verifica si el pedido está activo
     */
    fun isActive(): Boolean {
        return status in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.IN_TRANSIT)
    }

    /**
     * Verifica si el pedido puede ser cancelado
     */
    fun canBeCancelled(): Boolean {
        return status in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)
    }
}

/**
 * Item individual en un pedido
 */
data class OrderItem(
    val medicationId: String = "",
    val medicationRef: String = "", // Referencia al medicamento en medicamentosGlobales
    val medicationName: String = "",
    val quantity: Int = 0,
    val pricePerUnit: Int = 0,
    val batch: String = "",

    // Información adicional del medicamento
    val principioActivo: String = "",
    val presentacion: String = "",
    val laboratorio: String = ""
) {
    /**
     * Calcula el subtotal de este item
     */
    fun getSubtotal(): Int = quantity * pricePerUnit

    companion object {
        /**
         * Crea un OrderItem a partir de un InventoryMedication
         */
        fun fromInventoryMedication(
            medication: InventoryMedication,
            quantity: Int
        ): OrderItem {
            return OrderItem(
                medicationId = medication.id,
                medicationRef = medication.medicamentoRef,
                medicationName = medication.nombre,
                quantity = quantity,
                pricePerUnit = medication.precioUnidad,
                batch = medication.lote,
                principioActivo = medication.principioActivo,
                presentacion = medication.presentacion,
                laboratorio = medication.laboratorio
            )
        }
    }
}

/**
 * Estados posibles de un pedido
 */
enum class OrderStatus {
    PENDING,        // Pendiente de confirmación
    CONFIRMED,      // Confirmado por la farmacia
    IN_TRANSIT,     // En camino (para delivery)
    READY_PICKUP,   // Listo para recoger (para pickup)
    DELIVERED,      // Entregado
    COMPLETED,      // Completado
    CANCELLED       // Cancelado
}

/**
 * Tipos de entrega
 */
enum class DeliveryType {
    HOME_DELIVERY,      // Entrega a domicilio
    IN_PERSON_PICKUP    // Recoger en persona
}

/**
 * Carrito de compras (antes de crear el pedido)
 * Este se almacena localmente mientras el usuario está comprando
 */
data class ShoppingCart(
    val pharmacyId: String = "",
    val pharmacyName: String = "",
    val items: MutableList<CartItem> = mutableListOf()
) {
    /**
     * Agrega un item al carrito
     */
    fun addItem(medication: InventoryMedication, quantity: Int = 1) {
        val existingItem = items.find { it.medicationId == medication.id }

        if (existingItem != null) {
            // Si ya existe, aumentar cantidad
            existingItem.quantity += quantity
        } else {
            // Si no existe, agregar nuevo
            items.add(CartItem.fromInventoryMedication(medication, quantity))
        }
    }

    /**
     * Remueve un item del carrito
     */
    fun removeItem(medicationId: String) {
        items.removeAll { it.medicationId == medicationId }
    }

    /**
     * Actualiza la cantidad de un item
     */
    fun updateQuantity(medicationId: String, newQuantity: Int) {
        val item = items.find { it.medicationId == medicationId }
        if (item != null) {
            if (newQuantity <= 0) {
                removeItem(medicationId)
            } else {
                item.quantity = newQuantity
            }
        }
    }

    /**
     * Calcula el total del carrito
     */
    fun calculateTotal(): Int {
        return items.sumOf { it.getSubtotal() }
    }

    /**
     * Limpia el carrito
     */
    fun clear() {
        items.clear()
    }

    /**
     * Verifica si el carrito está vacío
     */
    fun isEmpty(): Boolean = items.isEmpty()

    /**
     * Obtiene el número total de items
     */
    fun getTotalItems(): Int = items.sumOf { it.quantity }

    /**
     * Convierte el carrito en un pedido
     */
    fun toOrder(
        userId: String,
        pharmacyAddress: String,
        deliveryType: DeliveryType,
        deliveryAddress: String = "",
        phoneNumber: String = "",
        notes: String = ""
    ): MedicationOrder {
        return MedicationOrder(
            userId = userId,
            pharmacyId = pharmacyId,
            pharmacyName = pharmacyName,
            pharmacyAddress = pharmacyAddress,
            items = items.map { it.toOrderItem() },
            totalAmount = calculateTotal(),
            status = OrderStatus.PENDING,
            deliveryType = deliveryType,
            deliveryAddress = deliveryAddress,
            phoneNumber = phoneNumber,
            notes = notes,
            createdAt = Timestamp.now()
        )
    }
}

/**
 * Item en el carrito de compras
 */
data class CartItem(
    val medicationId: String = "",
    val medicationRef: String = "",
    val medicationName: String = "",
    var quantity: Int = 0,
    val pricePerUnit: Int = 0,
    val stock: Int = 0, // Stock disponible
    val batch: String = "",

    // Información adicional
    val principioActivo: String = "",
    val presentacion: String = "",
    val laboratorio: String = "",
    val imagenUrl: String = ""
) {
    /**
     * Calcula el subtotal
     */
    fun getSubtotal(): Int = quantity * pricePerUnit

    /**
     * Verifica si la cantidad excede el stock
     */
    fun exceedsStock(): Boolean = quantity > stock

    /**
     * Convierte a OrderItem
     */
    fun toOrderItem(): OrderItem {
        return OrderItem(
            medicationId = medicationId,
            medicationRef = medicationRef,
            medicationName = medicationName,
            quantity = quantity,
            pricePerUnit = pricePerUnit,
            batch = batch,
            principioActivo = principioActivo,
            presentacion = presentacion,
            laboratorio = laboratorio
        )
    }

    companion object {
        /**
         * Crea un CartItem a partir de un InventoryMedication
         */
        fun fromInventoryMedication(
            medication: InventoryMedication,
            quantity: Int = 1
        ): CartItem {
            return CartItem(
                medicationId = medication.id,
                medicationRef = medication.medicamentoRef,
                medicationName = medication.nombre,
                quantity = quantity,
                pricePerUnit = medication.precioUnidad,
                stock = medication.stock,
                batch = medication.lote,
                principioActivo = medication.principioActivo,
                presentacion = medication.presentacion,
                laboratorio = medication.laboratorio
            )
        }
    }
}

/**
 * Resumen de pedido para mostrar en listas
 */
data class OrderSummary(
    val orderId: String,
    val pharmacyName: String,
    val totalAmount: Int,
    val itemCount: Int,
    val status: OrderStatus,
    val createdAt: Timestamp?,
    val deliveryType: DeliveryType
) {
    companion object {
        fun fromOrder(order: MedicationOrder): OrderSummary {
            return OrderSummary(
                orderId = order.id,
                pharmacyName = order.pharmacyName,
                totalAmount = order.totalAmount,
                itemCount = order.items.size,
                status = order.status,
                createdAt = order.createdAt,
                deliveryType = order.deliveryType
            )
        }
    }
}

/**
 * Filtros para pedidos
 */
data class OrderFilters(
    val status: OrderStatus? = null,
    val deliveryType: DeliveryType? = null,
    val pharmacyId: String? = null,
    val dateFrom: Timestamp? = null,
    val dateTo: Timestamp? = null
)