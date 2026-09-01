package io.r_a_d.geiravor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.r_a_d.geiravor.R

data class RadioColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    val blue: Color,
    val red: Color,
    val green: Color,
    val link: Color,
    val wallpaper: Int? = null,
    val glass: Boolean = false,
)

object RadioPacks {
    const val DEFAULT_DARK = "default-dark"
    const val DEFAULT_LIGHT = "default-light"
    const val CHRISTMAS = "christmas"
    const val HALLOWEEN = "halloween"
    const val NEWYEARS = "newyears"

    val HOLIDAYS = setOf(CHRISTMAS, HALLOWEEN, NEWYEARS)

    val PICKS = listOf(
        DEFAULT_DARK to "Default",
        DEFAULT_LIGHT to "Default light",
        CHRISTMAS to "Christmas",
        HALLOWEEN to "Halloween",
        NEWYEARS to "New Years",
    )

    /** `/assets/default-dark/css/bulma.min.css` */
    val defaultDark = RadioColors(
        background = Color.hsl(0f, 0f, 0.11f),
        surface = Color.hsl(0f, 0f, 0.13f),
        border = Color.hsl(0f, 0f, 0.24f),
        text = Color.hsl(0f, 0f, 0.96f),
        muted = Color.hsl(0f, 0f, 0.50f),
        onBackground = Color.hsl(0f, 0f, 0.96f),
        onBackgroundMuted = Color.hsl(0f, 0f, 0.50f),
        blue = Color.hsl(208f, 0.27f, 0.39f),
        red = Color.hsl(348f, 0.27f, 0.50f),
        green = Color.hsl(153f, 0.27f, 0.39f),
        link = Color.hsl(208f, 0.27f, 0.50f),
    )

    /**
     * Live `/assets/default-light/css/bulma.min.css` (stock Bulma light; no `--radio-*`).
     * Scheme 221/14%, text 21%/48%, info 198/100/70, link 233/100/63.
     */
    val defaultLight = RadioColors(
        background = Color.hsl(221f, 0.14f, 0.96f),
        surface = Color.hsl(221f, 0.14f, 1f),
        border = Color.hsl(221f, 0.14f, 0.86f),
        text = Color.hsl(221f, 0.14f, 0.21f),
        muted = Color.hsl(221f, 0.14f, 0.48f),
        onBackground = Color.hsl(221f, 0.14f, 0.21f),
        onBackgroundMuted = Color.hsl(221f, 0.14f, 0.48f),
        blue = Color.hsl(198f, 1f, 0.41f),
        red = Color.hsl(348f, 1f, 0.44f),
        green = Color.hsl(153f, 0.53f, 0.40f),
        link = Color.hsl(233f, 1f, 0.50f),
    )

    private val holidayLink = Color.hsl(208f, 0.47f, 0.59f)
    private val holidayGreen = Color.hsl(153f, 0.47f, 0.59f)
    private val holidayRed = Color.hsl(348f, 0.47f, 0.70f)
    private val holidayBg = Color(0xFF2A7CD3)

    /** White cards on wallpaper. Accent `--edenlight-color: #38A8E4`. */
    val christmas = RadioColors(
        background = holidayBg,
        surface = Color.White,
        border = Color(0x59000000),
        text = Color.hsl(222f, 0.14f, 0.29f),
        muted = Color.hsl(222f, 0.14f, 0.48f),
        onBackground = Color.White,
        onBackgroundMuted = Color.hsl(0f, 0f, 0.75f),
        blue = Color(0xFF38A8E4),
        red = holidayRed,
        green = holidayGreen,
        link = holidayLink,
        wallpaper = R.drawable.wallpaper_christmas,
    )

    /** Dark glass cards on wallpaper. Accent `#F4A246`. Official `rgba(0,0,0,0.5)`. */
    val halloween = RadioColors(
        background = holidayBg,
        surface = Color.Black.copy(alpha = 0.5f),
        border = Color(0x99FFFFFF),
        text = Color.White,
        muted = Color.hsl(0f, 0f, 0.82f),
        onBackground = Color.White,
        onBackgroundMuted = Color.hsl(0f, 0f, 0.82f),
        blue = Color(0xFFF4A246),
        red = holidayRed,
        green = holidayGreen,
        link = holidayLink,
        wallpaper = R.drawable.wallpaper_halloween,
        glass = true,
    )

    /** Dark glass cards on wallpaper. Accent `#60709f`. Official `rgba(0,0,0,0.5)`. */
    val newyears = RadioColors(
        background = holidayBg,
        surface = Color.Black.copy(alpha = 0.5f),
        border = Color(0x99FFFFFF),
        text = Color.White,
        muted = Color.hsl(0f, 0f, 0.82f),
        onBackground = Color.White,
        onBackgroundMuted = Color.hsl(0f, 0f, 0.82f),
        blue = Color(0xFF60709F),
        red = holidayRed,
        green = holidayGreen,
        link = holidayLink,
        wallpaper = R.drawable.wallpaper_newyears,
        glass = true,
    )

