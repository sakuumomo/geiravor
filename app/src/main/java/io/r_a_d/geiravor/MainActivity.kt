package io.r_a_d.geiravor

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.compat.ExactAlarms
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.compat.applyEdgeToEdge
import io.r_a_d.geiravor.playback.AlarmPolicy
import io.r_a_d.geiravor.playback.AlarmScheduler
import io.r_a_d.geiravor.playback.FavePolicy
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.playback.PlaybackService
import io.r_a_d.geiravor.playback.DjNotifier
import io.r_a_d.geiravor.playback.DjNotifierPolicy
import io.r_a_d.geiravor.playback.SleepPolicy
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.settings.SecretsStore
import io.r_a_d.geiravor.settings.SettingsPolicy
import io.r_a_d.geiravor.settings.SettingsStore
import uniffi.geiravor_core.IrcProfile
import io.r_a_d.geiravor.ui.AppLayout
import io.r_a_d.geiravor.ui.AppTab
import io.r_a_d.geiravor.ui.FavoritesPolicy
import io.r_a_d.geiravor.ui.NewsScreen
import io.r_a_d.geiravor.ui.NowPlayingScreen
import io.r_a_d.geiravor.ui.RadioTheme
import io.r_a_d.geiravor.ui.SettingsScreen
import io.r_a_d.geiravor.ui.SongsScreen
import io.r_a_d.geiravor.ui.TabLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge()
        setContent { GeiravorRoot() }
    }
}

