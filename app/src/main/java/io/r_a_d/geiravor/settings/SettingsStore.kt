package io.r_a_d.geiravor.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("geiravor")

private val GAIN = floatPreferencesKey("gain")

class SettingsStore(private val context: Context) {
    val gain: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[GAIN] ?: LivePlaybackPolicy.DEFAULT_GAIN
    }

    suspend fun setGain(value: Float) {
        context.dataStore.edit { it[GAIN] = value.coerceIn(0f, 1f) }
    }
}
