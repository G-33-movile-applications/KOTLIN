package com.mobile.mymeds.repository

import com.mobile.mymeds.data.local.room.dao.MedicationStockDao
import com.mobile.mymeds.data.local.room.entitites.MedicationStockEntity

class StockCacheRepository(private val stockCacheDao: MedicationStockDao) {

    /**
     * Añade un item a la caché para ser monitoreado por el Worker.
     * Esta función se llamaría cuando el usuario presiona "Avísame".
     */
    suspend fun followItem(
        inventoryId: String,
        medicationName: String,
        pharmacyId: String,
        pharmacyName: String
    ) {
        val newItemToCache = MedicationStockEntity(
            inventoryId = inventoryId,
            medicationName = medicationName,
            pharmacyId = pharmacyId,
            pharmacyName = pharmacyName,
            lastKnownStock = 0, // Lo marcamos como sin stock
            lastCheckedTimestamp = System.currentTimeMillis()
        )
        stockCacheDao.cacheStockStatus(listOf(newItemToCache))
    }
}
