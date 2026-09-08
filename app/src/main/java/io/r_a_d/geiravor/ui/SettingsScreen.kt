package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.settings.SecretsStore
import io.r_a_d.geiravor.theme.LocalTokens
import uniffi.geiravor_core.IrcProfile
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.ThemePack
import uniffi.geiravor_core.alarmHour12
import uniffi.geiravor_core.alarmHour24
import uniffi.geiravor_core.alarmIsPm

@Composable
fun SettingsScreen(ui: UiState, core: RadioCore, secrets: SecretsStore) {
    val t = LocalTokens.current
    val ctx = LocalContext.current
    val sections = SettingsSection.entries
    Pane(hug = false) {
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
                        val hover = remember(pack) { MutableInteractionSource() }
                        val hovered by hover.collectIsHoveredAsState()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .hoverable(hover)
                                .background(
                                    if (hovered) t.highlight.copy(alpha = if (t.glass) 0.28f else 0.16f)
                                    else Color.Transparent,
                                )
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
                        PrefField("Port", ui.bouncerPort, KeyboardType.Number, placeholder = "6697") {
                            ui.bouncerPort = it.filter { c -> c.isDigit() }
                            ui.setPref(core, Prefs.BOUNCER_PORT, it.filter { c -> c.isDigit() })
                        }
                        SecretField("Server password", secrets, SecretKeys.BOUNCER_PASS)
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
                    SecretField(
                        "Client cert PEM",
                        secrets,
                        SecretKeys.CLIENT_CERT,
                        copyCut = true,
                        multiline = true,
                        clearConfirm = true,
                    )
                    val certFp = remember(secrets.get(SecretKeys.CLIENT_CERT)) {
                        uniffi.geiravor_core.certificateFingerprintSha256(secrets.get(SecretKeys.CLIENT_CERT))
                    }
                    if (certFp.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                "Client cert SHA-256 $certFp",
                                color = t.muted,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                        }
                    }
                    SecretField(
                        "Client key PEM",
                        secrets,
                        SecretKeys.CLIENT_KEY,
                        multiline = true,
                        clearConfirm = true,
                    )
                    Button(
                        onClick = {
                            val cfg = ui.faveConfig(secrets)
                            ui.probeText = "Testing…"
                            ui.offMain {
                                val out = runCatching { core.probe(cfg) }.fold(
                                    onSuccess = { it },
                                    onFailure = { it.message ?: "failed" },
                                )
                                ui.onMain { ui.probeText = out }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = t.accent),
                    ) { Text("Test connection") }
                    ui.probeText?.let { Text(it, color = t.muted, modifier = Modifier.padding(top = 8.dp)) }
                }
                SettingsSection.Alerts -> {
                    FlagRow("Alarm", ui.alarmOn) {
                        ui.alarmOn = it
                        ui.setFlag(core, Prefs.ALARM_ON, it)
                        ui.offMain {
                            io.r_a_d.geiravor.alert.AlarmScheduler.schedule(ctx, core) { msg ->
                                ui.onMain { ui.alertError = msg }
                            }
                        }
                    }
                    ClockRow("Alarm time", ui.alarmHour, ui.alarmMinute, ui.alarm24h) { h, m ->
                        ui.alarmHour = h
                        ui.alarmMinute = m
                        ui.setPref(core, Prefs.ALARM_HOUR, h)
                        ui.setPref(core, Prefs.ALARM_MINUTE, m)
                        if (ui.alarmOn) {
                            ui.offMain {
                                io.r_a_d.geiravor.alert.AlarmScheduler.schedule(ctx, core) { msg ->
                                    ui.onMain { ui.alertError = msg }
                                }
                            }
                        }
                    }
                    FlagRow("24-hour clock", ui.alarm24h) {
                        ui.alarm24h = it
                        ui.setFlag(core, Prefs.ALARM_24H, it)
                    }
                    FlagRow("Snooze", ui.snoozeOn) {
                        ui.snoozeOn = it
                        ui.setFlag(core, Prefs.SNOOZE_ON, it)
                    }
                    DurationRow("Snooze for", ui.snoozeHours, ui.snoozeMinutes) { h, m ->
                        ui.snoozeHours = h
                        ui.snoozeMinutes = m
                        ui.setPref(core, Prefs.SNOOZE_HOURS, h)
                        ui.setPref(core, Prefs.SNOOZE_MINUTES, m)
                    }
                    FlagRow("Sleep timer", ui.sleepOn) {
                        ui.sleepOn = it
                        ui.setFlag(core, Prefs.SLEEP_ON, it)
                        if (it) armSleep(ctx, ui)
                    }
                    DurationRow("Sleep after", ui.sleepHours, ui.sleepMinutes) { h, m ->
                        ui.sleepHours = h
                        ui.sleepMinutes = m
                        ui.setPref(core, Prefs.SLEEP_HOURS, h)
                        ui.setPref(core, Prefs.SLEEP_MINUTES, m)
                        if (ui.sleepOn) armSleep(ctx, ui)
                    }
                    FlagRow("DJ online notifier", ui.djNotifier) {
                        ui.djNotifier = it
                        ui.setFlag(core, Prefs.DJ_NOTIFIER, it)
                        io.r_a_d.geiravor.alert.StationWatch.sync(ctx, it, ui.favePlaying)
                    }
                    FlagRow("Fave currently playing", ui.favePlaying) {
                        ui.favePlaying = it
                        ui.setFlag(core, Prefs.FAVE_PLAYING, it)
                        io.r_a_d.geiravor.alert.StationWatch.sync(ctx, ui.djNotifier, it)
                    }
                    ui.alertError?.let { Text(it, color = t.red) }
                    Text(
                        "DJ and fave notices check every 15 minutes and use battery. Denied notification permission shows here.",
                        color = t.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun armSleep(ctx: android.content.Context, ui: UiState) {
    val hours = ui.sleepHours.toUIntOrNull() ?: 0u
    val minutes = ui.sleepMinutes.toUIntOrNull() ?: 30u
    val mins = uniffi.geiravor_core.alertDurationMinutes(hours, minutes).toInt()
    ctx.startService(io.r_a_d.geiravor.playback.PlaybackService.sleepIntent(ctx, mins))
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
private fun ClockRow(
    label: String,
    hour: String,
    minute: String,
    use24: Boolean,
    onChange: (String, String) -> Unit,
) {
    val t = LocalTokens.current
    val h24 = hour.toUIntOrNull()?.coerceIn(0u, 23u) ?: 7u
    val shown = if (use24) h24.toString() else alarmHour12(h24).toString()
    val pm = alarmIsPm(h24)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.text, modifier = Modifier.weight(1f))
        CompactNumber(shown, "h") { raw ->
            val digits = raw.filter { c -> c.isDigit() }.take(2)
            val next = if (use24) {
                digits.toUIntOrNull()?.coerceIn(0u, 23u) ?: h24
            } else {
                val h12 = digits.toUIntOrNull()?.coerceIn(1u, 12u) ?: alarmHour12(h24)
                alarmHour24(h12, pm)
            }
            onChange(next.toString(), minute)
        }
        Text(":", color = t.muted, modifier = Modifier.padding(horizontal = 4.dp))
        CompactNumber(minute, "m") { onChange(hour, it.filter { c -> c.isDigit() }.take(2)) }
        if (!use24) {
            Text(
                if (pm) "PM" else "AM",
                color = t.highlight,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable {
                        onChange(alarmHour24(alarmHour12(h24), !pm).toString(), minute)
                    },
            )
        }
    }
}

@Composable
private fun DurationRow(
    label: String,
    hours: String,
    minutes: String,
    onChange: (String, String) -> Unit,
) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.text, modifier = Modifier.weight(1f))
        CompactNumber(hours, "h") { onChange(it.filter { c -> c.isDigit() }.take(2), minutes) }
        Text("h", color = t.muted, modifier = Modifier.padding(end = 8.dp))
        CompactNumber(minutes, "m") { onChange(hours, it.filter { c -> c.isDigit() }.take(2)) }
        Text("m", color = t.muted)
    }
}

