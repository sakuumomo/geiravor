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
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.compat.applyEdgeToEdge
import io.r_a_d.geiravor.playback.FavePolicy
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.playback.PlaybackService
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
        val nick = settings.favesNick.first()
        favesNick = nick
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
    LaunchedEffect(favesNick, ircNick, radioState.status?.trackId, radioState.status?.np) {
        val status = radioState.status
        val listedNick = FavePolicy.ircNick(ircNick, favesNick)
        val filled = withContext(Dispatchers.IO) {
            if (FavoritesPolicy.shouldFetch(listedNick)) {
                runCatching { app.radio.prefetchFavorites(listedNick) }
            }
            FavePolicy.isListed(listedNick, status, app.radio::isFavorite)
        }
        RadioStore.setHeart(filled)
    }

    LifecycleResumeEffect(app) {
        app.radio.setUiVisible(true)
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
                    val listedNick = FavePolicy.ircNick(ircNick, favesNick)
                    val listed = FavePolicy.isListed(
                        listedNick,
                        app.radio.snapshot(),
                        app.radio::isFavorite,
                    )
                    val done = app.radio.addFave(
                        FavePolicy.config(
                            nick = listedNick,
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
                    onFavesNick = { favesNick = it },
                    onFavesNickPersist = { nick ->
                        scope.launch { settings.setFavesNick(nick) }
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
                                            nick = FavePolicy.ircNick(ircNick, favesNick),
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
