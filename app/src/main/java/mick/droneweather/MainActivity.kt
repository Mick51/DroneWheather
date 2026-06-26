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

import android.Manifest
import android.app.LocaleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration as AndroidConfig
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mick.droneweather.ui.theme.DroneWeatherTheme
import mick.droneweather.ui.theme.AppBackground
import mick.droneweather.ui.theme.NeonGreen
import mick.droneweather.ui.theme.GreenSafe
import mick.droneweather.ui.theme.YellowWarn
import mick.droneweather.ui.theme.RedDanger
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.core.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch(Dispatchers.IO) {
            OrekitInitializer.init(this@MainActivity)
        }
        
        val db = AppDatabase.getDatabase(this)
        val weatherApi = RetrofitInstance.api
        val kpApi = RetrofitInstance.kpApi
        val gfzApi = RetrofitInstance.gfzApi
        val repository = WeatherRepository(weatherApi, kpApi, gfzApi, db.weatherDao())
        
        viewModel = ViewModelProvider(this, WeatherViewModelFactory(repository))[WeatherViewModel::class.java]

        scheduleWorkers()

        setContent {
            DroneWeatherTheme {
                SkyGoDashboard(viewModel)
            }
        }
    }

    private fun scheduleWorkers() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Periodic work
        val tleRequest = PeriodicWorkRequestBuilder<TleDownloadWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        val forecastRequest = PeriodicWorkRequestBuilder<SatelliteForecastWorker>(6, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TleDownload",
            ExistingPeriodicWorkPolicy.KEEP,
            tleRequest
        )

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SatelliteForecast",
            ExistingPeriodicWorkPolicy.KEEP,
            forecastRequest
        )

        // One-time work to ensure data is available immediately on first run
        val oneTimeTleRequest = OneTimeWorkRequestBuilder<TleDownloadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "TleDownloadInitial",
            ExistingWorkPolicy.KEEP,
            oneTimeTleRequest
        )
    }
}

