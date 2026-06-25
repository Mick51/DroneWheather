package com.example.droneweather

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class CompassManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var rotationMatrix = FloatArray(9)
    private var orientation = FloatArray(3)
    private var lastAzimuth = 0f
    private val alpha = 0.15f // Smoothing factor

    var onAzimuthChanged: ((Float) -> Unit)? = null

    fun start() {
        rotationSensor?.let { 
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) 
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            
            // Low-pass filter to smooth the needle movement
            val smoothedAzimuth = lastAzimuth + alpha * (azimuth - lastAzimuth)
            lastAzimuth = smoothedAzimuth
            
            onAzimuthChanged?.invoke(smoothedAzimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
