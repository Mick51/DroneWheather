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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE weatherSource = :source")
    suspend fun getCachedData(source: String): WeatherCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCache(data: WeatherCache)

    @Query("SELECT * FROM satellite_forecast ORDER BY timestamp ASC")
    fun getSatelliteForecastFlow(): kotlinx.coroutines.flow.Flow<List<SatelliteForecast>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSatelliteForecasts(forecasts: List<SatelliteForecast>)

    @Query("DELETE FROM satellite_forecast WHERE timestamp < :minTimestamp")
    suspend fun clearOldForecasts(minTimestamp: Long)

    @Query("SELECT * FROM tle_data")
    suspend fun getAllTleData(): List<TleData>

    @Query("DELETE FROM tle_data")
    suspend fun clearTleData()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTleData(tleList: List<TleData>)
}

