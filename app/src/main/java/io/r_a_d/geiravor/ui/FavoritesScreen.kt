package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.r_a_d.geiravor.data.FaveListStore
import io.r_a_d.geiravor.data.GeiravorDb
import io.r_a_d.geiravor.playback.FavePolicy
import io.r_a_d.geiravor.radio.ListingCache
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
    listFallback: String,
    onNick: (String) -> Unit,
    onNickPersist: (String) -> Unit,
    canRequest: Boolean?,
    onCanRequest: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val db = remember { GeiravorDb.get(context) }
    var listing by remember {
        val fetch = nick.trim().ifEmpty { listFallback.trim() }
        mutableStateOf(
            if (fetch.isNotEmpty()) fetch to SessionCache.favesCurrent(fetch) else "" to 1,
        )
    }
    var lastPage by remember {
        val fetch = nick.trim().ifEmpty { listFallback.trim() }
        mutableStateOf(
            PanePolicy.lastPage(
                ListingCache.favesTotal(fetch) ?: 0,
                ListingCache.favesVisible().coerceAtLeast(1),
            ),
        )
    }
    var rows by remember {
        val fetch = nick.trim().ifEmpty { listFallback.trim() }
        val p = SessionCache.favesCurrent(fetch)
        val vis = ListingCache.favesVisible().coerceAtLeast(1)
        mutableStateOf(
            PanePolicy.window(
                ListingCache.favesRows(fetch),
                PanePolicy.startIndex(p, vis),
                vis,
                PanePolicy.FAVES_PER_PAGE,
                ListingCache.favesServerLast(fetch) ?: 1,
            ).orEmpty(),
        )
    }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var loading by remember {
        mutableStateOf(
            rows.isEmpty() &&
                (FavoritesPolicy.shouldFetch(nick) || FavoritesPolicy.shouldFetch(listFallback)),
        )
    }
    var visible by remember { mutableStateOf(ListingCache.favesVisible()) }
    val listRevision by remember {
        RadioStore.state.map { it.faveListRevision }.distinctUntilChanged()
    }.collectAsState(initial = 0)
    var lastRevision by remember { mutableStateOf(listRevision) }
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

    LaunchedEffect(nick, listFallback) {
        val fetch = nick.trim().ifEmpty { listFallback.trim() }
        if (!FavoritesPolicy.shouldFetch(fetch)) {
            listing = "" to 1
            lastPage = 1
            rows = emptyList()
            loading = false
            return@LaunchedEffect
        }
        if (listing.first == fetch) {
            return@LaunchedEffect
        }
        if (listing.first.isNotEmpty()) {
            delay(350)
        }
        listing = fetch to SessionCache.favesCurrent(fetch)
    }

    fun showFaves(uiPage: Int, fit: Int) {
        val total = ListingCache.favesTotal(committed) ?: 0
        val last = PanePolicy.lastPage(total, fit)
        val clamped = PagerPolicy.clampPage(uiPage, last)
        val start = PanePolicy.startIndex(clamped, fit)
        val window = PanePolicy.window(
            ListingCache.favesRows(committed),
            start,
            fit,
            PanePolicy.FAVES_PER_PAGE,
            ListingCache.favesServerLast(committed) ?: 1,
        )
        rows = window.orEmpty()
        lastPage = last
        SessionCache.putFavesCurrent(committed, clamped)
        if (clamped != uiPage && committed.isNotEmpty()) {
            listing = committed to clamped
        }
    }

    LaunchedEffect(listing, listRevision, visible) {
        if (!FavoritesPolicy.shouldFetch(committed)) {
            rows = emptyList()
            lastPage = 1
            if (!FavoritesPolicy.shouldFetch(nick) && !FavoritesPolicy.shouldFetch(listFallback)) {
                loading = false
            }
            return@LaunchedEffect
        }
        val fit = visible.takeIf { it > 0 } ?: ListingCache.favesVisible().coerceAtLeast(1)
        val bumped = listRevision != lastRevision
        lastRevision = listRevision
        if (bumped) {
            ListingCache.dropFaves(committed)
            withContext(Dispatchers.IO) { FaveListStore.hydrateNick(db, committed) }
        } else if (ListingCache.faves(committed, 1) == null) {
            withContext(Dispatchers.IO) { FaveListStore.hydrateNick(db, committed) }
        }
        showFaves(page, fit)
        if (rows.isNotEmpty()) {
            loading = false
        }
        if (visible <= 0) {
            return@LaunchedEffect
        }
        if (rows.isEmpty()) {
            loading = true
        }
        val per = PanePolicy.FAVES_PER_PAGE
        val start = PanePolicy.startIndex(page, visible)
        val keep = FavePolicy.membershipNicks(homeNick, listFallback)
        suspend fun pull(server: Int) {
            val fetched = withContext(Dispatchers.IO) { radio.favorites(committed, server) }
            ListingCache.putFaves(committed, fetched)
            if (FavoritesPolicy.shouldWriteListing(keep, homeNick, nick, committed)) {
                withContext(Dispatchers.IO) { FaveListStore.savePage(db, committed, fetched) }
            }
        }
        suspend fun pullLast(refresh: Boolean) {
            var last = ListingCache.favesServerLast(committed) ?: 1
            if (last <= 1) {
                return
            }
            if (refresh || ListingCache.faves(committed, last) == null) {
                pull(last)
            }
            last = ListingCache.favesServerLast(committed) ?: last
            if (last > 1 && ListingCache.faves(committed, last) == null) {
                pull(last)
            }
        }
        try {
            val refresh = page == 1 || bumped
            if (refresh || ListingCache.faves(committed, 1) == null) {
                pull(1)
            }
            pullLast(refresh)
            val last = ListingCache.favesServerLast(committed) ?: 1
            val need = PanePolicy.serverPages(start, visible, per, last)
            for (server in need) {
                if (server == 1 && refresh) {
                    continue
                }
                if (ListingCache.faves(committed, server) == null) {
                    pull(server)
                }
            }
            message = null
            loading = false
            val total = ListingCache.favesTotal(committed) ?: 0
            val uiLast = PanePolicy.lastPage(total, visible)
            val clamped = PagerPolicy.clampPage(page, uiLast)
            if (FavoritesPolicy.shouldRememberAfterFetch(homeNick, nick, committed)) {
                onNickPersist(committed)
            }
            if (clamped != page) {
                listing = committed to clamped
            } else {
                showFaves(clamped, visible)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (rows.isEmpty()) {
                lastPage = 1
                message = false to (RequestPolicy.userFacingError(err) ?: "Favorites failed")
            }
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
            placeholder = {
                Text(listFallback.trim().ifEmpty { "Rizon nick" })
            },
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
                        runCatching { radio.requestRandomFavorite(committed) }
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
            enabled = FavoritesPolicy.randomCanRequest(allowed, committed) && busyId == null,
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
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val measured = PanePolicy.songThatFit(maxHeight.value)
            LaunchedEffect(measured) {
                val fit = ListingCache.freezeFavesVisible(measured)
                if (visible != fit) {
                    visible = fit
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                rows.forEach { row ->
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
                    committed == nick.trim().ifEmpty { listFallback.trim() } &&
                    FavoritesPolicy.shouldFetch(committed) &&
                    rows.isEmpty() &&
                    message == null &&
                    !loading
                ) {
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
