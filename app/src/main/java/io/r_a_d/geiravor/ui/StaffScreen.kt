package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.data.GeiravorDb
import io.r_a_d.geiravor.data.StaffStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.StaffMember
import uniffi.geiravor_core.djImageUrl

@Composable
fun StaffScreen(
    radio: RadioCore,
    modifier: Modifier = Modifier,
) {
    var members by remember { mutableStateOf(StaffStore.paint.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(members.isEmpty()) }
    val context = LocalContext.current

    LaunchedEffect(radio) {
        error = null
        val db = GeiravorDb.get(context)
        val cached = withContext(Dispatchers.IO) { StaffStore.load(db) }
        if (cached != null) {
            members = cached
            StaffStore.paint = cached
            loading = false
        } else if (members.isEmpty()) {
            loading = true
        }
        try {
            val fetched = withContext(Dispatchers.IO) { radio.staff() }
            withContext(Dispatchers.IO) { StaffStore.save(db, fetched) }
            members = fetched
            StaffStore.paint = fetched
            error = null
            (context.applicationContext as? GeiravorApp)?.refreshTheme(processStart = false)
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (members.isEmpty()) {
                error = err.message?.takeIf { it.isNotBlank() } ?: "Couldn't load staff"
            }
        }
        loading = false
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
            loading && members.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = RadioTheme.muted, fontSize = 16.sp)
                }
            }
            members.isEmpty() && error == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing yet", color = RadioTheme.muted, fontSize = 16.sp)
                }
            }
            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthDp = maxWidth.value.toInt()
                    val groups = StaffPolicy.groups(members)
                    val staff = groups.first { it.first == "staff" }
                    val dev = groups.first { it.first == "dev" }
                    val djs = groups.first { it.first == "dj" }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (StaffPolicy.pairStaffAndDev(widthDp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                StaffGroup(
                                    role = staff.first,
                                    members = staff.second,
                                    columns = StaffPolicy.PHONE_COLUMNS,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(RadioTheme.border),
                                )
                                StaffGroup(
                                    role = dev.first,
                                    members = dev.second,
                                    columns = StaffPolicy.PHONE_COLUMNS,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            StaffGroup(
                                role = staff.first,
                                members = staff.second,
                                columns = StaffPolicy.columns(widthDp, staff.first),
                            )
                            StaffGroup(
                                role = dev.first,
                                members = dev.second,
                                columns = StaffPolicy.columns(widthDp, dev.first),
                            )
                        }
                        StaffGroup(
                            role = djs.first,
                            members = djs.second,
                            columns = StaffPolicy.columns(widthDp, djs.first),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun StaffGroup(
    role: String,
    members: List<StaffMember>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            StaffPolicy.label(role),
            color = NewsPolicy.nameColor(role, RadioTheme.text),
            fontSize = 18.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(RadioTheme.border),
        )
        StaffPolicy.rows(members, columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { member ->
                    StaffCard(member = member, modifier = Modifier.weight(1f))
                }
                repeat(columns - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StaffCard(member: StaffMember, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (member.image.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(djImageUrl(member.image))
                    .crossfade(true)
                    .build(),
                contentDescription = member.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            member.name,
            color = NewsPolicy.nameColor(member.role),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
