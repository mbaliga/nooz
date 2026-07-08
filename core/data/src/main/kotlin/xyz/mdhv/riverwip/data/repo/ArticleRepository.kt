package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.extract.ArticleExtractor
import xyz.mdhv.riverwip.data.net.HttpClient

/** Abstraction over fetching an article's raw HTML, so [ArticleRepository] is unit-testable with a fake. */
interface ArticleFetcher {
    suspend fun fetch(url: String): HttpClient.Response
}

class HttpArticleFetcher(private val http: HttpClient) : ArticleFetcher {
    override suspend fun fetch(url: String): HttpClient.Response = http.get(url)
}

/**
 * Full-text extraction + offline cache (brief §P3). Feeds often truncate; this
 * fetches the original article, extracts its body with [ArticleExtractor], and
 * caches the result so it reads offline afterward.
 */
class ArticleRepository(
    private val itemDao: ItemDao,
    private val fetcher: ArticleFetcher,
    private val cache: FullTextCache,
) {
    data class ArticleText(val paragraphs: List<String>, val fromCache: Boolean)

    /** Cached text if present; otherwise fetch + extract + cache. Null if extraction fails or yields nothing. */
    suspend fun textFor(itemId: String, url: String): ArticleText? {
        cache.get(itemId)?.let { cached ->
            return ArticleText(cached.split(PARAGRAPH_SEP), fromCache = true)
        }
        val resp = try {
            fetcher.fetch(url)
        } catch (_: Exception) {
            return null
        }
        if (!resp.isSuccess) return null
        val extracted = ArticleExtractor.extract(resp.body, baseUri = url)
        if (!extracted.isUsable) return null
        cache.put(itemId, extracted.paragraphs.joinToString(PARAGRAPH_SEP))
        itemDao.setFullTextCached(itemId, true)
        return ArticleText(extracted.paragraphs, fromCache = false)
    }

    fun currentCacheSizeBytes(): Long = cache.currentSizeBytes()

    fun evictItem(itemId: String) = cache.remove(itemId)

    companion object {
        private const val PARAGRAPH_SEP = "\n\n"
    }
}
