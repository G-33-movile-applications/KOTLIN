package com.mobile.mymeds.data.local.room.entitites

import androidx.room.Entity
import androidx.room.PrimaryKey

// Acá se guardará la prescripción NFC cuando no haya red
@Entity(tableName = "pending_nfc_prescriptions")
data class NfcPrescriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String,
    val nfcDataJson: String // String que será después el JSON
)