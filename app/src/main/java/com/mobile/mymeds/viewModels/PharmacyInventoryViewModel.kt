package com.mobile.mymeds.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mobile.mymeds.models.InventoryMedication
import com.google.firebase.firestore.FirebaseFirestore

class PharmacyInventoryViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _medications = MutableLiveData<List<InventoryMedication>>()
    val medications: LiveData<List<InventoryMedication>> = _medications

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadPharmacyInventory(pharmacyId: String) {
        _isLoading.value = true
        _errorMessage.value = null

        db.collection("puntosFisicos")
            .document(pharmacyId)
            .collection("inventario")
            .get()
            .addOnSuccessListener { documents ->
                val inventoryItems = documents.mapNotNull { document ->
                    try {
                        InventoryMedication(
                            id = document.id,
                            nombre = document.getString("nombre") ?: "",
                            medicamentoRef = document.getString("medicamentoRef") ?: "",
                            proveedor = document.getString("proveedor") ?: "",
                            lote = document.getString("lote") ?: "",
                            stock = document.getLong("stock")?.toInt() ?: 0,
                            precioUnidad = document.getLong("precioUnidad")?.toInt() ?: 0,
                            fechaIngreso = document.getTimestamp("fechaIngreso"),
                            fechaVencimiento = document.getTimestamp("fechaVencimiento")
                        )
                    } catch (e: Exception) {
                        Log.e("PharmacyInventoryVM", "Error parsing inventory: ${e.message}")
                        null
                    }
                }

                // Cargar detalles completos de cada medicamento
                loadMedicationDetails(inventoryItems)

            }
            .addOnFailureListener { exception ->
                Log.e("PharmacyInventoryVM", "Error loading inventory: ${exception.message}")
                _errorMessage.value = "Error al cargar inventario: ${exception.message}"
                _isLoading.value = false
            }
    }

    private fun loadMedicationDetails(inventoryItems: List<InventoryMedication>) {
        if (inventoryItems.isEmpty()) {
            _medications.value = emptyList()
            _isLoading.value = false
            return
        }

        val medicationRefs = inventoryItems.mapNotNull { it.medicamentoRef.split("/").lastOrNull() }

        db.collection("medicamentosGlobales")
            .whereIn("__name__", medicationRefs.take(10)) // Firestore limita a 10
            .get()
            .addOnSuccessListener { documents ->
                val medicationsMap = documents.associate { doc ->
                    val contraindicaciones = (doc.get("contraindicaciones") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList()

                    doc.id to MedicationDetails(
                        descripcion = doc.getString("descripcion") ?: "",
                        principioActivo = doc.getString("principioActivo") ?: "",
                        presentacion = doc.getString("presentacion") ?: "",
                        laboratorio = doc.getString("laboratorio") ?: "",
                        contraindicaciones = contraindicaciones
                    )
                }

                // Combinar datos de inventario con detalles globales
                val enrichedList = inventoryItems.map { item ->
                    val medId = item.medicamentoRef.split("/").last()
                    val details = medicationsMap[medId]
                    item.copy(
                        descripcion = details?.descripcion ?: "",
                        principioActivo = details?.principioActivo ?: "",
                        presentacion = details?.presentacion ?: "",
                        laboratorio = details?.laboratorio ?: "",
                        contraindicaciones = details?.contraindicaciones ?: emptyList()
                    )
                }

                _medications.value = enrichedList
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                Log.e("PharmacyInventoryVM", "Error loading medication details: ${exception.message}")
                _medications.value = inventoryItems // Mostrar sin detalles
                _isLoading.value = false
            }
    }

    private data class MedicationDetails(
        val descripcion: String,
        val principioActivo: String,
        val presentacion: String,
        val laboratorio: String,
        val contraindicaciones: List<String>
    )
}