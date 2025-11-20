package com.mobile.mymeds.data.local.room.entitites


import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room para Medicamentos
 * Representa un medicamento en la base de datos local
 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey
    val medicationId: String,
    val name: String,
    val doseMg: Int,
    val frequencyHours: Int,
    val stockQuantity: Int,
    val prescriptionId: String,
    val active: Boolean,
    val lastSyncedAt: Long,
    val firebaseDocId: String? = null
)