package com.mobile.mymeds.models

data class PrescriptionMedication(
    val id: String = "",
    val medicationRef: String? = null,
    val name: String = "",
    val doseMg: Int = 0,
    val frequencyHours: Int = 24,
    val quantity: Int = 1
)
