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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

interface KpDataSource {
    suspend fun getKpIndex(): Double
}

class NoaaKpSource(private val api: KpApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        val list = try { api.getKpIndex() } catch (_: Exception) { emptyList() }
        return list.lastOrNull()?.effectiveKp ?: 0.0
    }
}

class NoaaRealTimeKpSource(private val api: KpApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        val list = try { api.getKpIndexRealTime() } catch (_: Exception) { emptyList() }
        return list.lastOrNull()?.effectiveKp ?: 0.0
    }
}

class GfzKpSource(private val api: GfzApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        return try {
            val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val end = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now.time)
            now.add(Calendar.HOUR, -12)
            val start = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now.time)
            val response = api.getKpIndex(start = start, end = end)
            response.kpValues.lastOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
}

class WeatherRepository(
    private val weatherApi: WeatherApiService,
    private val kpApi: KpApiService,
    gfzApi: GfzApiService,
    private val weatherDao: WeatherDao,
) {
    private val remoteSources = listOf(
        NoaaKpSource(kpApi),
        NoaaRealTimeKpSource(kpApi),
        GfzKpSource(gfzApi),
    )

    private val cacheTimeout = 15 * 60 * 1000L // 15 minutes for weather
    private val kpCacheTimeout = 1 * 60 * 1000L // 1 minute for live Kp
    private val weatherMutex = Mutex()

    private fun mapWmoToIcon(code: Int?): String {
        return when (code ?: 0) {
            0 -> "01d"
            1, 2, 3 -> "02d"
            45, 48 -> "50d"
            51, 53, 55 -> "09d"
            61, 63, 65 -> "10d"
            71, 73, 75, 77 -> "13d"
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
        source: WeatherSource = WeatherSource.OPEN_METEO,
    ): WeatherCache = weatherMutex.withLock {
        val cached = weatherDao.getCachedData(source.name)
        val isExpired = cached == null || (System.currentTimeMillis() - cached.lastUpdated > cacheTimeout)
        val isKpExpired = cached == null || (System.currentTimeMillis() - cached.lastUpdated > kpCacheTimeout)

        val locationChanged = if (lat != null && lon != null && cached != null) {
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(lat, lon, cached.latitude, cached.longitude, dist)
            dist[0] > 100 
        } else false
        
        // If weather is fresh, we might still want fresh Kp
        if (!force && !isExpired && !locationChanged && !isKpExpired) {
            Log.d("WeatherRepository", "Cache OK for ${source.name}")
            return@withLock cached
        }

        return@withLock coroutineScope {
            try {
                val targetLat: Double
                val targetLon: Double
                val resolvedCityName: String

                if (lat != null && lon != null) {
                    targetLat = lat
                    targetLon = lon
                    resolvedCityName = city
                } else {
                    val geoResponse = RetrofitInstance.geocodingApi.search(city)
                    val firstResult = geoResponse.results?.firstOrNull() ?: throw Exception("Ville non trouvée")
                    targetLat = firstResult.latitude
                    targetLon = firstResult.longitude
                    resolvedCityName = firstResult.name
                }

                val deviceTimeZoneId = Calendar.getInstance().timeZone.id
                
                val weatherDeferred = async { 
                    try {
                        if (source == WeatherSource.METEOCIEL) {
                            weatherApi.getMeteoFranceForecast(
                                lat = targetLat, 
                                lon = targetLon, 
                                timezone = deviceTimeZoneId
                            )
                        } else {
                            weatherApi.getForecast(
                                lat = targetLat, 
                                lon = targetLon,
                                timezone = deviceTimeZoneId
                            )
                        }
                    } catch (_: Exception) {
                        weatherApi.getForecast(lat = targetLat, lon = targetLon, timezone = deviceTimeZoneId)
                    }
                }

                val kpDeferred = async { 
                    val results = remoteSources.map { s ->
                        async {
                            try {
                                withTimeout(5000.milliseconds) { s.getKpIndex() }
                            } catch (_: Exception) {
                                -1.0
                            }
                        }
                    }.awaitAll()
                    
                    val validResults = results.filter { it >= 0.0 }
                    if (validResults.isNotEmpty()) validResults.maxOrNull() ?: 2.0 else 2.0
                }

                val plasmaDeferred = async { try { withTimeout(5000.milliseconds) { kpApi.getSolarWind() } } catch (_: Exception) { emptyList() } }
                val magDeferred = async { try { withTimeout(5000.milliseconds) { kpApi.getMagData() } } catch (_: Exception) { emptyList() } }
                val kpForecastDeferred = async { try { withTimeout(5000.milliseconds) { kpApi.getKpForecast() } } catch (_: Exception) { emptyList() } }
                val kp27DayDeferred = async { try { withTimeout(5000.milliseconds) { kpApi.getKp27DayOutlook() } } catch (_: Exception) { null } }

                Log.d("WeatherRepository", "Awaiting results...")
                val response = weatherDeferred.await()
                val newKpValue = try { withTimeout(8000.milliseconds) { kpDeferred.await() } } catch(_: Exception) { 2.0 }
                val plasmaList = plasmaDeferred.await()
                val magList = magDeferred.await()
                val kpForecastRaw = kpForecastDeferred.await()
                val kp27DayRaw = try { kp27DayDeferred.await()?.string() } catch(_: Exception) { null }

                // Solar Wind processing (Robust)
                val lastMag = magList.lastOrNull { it.firstOrNull()?.toString()?.contains(":") == true }
                val bzIdx = magList.firstOrNull()?.indexOf("bz_gsm")?.takeIf { it != -1 } ?: 4
                val newBz = lastMag?.getOrNull(bzIdx)?.toString()?.toDoubleOrNull() ?: 0.0
                
                val lastPlasma = plasmaList.lastOrNull { it.firstOrNull()?.toString()?.contains(":") == true }
                val speedIdx = plasmaList.firstOrNull()?.indexOf("speed")?.takeIf { it != -1 } ?: 2
                val densityIdx = plasmaList.firstOrNull()?.indexOf("density")?.takeIf { it != -1 } ?: 1
                val newSolarWindSpeed = lastPlasma?.getOrNull(speedIdx)?.toString()?.toDoubleOrNull() ?: 0.0
                val newSolarWindDensity = lastPlasma?.getOrNull(densityIdx)?.toString()?.toDoubleOrNull() ?: 0.0

                val current = response.current
                val hourly = response.hourly
                val nowSeconds = System.currentTimeMillis() / 1000
                
                val currentHourIdx = hourly?.time?.indexOfFirst { it >= nowSeconds - 1800 }?.coerceAtLeast(0) ?: 0
                
                val newWindSpeed = current?.windSpeed?.toInt()?.toString() ?: "0"
                val newWind80m = hourly?.windSpeed80m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWindSpeed
                val newWind120m = hourly?.windSpeed120m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind80m
                val newWind180m = hourly?.windSpeed180m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind120m
                val newWind320m = hourly?.windSpeed320m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind180m
                val newWind500mRaw = hourly?.windSpeed500m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind320m
                val newWind800m = hourly?.windSpeed800m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind500mRaw
                val newWind1000m = hourly?.windSpeed1000m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind800m
                val newWind1500m = hourly?.windSpeed1500m?.getOrNull(currentHourIdx)?.toInt()?.toString() ?: newWind1000m

                val newWindGust = current?.windGust?.toInt()?.toString() ?: "0"
                val newWindDeg = current?.windDeg ?: 0
                val newClouds = current?.clouds ?: 0
                val newPrecip = if ((current?.precipitation ?: 0.0) > 0) 100 else 0
                val newTemp = current?.temperature?.toInt()?.toString() ?: "0"
                val newDewPoint = current?.dewPoint?.toInt()?.toString() ?: "0"
                val newWeatherIcon = mapWmoToIcon(current?.weatherCode)
                
                val currentVis = current?.visibility ?: hourly?.visibility?.getOrNull(currentHourIdx) ?: 10000.0
                val visKm = (currentVis / 1000).toInt()
                val newVisibility = if (visKm >= 10) ">10" else visKm.toString()
                
                val newSunrise = response.daily?.sunrise?.firstOrNull() ?: 0L
                val newSunset = response.daily?.sunset?.firstOrNull() ?: 0L

                val forecastItems = mutableListOf<Map<String, Any>>()
                forecastItems.add(
                    mapOf(
                        "dt" to nowSeconds.toDouble(),
                        "temp" to newTemp,
                        "dew" to newDewPoint,
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
                        "isNow" to true,
                    )
                )

                hourly?.time?.forEachIndexed { i, ts ->
                    if (ts < (System.currentTimeMillis() / 1000) - 3600) return@forEachIndexed
                    
                    val kpForTime = findKpForTime(ts, kpForecastRaw, kp27DayRaw)
                    val hVis = hourly.visibility?.getOrNull(i) ?: 10000.0
                    val vKm = (hVis / 1000).toInt()
                    val vStr = if (vKm >= 10) ">10" else vKm.toString()

                    forecastItems.add(mapOf(
                        "dt" to ts.toDouble(),
                        "temp" to (hourly.temperature?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "dew" to (hourly.dewPoint?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind" to (hourly.windSpeed?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind80m" to (hourly.windSpeed80m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind120m" to (hourly.windSpeed120m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind180m" to (hourly.windSpeed180m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind320m" to (hourly.windSpeed320m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind500m" to (hourly.windSpeed500m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind800m" to (hourly.windSpeed800m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind1000m" to (hourly.windSpeed1000m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "wind1500m" to (hourly.windSpeed1500m?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "gust" to (hourly.windGust?.getOrNull(i)?.toInt()?.toString() ?: "0"),
                        "windDeg" to (hourly.windDeg?.getOrNull(i)?.toDouble() ?: 0.0),
                        "clouds" to (hourly.clouds?.getOrNull(i)?.toDouble() ?: 0.0),
                        "visibility" to vStr,
                        "precip" to (hourly.precipProb?.getOrNull(i)?.toDouble() ?: 0.0),
                        "kp" to kpForTime,
                        "icon" to mapWmoToIcon(hourly.weatherCode?.getOrNull(i))
                    ))
                }

                val forecastJson = Gson().toJson(forecastItems.sortedBy { (it["dt"] as? Number)?.toDouble() ?: 0.0 })

                val newData = WeatherCache(
                    weatherSource = source.name,
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
                    dewPoint = newDewPoint,
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
                    lastUpdated = System.currentTimeMillis()
                )

                weatherDao.updateCache(newData)
                newData
            } catch (e: Exception) {
                cached ?: throw e
            }
        }
    }

    fun getSatelliteForecastFlow(): Flow<List<SatelliteForecast>> = weatherDao.getSatelliteForecastFlow()
    suspend fun getAllTleData(): List<TleData> = weatherDao.getAllTleData()
    suspend fun updateSatelliteForecasts(forecasts: List<SatelliteForecast>) {
        weatherDao.clearOldForecasts(System.currentTimeMillis() - 3600000)
        weatherDao.insertSatelliteForecasts(forecasts)
    }

    suspend fun clearAllData() {
        weatherDao.clearWeatherCache()
        weatherDao.clearSatelliteForecasts()
        weatherDao.clearTleData()
    }

    private fun findKpForTime(timestamp: Long, kpForecast: List<KpEntry>?, kp27Day: String?): String {
        fun parseNoaaTime(tag: String): Long {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                sdf.parse(tag)?.time?.div(1000) ?: 0L
            } catch (_: Exception) { 
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    sdf.parse(tag)?.time?.div(1000) ?: 0L
                } catch(_: Exception) { 0L }
            }
        }

        // 1. Try NOAA 3-day forecast with Linear Interpolation
        kpForecast?.takeIf { it.isNotEmpty() }?.let { list ->
            val sortedList = list.map { it to parseNoaaTime(it.timeTag) }
                .filter { it.second != 0L }
                .sortedBy { it.second }

            val before = sortedList.lastOrNull { it.second <= timestamp }
            val after = sortedList.firstOrNull { it.second > timestamp }

            // Important: only interpolate if we are WITHIN the 3-day range (after is not null)
            if (before != null && after != null) {
                val timeDiff = (after.second - before.second).toDouble()
                val progress = (timestamp - before.second) / timeDiff
                val kpDiff = after.first.effectiveKp - before.first.effectiveKp
                val interpolatedKp = before.first.effectiveKp + (progress * kpDiff)
                return String.format(Locale.US, "%.1f", interpolatedKp)
            }
            // If after is null, we are past the 3-day detailed range. 
            // DON'T return before.kp, continue to fallback below.
        }
        
        // 2. Fallback to NOAA 27-day outlook (with smooth hourly interpolation)
        kp27Day?.let { outlook ->
            val date = Date(timestamp * 1000)
            val currentKp = extractKpFromOutlook(outlook, date) ?: 2.0
            
            // For a continuous curve, we interpolate between today and tomorrow
            val tomorrow = Date((timestamp + 86400) * 1000)
            val nextKp = extractKpFromOutlook(outlook, tomorrow) ?: currentKp
            
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = timestamp * 1000 }
            val hourOfDay = cal[Calendar.HOUR_OF_DAY]
            val minuteOfHour = cal[Calendar.MINUTE]
            
            // Calculate progress through the day (0.0 to 1.0)
            val dayProgress = (hourOfDay * 60 + minuteOfHour) / 1440.0
            
            // Linear interpolation between the daily predicted values
            val smoothedKp = currentKp + (dayProgress * (nextKp - currentKp))
            return String.format(Locale.US, "%.1f", smoothedKp)
        }

        return "2.0"
    }

    private fun extractKpFromOutlook(outlook: String, date: Date): Double? {
        try {
            val lines = outlook.lines()
            val sdfSearch = SimpleDateFormat("yyyy MMM dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val searchStr = sdfSearch.format(date).uppercase()
            
            for (line in lines) {
                val upperLine = line.uppercase().trim()
                if (upperLine.contains(searchStr)) {
                    val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (parts.size >= 4) {
                        val kpValue = if (parts.size >= 6) {
                            parts[5].toDoubleOrNull()
                        } else {
                            parts.lastOrNull()?.toDoubleOrNull()
                        }

                        if (kpValue != null && kpValue in 0.0..9.0) {
                            return kpValue
                        } else {
                            for (i in parts.indices.reversed()) {
                                val v = parts[i].toDoubleOrNull()
                                if (v != null && v in 0.0..9.0) {
                                    return v
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
