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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerChip(item = prev, widthDp = 40, onClick = {
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
                                widthDp = 40,
                                onClick = { jumpOpen = true },
                            )
                            else -> PagerChip(
                                item = item,
                                widthDp = 12 * digits + 8,
                                onClick = { onPage(PagerPolicy.clampPage(item.page, last)) },
                            )
                        }
                    }
                }
            }
        }
        PagerChip(item = next, widthDp = 40, onClick = {
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

@Composable
private fun PagerChip(
    item: PagerPolicy.Item,
    widthDp: Int,
    onClick: () -> Unit,
) {
    val selected = item.current
    Text(
        PagerPolicy.label(item),
        color = if (item.enabled) RadioTheme.text else RadioTheme.muted.copy(alpha = 0.35f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .width(widthDp.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) RadioTheme.blue else RadioTheme.surface)
            .clickable(enabled = item.enabled && !selected, onClick = onClick)
            .padding(vertical = 8.dp),
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
                        text = incoming.filter { it.isDigit() }.take(6)
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
        containerColor = RadioTheme.surface,
        titleContentColor = RadioTheme.text,
        textContentColor = RadioTheme.text,
    )
}
