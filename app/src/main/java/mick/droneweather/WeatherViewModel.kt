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

import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mick.droneweather.ui.theme.GreenSafe
import mick.droneweather.ui.theme.RedDanger
import mick.droneweather.ui.theme.YellowWarn
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

data class HourlyForecast(
    val timestamp: Long,
    val time: String,
    val temp: String,
    val dewPoint: String,
    val wind: String,
    val wind80m: String,
    val wind120m: String,
    val wind180m: String,
    val wind320m: String,
    val wind500m: String,
    val wind800m: String,
    val wind1000m: String,
    val wind1500m: String,
    val gusts: String,
    val kp: String,
    val iconUrl: String,
    val windDeg: Int,
    val clouds: Int,
    val visibility: String,
    val precip: Int,
    val weatherIcon: String?,
    val isNow: Boolean,
    val safetyColor: Color
)

enum class WeatherSource { OPEN_METEO, METEOCIEL }
enum class AppTab { DASHBOARD, TOOLS, COMMUNITY, HELP, SETTINGS }
enum class DroneType { DJI_MINI, DJI_AIR, DJI_MAVIC, CUSTOM }
enum class UpdateCheckStatus { IDLE, CHECKING, UP_TO_DATE, UPDATE_FOUND, ERROR }

enum class DistanceUnit { METERS, FEET, KILOMETERS, MILES, NAUTICAL_MILES }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }
enum class WindUnit { KMH, KNOTS, MPH, MS }

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

