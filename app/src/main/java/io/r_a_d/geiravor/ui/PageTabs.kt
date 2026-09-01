package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PageTabs(
    current: Int,
    last: Int,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!PagerPolicy.visible(last)) {
        return
    }
    var jumpOpen by remember { mutableStateOf(false) }
    val items = PagerPolicy.items(current, last)
    val prev = items.first { it.kind == PagerPolicy.Kind.Prev }
    val next = items.first { it.kind == PagerPolicy.Kind.Next }
    val middle = items.filter {
        it.kind != PagerPolicy.Kind.Prev && it.kind != PagerPolicy.Kind.Next
    }
    val digits = PagerPolicy.digitCount(last)
    val pageMinWidth = pageSlotMinWidth(digits)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerChip(item = prev, onClick = {
            onPage(PagerPolicy.clampPage(prev.page, last))
        })
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                middle.forEach { item ->
                    key("${item.kind}-${item.page}") {
                        when (item.kind) {
                            PagerPolicy.Kind.Jump -> PagerChip(
                                item = item,
                                onClick = { jumpOpen = true },
                            )
                            else -> PagerChip(
                                item = item,
                                minWidth = pageMinWidth,
                                onClick = { onPage(PagerPolicy.clampPage(item.page, last)) },
                            )
                        }
                    }
                }
            }
        }
        PagerChip(item = next, onClick = {
            onPage(PagerPolicy.clampPage(next.page, last))
        })
    }
    if (jumpOpen) {
        JumpPageDialog(
            current = current,
            last = last,
            onGo = { page ->
                jumpOpen = false
                onPage(page)
            },
            onDismiss = { jumpOpen = false },
        )
    }
}

private val PagerTextSize = 14.sp

@Composable
private fun pageSlotMinWidth(digits: Int): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = remember(measurer, density) {
        val style = TextStyle(fontSize = PagerTextSize)
        (0..9).maxOf { digit ->
            measurer.measure(digit.toString(), style).size.width
        }.toFloat()
    }
    return with(density) { PagerPolicy.pageSlotWidthPx(digits, widest).toDp() }
}

@Composable
private fun PagerChip(
    item: PagerPolicy.Item,
    onClick: () -> Unit,
    minWidth: Dp? = null,
) {
    val selected = item.current
    Text(
        PagerPolicy.label(item),
        color = when {
            !item.enabled -> RadioTheme.muted.copy(alpha = 0.35f)
            selected -> RadioTheme.onHighlight
            else -> RadioTheme.text
        },
        fontSize = PagerTextSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .then(if (minWidth != null) Modifier.widthIn(min = minWidth) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) RadioTheme.highlight else RadioTheme.chipIdle)
            .clickable(enabled = item.enabled && !selected, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun JumpPageDialog(
    current: Int,
    last: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { incoming ->
                        text = PagerPolicy.jumpInput(incoming, last)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RadioTheme.text,
                        unfocusedTextColor = RadioTheme.text,
                        focusedBorderColor = RadioTheme.blue,
                        unfocusedBorderColor = RadioTheme.border,
                        cursorColor = RadioTheme.blue,
                    ),
                )
                Text(
                    "1–$last",
                    color = RadioTheme.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = text.toIntOrNull() ?: return@TextButton
                    onGo(PagerPolicy.clampJump(parsed, last))
                },
            ) {
                Text("Go", color = RadioTheme.text)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RadioTheme.muted)
            }
        },
        containerColor = RadioTheme.dialogSurface,
        titleContentColor = RadioTheme.text,
        textContentColor = RadioTheme.text,
    )
}
