package io.r_a_d.geiravor.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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

    fun stillFromBitmap(bitmap: Bitmap?): Bitmap? {
        val src = bitmap?.takeUnless { it.isRecycled } ?: return null
        return softwareCopy(src)
    }

    fun stillBounds(intrinsicWidth: Int, intrinsicHeight: Int, maxEdge: Int = MAX_EDGE_PX): Pair<Int, Int> {
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return maxEdge to maxEdge
        }
        val longest = maxOf(intrinsicWidth, intrinsicHeight)
        if (longest <= maxEdge) {
            return intrinsicWidth to intrinsicHeight
        }
        val scale = maxEdge.toFloat() / longest.toFloat()
        return (intrinsicWidth * scale).toInt().coerceAtLeast(1) to
            (intrinsicHeight * scale).toInt().coerceAtLeast(1)
    }

    fun still(drawable: Drawable): Bitmap? {
        stillFromBitmap((drawable as? BitmapDrawable)?.bitmap)?.let { return it }
        val (w, h) = stillBounds(drawable.intrinsicWidth, drawable.intrinsicHeight)
        return runCatching {
            drawable.toBitmap(w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    fun mystery(context: Context): Bitmap {
        return BitmapFactory.decodeResource(
            context.resources,
            R.drawable.mystery_dj,
            decodeOptions(),
        ) ?: Bitmap.createBitmap(MAX_EDGE_PX, MAX_EDGE_PX, Bitmap.Config.ARGB_8888)
    }

    suspend fun loadStill(context: Context, url: String?): Bitmap? {
        val data = url?.trim().orEmpty()
        if (data.isEmpty()) {
            return null
        }
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(data)
                .size(MAX_EDGE_PX)
                .allowHardware(false)
                .memoryCacheKey(data)
                .diskCacheKey(data)
                .build(),
        )
        val drawable = (result as? SuccessResult)?.drawable ?: return null
        return still(drawable)
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
                    out.set(DjArtwork.still(drawable) ?: DjArtwork.mystery(context))
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
