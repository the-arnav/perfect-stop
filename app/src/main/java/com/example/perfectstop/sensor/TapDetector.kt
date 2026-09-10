package com.example.perfectstop.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

class TapDetector(
    private val context: Context,
    private val onTapDetected: (accelG: Double, rssi: Int) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    companion object {
        const val TAP_ACCEL_THRESHOLD_G = 1.8
        const val TAP_RSSI_THRESHOLD_DBM = -65
        const val TAP_COINCIDENCE_WINDOW_MS = 1500L
        const val GRAVITY = 9.80665
    }

    private var lastJoltTimestamp: Long = 0
    private var lastJoltMagnitudeG: Double = 0.0

    private var lastProximityTimestamp: Long = 0
    private var lastRssi: Int = -100

    private var lastTriggeredTime: Long = 0
    var currentLiveAccelG: Double = 1.0
        private set
    var currentHostRssi: Int = -100
        private set

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val rawMs2 = sqrt(ax * ax + ay * ay + az * az)
        val magG = rawMs2 / GRAVITY
        currentLiveAccelG = magG

        if (magG >= TAP_ACCEL_THRESHOLD_G) {
            val now = System.currentTimeMillis()
            lastJoltTimestamp = now
            lastJoltMagnitudeG = magG
            checkCoincidence(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun updateHostRssi(rssi: Int) {
        currentHostRssi = rssi
        if (rssi >= TAP_RSSI_THRESHOLD_DBM) {
            val now = System.currentTimeMillis()
            lastProximityTimestamp = now
            lastRssi = rssi
            checkCoincidence(now)
        }
    }

    private fun checkCoincidence(now: Long) {
        if (lastJoltTimestamp == 0L || lastProximityTimestamp == 0L) return
        if (now - lastTriggeredTime < 1500L) return // 1.5s debounce

        val delta = abs(lastJoltTimestamp - lastProximityTimestamp)
        if (delta <= TAP_COINCIDENCE_WINDOW_MS) {
            lastTriggeredTime = now
            val g = lastJoltMagnitudeG
            val r = lastRssi
            lastJoltTimestamp = 0L
            lastProximityTimestamp = 0L
            onTapDetected(g, r)
        }
    }

    fun simulateTap() {
        lastTriggeredTime = System.currentTimeMillis()
        onTapDetected(2.4, -32)
    }
}
