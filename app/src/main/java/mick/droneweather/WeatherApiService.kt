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

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

// --- Open-Meteo Models ---

data class OpenMeteoResponse(
    val current: OpenMeteoCurrent?,
    val hourly: OpenMeteoHourly?,
    val daily: OpenMeteoDaily?,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
    val timezone: String?
)

data class OpenMeteoCurrent(
    val time: Long?,
    @SerializedName("temperature_2m") val temperature: Double?,
    @SerializedName("dew_point_2m") val dewPoint: Double?,
    @SerializedName("relative_humidity_2m") val humidity: Int?,
    @SerializedName("wind_speed_10m") val windSpeed: Double?,
    @SerializedName("wind_gusts_10m") val windGust: Double?,
    @SerializedName("wind_direction_10m") val windDeg: Int?,
    @SerializedName("cloud_cover") val clouds: Int?,
    @SerializedName("weather_code") val weatherCode: Int?,
    val precipitation: Double?,
    @SerializedName("visibility") val visibility: Double?
)

data class OpenMeteoHourly(
    val time: List<Long>?,
    @SerializedName("temperature_2m") val temperature: List<Double>?,
    @SerializedName("dew_point_2m") val dewPoint: List<Double>?,
    @SerializedName("wind_speed_10m") val windSpeed: List<Double>?,
    @SerializedName("wind_speed_80m") val windSpeed80m: List<Double>?,
    @SerializedName("wind_speed_120m") val windSpeed120m: List<Double>?,
    @SerializedName("wind_speed_180m") val windSpeed180m: List<Double>?,
    @SerializedName("wind_speed_975hPa") val windSpeed320m: List<Double>?,
    @SerializedName("wind_speed_950hPa") val windSpeed500m: List<Double>?,
    @SerializedName("wind_speed_925hPa") val windSpeed800m: List<Double>?,
    @SerializedName("wind_speed_900hPa") val windSpeed1000m: List<Double>?,
    @SerializedName("wind_speed_850hPa") val windSpeed1500m: List<Double>?,
    @SerializedName("wind_gusts_10m") val windGust: List<Double>?,
    @SerializedName("wind_direction_10m") val windDeg: List<Int>?,
    @SerializedName("precipitation_probability") val precipProb: List<Int>?,
    @SerializedName("weather_code") val weatherCode: List<Int>?,
    @SerializedName("visibility") val visibility: List<Double>?,
    @SerializedName("cloud_cover") val clouds: List<Int>?
)

data class OpenMeteoDaily(
    val time: List<Long>?,
    val sunrise: List<Long>?,
    val sunset: List<Long>?
)

data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?
)

// --- Existing Solar/Kp Models ---

data class KpEntry(
    @SerializedName("time_tag") val timeTag: String,
    @SerializedName("Kp") val kpValue: Double,
    @SerializedName("a_running") val aRunning: Int? = null,
    @SerializedName("station_count") val stationCount: Int? = null
)

data class GfzKpResponse(
    @SerializedName("Kp") val kpValues: List<Double>,
    @SerializedName("datetime") val datetime: List<String>
)

// --- GitHub Models for Updates ---

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String
)

// --- API Interfaces ---

interface WeatherApiService {
    @GET("forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,cloud_cover,weather_code,precipitation,visibility",
        @Query("hourly") hourly: String = "temperature_2m,dew_point_2m,wind_speed_10m,wind_speed_80m,wind_speed_120m,wind_speed_180m,wind_speed_975hPa,wind_speed_950hPa,wind_speed_925hPa,wind_speed_900hPa,wind_speed_850hPa,wind_gusts_10m,wind_direction_10m,precipitation_probability,weather_code,visibility,cloud_cover",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("models") models: String? = null,
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeformat: String = "unixtime",
        @Query("forecast_days") days: Int = 7
    ): OpenMeteoResponse

    @GET("meteofrance")
    suspend fun getMeteoFranceForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,cloud_cover,weather_code,precipitation,visibility",
        @Query("hourly") hourly: String = "temperature_2m,dew_point_2m,wind_speed_10m,wind_speed_80m,wind_speed_120m,wind_speed_180m,wind_speed_975hPa,wind_speed_950hPa,wind_speed_925hPa,wind_speed_900hPa,wind_speed_850hPa,wind_gusts_10m,wind_direction_10m,precipitation_probability,weather_code,visibility,cloud_cover",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeformat: String = "unixtime",
        @Query("forecast_days") days: Int = 7
    ): OpenMeteoResponse
}

interface GeocodingApiService {
    @GET("search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "fr",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

interface KpApiService {
    @GET("products/noaa-planetary-k-index.json")
    suspend fun getKpIndex(): List<KpEntry>

    @GET("json/planetary_k_index_1m.json")
    suspend fun getKpIndexRealTime(): List<KpEntry>

    @GET("products/solar-wind/plasma-7-day.json")
    suspend fun getSolarWind(): List<List<Any>>

    @GET("products/solar-wind/mag-7-day.json")
    suspend fun getMagData(): List<List<Any>>

    @GET("products/noaa-planetary-k-index-forecast.json")
    suspend fun getKpForecast(): List<Map<String, Any>>

    @GET("text/27-day-outlook.txt")
    suspend fun getKp27DayOutlook(): okhttp3.ResponseBody
}

interface GfzApiService {
    @GET("app/json/")
    suspend fun getKpIndex(
        @Query("index") index: String = "Kp",
        @Query("start") start: String,
        @Query("end") end: String
    ): GfzKpResponse
}

interface GitHubApiService {
    @Headers("User-Agent: DroneWeather-App")
    @GET("repos/Mick51/DroneWheather/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}
