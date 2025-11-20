package com.mobile.mymeds.repository

import android.util.Log
import com.mobile.mymeds.data.local.room.dao.GlobalMedicationDao
import com.mobile.mymeds.models.GlobalMedication
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio para manejar la lista de medicamentos globales.
 * mediador entre data sources (Firestore, Room) y los ViewModels.
 * implementa la estrategia Lazy loading
 */
class GlobalMedicationRepository(
    private val firestore: FirebaseFirestore,
    private val globalMedicationDao: GlobalMedicationDao
) {

    /**
     * Flow que va al cache, si algun componente de UI la observa
     * tiene la data inmediata con actualizaciones cuando el cache cambie
     */
    val allMedications: Flow<List<GlobalMedication>> = globalMedicationDao.getAll()

    /**
     * Cache-Aside trigger, es la que actualiza el cache local contra firestore
     */
    suspend fun refreshMedications() {
        try {
            //Trae la info de manera asincrónica
            val remoteMedications = firestore.collection("medicamentosGlobales")
                .get()
                .await()
                .toObjects(GlobalMedication::class.java)

            globalMedicationDao.insertAll(remoteMedications)

            Log.d("GlobalMedicationRepo", "Cache successfully refreshed with ${remoteMedications.size} items.")

        } catch (e: Exception) {
            // Si no hay conexión solo se registra que no se pudo refrescar, se usa cache local
            Log.e("GlobalMedicationRepo", "Failed to refresh cache from Firestore: ${e.message}")
        }
    }
}
