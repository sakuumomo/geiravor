package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.canBlur
import io.r_a_d.geiravor.theme.LocalTokens

@Composable
fun Pane(
    modifier: Modifier = Modifier,
    hug: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val gutter = if (t.wallpaper != null) 16.dp else 0.dp
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .padding(gutter)
            .then(if (hug) Modifier.wrapContentHeight() else Modifier.fillMaxSize())
            .clip(shape)
            .background(t.surface)
            .then(
                if (t.glass && !canBlur()) Modifier
                else Modifier.border(1.dp, t.border, shape),
            )
            .padding(16.dp),
        content = content,
    )
}
