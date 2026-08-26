package io.r_a_d.geiravor.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("geiravor")

private val GAIN = floatPreferencesKey("gain")
private val AUTO_START_ON_PLUG = booleanPreferencesKey("auto_start_on_plug")
private val AUTO_START_IN_VEHICLE = booleanPreferencesKey("auto_start_in_vehicle")
private val FAVES_NICK = stringPreferencesKey("faves_nick")

class SettingsStore(private val context: Context) {
    val gain: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[GAIN] ?: LivePlaybackPolicy.DEFAULT_GAIN
    }

    val autoStartOnPlug: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START_ON_PLUG] ?: SettingsPolicy.AUTO_START_DEFAULT
    }

    val autoStartInVehicle: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START_IN_VEHICLE] ?: SettingsPolicy.AUTO_START_VEHICLE_DEFAULT
    }

    val favesNick: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FAVES_NICK].orEmpty()
    }

    suspend fun setGain(value: Float) {
        context.dataStore.edit { it[GAIN] = value.coerceIn(0f, 1f) }
    }

    suspend fun setAutoStartOnPlug(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START_ON_PLUG] = enabled }
    }

    suspend fun setAutoStartInVehicle(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START_IN_VEHICLE] = enabled }
    }

    suspend fun setFavesNick(nick: String) {
        context.dataStore.edit { it[FAVES_NICK] = nick.trim() }
    }
}
