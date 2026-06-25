package mick.droneweather

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tle_data")
data class TleData(
    @PrimaryKey val satelliteName: String,
    val line1: String,
    val line2: String,
    val lastUpdated: Long
)
