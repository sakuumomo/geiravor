package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.compat.ExactAlarms
import io.r_a_d.geiravor.playback.AlarmPolicy
import io.r_a_d.geiravor.playback.DurationPolicy
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.playback.DjNotifierPolicy
import io.r_a_d.geiravor.playback.SleepPolicy
import io.r_a_d.geiravor.settings.SettingsPolicy
import uniffi.geiravor_core.IrcProfile

@Composable
fun SettingsScreen(
    autoStartOnPlug: Boolean,
    onAutoStartOnPlug: (Boolean) -> Unit,
    convertScheduleTimes: Boolean,
    onConvertScheduleTimes: (Boolean) -> Unit,
    themePack: String,
    onThemePack: (String) -> Unit,
    holidayOptOut: Boolean,
    onHolidayOptOut: (Boolean) -> Unit,
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
    alarmEnabled: Boolean,
    onAlarmEnabled: (Boolean) -> Unit,
    alarmHour: Int,
    alarmMinute: Int,
    onAlarmTime: (Int, Int) -> Unit,
    snoozeEnabled: Boolean,
    onSnoozeEnabled: (Boolean) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutes: (Int) -> Unit,
    sleepEnabled: Boolean,
    onSleepEnabled: (Boolean) -> Unit,
    sleepMinutes: Int,
    onSleepMinutes: (Int) -> Unit,
    djNotifierEnabled: Boolean,
    onDjNotifierEnabled: (Boolean) -> Unit,
    notifyOk: Boolean,
    exactAlarmOk: Boolean,
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
                    convertScheduleTimes = convertScheduleTimes,
                    onConvertScheduleTimes = onConvertScheduleTimes,
                    themePack = themePack,
                    onThemePack = onThemePack,
                    holidayOptOut = holidayOptOut,
                    onHolidayOptOut = onHolidayOptOut,
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
                SectionLayout.SettingsSection.Alerts -> AlertSettings(
                    alarmEnabled = alarmEnabled,
                    onAlarmEnabled = onAlarmEnabled,
                    alarmHour = alarmHour,
                    alarmMinute = alarmMinute,
                    onAlarmTime = onAlarmTime,
                    snoozeEnabled = snoozeEnabled,
                    onSnoozeEnabled = onSnoozeEnabled,
                    snoozeMinutes = snoozeMinutes,
                    onSnoozeMinutes = onSnoozeMinutes,
                    sleepEnabled = sleepEnabled,
                    onSleepEnabled = onSleepEnabled,
                    sleepMinutes = sleepMinutes,
                    onSleepMinutes = onSleepMinutes,
                    djNotifierEnabled = djNotifierEnabled,
                    onDjNotifierEnabled = onDjNotifierEnabled,
                    notifyOk = notifyOk,
                    exactAlarmOk = exactAlarmOk,
                )
            }
        }
    }
}

