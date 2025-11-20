package com.mobile.mymeds.data.local.room.dao

import androidx.room.*
import com.mobile.mymeds.data.local.room.entitites.OrderItemEntity

/**
 * DAO para operaciones de ítems de pedidos
 */
@Dao
interface OrderItemDao {

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: Long): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)
}