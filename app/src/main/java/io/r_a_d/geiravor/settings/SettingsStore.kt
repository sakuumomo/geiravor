package io.r_a_d.geiravor.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.r_a_d.geiravor.playback.AlarmPolicy
import io.r_a_d.geiravor.playback.FavePolicy
import io.r_a_d.geiravor.playback.DjNotifierPolicy
import io.r_a_d.geiravor.playback.SleepPolicy
import uniffi.geiravor_core.IrcProfile
import androidx.datastore.preferences.preferencesDataStore
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.radio.SnapshotPolicy
import io.r_a_d.geiravor.ui.SchedulePolicy
import io.r_a_d.geiravor.ui.ThemePolicy
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
private val SLEEP_ENABLED = booleanPreferencesKey("sleep_enabled")
private val SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
private val SLEEP_ENDS_AT = longPreferencesKey("sleep_ends_at")
private val DJ_NOTIFIER = booleanPreferencesKey("dj_notifier")
private val CONVERT_SCHEDULE_TIMES = booleanPreferencesKey("convert_schedule_times")
private val THEME_PACK = stringPreferencesKey("theme_pack")
private val HOLIDAY_OPT_OUT = booleanPreferencesKey("holiday_opt_out")
private val THEME_SEEN_ON = stringPreferencesKey("theme_seen_on")
private val THEME_LAST_SNIFF = longPreferencesKey("theme_last_sniff")
private val DJ_SEEN_SET = booleanPreferencesKey("dj_seen_set")
private val DJ_SEEN_AFK = booleanPreferencesKey("dj_seen_afk")
private val DJ_SEEN_ID = longPreferencesKey("dj_seen_id")
private val DJ_SEEN_NAME = stringPreferencesKey("dj_seen_name")
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

    val sleepEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SLEEP_ENABLED] ?: SleepPolicy.ENABLED_DEFAULT
    }

    val sleepMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        SleepPolicy.clampMinutes(prefs[SLEEP_MINUTES] ?: SleepPolicy.DEFAULT_MINUTES)
    }

    val sleepEndsAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[SLEEP_ENDS_AT] ?: 0L
    }

    val djNotifierEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DJ_NOTIFIER] ?: DjNotifierPolicy.ENABLED_DEFAULT
    }

    val convertScheduleTimes: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CONVERT_SCHEDULE_TIMES] ?: SchedulePolicy.CONVERT_DEFAULT
    }

    val themePack: Flow<String> = context.dataStore.data.map { prefs ->
        ThemePolicy.clampUserPick(prefs[THEME_PACK] ?: ThemePolicy.USER_DEFAULT)
    }

    val holidayOptOut: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HOLIDAY_OPT_OUT] ?: ThemePolicy.OPT_OUT_DEFAULT
    }

    val themeSeenOn: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_SEEN_ON].orEmpty()
    }

    val themeLastSniff: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[THEME_LAST_SNIFF] ?: 0L
    }

    suspend fun djSeen(): DjNotifierPolicy.Seen? {
        val prefs = context.dataStore.data.first()
        if (prefs[DJ_SEEN_SET] != true) {
            return null
        }
        return DjNotifierPolicy.Seen(
            isAfk = prefs[DJ_SEEN_AFK] ?: true,
            djId = prefs[DJ_SEEN_ID] ?: 0L,
            djName = prefs[DJ_SEEN_NAME].orEmpty(),
        )
    }

    suspend fun setDjNotifierEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DJ_NOTIFIER] = enabled }
    }

    suspend fun setDjSeen(seen: DjNotifierPolicy.Seen) {
        context.dataStore.edit { prefs ->
            prefs[DJ_SEEN_SET] = true
            prefs[DJ_SEEN_AFK] = seen.isAfk
            prefs[DJ_SEEN_ID] = seen.djId
            prefs[DJ_SEEN_NAME] = seen.djName
        }
    }

    suspend fun setGain(value: Float) {
        context.dataStore.edit { it[GAIN] = value.coerceIn(0f, 1f) }
    }

    suspend fun setAutoStartOnPlug(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START_ON_PLUG] = enabled }
    }

    suspend fun setConvertScheduleTimes(enabled: Boolean) {
        context.dataStore.edit { it[CONVERT_SCHEDULE_TIMES] = enabled }
    }

    suspend fun setThemePack(id: String) {
        context.dataStore.edit { it[THEME_PACK] = ThemePolicy.clampUserPick(id) }
    }

    suspend fun setHolidayOptOut(enabled: Boolean) {
        context.dataStore.edit { it[HOLIDAY_OPT_OUT] = enabled }
    }

    suspend fun setThemeSeenOn(window: String) {
        context.dataStore.edit { it[THEME_SEEN_ON] = window }
    }

    suspend fun setThemeLastSniff(epochMillis: Long) {
        context.dataStore.edit { it[THEME_LAST_SNIFF] = epochMillis }
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

    suspend fun armSleep(minutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        val duration = SleepPolicy.clampMinutes(minutes)
        context.dataStore.edit { prefs ->
            prefs[SLEEP_ENABLED] = true
            prefs[SLEEP_MINUTES] = duration
            prefs[SLEEP_ENDS_AT] = SleepPolicy.endsAtMillis(nowMillis, duration)
        }
    }

    suspend fun clearSleep() {
        context.dataStore.edit { prefs ->
            prefs[SLEEP_ENABLED] = false
            prefs[SLEEP_ENDS_AT] = 0L
        }
    }

    suspend fun setSleepMinutes(minutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        val duration = SleepPolicy.clampMinutes(minutes)
        context.dataStore.edit { prefs ->
            prefs[SLEEP_MINUTES] = duration
            if (prefs[SLEEP_ENABLED] == true) {
                prefs[SLEEP_ENDS_AT] = SleepPolicy.endsAtMillis(nowMillis, duration)
            }
        }
    }

    suspend fun lastPaint(): SnapshotPolicy.LastPaint? =
        SnapshotPolicy.decode(context.dataStore.data.first()[LAST_PAINT].orEmpty())

    suspend fun setLastPaint(paint: SnapshotPolicy.LastPaint) {
        context.dataStore.edit { it[LAST_PAINT] = SnapshotPolicy.encode(paint) }
    }
}
