package com.mobile.mymeds.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobile.mymeds.data.local.room.entitites.NfcPrescriptionEntity

@Dao
interface NfcPrescriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prescription: NfcPrescriptionEntity)

    @Query("SELECT * FROM pending_nfc_prescriptions")
    suspend fun getAll(): List<NfcPrescriptionEntity>

    @Query("DELETE FROM pending_nfc_prescriptions WHERE id = :prescriptionId")
    suspend fun deleteById(prescriptionId: Int)
}
