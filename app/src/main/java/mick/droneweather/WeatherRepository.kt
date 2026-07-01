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
import kotlin.math.abs

interface KpDataSource {
    suspend fun getKpIndex(): Double
}

class NoaaKpSource(private val api: KpApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        val list = api.getKpIndex()
        return list.lastOrNull()?.kpValue ?: 0.0
    }
}

class NoaaRealTimeKpSource(private val api: KpApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        val list = api.getKpIndexRealTime()
        return list.lastOrNull()?.kpValue ?: 0.0
    }
}

class GfzKpSource(private val api: GfzApiService) : KpDataSource {
    override suspend fun getKpIndex(): Double {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val end = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now.time)
        now.add(Calendar.HOUR, -12)
        val start = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now.time)
        val response = api.getKpIndex(start = start, end = end)
        return response.kpValues.lastOrNull() ?: 0.0
    }
}

class WeatherRepository(
    private val weatherApi: WeatherApiService,
    private val kpApi: KpApiService,
    private val gfzApi: GfzApiService,
    private val weatherDao: WeatherDao
) {
    private val remoteSources = listOf(
        NoaaKpSource(kpApi),
        NoaaRealTimeKpSource(kpApi),
        GfzKpSource(gfzApi)
    )

    private val cacheTimeout = 15 * 60 * 1000L // 15 minutes
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
        source: WeatherSource = WeatherSource.OPEN_METEO
    ): WeatherCache = weatherMutex.withLock {
        val cached = weatherDao.getCachedData(source.name)
        val isExpired = cached == null || (System.currentTimeMillis() - cached.lastUpdated > cacheTimeout)

        val locationChanged = if (lat != null && lon != null && cached != null) {
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(lat, lon, cached.latitude, cached.longitude, dist)
            dist[0] > 100 
        } else false
        
        if (!force && !isExpired && !locationChanged) {
            Log.d("WeatherRepository", "Cache OK for ${source.name}")
            return@withLock cached!!
        }

        Log.d("WeatherRepository", "Fetching fresh data [Source: ${source.name}, Force: $force, Expired: $isExpired, LocChanged: $locationChanged]")

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
                    } catch (e: Exception) {
                        Log.w("WeatherRepository", "Primary fetch failed: ${e.message}, falling back to default")
                        weatherApi.getForecast(lat = targetLat, lon = targetLon, timezone = deviceTimeZoneId)
                    }
                }

                val kpDeferred = async { remoteSources.map { s -> async { try { s.getKpIndex() } catch (_: Exception) { 0.0 } } }.awaitAll().maxOrNull() ?: 0.0 }
                val plasmaDeferred = async { try { kpApi.getSolarWind() } catch (_: Exception) { emptyList() } }
                val magDeferred = async { try { kpApi.getMagData() } catch (_: Exception) { emptyList() } }
                val kpForecastDeferred = async { try { kpApi.getKpForecast() } catch (_: Exception) { null } }
                val kp27DayDeferred = async { try { kpApi.getKp27DayOutlook() } catch (_: Exception) { null } }

                val response = weatherDeferred.await()
                val newKpValue = kpDeferred.await()
                val plasmaList = plasmaDeferred.await()
                val magList = magDeferred.await()
                val kpForecastRaw = kpForecastDeferred.await()
                val kp27DayRaw = kp27DayDeferred.await()?.string()

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
                forecastItems.add(mapOf(
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
                    "isNow" to true
                ))

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
                Log.d("WeatherRepository", "Saved fresh data for source: ${source.name}")
                newData
            } catch (e: Exception) {
                Log.e("WeatherRepository", "Fatal Fetch Error: ${e.message}")
                if (cached != null) {
                    Log.w("WeatherRepository", "Returning stale cache due to error")
                    cached
                } else {
                    throw e
                }
            }
        }
    }

    fun getSatelliteForecastFlow(): Flow<List<SatelliteForecast>> = weatherDao.getSatelliteForecastFlow()
    suspend fun getAllTleData(): List<TleData> = weatherDao.getAllTleData()
    suspend fun updateSatelliteForecasts(forecasts: List<SatelliteForecast>) {
        weatherDao.clearOldForecasts(System.currentTimeMillis() - 3600000)
        weatherDao.insertSatelliteForecasts(forecasts)
    }

    private fun findKpForTime(timestamp: Long, kpForecast: List<Map<String, Any>>?, kp27Day: String?): String {
        kpForecast?.let { list ->
            val closest = list.minByOrNull { item ->
                val timeStr = item["time_tag"] as? String ?: ""
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    val itemTs = sdf.parse(timeStr)?.time?.div(1000) ?: 0L
                    abs(itemTs - timestamp)
                } catch (_: Exception) { Long.MAX_VALUE }
            }
            val kpValue = closest?.get("kp")
            if (kpValue != null) return kpValue.toString()
        }
        
        kp27Day?.let { outlook ->
            val date = Date(timestamp * 1000)
            val extracted = extractKpFromOutlook(outlook, date)
            if (extracted != null) return extracted.toString()
        }

        return "2.0"
    }

    private fun extractKpFromOutlook(outlook: String, date: Date): Double? {
        try {
            val lines = outlook.lines()
            val searchStr = SimpleDateFormat("yyyy MMM dd", Locale.US).format(date).uppercase()
            
            for (line in lines) {
                if (line.contains(searchStr)) {
                    val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (parts.size >= 4) return parts[3].toDoubleOrNull()
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
