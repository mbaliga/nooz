package xyz.mdhv.riverwip.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home-screen widgets' queries, run against a real (in-memory) Room
 * database rather than a fake: the rules worth pinning here live in SQL — the
 * join to enabled sources, the explicit-item exclusion, the order and the
 * limit — and a fake DAO would only restate them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetDaoTest {

    private lateinit var db: RiverDatabase
    private lateinit var dao: WidgetDao

    @Before fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, RiverDatabase::class.java).allowMainThreadQueries().build()
        dao = db.widgetDao()
    }

    @After fun close() = db.close()

    private fun source(id: String, enabled: Boolean) = SourceEntity(
        id = id, kind = "rss", url = "https://ex.com/$id.xml", title = id, tier = "user",
        enabled = enabled, addedAt = 0L,
    )

    private fun item(id: String, sourceId: String, publishedAt: Long, nsfw: Boolean = false) = ItemEntity(
        id = id, sourceId = sourceId, canonicalUrl = "https://ex.com/$id", title = "Story $id", author = null,
        publishedAt = publishedAt, fetchedAt = publishedAt, summary = null, fullTextCached = false,
        topicsJson = "[]", simhash = 0L, declaredNsfw = nsfw,
    )

    @Test fun headlinesComeOnlyFromEnabledSourcesNewestFirst() = runBlocking {
        db.sourceDao().upsert(source("on", enabled = true))
        db.sourceDao().upsert(source("off", enabled = false))
        db.itemDao().insertAllIgnoring(
            listOf(
                item("a", "on", 1_000L),
                item("b", "on", 3_000L),
                item("c", "off", 5_000L),
                item("d", "on", 2_000L),
                item("e", "on", 4_000L, nsfw = true),
            ),
        )
        assertEquals(listOf("b", "d", "a"), dao.newestFromEnabledSources(10).map { it.id })
        assertEquals(listOf("b", "d"), dao.newestFromEnabledSources(2).map { it.id })
    }

    @Test fun latestAggregateIsTheNewestWeek() = runBlocking {
        assertNull(dao.latestAggregate())
        db.weeklyAggregateDao().upsert(WeeklyAggregateEntity(100L, "{}", "{}", "{}"))
        db.weeklyAggregateDao().upsert(WeeklyAggregateEntity(700L, "{}", "{}", "{}"))
        assertEquals(700L, dao.latestAggregate()?.weekStart)
    }

    @Test fun clippingsAreMostRecentlyClippedFirstAndCounted() = runBlocking {
        fun clip(id: String, savedAt: Long) = ClippingEntity(
            itemId = id, title = id, sourceId = "s", sourceTitle = null, author = null,
            canonicalUrl = "https://ex.com/$id", topicKey = "tech", publishedAt = 0L, savedAt = savedAt, excerpt = null,
        )
        db.clippingDao().upsert(clip("old", 1L))
        db.clippingDao().upsert(clip("new", 3L))
        db.clippingDao().upsert(clip("mid", 2L))
        assertEquals(listOf("new", "mid"), dao.newestClippings(2).map { it.itemId })
        assertEquals(3, dao.clippingCount())
    }
}
