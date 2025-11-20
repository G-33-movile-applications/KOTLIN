package com.mobile.mymeds.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.repository.PhysicalPointsRepository
import com.mobile.mymeds.models.PhysicalPoint
import com.mobile.mymeds.utils.LocationUtils
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    private val repository = PhysicalPointsRepository()

    // Lista completa de farmacias (privada, solo para cálculos)
    private var allPharmacies: List<PhysicalPoint> = emptyList()

    // Farmacias a mostrar en el mapa (las relevantes)
    private val _visiblePharmacies = MutableLiveData<List<PhysicalPoint>>()
    val visiblePharmacies: LiveData<List<PhysicalPoint>> = _visiblePharmacies

    private val _nearestPharmacies = MutableLiveData<List<Pair<PhysicalPoint, Double>>>()
    val nearestPharmacies: LiveData<List<Pair<PhysicalPoint, Double>>> = _nearestPharmacies

    private val _userLocation = MutableLiveData<LatLng?>()
    val userLocation: LiveData<LatLng?> = _userLocation

    private val _loadingState = MutableLiveData<LoadingState>()
    val loadingState: LiveData<LoadingState> = _loadingState

    init {
        Log.d("MapViewModel", "ViewModel initialized")
        loadPhysicalPoints()
    }

    private fun loadPhysicalPoints() {
        Log.d("MapViewModel", "Starting to load physical points")
        _loadingState.value = LoadingState.LOADING

        viewModelScope.launch {
            try {
                val result = repository.getAllPhysicalPoints()

                result.onSuccess { pointsList ->
                    Log.d("MapViewModel", "✓ Success! Loaded ${pointsList.size} total points")
                    allPharmacies = pointsList

                    // Si ya tenemos la ubicación del usuario, calcular las relevantes
                    _userLocation.value?.let { location ->
                        updateRelevantPharmacies(location)
                    } ?: run {
                        // Sin ubicación, mostrar todas
                        _visiblePharmacies.value = pointsList
                    }

                    _loadingState.value = LoadingState.SUCCESS
                }.onFailure { exception ->
                    Log.e("MapViewModel", "✗ Error loading points: ${exception.message}", exception)
                    _loadingState.value = LoadingState.ERROR
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "✗ Exception: ${e.message}", e)
                _loadingState.value = LoadingState.ERROR
            }
        }
    }

    fun updateUserLocation(location: LatLng) {
        Log.d("MapViewModel", "User location updated: ${location.latitude}, ${location.longitude}")
        _userLocation.value = location

        // Actualizar las farmacias relevantes
        updateRelevantPharmacies(location)
    }

    private fun updateRelevantPharmacies(userLocation: LatLng) {
        val relevant = LocationUtils.getRelevantPharmacies(userLocation, allPharmacies, 6000.0)

        // Actualizar las 5 más cercanas
        _nearestPharmacies.value = relevant.nearestThree

        // Combinar las 5 más cercanas con las del radio (sin duplicados)
        val nearestPharmaciesSet = relevant.nearestThree.map { it.first }.toSet()
        val allRelevant = (nearestPharmaciesSet + relevant.withinRadius).toList()

        _visiblePharmacies.value = allRelevant

        Log.d("MapViewModel", "=== Location Update ===")
        Log.d("MapViewModel", "Total pharmacies in database: ${allPharmacies.size}")
        Log.d("MapViewModel", "Pharmacies within 6km: ${relevant.withinRadius.size}")
        Log.d("MapViewModel", "Total visible pharmacies: ${allRelevant.size}")
        Log.d("MapViewModel", "")
        Log.d("MapViewModel", "=== Top 5 Nearest Pharmacies ===")
        relevant.nearestThree.forEachIndexed { index, (pharmacy, distance) ->
            Log.d("MapViewModel", "${index + 1}. ${pharmacy.name}")
            Log.d("MapViewModel", "   Distance: ${LocationUtils.formatDistance(distance)}")
            Log.d("MapViewModel", "   Address: ${pharmacy.address}")
        }
    }
}

enum class LoadingState {
    LOADING,
    SUCCESS,
    ERROR
}