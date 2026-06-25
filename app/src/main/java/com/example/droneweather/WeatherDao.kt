package com.example.droneweather

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE id = 0")
    suspend fun getCachedData(): WeatherCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCache(data: WeatherCache)

    @Query("SELECT * FROM satellite_forecast ORDER BY timestamp ASC")
    suspend fun getSatelliteForecast(): List<SatelliteForecast>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSatelliteForecasts(forecasts: List<SatelliteForecast>)

    @Query("DELETE FROM satellite_forecast WHERE timestamp < :minTimestamp")
    suspend fun clearOldForecasts(minTimestamp: Long)

    @Query("SELECT * FROM tle_data")
    suspend fun getAllTleData(): List<TleData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTleData(tleList: List<TleData>)
}
