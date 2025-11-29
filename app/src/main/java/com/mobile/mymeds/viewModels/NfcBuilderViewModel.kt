package com.mobile.mymeds.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.models.GlobalMedication
import com.mobile.mymeds.repository.GlobalMedicationRepository
import com.mobile.mymeds.utils.ConnectivityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect // ✅ CAMBIO SUTIL: Usar .collect es más seguro aquí
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NfcBuilderUiState(
    val isLoading: Boolean = true,
    val availableMedications: List<GlobalMedication> = emptyList(),
    val showOfflineAndNoCacheError: Boolean = false
)

class NfcBuilderViewModel(
    private val repository: GlobalMedicationRepository,
    private val context: Application
) : AndroidViewModel(context) {

    private val _uiState = MutableStateFlow(NfcBuilderUiState())
    val uiState = _uiState.asStateFlow()

    init {
        Log.d("MY_MEDS_DEBUG", "NfcBuilderViewModel: init() INICIADO.")
        viewModelScope.launch {
            Log.d("MY_MEDS_DEBUG", "ViewModel: Dentro de la corutina, a punto de llamar a refreshMedications.")
            repository.refreshMedications()
            Log.d("MY_MEDS_DEBUG", "ViewModel: La llamada a refreshMedications() ha terminado.")

            repository.allMedications.collect { cachedMeds ->
                Log.d("MY_MEDS_DEBUG", "ViewModel: collect() ha recibido ${cachedMeds.size} medicamentos.")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availableMedications = cachedMeds,
                        showOfflineAndNoCacheError = cachedMeds.isEmpty() && !ConnectivityUtils.isNetworkAvailable(context)
                    )
                }
            }
        }
    }
}

class NfcBuilderViewModelFactory(
    private val repository: GlobalMedicationRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(NfcBuilderViewModel::class.java)) {
        @Suppress("UNCHECKED_CAST")
        return NfcBuilderViewModel(repository, application) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
}
}
