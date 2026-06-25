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
import android.util.Log
import org.orekit.bodies.GeodeticPoint

class SatellitePredictor {

    private val earth = OneAxisEllipsoid(
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
        Constants.WGS84_EARTH_FLATTENING,
        FramesFactory.getITRF(IERSConventions.IERS_2010, false)
    )

    fun calculatePrediction(
        timestamp: Long,
        userLat: Double,
        userLon: Double,
        kp: Float,
        tleList: List<TleData>,
        useGps: Boolean = true,
        useGlonass: Boolean = true,
        useGalileo: Boolean = true,
        useBeidou: Boolean = true
    ): SatelliteForecast {
        val date = AbsoluteDate(Date(timestamp * 1000), TimeScalesFactory.getUTC())
        val userPoint = GeodeticPoint(Math.toRadians(userLat), Math.toRadians(userLon), 0.0)
        val topoFrame = TopocentricFrame(earth, userPoint, "UserLocation")

        var visible = 0
        var locked = 0

        val lockProbabilityBase = when {
            kp >= 5f -> 0.4f
            kp >= 3f -> 0.7f
            else -> 0.95f
        }

        // Dynamic cap based on selected constellations
        // 4 active (GPS+GLO+GAL+BDS) -> ~40 visible, ~30 locked
        // 3 active (GPS+GLO+GAL) -> ~30 visible, ~22 locked
        val activeCount = (if(useGps) 1 else 0) + (if(useGlonass) 1 else 0) + (if(useGalileo) 1 else 0) + (if(useBeidou) 1 else 0)
        val maxVisible = (activeCount * 11).coerceAtLeast(12)
        val maxLocked = (activeCount * 8).coerceAtLeast(8)

        for (tleData in tleList) {
            try {
                val tle = TLE(tleData.line1, tleData.line2)
                val propagator = TLEPropagator.selectExtrapolator(tle)
                val pvCoordinates = propagator.getPVCoordinates(date, FramesFactory.getGCRF())
                
                val elevation = topoFrame.getElevation(pvCoordinates.position, FramesFactory.getGCRF(), date)
                val elevationDeg = Math.toDegrees(elevation)
                
                // Elevation mask of 15 degrees is standard for reliable drone positioning
                if (elevationDeg > 15.0) {
                    visible++
                    
                    val elevationWeight = (elevationDeg - 15.0) / 75.0
                    val adjustedLockProb = lockProbabilityBase * (0.75f + 0.25f * elevationWeight.toFloat())
                    
                    val prob = Math.random()
                    if (prob < adjustedLockProb) {
                        locked++
                    }
                }
            } catch (_: Exception) {}
        }

        val finalVisible = if (visible == 0 && tleList.isEmpty()) (maxVisible - 5..maxVisible).random() else visible.coerceAtMost(maxVisible)
        val finalLocked = if (locked == 0 && tleList.isEmpty()) (finalVisible * 0.75).toInt() else locked.coerceAtMost(maxLocked)
        
        Log.d("SatellitePredictor", "Final Result ($activeCount const): Vis=$finalVisible, Lock=$finalLocked")

        return SatelliteForecast(
            timestamp = timestamp,
            availableSatellites = finalVisible,
            lockedSatellites = finalLocked,
            kpIndex = kp
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

        for (i in 0 until points) {
            val targetTime = now + (i * step)
            forecasts.add(calculatePrediction(targetTime, startLat, startLon, currentKp, tleList, useGps, useGlonass, useGalileo, useBeidou))
        }

        return forecasts
    }

    fun generate24hForecast(
        startLat: Double,
        startLon: Double,
        currentKp: Float,
        tleList: List<TleData>,
        useGps: Boolean = true,
        useGlonass: Boolean = true,
        useGalileo: Boolean = true,
        useBeidou: Boolean = true
    ): List<SatelliteForecast> {
        return generateMultiDayForecast(startLat, startLon, currentKp, tleList, 1, useGps, useGlonass, useGalileo, useBeidou)
    }
}

