package xyz.mdhv.riverwip.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import xyz.mdhv.riverwip.model.DictionaryCatalog
import xyz.mdhv.riverwip.model.DictionaryOption
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

private val Context.dictionaryDataStore by preferencesDataStore(name = "dictionary")

/**
 * The dictionary lens's data layer (owner's Kindle-style definitions). Two
 * pieces:
 *  - a bundled, permissively-licensed common-word set (top-30k frequency list)
 *    for the offline obscure-word gate — no network, always available;
 *  - a downloaded dictionary (the owner's "one-click download") for the
 *    definitions themselves. Streaming download bypasses [HttpClient]'s small
 *    body cap; lookup lazily parses the flat `{WORD: definition}` JSON once and
 *    caches it. Local only — nothing synced or transmitted.
 *
 * The 22 MB Webster's map is held in memory once loaded (a considered v1
 * trade-off for O(1) lookups); an on-disk index is a later refinement.
 */
class DictionaryRepository(private val context: Context) {

    val options: List<DictionaryOption> get() = DictionaryCatalog.options

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- common-word gate (bundled asset) ----
    @Volatile private var commonCache: Set<String>? = null
    private val commonMutex = Mutex()

    suspend fun commonWords(): Set<String> {
        commonCache?.let { return it }
        return commonMutex.withLock {
            commonCache ?: withContext(Dispatchers.IO) {
                context.assets.open("common_words.txt").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
                }
            }.also { commonCache = it }
        }
    }

    // ---- downloaded dictionary ----
    private val downloadedKey = stringPreferencesKey("downloaded_id")

    fun observeDownloadedId(): Flow<String?> = context.dictionaryDataStore.data.map { it[downloadedKey] }

    private fun dictionariesDir() = File(context.filesDir, "dictionaries")
    private fun fileFor(id: String) = File(dictionariesDir(), "$id.json")

    /** Stream a chosen dictionary to storage and make it the active one (replacing any previous). */
    suspend fun download(option: DictionaryOption): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = dictionariesDir().apply { mkdirs() }
            val tmp = File(dir, "${option.id}.download")
            downloadTo(option.downloadUrl, tmp)
            val dest = fileFor(option.id)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            // Keep exactly one active dictionary.
            dir.listFiles()?.forEach { if (it.name != dest.name) it.delete() }
            context.dictionaryDataStore.edit { it[downloadedKey] = option.id }
            lookupCache = null
            lookupCacheId = null
        }
    }

    // ---- lookup ----
    @Volatile private var lookupCache: Map<String, String>? = null
    @Volatile private var lookupCacheId: String? = null
    private val lookupMutex = Mutex()

    suspend fun define(word: String): String? {
        val id = observeDownloadedId().first() ?: return null
        val map = ensureLoaded(id) ?: return null
        return map[word]
            ?: map[word.lowercase()]
            ?: map[word.uppercase()]
            ?: map[word.replaceFirstChar { it.uppercase() }]
    }

    private suspend fun ensureLoaded(id: String): Map<String, String>? {
        if (lookupCacheId == id) return lookupCache
        return lookupMutex.withLock {
            if (lookupCacheId == id) return lookupCache
            val f = fileFor(id)
            val map = if (!f.exists()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), f.readText())
                    }.getOrNull()
                }
            }
            lookupCache = map
            lookupCacheId = id
            map
        }
    }

    private fun downloadTo(url: String, dest: File) {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "river/0.1 (+news-omission-reader)")
                setRequestProperty("Accept-Encoding", "gzip")
            }
            try {
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308) && redirects < 5) {
                    val loc = conn.getHeaderField("Location") ?: error("redirect without Location")
                    current = URL(URL(current), loc).toString()
                    redirects++
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) error("HTTP $code downloading dictionary")
                val gzip = conn.contentEncoding?.contains("gzip", ignoreCase = true) == true
                val input = if (gzip) GZIPInputStream(conn.inputStream) else conn.inputStream
                input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out, 64 * 1024) } }
                return
            } finally {
                conn.disconnect()
            }
        }
    }
}
