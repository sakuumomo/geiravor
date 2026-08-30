package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.radio.SessionCache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.geiravor_core.FavoriteRow
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.songRequestable

@Composable
fun FavoritesPane(
    radio: RadioCore,
    status: Status?,
    nick: String,
    homeNick: String,
    onNick: (String) -> Unit,
    onNickPersist: (String) -> Unit,
    canRequest: Boolean?,
    onCanRequest: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var listing by remember { mutableStateOf("" to 1) }
    var lastPage by remember { mutableStateOf(1) }
    var rows by remember { mutableStateOf(listOf<FavoriteRow>()) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(FavoritesPolicy.shouldFetch(nick)) }
    val listState = rememberLazyListState()
    val listRevision by remember {
        RadioStore.state.map { it.faveListRevision }.distinctUntilChanged()
    }.collectAsState(initial = 0)
    var lastScrolledListing by remember { mutableStateOf<Pair<String, Int>?>(null) }
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

    LaunchedEffect(nick) {
        if (!FavoritesPolicy.shouldFetch(nick)) {
            listing = "" to 1
            lastPage = 1
            rows = emptyList()
            loading = false
            return@LaunchedEffect
        }
        val trimmed = nick.trim()
        if (listing.first == trimmed) {
            return@LaunchedEffect
        }
        loading = true
        if (listing.first.isNotEmpty() || FavoritesPolicy.shouldCommitHome(homeNick, trimmed)) {
            delay(350)
        }
        if (FavoritesPolicy.shouldCommitHome(homeNick, trimmed)) {
            onNickPersist(trimmed)
        }
        listing = trimmed to SessionCache.favesCurrent(trimmed)
    }

    LaunchedEffect(listing, listRevision) {
        if (!FavoritesPolicy.shouldFetch(committed)) {
            rows = emptyList()
            lastPage = 1
            if (!FavoritesPolicy.shouldFetch(nick)) {
                loading = false
            }
            return@LaunchedEffect
        }
        loading = true
        val listingChanged = lastScrolledListing != listing
        try {
            val fetched = withContext(Dispatchers.IO) { radio.favorites(committed, page) }
            rows = fetched.data
            lastPage = fetched.lastPage.toInt().coerceAtLeast(1)
            SessionCache.putFavesCurrent(committed, page)
            if (fetched.data.isEmpty() && page > 1) {
                listing = committed to (page - 1).coerceAtLeast(1)
            } else if (listingChanged) {
                lastScrolledListing = listing
                message = null
                listState.scrollToItem(0)
            }
            loading = false
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            rows = emptyList()
            lastPage = 1
            message = false to (RequestPolicy.userFacingError(err) ?: "Favorites failed")
            loading = false
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
            value = nick,
            onValueChange = onNick,
            singleLine = true,
            placeholder = { Text("Rizon nick") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onNickPersist(nick.trim())
                    focus.clearFocus()
                },
            ),
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
        Button(
            onClick = {
                busyId = FavoritesPolicy.RANDOM_BUSY_ID
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { radio.requestRandomFavorite(nick.trim()) }
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
            enabled = FavoritesPolicy.randomCanRequest(allowed, nick) && busyId == null,
            modifier = Modifier.fillMaxWidth(),
            colors = radioButtonColors(),
        ) {
            Text("Request random")
        }
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
            itemsIndexed(
                rows,
                key = { index, row -> "${index}:${row.tracksId}:${row.meta}" },
            ) { _, row ->
                FavoriteRowView(
                    row = row,
                    enabled = FavoritesPolicy.rowCanRequest(
                        allowed = allowed,
                        tracksId = row.tracksId,
                        requestable = songRequestable(
                            lastPlayed = row.lastPlayed,
                            lastRequested = row.lastRequested,
                            requestCount = row.requestCount,
                            now = status?.current ?: 0,
                        ),
                    ) && busyId == null,
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
            if (
                committed == nick.trim() &&
                FavoritesPolicy.shouldFetch(committed) &&
                rows.isEmpty() &&
                message == null &&
                !loading
            ) {
                item {
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
private fun FavoriteRowView(
    row: FavoriteRow,
    enabled: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonDefaults.MinHeight)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.meta,
            color = RadioTheme.text,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        val requestable = row.tracksId != null
        Button(
            onClick = onRequest,
            enabled = enabled && requestable,
            modifier = Modifier.alpha(if (requestable) 1f else 0f),
            colors = radioButtonColors(),
        ) {
            Text("Request", fontSize = 12.sp)
        }
    }
}
