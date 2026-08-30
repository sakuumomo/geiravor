package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.data.GeiravorDb
import io.r_a_d.geiravor.data.ScheduleStore
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.ScheduleDay
import uniffi.geiravor_core.djImageUrl

@Composable
fun ScheduleScreen(
    radio: RadioCore,
    convertTimes: Boolean,
    modifier: Modifier = Modifier,
) {
    var days by remember { mutableStateOf(ScheduleStore.paint.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(days.isEmpty()) }
    val context = LocalContext.current
    var today by remember { mutableStateOf(LocalDate.now().dayOfWeek) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now().dayOfWeek
            val zone = java.time.ZoneId.systemDefault()
            val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
            val wait = (nextMidnight.toEpochMilli() - java.time.Instant.now().toEpochMilli())
                .coerceAtLeast(1_000L)
            delay(wait)
        }
    }

    LaunchedEffect(radio) {
        error = null
        val db = GeiravorDb.get(context)
        val cached = withContext(Dispatchers.IO) { ScheduleStore.load(db) }
        if (cached != null) {
            days = cached
            ScheduleStore.paint = cached
            loading = false
        } else if (days.isEmpty()) {
            loading = true
        }
        try {
            val fetched = withContext(Dispatchers.IO) { radio.schedule() }
            withContext(Dispatchers.IO) { ScheduleStore.save(db, fetched) }
            days = fetched
            ScheduleStore.paint = fetched
            error = null
            (context.applicationContext as? GeiravorApp)?.refreshTheme(processStart = false)
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (days.isEmpty()) {
                error = err.message?.takeIf { it.isNotBlank() } ?: "Couldn't load schedule"
            }
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { text ->
            Text(
                text,
                color = RadioTheme.red,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            loading && days.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = RadioTheme.muted, fontSize = 16.sp)
                }
            }
            days.isEmpty() && error == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing yet", color = RadioTheme.muted, fontSize = 16.sp)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    days.forEach { day ->
                        ScheduleRow(
                            day = day,
                            convertTimes = convertTimes,
                            today = today,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    day: ScheduleDay,
    convertTimes: Boolean,
    today: DayOfWeek,
) {
    val highlight = SchedulePolicy.isToday(day.weekday, today)
    RadioCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (highlight) BorderStroke(2.dp, RadioTheme.highlight) else BorderStroke(1.dp, RadioTheme.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (day.ownerImage.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(djImageUrl(day.ownerImage))
                        .crossfade(true)
                        .build(),
                    contentDescription = day.ownerName,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(day.weekday, color = RadioTheme.text, fontSize = 16.sp)
                if (day.ownerName.isNotEmpty()) {
                    Text(day.ownerName, color = RadioTheme.muted, fontSize = 13.sp)
                }
                if (day.body.isNotEmpty()) {
                    Text(
                        SchedulePolicy.displayBody(day.body, day.weekday, convertTimes),
                        color = RadioTheme.text,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
