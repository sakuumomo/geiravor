package io.r_a_d.geiravor

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
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
import com.google.common.util.concurrent.MoreExecutors
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.compat.applyEdgeToEdge
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.playback.PlaybackService
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.settings.SettingsStore
import io.r_a_d.geiravor.ui.NowPlayingScreen
import io.r_a_d.geiravor.ui.RadioTheme
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
                playing = c.isPlaying
                c.volume = gain
                app.radio.setPlaying(c.isPlaying)
                c.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playing = isPlaying
                            app.radio.setPlaying(isPlaying)
                        }
                    },
                )
            },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            controller?.play()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RadioTheme.background) {
        NowPlayingScreen(
            radio = app.radio,
            status = radioState.status,
            streamDown = radioState.streamDown,
            playing = playing,
            gain = gain,
            onGain = { value ->
                gain = value
                controller?.volume = value
                scope.launch { settings.setGain(value) }
            },
            onPlayToggle = {
                if (playing) {
                    controller?.stop()
                } else if (Notifications.shouldRequest(Notifications.needed, Notifications.granted(context))) {
                    permission.launch(Notifications.permission())
                } else {
                    controller?.play()
                }
            },
            modifier = Modifier.systemBarsPadding(),
        )
    }
}
