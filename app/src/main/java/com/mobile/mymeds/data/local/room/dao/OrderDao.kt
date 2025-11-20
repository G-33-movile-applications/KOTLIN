package com.mobile.mymeds.data.local.room.dao

import androidx.room.*
import com.mobile.mymeds.data.local.room.entities.OrderEntity
import com.mobile.mymeds.models.OrderStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de base de datos de Pedidos
 */
@Dao
interface OrderDao {

    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY orderDate DESC")
    fun getUserOrders(userId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE orderId = :id")
    suspend fun getOrderById(id: Long): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status WHERE orderId = :id")
    suspend fun updateOrderStatus(id: Long, status: OrderStatus)

    @Query("DELETE FROM orders WHERE orderId = :id")
    suspend fun deleteOrder(id: Long)
}