package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.db.ReadEventDao
import xyz.mdhv.riverwip.data.mapping.toDomain
import xyz.mdhv.riverwip.data.mapping.toEntity
import xyz.mdhv.riverwip.model.DwellBucket
import xyz.mdhv.riverwip.model.ReadEvent

/**
 * Records reads (brief §3/§4): coarse [DwellBucket] only, never a precise
 * duration. [viaRiver] records whether the open came from the river surface.
 */
class ReadEventRepository(
    private val dao: ReadEventDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun record(itemId: String, dwellBucket: DwellBucket, viaRiver: Boolean) {
        dao.insert(ReadEvent(itemId, clock(), dwellBucket, viaRiver).toEntity())
    }

    /** Every recorded read, for the user's own data export (brief §4: their trace is theirs). */
    suspend fun allOnce(): List<ReadEvent> = dao.allOnce().map { it.toDomain() }
}
