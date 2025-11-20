
package com.mobile.mymeds.models

data class Medication(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val principioActivo: String = "",
    val presentacion: String = "",
    val laboratorio: String = "",
    val imagenUrl: String = "",
    val contraindicaciones: List<String> = emptyList()
)