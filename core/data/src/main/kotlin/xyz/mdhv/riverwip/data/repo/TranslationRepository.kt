package xyz.mdhv.riverwip.data.repo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.model.TranslationCatalog
import xyz.mdhv.riverwip.model.TranslationFormatting
import xyz.mdhv.riverwip.model.TranslationOption
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Context.translationDataStore by preferencesDataStore(name = "translation")

/**
 * Abstraction over pulling a dictionary file down, so [TranslationRepository]
 * is unit-testable with a fake — the same shape as [ArticleFetcher].
 */
interface TranslationDownloader {
    fun fetchTo(url: String, dest: File)
}

/**
 * Word-level translation (owner's ask): long-press a word while reading in a
 * language that isn't yours and see it in one that is.
 *
 * Same arrangement as [DictionaryRepository] — one dictionary installed at a
 * time, downloaded only when the reader asks, then queried entirely offline —
 * but stored as **SQLite rather than a flat JSON map**. That is a deliberate
 * departure: the Webster's map is held wholly in memory (its own doc comment
 * calls that "a considered v1 trade-off"), and doing the same to a 26 MB
 * bilingual dictionary would be a considerably worse one. Queried on disk, a
 * lookup costs an indexed row read and no resident memory at all.
 *
 * Nothing about a lookup leaves the device, which is the same promise the
 * dictionary lens already makes: which words a reader looks up is nobody's
 * business, and there is no request to intercept in the first place.
 */
class TranslationRepository(
    private val context: Context,
    private val downloader: TranslationDownloader = HttpTranslationDownloader(),
) {

    val options: List<TranslationOption> get() = TranslationCatalog.options

    private val installedKey = stringPreferencesKey("installed_id")

    fun observeInstalledId(): Flow<String?> = context.translationDataStore.data.map { it[installedKey] }

    suspend fun installedOption(): TranslationOption? =
        TranslationCatalog.byId(observeInstalledId().first())

    private fun translationsDir() = File(context.filesDir, "translations")

    private fun fileFor(id: String) = File(translationsDir(), "$id.sqlite3")

    /**
     * Fetch a dictionary and make it the active one, replacing any previous.
     *
     * Downloads to a temporary file first: a half-written database that
     * happened to keep the real name would be indistinguishable from a good
     * one at query time, and would fail as a corrupt file on every lookup
     * forever rather than as a failed download once.
     */
    suspend fun download(option: TranslationOption): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = translationsDir().apply { mkdirs() }
            val dest = fileFor(option.id)
            val staging = File(dir, "${option.id}.part")
            try {
                downloader.fetchTo(option.downloadUrl, staging)
                require(staging.length() > 0) { "empty download" }
                indexForLookup(staging)
                if (dest.exists()) dest.delete()
                check(staging.renameTo(dest)) { "could not place the downloaded dictionary" }
            } finally {
                staging.delete()
            }
            // One installed at a time, like the dictionary: the others are the
            // reader's storage, not ours to keep spending.
            dir.listFiles()?.forEach { if (it != dest) it.delete() }
            context.translationDataStore.edit { it[installedKey] = option.id }
            Unit
        }
    }

    /** Forget and delete the installed dictionary. */
    suspend fun remove(): Unit = withContext(Dispatchers.IO) {
        translationsDir().listFiles()?.forEach { it.delete() }
        context.translationDataStore.edit { it.remove(installedKey) }
    }

    /**
     * Translations for a word, or an empty list when there is no installed
     * dictionary or no entry. Never throws: a corrupt or half-written file is
     * a reason to show "no translation", not to take down the reader.
     */
    suspend fun translate(word: String): List<String> = withContext(Dispatchers.IO) {
        val id = observeInstalledId().first() ?: return@withContext emptyList()
        val file = fileFor(id)
        if (!file.isFile) return@withContext emptyList()
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                for (candidate in candidates(trimmed)) {
                    val hit = lookup(db, candidate)
                    if (hit.isNotEmpty()) return@use hit
                }
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun lookup(db: SQLiteDatabase, word: String): List<String> =
        db.rawQuery(
            "SELECT trans_list FROM simple_translation WHERE written_rep = ? LIMIT 1",
            arrayOf(word),
        ).use { c ->
            if (c.moveToFirst()) TranslationFormatting.senses(c.getString(0)) else emptyList()
        }

    /**
     * Spellings to try, in order. WikDict stores headwords in their own casing
     * and matches are exact, so a word tapped at the start of a sentence
     * ("Water") finds nothing unless its lowercase form is tried too.
     */
    private fun candidates(word: String): List<String> = listOf(
        word,
        word.lowercase(),
        word.replaceFirstChar { it.uppercase() },
    ).distinct()

    /**
     * WikDict ships these tables **without an index on `written_rep`**, so
     * every lookup would otherwise scan the whole table — tolerable at 4,000
     * rows, not at the hundreds of thousands in the larger pairs. Built once,
     * here, rather than on each query.
     */
    private fun indexForLookup(file: File) {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_simple_translation_written_rep " +
                    "ON simple_translation(written_rep)",
            )
        }
    }

}

/** The real downloader: streams straight to disk, following redirects. */
class HttpTranslationDownloader : TranslationDownloader {

    override fun fetchTo(url: String, dest: File) {

        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
                // Deliberately no gzip: these are SQLite files served as-is,
                // and a transparently-decoded stream would have to be written
                // to disk before it could be opened anyway.
            }
            try {
                val code = conn.responseCode
                if (code in REDIRECTS && redirects < MAX_REDIRECTS) {
                    val loc = conn.getHeaderField("Location") ?: error("redirect without Location")
                    current = URL(URL(current), loc).toString()
                    redirects++
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) error("HTTP $code downloading translation dictionary")
                conn.inputStream.use { ins -> dest.outputStream().use { out -> ins.copyTo(out, 64 * 1024) } }
                return
            } finally {
                conn.disconnect()
            }
        }
    }

    private companion object {
        const val USER_AGENT = "river/0.1 (+news-omission-reader)"
        val REDIRECTS = listOf(301, 302, 303, 307, 308)
        const val MAX_REDIRECTS = 5
    }
}
