package io.r_a_d.geiravor.data

import uniffi.geiravor_core.StaffMember

object StaffStore {
    @Volatile
    var paint: List<StaffMember>? = null

    fun toMembers(entities: List<StaffMemberEntity>): List<StaffMember> =
        entities.map { entity ->
            StaffMember(name = entity.name, image = entity.image, role = entity.role)
        }

    fun fromMembers(members: List<StaffMember>): List<StaffMemberEntity> =
        members.mapIndexed { index, member ->
            StaffMemberEntity(
                role = member.role,
                sortIndex = index,
                name = member.name,
                image = member.image,
            )
        }

    fun write(
        existing: List<StaffMemberEntity>,
        incoming: List<StaffMember>,
    ): List<StaffMemberEntity>? {
        val next = fromMembers(incoming)
        if (!DiskPolicy.changed(existing, next)) {
            return null
        }
        return next
    }

    suspend fun load(db: GeiravorDb): List<StaffMember>? {
        val rows = db.staff().all()
        if (rows.isEmpty()) {
            return null
        }
        return toMembers(rows)
    }

    suspend fun save(db: GeiravorDb, members: List<StaffMember>): Boolean {
        val write = write(db.staff().all(), members) ?: return false
        db.staff().deleteAll()
        if (write.isNotEmpty()) {
            db.staff().insertAll(write)
        }
        return true
    }
}
