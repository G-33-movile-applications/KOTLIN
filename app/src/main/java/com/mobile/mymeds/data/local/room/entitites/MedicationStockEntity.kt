package com.mobile.mymeds.data.local.room.entitites

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_stock_cache")
data class MedicationStockEntity (
    @PrimaryKey
    val inventoryId: String,
    val medicationName: String,
    val pharmacyId: String,
    val pharmacyName: String,
    val lastKnownStock: Int,
    val lastCheckedTimestamp: Long
)