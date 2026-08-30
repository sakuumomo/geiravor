package io.r_a_d.geiravor.data

import uniffi.geiravor_core.ScheduleDay

object ScheduleStore {
    @Volatile
    var paint: List<ScheduleDay>? = null

    fun toDays(entities: List<ScheduleDayEntity>): List<ScheduleDay> =
        entities.map { entity ->
            ScheduleDay(
                weekday = entity.weekday,
                body = entity.body,
                ownerName = entity.ownerName,
                ownerImage = entity.ownerImage,
            )
        }

    fun fromDays(days: List<ScheduleDay>): List<ScheduleDayEntity> =
        days.mapIndexed { index, day ->
            ScheduleDayEntity(
                weekday = day.weekday,
                body = day.body,
                ownerName = day.ownerName,
                ownerImage = day.ownerImage,
                sortIndex = index,
            )
        }

    fun write(
        existing: List<ScheduleDayEntity>,
        incoming: List<ScheduleDay>,
    ): List<ScheduleDayEntity>? {
        val next = fromDays(incoming)
        if (!DiskPolicy.changed(existing, next)) {
            return null
        }
        return next
    }

    suspend fun load(db: GeiravorDb): List<ScheduleDay>? {
        val rows = db.schedule().all()
        if (rows.isEmpty()) {
            return null
        }
        return toDays(rows)
    }

    suspend fun save(db: GeiravorDb, days: List<ScheduleDay>): Boolean {
        val write = write(db.schedule().all(), days) ?: return false
        db.schedule().deleteAll()
        if (write.isNotEmpty()) {
            db.schedule().insertAll(write)
        }
        return true
    }
}
