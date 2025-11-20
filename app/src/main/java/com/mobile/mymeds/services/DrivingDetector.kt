package com.mobile.mymeds.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Servicio que usa el acelerómeto para detectar si el usuario está manejando
 */
class DrivingDetector : Service(), SensorEventListener {

    private val _isDriving = MutableStateFlow(false)
    val isDriving = _isDriving.asStateFlow()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DrivingDetector = this@DrivingDetector
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Manejo sensor
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Parámetros lógica
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastTimestamp = 0L

    // límites para verificar la conducción
    private val SHAKE_THRESHOLD = 8.0f // Lower value = more sensitive to bumps
    private val TIME_THRESHOLD_MS = 500 // Time between significant shakes
    private val CONSECUTIVE_SHAKES_TO_START_DRIVING = 3 // Number of "bumps" to detect driving
    private val NO_SHAKE_TIMEOUT_MS = 10000L // 10 seconds of no bumps to stop driving mode

    private var shakeCount = 0
    private var lastShakeTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d("DrivingDetector", "Service onCreate")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (accelerometer == null) {
            Log.e("DrivingDetector", "Linear Acceleration sensor not available. Driving detection will not work.")
            stopSelf()
        } else {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        if (event.timestamp == 0L) return

        val currentTime = System.currentTimeMillis()
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Velocidad de movimiento
        val speed = abs(x + y + z - lastX - lastY - lastZ) / (event.timestamp - lastTimestamp) * 10000

        // bumps o shakes
        if (speed > SHAKE_THRESHOLD) {
            if (currentTime - lastShakeTimestamp > TIME_THRESHOLD_MS) {
                shakeCount++
                Log.d("DrivingDetector", "Shake detected! Count: $shakeCount")
                lastShakeTimestamp = currentTime

                if (shakeCount >= CONSECUTIVE_SHAKES_TO_START_DRIVING && !_isDriving.value) {
                    Log.i("DrivingDetector", "Driving Mode ENABLED")
                    _isDriving.value = true
                }
            }
        }

        // Check pare detener modo de conduccion
        if (_isDriving.value && (currentTime - lastShakeTimestamp > NO_SHAKE_TIMEOUT_MS)) {
            Log.i("DrivingDetector", "Driving Mode DISABLED (Timeout)")
            _isDriving.value = false
            shakeCount = 0
        }

        lastX = x
        lastY = y
        lastZ = z
        lastTimestamp = event.timestamp
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("DrivingDetector", "Service onDestroy")
    }
}
