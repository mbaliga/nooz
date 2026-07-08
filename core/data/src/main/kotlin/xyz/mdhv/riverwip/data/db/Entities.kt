package xyz.mdhv.riverwip.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities. Kept separate from the pure domain types in `:core:model` — the
 * mappers in the `mapping` package translate between them, so persistence concerns never
 * leak into the domain/analysis core. Structured domain fields ([TopicEvidence]
 * lists, count maps) are stored as JSON string columns (see `JsonColumns.kt`) so
 * the persistence format is a `:core:data` concern, not a domain-type one.
 */
@Entity(
    tableName = "sources",
    indices = [Index(value = ["url"], unique = false)],
)
data class SourceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val url: String,
    val title: String,
    val tier: String,
    val enabled: Boolean,
    val addedAt: Long,
    // Per-source health (brief §P6 groundwork; populated by fetch in P2).
    val etag: String? = null,
    val lastModified: String? = null,
    val lastFetchAt: Long? = null,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,
)

/**
 * An ingested item (brief §4). [topicsJson] is a JSON-encoded `List<TopicEvidence>`
 * (see `JsonColumns.TopicEvidenceJson`) — classification carries its own evidence
 * all the way into storage, so "tap to see why" never depends on recomputation.
 * [id] is stable (derived from [canonicalUrl] — see `Ids.itemId`), so re-ingesting
 * the same article across fetch cycles is naturally idempotent.
 */
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["canonicalUrl"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["publishedAt"]),
    ],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val canonicalUrl: String,
    val title: String,
    val author: String?,
    val publishedAt: Long,
    val fetchedAt: Long,
    val summary: String?,
    val fullTextCached: Boolean,
    val topicsJson: String,
    val simhash: Long,
)

/**
 * A read event (brief §3/§4): coarse [dwellBucket] only, never a precise
 * duration — data minimalism is enforced at the schema, not just by convention.
 */
@Entity(tableName = "read_events", indices = [Index(value = ["itemId"]), Index(value = ["openedAt"])])
data class ReadEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val openedAt: Long,
    val dwellBucket: String,
    val viaRiver: Boolean,
)

/**
 * The tiny, permanent weekly abstraction the river renders (brief §4). Count maps
 * are JSON-encoded (see `JsonColumns.CountMapJson`); one row per period, upserted
 * by [weekStart] as the primary key so recomputation is naturally idempotent.
 */
@Entity(tableName = "weekly_aggregates")
data class WeeklyAggregateEntity(
    @PrimaryKey val weekStart: Long,
    val streamCountsByTopicJson: String,
    val readCountsByTopicJson: String,
    val sourceCountsJson: String,
)
