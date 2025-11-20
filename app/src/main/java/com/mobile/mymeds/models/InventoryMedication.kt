package com.mobile.mymeds.models

import com.google.firebase.Timestamp

data class InventoryMedication(
    val id: String = "",
    val nombre: String = "",
    val medicamentoRef: String = "",
    val proveedor: String = "",
    val lote: String = "",
    val stock: Int = 0,
    val precioUnidad: Int = 0,
    val fechaIngreso: Timestamp? = null,
    val fechaVencimiento: Timestamp? = null,

    val descripcion: String = "",
    val principioActivo: String = "",
    val presentacion: String = "",
    val laboratorio: String = "",
    val contraindicaciones: List<String> = emptyList()
)