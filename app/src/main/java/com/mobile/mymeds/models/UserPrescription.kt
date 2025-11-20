package com.mobile.mymeds.models

import java.util.Date

data class UserPrescription(
    val id: String = "",
    val fileName: String = "",
    val totalItems: Int = 0,
    val uploadedAt: Date? = null,
    val status: String = "pendiente"
)
