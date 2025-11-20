package com.mobile.mymeds.models

import com.google.firebase.Timestamp

data class Prescription(
    val id: String = "",
    val activa: Boolean = false,
    val diagnostico: String = "",
    val fechaCreacion: Timestamp? = null,
    val medico: String = ""
)