@Composable
private fun GeneralSettings(
    autoStartOnPlug: Boolean,
    onAutoStartOnPlug: (Boolean) -> Unit,
    convertScheduleTimes: Boolean,
    onConvertScheduleTimes: (Boolean) -> Unit,
    themePack: String,
    onThemePack: (String) -> Unit,
    holidayOptOut: Boolean,
    onHolidayOptOut: (Boolean) -> Unit,
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
            Column {
                Text(
                    "Theme",
                    color = RadioTheme.text,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )
                RadioPacks.PICKS.forEach { (id, label) ->
                    SettingChoice(
                        label = label,
                        selected = themePack == id,
                        onClick = { onThemePack(id) },
                    )
                }
            }
        }
        SettingsCard {
            SettingToggle(
                label = "Opt out of holiday themes",
                checked = holidayOptOut,
                onCheckedChange = onHolidayOptOut,
            )
        }
        SettingsCard {
            SettingToggle(
                label = "Convert schedule times to local",
                checked = convertScheduleTimes,
                onCheckedChange = onConvertScheduleTimes,
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
private fun AlertSettings(
    alarmEnabled: Boolean,
    onAlarmEnabled: (Boolean) -> Unit,
    alarmHour: Int,
    alarmMinute: Int,
    onAlarmTime: (Int, Int) -> Unit,
    snoozeEnabled: Boolean,
    onSnoozeEnabled: (Boolean) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutes: (Int) -> Unit,
    sleepEnabled: Boolean,
    onSleepEnabled: (Boolean) -> Unit,
    sleepMinutes: Int,
    onSleepMinutes: (Int) -> Unit,
    djNotifierEnabled: Boolean,
    onDjNotifierEnabled: (Boolean) -> Unit,
    notifyOk: Boolean,
    exactAlarmOk: Boolean,
) {
    val context = LocalContext.current
    var timeOpen by remember { mutableStateOf(false) }
    var snoozeOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    val denied = AlarmPolicy.shouldExplainExactDenied(
        needsGrant = ExactAlarms.needsRuntimeGrant(),
        canSchedule = exactAlarmOk,
    )
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsCard {
            Column {
                SettingToggle(
                    label = "Alarm",
                    checked = alarmEnabled,
                    onCheckedChange = onAlarmEnabled,
                )
                SettingRow(
                    label = "Time",
                    value = AlarmPolicy.formatTime(alarmHour, alarmMinute),
                    onClick = { timeOpen = true },
                )
            }
        }
        SettingsCard {
            Column {
                SettingToggle(
                    label = "Snooze",
                    checked = snoozeEnabled,
                    onCheckedChange = onSnoozeEnabled,
                )
                if (snoozeEnabled) {
                    SettingRow(
                        label = "Snooze for",
                        value = AlarmPolicy.formatSnooze(snoozeMinutes),
                        onClick = { snoozeOpen = true },
                    )
                }
            }
        }
        SettingsCard {
            Column {
                SettingToggle(
                    label = "Sleep timer",
                    checked = sleepEnabled,
                    onCheckedChange = onSleepEnabled,
                )
                SettingRow(
                    label = "Sleep for",
                    value = SleepPolicy.formatMinutes(sleepMinutes),
                    onClick = { sleepOpen = true },
                )
            }
        }
        SettingsCard {
            Column {
                SettingToggle(
                    label = "DJ notifier",
                    checked = djNotifierEnabled,
                    onCheckedChange = onDjNotifierEnabled,
                )
                Text(
                    DjNotifierPolicy.BATTERY,
                    color = RadioTheme.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
        if (
            djNotifierEnabled &&
            DjNotifierPolicy.shouldExplainDenied(
                needsGrant = Notifications.needed,
                granted = notifyOk,
            )
        ) {
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(DjNotifierPolicy.NOTIFICATIONS_DENIED, color = RadioTheme.red, fontSize = 14.sp)
                }
            }
        }
        if (denied) {
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { context.startActivity(ExactAlarms.settingsIntent(context)) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(AlarmPolicy.EXACT_DENIED, color = RadioTheme.red, fontSize = 14.sp)
                    Text("Open system settings", color = RadioTheme.link, fontSize = 14.sp)
                }
            }
        }
    }
    if (timeOpen) {
        AlarmTimeDialog(
            hour = alarmHour,
            minute = alarmMinute,
            onDismiss = { timeOpen = false },
            onConfirm = { h, m ->
                onAlarmTime(h, m)
                timeOpen = false
            },
        )
    }
    if (snoozeOpen) {
        DurationDialog(
            title = "Snooze for",
            totalMinutes = snoozeMinutes,
            onDismiss = { snoozeOpen = false },
            onConfirm = { minutes ->
                onSnoozeMinutes(minutes)
                snoozeOpen = false
            },
        )
    }
    if (sleepOpen) {
        DurationDialog(
            title = "Sleep for",
            totalMinutes = sleepMinutes,
            onDismiss = { sleepOpen = false },
            onConfirm = { minutes ->
                onSleepMinutes(minutes)
                sleepOpen = false
            },
        )
    }
}

@Composable
private fun DurationDialog(
    title: String,
    totalMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hourText by remember {
        mutableStateOf(DurationPolicy.hours(totalMinutes).toString())
    }
    var minuteText by remember {
        mutableStateOf("%02d".format(DurationPolicy.minutePart(totalMinutes)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        DurationPolicy.fromHoursMinutes(
                            hourText.toIntOrNull() ?: DurationPolicy.hours(totalMinutes),
                            minuteText.toIntOrNull() ?: DurationPolicy.minutePart(totalMinutes),
                        ),
                    )
                },
            ) { Text("Set", color = RadioTheme.link) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = RadioTheme.muted) }
        },
        title = { Text(title, color = RadioTheme.text) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { hourText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Hours") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = alarmFieldColors(),
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = alarmFieldColors(),
                )
            }
        },
        containerColor = RadioTheme.surface,
    )
}

@Composable
private fun AlarmTimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var hourText by remember { mutableStateOf(hour.toString()) }
    var minuteText by remember { mutableStateOf("%02d".format(minute)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AlarmPolicy.clampHour(hourText.toIntOrNull() ?: hour),
                        AlarmPolicy.clampMinute(minuteText.toIntOrNull() ?: minute),
                    )
                },
            ) { Text("Set", color = RadioTheme.link) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = RadioTheme.muted) }
        },
        title = { Text("Alarm time", color = RadioTheme.text) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { hourText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Hour") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = alarmFieldColors(),
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Minute") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = alarmFieldColors(),
                )
            }
        },
        containerColor = RadioTheme.surface,
    )
}

@Composable
private fun alarmFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RadioTheme.text,
    unfocusedTextColor = RadioTheme.text,
    focusedBorderColor = RadioTheme.blue,
    unfocusedBorderColor = RadioTheme.border,
    cursorColor = RadioTheme.blue,
    focusedLabelColor = RadioTheme.muted,
    unfocusedLabelColor = RadioTheme.muted,
)

@Composable
private fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = RadioTheme.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = RadioTheme.muted, fontSize = 16.sp)
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
    RadioCard(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun SettingChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) RadioTheme.highlight.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = RadioTheme.text,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (selected) "●" else "○",
            color = if (selected) RadioTheme.highlight else RadioTheme.muted,
            fontSize = 16.sp,
        )
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
                checkedTrackColor = RadioTheme.highlight,
                uncheckedThumbColor = RadioTheme.muted,
                uncheckedTrackColor = RadioTheme.border,
            ),
        )
    }
}
