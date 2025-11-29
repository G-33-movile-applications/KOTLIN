package com.mobile.mymeds.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobile.mymeds.models.UserInteraction

@Dao
interface UserInteractionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: UserInteraction)

    // Ecuenta las ocurrencias de cada valor para un campo,
    // las ordena por el conteo (descendente) y luego por la más reciente,
    // y devuelve solo la primera (la más probable).
    @Query("SELECT selectedValue FROM user_interactions WHERE fieldId = :fieldId GROUP BY selectedValue ORDER BY COUNT(selectedValue) DESC, MAX(timestamp) DESC LIMIT 1")
    suspend fun getMostFrequentValue(fieldId: String): String?

    // Query para borrar todo el historial de un campo específico
    @Query("DELETE FROM user_interactions WHERE fieldId = :fieldId")
    suspend fun clearHistoryForField(fieldId: String)

    // Query para borrar todo el historial
    @Query("DELETE FROM user_interactions")
    suspend fun clearAll()
}
