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
        // ✅ TODA LA LÓGICA AQUÍ, EN UNA SOLA CORUTINA PARA EVITAR ERRORES
        viewModelScope.launch {
            // 1. PRIMERO, le pedimos al repositorio que se actualice desde la red.
            // La función 'refreshMedications' es 'suspend', por lo que esta corutina
            // esperará aquí hasta que la descarga de Firestore termine (o falle).
            repository.refreshMedications()

            // 2. UNA VEZ que la descarga ha terminado y la caché está (o no) actualizada,
            // empezamos a escuchar los cambios en la caché para siempre.
            repository.allMedications.collect { cachedMeds ->
                // 3. Cada vez que la caché se actualice, actualizamos la UI.
                _uiState.update {
                    it.copy(
                        isLoading = false, // La carga inicial definitivamente ya terminó.
                        availableMedications = cachedMeds,
                        showOfflineAndNoCacheError = cachedMeds.isEmpty() && !ConnectivityUtils.isNetworkAvailable(context)
                    )
                }
            }
        }
    }
}

// Tu ViewModelFactory ya es correcta y no necesita cambios.
class NfcBuilderViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NfcBuilderViewModel::class.java)) {
            val db = AppDatabase.getDatabase(application)
            val repository = GlobalMedicationRepository(
                FirebaseFirestore.getInstance(),
                db.globalMedicationDao(),
                application
            )
            @Suppress("UNCHECKED_CAST")
            return NfcBuilderViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
