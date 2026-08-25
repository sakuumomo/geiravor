package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val sections = SectionLayout.settingsSections()
    var selected by remember { mutableStateOf(SectionLayout.SettingsSection.General) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = SectionLayout.twoPane(maxWidth.value.toInt())
        Column(modifier = Modifier.fillMaxSize()) {
            if (!twoPane) {
                SectionTabs(
                    labels = sections.map { it.label },
                    selected = sections.indexOf(selected).coerceAtLeast(0),
                    onSelect = { selected = sections[it] },
                )
            }
            if (twoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GeneralSettings(
                        autoStartOnPlug = autoStartOnPlug,
                        onAutoStartOnPlug = onAutoStartOnPlug,
                        versionName = versionName,
                        modifier = Modifier.weight(1f),
                    )
                    AutoSettings(
                        autoStartInVehicle = autoStartInVehicle,
                        onAutoStartInVehicle = onAutoStartInVehicle,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (selected) {
                        SectionLayout.SettingsSection.General -> GeneralSettings(
                            autoStartOnPlug = autoStartOnPlug,
                            onAutoStartOnPlug = onAutoStartOnPlug,
                            versionName = versionName,
                        )
                        SectionLayout.SettingsSection.Auto -> AutoSettings(
                            autoStartInVehicle = autoStartInVehicle,
                            onAutoStartInVehicle = onAutoStartInVehicle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSettings(
    autoStartOnPlug: Boolean,
    onAutoStartOnPlug: (Boolean) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsCard {
            SettingToggle(
                label = "Auto-start on plug",
                checked = autoStartOnPlug,
                onCheckedChange = onAutoStartOnPlug,
            )
        }
        SettingsCard {
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
private fun AutoSettings(
    autoStartInVehicle: Boolean,
    onAutoStartInVehicle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsCard {
            SettingToggle(
                label = "Auto-start in vehicle",
                checked = autoStartInVehicle,
                onCheckedChange = onAutoStartInVehicle,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RadioTheme.surface),
        border = BorderStroke(1.dp, RadioTheme.border),
        content = { content() },
    )
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
