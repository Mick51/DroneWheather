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
            val forecasts = predictor.generateMultiDayForecast(lat, lon, kp, tleList, days = 7)
            
            weatherDao.clearOldForecasts(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
            weatherDao.insertSatelliteForecasts(forecasts)
            
            Log.d("SatelliteWorker", "Successfully updated ${forecasts.size} forecast points")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("SatelliteWorker", "Error updating satellite forecast", e)
            ListenableWorker.Result.retry()
        }
    }
}