    fun colors(id: String): RadioColors =
        when (id) {
            DEFAULT_LIGHT -> defaultLight
            CHRISTMAS -> christmas
            HALLOWEEN -> halloween
            NEWYEARS -> newyears
            else -> defaultDark
        }

    /**
     * Selection chrome (theme radios, pager current, tab indicator).
     * Glass packs sit on wallpaper; official `--edenlight-color` can vanish, so lift that
     * same hue toward white until it reads. Play/progress keep [RadioColors.blue].
     */
    fun highlight(colors: RadioColors): Color {
        if (!colors.glass) {
            return colors.blue
        }
        var lifted = colors.blue
        var t = 0f
        while (channelLuma(lifted) < GLASS_HIGHLIGHT_LUMA && t < 1f) {
            t += 0.08f
            lifted = lerp(colors.blue, Color.White, t)
        }
        return lifted
    }

    fun selectionWash(colors: RadioColors): Float =
        if (colors.glass) {
            0.82f
        } else if (channelLuma(colors.surface) > 0.7f) {
            0.40f
        } else {
            0.28f
        }

    /** Mouse-over / focus layer on clickable rows. Material default 0.08 vanishes on glass. */
    fun hoverAlpha(colors: RadioColors): Float =
        if (colors.glass) 0.36f else 0.08f

    fun pressAlpha(colors: RadioColors): Float =
        if (colors.glass) 0.44f else 0.12f

    /** Unselected pager chips; must not match the pane or they vanish on glass. */
    fun chipIdle(colors: RadioColors): Color =
        if (colors.glass) {
            Color.White.copy(alpha = 0.16f)
        } else if (channelLuma(colors.surface) > 0.7f) {
            Color.Black.copy(alpha = 0.08f)
        } else {
            colors.border
        }

    /** Alert dialogs have no frost; glass needs an opaque sheet. */
    fun dialogSurface(colors: RadioColors): Color =
        if (colors.glass) Color(0xF2141414) else colors.surface

    fun paneGutterDp(colors: RadioColors): Int =
        if (colors.wallpaper != null) PANE_INSET_DP else 0

    fun panePadDp(colors: RadioColors): Int = PANE_INSET_DP

    /** Nested cards skip a second scrim; frost already painted by the pane. */
    fun cardFill(colors: RadioColors, nested: Boolean): Color =
        if (nested || colors.glass) Color.Transparent else colors.surface

    fun frost(nested: Boolean): Boolean = !nested

    /** Chip under `← News` (and similar overlays) so hover/type read on wallpaper. */
    fun film(colors: RadioColors): Color =
        if (colors.glass) {
            colors.surface
        } else if (colors.wallpaper != null) {
            colors.surface.copy(alpha = 0.94f)
        } else {
            colors.surface
        }

    private const val PANE_INSET_DP = 16

    fun onHighlight(colors: RadioColors): Color =
        if (channelLuma(highlight(colors)) > 0.45f) Color(0xDE1A1A1A) else Color.White

    internal fun channelLuma(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private const val GLASS_HIGHLIGHT_LUMA = 0.85f
}

object RadioTheme {
    var packId: String = RadioPacks.DEFAULT_DARK
        private set
    var current: RadioColors by mutableStateOf(RadioPacks.defaultDark)
        private set

    fun apply(id: String) {
        packId = id
        current = RadioPacks.colors(id)
    }

    val background get() = current.background
    val surface get() = current.surface
    val border get() = current.border
    val text get() = current.text
    val muted get() = current.muted
    val onBackground get() = current.onBackground
    val onBackgroundMuted get() = current.onBackgroundMuted
    val blue get() = current.blue
    val red get() = current.red
    val green get() = current.green
    val link get() = current.link
    val wallpaper get() = current.wallpaper
    val glass get() = current.glass
    val highlight get() = RadioPacks.highlight(current)
    val onHighlight get() = RadioPacks.onHighlight(current)
    val selectionFill get() = highlight.copy(alpha = RadioPacks.selectionWash(current))
    val chipIdle get() = RadioPacks.chipIdle(current)
    val dialogSurface get() = RadioPacks.dialogSurface(current)
    val film get() = RadioPacks.film(current)
    val paneGutterDp get() = RadioPacks.paneGutterDp(current)
    val panePadDp get() = RadioPacks.panePadDp(current)
    val onBlue get(): Color {
        val l = RadioPacks.channelLuma(current.blue)
        return if (l > 0.45f) Color(0xDE1A1A1A) else Color.White
    }
}
