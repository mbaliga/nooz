package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.db.WidgetDao
import xyz.mdhv.riverwip.data.mapping.toClipping
import xyz.mdhv.riverwip.data.mapping.toDomain
import xyz.mdhv.riverwip.data.mapping.toHealth
import xyz.mdhv.riverwip.model.Clipping
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceHealth
import xyz.mdhv.riverwip.model.WeeklyAggregate

/**
 * Read-only, bounded access to what the home-screen widgets draw. No network,
 * no writes: a widget redraw reads what the app and its fetch worker already
 * stored, and the rules for what to show live in `:core:model` (HomeWidgets.kt).
 */
class HomeWidgetRepository(
    private val dao: WidgetDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun newestHeadlines(limit: Int): List<Item> =
        dao.newestFromEnabledSources(limit).map { it.toDomain() }

    /** Every source, paired with its health as of now. */
    suspend fun sourcesWithHealth(): List<Pair<Source, SourceHealth>> {
        val now = clock()
        return dao.sources().map { it.toDomain() to it.toHealth(now) }
    }

    suspend fun latestAggregate(): WeeklyAggregate? = dao.latestAggregate()?.toDomain()

    suspend fun newestClippings(limit: Int): List<Clipping> = dao.newestClippings(limit).map { it.toClipping() }

    suspend fun clippingCount(): Int = dao.clippingCount()
}
