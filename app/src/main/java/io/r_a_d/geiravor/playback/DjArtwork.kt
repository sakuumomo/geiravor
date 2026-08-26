package io.r_a_d.geiravor.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.r_a_d.geiravor.R

object DjArtwork {
    const val MAX_EDGE_PX = 512

    fun decodeOptions(): BitmapFactory.Options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    fun softwareCopy(bitmap: Bitmap): Bitmap {
        if (bitmap.config != Bitmap.Config.HARDWARE) {
            return bitmap
        }
        return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    fun mystery(context: Context): Bitmap {
        return BitmapFactory.decodeResource(
            context.resources,
            R.drawable.mystery_dj,
            decodeOptions(),
        ) ?: Bitmap.createBitmap(MAX_EDGE_PX, MAX_EDGE_PX, Bitmap.Config.ARGB_8888)
    }
}

internal class DjArtworkLoader(
    private val context: Context,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, DjArtwork.decodeOptions())
        return Futures.immediateFuture(
            bitmap?.let { DjArtwork.softwareCopy(it) } ?: DjArtwork.mystery(context),
        )
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val out = SettableFuture.create<Bitmap>()
        val key = uri.toString()
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(DjArtwork.MAX_EDGE_PX)
            .allowHardware(false)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .target(
                onSuccess = { drawable ->
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    out.set(
                        bitmap?.let { DjArtwork.softwareCopy(it) } ?: DjArtwork.mystery(context),
                    )
                },
                onError = {
                    out.set(DjArtwork.mystery(context))
                },
            )
            .build()
        context.imageLoader.enqueue(request)
        return out
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        val uri = metadata.artworkUri
        return if (uri != null) {
            loadBitmap(uri)
        } else {
            Futures.immediateFuture(DjArtwork.mystery(context))
        }
    }
}
