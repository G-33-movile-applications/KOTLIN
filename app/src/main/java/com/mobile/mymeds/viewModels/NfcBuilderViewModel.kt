package com.mobile.mymeds.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.models.GlobalMedication
import com.mobile.mymeds.repository.GlobalMedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel que mostrará los medicamentos disponibles para crear una prescripcion
 */
class NfcBuilderViewModel(
    private val repository: GlobalMedicationRepository
) : ViewModel() {

    val allMedications: StateFlow<List<GlobalMedication>> = repository.allMedications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Cuando el ViewModel es creado, refresca el cache
        refreshMedicationList()
    }

    private fun refreshMedicationList() {
        viewModelScope.launch {
            repository.refreshMedications()
        }
    }
}

class NfcBuilderViewModelFactory(
    private val repository: GlobalMedicationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NfcBuilderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NfcBuilderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}