package com.mobile.mymeds.repository

import com.mobile.mymeds.data.local.room.dao.UserInteractionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mobile.mymeds.models.UserInteraction

class AutofillRepository(private val interactionDao: UserInteractionDao) {

    /**
     * Registra una selección del usuario en la base de datos
     *
     * @param fieldId Un identificador único para el campo
     * @param value El valor que el usuario seleccionó
     */
    suspend fun recordInteraction(fieldId: String, value: String) {
        if (value.isNotBlank()) { // No guardar valores vacíos
            withContext(Dispatchers.IO) {
                val interaction = UserInteraction(fieldId = fieldId, selectedValue = value)
                interactionDao.insert(interaction)
            }
        }
    }

    /**
     * Obtiene la sugerencia más probable para un campo específico
     *
     * @param fieldId El identificador del campo para el que se busca sugerencia
     * @return El valor más frecuente (String) o null si no hay historial
     */
    suspend fun getSuggestionFor(fieldId: String): String? {
        return withContext(Dispatchers.IO) {
            interactionDao.getMostFrequentValue(fieldId)
        }
    }

    /**
     * Borra todo el historial de interacciones del usuario de la base de datos
     * Se ejecuta en un hilo de IO para no bloquear la UI
     */
    suspend fun clearInteractionHistory() {
        withContext(Dispatchers.IO) {
            interactionDao.clearAll()
        }
    }
}