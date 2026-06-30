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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCache(
    @PrimaryKey val id: Int = 0, // Une seule ligne pour le dernier ÃƒÂ©tat
    val windSpeed: String,
    val wind80m: String = "0",
    val wind120m: String = "0",
    val wind180m: String = "0",
    val wind320m: String = "0",
    val wind500m: String = "0",
    val wind800m: String = "0",
    val wind1000m: String = "0",
    val wind1500m: String = "0",
    val windGust: String,
    val windDeg: Int,
    val clouds: Int,
    val temperature: String,
    val kpValue: Double?,
    val currentBz: Double,
    val solarWindSpeed: Double = 0.0,
    val solarWindDensity: Double = 0.0,
    val sunrise: Long = 0L,
    val sunset: Long = 0L,
    val visibility: String,
    val precip: Int = 0,
    val weatherIcon: String? = null,
    val forecastJson: String? = null,
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val weatherSource: String = "OPEN_METEO",
    val lastUpdated: Long
)

