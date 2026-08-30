package io.r_a_d.geiravor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.playback.FavePolicy
import uniffi.geiravor_core.IrcProfile
import uniffi.geiravor_core.certificateFingerprintSha256

@Composable
fun ConnectionSettings(
    ircNick: String,
    onIrcNick: (String) -> Unit,
    profile: IrcProfile,
    onProfile: (IrcProfile) -> Unit,
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
    onTest: () -> Unit,
    testBusy: Boolean,
    testMessage: Pair<Boolean, String>?,
    modifier: Modifier = Modifier,
) {
    val bouncer = profile == IrcProfile.BOUNCER
    val certFingerprint = remember(clientCertPem) {
        runCatching { certificateFingerprintSha256(clientCertPem) }.getOrDefault("")
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Rizon",
                color = if (!bouncer) RadioTheme.text else RadioTheme.muted,
                modifier = Modifier.clickable { onProfile(IrcProfile.RIZON) },
            )
            Text(
                "Bouncer",
                color = if (bouncer) RadioTheme.text else RadioTheme.muted,
                modifier = Modifier.clickable { onProfile(IrcProfile.BOUNCER) },
            )
        }
        ConnectionField(
            value = ircNick,
            onValue = onIrcNick,
            label = "Nick",
            secret = false,
            placeholder = "Favorites nick",
        )
        if (bouncer) {
            ConnectionField(
                value = bouncerHost,
                onValue = onBouncerHost,
                label = "Host",
                secret = false,
            )
            ConnectionField(
                value = bouncerPort,
                onValue = { incoming ->
                    onBouncerPort(FavePolicy.portInput(incoming))
                },
                label = "Port",
                secret = false,
                placeholder = FavePolicy.DEFAULT_BOUNCER_PORT.toString(),
            )
            ConnectionField(value = bouncerPass, onValue = onBouncerPass, label = "Server password")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Allow insecure TLS",
                    color = RadioTheme.text,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = allowInsecureTls,
                    onCheckedChange = onAllowInsecureTls,
                    colors = SwitchDefaults.colors(checkedTrackColor = RadioTheme.highlight),
                )
            }
        } else {
            ConnectionField(
                value = nickservPassword,
                onValue = onNickservPassword,
                label = "NickServ password",
            )
        }
        ConnectionField(
            value = saslUsername,
            onValue = onSaslUsername,
            label = "SASL username",
            secret = false,
            placeholder = "connection nick",
        )
        ConnectionField(
            value = saslPassword,
            onValue = onSaslPassword,
            label = "SASL password",
        )
        PemField(
            value = clientCertPem,
            onValue = onClientCertPem,
            label = "Client certificate (PEM)",
            secret = false,
            clearTitle = FavePolicy.CLEAR_CERT_TITLE,
            fingerprint = FavePolicy.certFingerprintLabel(certFingerprint),
        )
        PemField(
            value = clientKeyPem,
            onValue = onClientKeyPem,
            label = "Client key (PEM)",
            secret = true,
            clearTitle = FavePolicy.CLEAR_KEY_TITLE,
        )
        ConnectionField(
            value = tlsFingerprint,
            onValue = { incoming ->
                onTlsFingerprint(FavePolicy.fingerprintInput(incoming))
            },
            label = "TLS fingerprint",
            secret = false,
            placeholder = "from Test connection",
        )
        Button(
            onClick = onTest,
            enabled = !testBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = radioButtonColors(),
        ) {
            Text("Test connection")
        }
        testMessage?.let { (ok, text) ->
            SelectionContainer {
                Text(
                    text,
                    color = if (ok) RadioTheme.green else RadioTheme.red,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PemField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    secret: Boolean,
    clearTitle: String,
    fingerprint: String = "",
) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Clear",
            color = if (value.isNotEmpty()) RadioTheme.red else RadioTheme.muted,
            fontSize = 13.sp,
            modifier = Modifier.clickable(enabled = value.isNotEmpty()) { confirm = true },
        )
    }
    ConnectionField(
        value = value,
        onValue = onValue,
        label = label,
        secret = secret,
        singleLine = false,
    )
    if (fingerprint.isNotEmpty()) {
        SelectionContainer {
            Text(
                fingerprint,
                color = RadioTheme.muted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(clearTitle) },
            text = { Text(FavePolicy.CLEAR_PEM_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValue("")
                        confirm = false
                    },
                ) {
                    Text("Clear", color = RadioTheme.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text("Cancel", color = RadioTheme.muted)
                }
            },
            containerColor = RadioTheme.surface,
            titleContentColor = RadioTheme.text,
            textContentColor = RadioTheme.text,
        )
    }
}

@Composable
private fun ConnectionField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    secret: Boolean = true,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    val field = @Composable {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 4,
            maxLines = if (singleLine) 1 else 8,
            label = { Text(label) },
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder) }
            } else {
                null
            },
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret && singleLine) {
                    KeyboardType.Password
                } else {
                    KeyboardType.Unspecified
                },
                autoCorrectEnabled = !secret,
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RadioTheme.text,
                unfocusedTextColor = RadioTheme.text,
                focusedBorderColor = RadioTheme.blue,
                unfocusedBorderColor = RadioTheme.border,
                cursorColor = RadioTheme.blue,
                focusedLabelColor = RadioTheme.muted,
                unfocusedLabelColor = RadioTheme.muted,
                focusedPlaceholderColor = RadioTheme.muted,
                unfocusedPlaceholderColor = RadioTheme.muted,
            ),
        )
    }
    if (FavePolicy.allowsClipboardCopy(secret)) {
        field()
    } else {
        val toolbar = LocalTextToolbar.current
        CompositionLocalProvider(LocalTextToolbar provides NoCopyTextToolbar(toolbar)) {
            field()
        }
    }
}

private class NoCopyTextToolbar(
    private val inner: TextToolbar,
) : TextToolbar {
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
        inner.showMenu(
            rect,
            onCopyRequested = null,
            onPasteRequested = onPasteRequested,
            onCutRequested = null,
            onSelectAllRequested = onSelectAllRequested,
        )
    }
}
