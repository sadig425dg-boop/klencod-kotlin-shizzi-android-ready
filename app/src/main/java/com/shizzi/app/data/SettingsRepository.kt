package com.shizzi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "shizzi_settings")

enum class ShizukuMode(val id: String) {
    OFF("off"),
    INDEPENDENT("independent"),
    FOLLOW_VPN("follow_vpn");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: FOLLOW_VPN
    }
}

data class ShizziSettings(
    val vpnConnected: Boolean = false,
    val hotspotShared: Boolean = false,
    val shizukuMode: ShizukuMode = ShizukuMode.FOLLOW_VPN,
    val autoSelectFastest: Boolean = true,
    val fontFamily: String = "cairo",
    val hotspotSsid: String = "Shizzi-Hotspot",
    val selectedServerId: Long = -1,
)

/** Persists user preferences with Jetpack DataStore (replaces SharedPreferences). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val VPN_CONNECTED = booleanPreferencesKey("vpn_connected")
        val HOTSPOT_SHARED = booleanPreferencesKey("hotspot_shared")
        val SHIZUKU_MODE = stringPreferencesKey("shizuku_mode")
        val AUTO_SELECT_FASTEST = booleanPreferencesKey("auto_select_fastest")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val HOTSPOT_SSID = stringPreferencesKey("hotspot_ssid")
        val SELECTED_SERVER_ID = intPreferencesKey("selected_server_id")
    }

    val settingsFlow: Flow<ShizziSettings> = context.dataStore.data.map { prefs ->
        ShizziSettings(
            vpnConnected = prefs[Keys.VPN_CONNECTED] ?: false,
            hotspotShared = prefs[Keys.HOTSPOT_SHARED] ?: false,
            shizukuMode = ShizukuMode.fromId(prefs[Keys.SHIZUKU_MODE] ?: ShizukuMode.FOLLOW_VPN.id),
            autoSelectFastest = prefs[Keys.AUTO_SELECT_FASTEST] ?: true,
            fontFamily = prefs[Keys.FONT_FAMILY] ?: "cairo",
            hotspotSsid = prefs[Keys.HOTSPOT_SSID] ?: "Shizzi-Hotspot",
            selectedServerId = (prefs[Keys.SELECTED_SERVER_ID] ?: -1).toLong(),
        )
    }

    suspend fun setVpnConnected(value: Boolean) {
        context.dataStore.edit { it[Keys.VPN_CONNECTED] = value }
    }

    suspend fun setHotspotShared(value: Boolean) {
        context.dataStore.edit { it[Keys.HOTSPOT_SHARED] = value }
    }

    suspend fun setShizukuMode(mode: ShizukuMode) {
        context.dataStore.edit { it[Keys.SHIZUKU_MODE] = mode.id }
    }

    suspend fun setAutoSelectFastest(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SELECT_FASTEST] = value }
    }

    suspend fun setFontFamily(value: String) {
        context.dataStore.edit { it[Keys.FONT_FAMILY] = value }
    }

    suspend fun setHotspotSsid(value: String) {
        context.dataStore.edit { it[Keys.HOTSPOT_SSID] = value }
    }

    suspend fun setSelectedServerId(id: Long) {
        context.dataStore.edit { it[Keys.SELECTED_SERVER_ID] = id.toInt() }
    }
}
