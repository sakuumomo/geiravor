package io.r_a_d.geiravor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.compat.enableEdgeToEdge
import io.r_a_d.geiravor.playback.PlaybackService
import io.r_a_d.geiravor.ui.GeiravorRoot
import io.r_a_d.geiravor.ui.Prefs
import io.r_a_d.geiravor.ui.tapFave

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.requestIfNeeded(this, 1)
        val app = application as GeiravorApp
        setContent {
            GeiravorRoot(
                ui = app.ui,
                core = app.core,
                secrets = app.secrets,
                onPlay = {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        PlaybackService.playIntent(this@MainActivity),
                    )
                },
                onStop = {
                    startService(PlaybackService.stopIntent(this@MainActivity))
                },
                onGain = { g ->
                    app.ui.gain = g
                    app.ui.setPref(app.core, Prefs.GAIN, g.toString())
                    startService(PlaybackService.gainIntent(this@MainActivity, g))
                },
                onFave = { tapFave(app.ui, app.core, app.secrets) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        (application as GeiravorApp).core.setUiVisible(true)
    }

    override fun onStop() {
        (application as GeiravorApp).core.setUiVisible(false)
        super.onStop()
    }
}
