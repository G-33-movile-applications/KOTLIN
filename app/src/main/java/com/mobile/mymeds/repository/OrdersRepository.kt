package com.mobile.mymeds.repository

import android.util.Log
import com.mobile.mymeds.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ═══════════════════════════════════════════════════════════════════════
 * ORDERS REPOSITORY
 * ═══════════════════════════════════════════════════════════════════════
 * 
 * Este repositorio maneja la conexión entre:
 * - Inventario de farmacias (/puntosFisicos/{id}/inventario)
 * - Pedidos de usuarios (/usuarios/{userId}/pedidos)
 * 
 * Flujo completo:
 * 1. Usuario ve inventario de farmacia
 * 2. Agrega medicamentos al carrito
 * 3. Crea pedido con los items del carrito
 * 4. Pedido se guarda en Firebase
 * 5. Stock se actualiza en inventario de farmacia
 */
class OrdersRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val inventoryRepo = PharmacyInventoryRepository()
    
    companion object {
        private const val TAG = "OrdersRepository"
        private const val COLLECTION_USERS = "usuarios"
        private const val SUBCOLLECTION_ORDERS = "pedidos"
        private const val COLLECTION_PHYSICAL_POINTS = "puntosFisicos"
        private const val SUBCOLLECTION_INVENTORY = "inventario"
    }
    
    /**
     * ═════════════════════════════════════════════════════════════════
     * CREAR PEDIDOS - CREATE ORDERS
     * ═════════════════════════════════════════════════════════════════
     */
    
    /**
     * Crea un nuevo pedido a partir del carrito de compras
     * 
     * Proceso:
     * 1. Validar disponibilidad de stock
     * 2. Crear documento de pedido en Firebase
     * 3. Actualizar stock en inventario de farmacia
     * 4. Retornar ID del pedido creado
     * 
     * @param cart Carrito de compras con los items
     * @param userId ID del usuario
     * @param pharmacy Información de la farmacia
     * @param deliveryType Tipo de entrega
     * @param deliveryAddress Dirección de entrega (si aplica)
     * @param phoneNumber Teléfono de contacto
     * @param notes Notas adicionales
     */
    suspend fun createOrder(
        cart: ShoppingCart,
        userId: String,
        pharmacy: PhysicalPoint,
        deliveryType: DeliveryType,
        deliveryAddress: String = "",
        phoneNumber: String = "",
        notes: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "═════════════════════════════════════════════════════════")
            Log.d(TAG, "CREANDO PEDIDO")
            Log.d(TAG, "Usuario: $userId")
            Log.d(TAG, "Farmacia: ${pharmacy.name}")
            Log.d(TAG, "Items: ${cart.items.size}")
            Log.d(TAG, "═════════════════════════════════════════════════════════")
            
            // PASO 1: Validar disponibilidad de stock
            Log.d(TAG, "PASO 1: Validando disponibilidad de stock...")
            val stockValidation = validateStock(pharmacy.id, cart.items)
            if (!stockValidation.isValid) {
                Log.e(TAG, "❌ Stock insuficiente: ${stockValidation.message}")
                return@withContext Result.failure(
                    Exception("Stock insuficiente: ${stockValidation.message}")
                )
            }
            Log.d(TAG, "✅ Stock validado correctamente")
            
            // PASO 2: Crear el pedido
            Log.d(TAG, "PASO 2: Creando documento de pedido...")
            val order = cart.toOrder(
                userId = userId,
                pharmacyAddress = pharmacy.address,
                deliveryType = deliveryType,
                deliveryAddress = deliveryAddress,
                phoneNumber = phoneNumber,
                notes = notes
            )
            
            // Guardar en Firebase
            val orderRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ORDERS)
                .add(hashMapOf(
                    "userId" to order.userId,
                    "pharmacyId" to order.pharmacyId,
                    "pharmacyName" to order.pharmacyName,
                    "pharmacyAddress" to order.pharmacyAddress,
                    "items" to order.items.map { item ->
                        hashMapOf(
                            "medicationId" to item.medicationId,
                            "medicationRef" to item.medicationRef,
                            "medicationName" to item.medicationName,
                            "quantity" to item.quantity,
                            "pricePerUnit" to item.pricePerUnit,
                            "batch" to item.batch,
                            "principioActivo" to item.principioActivo,
                            "presentacion" to item.presentacion,
                            "laboratorio" to item.laboratorio
                        )
                    },
                    "totalAmount" to order.totalAmount,
                    "status" to order.status.name,
                    "deliveryType" to order.deliveryType.name,
                    "deliveryAddress" to order.deliveryAddress,
                    "phoneNumber" to order.phoneNumber,
                    "notes" to order.notes,
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                ))
                .await()
            
            val orderId = orderRef.id
            Log.d(TAG, "✅ Pedido creado con ID: $orderId")
            
            // PASO 3: Actualizar stock en inventario
            Log.d(TAG, "PASO 3: Actualizando stock en inventario...")
            updateInventoryStock(pharmacy.id, cart.items)
            Log.d(TAG, "✅ Stock actualizado")
            
            Log.d(TAG, "═════════════════════════════════════════════════════════")
            Log.d(TAG, "✅ ÉXITO: Pedido creado exitosamente")
            Log.d(TAG, "ID del pedido: $orderId")
            Log.d(TAG, "═════════════════════════════════════════════════════════")
            
            Result.success(orderId)
            
        } catch (e: Exception) {
            Log.e(TAG, "═════════════════════════════════════════════════════════")
            Log.e(TAG, "❌ ERROR: Error creando pedido: ${e.message}", e)
            Log.e(TAG, "═════════════════════════════════════════════════════════")
            Result.failure(e)
        }
    }
    
    /**
     * Valida que haya stock suficiente para todos los items
     */
    private suspend fun validateStock(
        pharmacyId: String,
        items: List<CartItem>
    ): StockValidationResult {
        return try {
            for (item in items) {
                // Obtener stock actual del inventario
                val inventorySnapshot = firestore
                    .collection(COLLECTION_PHYSICAL_POINTS)
                    .document(pharmacyId)
                    .collection(SUBCOLLECTION_INVENTORY)
                    .document(item.medicationId)
                    .get()
                    .await()
                
                val currentStock = inventorySnapshot.getLong("stock")?.toInt() ?: 0
                
                if (currentStock < item.quantity) {
                    return StockValidationResult(
                        isValid = false,
                        message = "${item.medicationName}: Solo hay $currentStock unidades disponibles"
                    )
                }
            }
            
            StockValidationResult(isValid = true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validando stock: ${e.message}", e)
            StockValidationResult(
                isValid = false,
                message = "Error validando disponibilidad: ${e.message}"
            )
        }
    }
    
    /**
     * Actualiza el stock en el inventario después de crear el pedido
     */
    private suspend fun updateInventoryStock(
        pharmacyId: String,
        items: List<CartItem>
    ) {
        try {
            for (item in items) {
                val inventoryRef = firestore
                    .collection(COLLECTION_PHYSICAL_POINTS)
                    .document(pharmacyId)
                    .collection(SUBCOLLECTION_INVENTORY)
                    .document(item.medicationId)
                
                // Reducir stock
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(inventoryRef)
                    val currentStock = snapshot.getLong("stock")?.toInt() ?: 0
                    val newStock = (currentStock - item.quantity).coerceAtLeast(0)
                    
                    transaction.update(inventoryRef, "stock", newStock)
                }.await()
                
                Log.d(TAG, "Stock actualizado para ${item.medicationName}: -${item.quantity}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando stock: ${e.message}", e)
            // No lanzar excepción aquí porque el pedido ya se creó
            // Solo registrar el error
        }
    }
    
    /**
     * ═════════════════════════════════════════════════════════════════
     * CONSULTAR PEDIDOS - GET ORDERS
     * ═════════════════════════════════════════════════════════════════
     */
    
    /**
     * Obtiene todos los pedidos de un usuario
     * 
     * @param userId ID del usuario
     * @param filters Filtros opcionales
     */
    suspend fun getUserOrders(
        userId: String,
        filters: OrderFilters? = null
    ): Result<List<MedicationOrder>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Cargando pedidos para usuario: $userId")
            
            var query: Query = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ORDERS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
            
            // Aplicar filtros
            filters?.let { f ->
                f.status?.let { status ->
                    query = query.whereEqualTo("status", status.name)
                }
                f.deliveryType?.let { type ->
                    query = query.whereEqualTo("deliveryType", type.name)
                }
                f.pharmacyId?.let { pharmacyId ->
                    query = query.whereEqualTo("pharmacyId", pharmacyId)
                }
            }
            
            val snapshot = query.get().await()
            
            val orders = snapshot.documents.mapNotNull { doc ->
                try {
                    parseOrderDocument(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando pedido ${doc.id}: ${e.message}")
                    null
                }
            }
            
            Log.d(TAG, "✅ ${orders.size} pedidos cargados")
            Result.success(orders)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando pedidos: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene un pedido específico
     */
    suspend fun getOrderById(
        userId: String,
        orderId: String
    ): Result<MedicationOrder> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ORDERS)
                .document(orderId)
                .get()
                .await()
            
            val order = parseOrderDocument(snapshot.id, snapshot.data ?: emptyMap())
            Result.success(order)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo pedido: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parsea un documento de pedido de Firebase
     */
    private fun parseOrderDocument(orderId: String, data: Map<String, Any>): MedicationOrder {
        @Suppress("UNCHECKED_CAST")
        val itemsList = (data["items"] as? List<Map<String, Any>>) ?: emptyList()
        
        val items = itemsList.map { itemMap ->
            OrderItem(
                medicationId = itemMap["medicationId"] as? String ?: "",
                medicationRef = itemMap["medicationRef"] as? String ?: "",
                medicationName = itemMap["medicationName"] as? String ?: "",
                quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 0,
                pricePerUnit = (itemMap["pricePerUnit"] as? Long)?.toInt() ?: 0,
                batch = itemMap["batch"] as? String ?: "",
                principioActivo = itemMap["principioActivo"] as? String ?: "",
                presentacion = itemMap["presentacion"] as? String ?: "",
                laboratorio = itemMap["laboratorio"] as? String ?: ""
            )
        }
        
        return MedicationOrder(
            id = orderId,
            userId = data["userId"] as? String ?: "",
            pharmacyId = data["pharmacyId"] as? String ?: "",
            pharmacyName = data["pharmacyName"] as? String ?: "",
            pharmacyAddress = data["pharmacyAddress"] as? String ?: "",
            items = items,
            totalAmount = (data["totalAmount"] as? Long)?.toInt() ?: 0,
            status = OrderStatus.valueOf(data["status"] as? String ?: "PENDING"),
            deliveryType = DeliveryType.valueOf(data["deliveryType"] as? String ?: "IN_PERSON_PICKUP"),
            deliveryAddress = data["deliveryAddress"] as? String ?: "",
            phoneNumber = data["phoneNumber"] as? String ?: "",
            notes = data["notes"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }
    
    /**
     * ═════════════════════════════════════════════════════════════════
     * ACTUALIZAR PEDIDOS - UPDATE ORDERS
     * ═════════════════════════════════════════════════════════════════
     */
    
    /**
     * Actualiza el estado de un pedido
     */
    suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_ORDERS)
                .document(orderId)
                .update(
                    mapOf(
                        "status" to newStatus.name,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            
            Log.d(TAG, "✅ Estado del pedido $orderId actualizado a $newStatus")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cancela un pedido
     */
    suspend fun cancelOrder(
        userId: String,
        orderId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Obtener el pedido
            val orderResult = getOrderById(userId, orderId)
            if (orderResult.isFailure) {
                return@withContext Result.failure(orderResult.exceptionOrNull()!!)
            }
            
            val order = orderResult.getOrNull()!!
            
            // Verificar que puede ser cancelado
            if (!order.canBeCancelled()) {
                return@withContext Result.failure(
                    Exception("El pedido no puede ser cancelado en su estado actual: ${order.status}")
                )
            }
            
            // Actualizar estado
            updateOrderStatus(userId, orderId, OrderStatus.CANCELLED)
            
            // Devolver stock al inventario
            restoreInventoryStock(order.pharmacyId, order.items)
            
            Log.d(TAG, "✅ Pedido $orderId cancelado exitosamente")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando pedido: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Restaura el stock en el inventario (cuando se cancela un pedido)
     */
    private suspend fun restoreInventoryStock(
        pharmacyId: String,
        items: List<OrderItem>
    ) {
        try {
            for (item in items) {
                val inventoryRef = firestore
                    .collection(COLLECTION_PHYSICAL_POINTS)
                    .document(pharmacyId)
                    .collection(SUBCOLLECTION_INVENTORY)
                    .document(item.medicationId)
                
                // Aumentar stock
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(inventoryRef)
                    val currentStock = snapshot.getLong("stock")?.toInt() ?: 0
                    val newStock = currentStock + item.quantity
                    
                    transaction.update(inventoryRef, "stock", newStock)
                }.await()
                
                Log.d(TAG, "Stock restaurado para ${item.medicationName}: +${item.quantity}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando stock: ${e.message}", e)
        }
    }
}

/**
 * Resultado de validación de stock
 */
data class StockValidationResult(
    val isValid: Boolean,
    val message: String = ""
)