@Composable
fun WindDirectionIndicator(
    degrees: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp
) {
    Box(
        modifier = modifier.size(size + 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color.LightGray.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
        }

        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = "Direction",
            tint = Color.Black,
            modifier = Modifier
                .rotate(degrees.toFloat())
                .size(size)
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    modifier: Modifier = Modifier,
    value: String = "",
    backgroundColor: Color = GreenSafe,
    onClick: () -> Unit = {},
    isLandscape: Boolean = false,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier.padding(1.dp).fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = if (isLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = if (isLandscape) 10.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isLandscape) Spacer(modifier = Modifier.height(2.dp))
            if (content != null) {
                content()
            } else {
                val parts = value.split(" ")
                if (parts.size > 1) {
                    Text(
                        text = parts[0],
                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = parts.subList(1, parts.size).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                } else {
                    Text(
                        text = value,
                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun SunTimesCard(sunrise: String, sunset: String, modifier: Modifier = Modifier, isLandscape: Boolean = false, onClick: () -> Unit = {}) {
    MetricCard(
        title = stringResource(R.string.metric_sunrise),
        modifier = modifier,
        isLandscape = isLandscape,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.WbTwilight, contentDescription = null, modifier = Modifier.size(if (isLandscape) 12.dp else 16.dp), tint = Color.Black)
                Text(text = sunrise, style = if (isLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(if (isLandscape) 12.dp else 16.dp), tint = Color.Black)
                Text(text = sunset, style = if (isLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
fun InteractiveForecastSelector(
    uiState: WeatherUiState,
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    if (uiState.hourlyForecast.isEmpty()) return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == AndroidConfig.ORIENTATION_LANDSCAPE
    val locale = configuration.locales[0]

    // 1. Group points by day and prepare colors for the heatmap gradient
    val daysWithHours = remember(uiState.hourlyForecast) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", locale)
        uiState.hourlyForecast.groupBy { 
            sdf.format(Date(it.timestamp * 1000)) 
        }
    }

    val daysData = remember(daysWithHours, uiState.language) {
        val sdfDay = SimpleDateFormat("EEE", locale)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", locale).format(Date())

        daysWithHours.map { (dateString, points) ->
            val dayName = if (dateString == todayStr) "TODAY" else {
                sdfDay.format(Date(points.first().timestamp * 1000)).replaceFirstChar { it.uppercase() }
            }
            val dayColors = listOf(0, 8, 16, points.size - 1).map { i ->
                points[i.coerceIn(points.indices)].safetyColor
            }
            object {
                val date = dateString
                val name = dayName
                val colors = dayColors
            }
        }
    }

    val selectedDayString = remember(uiState.hourlyForecast, uiState.selectedIndex) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", locale)
        sdfDate.format(Date(uiState.hourlyForecast[uiState.selectedIndex].timestamp * 1000))
    }

    val dayHours = remember(daysWithHours, selectedDayString) {
        daysWithHours[selectedDayString] ?: emptyList()
    }
    
    val hourIndexInDay = remember(dayHours, uiState.selectedIndex) {
        dayHours.indexOf(uiState.hourlyForecast[uiState.selectedIndex]).coerceAtLeast(0)
    }

    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(hourIndexInDay.toFloat()) }
    
    LaunchedEffect(hourIndexInDay) {
        if (!isDragging) {
            sliderValue = hourIndexInDay.toFloat()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) sliderValue / (dayHours.size - 1).coerceAtLeast(1) 
                     else hourIndexInDay.toFloat() / (dayHours.size - 1).coerceAtLeast(1),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SliderAnimation"
    )

    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        // --- 1. Hour Selector Slider (Top) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF00B0FF).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (dayHours[hourIndexInDay].isNow) stringResource(R.string.label_now_short) else dayHours[hourIndexInDay].time,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!dayHours[hourIndexInDay].isNow) {
                Text(
                    text = stringResource(R.string.return_to_now),
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable {
                        val nowIdx = uiState.hourlyForecast.indexOfFirst { it.isNow }
                        if (nowIdx != -1) viewModel.selectForecastIndex(nowIdx)
                    }
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .pointerInput(dayHours) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val widthPx = size.width - 48.dp.toPx()
                            val touchX = (change.position.x - 24.dp.toPx()).coerceIn(0f, widthPx)
                            val progress = touchX / widthPx
                            val newValue = progress * (dayHours.size - 1)
                            
                            val oldStep = sliderValue.roundToInt()
                            val newStep = newValue.roundToInt().coerceIn(0, dayHours.size - 1)
                            
                            if (oldStep != newStep) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val globalIdx = uiState.hourlyForecast.indexOf(dayHours[newStep])
                                if (globalIdx != -1) {
                                    viewModel.selectForecastIndex(globalIdx)
                                }
                            }
                            sliderValue = newValue
                        }
                    )
                }
                .pointerInput(dayHours) {
                    detectTapGestures { offset ->
                        val widthPx = size.width - 48.dp.toPx()
                        val touchX = (offset.x - 24.dp.toPx()).coerceIn(0f, widthPx)
                        val progress = touchX / widthPx
                        val newValue = progress * (dayHours.size - 1)
                        
                        val newStep = newValue.roundToInt().coerceIn(0, dayHours.size - 1)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        val globalIdx = uiState.hourlyForecast.indexOf(dayHours[newStep])
                        if (globalIdx != -1) {
                            viewModel.selectForecastIndex(globalIdx)
                        }
                        sliderValue = newValue
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val width = maxWidth - 48.dp
            val xOffset = (width.value * animatedProgress).dp

            val barBrush = remember(dayHours) {
                if (dayHours.size > 1) {
                    val samples = listOf(0, 4, 8, 12, 16, 20, dayHours.size - 1)
                    val sampledColors = samples.map { i -> 
                        dayHours[i.coerceIn(dayHours.indices)].safetyColor 
                    }
                    Brush.horizontalGradient(sampledColors)
                } else {
                    Brush.horizontalGradient(listOf(GreenSafe, GreenSafe))
                }
            }
            
            // Barre de progression
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .offset(y = 12.dp)
                    .background(brush = barBrush, shape = CircleShape)
            )

            Box(modifier = Modifier.width(width).fillMaxHeight()) {
                // Petit point sur la barre
                Box(
                    modifier = Modifier
                        .offset(x = xOffset - 4.dp, y = 39.dp)
                        .size(8.dp)
                        .background(Color(0xFF00B0FF), CircleShape)
                )

                // Bulle réduite à 30dp
                Surface(
                    color = Color(0xFF00B0FF),
                    shape = CircleShape,
                    modifier = Modifier
                        .offset(x = xOffset - 15.dp, y = 4.dp)
                        .size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val currentStep = sliderValue.roundToInt().coerceIn(0, dayHours.size - 1)
                        // Heure uniquement, adaptée au format
                        val displayTime = if (uiState.timeFormat24h) {
                            dayHours[currentStep].time.split(":")[0] + "H"
                        } else {
                            dayHours[currentStep].time // "02:00 PM"
                        }
                        Text(
                            text = displayTime,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!isLandscape) Spacer(modifier = Modifier.height(4.dp))

        // --- 2. Day Selector Row (Heatmap Cards) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (isLandscape) 0.dp else 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            daysData.take(7).forEach { day ->
                val isSelected = day.date == selectedDayString
                val displayName = if (day.name == "TODAY") stringResource(R.string.today).take(4) else day.name.take(3)
                
                if (!isLandscape) {
                    // Portrait: HEATMAP SQUARE CARDS
                    Card(
                        modifier = Modifier
                            .size(width = 46.dp, height = 38.dp)
                            .clickable {
                                val globalIdx = uiState.hourlyForecast.indexOfFirst { 
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp * 1000)) == day.date 
                                }
                                if (globalIdx != -1) viewModel.selectForecastIndex(globalIdx)
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(Color(0xFF00B0FF), Color(0xFF0081CB)))
                                    else Brush.horizontalGradient(day.colors)
                                ),
                        contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName,
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Landscape (DJI RC2): Capsules horizontales colorées et compactes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(horizontal = 2.dp)
                            .background(
                                if (isSelected) androidx.compose.ui.graphics.SolidColor(Color(0xFF00B0FF))
                                else Brush.horizontalGradient(day.colors),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                val globalIdx = uiState.hourlyForecast.indexOfFirst { 
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp * 1000)) == day.date 
                                }
                                if (globalIdx != -1) viewModel.selectForecastIndex(globalIdx)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName,
                            color = if (isSelected) Color.White else Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(uiState: WeatherUiState, viewModel: WeatherViewModel, context: android.content.Context) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == AndroidConfig.ORIENTATION_LANDSCAPE
    
    var detailTitle by remember { mutableStateOf("") }
    var detailDesc by remember { mutableStateOf("") }
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text(detailTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (detailTitle == stringResource(R.string.metric_wind_air)) {
                        Text(stringResource(R.string.wind_by_altitude), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val altitudes = listOf(
                            stringResource(R.string.altitude_ground) to uiState.windSpeed,
                            "80m" to uiState.wind80m,
                            "120m" to uiState.wind120m,
                            "180m" to uiState.wind180m,
                            "320m" to uiState.wind320m,
                            "500m" to uiState.wind500m
                        )
                        
                        altitudes.forEach { (alt, speed) ->
                            val speedVal = speed.toIntOrNull() ?: 0
                            val color = when {
                                speedVal >= 25 -> RedDanger
                                speedVal >= 15 -> YellowWarn
                                else -> GreenSafe
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(alt, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 70.dp, height = 4.dp)
                                            .background(Color.DarkGray, RoundedCornerShape(2.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction = (speedVal.toFloat() / 40f).coerceIn(0f, 1f))
                                                .background(color, RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("$speed km/h", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(detailDesc, style = MaterialTheme.typography.bodySmall, lineHeight = 14.sp)
                    } else {
                        Text(detailDesc)
                        if (detailTitle == stringResource(R.string.metric_sats_vis) || detailTitle == stringResource(R.string.metric_sats_lock)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val url = "https://oktofly.com/"
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(intent)
                                    showDetail = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.btn_oktofly), color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetail = false }) {
                    Text(stringResource(R.string.detail_close))
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color(0xFF1E2330),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 16.dp else 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val headerWeight = if (isLandscape) 0.8f else 1.5f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(headerWeight)
                .padding(vertical = if (isLandscape) 0.dp else 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            var showSearchDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = R.drawable.app_logo,
                        contentDescription = null,
                        modifier = Modifier.size(if (isLandscape) 36.dp else 44.dp).padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = uiState.cityNameState.ifEmpty { stringResource(R.string.location_loading) },
                            color = Color.White,
                            style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.uav_forecast_mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                Row {
                    IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_content_description), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = {
                        viewModel.updateLocationAndData(context, force = true)
                    }, modifier = Modifier.size(36.dp)) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_content_description), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            if (showSearchDialog) {
                var searchQuery by remember { mutableStateOf("") }
                androidx.compose.ui.window.Dialog(onDismissRequest = { showSearchDialog = false }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = AppBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.search_dialog_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.refresh(searchQuery, force = true)
                                        showSearchDialog = false
                                    }
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    cursorColor = NeonGreen
                                )
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showSearchDialog = false }) {
                                    Text(stringResource(R.string.btn_cancel), color = Color.Gray)
                                }
                                TextButton(onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.refresh(searchQuery, force = true)
                                        showSearchDialog = false
                                    }
                                }) {
                                    Text(stringResource(R.string.btn_search), color = NeonGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        val statusWeight = if (isLandscape) 0.6f else 1.0f
        Card(
            modifier = Modifier.fillMaxWidth().weight(statusWeight).padding(vertical = 1.dp),
            colors = CardDefaults.cardColors(containerColor = uiState.statusColor),
            shape = RoundedCornerShape(8.dp),
            onClick = {
                detailTitle = context.getString(R.string.detail_title)
                detailDesc = context.getString(if (uiState.isSafe) R.string.status_ready else uiState.statusTextResId)
                showDetail = true
            }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(uiState.statusTextResId),
                    color = Color.Black,
                    fontSize = if (isLandscape) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        val metricsWeight = if (isLandscape) 4.0f else 5.5f
        Column(modifier = Modifier.weight(metricsWeight).padding(vertical = if (isLandscape) 1.dp else 4.dp)) {
            if (isLandscape) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MetricCard(title = stringResource(R.string.metric_weather), modifier = Modifier.weight(1f), isLandscape = true, onClick = { detailTitle = context.getString(R.string.metric_weather); detailDesc = context.getString(R.string.desc_clouds); showDetail = true }) {
                        AsyncImage(model = "https://openweathermap.org/img/wn/${uiState.weatherIcon ?: "01d"}@2x.png", contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    MetricCard(title = stringResource(R.string.metric_wind_air), value = "${uiState.windSpeed} km/h", backgroundColor = viewModel.getCardColor("Vent", uiState.windSpeed), isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_wind_air); detailDesc = context.getString(R.string.desc_wind); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_gusts), value = "${uiState.windGust} km/h", backgroundColor = viewModel.getCardColor("Gusts", uiState.windGust), isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_wind_air); detailDesc = context.getString(R.string.desc_wind); showDetail = true })
                    Card(modifier = Modifier.weight(1f).padding(1.dp).fillMaxSize(), colors = CardDefaults.cardColors(containerColor = GreenSafe), shape = RoundedCornerShape(8.dp), onClick = { detailTitle = context.getString(R.string.metric_wind_dir); detailDesc = context.getString(R.string.desc_wind_dir); showDetail = true }) {
                        Column(modifier = Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.metric_wind_dir), style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f), maxLines = 1)
                            WindDirectionIndicator(degrees = uiState.windDeg, size = 18.dp)
                            Text(text = getCardinalDirection(uiState.windDeg), style = MaterialTheme.typography.labelSmall, color = Color.Black, maxLines = 1)
                        }
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MetricCard(title = stringResource(R.string.metric_precip), value = "${uiState.precip}%", backgroundColor = viewModel.getCardColor("Precip", uiState.precip), isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_precip); detailDesc = context.getString(R.string.desc_precip); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_clouds), value = "${uiState.clouds}%", backgroundColor = viewModel.getCardColor("Cloud", uiState.clouds), isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_clouds); detailDesc = context.getString(R.string.desc_clouds); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_visibility), value = "${uiState.visibility} km", backgroundColor = viewModel.getCardColor("Visibility", uiState.visibility), isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_visibility); detailDesc = context.getString(R.string.desc_visibility); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_kp_live), value = uiState.kpValue?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", backgroundColor = viewModel.getCardColor("Kp", uiState.kpValue), isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_kp_live); detailDesc = context.getString(R.string.desc_kp); showDetail = true })
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SunTimesCard(sunrise = formatTime(uiState.sunrise, uiState.timeFormat24h), sunset = formatTime(uiState.sunset, uiState.timeFormat24h), modifier = Modifier.weight(1f), isLandscape = true, onClick = { detailTitle = context.getString(R.string.metric_sunrise); detailDesc = context.getString(R.string.desc_sunrise_sunset); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_temperature), value = "${uiState.temperature} \u00B0C", isLandscape = true, modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_temperature); detailDesc = context.getString(R.string.desc_temp); showDetail = true })
                    MetricCard(
                        title = stringResource(R.string.metric_sats_vis), 
                        value = uiState.forecastSats.toString(), 
                        isLandscape = true, 
                        modifier = Modifier.weight(1f), 
                        onClick = { detailTitle = context.getString(R.string.metric_sats_vis); detailDesc = context.getString(R.string.desc_sats); showDetail = true }
                    )
                    MetricCard(
                        title = stringResource(R.string.metric_sats_lock), 
                        value = "${uiState.forecastSatsLocked}", 
                        isLandscape = true, 
                        modifier = Modifier.weight(1f), 
                        onClick = { detailTitle = context.getString(R.string.metric_sats_lock); detailDesc = context.getString(R.string.desc_sats); showDetail = true }
                    )
                }
            } else {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MetricCard(title = stringResource(R.string.metric_weather), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_weather); detailDesc = context.getString(R.string.desc_clouds); showDetail = true }) {
                        AsyncImage(model = "https://openweathermap.org/img/wn/${uiState.weatherIcon ?: "01d"}@2x.png", contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                    MetricCard(title = stringResource(R.string.metric_wind_air), value = "${uiState.windSpeed} km/h", backgroundColor = viewModel.getCardColor("Vent", uiState.windSpeed), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_wind_air); detailDesc = context.getString(R.string.desc_wind); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_gusts), value = "${uiState.windGust} km/h", backgroundColor = viewModel.getCardColor("Gusts", uiState.windGust), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_gusts); detailDesc = context.getString(R.string.desc_gusts); showDetail = true })
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Card(modifier = Modifier.weight(1f).padding(2.dp).fillMaxSize(), colors = CardDefaults.cardColors(containerColor = GreenSafe), shape = RoundedCornerShape(8.dp), onClick = { detailTitle = context.getString(R.string.metric_wind_dir); detailDesc = context.getString(R.string.desc_wind_dir); showDetail = true }) {
                        Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.metric_wind_dir), style = MaterialTheme.typography.labelMedium, color = Color.Black.copy(alpha = 0.6f), maxLines = 1)
                            WindDirectionIndicator(degrees = uiState.windDeg)
                            Text(text = getCardinalDirection(uiState.windDeg), style = MaterialTheme.typography.labelMedium, color = Color.Black, maxLines = 1)
                        }
                    }
                    MetricCard(title = stringResource(R.string.metric_precip), value = "${uiState.precip}%", backgroundColor = viewModel.getCardColor("Precip", uiState.precip), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_precip); detailDesc = context.getString(R.string.desc_precip); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_clouds), value = "${uiState.clouds}%", backgroundColor = viewModel.getCardColor("Cloud", uiState.clouds), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_clouds); detailDesc = context.getString(R.string.desc_clouds); showDetail = true })
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MetricCard(title = stringResource(R.string.metric_visibility), value = "${uiState.visibility} km", backgroundColor = viewModel.getCardColor("Visibility", uiState.visibility), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_visibility); detailDesc = context.getString(R.string.desc_visibility); showDetail = true })
                    MetricCard(title = stringResource(R.string.metric_kp_live), value = uiState.kpValue?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", backgroundColor = viewModel.getCardColor("Kp", uiState.kpValue), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_kp_live); detailDesc = context.getString(R.string.desc_kp); showDetail = true })
                    SunTimesCard(sunrise = formatTime(uiState.sunrise, uiState.timeFormat24h), sunset = formatTime(uiState.sunset, uiState.timeFormat24h), modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_sunrise); detailDesc = context.getString(R.string.desc_sunrise_sunset); showDetail = true })
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MetricCard(title = stringResource(R.string.metric_temperature), value = "${uiState.temperature} \u00B0C", modifier = Modifier.weight(1f), onClick = { detailTitle = context.getString(R.string.metric_temperature); detailDesc = context.getString(R.string.desc_temp); showDetail = true })
                    MetricCard(
                        title = stringResource(R.string.metric_sats_vis), 
                        value = uiState.forecastSats.toString(), 
                        modifier = Modifier.weight(1f), 
                        onClick = { detailTitle = context.getString(R.string.metric_sats_vis); detailDesc = context.getString(R.string.desc_sats); showDetail = true }
                    )
                    MetricCard(
                        title = stringResource(R.string.metric_sats_lock), 
                        value = "${uiState.forecastSatsLocked}",
                        modifier = Modifier.weight(1f), 
                        onClick = { detailTitle = context.getString(R.string.metric_sats_lock); detailDesc = context.getString(R.string.desc_sats); showDetail = true }
                    )
                }
            }
        }
        val forecastWeight = if (isLandscape) 2.2f else 2.5f
        InteractiveForecastSelector(uiState, viewModel, modifier = Modifier.weight(forecastWeight))
    }
}

