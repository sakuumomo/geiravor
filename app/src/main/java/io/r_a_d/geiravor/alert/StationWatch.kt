package io.r_a_d.geiravor.alert

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.os.Handler
import android.os.Looper
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.compat.loadCoilStill
import io.r_a_d.geiravor.notify.Alerts
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.djNotice
import uniffi.geiravor_core.faveOnAirKey
import java.util.concurrent.TimeUnit

object StationWatch {
    private const val WORK = "geiravor-station-check"
    @Volatile
    var prev: Status? = null
    @Volatile
    var firstDj = true
    @Volatile
    var firstFave = true
    @Volatile
    var lastFaveKey: String = ""

    fun sync(context: Context, djOn: Boolean, faveOn: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!djOn && !faveOn) {
            wm.cancelUniqueWork(WORK)
            firstDj = true
            firstFave = true
            return
        }
        val req = PeriodicWorkRequestBuilder<StationCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun onSnapshot(context: Context, next: Status, streamDown: Boolean, playing: Boolean, djOn: Boolean, faveOn: Boolean, isMember: Boolean) {
        if (djOn) {
            val n = djNotice(prev, next, streamDown, firstDj)
            firstDj = false
            if (n != null) {
                val url = LivePlaybackPolicy.djImageUrl(next.dj.image)
                Thread({
                    val art = loadCoilStill(context, url)
                    Handler(Looper.getMainLooper()).post {
                        Alerts.show(context, Alerts.ID_DJ, n.body, largeIcon = art)
                    }
                }, "geiravor-dj-art").start()
            }
        }
        if (faveOn) {
            val key = faveOnAirKey(next.isAfk, next.trackId, next.np)
            if (!firstFave && !streamDown && isMember && key != lastFaveKey) {
                if (!playing) {
                    val body = next.np.trim().ifEmpty { "A favorite is playing" }
                    Alerts.show(context, Alerts.ID_FAVE, body)
                }
            }
            firstFave = false
            lastFaveKey = key
        }
        prev = next
    }
}

class StationCheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val app = applicationContext as? GeiravorApp ?: return Result.success()
        val status = runCatching { app.core.fetchStatus() }.getOrNull() ?: return Result.retry()
        val playing = app.core.isPlaying()
        val down = app.core.isStreamDown()
        val nick = app.ui.listNickOrConnection()
        val member = if (nick.isEmpty()) {
            false
        } else {
            runCatching {
                app.core.membershipHas(nick, if (status.isAfk) status.trackId else 0, status.np)
            }.getOrDefault(false)
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            StationWatch.onSnapshot(
                app,
                status,
                down,
                playing,
                app.ui.djNotifier,
                app.ui.favePlaying,
                member,
            )
        }
        return Result.success()
    }
}
