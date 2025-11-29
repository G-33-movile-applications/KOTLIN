package com.mobile.mymeds.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mobile.mymeds.data.local.room.entitites.MedicationStockEntity

@Dao
interface MedicationStockDao {
    /**
     * Inserta una lista de items en la caché. Si un item ya existe (misma inventoryId),
     * lo actualiza.
     */
    @Upsert
    suspend fun cacheStockStatus(stockStatus: List<MedicationStockEntity>)

    /**
     * Obtiene todos los items cacheados que el usuario sigue y están marcados como "sin stock".
     * Estos son nuestros candidatos para verificar en la red si han vuelto a tener stock.
     */
    @Query("SELECT * FROM medication_stock_cache WHERE lastKnownStock <= 0")
    suspend fun getOutOfStockItems(): List<MedicationStockEntity>
}