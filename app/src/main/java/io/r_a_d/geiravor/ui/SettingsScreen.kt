package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    autoStartOnPlug: Boolean,
    onAutoStartOnPlug: (Boolean) -> Unit,
    autoStartInVehicle: Boolean,
    onAutoStartInVehicle: (Boolean) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadioTheme.surface),
            border = BorderStroke(1.dp, RadioTheme.border),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingToggle(
                    label = "Auto-start on plug",
                    checked = autoStartOnPlug,
                    onCheckedChange = onAutoStartOnPlug,
                )
                SettingToggle(
                    label = "Auto-start in vehicle",
                    checked = autoStartInVehicle,
                    onCheckedChange = onAutoStartInVehicle,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadioTheme.surface),
            border = BorderStroke(1.dp, RadioTheme.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("About", color = RadioTheme.text, fontSize = 18.sp)
                Text("r/a/dio", color = RadioTheme.muted, fontSize = 14.sp)
                Text(versionName, color = RadioTheme.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = RadioTheme.text,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RadioTheme.text,
                checkedTrackColor = RadioTheme.blue,
                uncheckedThumbColor = RadioTheme.muted,
                uncheckedTrackColor = RadioTheme.border,
            ),
        )
    }
}
