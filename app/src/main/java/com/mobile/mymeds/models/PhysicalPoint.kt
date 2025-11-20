package com.mobile.mymeds.models

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

data class PhysicalPoint(
    @get:PropertyName("id")
    @set:PropertyName("id")
    var id: String = "",

    @get:PropertyName("nombre")
    @set:PropertyName("nombre")
    var name: String = "",

    @get:PropertyName("direccion")
    @set:PropertyName("direccion")
    var address: String = "",

    @get:PropertyName("ubicacion")
    @set:PropertyName("ubicacion")
    var location: GeoPoint = GeoPoint(0.0, 0.0),

    @get:PropertyName("horario")
    @set:PropertyName("horario")
    var openingHours: String = "",

    @get:PropertyName("localidad")
    @set:PropertyName("localidad")
    var locality: String = "",

    @get:PropertyName("telefono")
    @set:PropertyName("telefono")
    var phone: String = ""
)