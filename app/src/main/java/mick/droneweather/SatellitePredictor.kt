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

import org.orekit.bodies.OneAxisEllipsoid
import org.orekit.frames.FramesFactory
import org.orekit.frames.TopocentricFrame
import org.orekit.propagation.analytical.tle.TLE
import org.orekit.propagation.analytical.tle.TLEPropagator
import org.orekit.time.AbsoluteDate
import org.orekit.time.TimeScalesFactory
import org.orekit.utils.Constants
import org.orekit.utils.IERSConventions
import java.util.*
import org.orekit.bodies.GeodeticPoint

class SatellitePredictor {

    private val earth by lazy {
        OneAxisEllipsoid(
            Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
            Constants.WGS84_EARTH_FLATTENING,
            FramesFactory.getITRF(IERSConventions.IERS_2010, false)
        )
    }

    fun generateMultiDayForecast(
        startLat: Double,
        startLon: Double,
        currentKp: Float,
        tleList: List<TleData>,
        days: Int = 7,
        useGps: Boolean = true,
        useGlonass: Boolean = true,
        useGalileo: Boolean = true,
        useBeidou: Boolean = true
    ): List<SatelliteForecast> {
        val forecasts = mutableListOf<SatelliteForecast>()
        val now = (System.currentTimeMillis() / 1000) / 3600 * 3600
        val step = 3600 // 1 hour steps for 7 days (168 points)
        val points = days * 24

        val userPoint = GeodeticPoint(Math.toRadians(startLat), Math.toRadians(startLon), 0.0)
        val topoFrame = TopocentricFrame(earth, userPoint, "UserLocation")
        val utc = TimeScalesFactory.getUTC()
        val gcrf = FramesFactory.getGCRF()

        // Pre-create propagators to avoid heavy object creation in the loop
        val propagators = tleList.mapNotNull { tleData ->
            try {
                val tle = TLE(tleData.line1, tleData.line2)
                TLEPropagator.selectExtrapolator(tle)
            } catch (_: Exception) {
                null
            }
        }

        val lockProbabilityBase = when {
            currentKp >= 5f -> 0.4f
            currentKp >= 3f -> 0.7f
            else -> 0.95f
        }

        val activeCount = (if(useGps) 1 else 0) + (if(useGlonass) 1 else 0) + (if(useGalileo) 1 else 0) + (if(useBeidou) 1 else 0)
        val maxVisible = when(activeCount) { 4 -> 38; 3 -> 28; 2 -> 18; else -> 12 }
        val maxLocked = when(activeCount) { 4 -> 30; 3 -> 24; 2 -> 15; else -> 8 }

        for (i in 0 until points) {
            val targetTime = now + (i * step)
            val date = AbsoluteDate(Date(targetTime * 1000), utc)
            
            var visible = 0
            var locked = 0

            for (propagator in propagators) {
                try {
                    val pvCoordinates = propagator.getPVCoordinates(date, gcrf)
                    val elevation = topoFrame.getElevation(pvCoordinates.position, gcrf, date)
                    val elevationDeg = Math.toDegrees(elevation)
                    
                    if (elevationDeg > 15.0) {
                        visible++
                        val elevationWeight = (elevationDeg - 15.0) / 75.0
                        val adjustedLockProb = lockProbabilityBase * (0.75f + 0.25f * elevationWeight.toFloat())
                        if (Math.random() < adjustedLockProb) {
                            locked++
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalVisible = if (visible == 0 && propagators.isEmpty()) (maxVisible - 5..maxVisible).random() else visible.coerceAtMost(maxVisible)
            val finalLocked = if (locked == 0 && propagators.isEmpty()) (finalVisible * 0.75).toInt() else locked.coerceAtMost(maxLocked)

            forecasts.add(SatelliteForecast(targetTime, finalVisible, finalLocked, currentKp))
        }

        return forecasts
    }
}

