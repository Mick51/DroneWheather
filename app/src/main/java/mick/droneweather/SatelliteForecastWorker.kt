package mick.droneweather

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import android.util.Log

class SatelliteForecastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        Log.d("SatelliteWorker", "Starting background satellite forecast update")
        
        val db = AppDatabase.getDatabase(applicationContext)
        val weatherDao = db.weatherDao()
        
        // Use a dummy location if we don't have one, or better, use the last cached location
        val cached = weatherDao.getCachedData()
        val tleList = weatherDao.getAllTleData()
        val lat = cached?.latitude ?: 49.2217
        val lon = cached?.longitude ?: 3.9928
        val kp = cached?.kpValue?.toFloat() ?: 0f

        return try {
            val predictor = SatellitePredictor()
            val forecasts = predictor.generate24hForecast(lat, lon, kp, tleList)
            
            weatherDao.clearOldForecasts(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            weatherDao.insertSatelliteForecasts(forecasts)
            
            Log.d("SatelliteWorker", "Successfully updated ${forecasts.size} forecast points")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("SatelliteWorker", "Error updating satellite forecast", e)
            ListenableWorker.Result.retry()
        }
    }
}
