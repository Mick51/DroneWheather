package mick.droneweather

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "satellite_forecast")
data class SatelliteForecast(
    @PrimaryKey val timestamp: Long, // Temps de la prÃ©diction
    val availableSatellites: Int,     // PrÃ©diction satellites en vue
    val lockedSatellites: Int,       // PrÃ©diction satellites verrouillables (avec Kp)
    val kpIndex: Float
)
