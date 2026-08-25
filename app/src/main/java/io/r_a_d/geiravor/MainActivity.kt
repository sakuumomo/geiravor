package io.r_a_d.geiravor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uniffi.geiravor_core.add

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GeiravorRoot() }
    }
}

@Composable
private fun GeiravorRoot() {
    val bg = Color.hsl(0f, 0f, 0.11f)
    val fg = Color.hsl(0f, 0f, 0.96f)
    Surface(modifier = Modifier.fillMaxSize(), color = bg) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("r/a/dio", color = fg, style = MaterialTheme.typography.headlineMedium)
            Text("Geiravor ${BuildConfig.VERSION_NAME}", color = fg)
            Text("core.add(1, 2) = ${add(1u, 2u)}", color = fg)
        }
    }
}
