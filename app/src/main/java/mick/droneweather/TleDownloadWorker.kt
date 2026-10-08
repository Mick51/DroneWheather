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
            
            val tleList = mutableListOf<TleData>()
            val now = System.currentTimeMillis()
            var currentName = ""
            var currentL1 = ""

            connection.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    if (!trimmed.startsWith("1 ") && !trimmed.startsWith("2 ")) {
                        currentName = trimmed.uppercase()
                    } else if (trimmed.startsWith("1 ")) {
                        currentL1 = trimmed
                    } else if (trimmed.startsWith("2 ")) {
                        if (currentName.isNotEmpty() && currentL1.isNotEmpty()) {
                            val isOperational = when {
                                currentName.contains("GPS") && (currentName.contains("PRN") || currentName.contains("BI")) -> true
                                currentName.contains("COSMOS") || currentName.contains("GLONASS") -> true
                                currentName.contains("GSAT") || currentName.contains("GALILEO") -> true
                                currentName.contains("BEIDOU") || currentName.contains("BDS") -> {
                                    !currentName.contains(" G") && !currentName.contains("IGSO")
                                }
                                else -> false
                            }

                            if (isOperational) {
                                tleList.add(TleData(currentName, currentL1, trimmed, now))
                            }
                            
                            // Reset state for next satellite
                            currentName = ""
                            currentL1 = ""
                        }
                    }
                }
            }
            
            // 3. Save to Room
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.weatherDao()
            dao.clearTleData() // Clear old data to apply the new strict filters
            dao.insertTleData(tleList)
            
            // Trigger an immediate satellite forecast update since we have new orbital data
            val forecastRequest = androidx.work.OneTimeWorkRequestBuilder<SatelliteForecastWorker>().build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueue(forecastRequest)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
