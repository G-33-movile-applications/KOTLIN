package com.mobile.mymeds.repository

import android.util.Log
import com.mobile.mymeds.models.Prescription
import com.mobile.mymeds.models.PrescriptionMedication
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

private const val TAG = "PrescriptionMedicationsRepo"

/**
 * Modelo combinado que une Prescription con sus medicamentos
 */
data class PrescriptionWithMedications(
    val prescription: Prescription,
    val medications: List<PrescriptionMedication>
)

/**
 * Repositorio para gestionar medicamentos de prescripciones
 * Adaptado a la estructura existente del proyecto
 */
class PrescriptionMedicationsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Obtiene todas las prescripciones activas de un usuario con sus medicamentos
     */
    suspend fun getUserActivePrescriptions(userId: String): Result<List<PrescriptionWithMedications>> {
        return try {
            Log.d(TAG, "📋 Obteniendo prescripciones activas para usuario: $userId")

            // Obtener prescripciones activas
            val prescriptionsSnapshot = firestore
                .collection("usuarios")
                .document(userId)
                .collection("prescripcionesUsuario")
                .whereEqualTo("activa", true)
                .orderBy("fechaCreacion", Query.Direction.DESCENDING)
                .get()
                .await()

            val prescriptionsWithMeds = mutableListOf<PrescriptionWithMedications>()

            // Para cada prescripción, obtener sus medicamentos
            for (prescriptionDoc in prescriptionsSnapshot.documents) {
                try {
                    val prescription = Prescription(
                        id = prescriptionDoc.id,
                        activa = prescriptionDoc.getBoolean("activa") ?: false,
                        diagnostico = prescriptionDoc.getString("diagnostico") ?: "",
                        fechaCreacion = prescriptionDoc.getTimestamp("fechaCreacion"),
                        medico = prescriptionDoc.getString("medico") ?: ""
                    )

                    // Obtener medicamentos de esta prescripción
                    val medicationsSnapshot = firestore
                        .collection("usuarios")
                        .document(userId)
                        .collection("prescripcionesUsuario")
                        .document(prescriptionDoc.id)
                        .collection("medicamentosPrescripcion")
                        .get()
                        .await()

                    val medications = medicationsSnapshot.documents.mapNotNull { medDoc ->
                        try {
                            PrescriptionMedication(
                                id = medDoc.id,
                                medicationRef = medDoc.getString("medicationRef"),
                                name = medDoc.getString("name") ?: "",
                                doseMg = medDoc.getLong("doseMg")?.toInt() ?: 0,
                                frequencyHours = medDoc.getLong("frequencyHours")?.toInt() ?: 24,
                                quantity = medDoc.getLong("quantity")?.toInt() ?: 1
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parseando medicamento: ${medDoc.id}", e)
                            null
                        }
                    }

                    if (medications.isNotEmpty()) {
                        prescriptionsWithMeds.add(
                            PrescriptionWithMedications(
                                prescription = prescription,
                                medications = medications
                            )
                        )
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error procesando prescripción: ${prescriptionDoc.id}", e)
                }
            }

            Log.d(TAG, "✅ ${prescriptionsWithMeds.size} prescripciones con medicamentos obtenidas")
            Result.success(prescriptionsWithMeds)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo prescripciones", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene todos los medicamentos de todas las prescripciones activas
     * Retorna una lista plana de medicamentos con info de su prescripción
     */
    suspend fun getAllActivePrescribedMedications(userId: String): Result<List<MedicationWithPrescriptionInfo>> {
        return try {
            val prescriptionsResult = getUserActivePrescriptions(userId)

            if (prescriptionsResult.isFailure) {
                return Result.failure(prescriptionsResult.exceptionOrNull()!!)
            }

            val prescriptions = prescriptionsResult.getOrNull() ?: emptyList()
            val allMedications = mutableListOf<MedicationWithPrescriptionInfo>()

            for (prescWithMeds in prescriptions) {
                for (medication in prescWithMeds.medications) {
                    allMedications.add(
                        MedicationWithPrescriptionInfo(
                            medication = medication,
                            prescriptionId = prescWithMeds.prescription.id,
                            diagnostico = prescWithMeds.prescription.diagnostico,
                            medico = prescWithMeds.prescription.medico,
                            fechaCreacion = prescWithMeds.prescription.fechaCreacion
                        )
                    )
                }
            }

            Log.d(TAG, "✅ ${allMedications.size} medicamentos totales obtenidos")
            Result.success(allMedications)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo todos los medicamentos", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene medicamentos de una prescripción específica
     */
    suspend fun getPrescriptionMedications(
        userId: String,
        prescriptionId: String
    ): Result<List<PrescriptionMedication>> {
        return try {
            Log.d(TAG, "📋 Obteniendo medicamentos de prescripción: $prescriptionId")

            val snapshot = firestore
                .collection("usuarios")
                .document(userId)
                .collection("prescripcionesUsuario")
                .document(prescriptionId)
                .collection("medicamentosPrescripcion")
                .get()
                .await()

            val medications = snapshot.documents.mapNotNull { doc ->
                try {
                    PrescriptionMedication(
                        id = doc.id,
                        medicationRef = doc.getString("medicationRef"),
                        name = doc.getString("name") ?: "",
                        doseMg = doc.getLong("doseMg")?.toInt() ?: 0,
                        frequencyHours = doc.getLong("frequencyHours")?.toInt() ?: 24,
                        quantity = doc.getLong("quantity")?.toInt() ?: 1
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parseando medicamento: ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "✅ ${medications.size} medicamentos obtenidos")
            Result.success(medications)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo medicamentos de prescripción", e)
            Result.failure(e)
        }
    }

    /**
     * Busca un medicamento prescrito en el inventario de una farmacia
     * Busca por nombre similar
     */
    suspend fun findMatchingPharmacyMedication(
        prescriptionMedication: PrescriptionMedication,
        pharmacyId: String
    ): Result<String?> {
        return try {
            Log.d(TAG, "🔍 Buscando medicamento en farmacia: ${prescriptionMedication.name}")

            val snapshot = firestore
                .collection("physicalPoints")
                .document(pharmacyId)
                .collection("medications")
                .whereEqualTo("disponible", true)
                .get()
                .await()

            // Buscar coincidencia por nombre
            val matchingMed = snapshot.documents.find { doc ->
                val nombre = doc.getString("nombre") ?: ""
                nombre.equals(prescriptionMedication.name, ignoreCase = true) ||
                        nombre.contains(prescriptionMedication.name, ignoreCase = true) ||
                        prescriptionMedication.name.contains(nombre, ignoreCase = true)
            }

            if (matchingMed != null) {
                Log.d(TAG, "✅ Medicamento encontrado en farmacia: ${matchingMed.id}")
                Result.success(matchingMed.id)
            } else {
                Log.d(TAG, "⚠️ Medicamento no encontrado en farmacia")
                Result.success(null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error buscando medicamento en farmacia", e)
            Result.failure(e)
        }
    }

    /**
     * Desactiva una prescripción (cuando se completa el tratamiento)
     */
    suspend fun deactivatePrescription(
        userId: String,
        prescriptionId: String
    ): Result<Unit> {
        return try {
            Log.d(TAG, "🔄 Desactivando prescripción: $prescriptionId")

            firestore
                .collection("usuarios")
                .document(userId)
                .collection("prescripcionesUsuario")
                .document(prescriptionId)
                .update("activa", false)
                .await()

            Log.d(TAG, "✅ Prescripción desactivada")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error desactivando prescripción", e)
            Result.failure(e)
        }
    }
}

/**
 * Modelo que combina un medicamento prescrito con información de su prescripción
 */
data class MedicationWithPrescriptionInfo(
    val medication: PrescriptionMedication,
    val prescriptionId: String,
    val diagnostico: String,
    val medico: String,
    val fechaCreacion: com.google.firebase.Timestamp?
) {
    /**
     * Obtiene descripción de la dosis
     */
    fun getDoseDescription(): String {
        return "${medication.doseMg}mg cada ${medication.frequencyHours}h"
    }

    /**
     * Obtiene descripción completa del medicamento
     */
    fun getFullDescription(): String = buildString {
        append(medication.name)
        if (medication.doseMg > 0) {
            append(" - ${medication.doseMg}mg")
        }
        if (medication.frequencyHours > 0) {
            append(" (cada ${medication.frequencyHours}h)")
        }
    }
}