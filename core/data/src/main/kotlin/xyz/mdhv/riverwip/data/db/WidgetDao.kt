package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Query

/**
 * The home-screen widgets' reads, and only reads.
 *
 * A DAO of its own rather than new methods on the existing ones: each query
 * here is bounded (a `LIMIT`, a single row or a count) so a widget redraw never
 * loads the whole item table the way the Stand's flows do, and keeping them
 * apart means the widgets cannot quietly grow a write path.
 */
@Dao
interface WidgetDao {

    /**
     * The newest items from the reader's enabled sources, by publication time
     * and nothing else. Items whose own feed declared them explicit never
     * reach a home screen.
     */
    @Query(
        """
        SELECT items.* FROM items
        INNER JOIN sources ON sources.id = items.sourceId
        WHERE sources.enabled = 1 AND items.declaredNsfw = 0
        ORDER BY items.publishedAt DESC, items.id ASC
        LIMIT :limit
        """,
    )
    suspend fun newestFromEnabledSources(limit: Int): List<ItemEntity>

    /** Every source with its fetch history; the health widget filters to enabled ones itself. */
    @Query("SELECT * FROM sources")
    suspend fun sources(): List<SourceEntity>

    /** The latest weekly aggregate: the same row the cross-section opens on. */
    @Query("SELECT * FROM weekly_aggregates ORDER BY weekStart DESC LIMIT 1")
    suspend fun latestAggregate(): WeeklyAggregateEntity?

    /** The clippings shelf, most recently clipped first. */
    @Query("SELECT * FROM clippings ORDER BY savedAt DESC, itemId ASC LIMIT :limit")
    suspend fun newestClippings(limit: Int): List<ClippingEntity>

    @Query("SELECT COUNT(*) FROM clippings")
    suspend fun clippingCount(): Int
}
