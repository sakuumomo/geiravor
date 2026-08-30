package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.compatBlur
import kotlin.math.roundToInt

private val cardShape = RoundedCornerShape(6.dp)
private val glassBlur = 15.dp

@Composable
fun GeiravorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = radioColorScheme(), content = content)
}

@Composable
fun radioButtonColors() = ButtonDefaults.buttonColors(
    containerColor = RadioTheme.blue,
    contentColor = RadioTheme.onBlue,
    disabledContainerColor = RadioTheme.border,
    disabledContentColor = RadioTheme.muted,
)

@Composable
fun RadioPane(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (RadioTheme.wallpaper == null) {
        Column(modifier, content = content)
        return
    }
    Box(modifier.clip(cardShape)) {
        if (RadioTheme.glass) {
            FrostBackdrop(Modifier.matchParentSize())
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(RadioTheme.surface.copy(alpha = 0.94f)),
            )
        }
        Column(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun RadioCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, RadioTheme.border),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.clip(cardShape)) {
        FrostBackdrop(Modifier.matchParentSize())
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (RadioTheme.glass) androidx.compose.ui.graphics.Color.Transparent else RadioTheme.surface,
            ),
            border = border,
            content = { content() },
        )
    }
}

@Composable
fun FrostBackdrop(modifier: Modifier = Modifier) {
    val wallpaper = RadioTheme.wallpaper
    if (!RadioTheme.glass || wallpaper == null) {
        return
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp }
    val screenH = with(density) { config.screenHeightDp.dp }
    Box(
        modifier
            .clip(cardShape)
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        Image(
            painter = painterResource(wallpaper),
            contentDescription = null,
            modifier = Modifier
                .requiredSize(screenW, screenH)
                .offset { IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()) }
                .compatBlur(glassBlur),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .matchParentSize()
                .background(RadioTheme.surface),
        )
    }
}

@Composable
private fun radioColorScheme(): ColorScheme {
    val colors = RadioTheme.current
    val lightPage = colors.onBackground.red + colors.onBackground.green + colors.onBackground.blue < 1.5f
    val scheme = if (lightPage) {
        lightColorScheme(
            primary = colors.blue,
            onPrimary = RadioTheme.onBlue,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.text,
            error = colors.red,
        )
    } else {
        darkColorScheme(
            primary = colors.blue,
            onPrimary = RadioTheme.onBlue,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.text,
            error = colors.red,
        )
    }
    return scheme
}
