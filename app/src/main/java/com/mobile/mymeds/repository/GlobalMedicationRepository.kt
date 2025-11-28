package com.mobile.mymeds.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.data.local.room.dao.GlobalMedicationDao
import com.mobile.mymeds.models.GlobalMedication
import com.mobile.mymeds.utils.ConnectivityUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio para manejar la lista de medicamentos globales.
 * Mediador entre data sources (Firestore, Room) y los ViewModels.
 * Implementa la estrategia de "Caché-Primero" siendo consciente de la red.
 */
class GlobalMedicationRepository(
    private val firestore: FirebaseFirestore,
    private val globalMedicationDao: GlobalMedicationDao,
    private val context: Context
) {

    /**
     * Flow que expone los datos desde la caché de Room.
     */
    val allMedications: Flow<List<GlobalMedication>> = globalMedicationDao.getAll()

    /**
     * Intenta actualizar la caché local desde Firestore, pero solo si hay conexión.
     */
    suspend fun refreshMedications() {
        if (!ConnectivityUtils.isNetworkAvailable(context)) {
            Log.d("GlobalMedicationRepo", "Sin conexión a internet. Se usará la caché local.")
            return
        }

        try {
            Log.d("GlobalMedicationRepo", "Red disponible. Refrescando caché desde Firestore.")
            val remoteMedications = firestore.collection("medicamentosGlobales")
                .get()
                .await()
                .toObjects(GlobalMedication::class.java)

            globalMedicationDao.clearAll()
            globalMedicationDao.insertAll(remoteMedications)

            Log.d("GlobalMedicationRepo", "Caché refrescada con ${remoteMedications.size} medicamentos.")

        } catch (e: Exception) {
            Log.e("GlobalMedicationRepo", "Error al refrescar la caché desde Firestore: ${e.message}")
        }
    }
}
