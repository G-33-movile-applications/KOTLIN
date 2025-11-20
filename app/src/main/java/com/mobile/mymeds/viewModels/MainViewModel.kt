package com.mobile.mymeds.viewModels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.services.DrivingDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainViewModel : ViewModel() {

    private val _isDriving = MutableStateFlow(false)
    val isDriving = _isDriving.asStateFlow()

    private var drivingDetectorService: DrivingDetector? = null
    private var isBound = false
    private var isManuallyOverridden = false // bloquea el sensor cuando se usa el boton debug

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DrivingDetector.LocalBinder
            drivingDetectorService = binder.getService()
            isBound = true
            Log.i("MainViewModel", "DrivingDetector Service Connected")

            drivingDetectorService?.isDriving?.onEach { drivingStateFromSensor ->
                if (!isManuallyOverridden) {
                    _isDriving.value = drivingStateFromSensor
                    Log.d("MainViewModel", "Driving State Updated from Service: $drivingStateFromSensor")
                }
            }?.launchIn(viewModelScope)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            drivingDetectorService = null
            _isDriving.value = false
            Log.w("MainViewModel", "DrivingDetector Service Disconnected")
        }
    }

    /**
     * Modo conducción debug
     */
    fun toggleDrivingModeForDebug() {
        isManuallyOverridden = true // Once we use the button, we take manual control
        _isDriving.value = !_isDriving.value
        Log.i("MainViewModel", "DEBUG: Driving mode toggled manually to ${_isDriving.value}")
    }

    /**
     * Comienza la detección por si está conduciendo
     */
    fun startDrivingDetection(context: Context) {
        if (isBound) return
        val intent = Intent(context, DrivingDetector::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        // Recordar unbind en onStop() o onDestroy().
        Log.d("MainViewModel", "ViewModel Cleared")
    }
}
