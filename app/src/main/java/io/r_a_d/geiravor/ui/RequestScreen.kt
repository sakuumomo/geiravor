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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.SearchHit
import uniffi.geiravor_core.Status

@Composable
fun RequestPane(
    radio: RadioCore,
    status: Status?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(listOf<SearchHit>()) }
    var canRequest by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var searching by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        canRequest = withContext(Dispatchers.IO) {
            runCatching { radio.canRequest() }.getOrNull()
        }
    }
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        runCatching {
            withContext(Dispatchers.IO) { radio.search(trimmed, 1) }
        }.onSuccess { page ->
            hits = page.data
            message = null
        }.onFailure { err ->
            hits = emptyList()
            message = false to (err.message ?: "Search failed")
        }
        searching = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search") },
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
        hits.forEach { hit ->
            SearchRow(
                hit = hit,
                enabled = RequestPolicy.rowCanRequest(allowed, hit.requestable) && busyId == null,
                onRequest = {
                    busyId = hit.id
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { radio.request(hit.id) }
                        }
                        busyId = null
                        result.onSuccess { done ->
                            message = done.ok to done.message
                            canRequest = RequestPolicy.canRequestAfterRequest(done.ok, canRequest)
                        }.onFailure { err ->
                            message = false to (err.message ?: "Request failed")
                        }
                    }
                },
            )
        }
        if (query.isNotBlank() && hits.isEmpty() && message == null && !searching) {
            Text(
                "No results",
                color = RadioTheme.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchRow(
    hit: SearchHit,
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
            "${hit.artist} - ${hit.title}",
            color = RadioTheme.text,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
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
