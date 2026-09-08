package io.r_a_d.geiravor.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/** Shade line 2 is `artist | DJ`, not Auto's artist subtitle. `docs/spec/playback.md`. */
@UnstableApi
class ShadeNotificationProvider(context: Context) : DefaultMediaNotificationProvider(
    context,
    { _ -> PlaybackNotice.ID },
    PlaybackNotice.CHANNEL,
    io.r_a_d.geiravor.R.string.playback_channel,
) {
    private val appContext = context.applicationContext

    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
        metadata.displayTitle ?: metadata.title

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence =
        ShadeLine.fitForShade(appContext, NowPlayingMeta.rawArtist(metadata), NowPlayingMeta.dj(metadata))
}
