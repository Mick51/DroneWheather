package com.example.droneweather

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.URL

class TleDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.d("TleWorker", "Starting TLE download from CelesTrak")
            
            // 1. Download TLE file
            val url = "https://celestrak.org/NORAD/elements/gp.php?GROUP=gnss&FORMAT=tle"
            val tleContent = URL(url).readText()
            
            // 2. Parse the content (TLE is 3 lines per satellite)
            val satelliteList = parseTleData(tleContent)
            
            // 3. Save to Room
            val db = AppDatabase.getDatabase(applicationContext)
            db.weatherDao().insertTleData(satelliteList)
            
            Log.d("TleWorker", "Successfully downloaded and saved ${satelliteList.size} TLE entries")
            Result.success()
        } catch (e: Exception) {
            Log.e("TleWorker", "Error downloading TLE data", e)
            Result.retry()
        }
    }

    private fun parseTleData(content: String): List<TleData> {
        val lines = content.lines().filter { it.isNotBlank() }
        val tleList = mutableListOf<TleData>()
        val now = System.currentTimeMillis()

        // TLE format: Line 0 (Name), Line 1, Line 2
        for (i in 0 until lines.size - 2 step 3) {
            val name = lines[i].trim()
            val l1 = lines[i+1]
            val l2 = lines[i+2]
            
            if (l1.startsWith("1") && l2.startsWith("2")) {
                tleList.add(TleData(name, l1, l2, now))
            }
        }
        return tleList
    }
}
