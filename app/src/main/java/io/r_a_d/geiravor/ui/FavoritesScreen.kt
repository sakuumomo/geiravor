package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.geiravor_core.FavoriteRow
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status

@Composable
fun FavoritesPane(
    radio: RadioCore,
    status: Status?,
    nick: String,
    onNick: (String) -> Unit,
    onNickPersist: (String) -> Unit,
    canRequest: Boolean?,
    onCanRequest: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(listOf<FavoriteRow>()) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(false) }
    val allowed = RequestPolicy.requestsAllowed(
        isAfkStream = status?.isAfkStream == true,
        requesting = status?.requesting == true,
        canRequest = canRequest,
    )
    val showOff = RequestPolicy.showRequestsOff(
        isAfkStream = status?.isAfkStream,
        requesting = status?.requesting,
        canRequest = canRequest,
    )

    LaunchedEffect(nick) {
        if (!FavoritesPolicy.shouldFetch(nick)) {
            rows = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            delay(350)
            onNickPersist(nick.trim())
            rows = withContext(Dispatchers.IO) { radio.favorites(nick.trim(), 1) }
            message = null
            loading = false
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            rows = emptyList()
            message = false to (RequestPolicy.userFacingError(err) ?: "Favorites failed")
            loading = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = nick,
            onValueChange = onNick,
            singleLine = true,
            placeholder = { Text("Rizon nick") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RadioTheme.text,
                unfocusedTextColor = RadioTheme.text,
                focusedBorderColor = RadioTheme.blue,
                unfocusedBorderColor = RadioTheme.border,
                cursorColor = RadioTheme.blue,
                focusedPlaceholderColor = RadioTheme.muted,
                unfocusedPlaceholderColor = RadioTheme.muted,
            ),
        )
        if (showOff) {
            Text(
                "Requests are off (live DJ or cooldown).",
                color = RadioTheme.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        message?.let { (ok, text) ->
            Text(
                text,
                color = if (ok) RadioTheme.green else RadioTheme.red,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        rows.forEach { row ->
            FavoriteRowView(
                row = row,
                enabled = FavoritesPolicy.rowCanRequest(allowed, row.tracksId) && busyId == null,
                onRequest = {
                    val id = row.tracksId ?: return@FavoriteRowView
                    busyId = id
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { radio.request(id) }
                        }
                        busyId = null
                        result.onSuccess { done ->
                            message = done.ok to done.message
                            onCanRequest(
                                RequestPolicy.canRequestAfterRequest(done.ok, canRequest),
                            )
                        }.onFailure { err ->
                            if (err is CancellationException) {
                                throw err
                            }
                            RequestPolicy.userFacingError(err)?.let { text ->
                                message = false to text
                            }
                        }
                    }
                },
            )
        }
        if (FavoritesPolicy.shouldFetch(nick) && rows.isEmpty() && message == null && !loading) {
            Text(
                "No favorites",
                color = RadioTheme.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FavoriteRowView(
    row: FavoriteRow,
    enabled: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            row.meta,
            color = RadioTheme.text,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        if (row.tracksId != null) {
            Button(
                onClick = onRequest,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadioTheme.blue,
                    contentColor = RadioTheme.text,
                    disabledContainerColor = RadioTheme.border,
                    disabledContentColor = RadioTheme.muted,
                ),
            ) {
                Text("Request", fontSize = 12.sp)
            }
        }
    }
}
