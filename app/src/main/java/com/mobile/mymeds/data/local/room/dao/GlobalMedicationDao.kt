package com.mobile.mymeds.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobile.mymeds.models.GlobalMedication
import kotlinx.coroutines.flow.Flow

/**
 * DAO para cargar todos los medicamentos disponibles para añadir a prescripcion
 */
@Dao
interface GlobalMedicationDao {

    /**
     * Reemplaza todo el cache con una lista de meds de firebase
     * el OnConflictStrategy.replace hace que si el medicamento ya existe
     * entonces simplemente se actualice.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<GlobalMedication>)

    /**
     * Flow con todos los medicamentos en cache.
     */
    @Query("SELECT * FROM medicamentos_globales ORDER BY nombre ASC")
    fun getAll(): Flow<List<GlobalMedication>>

    /**
     * Limpia todo el cache, util para forzar un refresh
     */
    @Query("DELETE FROM medicamentos_globales")
    suspend fun clearAll()
}
