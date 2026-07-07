package xyz.mdhv.riverwip.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities. Kept separate from the pure domain types in `:core:model` — the
 * mappers in `SourceMapping.kt` translate between them, so persistence concerns
 * never leak into the domain/analysis core.
 *
 * P1 materializes only [SourceEntity]; item/read/aggregate tables land in P2/P4.
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
