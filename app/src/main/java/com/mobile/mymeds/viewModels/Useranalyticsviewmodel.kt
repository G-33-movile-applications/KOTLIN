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
 * VIEWMODEL DE ANALÍTICAS - BQT2 + Offline Connectivity
 * ═══════════════════════════════════════════════════════════════════════════
 */
class UserAnalyticsViewModel : ViewModel() {

    private val repository = UserAnalyticsRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = mutableStateOf<AnalyticsUiState>(AnalyticsUiState.Loading)
    val uiState: State<AnalyticsUiState> get() = _uiState

    private val _isRefreshing = mutableStateOf(false)
    val isRefreshing: State<Boolean> get() = _isRefreshing

    // 🆕 Recordar últimos filtros aplicados (para refresh)
    private var lastDays: Int? = null
    private var lastMode: DeliveryMode? = null

    /**
     * Cargar analíticas del usuario
     */
    fun loadAnalytics(days: Int? = null, mode: DeliveryMode? = null) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            _uiState.value = AnalyticsUiState.Error("Usuario no autenticado")
            return
        }

        // Si vienen null, usamos los últimos; si no, actualizamos memoria
        val effectiveDays = days ?: lastDays
        val effectiveMode = mode ?: lastMode
        lastDays = effectiveDays
        lastMode = effectiveMode

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = AnalyticsUiState.Loading
                }

                // 🆕 El repo ya debe calcular también las métricas offline
                val analytics = repository.getUserAnalytics(
                    userId = userId,
                    days = effectiveDays,
                    mode = effectiveMode
                )

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
     * Refrescar analíticas con los últimos filtros usados
     */
    fun refresh() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isRefreshing.value = true
                }

                val analytics = repository.getUserAnalytics(
                    userId = userId,
                    days = lastDays,
                    mode = lastMode
                )

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
