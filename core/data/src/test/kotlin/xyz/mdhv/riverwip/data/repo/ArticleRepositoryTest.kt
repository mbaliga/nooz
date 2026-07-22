package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.db.ItemEntity
import xyz.mdhv.riverwip.data.net.HttpClient
import java.io.File
import java.nio.file.Files

private class FakeItemDaoForArticles : ItemDao {
    val store = MutableStateFlow<List<ItemEntity>>(emptyList())
    override fun observeForSources(sourceIds: List<String>): Flow<List<ItemEntity>> = throw NotImplementedError()
    override suspend fun byId(id: String): ItemEntity? = store.value.firstOrNull { it.id == id }
    override suspend fun insertAllIgnoring(items: List<ItemEntity>): List<Long> = emptyList()
    override suspend fun setFullTextCached(id: String, cached: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(fullTextCached = cached) else it }
    }
    override suspend fun inRange(fromMillis: Long, toMillis: Long): List<ItemEntity> = emptyList()
    override suspend fun allOnce(): List<ItemEntity> = store.value
    override suspend fun pruneOlderThan(beforeMillis: Long): Int = 0
    override suspend fun count(): Int = store.value.size
}

private class FakeArticleFetcher(private val response: HttpClient.Response? = null, private val throwOn: Exception? = null) : ArticleFetcher {
    override suspend fun fetch(url: String): HttpClient.Response {
        throwOn?.let { throw it }
        return response!!
    }
}

private fun fakeResponse(code: Int, body: String) =
    HttpClient.Response(code = code, body = body, etag = null, lastModified = null, contentType = "text/html", finalUrl = "https://ex.com/a")

class ArticleRepositoryTest {

    private lateinit var dir: File

    private val articleHtml = """
        <html><body><article>
          <h1>Test Story</h1>
          <p>This is the first substantial paragraph of the article, long enough to survive the extraction threshold easily.</p>
          <p>This is the second substantial paragraph, continuing the article body content for the extraction test to verify.</p>
        </article></body></html>
    """.trimIndent()

    @Test fun cacheHitSkipsFetch() = runTest {
        dir = Files.createTempDirectory("article-repo-test").toFile()
        val cache = FullTextCache(dir, maxBytes = 10_000_000)
        cache.put("i1", "cached paragraph one\n\ncached paragraph two")
        val itemDao = FakeItemDaoForArticles()
        val repo = ArticleRepository(itemDao, FakeArticleFetcher(throwOn = IllegalStateException("should not fetch")), cache)
        val result = repo.textFor("i1", "https://ex.com/a")
        assertTrue(result!!.fromCache)
        assertEquals(2, result.paragraphs.size)
    }

    @Test fun fetchExtractAndCacheOnMiss() = runTest {
        dir = Files.createTempDirectory("article-repo-test").toFile()
        val cache = FullTextCache(dir, maxBytes = 10_000_000)
        val itemDao = FakeItemDaoForArticles()
        itemDao.store.value = listOf(ItemEntity("i1", "s1", "https://ex.com/a", "Test Story", null, 0L, 0L, null, false, "[]", 0L))
        val repo = ArticleRepository(itemDao, FakeArticleFetcher(fakeResponse(200, articleHtml)), cache)
        val result = repo.textFor("i1", "https://ex.com/a")
        assertTrue(result != null && !result.fromCache)
        assertEquals(2, result!!.paragraphs.size)
        // Cached for next time, and the item's fullTextCached flag flipped.
        assertTrue(cache.contains("i1"))
        assertTrue(itemDao.byId("i1")!!.fullTextCached)
    }

    @Test fun httpFailureReturnsNullAndDoesNotCache() = runTest {
        dir = Files.createTempDirectory("article-repo-test").toFile()
        val cache = FullTextCache(dir, maxBytes = 10_000_000)
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(fakeResponse(404, "not found")), cache)
        assertNull(repo.textFor("i1", "https://ex.com/a"))
        assertTrue(!cache.contains("i1"))
    }

    @Test fun networkExceptionReturnsNull() = runTest {
        dir = Files.createTempDirectory("article-repo-test").toFile()
        val cache = FullTextCache(dir, maxBytes = 10_000_000)
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(throwOn = java.io.IOException("timeout")), cache)
        assertNull(repo.textFor("i1", "https://ex.com/a"))
    }

    @Test fun extractionYieldingNothingReturnsNull() = runTest {
        dir = Files.createTempDirectory("article-repo-test").toFile()
        val cache = FullTextCache(dir, maxBytes = 10_000_000)
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(fakeResponse(200, "<html><body>too short</body></html>")), cache)
        assertNull(repo.textFor("i1", "https://ex.com/a"))
    }
}
