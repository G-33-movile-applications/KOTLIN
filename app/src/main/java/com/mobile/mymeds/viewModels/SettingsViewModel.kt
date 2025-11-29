package com.mobile.mymeds.viewModels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.data.local.datastore.UserPreferencesManager
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.repository.AutofillRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPrefsManager: UserPreferencesManager,
    private val autofillRepository: AutofillRepository
) : ViewModel() {

    // --- LiveData para la UI ---
    private val _notificationsEnabled = MutableLiveData<Boolean>()
    val notificationsEnabled: LiveData<Boolean> = _notificationsEnabled

    private val _smartAutofillEnabled = MutableLiveData<Boolean>()
    val smartAutofillEnabled: LiveData<Boolean> = _smartAutofillEnabled

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message


    init {
        viewModelScope.launch {
            _notificationsEnabled.value = userPrefsManager.notificationsEnabled.first()
            _smartAutofillEnabled.value = userPrefsManager.smartAutofillEnabled.first()
        }
    }

    // --- Funciones para la UI ---

    fun setNotificationsEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPrefsManager.setNotificationsEnabled(isEnabled)
            _notificationsEnabled.value = isEnabled
        }
    }

    fun setSmartAutofillEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPrefsManager.setSmartAutofillEnabled(isEnabled)
            _smartAutofillEnabled.value = isEnabled
            _message.postValue(if (isEnabled) "Autocompletado inteligente activado" else "Autocompletado inteligente desactivado")
        }
    }

    fun clearAutofillHistory() {
        viewModelScope.launch {
            autofillRepository.clearInteractionHistory()
            _message.postValue("Historial de sugerencias borrado.")
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            val autofillRepository = AutofillRepository(database.userInteractionDao())
            val userPreferencesManager = UserPreferencesManager(application)
            return SettingsViewModel(userPreferencesManager, autofillRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
