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

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import mick.droneweather.ui.theme.GreenSafe
import mick.droneweather.ui.theme.YellowWarn
import mick.droneweather.ui.theme.RedDanger
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.*

// --- ModÃƒÂ¨les d'ÃƒÂ©tat ---

data class HourlyForecast(
    val timestamp: Long,
    val time: String,
    val temp: String,
    val wind: String,
    val wind80m: String = "0",
    val wind120m: String = "0",
    val wind180m: String = "0",
    val wind320m: String = "0",
    val wind500m: String = "0",
    val wind800m: String = "0",
    val wind1000m: String = "0",
    val wind1500m: String = "0",
    val gusts: String,
    val kp: String,
    val iconUrl: String,
    val windDeg: Int = 0,
    val clouds: Int = 0,
    val visibility: String = ">10",
    val precip: Int = 0,
    val weatherIcon: String? = null,
    val isNow: Boolean = false,
    val safetyColor: Color = GreenSafe,
)

enum class WeatherSource {
    DEFAULT, OPEN_METEO, APPLE_WEATHER
}

enum class AppTab {
    DASHBOARD, TOOLS, COMMUNITY, HELP, SETTINGS
}

enum class DroneType {
    DJI_MINI, DJI_AIR, DJI_MAVIC, CUSTOM
}

enum class DistanceUnit { METERS, FEET, KILOMETERS }

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
    val lastUpdate: Long = 0L,
    val sats: Int = 12,
    val satsLocked: Int = 8,
    val sunrise: Long = 0L,
    val sunset: Long = 0L,
    val currentBz: Double = 0.0,
    val solarWindSpeed: Double = 0.0,
    val solarWindDensity: Double = 0.0,
    
    val hourlyForecast: List<HourlyForecast> = emptyList(),
    val selectedIndex: Int = 0,
    
    // Derived values for the currently selected hour
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
    val temperature: String = "0",
    val kpValue: Double? = null,
    val visibility: String = ">10",
    val weatherIcon: String? = null,
    val precip: Int = 0,
    
    // Predicted satellites for the selected hour
    val forecastSats: Int = 0,
    val forecastSatsLocked: Int = 0,
    
    val isSafe: Boolean = true,
    val statusTextResId: Int = R.string.status_ready,
    val statusColor: Color = GreenSafe,
    val selectedSource: WeatherSource = WeatherSource.DEFAULT,

    // App Settings
    val language: String = Locale.getDefault().language.let { lang ->
        if (lang in listOf("fr", "en", "pl")) lang else "en"
    },
    val timeFormat24h: Boolean = true, // Will be updated in MainActivity
    val droneType: DroneType = DroneType.DJI_MINI,
    val tempMinThreshold: Int = 0,
    val tempMaxThreshold: Int = 40,
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
    
    val deviceAzimuth: Float = 0f,
    val satelliteForecast: List<SatelliteForecast> = emptyList(),

    val checklist: List<ChecklistItem> = listOf(
        ChecklistItem(text = "VÃƒÂ©rifier les autorisations et l'espace aÃƒÂ©rien"),
        ChecklistItem(text = "Inspecter le drone (hÃƒÂ©lices, batterie et structure)"),
        ChecklistItem(text = "Planifier l'itinÃƒÂ©raire"),
        ChecklistItem(text = "VÃƒÂ©rifier les conditions mÃƒÂ©tÃƒÂ©orologiques"),
        ChecklistItem(text = "Calibrer la boussole et dÃƒÂ©finir le point de dÃƒÂ©part"),
        ChecklistItem(text = "S'assurer d'une zone de dÃƒÂ©collage sÃƒÂ»re")
    )
)

