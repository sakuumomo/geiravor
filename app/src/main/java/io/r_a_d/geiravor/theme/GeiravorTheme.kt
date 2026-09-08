package io.r_a_d.geiravor.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import uniffi.geiravor_core.ThemePack

@Composable
fun GeiravorTheme(pack: ThemePack, content: @Composable () -> Unit) {
    val t = tokens(pack)
    val scheme = if (pack == ThemePack.DEFAULT_LIGHT) {
        lightColorScheme(
            primary = t.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            background = t.background,
            onBackground = t.onBackground,
            surface = t.surface,
            onSurface = t.text,
            secondary = t.highlight,
            error = t.red,
        )
    } else {
        darkColorScheme(
            primary = t.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            background = t.background,
            onBackground = t.onBackground,
            surface = t.surface,
            onSurface = t.text,
            secondary = t.highlight,
            error = t.red,
        )
    }
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
