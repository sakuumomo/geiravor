package io.r_a_d.geiravor.playback

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.util.TypedValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.r_a_d.geiravor.compat.displayWidthPx
import uniffi.geiravor_core.SongProgress
import uniffi.geiravor_core.Status

data class NowPlayingCard(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val artworkUrl: String,
)

object SessionMetadata {
    private const val ELLIPSIS = "…"

    class ShadeSpace(
        val maxWidthPx: Float,
        val widthOf: (String) -> Float,
    )

    fun shadeTextPaint(context: Context): TextPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val attrs = intArrayOf(
            android.R.attr.textSize,
            android.R.attr.fontFamily,
            android.R.attr.textStyle,
            android.R.attr.letterSpacing,
        )
        val ta = context.obtainStyledAttributes(
            android.R.style.TextAppearance_Material_Notification_Line2,
            attrs,
        )
        try {
            val size = ta.getDimension(0, 0f)
            paint.textSize = if (size > 0f) {
                size
            } else {
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    14f,
                    context.resources.displayMetrics,
                )
            }
            val family = ta.getString(1)
            val style = ta.getInt(2, Typeface.NORMAL)
            if (family != null) {
                paint.typeface = Typeface.create(family, style)
            } else if (style != Typeface.NORMAL) {
                paint.typeface = Typeface.defaultFromStyle(style)
            }
            if (ta.hasValue(3)) {
                paint.letterSpacing = ta.getFloat(3, 0f)
            }
        } finally {
            ta.recycle()
        }
        paint.density = context.resources.displayMetrics.density
        return paint
    }

    fun shadeTextMaxWidthPx(context: Context): Float {
        val icon = context.resources.getDimension(android.R.dimen.notification_large_icon_width)
        return shadeTextMaxWidthPx(
            widthPx = context.displayWidthPx(),
            largeIconPx = icon,
            compactActionPx = icon,
        )
    }

    fun shadeTextMaxWidthPx(widthPx: Int, largeIconPx: Float, compactActionPx: Float): Float =
        (widthPx - largeIconPx - compactActionPx).coerceAtLeast(largeIconPx)

    fun shadeSpace(context: Context): ShadeSpace {
        val paint = shadeTextPaint(context)
        return ShadeSpace(
            maxWidthPx = shadeTextMaxWidthPx(context),
            widthOf = { paint.measureText(it) },
        )
    }

    fun present(value: String): String? = value.trim().takeIf { it.isNotEmpty() }

    fun shadeArtist(card: NowPlayingCard, space: ShadeSpace? = null): String {
        val artist = present(card.artist)
        val dj = present(card.albumArtist)
        return when {
            artist == null -> dj.orEmpty()
            dj == null -> artist
            else -> ellipsizeKeepingSuffix(artist, " | $dj", space)
        }
    }

    fun ellipsizeKeepingSuffix(prefix: String, suffix: String, space: ShadeSpace?): String {
        val full = prefix + suffix
        if (space == null || space.widthOf(full) <= space.maxWidthPx) {
            return full
        }
        val ellipsisAndSuffix = ELLIPSIS + suffix
        if (space.widthOf(ellipsisAndSuffix) >= space.maxWidthPx) {
            return ellipsisAndSuffix
        }
        var cut = prefix.length
        while (cut > 0) {
            cut = prefix.offsetByCodePoints(cut, -1)
            val candidate = prefix.substring(0, cut) + ellipsisAndSuffix
            if (space.widthOf(candidate) <= space.maxWidthPx) {
                return candidate
            }
        }
        return ellipsisAndSuffix
    }

    fun card(status: Status?): NowPlayingCard? {
        if (status == null) return null
        return NowPlayingCard(
            title = status.title.trim(),
            artist = status.artist.trim(),
            albumArtist = status.dj.name.trim(),
            artworkUrl = "https://r-a-d.io/api/dj-image/${status.dj.image}",
        )
    }

    fun fromStatus(
        status: Status?,
        shade: ShadeSpace? = null,
        durationMs: Long = C.TIME_UNSET,
    ): MediaMetadata? {
        val card = card(status) ?: return null
        return withSongDuration(
            sessionMetadata(card, shadeArtist(card, shade)).buildUpon()
                .setArtworkUri(Uri.parse(card.artworkUrl))
                .build(),
            durationMs,
        )
    }

    fun withSongDuration(metadata: MediaMetadata, durationMs: Long): MediaMetadata {
        val duration = durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        if (metadata.durationMs == duration) {
            return metadata
        }
        return metadata.buildUpon().setDurationMs(duration).build()
    }

    fun sessionMetadata(card: NowPlayingCard, shadeLine: String = shadeArtist(card)): MediaMetadata {
        val artist = present(card.artist)
        val dj = present(card.albumArtist)
        val builder = MediaMetadata.Builder()
            .setTitle(card.title)
            .setDisplayTitle(card.title)
            .setArtist(shadeLine)
        dj?.let { builder.setAlbumArtist(it) }
        if (artist != null) {
            builder.setSubtitle(artist)
            dj?.let { builder.setDescription(it) }
        } else {
            dj?.let { builder.setSubtitle(it) }
        }
        return builder.build()
    }

    fun liveMediaItem(
        status: Status?,
        mediaId: String = AutoBrowse.NOW_PLAYING,
        shade: ShadeSpace? = null,
        durationMs: Long = C.TIME_UNSET,
    ): MediaItem = liveMediaItem(fromStatus(status, shade, durationMs), mediaId)

    fun liveMediaItem(
        metadata: MediaMetadata?,
        mediaId: String = AutoBrowse.NOW_PLAYING,
    ): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(LivePlaybackPolicy.STREAM_URL)
        metadata?.let { builder.setMediaMetadata(it) }
        return builder.build()
    }

    fun replaceLiveMetadata(current: MediaItem?, metadata: MediaMetadata?): MediaItem? {
        if (current == null || metadata == null) {
            return null
        }
        if (current.mediaMetadata == metadata) {
            return null
        }
        return current.buildUpon().setMediaMetadata(metadata).build()
    }

    fun published(api: MediaMetadata?, exo: MediaMetadata): MediaMetadata = api ?: exo

    fun positionMs(progress: SongProgress?): Long =
        (progress?.elapsedSecs ?: 0).coerceAtLeast(0) * 1000

    fun durationMs(progress: SongProgress?): Long {
        val secs = progress?.durationSecs ?: return C.TIME_UNSET
        return secs.coerceAtLeast(0) * 1000
    }
}
