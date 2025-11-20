package com.mobile.mymeds.models

import com.google.firebase.Timestamp

data class Order(
    val id: String = "",
    val direccionEntrega: String = "",
    val estado: String = "",
    val fechaEntrega: Timestamp? = null,
    val fechaPedido: Timestamp? = null,
    val prescripcionId: String = "",
    val puntoFisicoId: String = "",
    val tipoEntrega: String = ""
)