// --- ViewModel ---

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    
    private var isCalculatingSatellites = false

    init {
        loadCachedData()
        loadSatelliteForecast()
    }

    private fun loadInitialState(): WeatherUiState {
        return WeatherUiState(
            language = settingsManager.getString("language", Locale.getDefault().language.let { if (it in listOf("fr", "en", "pl")) it else "en" }),
            timeFormat24h = settingsManager.getBoolean("timeFormat24h", true),
            droneType = DroneType.valueOf(settingsManager.getString("droneType", DroneType.DJI_MINI.name)),
            tempMinThreshold = settingsManager.getInt("tempMin", 0),
            tempMaxThreshold = settingsManager.getInt("tempMax", 40),
            windMaxThreshold = settingsManager.getInt("windMax", 25),
            altitudeUnit = DistanceUnit.valueOf(settingsManager.getString("altitudeUnit", DistanceUnit.METERS.name)),
            forecastAltitude = settingsManager.getInt("forecastAltitude", 10),
            visibilityMinThreshold = settingsManager.getDouble("visibilityMin", 5.0),
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
            selectedSource = WeatherSource.valueOf(settingsManager.getString("selectedSource", WeatherSource.DEFAULT.name))
        )
    }

    private fun loadSatelliteForecast() {
        viewModelScope.launch {
            repository.getSatelliteForecastFlow().collect { forecasts ->
                val now = System.currentTimeMillis() / 1000
                
                _uiState.update { currentState ->
                    val selectedHour = currentState.hourlyForecast.getOrNull(currentState.selectedIndex)
                    val correspondingSat = if (selectedHour != null) {
                        forecasts.minByOrNull { kotlin.math.abs(it.timestamp - selectedHour.timestamp) }
                    } else {
                        forecasts.minByOrNull { kotlin.math.abs(it.timestamp - now) }
                    }

                    currentState.copy(
                        satelliteForecast = forecasts,
                        forecastSats = correspondingSat?.availableSatellites ?: currentState.forecastSats,
                        forecastSatsLocked = correspondingSat?.lockedSatellites ?: currentState.forecastSatsLocked
                    )
                }
            }
        }
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            refresh(city = "Bezannes") 
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
                val predictor = SatellitePredictor()
                val allTle = repository.getAllTleData()
                
                // Get fresh state inside the calculation block to avoid race conditions
                val currentState = _uiState.value
                
                // Filter TLEs based on user settings
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
                
                // We ONLY update the repository. 
                // The Flow in loadSatelliteForecast will handle the UI update safely.
                repository.updateSatelliteForecasts(satForecasts)
                
            } catch (_: Exception) {
            } finally {
                isCalculatingSatellites = false
            }
        }
    }

    fun refresh(city: String, lat: Double? = null, lon: Double? = null, force: Boolean = false) {
        _uiState.update { it.copy(detailedError = null, isLoading = true) }
        
        viewModelScope.launch {
            try {
                val data = repository.getWeatherData(city, lat, lon, force)
                
                // Update map center and basic data first
                _uiState.update { it.copy(
                    cityNameState = data.cityName,
                    mapCenter = if (lat != null && lon != null) GeoPoint(lat, lon) else it.mapCenter,
                    kpValue = data.kpValue
                ) }

                // Generate Satellite Forecast if we have location
                if (data.latitude != 0.0 && data.longitude != 0.0) {
                    triggerSatelliteRecalculation(data.latitude, data.longitude)
                }

                val (isSafe, statusResId, statusColor) = calculateSafetyStatus(
                    data.windSpeed, data.windGust, data.kpValue, data.currentBz, data.precip, data.temperature
                )

                val forecast = parseForecast(data.forecastJson)
                val now = System.currentTimeMillis() / 1000

                _uiState.update { state ->
                    state.copy(
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
                        // Initialize selected hour data
                        val idx = updatedState.selectedIndex
                        if (forecast.isNotEmpty() && idx in forecast.indices) {
                            val selected = forecast[idx]
                            
                            // Find corresponding satellite forecast from existing state data
                            val satForecast = updatedState.satelliteForecast.minByOrNull { 
                                kotlin.math.abs(it.timestamp - selected.timestamp) 
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
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Erreur lors du rafraÃƒÂ®chissement: ${e.message}")
                _uiState.update { 
                    it.copy(
                        detailedError = e.message ?: "Erreur de connexion",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateSatellites(visible: Int, locked: Int) {
        val state = _uiState.value
        val activeConstellations = (if(state.useGps) 1 else 0) + (if(state.useGlonass) 1 else 0) + (if(state.useGalileo) 1 else 0) + (if(state.useBeidou) 1 else 0)
        
        // Dynamic cap for live data to match Predictor Realism:
        val maxVis = when(activeConstellations) {
            4 -> 38
            3 -> 28
            2 -> 18
            else -> 12
        }
        val maxLock = when(activeConstellations) {
            4 -> 30
            3 -> 24
            2 -> 15
            else -> 8
        }

        _uiState.update { it.copy(
            sats = visible.coerceAtMost(maxVis), 
            satsLocked = locked.coerceAtMost(maxLock)
        ) }
    }

    fun updateDeviceAzimuth(azimuth: Float) {
        _uiState.update { it.copy(deviceAzimuth = azimuth) }
    }

    fun updateSource(source: WeatherSource) {
        settingsManager.saveString("selectedSource", source.name)
        _uiState.update { it.copy(selectedSource = source) }
        // Trigger a refresh with the new source
        val currentState = _uiState.value
        refresh(currentState.cityNameState, currentState.mapCenter.latitude, currentState.mapCenter.longitude, force = true)
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
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

    fun addChecklistItem(text: String) {
        if (text.isBlank()) return
        _uiState.update { state ->
            state.copy(
                checklist = state.checklist + ChecklistItem(text = text)
            )
        }
    }

    fun removeChecklistItem(id: String) {
        _uiState.update { state ->
            state.copy(
                checklist = state.checklist.filter { it.id != id }
            )
        }
    }

    fun updateSettings(
        language: String? = null,
        timeFormat24h: Boolean? = null,
        droneType: DroneType? = null,
        tempMin: Int? = null,
        tempMax: Int? = null,
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
        val gnssChanged = useGps != null || useGlonass != null || useGalileo != null || useBeidou != null
        
        language?.let { settingsManager.saveString("language", it) }
        timeFormat24h?.let { settingsManager.saveBoolean("timeFormat24h", it) }
        droneType?.let { settingsManager.saveString("droneType", it.name) }
        tempMin?.let { settingsManager.saveInt("tempMin", it) }
        tempMax?.let { settingsManager.saveInt("tempMax", it) }
        windMax?.let { settingsManager.saveInt("windMax", it) }
        altitudeUnit?.let { settingsManager.saveString("altitudeUnit", it.name) }
        forecastAltitude?.let { settingsManager.saveInt("forecastAltitude", it) }
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

        _uiState.update { 
            val updatedBase = it.copy(
                language = language ?: it.language,
                timeFormat24h = timeFormat24h ?: it.timeFormat24h,
                droneType = droneType ?: it.droneType,
                tempMinThreshold = tempMin ?: it.tempMinThreshold,
                tempMaxThreshold = tempMax ?: it.tempMaxThreshold,
                windMaxThreshold = windMax ?: it.windMaxThreshold,
                altitudeUnit = altitudeUnit ?: it.altitudeUnit,
                forecastAltitude = forecastAltitude ?: it.forecastAltitude,
                visibilityUnit = visibilityUnit ?: it.visibilityUnit,
                visibilityMinThreshold = visibilityMin ?: it.visibilityMinThreshold,
                precipMaxThreshold = precipMax ?: it.precipMaxThreshold,
                useGps = useGps ?: it.useGps,
                useGlonass = useGlonass ?: it.useGlonass,
                useGalileo = useGalileo ?: it.useGalileo,
                useBeidou = useBeidou ?: it.useBeidou,
                alertRain = alertRain ?: it.alertRain,
                alertStorm = alertStorm ?: it.alertStorm,
                darkTheme = darkTheme ?: it.darkTheme,
                smoothAnim = smoothAnim ?: it.smoothAnim,
                alertWeather = alertWeather ?: it.alertWeather,
                morningForecast = morningForecast ?: it.morningForecast
            )

            // 1. Re-calculate colors for the entire hourly forecast list
            val updatedForecast = updatedBase.hourlyForecast.map { hour ->
                val (_, _, newColor) = calculateSafetyStatus(
                    hour.wind, hour.gusts, hour.kp.toDoubleOrNull(), updatedBase.currentBz, hour.precip, hour.temp, updatedBase
                )
                hour.copy(safetyColor = newColor)
            }

            // 2. Re-calculate overall safety status for the currently selected hour
            val (safety, resId, color) = calculateSafetyStatus(
                updatedBase.windSpeed, 
                updatedBase.windGust, 
                updatedBase.kpValue, 
                updatedBase.currentBz, 
                updatedBase.precip, 
                updatedBase.temperature,
                updatedBase
            )

            updatedBase.copy(
                hourlyForecast = updatedForecast,
                isSafe = safety,
                statusTextResId = resId,
                statusColor = color
            )
        }

        if (gnssChanged) {
            val state = _uiState.value
            triggerSatelliteRecalculation(state.mapCenter.latitude, state.mapCenter.longitude)
        }
    }

    fun selectForecastIndex(index: Int) {
        _uiState.update { state ->
            if (index !in state.hourlyForecast.indices) return@update state
            val item = state.hourlyForecast[index]
            
            // Find corresponding satellite forecast
            val satForecast = state.satelliteForecast.minByOrNull { 
                kotlin.math.abs(it.timestamp - item.timestamp) 
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
                wind500m = item.wind500m,
                windGust = item.gusts,
                windDeg = item.windDeg,
                clouds = item.clouds,
                temperature = item.temp,
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
        }
    }

    @SuppressLint("MissingPermission")
    fun updateLocationAndData(context: Context, force: Boolean = false) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        _uiState.update { it.copy(isLoading = true) }

        // Strategy for Indoor/Difficult conditions:
        val currentState = _uiState.value

        fusedClient.lastLocation
            .addOnSuccessListener { lastLocation ->
                if (lastLocation != null && !force) {
                    processLocation(context, lastLocation)
                } else {
                    // Try to get fresh location, but with a timeout or fallback if indoors
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                processLocation(context, location)
                            } else {
                                handleLocationFallback(currentState, force)
                            }
                        }
                        .addOnFailureListener {
                            handleLocationFallback(currentState, force)
                        }
                }
            }
            .addOnFailureListener {
                handleLocationFallback(currentState, force)
            }
    }

    private fun handleLocationFallback(state: WeatherUiState, force: Boolean) {
        val lat = state.mapCenter.latitude
        val lon = state.mapCenter.longitude
        val city = state.cityNameState.ifEmpty { "Bezannes" }
        
        if (lat != 0.0 && lon != 0.0) {
            refresh(city, lat, lon, force = force)
        } else {
            refresh("Bezannes", 49.2217, 3.9928, force = force)
        }
    }

    private fun processLocation(context: Context, location: android.location.Location) {
        val lat = location.latitude
        val lon = location.longitude
        
        viewModelScope.launch {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val cityName = withContext(Dispatchers.IO) {
                try {
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    addresses?.firstOrNull()?.locality ?: "Inconnue"
                } catch (_: Exception) {
                    "Erreur Ville"
                }
            }
            refresh(cityName, lat, lon)
        }
    }

    private fun parseForecast(json: String?): List<HourlyForecast> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = Gson().fromJson(json, type)
            
            val timePattern = if (_uiState.value.timeFormat24h) "HH:mm" else "hh:mm a"
            val sdf = java.text.SimpleDateFormat(timePattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }

            val realForecast = rawList.asSequence().map { map ->
                val dt = (map["dt"] as? Double)?.toLong() ?: 0L
                val isNow = map["isNow"] as? Boolean ?: false
                val wind = map["wind"]?.toString() ?: "0"
                val wind80m = map["wind80m"]?.toString() ?: wind
                val wind120m = map["wind120m"]?.toString() ?: wind80m
                val wind180m = map["wind180m"]?.toString() ?: wind120m
                val wind320m = map["wind320m"]?.toString() ?: wind180m
                val wind500m = map["wind500m"]?.toString() ?: wind320m
                val wind800m = map["wind800m"]?.toString() ?: wind500m
                val wind1000m = map["wind1000m"]?.toString() ?: wind800m
                val wind1500m = map["wind1500m"]?.toString() ?: wind1000m
                val gusts = map["gust"]?.toString() ?: "0"
                val kp = map["kp"]?.toString() ?: "0"
                
                // Calculate safety color for this specific hour
                val (_, _, sColor) = calculateSafetyStatus(
                    wind, gusts, kp.toDoubleOrNull(), _uiState.value.currentBz, (map["precip"] as? Double)?.toInt() ?: 0, map["temp"]?.toString() ?: "0"
                )

                HourlyForecast(
                    timestamp = dt,
                    time = sdf.format(Date(dt * 1000)),
                    temp = map["temp"]?.toString() ?: "0",
                    wind = wind,
                    wind80m = wind80m,
                    wind120m = wind120m,
                    wind180m = wind180m,
                    wind320m = wind320m,
                    wind500m = wind500m,
                    wind800m = wind800m,
                    wind1000m = wind1000m,
                    wind1500m = wind1500m,
                    gusts = gusts,
                    kp = kp,
                    iconUrl = "https://openweathermap.org/img/wn/${map["icon"]}@2x.png",
                    windDeg = (map["windDeg"] as? Double)?.toInt() ?: 0,
                    clouds = (map["clouds"] as? Double)?.toInt() ?: 0,
                    visibility = map["visibility"]?.toString() ?: ">10",
                    precip = (map["precip"] as? Double)?.toInt() ?: 0,
                    weatherIcon = map["icon"]?.toString(),
                    isNow = isNow,
                    safetyColor = sColor
                )
            }.toMutableList()

            realForecast
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Error parsing forecast: ${e.message}")
            emptyList()
        }
    }
}

class WeatherViewModelFactory(
    private val repository: WeatherRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            return WeatherViewModel(repository, settingsManager) as T
        }
        throw IllegalArgumentException("ViewModel inconnu")
    }
}

