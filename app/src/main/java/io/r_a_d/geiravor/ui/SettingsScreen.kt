package io.r_a_d.geiravor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.settings.SecretsStore
import io.r_a_d.geiravor.theme.LocalTokens
import uniffi.geiravor_core.IrcProfile
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.ThemePack

@Composable
fun SettingsScreen(ui: UiState, core: RadioCore, secrets: SecretsStore) {
    val t = LocalTokens.current
    val sections = SettingsSection.entries
    Pane(Modifier.fillMaxSize(), hug = false) {
        SectionTabs(
            labels = sections.map { it.label },
            selected = ui.settingsSection.ordinal,
            onSelect = { ui.settingsSection = sections[it] },
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
            when (ui.settingsSection) {
                SettingsSection.General -> {
                    FlagRow("Auto-start on plug", ui.autoStartPlug) {
                        ui.autoStartPlug = it
                        ui.setFlag(core, Prefs.AUTOSTART_PLUG, it)
                    }
                    FlagRow("Convert schedule times to local", ui.scheduleLocal) {
                        ui.scheduleLocal = it
                        ui.setFlag(core, Prefs.SCHEDULE_LOCAL, it)
                    }
                    FlagRow("Opt out of holiday themes", ui.holidayOptOut) {
                        ui.setHolidayOptOut(core, it)
                    }
                    Text("Theme", color = t.muted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    ThemePack.entries.forEach { pack ->
                        val label = when (pack) {
                            ThemePack.DEFAULT_DARK -> "Default"
                            ThemePack.DEFAULT_LIGHT -> "Default light"
                            ThemePack.CHRISTMAS -> "Christmas"
                            ThemePack.HALLOWEEN -> "Halloween"
                            ThemePack.NEW_YEARS -> "New Years"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { ui.setThemePick(core, pack) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = ui.userPick == pack,
                                onClick = { ui.setThemePick(core, pack) },
                                colors = RadioButtonDefaults.colors(selectedColor = t.highlight),
                            )
                            Text(label, color = t.text)
                        }
                    }
                    Text(
                        "Geiravor ${BuildConfig.VERSION_NAME}\nBased on r/a/dio’s Valkyrie.",
                        color = t.muted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                SettingsSection.Auto -> {
                    FlagRow("Auto-start in vehicle", ui.autoStartVehicle) {
                        ui.autoStartVehicle = it
                        ui.setFlag(core, Prefs.AUTOSTART_VEHICLE, it)
                    }
                }
                SettingsSection.Connection -> {
                    Text("Rizon vs bouncer", color = t.muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = ui.profile == IrcProfile.RIZON,
                            onClick = {
                                ui.profile = IrcProfile.RIZON
                                ui.setPref(core, Prefs.PROFILE, "rizon")
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = t.highlight),
                        )
                        Text("Rizon", color = t.text, modifier = Modifier.padding(end = 12.dp))
                        RadioButton(
                            selected = ui.profile == IrcProfile.BOUNCER,
                            onClick = {
                                ui.profile = IrcProfile.BOUNCER
                                ui.setPref(core, Prefs.PROFILE, "bouncer")
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = t.highlight),
                        )
                        Text("Bouncer", color = t.text)
                    }
                    PrefField("Nick *", ui.nick) {
                        ui.nick = it
                        ui.setPref(core, Prefs.NICK, it)
                    }
                    PrefField("Favorites nick", ui.listNick) {
                        ui.listNick = it
                        ui.setPref(core, Prefs.LIST_NICK, it)
                    }
                    if (ui.profile == IrcProfile.BOUNCER) {
                        PrefField("Host *", ui.bouncerHost) {
                            ui.bouncerHost = it
                            ui.setPref(core, Prefs.BOUNCER_HOST, it)
                        }
                        PrefField("Port", ui.bouncerPort, KeyboardType.Number) {
                            ui.bouncerPort = it
                            ui.setPref(core, Prefs.BOUNCER_PORT, it)
                        }
                        SecretField("Bouncer PASS", secrets, SecretKeys.BOUNCER_PASS)
                        FlagRow("Allow insecure TLS", ui.allowInsecure) {
                            ui.allowInsecure = it
                            ui.setFlag(core, Prefs.ALLOW_INSECURE, it)
                        }
                        PrefField("TLS fingerprint", ui.tlsFingerprint) {
                            ui.tlsFingerprint = it
                            ui.setPref(core, Prefs.TLS_FP, it)
                        }
                    } else {
                        SecretField("NickServ password", secrets, SecretKeys.NICKSERV)
                    }
                    PrefField("SASL username", ui.saslUser) {
                        ui.saslUser = it
                        ui.setPref(core, Prefs.SASL_USER, it)
                    }
                    SecretField("SASL password", secrets, SecretKeys.SASL_PASSWORD)
                }
                SettingsSection.Alerts -> {
                    FlagRow("DJ online notifier", ui.djNotifier) {
                        ui.djNotifier = it
                        ui.setFlag(core, Prefs.DJ_NOTIFIER, it)
                    }
                    FlagRow("Fave currently playing", ui.favePlaying) {
                        ui.favePlaying = it
                        ui.setFlag(core, Prefs.FAVE_PLAYING, it)
                    }
                    Text(
                        "Alarm and sleep use the same play/stop path. Battery cost applies when a notifier is on.",
                        color = t.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.text, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = t.highlight),
        )
    }
}

@Composable
private fun PrefField(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    val t = LocalTokens.current
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = t.text,
            unfocusedTextColor = t.text,
            focusedContainerColor = t.surface,
            unfocusedContainerColor = t.surface,
            focusedLabelColor = t.muted,
            unfocusedLabelColor = t.muted,
        ),
    )
}

@Composable
private fun SecretField(label: String, secrets: SecretsStore, key: String) {
    val t = LocalTokens.current
    var value by remember(key) { mutableStateOf(secrets.get(key)) }
    TextField(
        value = value,
        onValueChange = {
            value = it
            secrets.set(key, it)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = t.text,
            unfocusedTextColor = t.text,
            focusedContainerColor = t.surface,
            unfocusedContainerColor = t.surface,
            focusedLabelColor = t.muted,
            unfocusedLabelColor = t.muted,
        ),
    )
}