@Composable
fun SkyGoDashboard(viewModel: WeatherViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Global back handler to return to DASHBOARD tab
    BackHandler(enabled = uiState.currentTab != AppTab.DASHBOARD) {
        viewModel.setTab(AppTab.DASHBOARD)
    }

    // Refresh data when app returns to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateLocationAndData(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.language) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = LocaleList.forLanguageTags(uiState.language)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(uiState.language))
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val gnssManager = remember { GnssManager(context) }
    val compassManager = remember { CompassManager(context) }

    DisposableEffect(permissionsGranted, uiState.useGps, uiState.useGlonass, uiState.useGalileo, uiState.useBeidou) {
        if (permissionsGranted) {
            gnssManager.useGps = uiState.useGps
            gnssManager.useGlonass = uiState.useGlonass
            gnssManager.useGalileo = uiState.useGalileo
            gnssManager.useBeidou = uiState.useBeidou
            gnssManager.onStatusChanged = { total, locked -> viewModel.updateSatellites(total, locked) }
            gnssManager.startTracking()
        }
        onDispose { gnssManager.stopTracking() }
    }

    DisposableEffect(Unit) {
        compassManager.onAzimuthChanged = { azimuth -> viewModel.updateDeviceAzimuth(azimuth) }
        compassManager.start()
        onDispose { compassManager.stop() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) { permissionsGranted = true; viewModel.updateLocationAndData(context) }
        else { viewModel.refresh("Bezannes", 49.2217, 3.9928) }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            // Déjà accordé, on met à jour la position et les données
            viewModel.updateLocationAndData(context)
        }
        // Initialiser le format 24h selon les paramètres du système
        viewModel.updateSettings(timeFormat24h = android.text.format.DateFormat.is24HourFormat(context))
    }

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), color = Color(0xFF12151C), border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))) {
                Row(modifier = Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    NavItem(Icons.Default.SettingsInputAntenna, uiState.currentTab == AppTab.DASHBOARD) { viewModel.setTab(AppTab.DASHBOARD) }
                    NavItem(Icons.Default.Build, uiState.currentTab == AppTab.TOOLS) { viewModel.setTab(AppTab.TOOLS) }
                    NavItem(Icons.Default.Group, uiState.currentTab == AppTab.COMMUNITY) { viewModel.setTab(AppTab.COMMUNITY) }
                    NavItem(Icons.AutoMirrored.Filled.Help, uiState.currentTab == AppTab.HELP) { viewModel.setTab(AppTab.HELP) }
                    NavItem(Icons.Default.Settings, uiState.currentTab == AppTab.SETTINGS) { viewModel.setTab(AppTab.SETTINGS) }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (uiState.currentTab) {
                AppTab.DASHBOARD -> DashboardContent(uiState, viewModel, context)
                AppTab.TOOLS -> ToolScreen(viewModel)
                AppTab.COMMUNITY -> CommunityScreen()
                AppTab.HELP -> HelpScreen()
                AppTab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) Color(0xFF00B0FF) else Color.Gray, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun ToolScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var activeTool by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == AndroidConfig.ORIENTATION_LANDSCAPE
    
    BackHandler(enabled = activeTool != null) {
        activeTool = null
    }

    val menuItems = listOf(
        Triple(R.string.tool_forecast_table, "table", Icons.Default.GridOn to Color(0xFF00B0FF)),
        Triple(R.string.tool_wind_profile, "wind_profile", Icons.Default.AlignVerticalBottom to Color(0xFFFFB300)),
        Triple(R.string.tool_checklist, "checklist", Icons.AutoMirrored.Filled.FactCheck to Color(0xFF4CAF50)),
        Triple(R.string.tool_wind_compass, "compass", Icons.Default.Explore to Color(0xFF00B0FF)),
        Triple(R.string.tool_safe_zones, "map", Icons.Default.Public to Color(0xFFF44336)),
        Triple(R.string.tool_sat_stats, "sat_stats", Icons.Default.SatelliteAlt to Color(0xFF9C27B0))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.tab_tools),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            items(menuItems) { (resId, id, iconColor) ->
                val (icon, color) = iconColor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeTool = id }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(resId),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
        }

        // Tool Overlays
        activeTool?.let { tool ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground)
                    .clickable(enabled = false) {} // Intercept clicks
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isLandscape) 4.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { activeTool = null },
                            modifier = Modifier.size(if (isLandscape) 36.dp else 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = null, 
                                tint = Color.White,
                                modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                            )
                        }
                        Text(
                            text = stringResource(menuItems.find { it.second == tool }?.first ?: R.string.tab_tools),
                            style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f).padding(top = if (isLandscape) 0.dp else 8.dp)) {
                        when (tool) {
                            "table" -> ForecastTable(uiState, viewModel)
                            "wind_profile" -> WindProfileScreen(uiState, viewModel)
                            "checklist" -> ChecklistScreen(uiState, viewModel)
                            "compass" -> WindCompassScreen(uiState, viewModel)
                            "map" -> SafeZoneMapScreen(uiState)
                            "sat_stats" -> SatelliteStatsScreen(uiState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SatelliteStatsScreen(uiState: WeatherUiState) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == AndroidConfig.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Graph
        SatelliteForecastChart(
            forecasts = uiState.satelliteForecast,
            modifier = Modifier.padding(horizontal = 16.dp),
            isLandscape = isLandscape
        )
        
        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))
        
        // 2. Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2330)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.desc_sats),
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Extra space at bottom to ensure scroll visibility
        if (isLandscape) Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ForecastTable(uiState: WeatherUiState, viewModel: WeatherViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val groupedForecast = remember(uiState.hourlyForecast) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", locale)
        uiState.hourlyForecast.groupBy {
            sdf.format(Date(it.timestamp * 1000))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12151C))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val headerStyle = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.header_time), modifier = Modifier.weight(1.2f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.header_temp), modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.header_wind), modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.header_gusts), modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.header_kp), modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.header_rain), modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groupedForecast.forEach { (date, hours) ->
                item {
                    val dayLabel = if (date == SimpleDateFormat("yyyy-MM-dd", locale).format(Date())) {
                        stringResource(R.string.today)
                    } else {
                        SimpleDateFormat("EEEE d MMMM", locale).format(Date(hours.first().timestamp * 1000))
                            .replaceFirstChar { it.uppercase() }
                    }
                    Text(
                        text = dayLabel,
                        color = Color(0xFF00B0FF),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }

                items(hours) { hour ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Time Column
                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = hour.time,
                                color = if (hour.isNow) Color(0xFF00B0FF) else Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (hour.isNow) FontWeight.Bold else FontWeight.Normal
                            )
                            if (hour.isNow) {
                                Text(stringResource(R.string.label_now_short), color = Color(0xFF00B0FF), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        // Data Cells with Colors
                        TableCell(text = "${hour.temp}\u00B0", color = viewModel.getCardColor("Temp", hour.temp), modifier = Modifier.weight(1f))
                        TableCell(text = hour.wind, color = viewModel.getCardColor("Vent", hour.wind), modifier = Modifier.weight(1f))
                        TableCell(text = hour.gusts, color = viewModel.getCardColor("Gusts", hour.gusts), modifier = Modifier.weight(1f))
                        TableCell(text = hour.kp, color = viewModel.getCardColor("Kp", hour.kp.toDoubleOrNull()), modifier = Modifier.weight(1f))
                        TableCell(text = "${hour.precip}%", color = viewModel.getCardColor("Precip", hour.precip), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(32.dp),
        color = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.Black,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WindProfileScreen(uiState: WeatherUiState, viewModel: WeatherViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == AndroidConfig.ORIENTATION_LANDSCAPE
    
    val altitudes = listOf(
        stringResource(R.string.altitude_ground) to uiState.windSpeed,
        "80m" to uiState.wind80m,
        "120m" to uiState.wind120m,
        "180m" to uiState.wind180m,
        "320m" to uiState.wind320m,
        "500m" to uiState.wind500m,
        "800m" to uiState.wind800m,
        "1000m" to uiState.wind1000m,
        "1500m" to uiState.wind1500m
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = if (isLandscape) 8.dp else 0.dp)) {
        // Data Table
        Column(modifier = Modifier.weight(1f)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(vertical = if (isLandscape) 4.dp else 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TableHeading(stringResource(R.string.header_altitude), Icons.Default.Layers, isLandscape)
                TableHeading(stringResource(R.string.header_wind), Icons.Default.Air, isLandscape)
                TableHeading(stringResource(R.string.header_gusts), Icons.Default.Cloud, isLandscape)
                TableHeading(stringResource(R.string.header_direction), Icons.Default.Navigation, isLandscape)
                TableHeading(stringResource(R.string.header_temp), Icons.Default.Thermostat, isLandscape)
            }

            // Altitude Rows - Scrollable to see all data in Landscape
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(altitudes) { (alt, speed) ->
                    val speedInt = speed.toIntOrNull() ?: 0
                    val rowColor = when {
                        speedInt >= 25 -> RedDanger
                        speedInt >= 15 -> YellowWarn
                        else -> GreenSafe
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowColor)
                            .padding(vertical = if (isLandscape) 3.dp else 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val textStyle = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
                        Text(alt, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black, style = textStyle)
                        Text("$speed km/h", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black, style = textStyle)
                        Text("${(speedInt * 1.3).toInt()} km/h", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black, style = textStyle)
                        Icon(
                            Icons.Default.PlayArrow, 
                            contentDescription = null, 
                            modifier = Modifier.weight(1f).size(if (isLandscape) 14.dp else 16.dp).rotate(uiState.windDeg.toFloat() - 90f),
                            tint = Color.Black
                        )
                        Text("${uiState.temperature} \u00B0C", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Black, style = textStyle)
                    }
                    HorizontalDivider(color = Color.Black.copy(alpha = 0.1f), thickness = 0.5.dp)
                }
            }
        }

        // Time Selector
        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
        InteractiveForecastSelector(uiState, viewModel)
    }
}

@Composable
fun TableHeading(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isLandscape: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (isLandscape) 55.dp else 65.dp)) {
        Text(label, color = Color.Gray, fontSize = if (isLandscape) 9.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (isLandscape) 12.dp else 14.dp))
    }
}

