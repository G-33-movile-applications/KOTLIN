package com.mobile.mymeds.repository

import android.util.Log
import com.mobile.mymeds.models.PhysicalPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await

class PhysicalPointsRepository {

    private val pointsCollection = FirebaseFirestore.getInstance().collection("puntosFisicos")

    suspend fun getAllPhysicalPoints(): Result<List<PhysicalPoint>> {
        return try {
            Log.d("Repository", "Fetching from Firestore collection: puntosFisicos")

            val documents = pointsCollection.get().await()

            Log.d("Repository", "Documents received: ${documents.size()}")

            val pointsList = documents.documents.mapNotNull { doc ->
                try {
                    val id = doc.id // <- AGREGAR ESTA LÍNEA
                    val nombre = doc.getString("nombre") ?: ""
                    val direccion = doc.getString("direccion") ?: ""
                    val ubicacion = doc.getGeoPoint("ubicacion") ?: GeoPoint(0.0, 0.0)
                    val horario = doc.getString("horario") ?: ""
                    val localidad = doc.getString("localidad") ?: ""
                    val telefono = doc.getString("telefono") ?: ""

                    Log.d("Repository", "Processing: ID=$id, $nombre at (${ubicacion.latitude}, ${ubicacion.longitude})")

                    PhysicalPoint(
                        id = id, // <- AGREGAR ESTA LÍNEA
                        name = nombre,
                        address = direccion,
                        location = ubicacion,
                        openingHours = horario,
                        locality = localidad,
                        phone = telefono
                    )
                } catch (e: Exception) {
                    Log.e("Repository", "Error mapping document ${doc.id}: ${e.message}", e)
                    null
                }
            }

            Log.d("Repository", "Successfully converted ${pointsList.size} points")
            pointsList.forEachIndexed { index, point ->
                Log.d("Repository", "Point $index: ID=${point.id}, ${point.name} at (${point.location.latitude}, ${point.location.longitude})")
            }

            Result.success(pointsList)
        } catch (e: Exception) {
            Log.e("Repository", "ERROR fetching points: ${e.message}", e)
            Result.failure(e)
        }
    }
}