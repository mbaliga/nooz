package xyz.mdhv.riverwip.data.cache

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FullTextCacheTest {

    private lateinit var dir: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("fulltext-cache-test").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun putThenGetRoundTrips() {
        val cache = FullTextCache(dir, maxBytes = 1_000_000)
        cache.put("item1", "hello world")
        assertEquals("hello world", cache.get("item1"))
        assertTrue(cache.contains("item1"))
    }

    @Test fun missingItemReturnsNull() {
        val cache = FullTextCache(dir, maxBytes = 1_000_000)
        assertNull(cache.get("missing"))
        assertTrue(!cache.contains("missing"))
    }

    @Test fun removeDeletesEntry() {
        val cache = FullTextCache(dir, maxBytes = 1_000_000)
        cache.put("item1", "text")
        cache.remove("item1")
        assertNull(cache.get("item1"))
    }

    @Test fun evictsOldestFirstWhenOverBudget() {
        // Each entry ~10 bytes; budget fits 2 entries.
        val cache = FullTextCache(dir, maxBytes = 22)
        cache.put("a", "0123456789") // written first -> oldest
        Thread.sleep(10) // ensure distinct lastModified ordering
        cache.put("b", "0123456789")
        Thread.sleep(10)
        cache.put("c", "0123456789") // pushes total over budget -> "a" evicted
        assertNull(cache.get("a"))
        assertEquals("0123456789", cache.get("b"))
        assertEquals("0123456789", cache.get("c"))
    }

    @Test fun currentSizeBytesReflectsContents() {
        val cache = FullTextCache(dir, maxBytes = 1_000_000)
        assertEquals(0L, cache.currentSizeBytes())
        cache.put("a", "12345")
        assertEquals(5L, cache.currentSizeBytes())
    }

    @Test fun clearRemovesEverything() {
        val cache = FullTextCache(dir, maxBytes = 1_000_000)
        cache.put("a", "x")
        cache.put("b", "y")
        cache.clear()
        assertEquals(0L, cache.currentSizeBytes())
    }
}