@Composable
fun ChecklistScreen(uiState: WeatherUiState, viewModel: WeatherViewModel) {
    var newItemText by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.checklist) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleChecklistItem(item.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = item.isChecked, onCheckedChange = { viewModel.toggleChecklistItem(item.id) }, colors = CheckboxDefaults.colors(checkedColor = NeonGreen))
                    Text(text = item.text, color = if (item.isChecked) Color.Gray else Color.White, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    IconButton(onClick = { viewModel.removeChecklistItem(item.id) }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.6f)) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItemText, onValueChange = { newItemText = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.add_item_placeholder), color = Color.Gray) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { if (newItemText.isNotBlank()) { viewModel.addChecklistItem(newItemText); newItemText = "" } }, modifier = Modifier.size(48.dp).background(NeonGreen, CircleShape)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            }
        }
    }
}

@Composable
fun WindCompassScreen(uiState: WeatherUiState, viewModel: WeatherViewModel) {
    val deviceAzimuth = uiState.deviceAzimuth // Orientation du téléphone (0 = Nord)
    val windDeg = uiState.windDeg.toFloat() // Direction du vent (0 = vient du Nord)
    
    // L'angle de l'aiguille bleue (vent) par rapport au téléphone
    // On ajoute 180 car l'icône Navigation pointe vers le haut (0°), 
    // et on veut montrer d'où vient le vent ou vers où il va.
    val relativeWindAngle = windDeg - deviceAzimuth

    Column(modifier = Modifier.fillMaxSize()) {
        // Location & Stats
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF00B0FF), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.current_location), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = uiState.windSpeed,
                    color = Color(0xFF00B0FF),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Column {
                    Text(
                        text = getCardinalDirection(uiState.windDeg),
                        color = Color(0xFF00B0FF),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.windDeg}\u00B0",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // 3. Visual Compass
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Compass Ring (Rotates with phone)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .rotate(-deviceAzimuth),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 1.dp.toPx()))
                    
                    // Cardinal Markers
                    val radius = size.minDimension / 2
                    val paint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 40f
                        textAlign = Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    
                    drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - radius + 50f, paint)
                    drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + radius - 20f, paint)
                    drawContext.canvas.nativeCanvas.drawText("E", center.x + radius - 30f, center.y + 15f, paint)
                    drawContext.canvas.nativeCanvas.drawText("W", center.x - radius + 30f, center.y + 15f, paint)
                }
            }

            // Fixed Phone Needle (Red - points UP)
            Box(
                modifier = Modifier
                    .height(260.dp)
                    .width(2.dp)
                    .background(Color.Red.copy(alpha = 0.8f))
                    .align(Alignment.Center)
            )

            // Wind Needle (Blue - Points to wind direction)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .rotate(relativeWindAngle),
                contentAlignment = Alignment.Center
            ) {
                // Main Wind Line
                Box(
                    modifier = Modifier
                        .height(140.dp)
                        .width(4.dp)
                        .offset(y = (-70).dp)
                        .background(
                            brush = Brush.verticalGradient(listOf(Color(0xFF00B0FF), Color.Transparent)),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                // Center Circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF12151C), CircleShape)
                        .padding(4.dp)
                        .background(Color(0xFF00B0FF), CircleShape)
                )
            }

            // Degree Badges
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BadgeInfo("MAG: ${uiState.windDeg}\u00B0", Color(0xFF00B0FF).copy(alpha = 0.2f), Color(0xFF00B0FF))
                BadgeInfo("TRUE: ${uiState.windDeg}\u00B0", Color(0xFF4CAF50).copy(alpha = 0.2f), Color(0xFF4CAF50))
            }
        }

        // 4. Time Selector
        InteractiveForecastSelector(uiState, viewModel)
    }
}

