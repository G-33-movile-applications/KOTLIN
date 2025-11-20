package com.mobile.mymeds.data.local.room.entitites

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index // <-- 1. ADD THIS IMPORT
import androidx.room.PrimaryKey
import com.mobile.mymeds.data.local.room.entities.OrderEntity

/**
 * Entidad Room para Ítems de Pedidos
 * Representa un ítem dentro de un pedido (relación Many-to-Many)
 */
@Entity(
    tableName = "order_items",
    // V-- 3. WRAP FOREIGN KEYS IN AN ARRAY [ ... ] --V
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["orderId"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["medicationId"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    // ^-- END OF FOREIGN KEY ARRAY --^
    indices = [Index(value = ["orderId"]), Index(value = ["medicationId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val itemId: Long = 0,
    val orderId: Long,
    val medicationId: String,
    val quantity: Int,
    val medicationName: String,
    val doseMg: Int
)
