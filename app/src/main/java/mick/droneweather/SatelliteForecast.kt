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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "satellite_forecast")
data class SatelliteForecast(
    @PrimaryKey val timestamp: Long, // Temps de la prÃƒÂ©diction
    val availableSatellites: Int,     // PrÃƒÂ©diction satellites en vue
    val lockedSatellites: Int,       // PrÃƒÂ©diction satellites verrouillables (avec Kp)
    val kpIndex: Float
)

