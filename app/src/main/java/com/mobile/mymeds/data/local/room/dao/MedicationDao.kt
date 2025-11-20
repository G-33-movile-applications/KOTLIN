package com.mobile.mymeds.data.local.room.dao

import androidx.room.*
import com.mobile.mymeds.data.local.room.entitites.MedicationEntity

import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de base de datos de Medicamentos
 */
@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name ASC")
    fun getAllActiveMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE medicationId = :id")
    suspend fun getMedicationById(id: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Query("UPDATE medications SET stockQuantity = :quantity WHERE medicationId = :id")
    suspend fun updateStock(id: String, quantity: Int)

    @Query("DELETE FROM medications WHERE medicationId = :id")
    suspend fun deleteMedication(id: String)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}