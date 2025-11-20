package com.mobile.mymeds.data.local.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mobile.mymeds.models.DeliveryType
import com.mobile.mymeds.models.OrderStatus

/**
 * Entidad Room para Pedidos
 * Representa un pedido de medicamentos
 */
@Entity(
    tableName = "orders",
    indices = [Index(value = ["userId"])]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val orderId: Long = 0,
    val userId: String,
    val orderDate: Long,
    val deliveryType: DeliveryType,
    val deliveryAddress: String?,
    val status: OrderStatus,
    val totalItems: Int,
    val notes: String?,
    val createdAt: Long,
    val syncedToFirebase: Boolean = false,
    val firebaseOrderId: String? = null
)