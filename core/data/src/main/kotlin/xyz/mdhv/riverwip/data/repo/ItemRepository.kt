package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.db.SourceDao
import xyz.mdhv.riverwip.data.db.SourceEntity
import xyz.mdhv.riverwip.data.mapping.toDomain
import xyz.mdhv.riverwip.data.mapping.toEntity
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.model.Ingest
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceKind
import xyz.mdhv.riverwip.model.Tier

/**
 * Fetch + ingest + read the item stream (brief §P2). [FetchOutcome] surfaces
 * per-source failure tracking; a failure on one source never blocks the others
 * (brief §P2: "per-source failure tracking").
 */
class ItemRepository(
    private val sourceDao: SourceDao,
    private val itemDao: ItemDao,
    private val http: HttpClient,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Items from the user's *currently enabled* sources — the content behind the honest denominator (brief §1). */
    fun observeItemsForEnabledSources(): Flow<List<Item>> =
        sourceDao.observeAll()
            .map { list -> list.filter { it.enabled }.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else itemDao.observeForSources(ids).map { entities -> entities.map { it.toDomain() } }
            }

    data class FetchOutcome(val sourceId: String, val newItemCount: Int, val error: String? = null) {
        val succeeded: Boolean get() = error == null
    }

    /** Fetch + ingest every enabled source. One source's failure never blocks the rest. */
    suspend fun fetchAndIngestAllEnabled(): List<FetchOutcome> =
        sourceDao.enabled().map { fetchAndIngestOne(it) }

    private suspend fun fetchAndIngestOne(source: SourceEntity): FetchOutcome {
        val now = clock()
        return try {
            val resp = http.get(source.url, etag = source.etag, lastModified = source.lastModified)
            if (resp.notModified) {
                sourceDao.markFetchSuccess(source.id, source.etag, source.lastModified, now)
                return FetchOutcome(source.id, 0)
            }
            if (!resp.isSuccess) {
                val reason = "HTTP ${resp.code}"
                sourceDao.markFetchFailure(source.id, reason, now)
                return FetchOutcome(source.id, 0, reason)
            }
            val kind = SourceKind.fromKey(source.kind) ?: SourceKind.RSS
            val domainSource = Source(
                id = source.id, kind = kind, url = source.url, title = source.title,
                tier = Tier.fromKey(source.tier), enabled = source.enabled, addedAt = source.addedAt,
            )
            val items = Ingest.ingest(domainSource, resp.body, resp.contentType, now)
            itemDao.insertAllIgnoring(items.map { it.toEntity() })
            sourceDao.markFetchSuccess(source.id, resp.etag, resp.lastModified, now)
            FetchOutcome(source.id, items.size)
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            sourceDao.markFetchFailure(source.id, reason, now)
            FetchOutcome(source.id, 0, reason)
        }
    }

    /** Retention: prune Item rows older than [days] (default 60, brief §4). Aggregates survive independently. */
    suspend fun pruneOlderThan(days: Int = 60) {
        val cutoff = clock() - days.toLong() * 24 * 60 * 60 * 1000
        itemDao.pruneOlderThan(cutoff)
    }

    suspend fun allItemsOnce(): List<Item> = itemDao.allOnce().map { it.toDomain() }

    suspend fun itemsInRange(fromMillis: Long, toMillis: Long): List<Item> =
        itemDao.inRange(fromMillis, toMillis).map { it.toDomain() }

    suspend fun markFullTextCached(itemId: String, cached: Boolean) = itemDao.setFullTextCached(itemId, cached)

    suspend fun byId(itemId: String): Item? = itemDao.byId(itemId)?.toDomain()
}
