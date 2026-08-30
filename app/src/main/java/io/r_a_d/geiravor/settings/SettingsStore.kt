package io.r_a_d.geiravor.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.r_a_d.geiravor.playback.AlarmPolicy
import io.r_a_d.geiravor.playback.FavePolicy
import uniffi.geiravor_core.IrcProfile
import androidx.datastore.preferences.preferencesDataStore
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.radio.SnapshotPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("geiravor")

private val GAIN = floatPreferencesKey("gain")
private val AUTO_START_ON_PLUG = booleanPreferencesKey("auto_start_on_plug")
private val AUTO_START_IN_VEHICLE = booleanPreferencesKey("auto_start_in_vehicle")
private val FAVES_NICK = stringPreferencesKey("faves_nick")
private val IRC_NICK = stringPreferencesKey("irc_nick")
private val IRC_PROFILE = stringPreferencesKey("irc_profile")
private val BOUNCER_HOST = stringPreferencesKey("bouncer_host")
private val BOUNCER_PORT = intPreferencesKey("bouncer_port")
private val IRC_INSECURE_TLS = booleanPreferencesKey("irc_insecure_tls")
private val SASL_USERNAME = stringPreferencesKey("sasl_username")
private val TLS_FINGERPRINT = stringPreferencesKey("tls_fingerprint")
private val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
private val ALARM_HOUR = intPreferencesKey("alarm_hour")
private val ALARM_MINUTE = intPreferencesKey("alarm_minute")
private val SNOOZE_ENABLED = booleanPreferencesKey("snooze_enabled")
private val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
private val LAST_PAINT = stringPreferencesKey("last_paint")

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

    val ircNick: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[IRC_NICK].orEmpty()
    }

    val ircProfile: Flow<IrcProfile> = context.dataStore.data.map { prefs ->
        if (prefs[IRC_PROFILE] == "bouncer") IrcProfile.BOUNCER else IrcProfile.RIZON
    }

    val bouncerHost: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BOUNCER_HOST].orEmpty()
    }

    val bouncerPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BOUNCER_PORT] ?: FavePolicy.DEFAULT_BOUNCER_PORT
    }

    val allowInsecureTls: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IRC_INSECURE_TLS] ?: false
    }

    val saslUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SASL_USERNAME].orEmpty()
    }

    val tlsFingerprint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TLS_FINGERPRINT].orEmpty()
    }

    val alarmEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ALARM_ENABLED] ?: AlarmPolicy.ENABLED_DEFAULT
    }

    val alarmHour: Flow<Int> = context.dataStore.data.map { prefs ->
        AlarmPolicy.clampHour(prefs[ALARM_HOUR] ?: AlarmPolicy.DEFAULT_HOUR)
    }

    val alarmMinute: Flow<Int> = context.dataStore.data.map { prefs ->
        AlarmPolicy.clampMinute(prefs[ALARM_MINUTE] ?: AlarmPolicy.DEFAULT_MINUTE)
    }

    val snoozeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SNOOZE_ENABLED] ?: AlarmPolicy.SNOOZE_ENABLED_DEFAULT
    }

    val snoozeMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        AlarmPolicy.clampSnoozeMinutes(prefs[SNOOZE_MINUTES] ?: AlarmPolicy.DEFAULT_SNOOZE_MINUTES)
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

    suspend fun setIrcNick(nick: String) {
        context.dataStore.edit { it[IRC_NICK] = nick.trim() }
    }

    suspend fun setIrcProfile(profile: IrcProfile) {
        context.dataStore.edit {
            it[IRC_PROFILE] = if (profile == IrcProfile.BOUNCER) "bouncer" else "rizon"
        }
    }

    suspend fun setBouncerHost(host: String) {
        context.dataStore.edit { it[BOUNCER_HOST] = host.trim() }
    }

    suspend fun setBouncerPort(port: Int) {
        context.dataStore.edit { it[BOUNCER_PORT] = FavePolicy.sanitizePort(port) }
    }

    suspend fun setAllowInsecureTls(enabled: Boolean) {
        context.dataStore.edit { it[IRC_INSECURE_TLS] = enabled }
    }

    suspend fun setSaslUsername(username: String) {
        context.dataStore.edit { it[SASL_USERNAME] = username.trim() }
    }

    suspend fun setTlsFingerprint(fingerprint: String) {
        context.dataStore.edit { it[TLS_FINGERPRINT] = fingerprint.trim() }
    }

    suspend fun setAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ALARM_ENABLED] = enabled }
    }

    suspend fun setAlarmHour(hour: Int) {
        context.dataStore.edit { it[ALARM_HOUR] = AlarmPolicy.clampHour(hour) }
    }

    suspend fun setAlarmMinute(minute: Int) {
        context.dataStore.edit { it[ALARM_MINUTE] = AlarmPolicy.clampMinute(minute) }
    }

    suspend fun setSnoozeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SNOOZE_ENABLED] = enabled }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[SNOOZE_MINUTES] = AlarmPolicy.clampSnoozeMinutes(minutes) }
    }

    suspend fun lastPaint(): SnapshotPolicy.LastPaint? =
        SnapshotPolicy.decode(context.dataStore.data.first()[LAST_PAINT].orEmpty())

    suspend fun setLastPaint(paint: SnapshotPolicy.LastPaint) {
        context.dataStore.edit { it[LAST_PAINT] = SnapshotPolicy.encode(paint) }
    }
}
