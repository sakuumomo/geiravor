package io.r_a_d.geiravor.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.r_a_d.geiravor.settings.SecretsStore
import uniffi.geiravor_core.FaveConfig
import uniffi.geiravor_core.FaveRow
import uniffi.geiravor_core.IrcProfile
import uniffi.geiravor_core.NewsArticle
import uniffi.geiravor_core.NewsList
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.ScheduleDay
import uniffi.geiravor_core.SearchPage
import uniffi.geiravor_core.StaffGroup
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.ThemePack
import uniffi.geiravor_core.decideTheme
import uniffi.geiravor_core.holidayWindow
import uniffi.geiravor_core.shouldSniffHome
import uniffi.geiravor_core.sniffedMatchesWindow
import uniffi.geiravor_core.themePackFromPref
import uniffi.geiravor_core.themePackPref
import java.util.Calendar
import java.util.concurrent.Executors

class UiState {
    var status by mutableStateOf<Status?>(null)
    var playing by mutableStateOf(false)
    var streamDown by mutableStateOf(false)
    var fetchedAt by mutableLongStateOf(0L)
    var pack by mutableStateOf(ThemePack.DEFAULT_DARK)
    var userPick by mutableStateOf(ThemePack.DEFAULT_DARK)
    var holidayOptOut by mutableStateOf(false)
    var gain by mutableFloatStateOf(0.8f)
    var lastGain by mutableFloatStateOf(0.8f)
    var autoStartPlug by mutableStateOf(false)
    var autoStartVehicle by mutableStateOf(false)
    var listNick by mutableStateOf("")
    var nick by mutableStateOf("")
    var profile by mutableStateOf(IrcProfile.RIZON)
    var bouncerHost by mutableStateOf("")
    var bouncerPort by mutableStateOf("6697")
    var allowInsecure by mutableStateOf(false)
    var tlsFingerprint by mutableStateOf("")
    var saslUser by mutableStateOf("")
    var scheduleLocal by mutableStateOf(false)
    var djNotifier by mutableStateOf(false)
    var favePlaying by mutableStateOf(false)
    var alarmOn by mutableStateOf(false)
    var alarmHour by mutableStateOf("7")
    var alarmMinute by mutableStateOf("0")
    var alarm24h by mutableStateOf(false)
    var snoozeOn by mutableStateOf(true)
    var snoozeHours by mutableStateOf("0")
    var snoozeMinutes by mutableStateOf("10")
    var sleepOn by mutableStateOf(false)
    var sleepHours by mutableStateOf("0")
    var sleepMinutes by mutableStateOf("30")
    var alertError by mutableStateOf<String?>(null)
    var probeText by mutableStateOf<String?>(null)
    var heartFilled by mutableStateOf(false)
    var faveBusy by mutableStateOf(false)
    var faveTaps = 0
    val faveQueue = ArrayDeque<FaveTap.Job>()
    var faveError by mutableStateOf<String?>(null)
    var faveErrorFading by mutableStateOf(false)
    var tab by mutableStateOf(BottomTab.NowPlaying)
    var songsSection by mutableStateOf(SongsSection.LastPlayed)
    var boardSection by mutableStateOf(BoardSection.News)
    var settingsSection by mutableStateOf(SettingsSection.General)
    var tagsOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var search by mutableStateOf<SearchPage?>(null)
    var requestText by mutableStateOf<String?>(null)
    var canRequest by mutableStateOf(true)
    var faveRows by mutableStateOf<List<FaveRow>>(emptyList())
    var favePage by mutableStateOf(1u)
    var faveLast by mutableStateOf(1u)
    var news by mutableStateOf<NewsList?>(null)
    var newsPage by mutableStateOf(1u)
    var newsFit by mutableStateOf(0u)
    var searchFit by mutableStateOf(0u)
    var faveFit by mutableStateOf(0u)
    var article by mutableStateOf<NewsArticle?>(null)
    var newsCoilUrls by mutableStateOf<List<String>>(emptyList())
    var schedule by mutableStateOf<List<ScheduleDay>>(emptyList())
    var staff by mutableStateOf<List<StaffGroup>>(emptyList())

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "geiravor-ui").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun load(core: RadioCore, secrets: SecretsStore) {
        worker.execute {
            fun flag(key: String) = core.pref(key) == "1"
            val pick = themePackFromPref(core.pref(Prefs.THEME))
            val opt = flag(Prefs.HOLIDAY_OPT_OUT)
            val g = core.pref(Prefs.GAIN).toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.8f
            val cal = Calendar.getInstance()
            val month = (cal.get(Calendar.MONTH) + 1).toUByte()
            val day = cal.get(Calendar.DAY_OF_MONTH).toUByte()
            val sniffed = sniffName(core, opt, month, day, processStart = true)
            val decided = decideTheme(pick, opt, month, day, sniffed)
            val snap = core.snapshot()
            val plug = flag(Prefs.AUTOSTART_PLUG)
            val vehicle = flag(Prefs.AUTOSTART_VEHICLE)
            val list = core.pref(Prefs.LIST_NICK)
            val n = core.pref(Prefs.NICK)
            val prof = if (core.pref(Prefs.PROFILE) == "bouncer") IrcProfile.BOUNCER else IrcProfile.RIZON
            val host = core.pref(Prefs.BOUNCER_HOST)
            val port = core.pref(Prefs.BOUNCER_PORT)
            val insecure = flag(Prefs.ALLOW_INSECURE)
            val fp = core.pref(Prefs.TLS_FP)
            val sasl = core.pref(Prefs.SASL_USER)
            val local = flag(Prefs.SCHEDULE_LOCAL)
            val djN = flag(Prefs.DJ_NOTIFIER)
            val faveP = flag(Prefs.FAVE_PLAYING)
            val alarm = flag(Prefs.ALARM_ON)
            val ah = core.pref(Prefs.ALARM_HOUR).ifBlank { "7" }
            val amn = core.pref(Prefs.ALARM_MINUTE).ifBlank { "0" }
            val a24 = flag(Prefs.ALARM_24H)
            val snOn = core.pref(Prefs.SNOOZE_ON).let { it.isEmpty() || it == "1" }
            val snHr = core.pref(Prefs.SNOOZE_HOURS).ifBlank { "0" }
            val snMin = core.pref(Prefs.SNOOZE_MINUTES).ifBlank { "10" }
            val slOn = flag(Prefs.SLEEP_ON)
            val slHr = core.pref(Prefs.SLEEP_HOURS).ifBlank { "0" }
            val slMin = core.pref(Prefs.SLEEP_MINUTES).ifBlank { "30" }
            secrets.get(SecretKeys.NICKSERV)
            worker.execute {
                listOf(list, n)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { nick ->
                        runCatching { core.revalidateMembership(nick) }
                    }
            }
            main.post {
                userPick = pick
                holidayOptOut = opt
                pack = decided
                gain = g
                lastGain = if (g > 0f) g else 0.8f
                autoStartPlug = plug
                autoStartVehicle = vehicle
                listNick = list
                nick = n
                profile = prof
                bouncerHost = host
                bouncerPort = port
                allowInsecure = insecure
                tlsFingerprint = fp
                saslUser = sasl
                scheduleLocal = local
                djNotifier = djN
                favePlaying = faveP
                alarmOn = alarm
                alarmHour = ah
                alarmMinute = amn
                alarm24h = a24
                snoozeOn = snOn
                snoozeHours = snHr
                snoozeMinutes = snMin
                sleepOn = slOn
                sleepHours = slHr
                sleepMinutes = slMin
                applyStatus(snap, streamDown, playing)
            }
        }
    }

    fun applyStatus(next: Status?, down: Boolean, isPlaying: Boolean) {
        if (FaveError.shouldFade(faveError, status?.np, next?.np)) {
            faveErrorFading = true
        }
        status = next
        streamDown = down
        playing = isPlaying
        fetchedAt = System.currentTimeMillis() / 1000
    }

    fun setPref(core: RadioCore, key: String, value: String) {
        worker.execute { runCatching { core.setPref(key, value) } }
    }

    fun setFlag(core: RadioCore, key: String, on: Boolean) {
        setPref(core, key, if (on) "1" else "0")
    }

    fun setThemePick(core: RadioCore, pick: ThemePack) {
        userPick = pick
        setPref(core, Prefs.THEME, themePackPref(pick))
        redecide(core)
    }

    fun setHolidayOptOut(core: RadioCore, out: Boolean) {
        holidayOptOut = out
        setFlag(core, Prefs.HOLIDAY_OPT_OUT, out)
        redecide(core)
    }

    fun faveConfig(secrets: SecretsStore): FaveConfig {
        val port = bouncerPort.toUShortOrNull() ?: 0u
        return FaveConfig(
            nick = nick,
            listNick = listNick,
            profile = profile,
            nickservPassword = secrets.get(SecretKeys.NICKSERV),
            bouncerHost = bouncerHost,
            bouncerPort = port,
            bouncerPass = secrets.get(SecretKeys.BOUNCER_PASS),
            allowInsecureTls = allowInsecure,
            saslUsername = saslUser,
            saslPassword = secrets.get(SecretKeys.SASL_PASSWORD),
            clientCertPem = secrets.get(SecretKeys.CLIENT_CERT),
            clientKeyPem = secrets.get(SecretKeys.CLIENT_KEY),
            tlsFingerprint = tlsFingerprint,
        )
    }

    fun offMain(block: () -> Unit) {
        worker.execute {
            runCatching(block)
        }
    }

    fun onMain(block: () -> Unit) {
        main.post(block)
    }

    fun listNickOrConnection(): String =
        listNick.trim().ifEmpty { nick.trim() }

    fun membershipNicks(): List<String> {
        val out = ArrayList<String>(2)
        val list = listNick.trim()
        val connection = nick.trim()
        if (list.isNotEmpty()) out.add(list)
        if (connection.isNotEmpty() && connection != list) out.add(connection)
        return out
    }

    private fun redecide(core: RadioCore) {
        worker.execute {
            val cal = Calendar.getInstance()
            val month = (cal.get(Calendar.MONTH) + 1).toUByte()
            val day = cal.get(Calendar.DAY_OF_MONTH).toUByte()
            val sniffed = sniffName(core, holidayOptOut, month, day, processStart = false)
            val decided = decideTheme(userPick, holidayOptOut, month, day, sniffed)
            main.post { pack = decided }
        }
    }

    private fun sniffName(
        core: RadioCore,
        optOut: Boolean,
        month: UByte,
        day: UByte,
        processStart: Boolean,
    ): String? {
        if (optOut || holidayWindow(month, day) == null) return null
        val cached = runCatching { core.cachedThemeName() }.getOrNull()
        if (cached != null && sniffedMatchesWindow(month, day, cached)) {
            core.setPref(Prefs.SNIFF_SEEN, cached)
            if (!processStart) return cached
        }
        val now = System.currentTimeMillis() / 1000
        val last = core.pref(Prefs.SNIFF_AT).toLongOrNull() ?: 0L
        val seen = holidayWindow(month, day)?.let { themePackPref(it) } == core.pref(Prefs.SNIFF_SEEN)
        val getHome = shouldSniffHome(optOut, month, day, now, last, seen, processStart)
        if (!getHome) return cached
        val live = runCatching { core.sniffTheme() }.getOrNull()
        core.setPref(Prefs.SNIFF_AT, now.toString())
        val name = live ?: cached
        if (name != null && sniffedMatchesWindow(month, day, name)) {
            core.setPref(Prefs.SNIFF_SEEN, name)
        }
        return name
    }
}