@Composable
private fun GeiravorRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as GeiravorApp
    val settings = remember { SettingsStore(context.applicationContext) }
    val secrets = remember { SecretsStore(context.applicationContext) }
    val radioState by RadioStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var gain by remember { mutableStateOf(LivePlaybackPolicy.DEFAULT_GAIN) }
    var autoStartOnPlug by remember { mutableStateOf(SettingsPolicy.AUTO_START_DEFAULT) }
    var autoStartInVehicle by remember { mutableStateOf(SettingsPolicy.AUTO_START_VEHICLE_DEFAULT) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    var favesNick by remember { mutableStateOf("") }
    var homeNick by remember { mutableStateOf("") }
    var ircNick by remember { mutableStateOf("") }
    var ircProfile by remember { mutableStateOf(IrcProfile.RIZON) }
    var nickservPassword by remember { mutableStateOf(secrets.nickservPassword()) }
    var bouncerHost by remember { mutableStateOf("") }
    var bouncerPort by remember { mutableStateOf(FavePolicy.DEFAULT_BOUNCER_PORT.toString()) }
    var bouncerPass by remember { mutableStateOf(secrets.bouncerPass()) }
    var allowInsecureTls by remember { mutableStateOf(false) }
    var saslUsername by remember { mutableStateOf("") }
    var saslPassword by remember { mutableStateOf(secrets.saslPassword()) }
    var clientCertPem by remember { mutableStateOf(secrets.clientCertPem()) }
    var clientKeyPem by remember { mutableStateOf(secrets.clientKeyPem()) }
    var tlsFingerprint by remember { mutableStateOf("") }
    var faveBusy by remember { mutableStateOf(false) }
    var probeBusy by remember { mutableStateOf(false) }
    var probeMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var alarmEnabled by remember { mutableStateOf(AlarmPolicy.ENABLED_DEFAULT) }
    var alarmHour by remember { mutableStateOf(AlarmPolicy.DEFAULT_HOUR) }
    var alarmMinute by remember { mutableStateOf(AlarmPolicy.DEFAULT_MINUTE) }
    var snoozeEnabled by remember { mutableStateOf(AlarmPolicy.SNOOZE_ENABLED_DEFAULT) }
    var snoozeMinutes by remember { mutableStateOf(AlarmPolicy.DEFAULT_SNOOZE_MINUTES) }
    var sleepEnabled by remember { mutableStateOf(SleepPolicy.ENABLED_DEFAULT) }
    var sleepMinutes by remember { mutableStateOf(SleepPolicy.DEFAULT_MINUTES) }
    var djNotifierEnabled by remember { mutableStateOf(DjNotifierPolicy.ENABLED_DEFAULT) }
    var exactAlarmOk by remember { mutableStateOf(ExactAlarms.canSchedule(context)) }
    var notifyOk by remember { mutableStateOf(Notifications.granted(context)) }

    LaunchedEffect(Unit) {
        settings.gain.collect { stored ->
            if (!sliding) {
                gain = stored
                controller?.volume = stored
            }
        }
    }
    LaunchedEffect(Unit) {
        settings.autoStartOnPlug.collect { autoStartOnPlug = it }
    }
    LaunchedEffect(Unit) {
        settings.autoStartInVehicle.collect { autoStartInVehicle = it }
    }
    LaunchedEffect(Unit) {
        val allowed = withContext(Dispatchers.IO) {
            runCatching { app.radio.canRequest() }.getOrNull()
        }
        RadioStore.setCanRequest(allowed)
    }
    LaunchedEffect(Unit) {
        settings.favesNick.collect { stored ->
            homeNick = stored
        }
    }
    LaunchedEffect(Unit) {
        val nick = settings.favesNick.first()
        favesNick = nick
        homeNick = nick
        if (FavoritesPolicy.shouldFetch(nick)) {
            withContext(Dispatchers.IO) {
                runCatching { app.radio.prefetchFavorites(nick) }
            }
        }
    }
    LaunchedEffect(Unit) {
        settings.ircNick.collect { ircNick = it }
    }
    LaunchedEffect(Unit) {
        settings.ircProfile.collect { ircProfile = it }
    }
    LaunchedEffect(Unit) {
        settings.bouncerHost.collect { bouncerHost = it }
    }
    LaunchedEffect(Unit) {
        settings.bouncerPort.collect { bouncerPort = it.toString() }
    }
    LaunchedEffect(Unit) {
        settings.allowInsecureTls.collect { allowInsecureTls = it }
    }
    LaunchedEffect(Unit) {
        settings.saslUsername.collect { saslUsername = it }
    }
    LaunchedEffect(Unit) {
        settings.tlsFingerprint.collect { tlsFingerprint = it }
    }
    LaunchedEffect(Unit) {
        settings.alarmEnabled.collect { alarmEnabled = it }
    }
    LaunchedEffect(Unit) {
        settings.alarmHour.collect { alarmHour = it }
    }
    LaunchedEffect(Unit) {
        settings.alarmMinute.collect { alarmMinute = it }
    }
    LaunchedEffect(Unit) {
        settings.snoozeEnabled.collect { snoozeEnabled = it }
    }
    LaunchedEffect(Unit) {
        settings.snoozeMinutes.collect { snoozeMinutes = it }
    }
    LaunchedEffect(Unit) {
        settings.sleepEnabled.collect { sleepEnabled = it }
    }
    LaunchedEffect(Unit) {
        settings.sleepMinutes.collect { sleepMinutes = it }
    }
    LaunchedEffect(Unit) {
        settings.djNotifierEnabled.collect { djNotifierEnabled = it }
    }
    LaunchedEffect(homeNick, radioState.status?.trackId, radioState.status?.np) {
        val status = radioState.status
        val home = FavePolicy.listNick(homeNick)
        val filled = withContext(Dispatchers.IO) {
            FavePolicy.isListed(home, status, app.radio::isFavorite)
        }
        RadioStore.setHeart(filled)
    }

    LifecycleResumeEffect(app) {
        app.radio.setUiVisible(true)
        exactAlarmOk = ExactAlarms.canSchedule(context)
        notifyOk = Notifications.granted(context)
        if (alarmEnabled) {
            AlarmScheduler.scheduleDaily(context, true, alarmHour, alarmMinute)
        }
        onPauseOrDispose { app.radio.setUiVisible(false) }
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val c = future.get()
                controller = c
                c.volume = gain
                fun syncPlaying() {
                    playing = LivePlaybackPolicy.showAsPlaying(
                        c.playbackState,
                        c.isPlaying,
                        c.playWhenReady,
                    )
                }
                syncPlaying()
                c.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            syncPlaying()
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            syncPlaying()
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            syncPlaying()
                        }
                    },
                )
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val onGain: (Float) -> Unit = { value ->
        controller?.volume = value
    }
    val onGainFinished: (Float) -> Unit = { value ->
        gain = value
        controller?.volume = value
        scope.launch { settings.setGain(value) }
    }
    val onPlayToggle: () -> Unit = {
        if (playing) {
            playing = false
            when (LivePlaybackPolicy.leavePlaybackCommand()) {
                LivePlaybackPolicy.Command.PAUSE -> controller?.pause()
                LivePlaybackPolicy.Command.STOP -> controller?.stop()
                LivePlaybackPolicy.Command.PLAY -> controller?.play()
            }
        } else {
            controller?.let { c ->
                playing = true
                c.play()
                if (Notifications.shouldRequest(Notifications.needed, Notifications.granted(context))) {
                    permission.launch(Notifications.permission())
                }
            }
        }
    }
    val onFave: () -> Unit = {
        if (!faveBusy) {
            faveBusy = true
            scope.launch {
                val (result, wasFilled) = withContext(Dispatchers.IO) {
                    val home = FavePolicy.listNick(homeNick)
                    val listed = FavePolicy.isListed(
                        home,
                        app.radio.snapshot(),
                        app.radio::isFavorite,
                    )
                    val done = app.radio.addFave(
                        FavePolicy.config(
                            nick = FavePolicy.ircNick(ircNick, homeNick),
                            listNick = home,
                            profile = ircProfile,
                            nickservPassword = nickservPassword,
                            bouncerHost = bouncerHost,
                            bouncerPort = bouncerPort.toIntOrNull() ?: 0,
                            bouncerPass = bouncerPass,
                            allowInsecureTls = allowInsecureTls,
                            saslUsername = saslUsername,
                            saslPassword = saslPassword,
                            clientCertPem = clientCertPem,
                            clientKeyPem = clientKeyPem,
                            tlsFingerprint = tlsFingerprint,
                        ),
                    )
                    done to listed
                }
                faveBusy = false
                val heart = FavePolicy.heartUpdate(wasFilled, result)
                RadioStore.setHeart(
                    filled = heart.filled,
                    notice = heart.notice,
                    replaceNotice = true,
                    bumpList = heart.bumpList,
                )
                if (heart.bumpList) {
                    val home = FavePolicy.listNick(homeNick)
                    withContext(Dispatchers.IO) {
                        runCatching { app.radio.prefetchFavorites(home) }
                    }
                    app.persistHomeFaves(home)
                }
            }
        }
    }
    var tab by remember { mutableStateOf(AppTab.NowPlaying) }
    val configuration = LocalConfiguration.current
    val twoPane = AppLayout.twoPane(configuration.screenWidthDp, configuration.smallestScreenWidthDp)
    val shown = AppLayout.clampTab(tab, twoPane)

    Surface(modifier = Modifier.fillMaxSize(), color = RadioTheme.background) {
        Scaffold(
            modifier = Modifier.systemBarsPadding(),
            containerColor = RadioTheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = RadioTheme.surface) {
                    AppLayout.tabs(twoPane).forEach { dest ->
                        NavigationBarItem(
                            selected = shown == dest,
                            onClick = { tab = dest },
                            icon = { Text(dest.icon) },
                            label = { TabLabel(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RadioTheme.blue,
                                selectedTextColor = RadioTheme.text,
                                indicatorColor = RadioTheme.border,
                                unselectedIconColor = RadioTheme.muted,
                                unselectedTextColor = RadioTheme.muted,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            val paneModifier = Modifier.padding(padding)
            val nowPlaying: @Composable (Modifier) -> Unit = { modifier ->
                NowPlayingScreen(
                    radio = app.radio,
                    status = radioState.status,
                    streamDown = radioState.streamDown,
                    playing = playing,
                    gain = gain,
                    onGain = onGain,
                    onGainFinished = onGainFinished,
                    onSliding = { sliding = it },
                    onPlayToggle = onPlayToggle,
                    onFave = onFave,
                    faveBusy = faveBusy,
                    faveFilled = radioState.heartFilled,
                    faveMessage = radioState.faveNotice,
                    showThread = !twoPane,
                    modifier = modifier,
                )
            }
            val songs: @Composable (Modifier) -> Unit = { modifier ->
                SongsScreen(
                    radio = app.radio,
                    status = radioState.status,
                    streamDown = radioState.streamDown,
                    canRequest = radioState.canRequest,
                    onCanRequest = RadioStore::setCanRequest,
                    favesNick = favesNick,
                    homeNick = homeNick,
                    onFavesNick = { favesNick = it },
                    onFavesNickPersist = { nick ->
                        scope.launch {
                            settings.setFavesNick(nick)
                            val home = FavePolicy.listNick(nick)
                            withContext(Dispatchers.IO) {
                                app.radio.keepMembership(home)
                                if (home.isNotEmpty()) {
                                    runCatching { app.radio.prefetchFavorites(home) }
                                }
                            }
                            app.persistHomeFaves(home)
                        }
                    },
                    modifier = modifier,
                )
            }
            val news: @Composable (Modifier) -> Unit = { modifier ->
                NewsScreen(radio = app.radio, modifier = modifier)
            }
            val settings: @Composable (Modifier) -> Unit = { modifier ->
                SettingsScreen(
                    autoStartOnPlug = autoStartOnPlug,
                    onAutoStartOnPlug = { enabled ->
                        autoStartOnPlug = enabled
                        scope.launch { settings.setAutoStartOnPlug(enabled) }
                    },
                    autoStartInVehicle = autoStartInVehicle,
                    onAutoStartInVehicle = { enabled ->
                        autoStartInVehicle = enabled
                        scope.launch { settings.setAutoStartInVehicle(enabled) }
                    },
                    ircNick = ircNick,
                    onIrcNick = { value ->
                        ircNick = value
                        scope.launch { settings.setIrcNick(value) }
                    },
                    ircProfile = ircProfile,
                    onIrcProfile = { profile ->
                        ircProfile = profile
                        scope.launch { settings.setIrcProfile(profile) }
                    },
                    nickservPassword = nickservPassword,
                    onNickservPassword = { value ->
                        nickservPassword = value
                        secrets.setNickservPassword(value)
                    },
                    bouncerHost = bouncerHost,
                    onBouncerHost = { value ->
                        bouncerHost = value
                        scope.launch { settings.setBouncerHost(value) }
                    },
                    bouncerPort = bouncerPort,
                    onBouncerPort = { value ->
                        bouncerPort = value
                        scope.launch {
                            settings.setBouncerPort(value.toIntOrNull() ?: 0)
                        }
                    },
                    bouncerPass = bouncerPass,
                    onBouncerPass = { value ->
                        bouncerPass = value
                        secrets.setBouncerPass(value)
                    },
                    allowInsecureTls = allowInsecureTls,
                    onAllowInsecureTls = { enabled ->
                        allowInsecureTls = enabled
                        scope.launch { settings.setAllowInsecureTls(enabled) }
                    },
                    saslUsername = saslUsername,
                    onSaslUsername = { value ->
                        saslUsername = value
                        scope.launch { settings.setSaslUsername(value) }
                    },
                    saslPassword = saslPassword,
                    onSaslPassword = { value ->
                        saslPassword = value
                        secrets.setSaslPassword(value)
                    },
                    clientCertPem = clientCertPem,
                    onClientCertPem = { value ->
                        clientCertPem = value
                        secrets.setClientCertPem(value)
                    },
                    clientKeyPem = clientKeyPem,
                    onClientKeyPem = { value ->
                        clientKeyPem = value
                        secrets.setClientKeyPem(value)
                    },
                    tlsFingerprint = tlsFingerprint,
                    onTlsFingerprint = { value ->
                        tlsFingerprint = value
                        scope.launch { settings.setTlsFingerprint(value) }
                    },
                    onTestConnection = {
                        if (!probeBusy) {
                            probeBusy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    app.radio.probeIrc(
                                        FavePolicy.config(
                                            nick = FavePolicy.ircNick(ircNick, homeNick),
                                            profile = ircProfile,
                                            nickservPassword = nickservPassword,
                                            bouncerHost = bouncerHost,
                                            bouncerPort = bouncerPort.toIntOrNull() ?: 0,
                                            bouncerPass = bouncerPass,
                                            allowInsecureTls = allowInsecureTls,
                                            saslUsername = saslUsername,
                                            saslPassword = saslPassword,
                                            clientCertPem = clientCertPem,
                                            clientKeyPem = clientKeyPem,
                                            tlsFingerprint = tlsFingerprint,
                                        ),
                                    )
                                }
                                probeBusy = false
                                probeMessage = FavePolicy.probeMessage(result)
                            }
                        }
                    },
                    testBusy = probeBusy,
                    testMessage = probeMessage,
                    versionName = BuildConfig.VERSION_NAME,
                    alarmEnabled = alarmEnabled,
                    onAlarmEnabled = { enabled ->
                        alarmEnabled = enabled
                        scope.launch {
                            settings.setAlarmEnabled(enabled)
                            AlarmScheduler.scheduleDaily(
                                context,
                                enabled,
                                alarmHour,
                                alarmMinute,
                            )
                            exactAlarmOk = ExactAlarms.canSchedule(context)
                        }
                    },
                    alarmHour = alarmHour,
                    alarmMinute = alarmMinute,
                    onAlarmTime = { hour, minute ->
                        alarmHour = hour
                        alarmMinute = minute
                        scope.launch {
                            settings.setAlarmHour(hour)
                            settings.setAlarmMinute(minute)
                            AlarmScheduler.scheduleDaily(
                                context,
                                alarmEnabled,
                                hour,
                                minute,
                            )
                        }
                    },
                    snoozeEnabled = snoozeEnabled,
                    onSnoozeEnabled = { enabled ->
                        snoozeEnabled = enabled
                        scope.launch { settings.setSnoozeEnabled(enabled) }
                    },
                    snoozeMinutes = snoozeMinutes,
                    onSnoozeMinutes = { minutes ->
                        snoozeMinutes = minutes
                        scope.launch { settings.setSnoozeMinutes(minutes) }
                    },
                    sleepEnabled = sleepEnabled,
                    onSleepEnabled = { enabled ->
                        sleepEnabled = enabled
                        scope.launch {
                            if (enabled) {
                                settings.armSleep(sleepMinutes)
                            } else {
                                settings.clearSleep()
                            }
                        }
                    },
                    sleepMinutes = sleepMinutes,
                    onSleepMinutes = { minutes ->
                        sleepMinutes = minutes
                        scope.launch { settings.setSleepMinutes(minutes) }
                    },
                    djNotifierEnabled = djNotifierEnabled,
                    onDjNotifierEnabled = { enabled ->
                        djNotifierEnabled = enabled
                        scope.launch {
                            settings.setDjNotifierEnabled(enabled)
                            DjNotifier.enqueue(context, enabled)
                            if (enabled) {
                                DjNotifier.ensureChannel(context)
                                if (
                                    Notifications.shouldRequest(
                                        Notifications.needed,
                                        Notifications.granted(context),
                                    )
                                ) {
                                    permission.launch(Notifications.permission())
                                }
                            }
                            notifyOk = Notifications.granted(context)
                        }
                    },
                    notifyOk = notifyOk,
                    exactAlarmOk = exactAlarmOk,
                    modifier = modifier,
                )
            }
            if (twoPane) {
                Row(modifier = paneModifier.fillMaxSize()) {
                    nowPlaying(Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider(color = RadioTheme.border)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (shown) {
                            AppTab.News -> news(Modifier.fillMaxSize())
                            AppTab.Settings -> settings(Modifier.fillMaxSize())
                            else -> songs(Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                when (shown) {
                    AppTab.NowPlaying -> nowPlaying(paneModifier)
                    AppTab.Songs -> songs(paneModifier)
                    AppTab.News -> news(paneModifier)
                    AppTab.Settings -> settings(paneModifier)
                }
            }
        }
    }
}
