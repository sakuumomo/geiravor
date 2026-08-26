package io.r_a_d.geiravor.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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
    private val http = DataSourceBitmapLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = http.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = http.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val out = SettableFuture.create<Bitmap>()
        Futures.addCallback(
            http.loadBitmap(uri),
            object : FutureCallback<Bitmap> {
                override fun onSuccess(result: Bitmap) {
                    out.set(DjArtwork.softwareCopy(result))
                }

                override fun onFailure(t: Throwable) {
                    out.set(DjArtwork.mystery(context))
                }
            },
            MoreExecutors.directExecutor(),
        )
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