@Composable
fun BadgeInfo(text: String, bgColor: Color, textColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SafeZoneMapScreen(uiState: WeatherUiState) {
    val context = LocalContext.current
    
    // 1. Gérer la WebView avec 'remember' pour éviter de la recréer inutilement au sein de l'écran
    val webView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            webViewClient = WebViewClient()
            webChromeClient = android.webkit.WebChromeClient()
            
            @android.annotation.SuppressLint("SetJavaScriptEnabled")
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            }
        }
    }

    // 2. Cycle de vie : Détruire la WebView proprement quand on quitte l'onglet Map
    DisposableEffect(webView) {
        val lon = uiState.mapCenter.longitude
        val lat = uiState.mapCenter.latitude
        val url = "https://www.geoportail.gouv.fr/carte?c=$lon,$lat&z=14&l0=GEOGRAPHICALGRIDSYSTEMS.MAPS.SCAN25TOUR.CV::GEOPORTAIL:OGC:WMTS(1)&l1=TRANSPORTS.DRONES.RESTRICTIONS::GEOPORTAIL:OGC:WMTS(0.8)&permalink=yes"
        webView.loadUrl(url)
        
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank") // Libère les ressources JS
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    // 3. Mise en pause/reprise auto (réduit la charge CPU en arrière-plan)
    DisposableEffect(Unit) {
        onDispose {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
@Composable fun CommunityScreen() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.tab_community), color = Color.White) } }
@Composable 
fun HelpScreen() { 
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Help,
            contentDescription = null,
            tint = Color(0xFF00B0FF),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.tab_help),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "DroneWeather v1.2 Beta",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "Licence GNU GPL v3",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2330)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Support & Contact",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Mick",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "naudclick.informatik@gmail.com",
                    color = Color(0xFF00B0FF),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:naudclick.informatik@gmail.com".toUri()
                            putExtra(Intent.EXTRA_SUBJECT, "DroneWeather Support")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedSection by remember { mutableStateOf<String?>(null) }
    
    Column(modifier = Modifier.fillMaxSize().background(AppBackground).padding(16.dp)) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                // 1. Language
                var langExpanded by remember { mutableStateOf(false) }
                SettingsSelectorRow(stringResource(R.string.settings_language), when(uiState.language) {
                    "fr" -> stringResource(R.string.lang_fr)
                    "en" -> stringResource(R.string.lang_en)
                    "pl" -> stringResource(R.string.lang_pl)
                    else -> stringResource(R.string.lang_fr)
                }, langExpanded) { langExpanded = !langExpanded }
                DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }, modifier = Modifier.background(Color(0xFF1E2330))) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.lang_fr), color = Color.White) }, onClick = { viewModel.updateSettings(language = "fr"); langExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.lang_en), color = Color.White) }, onClick = { viewModel.updateSettings(language = "en"); langExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.lang_pl), color = Color.White) }, onClick = { viewModel.updateSettings(language = "pl"); langExpanded = false })
                }

                // 2. Time Format
                var timeExpanded by remember { mutableStateOf(false) }
                SettingsSelectorRow(stringResource(R.string.settings_time_format), if(uiState.timeFormat24h) stringResource(R.string.settings_time_24h) else stringResource(R.string.settings_time_12h), timeExpanded) { timeExpanded = !timeExpanded }
                DropdownMenu(expanded = timeExpanded, onDismissRequest = { timeExpanded = false }, modifier = Modifier.background(Color(0xFF1E2330))) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_time_24h), color = Color.White) }, onClick = { viewModel.updateSettings(timeFormat24h = true); timeExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_time_12h), color = Color.White) }, onClick = { viewModel.updateSettings(timeFormat24h = false); timeExpanded = false })
                }

                // 3. Drone
                var droneExpanded by remember { mutableStateOf(false) }
                SettingsSelectorRow(stringResource(R.string.settings_drone_label), when(uiState.droneType) {
                    DroneType.DJI_MINI -> stringResource(R.string.settings_drone_mini)
                    DroneType.DJI_AIR -> stringResource(R.string.settings_drone_air)
                    DroneType.DJI_MAVIC -> stringResource(R.string.settings_drone_mavic)
                    else -> stringResource(R.string.settings_drone_custom)
                }, droneExpanded) { droneExpanded = !droneExpanded }
                DropdownMenu(expanded = droneExpanded, onDismissRequest = { droneExpanded = false }, modifier = Modifier.background(Color(0xFF1E2330))) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_drone_mini), color = Color.White) }, onClick = { viewModel.updateSettings(droneType = DroneType.DJI_MINI); droneExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_drone_air), color = Color.White) }, onClick = { viewModel.updateSettings(droneType = DroneType.DJI_AIR); droneExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_drone_mavic), color = Color.White) }, onClick = { viewModel.updateSettings(droneType = DroneType.DJI_MAVIC); droneExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_drone_custom), color = Color.White) }, onClick = { viewModel.updateSettings(droneType = DroneType.CUSTOM); droneExpanded = false })
                }

                // 4. Source
                var sourceExpanded by remember { mutableStateOf(false) }
                SettingsSelectorRow(stringResource(R.string.settings_source_label), when(uiState.selectedSource) {
                    WeatherSource.OPEN_METEO -> "Open-Meteo"
                    WeatherSource.APPLE_WEATHER -> "Apple Weather"
                    else -> "WeatherAPI"
                }, sourceExpanded) { sourceExpanded = !sourceExpanded }
                DropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }, modifier = Modifier.background(Color(0xFF1E2330))) {
                    DropdownMenuItem(text = { Text("Open-Meteo", color = Color.White) }, onClick = { viewModel.updateSource(WeatherSource.OPEN_METEO); sourceExpanded = false })
                    DropdownMenuItem(text = { Text("Apple Weather", color = Color.White) }, onClick = { viewModel.updateSource(WeatherSource.APPLE_WEATHER); sourceExpanded = false })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Expandable Sections ---

                // Température
                SettingsExpandableRow(stringResource(R.string.settings_temp_label), expandedSection == "temp") { expandedSection = if (expandedSection == "temp") null else "temp" }
                if (expandedSection == "temp") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_temp_range, uiState.tempMinThreshold, uiState.tempMaxThreshold), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        RangeSlider(
                            value = uiState.tempMinThreshold.toFloat()..uiState.tempMaxThreshold.toFloat(),
                            onValueChange = { viewModel.updateSettings(tempMin = it.start.roundToInt(), tempMax = it.endInclusive.roundToInt()) },
                            valueRange = -20f..50f,
                            colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
                        )
                    }
                }

                // Vitesse du vent
                SettingsExpandableRow(stringResource(R.string.settings_wind_label), expandedSection == "wind") { expandedSection = if (expandedSection == "wind") null else "wind" }
                if (expandedSection == "wind") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_wind_max, uiState.windMaxThreshold), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = uiState.windMaxThreshold.toFloat(),
                            onValueChange = { viewModel.updateSettings(windMax = it.roundToInt()) },
                            valueRange = 10f..60f,
                            colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
                        )
                    }
                }

                // Altitude
                SettingsExpandableRow(stringResource(R.string.settings_altitude_label), expandedSection == "alt") { expandedSection = if (expandedSection == "alt") null else "alt" }
                if (expandedSection == "alt") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UnitChip(stringResource(R.string.settings_alt_meters), uiState.altitudeUnit == DistanceUnit.METERS) { viewModel.updateSettings(altitudeUnit = DistanceUnit.METERS) }
                            UnitChip(stringResource(R.string.settings_alt_feet), uiState.altitudeUnit == DistanceUnit.FEET) { viewModel.updateSettings(altitudeUnit = DistanceUnit.FEET) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.settings_forecast_alt, uiState.forecastAltitude), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = uiState.forecastAltitude.toFloat(),
                            onValueChange = { viewModel.updateSettings(forecastAltitude = it.roundToInt()) },
                            valueRange = 10f..500f,
                            colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
                        )
                    }
                }

                // Visibilité
                SettingsExpandableRow(stringResource(R.string.settings_visibility_label), expandedSection == "vis") { expandedSection = if (expandedSection == "vis") null else "vis" }
                if (expandedSection == "vis") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_visibility_min, uiState.visibilityMinThreshold), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = uiState.visibilityMinThreshold.toFloat(),
                            onValueChange = { viewModel.updateSettings(visibilityMin = it.toDouble()) },
                            valueRange = 1f..10f,
                            colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
                        )
                    }
                }

                // Météo
                SettingsExpandableRow(stringResource(R.string.settings_weather_label), expandedSection == "meteo") { expandedSection = if (expandedSection == "meteo") null else "meteo" }
                if (expandedSection == "meteo") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSwitchRow(stringResource(R.string.settings_alert_rain), true) {}
                        SettingsSwitchRow(stringResource(R.string.settings_alert_storm), true) {}
                    }
                }

                // Satellites
                SettingsExpandableRow(stringResource(R.string.settings_gnss_constellations), expandedSection == "gnss") { expandedSection = if (expandedSection == "gnss") null else "gnss" }
                if (expandedSection == "gnss") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSwitchRow(stringResource(R.string.gnss_gps), uiState.useGps) { viewModel.updateSettings(useGps = it) }
                        SettingsSwitchRow(stringResource(R.string.gnss_glonass), uiState.useGlonass) { viewModel.updateSettings(useGlonass = it) }
                        SettingsSwitchRow(stringResource(R.string.gnss_galileo), uiState.useGalileo) { viewModel.updateSettings(useGalileo = it) }
                        SettingsSwitchRow(stringResource(R.string.gnss_beidou), uiState.useBeidou) { viewModel.updateSettings(useBeidou = it) }
                    }
                }

                // Affichage des Données
                SettingsExpandableRow(stringResource(R.string.settings_ui_label), expandedSection == "ui") { expandedSection = if (expandedSection == "ui") null else "ui" }
                if (expandedSection == "ui") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSwitchRow(stringResource(R.string.settings_dark_theme), true) {}
                        SettingsSwitchRow(stringResource(R.string.settings_smooth_anim), true) {}
                    }
                }

                // Notifications
                SettingsExpandableRow(stringResource(R.string.settings_notifications_label), expandedSection == "notif") { expandedSection = if (expandedSection == "notif") null else "notif" }
                if (expandedSection == "notif") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsSwitchRow(stringResource(R.string.settings_alert_weather), true) {}
                        SettingsSwitchRow(stringResource(R.string.settings_morning_forecast), false) {}
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSelectorRow(label: String, value: String, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
            )
        }
    }
}

@Composable
fun UnitChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(name, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonGreen,
            selectedLabelColor = Color.Black
        )
    )
}

@Composable
fun SettingsExpandableRow(title: String, isExpanded: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = NeonGreen.copy(alpha = 0.5f)))
    }
}

fun formatTime(timestamp: Long, is24h: Boolean = true): String {
    if (timestamp == 0L) return "--:--"
    val pattern = if (is24h) "HH:mm" else "hh:mm a"
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}

fun getCardinalDirection(degrees: Int): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO", "N")
    return directions[((degrees % 360) / 45.0).roundToInt()]
}
