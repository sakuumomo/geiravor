package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.radio.SessionCache
import kotlin.coroutines.cancellation.CancellationException
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
    canRequest: Boolean?,
    onCanRequest: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(SessionCache.searchQuery()) }
    var listing by remember {
        mutableStateOf(SessionCache.searchQuery() to SessionCache.searchCurrent())
    }
    var lastPage by remember { mutableStateOf(1) }
    var hits by remember { mutableStateOf(listOf<SearchHit>()) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var searching by remember { mutableStateOf(SessionCache.searchQuery().isNotEmpty()) }
    val listState = rememberLazyListState()
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

    val committed = listing.first
    val page = listing.second

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            listing = "" to 1
            lastPage = 1
            hits = emptyList()
            searching = false
            SessionCache.putSearchQuery("")
            return@LaunchedEffect
        }
        if (listing.first == trimmed) {
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        listing = trimmed to 1
        SessionCache.putSearchQuery(trimmed, 1)
    }

    LaunchedEffect(listing) {
        if (committed.isEmpty()) {
            hits = emptyList()
            lastPage = 1
            if (query.trim().isEmpty()) {
                searching = false
            }
            return@LaunchedEffect
        }
        searching = true
        try {
            val result = withContext(Dispatchers.IO) { radio.search(committed, page) }
            hits = result.data
            lastPage = result.lastPage.toInt().coerceAtLeast(1)
            SessionCache.putSearchQuery(committed, page)
            if (page > lastPage) {
                listing = committed to lastPage
            }
            message = null
            searching = false
            listState.scrollToItem(0)
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            hits = emptyList()
            lastPage = 1
            message = false to (RequestPolicy.userFacingError(err) ?: "Search failed")
            searching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(hits, key = { it.id }) { hit ->
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
            if (
                committed == query.trim() &&
                committed.isNotEmpty() &&
                hits.isEmpty() &&
                message == null &&
                !searching
            ) {
                item {
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
        if (PagerPolicy.visible(lastPage)) {
            PageTabs(
                current = page,
                last = lastPage,
                onPage = { listing = committed to it },
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${hit.artist} - ${hit.title}",
            color = RadioTheme.text,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Button(
            onClick = onRequest,
            enabled = enabled,
            colors = radioButtonColors(),
        ) {
            Text("Request", fontSize = 12.sp)
        }
    }
}
