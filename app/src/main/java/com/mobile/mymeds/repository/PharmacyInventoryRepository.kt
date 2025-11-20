package com.mobile.mymeds.repository

import android.util.Log
import com.mobile.mymeds.models.InventoryMedication
import com.mobile.mymeds.models.PhysicalPoint
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * ═══════════════════════════════════════════════════════════════════════
 * PHARMACY INVENTORY REPOSITORY
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Este repositorio maneja las operaciones de Firebase para:
 * 1. Cargar puntos físicos (farmacias) desde /puntosFisicos
 * 2. Cargar inventario desde /puntosFisicos/{id}/inventario
 * 3. Cargar datos globales de medicamentos desde /medicamentosGlobales
 * 4. Combinar inventario + datos globales en InventoryMedication
 */
class PharmacyInventoryRepository {

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "PharmacyInventoryRepo"
        private const val COLLECTION_PHYSICAL_POINTS = "puntosFisicos"
        private const val COLLECTION_GLOBAL_MEDS = "medicamentosGlobales"
        private const val SUBCOLLECTION_INVENTORY = "inventario"
    }

    /**
     * ═════════════════════════════════════════════════════════════════
     * PHARMACY OPERATIONS - PUNTOS FÍSICOS
     * ═════════════════════════════════════════════════════════════════
     */

    /**
     * Carga todos los puntos físicos (farmacias) desde Firebase
     * Usado por: MapViewModel para mostrar marcadores en el mapa
     *
     * Firebase path: /puntosFisicos
     */
    suspend fun getAllPharmacies(): Result<List<PhysicalPoint>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "═══════════════════════════════════════════════════════")
            Log.d(TAG, "Cargando todas las farmacias desde Firebase...")
            Log.d(TAG, "═══════════════════════════════════════════════════════")

            val snapshot = firestore.collection(COLLECTION_PHYSICAL_POINTS)
                .get()
                .await()

            val pharmacies = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(PhysicalPoint::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando farmacia ${doc.id}: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "✅ Cargadas ${pharmacies.size} farmacias exitosamente")
            Log.d(TAG, "═══════════════════════════════════════════════════════")
            Result.success(pharmacies)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando farmacias: ${e.message}", e)
            Log.e(TAG, "═══════════════════════════════════════════════════════")
            Result.failure(e)
        }
    }

    /**
     * Obtiene una farmacia específica por ID
     * Usado por: PharmacyInventoryViewModel cuando el usuario hace click en "Ver inventario"
     *
     * @param pharmacyId El ID del documento de la farmacia
     * Firebase path: /puntosFisicos/{pharmacyId}
     */
    suspend fun getPharmacyById(pharmacyId: String): Result<PhysicalPoint> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Cargando farmacia con ID: $pharmacyId")

            val snapshot = firestore.collection(COLLECTION_PHYSICAL_POINTS)
                .document(pharmacyId)
                .get()
                .await()

            val pharmacy = snapshot.toObject(PhysicalPoint::class.java)?.copy(id = snapshot.id)

            if (pharmacy != null) {
                Log.d(TAG, "✅ Farmacia cargada: ${pharmacy.name}")
                Result.success(pharmacy)
            } else {
                Log.e(TAG, "❌ Farmacia no encontrada con ID: $pharmacyId")
                Result.failure(Exception("Farmacia no encontrada"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando farmacia $pharmacyId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * ═════════════════════════════════════════════════════════════════
     * INVENTORY OPERATIONS - INVENTARIO
     * ═════════════════════════════════════════════════════════════════
     */

    /**
     * FUNCIÓN PRINCIPAL: Carga el inventario de una farmacia con detalles completos
     *
     * Este es el proceso que describiste:
     * 1. Obtener info de la farmacia desde /puntosFisicos/{pharmacyId}
     * 2. Obtener items del inventario desde /puntosFisicos/{pharmacyId}/inventario
     * 3. Obtener datos globales desde /medicamentosGlobales para cada medicamento
     * 4. Combinar todo en objetos InventoryMedication completos
     *
     * @param pharmacyId El ID de la farmacia
     * @return Lista de InventoryMedication con datos combinados
     */
    suspend fun getPharmacyInventoryWithDetails(pharmacyId: String): Result<List<InventoryMedication>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "═════════════════════════════════════════════════════════")
                Log.d(TAG, "INICIANDO: Carga de inventario para farmacia: $pharmacyId")
                Log.d(TAG, "═════════════════════════════════════════════════════════")

                // PASO 1: Obtener información de la farmacia
                Log.d(TAG, "PASO 1: Obteniendo información de la farmacia...")
                val pharmacyResult = getPharmacyById(pharmacyId)
                if (pharmacyResult.isFailure) {
                    Log.e(TAG, "❌ Falló la carga de farmacia")
                    return@withContext Result.failure(pharmacyResult.exceptionOrNull()!!)
                }
                val pharmacy = pharmacyResult.getOrNull()!!
                Log.d(TAG, "✅ PASO 1 COMPLETO: Farmacia '${pharmacy.name}' cargada")

                // PASO 2: Obtener items del inventario de la subcolección
                Log.d(TAG, "PASO 2: Cargando items del inventario desde subcolección...")
                val inventoryItems = getPharmacyInventoryItems(pharmacyId)
                Log.d(TAG, "✅ PASO 2 COMPLETO: ${inventoryItems.size} items de inventario cargados")

                if (inventoryItems.isEmpty()) {
                    Log.d(TAG, "⚠️ No se encontraron items en el inventario")
                    return@withContext Result.success(emptyList())
                }

                // PASO 3: Extraer IDs de medicamentos únicos
                val medicationRefs = inventoryItems.mapNotNull {
                    it.medicamentoRef.split("/").lastOrNull()
                }.distinct()
                Log.d(TAG, "PASO 3: ${medicationRefs.size} medicamentos únicos encontrados")
                Log.d(TAG, "IDs de medicamentos: $medicationRefs")

                // PASO 4: Cargar datos globales de medicamentos EN PARALELO
                Log.d(TAG, "PASO 4: Cargando datos globales de medicamentos en paralelo...")
                val globalMedicationsMap = loadGlobalMedicationsParallel(medicationRefs)
                Log.d(TAG, "✅ PASO 4 COMPLETO: ${globalMedicationsMap.size} medicamentos globales cargados")

                // PASO 5: Combinar datos de inventario con datos globales
                Log.d(TAG, "PASO 5: Combinando datos de inventario con datos globales...")
                val enrichedInventory = inventoryItems.map { item ->
                    val medId = item.medicamentoRef.split("/").lastOrNull() ?: ""
                    val globalDetails = globalMedicationsMap[medId]

                    if (globalDetails != null) {
                        item.copy(
                            descripcion = globalDetails.descripcion,
                            principioActivo = globalDetails.principioActivo,
                            presentacion = globalDetails.presentacion,
                            laboratorio = globalDetails.laboratorio,
                            contraindicaciones = globalDetails.contraindicaciones
                        )
                    } else {
                        Log.w(TAG, "⚠️ No se encontraron datos globales para: $medId")
                        item
                    }
                }

                Log.d(TAG, "✅ PASO 5 COMPLETO: ${enrichedInventory.size} items enriquecidos")
                Log.d(TAG, "═════════════════════════════════════════════════════════")
                Log.d(TAG, "✅ ÉXITO: Carga de inventario completada")
                Log.d(TAG, "═════════════════════════════════════════════════════════")

                Result.success(enrichedInventory)

            } catch (e: Exception) {
                Log.e(TAG, "═════════════════════════════════════════════════════════")
                Log.e(TAG, "❌ ERROR: Falló la carga de inventario: ${e.message}", e)
                Log.e(TAG, "═════════════════════════════════════════════════════════")
                Result.failure(e)
            }
        }

    /**
     * Carga los items de inventario de una farmacia (solo datos básicos)
     * Firebase path: /puntosFisicos/{pharmacyId}/inventario
     */
    private suspend fun getPharmacyInventoryItems(pharmacyId: String): List<InventoryMedication> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection(COLLECTION_PHYSICAL_POINTS)
                    .document(pharmacyId)
                    .collection(SUBCOLLECTION_INVENTORY)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { doc ->
                    try {
                        InventoryMedication(
                            id = doc.id,
                            nombre = doc.getString("nombre") ?: "",
                            medicamentoRef = doc.getString("medicamentoRef") ?: "",
                            proveedor = doc.getString("proveedor") ?: "",
                            lote = doc.getString("lote") ?: "",
                            stock = doc.getLong("stock")?.toInt() ?: 0,
                            precioUnidad = doc.getLong("precioUnidad")?.toInt() ?: 0,
                            fechaIngreso = doc.getTimestamp("fechaIngreso"),
                            fechaVencimiento = doc.getTimestamp("fechaVencimiento")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando item de inventario ${doc.id}: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando items de inventario: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * ═════════════════════════════════════════════════════════════════
     * GLOBAL MEDICATIONS - MEDICAMENTOS GLOBALES
     * ═════════════════════════════════════════════════════════════════
     */

    /**
     * Carga múltiples medicamentos globales EN PARALELO
     * Esto es más eficiente que cargarlos uno por uno
     *
     * Firebase path: /medicamentosGlobales/{medicationId}
     */
    private suspend fun loadGlobalMedicationsParallel(medicationIds: List<String>): Map<String, GlobalMedicationDetails> =
        coroutineScope {
            try {
                // Firestore tiene límite de 10 en whereIn, así que dividimos en lotes
                val batches = medicationIds.chunked(10)

                val allMedications = batches.map { batch ->
                    async {
                        try {
                            val snapshot = firestore.collection(COLLECTION_GLOBAL_MEDS)
                                .whereIn("__name__", batch)
                                .get()
                                .await()

                            snapshot.documents.mapNotNull { doc ->
                                try {
                                    val contraindicaciones = (doc.get("contraindicaciones") as? List<*>)
                                        ?.mapNotNull { it as? String } ?: emptyList()

                                    doc.id to GlobalMedicationDetails(
                                        descripcion = doc.getString("descripcion") ?: "",
                                        principioActivo = doc.getString("principioActivo") ?: "",
                                        presentacion = doc.getString("presentacion") ?: "",
                                        laboratorio = doc.getString("laboratorio") ?: "",
                                        contraindicaciones = contraindicaciones
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parseando medicamento ${doc.id}: ${e.message}")
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cargando lote de medicamentos: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten().toMap()

                Log.d(TAG, "✅ Cargados ${allMedications.size} medicamentos globales en paralelo")
                allMedications

            } catch (e: Exception) {
                Log.e(TAG, "Error en carga paralela de medicamentos: ${e.message}", e)
                emptyMap()
            }
        }

    /**
     * Clase auxiliar para almacenar detalles de medicamentos globales
     */
    private data class GlobalMedicationDetails(
        val descripcion: String,
        val principioActivo: String,
        val presentacion: String,
        val laboratorio: String,
        val contraindicaciones: List<String>
    )

    /**
     * ═════════════════════════════════════════════════════════════════
     * SEARCH AND FILTER OPERATIONS
     * ═════════════════════════════════════════════════════════════════
     */

    /**
     * Busca un medicamento específico en el inventario de una farmacia
     */
    suspend fun searchMedicationInPharmacy(
        pharmacyId: String,
        medicationName: String
    ): Result<List<InventoryMedication>> = withContext(Dispatchers.IO) {
        try {
            val inventoryResult = getPharmacyInventoryWithDetails(pharmacyId)
            if (inventoryResult.isFailure) {
                return@withContext Result.failure(inventoryResult.exceptionOrNull()!!)
            }

            val allMedications = inventoryResult.getOrNull()!!
            val filtered = allMedications.filter { med ->
                med.nombre.contains(medicationName, ignoreCase = true) ||
                        med.principioActivo.contains(medicationName, ignoreCase = true)
            }

            Log.d(TAG, "Búsqueda '$medicationName': ${filtered.size} resultados")
            Result.success(filtered)

        } catch (e: Exception) {
            Log.e(TAG, "Error en búsqueda: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Busca un medicamento en todas las farmacias
     * Útil para "¿Dónde puedo encontrar este medicamento?"
     */
    suspend fun findMedicationInAllPharmacies(
        medicationName: String
    ): Result<Map<PhysicalPoint, List<InventoryMedication>>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando '$medicationName' en todas las farmacias...")

            // Obtener todas las farmacias
            val pharmaciesResult = getAllPharmacies()
            if (pharmaciesResult.isFailure) {
                return@withContext Result.failure(pharmaciesResult.exceptionOrNull()!!)
            }

            val pharmacies = pharmaciesResult.getOrNull()!!

            // Buscar en cada farmacia en paralelo
            val results = coroutineScope {
                pharmacies.map { pharmacy ->
                    async {
                        val inventoryResult = getPharmacyInventoryWithDetails(pharmacy.id)
                        val inventory = inventoryResult.getOrNull() ?: emptyList()

                        val matches = inventory.filter { med ->
                            med.nombre.contains(medicationName, ignoreCase = true) ||
                                    med.principioActivo.contains(medicationName, ignoreCase = true)
                        }

                        if (matches.isNotEmpty()) {
                            pharmacy to matches
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }

            Log.d(TAG, "Medicamento encontrado en ${results.size} farmacias")
            Result.success(results)

        } catch (e: Exception) {
            Log.e(TAG, "Error buscando en todas las farmacias: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Verifica disponibilidad de un medicamento por referencia
     */
    suspend fun checkMedicationAvailability(
        pharmacyId: String,
        medicationRef: String
    ): Result<InventoryMedication?> = withContext(Dispatchers.IO) {
        try {
            val inventoryResult = getPharmacyInventoryWithDetails(pharmacyId)
            if (inventoryResult.isFailure) {
                return@withContext Result.failure(inventoryResult.exceptionOrNull()!!)
            }

            val medication = inventoryResult.getOrNull()!!.find {
                it.medicamentoRef == medicationRef
            }

            Result.success(medication)

        } catch (e: Exception) {
            Log.e(TAG, "Error verificando disponibilidad: ${e.message}", e)
            Result.failure(e)
        }
    }
}