package com.mobile.mymeds.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mobile.mymeds.models.Prescription
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DeliveryViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _prescriptions = MutableLiveData<List<Prescription>>()
    val prescriptions: LiveData<List<Prescription>> = _prescriptions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadUserPrescriptions() {
        _isLoading.value = true
        _errorMessage.value = null

        val userId = auth.currentUser?.uid

        if (userId == null) {
            _errorMessage.value = "Usuario no autenticado"
            _isLoading.value = false
            return
        }

        db.collection("usuarios")
            .document(userId)
            .collection("prescripciones")
            .get()
            .addOnSuccessListener { documents ->
                val prescriptionsList = documents.mapNotNull { document ->
                    try {
                        Prescription(
                            id = document.id,
                            activa = document.getBoolean("activa") ?: false,
                            diagnostico = document.getString("diagnostico") ?: "",
                            fechaCreacion = document.getTimestamp("fechaCreacion"),
                            medico = document.getString("medico") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e("DeliveryViewModel", "Error parsing prescription: ${e.message}")
                        null
                    }
                }
                _prescriptions.value = prescriptionsList
                _isLoading.value = false
                Log.d("DeliveryViewModel", "Loaded ${prescriptionsList.size} prescriptions")
            }
            .addOnFailureListener { exception ->
                Log.e("DeliveryViewModel", "Error loading prescriptions: ${exception.message}")
                _errorMessage.value = "Error al cargar prescripciones: ${exception.message}"
                _isLoading.value = false
            }
    }
}