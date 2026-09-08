package io.r_a_d.geiravor.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.r_a_d.geiravor.compat.loadCoilStill
import io.r_a_d.geiravor.compat.mysteryDjBitmap

/** Shade/Auto artwork from Coil stills; mystery-DJ on miss. `docs/spec/playback.md`. */
@UnstableApi
class CoilStillBitmapLoader(private val context: Context) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        Thread({
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: mysteryDjBitmap(context)
            future.set(bmp)
        }, "geiravor-art-bytes").start()
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        Thread({
            val bmp = loadCoilStill(context, uri.toString()) ?: mysteryDjBitmap(context)
            future.set(bmp)
        }, "geiravor-art").start()
        return future
    }
}
