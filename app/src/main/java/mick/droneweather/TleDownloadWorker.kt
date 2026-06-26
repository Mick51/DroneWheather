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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class TleDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Download TLE file
            // Use the comprehensive GNSS group
            val url = "https://celestrak.org/NORAD/elements/gp.php?GROUP=gnss&FORMAT=tle"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val tleContent = connection.inputStream.bufferedReader().use { it.readText() }
            
            // 2. Parse and FILTER operational satellites
            val satelliteList = parseTleData(tleContent)
            
            // 3. Save to Room
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.weatherDao()
            dao.clearTleData() // Clear old data to apply the new strict filters
            dao.insertTleData(satelliteList)
            
            // Trigger an immediate satellite forecast update since we have new orbital data
            val forecastRequest = androidx.work.OneTimeWorkRequestBuilder<SatelliteForecastWorker>().build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueue(forecastRequest)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun parseTleData(content: String): List<TleData> {
        val lines = content.lines().filter { it.isNotBlank() }
        val tleList = mutableListOf<TleData>()
        val now = System.currentTimeMillis()

        // TLE format: Line 0 (Name), Line 1, Line 2
        for (i in 0 until lines.size - 2 step 3) {
            val name = lines[i].trim().uppercase()
            val l1 = lines[i+1]
            val l2 = lines[i+2]
            
            if (l1.startsWith("1") && l2.startsWith("2")) {
                // STRICT FILTERING: Only keep operational GPS, GLONASS, Galileo, and BeiDou.
                // This removes experimental, non-operational, and SBAS (EGNOS/WAAS) 
                // which drones often exclude from the main 'locked' count.
                val isOperational = when {
                    // GPS: PRN 01-32 are operational. TLE name usually includes PRN.
                    name.contains("GPS") && (name.contains("PRN") || name.contains("BI")) -> true
                    // GLONASS: Operational satellites
                    name.contains("COSMOS") || name.contains("GLONASS") -> true
                    // GALILEO: GSAT series
                    name.contains("GSAT") || name.contains("GALILEO") -> true
                    // BEIDOU: BD/BDS series
                    name.contains("BEIDOU") || name.contains("BDS") -> {
                        // STRICT FILTER: Drones mainly use MEO satellites (higher PRNs/specific series)
                        // and ignore GEO/IGSO satellites which are often too low or fixed.
                        // On CelesTrak, MEOs are typically NOT marked with G or IGSO.
                        !name.contains(" G") && !name.contains("IGSO")
                    }
                    else -> false
                }

                if (isOperational) {
                    tleList.add(TleData(name, l1, l2, now))
                }
            }
        }
        return tleList
    }
}