@Composable
private fun CompactNumber(value: String, label: String, onChange: (String) -> Unit) {
    val t = LocalTokens.current
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(64.dp),
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
private fun PrefField(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    val t = LocalTokens.current
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
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
private fun SecretField(
    label: String,
    secrets: SecretsStore,
    key: String,
    copyCut: Boolean = false,
    multiline: Boolean = false,
    clearConfirm: Boolean = false,
) {
    val t = LocalTokens.current
    var value by remember(key) { mutableStateOf(secrets.get(key)) }
    var confirm by remember { mutableStateOf(false) }
    if (clearConfirm) {
        TextButton(onClick = { confirm = true }) { Text("Clear") }
    }
    val field = @Composable {
        TextField(
            value = value,
            onValueChange = {
                value = it
                secrets.set(key, it)
            },
            label = { Text(label) },
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            visualTransformation = if (copyCut && multiline) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (multiline) KeyboardType.Text else KeyboardType.Password,
            ),
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
    if (copyCut) {
        field()
    } else {
        PasteOnlyText { field() }
    }
    if (confirm) {
        AlertDialog(
            containerColor = if (t.glass) Color(0xFF1A1A1A) else t.surface,
            onDismissRequest = { confirm = false },
            title = { Text("Clear $label?") },
            confirmButton = {
                TextButton(onClick = {
                    secrets.set(key, "")
                    value = ""
                    confirm = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PasteOnlyText(content: @Composable () -> Unit) {
    val inner = LocalTextToolbar.current
    val toolbar = remember(inner) { PasteOnlyToolbar(inner) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar, content = content)
}

private class PasteOnlyToolbar(private val inner: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus
        get() = inner.status

    override fun hide() = inner.hide()

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        inner.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested)
    }
}
