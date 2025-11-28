package com.mobile.mymeds.viewModels

import android.app.Application
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class NfcBuilderUiState(
    val isLoading: Boolean = false,
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
        viewModelScope.launch {
            repository.allMedications.collectLatest { cachedMeds ->
                _uiState.value = _uiState.value.copy(
                    availableMedications = cachedMeds,
                    showOfflineAndNoCacheError = cachedMeds.isEmpty() && !ConnectivityUtils.isNetworkAvailable(context)
                )
            }
        }
    }
}

class NfcBuilderViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NfcBuilderViewModel::class.java)) {
            val db = AppDatabase.getDatabase(application)
            val repository = GlobalMedicationRepository(
                FirebaseFirestore.getInstance(),
                db.globalMedicationDao(),
                application // Le pasamos el contexto al repo
            )
            @Suppress("UNCHECKED_CAST")
            return NfcBuilderViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