data class WeatherUiState(
    val currentTab: AppTab = AppTab.DASHBOARD,
    val cityNameState: String = "",
    val mapCenter: GeoPoint = GeoPoint(49.2217, 3.9928),
    val detailedError: String? = null,
    val isLoading: Boolean = false,
    val lastUpdate: Long = 0,
    val sats: Int = 0,
    val satsLocked: Int = 0,
    val sunrise: Long = 0,
    val sunset: Long = 0,
    val currentBz: Double = 0.0,
    val solarWindSpeed: Double = 0.0,
    val solarWindDensity: Double = 0.0,
    
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    val selectedIndex: Int = 0,
    
    // Valeurs affichÃ©es (peuvent provenir du point selectionnÃ©)
    val windSpeed: String = "0",
    val wind80m: String = "0",
    val wind120m: String = "0",
    val wind180m: String = "0",
    val wind320m: String = "0",
    val wind500m: String = "0",
    val wind800m: String = "0",
    val wind1000m: String = "0",
    val wind1500m: String = "0",
    val windGust: String = "0",
    val windDeg: Int = 0,
    val clouds: Int = 0,
    val temperature: String = "20",
    val dewPoint: String = "15",
    val kpValue: Double? = 2.0,
    val visibility: String = ">10",
    val weatherIcon: String? = null,
    val precip: Int = 0,

    // Stats Satellites calculÃ©es
    val forecastSats: Int = 0,
    val forecastSatsLocked: Int = 0,

    val isSafe: Boolean = true,
    val statusTextResId: Int = R.string.status_ready,
    val statusColor: Color = GreenSafe,
    val selectedSource: WeatherSource = WeatherSource.OPEN_METEO,

    // ParamÃ¨tres utilisateur
    val language: String = "fr",
    val timeFormat24h: Boolean = true,
    val droneType: DroneType = DroneType.DJI_MINI,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val tempMinThreshold: Int = 0,
    val tempMaxThreshold: Int = 40,
    val windUnit: WindUnit = WindUnit.KMH,
    val windMaxThreshold: Int = 25,
    val altitudeUnit: DistanceUnit = DistanceUnit.METERS,
    val forecastAltitude: Int = 10,
    val visibilityUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val visibilityMinThreshold: Double = 5.0,
    val precipMaxThreshold: Int = 60,

    val useGps: Boolean = true,
    val useGlonass: Boolean = true,
    val useGalileo: Boolean = true,
    val useBeidou: Boolean = false,

    val alertRain: Boolean = true,
    val alertStorm: Boolean = true,
    val darkTheme: Boolean = true,
    val smoothAnim: Boolean = true,
    val alertWeather: Boolean = true,
    val morningForecast: Boolean = false,

    val updateAvailable: GitHubRelease? = null,
    val updateProgress: Float = 0f,
    val isDownloadingUpdate: Boolean = false,
    val lastUpdateCheckStatus: UpdateCheckStatus = UpdateCheckStatus.IDLE,

    val deviceAzimuth: Float = 0f,
    val satelliteForecast: List<SatelliteForecast> = emptyList(),
    val favorites: Set<String> = emptySet(),

    val checklist: List<ChecklistItem> = listOf(
        ChecklistItem(text = "VÃ©rifier les autorisations et l'espace aÃ©rien"),
        ChecklistItem(text = "Inspecter le drone (hÃ©lices, batterie et structure)"),
        ChecklistItem(text = "Planifier l'itinÃ©raire"),
        ChecklistItem(text = "VÃ©rifier les conditions mÃ©tÃ©orologiques"),
        ChecklistItem(text = "Calibrer la boussole et dÃ©finir le point de dÃ©part"),
        ChecklistItem(text = "S'assurer d'une zone de dÃ©collage sÃ»re")
    )
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var isCalculatingSatellites = false
    private var refreshJob: Job? = null

    init {
        loadCachedData()
        loadSatelliteForecast()
        viewModelScope.launch {
            val lastCity = settingsManager.getString("lastSearchedCity", "Bezannes")
            refresh(city = lastCity) 
        }
    }

    fun checkForUpdates(context: Context, manual: Boolean = false, currentVersionCode: Long) {
        viewModelScope.launch {
            if (manual) _uiState.update { it.copy(lastUpdateCheckStatus = UpdateCheckStatus.CHECKING) }
            val updateManager = UpdateManager(context)
            val release = updateManager.checkForUpdates(currentVersionCode)
            
            _uiState.update { state ->
                if (release != null) {
                    state.copy(
                        updateAvailable = release,
                        lastUpdateCheckStatus = UpdateCheckStatus.UPDATE_FOUND
                    )
                } else {
                    state.copy(
                        updateAvailable = null,
                        lastUpdateCheckStatus = if (manual) UpdateCheckStatus.UP_TO_DATE else UpdateCheckStatus.IDLE
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context, release: GitHubRelease) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingUpdate = true, updateProgress = 0f) }
            val updateManager = UpdateManager(context)
            try {
                updateManager.downloadAndInstall(release) { progress ->
                    _uiState.update { it.copy(updateProgress = progress) }
                }
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Update failed: ${e.message}")
                _uiState.update { it.copy(isDownloadingUpdate = false) }
            }
        }
    }

    fun downloadUpdate(context: Context) {
        val release = _uiState.value.updateAvailable ?: return
        downloadAndInstallUpdate(context, release)
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = null) }
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            try {
                val cached = repository.getWeatherData(city = "Cache", source = _uiState.value.selectedSource)
                processRefreshResult(cached, null, null)
                
                // Trigger recalculation from cache if we have coordinates
                if (cached.latitude != 0.0 && cached.longitude != 0.0) {
                    Log.d("WeatherViewModel", "Triggering initial sat calculation from cache")
                    triggerSatelliteRecalculation(cached.latitude, cached.longitude)
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadInitialState(): WeatherUiState {
        return WeatherUiState(
            language = settingsManager.getString("language", Locale.getDefault().language.let { if (it in listOf("fr", "en", "pl")) it else "en" }),
            timeFormat24h = settingsManager.getBoolean("timeFormat24h", true),
            droneType = try { DroneType.valueOf(settingsManager.getString("droneType", DroneType.DJI_MINI.name)) } catch (_: Exception) { DroneType.DJI_MINI },
            temperatureUnit = try { TemperatureUnit.valueOf(settingsManager.getString("temperatureUnit", TemperatureUnit.CELSIUS.name)) } catch (_: Exception) { TemperatureUnit.CELSIUS },
            tempMinThreshold = settingsManager.getInt("tempMin", 0),
            tempMaxThreshold = settingsManager.getInt("tempMax", 40),
            windUnit = try { WindUnit.valueOf(settingsManager.getString("windUnit", WindUnit.KMH.name)) } catch (_: Exception) { WindUnit.KMH },
            windMaxThreshold = settingsManager.getInt("windMax", 25),
            altitudeUnit = try { DistanceUnit.valueOf(settingsManager.getString("altitudeUnit", DistanceUnit.METERS.name)) } catch (_: Exception) { DistanceUnit.METERS },
            forecastAltitude = settingsManager.getInt("forecastAltitude", 10),
            visibilityMinThreshold = settingsManager.getDouble("visibilityMin", 5.0),
            visibilityUnit = try { DistanceUnit.valueOf(settingsManager.getString("visibilityUnit", DistanceUnit.KILOMETERS.name)) } catch (_: Exception) { DistanceUnit.KILOMETERS },
            precipMaxThreshold = settingsManager.getInt("precipMax", 60),
            useGps = settingsManager.getBoolean("useGps", true),
            useGlonass = settingsManager.getBoolean("useGlonass", true),
            useGalileo = settingsManager.getBoolean("useGalileo", true),
            useBeidou = settingsManager.getBoolean("useBeidou", false),
            alertRain = settingsManager.getBoolean("alertRain", true),
            alertStorm = settingsManager.getBoolean("alertStorm", true),
            darkTheme = settingsManager.getBoolean("darkTheme", true),
            smoothAnim = settingsManager.getBoolean("smoothAnim", true),
            alertWeather = settingsManager.getBoolean("alertWeather", true),
            morningForecast = settingsManager.getBoolean("morningForecast", false),
            selectedSource = try { WeatherSource.valueOf(settingsManager.getString("selectedSource", WeatherSource.OPEN_METEO.name)) } catch (_: Exception) { WeatherSource.OPEN_METEO },
            favorites = settingsManager.getStringSet("favorites", emptySet())
        )
    }

    private fun loadSatelliteForecast() {
        viewModelScope.launch {
            repository.getSatelliteForecastFlow().collect { forecasts ->
                Log.d("WeatherViewModel", "Received ${forecasts.size} satellite forecasts from DB")
                val now = System.currentTimeMillis() / 1000
                
                _uiState.update { currentState ->
                    val selectedHour = currentState.hourlyForecast.getOrNull(currentState.selectedIndex)
                    val correspondingSat = if (selectedHour != null) {
                        forecasts.minByOrNull { abs(it.timestamp - selectedHour.timestamp) }
                    } else {
                        forecasts.minByOrNull { abs(it.timestamp - now) }
                    }

                    currentState.copy(
                        satelliteForecast = forecasts.ifEmpty { currentState.satelliteForecast },
                        forecastSats = correspondingSat?.availableSatellites ?: currentState.forecastSats,
                        forecastSatsLocked = correspondingSat?.lockedSatellites ?: currentState.forecastSatsLocked
                    )
                }
            }
        }
    }

    fun refresh(city: String, lat: Double? = null, lon: Double? = null, force: Boolean = false, source: WeatherSource? = null) {
        refreshJob?.cancel()
        _uiState.update { it.copy(detailedError = null, isLoading = true) }
        
        val targetSource = source ?: _uiState.value.selectedSource
        Log.d("WeatherViewModel", "Refreshing UI [City: $city, Source: ${targetSource.name}, Force: $force]")
        
        refreshJob = viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    repository.getWeatherData(city, lat, lon, force, targetSource)
                }
                if (lat != null && lon != null) {
                    settingsManager.saveString("lastSearchedCity", city)
                }
                processRefreshResult(data, lat, lon)
            } catch (e: Exception) {
                handleRefreshError(e)
            }
        }
    }

    private suspend fun processRefreshResult(data: WeatherCache, lat: Double?, lon: Double?) {
        val forecast = withContext(Dispatchers.Default) {
            parseForecast(data.forecastJson)
        }
        
        val now = System.currentTimeMillis() / 1000
        val (isSafe, statusResId, statusColor) = calculateSafetyStatus(
            data.windSpeed, data.windGust, data.kpValue, data.currentBz, data.precip, data.temperature
        )

        // RESTORED: Trigger satellite recalculation when we have a position
        if (data.latitude != 0.0 && data.longitude != 0.0) {
            triggerSatelliteRecalculation(data.latitude, data.longitude)
        }

        _uiState.update { state ->
            val targetSource = try { WeatherSource.valueOf(data.weatherSource) } catch(_: Exception) { state.selectedSource }
            
            state.copy(
                selectedSource = targetSource,
                currentBz = data.currentBz,
                solarWindSpeed = data.solarWindSpeed,
                solarWindDensity = data.solarWindDensity,
                sunrise = data.sunrise,
                sunset = data.sunset,
                cityNameState = data.cityName,
                mapCenter = if (lat != null && lon != null) GeoPoint(lat, lon) else state.mapCenter,
                isLoading = false,
                lastUpdate = data.lastUpdated,
                hourlyForecast = forecast,
                selectedIndex = forecast.indexOfFirst { it.isNow }.takeIf { idx -> idx != -1 } 
                    ?: forecast.indexOfFirst { item -> item.timestamp >= now }.coerceAtLeast(0)
            ).let { updatedState ->
                val idx = updatedState.selectedIndex
                if (forecast.isNotEmpty() && idx in forecast.indices) {
                    val selected = forecast[idx]
                    val satForecast = updatedState.satelliteForecast.minByOrNull { 
                        abs(it.timestamp - selected.timestamp) 
                    }

                    val (safety, resId, color) = calculateSafetyStatus(
                        selected.wind, selected.gusts, selected.kp.toDoubleOrNull(), data.currentBz, selected.precip, selected.temp
                    )
                    updatedState.copy(
                        windSpeed = selected.wind,
                        wind80m = selected.wind80m,
                        wind120m = selected.wind120m,
                        wind180m = selected.wind180m,
                        wind320m = selected.wind320m,
                        wind500m = selected.wind500m,
                        wind800m = selected.wind800m,
                        wind1000m = selected.wind1000m,
                        wind1500m = selected.wind1500m,
                        windGust = selected.gusts,
                        windDeg = selected.windDeg,
                        clouds = selected.clouds,
                        temperature = selected.temp,
                        dewPoint = selected.dewPoint,
                        kpValue = selected.kp.toDoubleOrNull(),
                        visibility = selected.visibility,
                        weatherIcon = selected.weatherIcon,
                        precip = selected.precip,
                        forecastSats = satForecast?.availableSatellites ?: updatedState.forecastSats,
                        forecastSatsLocked = satForecast?.lockedSatellites ?: updatedState.forecastSatsLocked,
                        isSafe = safety,
                        statusTextResId = resId,
                        statusColor = color
                    )
                } else {
                    updatedState.copy(
                        windSpeed = data.windSpeed,
                        wind80m = data.wind80m,
                        wind120m = data.wind120m,
                        wind180m = data.wind180m,
                        wind320m = data.wind320m,
                        wind500m = data.wind500m,
                        wind800m = data.wind800m,
                        wind1000m = data.wind1000m,
                        wind1500m = data.wind1500m,
                        windGust = data.windGust,
                        windDeg = data.windDeg,
                        clouds = data.clouds,
                        temperature = data.temperature,
                        dewPoint = data.dewPoint,
                        kpValue = data.kpValue,
                        visibility = data.visibility,
                        weatherIcon = data.weatherIcon,
                        precip = data.precip,
                        isSafe = isSafe,
                        statusTextResId = statusResId,
                        statusColor = statusColor
                    )
                }
            }
        }
    }

    private fun handleRefreshError(e: Exception) {
        Log.e("WeatherViewModel", "Refresh Error: ${e.message}")
        _uiState.update { it.copy(
            detailedError = e.message ?: "Erreur inconnue",
            isLoading = false
        ) }
    }

    fun updateSatellites(total: Int, locked: Int) {
        _uiState.update { it.copy(sats = total, satsLocked = locked) }
    }

    fun updateSelectedHour(index: Int) {
        _uiState.update { state ->
            if (index in state.hourlyForecast.indices) {
                val item = state.hourlyForecast[index]
                val satForecast = state.satelliteForecast.minByOrNull { 
                    abs(it.timestamp - item.timestamp) 
                }

                val (safety, resId, color) = calculateSafetyStatus(
                    item.wind, item.gusts, item.kp.toDoubleOrNull(), state.currentBz, item.precip, item.temp
                )

                state.copy(
                    selectedIndex = index,
                    windSpeed = item.wind,
                    wind80m = item.wind80m,
                    wind120m = item.wind120m,
                    wind180m = item.wind180m,
                    wind320m = item.wind320m,
                    wind500m = item.wind500m,
                    wind800m = item.wind800m,
                    wind1000m = item.wind1000m,
                    wind1500m = item.wind1500m,
                    windGust = item.gusts,
                    windDeg = item.windDeg,
                    clouds = item.clouds,
                    temperature = item.temp,
                    dewPoint = item.dewPoint,
                    kpValue = item.kp.toDoubleOrNull(),
                    visibility = item.visibility,
                    weatherIcon = item.weatherIcon,
                    precip = item.precip,
                    forecastSats = satForecast?.availableSatellites ?: 0,
                    forecastSatsLocked = satForecast?.lockedSatellites ?: 0,
                    isSafe = safety,
                    statusTextResId = resId,
                    statusColor = color
                )
            } else state
        }
    }

    fun updateLocationAndData(context: Context, force: Boolean = false) {
        _uiState.update { it.copy(isLoading = true) }
        val locationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        processLocation(context, location, force)
                    } else {
                        refresh(city = _uiState.value.cityNameState.ifEmpty { "Bezannes" }, force = force)
                    }
                }
                .addOnFailureListener {
                    refresh(city = _uiState.value.cityNameState.ifEmpty { "Bezannes" }, force = force)
                }
        } catch (_: SecurityException) {
            refresh(city = _uiState.value.cityNameState.ifEmpty { "Bezannes" }, force = force)
        }
    }

    private fun processLocation(context: Context, location: Location, force: Boolean = false) {
        val lat = location.latitude
        val lon = location.longitude
        
        viewModelScope.launch {
            try {
                val geocoder = android.location.Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = withContext(Dispatchers.IO) { geocoder.getFromLocation(lat, lon, 1) }
                val cityName = addresses?.firstOrNull()?.locality ?: "Position actuelle"
                refresh(cityName, lat, lon, force)
            } catch (e: Exception) {
                refresh("Position actuelle", lat, lon, force)
            }
        }
    }

    fun updateDeviceAzimuth(azimuth: Float) {
        _uiState.update { it.copy(deviceAzimuth = azimuth) }
    }

    fun updateSource(source: WeatherSource) {
        Log.d("WeatherViewModel", "User requested source change: ${source.name}")
        settingsManager.saveString("selectedSource", source.name)
        _uiState.update { it.copy(selectedSource = source, isLoading = true) }
        
        val currentState = _uiState.value
        val lat = currentState.mapCenter.latitude
        val lon = currentState.mapCenter.longitude
        val city = currentState.cityNameState.takeIf { it.isNotBlank() } ?: "Position actuelle"
        
        refresh(city, lat, lon, force = true, source = source)
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun addChecklistItem(text: String) {
        _uiState.update { state ->
            val newItem = ChecklistItem(text = text)
            state.copy(checklist = state.checklist + newItem)
        }
    }

    fun removeChecklistItem(id: String) {
        _uiState.update { state ->
            state.copy(checklist = state.checklist.filter { it.id != id })
        }
    }

    fun toggleChecklistItem(id: String) {
        _uiState.update { state ->
            state.copy(
                checklist = state.checklist.map { item ->
                    if (item.id == id) item.copy(isChecked = !item.isChecked) else item
                }
            )
        }
    }

    fun toggleFavorite(city: String) {
        _uiState.update { state ->
            val newFavorites = if (state.favorites.contains(city)) {
                state.favorites - city
            } else {
                state.favorites + city
            }
            settingsManager.saveStringSet("favorites", newFavorites)
            state.copy(favorites = newFavorites)
        }
    }

    fun updateSettings(
        language: String? = null,
        timeFormat24h: Boolean? = null,
        droneType: DroneType? = null,
        temperatureUnit: TemperatureUnit? = null,
        tempMin: Int? = null,
        tempMax: Int? = null,
        windUnit: WindUnit? = null,
        windMax: Int? = null,
        altitudeUnit: DistanceUnit? = null,
        forecastAltitude: Int? = null,
        visibilityUnit: DistanceUnit? = null,
        visibilityMin: Double? = null,
        precipMax: Int? = null,
        useGps: Boolean? = null,
        useGlonass: Boolean? = null,
        useGalileo: Boolean? = null,
        useBeidou: Boolean? = null,
        alertRain: Boolean? = null,
        alertStorm: Boolean? = null,
        darkTheme: Boolean? = null,
        smoothAnim: Boolean? = null,
        alertWeather: Boolean? = null,
        morningForecast: Boolean? = null
    ) {
        _uiState.update { state ->
            val newState = state.copy(
                language = language ?: state.language,
                timeFormat24h = timeFormat24h ?: state.timeFormat24h,
                droneType = droneType ?: state.droneType,
                temperatureUnit = temperatureUnit ?: state.temperatureUnit,
                tempMinThreshold = tempMin ?: state.tempMinThreshold,
                tempMaxThreshold = tempMax ?: state.tempMaxThreshold,
                windUnit = windUnit ?: state.windUnit,
                windMaxThreshold = windMax ?: state.windMaxThreshold,
                altitudeUnit = altitudeUnit ?: state.altitudeUnit,
                forecastAltitude = forecastAltitude ?: state.forecastAltitude,
                visibilityUnit = visibilityUnit ?: state.visibilityUnit,
                visibilityMinThreshold = visibilityMin ?: state.visibilityMinThreshold,
                precipMaxThreshold = precipMax ?: state.precipMaxThreshold,
                useGps = useGps ?: state.useGps,
                useGlonass = useGlonass ?: state.useGlonass,
                useGalileo = useGalileo ?: state.useGalileo,
                useBeidou = useBeidou ?: state.useBeidou,
                alertRain = alertRain ?: state.alertRain,
                alertStorm = alertStorm ?: state.alertStorm,
                darkTheme = darkTheme ?: state.darkTheme,
                smoothAnim = smoothAnim ?: state.smoothAnim,
                alertWeather = alertWeather ?: state.alertWeather,
                morningForecast = morningForecast ?: state.morningForecast
            )
            
            // Persist all settings
            language?.let { settingsManager.saveString("language", it) }
            timeFormat24h?.let { settingsManager.saveBoolean("timeFormat24h", it) }
            droneType?.let { settingsManager.saveString("droneType", it.name) }
            temperatureUnit?.let { settingsManager.saveString("temperatureUnit", it.name) }
            tempMin?.let { settingsManager.saveInt("tempMin", it) }
            tempMax?.let { settingsManager.saveInt("tempMax", it) }
            windUnit?.let { settingsManager.saveString("windUnit", it.name) }
            windMax?.let { settingsManager.saveInt("windMax", it) }
            altitudeUnit?.let { settingsManager.saveString("altitudeUnit", it.name) }
            forecastAltitude?.let { settingsManager.saveInt("forecastAltitude", it) }
            visibilityUnit?.let { settingsManager.saveString("visibilityUnit", it.name) }
            visibilityMin?.let { settingsManager.saveDouble("visibilityMin", it) }
            precipMax?.let { settingsManager.saveInt("precipMax", it) }
            useGps?.let { settingsManager.saveBoolean("useGps", it) }
            useGlonass?.let { settingsManager.saveBoolean("useGlonass", it) }
            useGalileo?.let { settingsManager.saveBoolean("useGalileo", it) }
            useBeidou?.let { settingsManager.saveBoolean("useBeidou", it) }
            alertRain?.let { settingsManager.saveBoolean("alertRain", it) }
            alertStorm?.let { settingsManager.saveBoolean("alertStorm", it) }
            darkTheme?.let { settingsManager.saveBoolean("darkTheme", it) }
            smoothAnim?.let { settingsManager.saveBoolean("smoothAnim", it) }
            alertWeather?.let { settingsManager.saveBoolean("alertWeather", it) }
            morningForecast?.let { settingsManager.saveBoolean("morningForecast", it) }

            // Recalculate safety status with new thresholds
            val (isSafe, statusResId, statusColor) = calculateSafetyStatus(
                newState.windSpeed, newState.windGust, newState.kpValue, newState.currentBz, newState.precip, newState.temperature, newState
            )
            
            newState.copy(isSafe = isSafe, statusTextResId = statusResId, statusColor = statusColor)
        }
        
        val gnssChanged = useGps != null || useGlonass != null || useGalileo != null || useBeidou != null
        if (gnssChanged) {
            triggerSatelliteRecalculation(_uiState.value.mapCenter.latitude, _uiState.value.mapCenter.longitude)
        }
    }

    fun getCardColor(type: String, value: Any?, providedState: WeatherUiState? = null): Color {
        val state = providedState ?: _uiState.value
        val doubleValue = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        return when (type) {
            "Vent" -> when {
                doubleValue >= state.windMaxThreshold -> RedDanger
                doubleValue >= (state.windMaxThreshold * 0.6f) -> YellowWarn
                else -> GreenSafe
            }
            "Gusts" -> when {
                doubleValue >= (state.windMaxThreshold * 1.4f) -> RedDanger
                doubleValue >= state.windMaxThreshold -> YellowWarn
                else -> GreenSafe
            }
            "Temp" -> when {
                doubleValue > state.tempMaxThreshold || doubleValue < state.tempMinThreshold -> RedDanger
                doubleValue > state.tempMaxThreshold - 5 || doubleValue < state.tempMinThreshold + 5 -> YellowWarn
                else -> GreenSafe
            }
            "Kp" -> when {
                doubleValue >= 5.0 -> RedDanger
                doubleValue >= 3.0 -> YellowWarn
                else -> GreenSafe
            }
            "Cloud" -> when {
                doubleValue > 80 -> RedDanger
                doubleValue > 50 -> YellowWarn
                else -> GreenSafe
            }
            "Precip" -> when {
                doubleValue >= state.precipMaxThreshold -> RedDanger
                doubleValue >= (state.precipMaxThreshold * 0.5f) -> YellowWarn
                else -> GreenSafe
            }
            "Visibility" -> {
                val parsedValue = if (value is String && value.startsWith(">")) {
                    value.substring(1).toDoubleOrNull() ?: 10.0
                } else {
                    doubleValue
                }
                when {
                    parsedValue < state.visibilityMinThreshold * 0.4 -> RedDanger
                    parsedValue < state.visibilityMinThreshold -> YellowWarn
                    else -> GreenSafe
                }
            }
            "Sats" -> when {
                doubleValue < 8 -> RedDanger
                doubleValue < 12 -> YellowWarn
                else -> GreenSafe
            }
            else -> GreenSafe
        }
    }

    private fun calculateSafetyStatus(
        windSpeed: String,
        windGust: String,
        kpValue: Double?,
        currentBz: Double,
        precip: Int = 0,
        temperature: String = "0",
        providedState: WeatherUiState? = null
    ): Triple<Boolean, Int, Color> {
        val state = providedState ?: _uiState.value
        val currentKp = kpValue ?: 0.0
        val currentWind = windSpeed.toIntOrNull() ?: 0
        val currentGust = windGust.toIntOrNull() ?: 0
        val currentTemp = temperature.toIntOrNull() ?: 20
        
        // --- DANGER THRESHOLDS (RED) ---
        val isPrecipDanger = precip >= state.precipMaxThreshold
        val isWindDanger = currentWind >= state.windMaxThreshold || currentGust >= (state.windMaxThreshold * 1.4f).toInt()
        val isSolarDanger = currentKp >= 5.0 || currentBz <= -10.0
        val isTempDanger = currentTemp > state.tempMaxThreshold || currentTemp < state.tempMinThreshold
        
        // --- WARNING THRESHOLDS (YELLOW) ---
        val isWindWarn = currentWind >= (state.windMaxThreshold * 0.6f).toInt() || currentGust >= state.windMaxThreshold
        val isSolarWarn = currentKp >= 3.0 || currentBz <= -5.0
        
        val statusColor = when {
            isPrecipDanger || isWindDanger || isSolarDanger || isTempDanger -> RedDanger
            isWindWarn || isSolarWarn -> YellowWarn
            else -> GreenSafe
        }

        val isSafe = statusColor != RedDanger

        val statusResId = when {
            isPrecipDanger -> R.string.status_precip_danger
            isWindDanger -> R.string.status_winds_danger
            isTempDanger -> R.string.status_winds_danger
            currentKp >= 5.0 -> R.string.status_kp_danger
            currentKp >= 3.0 -> R.string.status_kp_warn
            currentBz <= -10.0 -> R.string.status_bz_danger
            currentBz <= -5.0 -> R.string.status_bz_warn
            else -> R.string.status_ready
        }
        
        return Triple(isSafe, statusResId, statusColor)
    }

    private fun triggerSatelliteRecalculation(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0 || isCalculatingSatellites) return

        isCalculatingSatellites = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val allTle = repository.getAllTleData()
                
                if (allTle.isEmpty()) {
                    Log.w("WeatherViewModel", "No TLE data found in DB, requesting immediate download")
                    // If no TLE, orbital math is impossible. Predictor will return dummy data.
                }

                val predictor = SatellitePredictor()
                val currentState = _uiState.value
                
                val filteredTle = allTle.filter { tle ->
                    val name = tle.satelliteName.uppercase()
                    when {
                        name.contains("GPS") -> currentState.useGps
                        name.contains("COSMOS") || name.contains("GLONASS") -> currentState.useGlonass
                        name.contains("GALILEO") -> currentState.useGalileo
                        name.contains("BEIDOU") || name.contains("BDS") -> currentState.useBeidou
                        else -> true
                    }
                }
                
                val satForecasts = predictor.generateMultiDayForecast(
                    lat, 
                    lon, 
                    currentState.kpValue?.toFloat() ?: 0f,
                    filteredTle,
                    days = 7,
                    currentState.useGps,
                    currentState.useGlonass,
                    currentState.useGalileo,
                    currentState.useBeidou
                )
                
                repository.updateSatelliteForecasts(satForecasts)
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Sat calculation error: ${e.message}")
            } finally {
                isCalculatingSatellites = false
            }
        }
    }

    private fun parseForecast(json: String?): List<HourlyForecast> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = Gson().fromJson(json, type)
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

            rawList.map { map ->
                val dt = (map["dt"] as? Double)?.toLong() ?: 0L
                val isNow = map["isNow"] as? Boolean ?: false
                val wind = map["wind"]?.toString() ?: "0"
                val temp = map["temp"]?.toString() ?: "0"
                val dew = map["dew"]?.toString() ?: "0"
                val wind80m = map["wind80m"]?.toString() ?: wind
                val wind120m = map["wind120m"]?.toString() ?: wind80m
                val wind180m = map["wind180m"]?.toString() ?: wind120m
                val wind320m = map["wind320m"]?.toString() ?: wind180m
                val wind500m = map["wind500m"]?.toString() ?: wind320m
                val wind800m = map["wind800m"]?.toString() ?: wind500m
                val wind1000m = map["wind1000m"]?.toString() ?: wind800m
                val wind1500m = map["wind1500m"]?.toString() ?: wind1000m

                val (_, _, color) = calculateSafetyStatus(
                    wind, map["gust"]?.toString() ?: "0", map["kp"]?.toString()?.toDoubleOrNull(), 0.0, (map["precip"] as? Double)?.toInt() ?: 0, temp
                )

                HourlyForecast(
                    timestamp = dt,
                    time = sdf.format(Date(dt * 1000)),
                    temp = temp,
                    dewPoint = dew,
                    wind = wind,
                    wind80m = wind80m,
                    wind120m = wind120m,
                    wind180m = wind180m,
                    wind320m = wind320m,
                    wind500m = wind500m,
                    wind800m = wind800m,
                    wind1000m = wind1000m,
                    wind1500m = wind1500m,
                    gusts = map["gust"]?.toString() ?: "0",
                    kp = map["kp"]?.toString() ?: "0",
                    iconUrl = "https://openweathermap.org/img/wn/${map["icon"] ?: "01d"}@2x.png",
                    windDeg = (map["windDeg"] as? Double)?.toInt() ?: 0,
                    clouds = (map["clouds"] as? Double)?.toInt() ?: 0,
                    visibility = map["visibility"]?.toString() ?: ">10",
                    precip = (map["precip"] as? Double)?.toInt() ?: 0,
                    weatherIcon = map["icon"]?.toString(),
                    isNow = isNow,
                    safetyColor = color
                )
            }
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Forecast parse error: ${e.message}")
            emptyList()
        }
    }


}

class WeatherViewModelFactory(
    private val repository: WeatherRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(repository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
