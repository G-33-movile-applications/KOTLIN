package com.mobile.mymeds.models

import com.google.firebase.Timestamp

data class MedicineUser(
    val id: String = "",
    val activo: Boolean = false,
    val dosisMg: Int = 0,
    val fechaFin: Timestamp? = null,
    val fechaInicio: Timestamp? = null,
    val frecuenciaHoras: Int = 0,
    val medicamentoRef: String = "",
    val nombre: String = "",
    val prescripcionId: String = ""
)