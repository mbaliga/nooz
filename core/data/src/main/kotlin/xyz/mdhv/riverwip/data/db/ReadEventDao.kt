package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReadEventDao {

    @Insert
    suspend fun insert(event: ReadEventEntity): Long

    @Query("SELECT * FROM read_events WHERE openedAt >= :fromMillis AND openedAt < :toMillis")
    suspend fun inRange(fromMillis: Long, toMillis: Long): List<ReadEventEntity>

    /** Full snapshot for aggregation (brief §P4); read events are small (coarse buckets only). */
    @Query("SELECT * FROM read_events")
    suspend fun allOnce(): List<ReadEventEntity>

    @Query("SELECT COUNT(*) FROM read_events WHERE itemId = :itemId")
    suspend fun countForItem(itemId: String): Int
}
