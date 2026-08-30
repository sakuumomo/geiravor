package io.r_a_d.geiravor.radio

import io.r_a_d.geiravor.data.DiskPolicy
import uniffi.geiravor_core.Dj
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status
import java.util.Properties

object SnapshotPolicy {
    const val RESTORE_STREAM_DOWN = false

    data class LastPaint(
        val np: String,
        val artist: String,
        val title: String,
        val listeners: Long,
        val isAfkStream: Boolean,
        val requesting: Boolean,
        val current: Long,
        val startTime: Long,
        val endTime: Long,
        val trackId: Long,
        val thread: String,
        val djId: Long,
        val djName: String,
        val djImage: String,
        val lpMeta: String,
        val lpTs: Long,
        val lpRequest: Boolean,
        val queueMeta: String,
        val queueTs: Long,
        val queueRequest: Boolean,
        val tags: String,
    )

    fun fromStatus(status: Status): LastPaint {
        val lp = status.lastPlayed.firstOrNull()
        val next = status.queue.firstOrNull()
        return LastPaint(
            np = status.np,
            artist = status.artist,
            title = status.title,
            listeners = status.listeners,
            isAfkStream = status.isAfkStream,
            requesting = status.requesting,
            current = status.current,
            startTime = status.startTime,
            endTime = status.endTime,
            trackId = status.trackId,
            thread = status.thread.orEmpty(),
            djId = status.dj.id,
            djName = status.dj.name,
            djImage = status.dj.image,
            lpMeta = lp?.meta.orEmpty(),
            lpTs = lp?.timestamp ?: 0L,
            lpRequest = lp?.isRequest == true,
            queueMeta = next?.meta.orEmpty(),
            queueTs = next?.timestamp ?: 0L,
            queueRequest = next?.isRequest == true,
            tags = status.tags.joinToString("\u001f"),
        )
    }

    fun toStatus(paint: LastPaint): Status {
        fun entry(meta: String, ts: Long, request: Boolean): List<ListEntry> {
            if (meta.isEmpty()) {
                return emptyList()
            }
            val split = meta.split(" - ", limit = 2)
            val artist = if (split.size > 1) split[0] else ""
            val title = if (split.size > 1) split[1] else meta
            return listOf(ListEntry(meta, artist, title, ts, request))
        }
        return Status(
            np = paint.np,
            artist = paint.artist,
            title = paint.title,
            listeners = paint.listeners,
            isAfkStream = paint.isAfkStream,
            requesting = paint.requesting,
            current = paint.current,
            startTime = paint.startTime,
            endTime = paint.endTime,
            trackId = paint.trackId,
            thread = paint.thread.ifBlank { null },
            dj = Dj(id = paint.djId, name = paint.djName, image = paint.djImage),
            queue = entry(paint.queueMeta, paint.queueTs, paint.queueRequest),
            lastPlayed = entry(paint.lpMeta, paint.lpTs, paint.lpRequest),
            tags = if (paint.tags.isEmpty()) emptyList() else paint.tags.split('\u001f'),
        )
    }

    /** `current` and `listeners` tick every poll; they must not force a Room rewrite. */
    fun sameOnDisk(old: LastPaint?, new: LastPaint): Boolean =
        !DiskPolicy.changed(old?.copy(current = 0, listeners = 0), new.copy(current = 0, listeners = 0))

    fun encode(paint: LastPaint): String {
        val props = Properties()
        props["np"] = paint.np
        props["artist"] = paint.artist
        props["title"] = paint.title
        props["listeners"] = paint.listeners.toString()
        props["isAfkStream"] = paint.isAfkStream.toString()
        props["requesting"] = paint.requesting.toString()
        props["current"] = paint.current.toString()
        props["startTime"] = paint.startTime.toString()
        props["endTime"] = paint.endTime.toString()
        props["trackId"] = paint.trackId.toString()
        props["thread"] = paint.thread
        props["djId"] = paint.djId.toString()
        props["djName"] = paint.djName
        props["djImage"] = paint.djImage
        props["lpMeta"] = paint.lpMeta
        props["lpTs"] = paint.lpTs.toString()
        props["lpRequest"] = paint.lpRequest.toString()
        props["queueMeta"] = paint.queueMeta
        props["queueTs"] = paint.queueTs.toString()
        props["queueRequest"] = paint.queueRequest.toString()
        props["tags"] = paint.tags
        return java.io.StringWriter().also { props.store(it, null) }.toString()
    }

    fun decode(blob: String): LastPaint? {
        if (blob.isBlank()) {
            return null
        }
        val props = Properties()
        props.load(java.io.StringReader(blob))
        val np = props.getProperty("np") ?: return null
        return LastPaint(
            np = np,
            artist = props.getProperty("artist").orEmpty(),
            title = props.getProperty("title").orEmpty(),
            listeners = props.getProperty("listeners")?.toLongOrNull() ?: 0L,
            isAfkStream = props.getProperty("isAfkStream").toBoolean(),
            requesting = props.getProperty("requesting").toBoolean(),
            current = props.getProperty("current")?.toLongOrNull() ?: 0L,
            startTime = props.getProperty("startTime")?.toLongOrNull() ?: 0L,
            endTime = props.getProperty("endTime")?.toLongOrNull() ?: 0L,
            trackId = props.getProperty("trackId")?.toLongOrNull() ?: 0L,
            thread = props.getProperty("thread").orEmpty(),
            djId = props.getProperty("djId")?.toLongOrNull() ?: 0L,
            djName = props.getProperty("djName").orEmpty(),
            djImage = props.getProperty("djImage").orEmpty(),
            lpMeta = props.getProperty("lpMeta").orEmpty(),
            lpTs = props.getProperty("lpTs")?.toLongOrNull() ?: 0L,
            lpRequest = props.getProperty("lpRequest").toBoolean(),
            queueMeta = props.getProperty("queueMeta").orEmpty(),
            queueTs = props.getProperty("queueTs")?.toLongOrNull() ?: 0L,
            queueRequest = props.getProperty("queueRequest").toBoolean(),
            tags = props.getProperty("tags").orEmpty(),
        )
    }
}
