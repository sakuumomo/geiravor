package io.r_a_d.geiravor

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.compat.applyEdgeToEdge
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.playback.PlaybackService
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.settings.SettingsStore
import io.r_a_d.geiravor.ui.AppTab
import io.r_a_d.geiravor.ui.NowPlayingScreen
import io.r_a_d.geiravor.ui.RadioTheme
import io.r_a_d.geiravor.ui.SongsScreen
import kotlinx.coroutines.launch

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
    val radioState by RadioStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var gain by remember { mutableStateOf(LivePlaybackPolicy.DEFAULT_GAIN) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings.gain.collect { stored ->
            gain = stored
            controller?.volume = stored
        }
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
            playing = true
            controller?.play()
            if (Notifications.shouldRequest(Notifications.needed, Notifications.granted(context))) {
                permission.launch(Notifications.permission())
            }
        }
    }
    var tab by remember { mutableStateOf(AppTab.NowPlaying) }

    Surface(modifier = Modifier.fillMaxSize(), color = RadioTheme.background) {
        Scaffold(
            modifier = Modifier.systemBarsPadding(),
            containerColor = RadioTheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = RadioTheme.surface) {
                    AppTab.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = tab == dest,
                            onClick = { tab = dest },
                            icon = { Text(if (dest == AppTab.NowPlaying) "▶" else "≡") },
                            label = { Text(dest.label) },
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
            when (tab) {
                AppTab.NowPlaying -> NowPlayingScreen(
                    radio = app.radio,
                    status = radioState.status,
                    streamDown = radioState.streamDown,
                    playing = playing,
                    gain = gain,
                    onGain = onGain,
                    onGainFinished = onGainFinished,
                    onPlayToggle = onPlayToggle,
                    modifier = Modifier.padding(padding),
                )
                AppTab.Songs -> SongsScreen(
                    status = radioState.status,
                    streamDown = radioState.streamDown,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
