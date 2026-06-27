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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mick.droneweather.ui.theme.CardBackground
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SatelliteForecastChart(
    forecasts: List<SatelliteForecast>,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    if (forecasts.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(100.dp).background(CardBackground, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text("Calcul des trajectoires...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val chartHeight = if (isLandscape) 80.dp else 120.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            stringResource(R.string.chart_sat_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val maxSats = 35f
                val points = forecasts.size
                val stepX = width / (points - 1).coerceAtLeast(1)

                // Draw Grid
                for (i in 0..3) {
                    val y = height - (i * (height / 3))
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Path for Available Satellites (Light Blue)
                val pathAvailable = Path().apply {
                    forecasts.forEachIndexed { index, forecast ->
                        val x = index * stepX
                        val y = height - (forecast.availableSatellites / maxSats * height)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(pathAvailable, color = Color(0xFF00B0FF).copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))

                // Path for Locked Satellites (Green)
                val pathLocked = Path().apply {
                    forecasts.forEachIndexed { index, forecast ->
                        val x = index * stepX
                        val y = height - (forecast.lockedSatellites / maxSats * height)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(pathLocked, color = Color(0xFF4CAF50), style = Stroke(width = 3.dp.toPx()))
            }
        }

        // Legend/X-Axis
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val sdf = SimpleDateFormat("HH:mm", locale)
            Text(sdf.format(Date(forecasts.first().timestamp * 1000)), color = Color.Gray, fontSize = 10.sp)
            Text(sdf.format(Date(forecasts[forecasts.size / 2].timestamp * 1000)), color = Color.Gray, fontSize = 10.sp)
            Text(sdf.format(Date(forecasts.last().timestamp * 1000)), color = Color.Gray, fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.chart_sat_locked), color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(8.dp).background(Color(0xFF00B0FF).copy(alpha = 0.4f)))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.chart_sat_visible), color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
        }
    }
}

