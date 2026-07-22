package xyz.mdhv.riverwip.data.cache

import java.io.File

/**
 * File-based LRU cache for extracted full text (brief §4: "full text is an LRU
 * cache with a user-visible storage budget"). Keyed by item id; values are plain
 * UTF-8 text files. When the budget is exceeded, the least-recently-*written*
 * files are evicted first (approximated via `lastModified`).
 *
 * Pure `java.io` — no Android `Context` needed directly; the caller resolves the
 * platform cache directory (e.g. `context.cacheDir`) and passes it in, so this
 * class is unit-tested against a plain temp directory.
 */
class FullTextCache(private val baseDir: File, private val maxBytes: Long) {

    init { baseDir.mkdirs() }

    private fun fileFor(itemId: String): File = File(baseDir, "$itemId.txt")

    fun get(itemId: String): String? {
        val f = fileFor(itemId)
        return if (f.isFile) f.readText() else null
    }

    fun put(itemId: String, text: String) {
        fileFor(itemId).writeText(text)
        evictIfNeeded()
    }

    fun remove(itemId: String) {
        fileFor(itemId).delete()
    }

    fun contains(itemId: String): Boolean = fileFor(itemId).isFile

    /** Current cache size — surfaced in the storage-budget UI (brief §4). */
    fun currentSizeBytes(): Long = baseDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        baseDir.listFiles()?.forEach { it.delete() }
    }

    private fun evictIfNeeded() {
        var size = currentSizeBytes()
        if (size <= maxBytes) return
        val oldestFirst = baseDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (f in oldestFirst) {
            if (size <= maxBytes) break
            size -= f.length()
            f.delete()
        }
    }
}
