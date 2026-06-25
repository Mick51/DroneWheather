package com.example.droneweather

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "satellite_forecast")
data class SatelliteForecast(
    @PrimaryKey val timestamp: Long, // Temps de la prédiction
    val availableSatellites: Int,     // Prédiction satellites en vue
    val lockedSatellites: Int,       // Prédiction satellites verrouillables (avec Kp)
    val kpIndex: Float
)
