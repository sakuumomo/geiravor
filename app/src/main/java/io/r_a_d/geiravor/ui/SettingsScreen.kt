package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.settings.SettingsPolicy
import uniffi.geiravor_core.IrcProfile

@Composable
fun SettingsScreen(
    autoStartOnPlug: Boolean,
    onAutoStartOnPlug: (Boolean) -> Unit,
    autoStartInVehicle: Boolean,
    onAutoStartInVehicle: (Boolean) -> Unit,
    ircNick: String,
    onIrcNick: (String) -> Unit,
    ircProfile: IrcProfile,
    onIrcProfile: (IrcProfile) -> Unit,
    nickservPassword: String,
    onNickservPassword: (String) -> Unit,
    bouncerHost: String,
    onBouncerHost: (String) -> Unit,
    bouncerPort: String,
    onBouncerPort: (String) -> Unit,
    bouncerPass: String,
    onBouncerPass: (String) -> Unit,
    allowInsecureTls: Boolean,
    onAllowInsecureTls: (Boolean) -> Unit,
    saslUsername: String,
    onSaslUsername: (String) -> Unit,
    saslPassword: String,
    onSaslPassword: (String) -> Unit,
    clientCertPem: String,
    onClientCertPem: (String) -> Unit,
    clientKeyPem: String,
    onClientKeyPem: (String) -> Unit,
    tlsFingerprint: String,
    onTlsFingerprint: (String) -> Unit,
    onTestConnection: () -> Unit,
    testBusy: Boolean,
    testMessage: Pair<Boolean, String>?,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val sections = SectionLayout.settingsSections()
    var selected by remember { mutableStateOf(SectionLayout.SettingsSection.General) }

    Column(modifier = modifier.fillMaxSize()) {
        SectionTabs(
            labels = sections.map { it.label },
            selected = sections.indexOf(selected).coerceAtLeast(0),
            onSelect = { selected = sections[it] },
        )
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
                SectionLayout.SettingsSection.Connection -> SettingsCard {
                    ConnectionSettings(
                        ircNick = ircNick,
                        onIrcNick = onIrcNick,
                        profile = ircProfile,
                        onProfile = onIrcProfile,
                        nickservPassword = nickservPassword,
                        onNickservPassword = onNickservPassword,
                        bouncerHost = bouncerHost,
                        onBouncerHost = onBouncerHost,
                        bouncerPort = bouncerPort,
                        onBouncerPort = onBouncerPort,
                        bouncerPass = bouncerPass,
                        onBouncerPass = onBouncerPass,
                        allowInsecureTls = allowInsecureTls,
                        onAllowInsecureTls = onAllowInsecureTls,
                        saslUsername = saslUsername,
                        onSaslUsername = onSaslUsername,
                        saslPassword = saslPassword,
                        onSaslPassword = onSaslPassword,
                        clientCertPem = clientCertPem,
                        onClientCertPem = onClientCertPem,
                        clientKeyPem = clientKeyPem,
                        onClientKeyPem = onClientKeyPem,
                        tlsFingerprint = tlsFingerprint,
                        onTlsFingerprint = onTlsFingerprint,
                        onTest = onTestConnection,
                        testBusy = testBusy,
                        testMessage = testMessage,
                        modifier = Modifier.padding(16.dp),
                    )
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
                Text(SettingsPolicy.ABOUT_NAME, color = RadioTheme.text, fontSize = 14.sp)
                Text(SettingsPolicy.ABOUT_LINE, color = RadioTheme.muted, fontSize = 13.sp)
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
            .clickable { onCheckedChange(!checked) }
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
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RadioTheme.text,
                checkedTrackColor = RadioTheme.blue,
                uncheckedThumbColor = RadioTheme.muted,
                uncheckedTrackColor = RadioTheme.border,
            ),
        )
    }
}
