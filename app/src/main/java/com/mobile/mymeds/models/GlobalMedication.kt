package com.mobile.mymeds.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.mobile.mymeds.data.local.room.converters.Converters
import com.google.firebase.firestore.DocumentId

@Entity(tableName = "medicamentos_globales")
@TypeConverters(Converters::class)
data class GlobalMedication(
    @PrimaryKey
    @DocumentId
    val id: String = "",
    
    val nombre: String = "",
    val descripcion: String = "",
    val laboratorio: String = "",
    val principioActivo: String = "",
    val presentacion: String = "",
    val imagenUrl: String = "",
    val contraindicaciones: List<String> = emptyList()
)

