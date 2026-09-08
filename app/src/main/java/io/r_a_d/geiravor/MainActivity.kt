package io.r_a_d.geiravor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.compat.enableEdgeToEdge
import io.r_a_d.geiravor.playback.PlaybackService
import kotlinx.coroutines.delay
import uniffi.geiravor_core.Status

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.requestIfNeeded(this, 1)
        val app = application as GeiravorApp
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var snap by remember { mutableStateOf<Status?>(null) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            snap = app.core.snapshot()
                            delay(1_000)
                        }
                    }
                    Column(Modifier.padding(24.dp)) {
                        Text("r/a/dio", style = MaterialTheme.typography.headlineMedium)
                        Text(snap?.np ?: "…", Modifier.padding(top = 12.dp))
                        Button(
                            onClick = {
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    PlaybackService.playIntent(this@MainActivity),
                                )
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("Play") }
                        Button(
                            onClick = {
                                startService(PlaybackService.stopIntent(this@MainActivity))
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("Stop") }
                    }
                }
            }
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
