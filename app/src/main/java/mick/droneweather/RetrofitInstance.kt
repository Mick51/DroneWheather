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

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val WEATHER_URL = "https://api.open-meteo.com/v1/"
    private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/"
    private const val NOAA_URL = "https://services.swpc.noaa.gov/"
    private const val GFZ_URL = "https://kp.gfz-potsdam.de/"

    val api: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WEATHER_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    val geocodingApi: GeocodingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GEOCODING_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApiService::class.java)
    }

    val kpApi: KpApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NOAA_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KpApiService::class.java)
    }

    val gfzApi: GfzApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GFZ_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GfzApiService::class.java)
    }
}

