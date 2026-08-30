package io.r_a_d.geiravor.playback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DjNotifierWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GeiravorApp
        val settings = SettingsStore(app)
        if (!settings.djNotifierEnabled.first()) {
            return Result.success()
        }
        val ok = withContext(Dispatchers.IO) {
            runCatching { app.radio.refresh() }.isSuccess
        }
        if (!ok) {
            return Result.retry()
        }
        val status = app.radio.snapshot() ?: return Result.retry()
        DjNotifier.consider(app, settings, status, streamDown = false)
        return Result.success()
    }
}
