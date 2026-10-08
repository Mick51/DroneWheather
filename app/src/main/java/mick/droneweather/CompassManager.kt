/*
 * Copyright (C) 2026 Mick
 *
 * Ce programme est un logiciel libre : vous pouvez le redistribuer et/ou le modifier
 * selon les termes de la Licence Publique Générale GNU telle que publiée par
 * la Free Software Foundation, soit la version 3 de la licence, ou (au choix)
 * toute version ultérieure.
 *
 * Ce programme est distribué dans l'espoir qu'il sera utile, mais SANS AUCUNE GARANTIE ;
 * sans même la garantie implicite de COMMERCIALISATION ou D'ADÉQUATION À UN USAGE PARTICULIER.
 * Voir la Licence Publique Générale GNU pour plus de détails.
 *
 * Vous devriez avoir reçu une copie de la Licence Publique Générale GNU avec ce programme.
 * Sinon, voir <https://www.gnu.org/licenses/>.
 */

package mick.droneweather

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
            
            // 1. Convertir en degrés et normaliser l'azimut initial entre [0°, 360°[
            // getOrientation retourne typiquement une valeur entre -PI et PI (-180° à 180°)
            val rawAzimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val azimuth = (rawAzimuthDeg + 360f) % 360f
            
            // 2. Calculer la différence et la normaliser dans l'intervalle [-180°, 180°[
            // pour gérer le passage du Nord (discontinuité 359° <-> 0°)
            var diff = azimuth - lastAzimuth
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            
            // 3. Appliquer le filtre passe-bas et normaliser la valeur lissée entre [0°, 360°[
            val smoothedAzimuth = (lastAzimuth + alpha * diff + 360f) % 360f
            lastAzimuth = smoothedAzimuth
            
            onAzimuthChanged?.invoke(smoothedAzimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
