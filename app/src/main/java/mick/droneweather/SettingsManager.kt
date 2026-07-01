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
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("drone_weather_prefs", Context.MODE_PRIVATE)

    fun saveString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun saveInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun saveDouble(key: String, value: Double) {
        prefs.edit { putLong(key, java.lang.Double.doubleToRawLongBits(value)) }
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return java.lang.Double.longBitsToDouble(prefs.getLong(key, java.lang.Double.doubleToRawLongBits(defaultValue)))
    }

    fun saveStringSet(key: String, value: Set<String>) {
        prefs.edit { putStringSet(key, value) }
    }

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        return prefs.getStringSet(key, defaultValue) ?: defaultValue
    }

    fun clearAll() {
        prefs.edit { clear() }
    }
}
