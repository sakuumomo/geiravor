package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.radio.ListingCache
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
    var lastPage by remember {
        mutableStateOf(
            PanePolicy.lastPage(
                ListingCache.searchTotal(SessionCache.searchQuery()) ?: 0,
                ListingCache.searchVisible().coerceAtLeast(1),
            ),
        )
    }
    var hits by remember {
        val q = SessionCache.searchQuery()
        val p = SessionCache.searchCurrent()
        val vis = ListingCache.searchVisible().coerceAtLeast(1)
        mutableStateOf(
            PanePolicy.window(
                ListingCache.searchRows(q),
                PanePolicy.startIndex(p, vis),
                vis,
                PanePolicy.SEARCH_PER_PAGE,
                ListingCache.searchServerLast(q) ?: 1,
            ).orEmpty(),
        )
    }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var searching by remember {
        mutableStateOf(
            SessionCache.searchQuery().isNotEmpty() &&
                ListingCache.search(SessionCache.searchQuery(), 1) == null,
        )
    }
    var visible by remember { mutableStateOf(ListingCache.searchVisible()) }
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

    fun showSearch(uiPage: Int, fit: Int) {
        val total = ListingCache.searchTotal(committed) ?: 0
        val last = PanePolicy.lastPage(total, fit)
        val clamped = PagerPolicy.clampPage(uiPage, last)
        val start = PanePolicy.startIndex(clamped, fit)
        val window = PanePolicy.window(
            ListingCache.searchRows(committed),
            start,
            fit,
            PanePolicy.SEARCH_PER_PAGE,
            ListingCache.searchServerLast(committed) ?: 1,
        )
        hits = window.orEmpty()
        lastPage = last
        SessionCache.putSearchQuery(committed, clamped)
        if (clamped != uiPage && committed.isNotEmpty()) {
            listing = committed to clamped
        }
    }

    LaunchedEffect(listing, visible) {
        if (committed.isEmpty()) {
            hits = emptyList()
            lastPage = 1
            if (query.trim().isEmpty()) {
                searching = false
            }
            return@LaunchedEffect
        }
        val fit = visible.takeIf { it > 0 } ?: ListingCache.searchVisible().coerceAtLeast(1)
        showSearch(page, fit)
        if (hits.isNotEmpty()) {
            searching = false
        }
        if (visible <= 0) {
            return@LaunchedEffect
        }
        if (hits.isEmpty()) {
            searching = true
        }
        val per = PanePolicy.SEARCH_PER_PAGE
        val start = PanePolicy.startIndex(page, visible)
        suspend fun pull(server: Int) {
            val result = withContext(Dispatchers.IO) { radio.search(committed, server) }
            ListingCache.putSearch(committed, result)
        }
        try {
            val refresh = page == 1
            if (refresh || ListingCache.search(committed, 1) == null) {
                pull(1)
            }
            val last = ListingCache.searchServerLast(committed) ?: 1
            val need = PanePolicy.serverPages(start, visible, per, last)
            for (server in need) {
                if (server == 1 && refresh) {
                    continue
                }
                if (ListingCache.search(committed, server) == null) {
                    pull(server)
                }
            }
            val total = ListingCache.searchTotal(committed) ?: 0
            val uiLast = PanePolicy.lastPage(total, visible)
            val clamped = PagerPolicy.clampPage(page, uiLast)
            message = null
            searching = false
            if (clamped != page) {
                listing = committed to clamped
            } else {
                showSearch(clamped, visible)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (hits.isEmpty()) {
                lastPage = 1
                message = false to (RequestPolicy.userFacingError(err) ?: "Search failed")
            }
            searching = false
        }
    }

    RadioPane(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val measured = PanePolicy.songThatFit(maxHeight.value)
            LaunchedEffect(measured) {
                val fit = ListingCache.freezeSearchVisible(measured)
                if (visible != fit) {
                    visible = fit
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
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
