package com.example.droneweather

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log

class GnssManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var gnssStatusCallback: GnssStatus.Callback? = null
    
    // Un LocationListener est nécessaire pour "réveiller" le GPS sur certains appareils
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: android.location.Location) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    
    var onStatusChanged: ((Int, Int) -> Unit)? = null
    
    var useGps: Boolean = true
    var useGlonass: Boolean = true
    var useGalileo: Boolean = true
    var useBeidou: Boolean = true

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (gnssStatusCallback != null) return

        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val totalCount = status.satelliteCount
                var visibleSats = 0
                var lockedSats = 0
                
                for (i in 0 until totalCount) {
                    val constellation = status.getConstellationType(i)
                    // Filtrer selon les paramètres utilisateur
                    val isTargetConstellation = when (constellation) {
                        GnssStatus.CONSTELLATION_GPS -> useGps
                        GnssStatus.CONSTELLATION_GLONASS -> useGlonass
                        GnssStatus.CONSTELLATION_BEIDOU -> useBeidou
                        GnssStatus.CONSTELLATION_GALILEO -> useGalileo
                        else -> false
                    }

                    if (isTargetConstellation) {
                        visibleSats++
                        if (status.usedInFix(i)) {
                            lockedSats++
                        }
                    }
                }
                onStatusChanged?.invoke(visibleSats, lockedSats)
            }

            override fun onStarted() {
                Log.d("GnssManager", "GNSS Tracking Started")
            }

            override fun onStopped() {
                Log.d("GnssManager", "GNSS Tracking Stopped")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(context.mainExecutor, gnssStatusCallback!!)
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(gnssStatusCallback!!)
            }
            
            // Demander des mises à jour de localisation légères pour forcer l'activation du GNSS
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // 1 seconde
                0f, 
                locationListener
            )
            
        } catch (e: Exception) {
            Log.e("GnssManager", "Error registering GNSS callback: ${e.message}")
        }
    }

    fun stopTracking() {
        try {
            gnssStatusCallback?.let {
                locationManager.unregisterGnssStatusCallback(it)
                gnssStatusCallback = null
            }
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e("GnssManager", "Error stopping GNSS tracking: ${e.message}")
        }
    }
}
