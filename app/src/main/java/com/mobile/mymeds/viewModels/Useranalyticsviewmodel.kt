package com.mobile.mymeds.viewModels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.models.AnalyticsUiState
import com.mobile.mymeds.models.DeliveryMode
import com.mobile.mymeds.repository.UserAnalyticsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * VIEWMODEL DE ANALÍTICAS - BQT2 IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════
 */
class UserAnalyticsViewModel : ViewModel() {

    private val repository = UserAnalyticsRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = mutableStateOf<AnalyticsUiState>(AnalyticsUiState.Loading)
    val uiState: State<AnalyticsUiState> get() = _uiState

    private val _isRefreshing = mutableStateOf(false)
    val isRefreshing: State<Boolean> get() = _isRefreshing

    /**
     * Cargar analíticas del usuario
     */
    fun loadAnalytics(days: Int? = null, mode: DeliveryMode? = null) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            _uiState.value = AnalyticsUiState.Error("Usuario no autenticado")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = AnalyticsUiState.Loading
                }

                val analytics = repository.getUserAnalytics(userId)

                withContext(Dispatchers.Main) {
                    _uiState.value = AnalyticsUiState.Success(analytics)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = AnalyticsUiState.Error(
                        e.message ?: "Error al cargar analíticas"
                    )
                }
            }
        }
    }

    /**
     * Refrescar analíticas
     */
    fun refresh() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isRefreshing.value = true
                }

                val analytics = repository.getUserAnalytics(userId)

                withContext(Dispatchers.Main) {
                    _uiState.value = AnalyticsUiState.Success(analytics)
                    _isRefreshing.value = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = AnalyticsUiState.Error(
                        e.message ?: "Error al refrescar"
                    )
                    _isRefreshing.value = false
                }
            }
        }
    }
}