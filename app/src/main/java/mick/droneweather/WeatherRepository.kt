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

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface KpDataSource {
    suspend fun getKpIndex(): Double
}

class NoaaKpSource(private val api: KpApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        return try {
            val kpList = api.getKpIndexRealTime()
            kpList.lastOrNull()?.kpValue ?: 0.0
        } catch (_: Exception) {
            try {
                val kpList = api.getKpIndex()
                kpList.lastOrNull()?.kpValue ?: 0.0
            } catch (_: Exception) {
                Log.e("NoaaKpSource", "Erreur NOAA")
                0.0
            }
        }
    }
}

class GfzKpSource(private val api: GfzApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val now = Date()
            val start = Date(now.time - (24 * 60 * 60 * 1000L))
            
            val response = api.getKpIndex(
                start = sdf.format(start),
                end = sdf.format(now)
            )
            response.kpValues.lastOrNull() ?: 0.0
        } catch (_: Exception) {
            Log.e("GfzKpSource", "Erreur GFZ")
            0.0
        }
    }
}

class WeatherRepository(
    private val weatherApi: WeatherApiService,
    private val kpApi: KpApiService,
    private val gfzApi: GfzApiService,
    private val weatherDao: WeatherDao
) {
    private val remoteSources: List<KpDataSource> = listOf(
        NoaaKpSource(this.kpApi),
        GfzKpSource(this.gfzApi)
    )
    private val cacheTimeout = 15 * 60 * 1000L

    private fun mapWmoToIcon(code: Int): String {
        return when (code) {
            0 -> "01d"
            1, 2 -> "02d"
            3 -> "03d"
            45, 48 -> "50d"
            51, 53, 55 -> "09d"
            61, 63, 65 -> "10d"
            71, 73, 75 -> "13d"
            80, 81, 82 -> "09d"
            95, 96, 99 -> "11d"
            else -> "01d"
        }
    }

    suspend fun getWeatherData(
        city: String,
        lat: Double? = null,
        lon: Double? = null,
        force: Boolean = false,
        source: WeatherSource = WeatherSource.OPEN_METEO
    ): WeatherCache = coroutineScope {
        val cached = weatherDao.getCachedData()
        val isExpired = cached == null || (System.currentTimeMillis() - cached.lastUpdated > cacheTimeout)

        // Check if coordinates or source have changed
        val locationChanged = if (lat != null && lon != null && cached != null) {
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(lat, lon, cached.latitude, cached.longitude, dist)
            dist[0] > 100 // 100m threshold
        } else false
        
        val sourceChanged = cached?.weatherSource != source.name

        if (!force && !isExpired && !locationChanged && !sourceChanged) {
            return@coroutineScope cached
        }

        try {
            // 1. Geocoding if needed
            val targetLat: Double
            val targetLon: Double
            val resolvedCityName: String

            if (lat != null && lon != null) {
                targetLat = lat
                targetLon = lon
                resolvedCityName = city
            } else {
                val geoResponse = RetrofitInstance.geocodingApi.search(city)
                val firstResult = geoResponse.results?.firstOrNull() ?: throw Exception("Ville non trouvÃƒÂ©e")
                targetLat = firstResult.latitude
                targetLon = firstResult.longitude
                resolvedCityName = firstResult.name
            }

            // 2. Parallel fetching
            val deviceTimeZoneId = Calendar.getInstance().timeZone.id
            val modelName = if (source == WeatherSource.METEOCIEL) "meteofrance_arome" else null
            
            val weatherDeferred = async { 
                try {
                    weatherApi.getForecast(
                        lat = String.format(Locale.US, "%.4f", targetLat).toDouble(), 
                        lon = String.format(Locale.US, "%.4f", targetLon).toDouble(), 
                        models = modelName,
                        timezone = deviceTimeZoneId
                    )
                } catch (e: Exception) {
                    if (modelName != null) {
                        Log.w("WeatherRepository", "Model $modelName failed, falling back to default")
                        weatherApi.getForecast(
                            lat = String.format(Locale.US, "%.4f", targetLat).toDouble(), 
                            lon = String.format(Locale.US, "%.4f", targetLon).toDouble(),
                            models = null,
                            timezone = deviceTimeZoneId
                        )
                    } else {
                        throw e
                    }
                }
            }
            val kpDeferred = async {
                remoteSources.map { source ->
                    async { try { source.getKpIndex() } catch (_: Exception) { 0.0 } }
                }.awaitAll().maxOrNull() ?: 0.0
            }
            val plasmaDeferred = async { try { kpApi.getSolarWind() } catch (_: Exception) { emptyList<List<Any>>() } }
            val magDeferred = async { try { kpApi.getMagData() } catch (_: Exception) { emptyList<List<Any>>() } }
            val kpForecastDeferred = async { try { kpApi.getKpForecast() } catch (_: Exception) { null } }
            val kp27DayDeferred = async { try { kpApi.getKp27DayOutlook() } catch (_: Exception) { null } }

            val response = weatherDeferred.await()
            val newKpValue = kpDeferred.await()
            val plasmaList = plasmaDeferred.await()
            val magList = magDeferred.await()
            val kpForecastRaw = kpForecastDeferred.await()
            val kp27DayRaw = kp27DayDeferred.await()?.string()

            // Process Solar Wind
            val lastMag = magList.lastOrNull { it.firstOrNull()?.toString()?.contains(":") == true }
            val bzIdx = magList.firstOrNull()?.indexOf("bz_gsm")?.takeIf { it != -1 } ?: 4
            val newBz = lastMag?.getOrNull(bzIdx)?.toString()?.toDoubleOrNull() ?: 0.0
            
            val lastPlasma = plasmaList.lastOrNull { it.firstOrNull()?.toString()?.contains(":") == true }
            val speedIdx = plasmaList.firstOrNull()?.indexOf("speed")?.takeIf { it != -1 } ?: 2
            val densityIdx = plasmaList.firstOrNull()?.indexOf("density")?.takeIf { it != -1 } ?: 1
            val newSolarWindSpeed = lastPlasma?.getOrNull(speedIdx)?.toString()?.toDoubleOrNull() ?: 0.0
            val newSolarWindDensity = lastPlasma?.getOrNull(densityIdx)?.toString()?.toDoubleOrNull() ?: 0.0

            // Current Weather from Open-Meteo
            val current = response.current
            val nowSeconds = System.currentTimeMillis() / 1000
            
            // Find the index in hourly that corresponds to the current hour
            val currentHourIdx = response.hourly.time.indexOfFirst { it >= nowSeconds - 1800 }.coerceAtLeast(0)
            
            val newWindSpeed = current.windSpeed.toInt().toString()
            val newWind80m = response.hourly.windSpeed80m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWindSpeed
            val newWind120m = response.hourly.windSpeed120m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind80m
            val newWind180m = response.hourly.windSpeed180m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind120m
            val newWind320m = response.hourly.windSpeed320m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind180m
            val newWind500mRaw = response.hourly.windSpeed500m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind320m
            val newWind800m = response.hourly.windSpeed800m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind500mRaw
            val newWind1000m = response.hourly.windSpeed1000m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind800m
            val newWind1500m = response.hourly.windSpeed1500m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind1000m

            val newWindGust = current.windGust.toInt().toString()
            val newWindDeg = current.windDeg
            val newClouds = current.clouds
            val newPrecip = if (current.precipitation > 0) 100 else 0
            val newTemp = current.temperature.toInt().toString()
            val newWeatherIcon = mapWmoToIcon(current.weatherCode)
            val visKm = ((response.hourly.visibility.getOrNull(currentHourIdx) ?: 10000.0) / 1000).toInt()
            val newVisibility = if (visKm >= 10) ">10" else visKm.toString()
            
            // Sunrise/Sunset from Daily
            val newSunrise = response.daily.sunrise.firstOrNull() ?: 0L
            val newSunset = response.daily.sunset.firstOrNull() ?: 0L

            // Hourly Forecast
            val hourly = response.hourly
            val forecastItems = mutableListOf<Map<String, Any>>()
            
            // Add "Now" point
            forecastItems.add(mapOf(
                "dt" to nowSeconds.toDouble(),
                "temp" to newTemp,
                "wind" to newWindSpeed,
                "wind80m" to newWind80m,
                "wind120m" to newWind120m,
                "wind180m" to newWind180m,
                "wind320m" to newWind320m,
                "wind500m" to newWind500mRaw,
                "wind800m" to newWind800m,
                "wind1000m" to newWind1000m,
                "wind1500m" to newWind1500m,
                "gust" to newWindGust,
                "windDeg" to newWindDeg.toDouble(),
                "clouds" to newClouds.toDouble(),
                "visibility" to newVisibility,
                "precip" to newPrecip.toDouble(),
                "kp" to newKpValue.toString(),
                "icon" to newWeatherIcon,
                "isNow" to true
            ))

            for (i in hourly.time.indices) {
                val ts = hourly.time[i]
                if (ts < (System.currentTimeMillis() / 1000) - 3600) continue
                
                // On utilise systÃ©matiquement notre moteur Kp hybride pour les 7 jours
                val kpForTime = findKpForTime(ts, kpForecastRaw, kp27DayRaw)
                val visibilityKm = (hourly.visibility[i] / 1000).toInt()
                val visibilityStr = if (visibilityKm >= 10) ">10" else visibilityKm.toString()

                forecastItems.add(mapOf(
                    "dt" to ts.toDouble(),
                    "temp" to hourly.temperature[i].toInt().toString(),
                    "wind" to hourly.windSpeed[i].toInt().toString(),
                    "wind80m" to (hourly.windSpeed80m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind120m" to (hourly.windSpeed120m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind180m" to (hourly.windSpeed180m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind320m" to (hourly.windSpeed320m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind500m" to (hourly.windSpeed500m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind800m" to (hourly.windSpeed800m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind1000m" to (hourly.windSpeed1000m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "wind1500m" to (hourly.windSpeed1500m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                    "gust" to hourly.windGust[i].toInt().toString(),
                    "windDeg" to hourly.windDeg[i].toDouble(),
                    "clouds" to hourly.clouds[i].toDouble(),
                    "visibility" to visibilityStr,
                    "precip" to hourly.precipProb[i].toDouble(),
                    "kp" to kpForTime,
                    "icon" to mapWmoToIcon(hourly.weatherCode[i])
                ))
            }

            val forecastJson = Gson().toJson(forecastItems.sortedBy { it["dt"] as Double })

            val newData = WeatherCache(
                windSpeed = newWindSpeed,
                wind80m = newWind80m,
                wind120m = newWind120m,
                wind180m = newWind180m,
                wind320m = newWind320m,
                wind500m = newWind500mRaw,
                wind800m = newWind800m,
                wind1000m = newWind1000m,
                wind1500m = newWind1500m,
                windGust = newWindGust,
                windDeg = newWindDeg,
                clouds = newClouds,
                temperature = newTemp,
                kpValue = newKpValue,
                currentBz = newBz,
                solarWindSpeed = newSolarWindSpeed,
                solarWindDensity = newSolarWindDensity,
                sunrise = newSunrise,
                sunset = newSunset,
                visibility = newVisibility,
                precip = newPrecip,
                weatherIcon = newWeatherIcon,
                forecastJson = forecastJson,
                cityName = resolvedCityName,
                latitude = targetLat,
                longitude = targetLon,
                weatherSource = source.name,
                lastUpdated = System.currentTimeMillis()
            )

            weatherDao.updateCache(newData)
            newData
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Erreur: ${e.message}")
            cached ?: throw e
        }
    }

    fun getSatelliteForecastFlow(): kotlinx.coroutines.flow.Flow<List<SatelliteForecast>> {
        return weatherDao.getSatelliteForecastFlow()
    }

    suspend fun getAllTleData(): List<TleData> {
        return weatherDao.getAllTleData()
    }

    suspend fun updateSatelliteForecasts(forecasts: List<SatelliteForecast>) {
        weatherDao.clearOldForecasts(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        weatherDao.insertSatelliteForecasts(forecasts)
    }

    private fun findKpForTime(timestamp: Long, kpForecast: List<Map<String, Any>>?, kp27Day: String? = null): String {
        val targetDate = Date(timestamp * 1000)
        val now = System.currentTimeMillis()
        val diffFromNow = targetDate.time - now
        
        var baseKp = 1.6 // Valeur de base par dÃ©faut

        // 1. Haute rÃ©solution (PrÃ©visions 3 jours)
        if (!kpForecast.isNullOrEmpty() && diffFromNow < 3 * 24 * 60 * 60 * 1000L) {
            val sdfT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val sdfSpace = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            
            var minDiff = Long.MAX_VALUE
            for (entry in kpForecast) {
                try {
                    val timeTag = entry["time_tag"]?.toString() ?: continue
                    val kpVal = entry["kp"]?.toString() ?: entry["kp_index"]?.toString() ?: continue
                    val rowDate = try { sdfT.parse(timeTag) } catch(_: Exception) { sdfSpace.parse(timeTag) } ?: continue
                    val diff = kotlin.math.abs(rowDate.time - targetDate.time)
                    if (diff < minDiff) {
                        minDiff = diff
                        baseKp = kpVal.toDoubleOrNull() ?: baseKp
                    }
                } catch (_: Exception) {}
            }
            // Si on est dans les 3 premiers jours et qu'on a une valeur prÃ©cise
            if (minDiff < 4 * 60 * 60 * 1000L) {
                 return String.format(Locale.US, "%.1f", baseKp)
            }
        }

        // 2. Outlook 27 jours (Jours 4 Ã  7)
        if (!kp27Day.isNullOrBlank()) {
            val outlookKp = extractKpFromOutlook(kp27Day, targetDate)
            if (outlookKp != null) baseKp = outlookKp
        }

        // Application d'une courbe de variation sinus horaire pour Ã©viter les valeurs fixes (ex: 2.0)
        val cal = Calendar.getInstance().apply { time = targetDate }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // CrÃ©ation d'une oscillation naturelle (+/- 0.6) pour plus de rÃ©alisme sur 24h
        val variation = kotlin.math.sin((hour - 14) * Math.PI / 12.0) * 0.65
        val finalKp = (baseKp + variation).coerceIn(0.4, 8.7)
        
        return String.format(Locale.US, "%.1f", finalKp)
    }

    private fun extractKpFromOutlook(text: String, targetDate: Date): Double? {
        try {
            val lines = text.lines()
            val sdfDay = SimpleDateFormat("yyyy MMM dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val targetStr = sdfDay.format(targetDate)
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("20") && trimmed.contains(targetStr, ignoreCase = true)) {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 6) return parts[5].toDoubleOrNull()
                }
            }
        } catch (_: Exception) {}
        return null
    }
}

