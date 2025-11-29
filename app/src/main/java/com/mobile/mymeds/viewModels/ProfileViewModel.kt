package com.mobile.mymeds.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.data.local.datastore.UserPreferencesManager
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.repository.AutofillRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- Data Model --- //
data class UserProfile(
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val city: String = "",
    val department: String = "",
    val zipCode: String = "",
    val profilePictureUrl: String = "https://cdn-icons-png.flaticon.com/512/847/847969.png",
    val notificationsEnabled: Boolean = true
)

// --- ViewModel --- //
class ProfileViewModel(
    private val userPrefsManager: UserPreferencesManager,
    private val autofillRepository: AutofillRepository,
    private val application: Application
) : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _profile = MutableLiveData<UserProfile?>()
    val profile: LiveData<UserProfile?> = _profile

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    /**
     * Carga el perfil del usuario autenticado desde Firestore.
     */
    private val _smartAutofillEnabled = MutableLiveData<Boolean>()
    val smartAutofillEnabled: LiveData<Boolean> = _smartAutofillEnabled

    init {
        loadProfile()
        viewModelScope.launch {
            _smartAutofillEnabled.value = userPrefsManager.smartAutofillEnabled.first()
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

    fun loadProfile() {
        viewModelScope.launch {
            _loading.postValue(true)
            try {
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    _message.postValue("Error: No user is currently logged in.")
                    _loading.postValue(false)
                    return@launch
                }

                Log.d("MyMedsApp_Firebase", "Fetching profile for user ID: $userId")

                val documentSnapshot = firestore.collection("usuarios")
                    .document(userId)
                    .get()
                    .await()

                if (documentSnapshot.exists()) {
                    val user = documentSnapshot.toObject(UserProfile::class.java)
                    _profile.postValue(user)
                    Log.d("MyMedsApp_Firebase", "SUCCESS! Profile data loaded: $user")
                } else {
                    // Si no existe, crear un perfil inicial con mockup
                    val defaultProfile = UserProfile(
                        fullName = auth.currentUser?.displayName ?: "",
                        email = auth.currentUser?.email ?: "",
                        profilePictureUrl = "https://cdn-icons-png.flaticon.com/512/847/847969.png"
                    )
                    _profile.postValue(defaultProfile)
                    _message.postValue("No profile found. Default values loaded.")
                    Log.e("MyMedsApp_Firebase", "FAILURE! No document found for user ID: $userId")
                }

            } catch (e: Exception) {
                _message.postValue("An error occurred: ${e.message}")
                Log.e("MyMedsApp_Firebase", "EXCEPTION! Error fetching profile", e)
            } finally {
                _loading.postValue(false)
            }
        }
    }

    /**
     * Guarda el perfil actualizado en Firestore.
     */
    fun saveProfile(updated: UserProfile, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _loading.postValue(true)
            try {
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _message.postValue("Error: No user is currently logged in.")
                    _loading.postValue(false)
                    onResult(false, "No logged user")
                    return@launch
                }
                firestore.collection("usuarios")
                    .document(uid)
                    .set(updated)
                    .addOnSuccessListener {
                        _profile.postValue(updated)
                        _message.postValue("Profile updated successfully.")
                        _loading.postValue(false)
                        Log.d("MyMedsApp_Firebase", "SUCCESS! Profile updated for user ID: $uid")
                        onResult(true, "Profile updated successfully")
                    }
                    .addOnFailureListener { e ->
                        _message.postValue(e.message ?: "Update failed")
                        _loading.postValue(false)
                        Log.e("MyMedsApp_Firebase", "FAILURE! Error updating profile", e)
                        onResult(false, e.message ?: "Update failed")
                    }

            } catch (e: Exception) {
                _message.postValue(e.message ?: "Unexpected error")
                _loading.postValue(false)
                Log.e("MyMedsApp_Firebase", "EXCEPTION! Error saving profile", e)
                onResult(false, e.message ?: "Unexpected error")
            }
        }
    }

    /**
     * Cambia el estado de notificaciones y actualiza en Firestore.
     */
    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            firestore.collection("usuarios").document(uid)
                .update("notificationsEnabled", enabled)
                .addOnSuccessListener {
                    val current = _profile.value
                    if (current != null) {
                        _profile.postValue(current.copy(notificationsEnabled = enabled))
                    }
                    _message.postValue(
                        if (enabled) "Notifications enabled" else "Notifications disabled"
                    )
                }
                .addOnFailureListener { e ->
                    _message.postValue(e.message ?: "Error updating notifications")
                }
        }
    }
}

class ProfileViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            val autofillRepository = AutofillRepository(database.userInteractionDao())
            val userPreferencesManager = UserPreferencesManager(application)
            return ProfileViewModel(userPreferencesManager, autofillRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
