package xyz.mdhv.riverwip.data.repo

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.riverwip.model.TranslationCatalog
import java.io.File

/**
 * Exercises the real install-then-query path against a real SQLite file in
 * WikDict's actual shape — built here rather than downloaded, so the test needs
 * no network and no multi-megabyte fixture, but goes through the same staging,
 * indexing, rename and lookup code a real download does.
 *
 * The schema and the two behaviours that matter (pipe-separated `trans_list`,
 * case-sensitive exact-match headwords) were read off a genuine WikDict export
 * during the 2026-09-01 verification run, not assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationRepositoryTest {

    /** Writes a file with WikDict's own table shape and a handful of entries. */
    private fun writeWikDictLike(dest: File, rows: List<Triple<String, String, Double>>) {
        dest.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dest, null).use { db ->
            db.execSQL(
                "CREATE TABLE simple_translation(" +
                    "written_rep TEXT, trans_list, max_score, rel_importance)",
            )
            for ((word, trans, score) in rows) {
                db.execSQL(
                    "INSERT INTO simple_translation(written_rep, trans_list, max_score, rel_importance) " +
                        "VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(word, trans, score, 1.0),
                )
            }
        }
    }

    private class FakeDownloader(private val source: File) : TranslationDownloader {
        var calls = 0
        override fun fetchTo(url: String, dest: File) {
            calls++
            source.copyTo(dest, overwrite = true)
        }
    }

    private fun option() = TranslationCatalog.byId("en-es")!!

    @Test fun downloadsInstallsAndTranslates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staged = File(context.cacheDir, "src-en-es.sqlite3")
        writeWikDictLike(
            staged,
            listOf(
                Triple("water", "agua | riego", 3.0),
                Triple("newspaper", "periódico", 2.0),
            ),
        )
        val repo = TranslationRepository(context, FakeDownloader(staged))

        assertNull("nothing installed to begin with", repo.observeInstalledId().first())
        assertEquals(emptyList<String>(), repo.translate("water"))

        assertTrue(repo.download(option()).isSuccess)

        assertEquals("en-es", repo.observeInstalledId().first())
        assertEquals(option(), repo.installedOption())
        assertEquals(listOf("agua", "riego"), repo.translate("water"))
        assertEquals(listOf("periódico"), repo.translate("newspaper"))
    }

    @Test fun aWordTappedAtTheStartOfASentenceStillResolves() = runBlocking {
        // WikDict matches headwords exactly and stores them lowercase, so
        // "Water" — which is what a reader long-presses at the start of a
        // sentence — finds nothing without the case fallback.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staged = File(context.cacheDir, "src-case.sqlite3")
        writeWikDictLike(staged, listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(context, FakeDownloader(staged))
        repo.download(option())

        assertEquals(listOf("agua"), repo.translate("Water"))
        assertEquals(listOf("agua"), repo.translate("  water  "))
        assertEquals(emptyList<String>(), repo.translate("unlisted"))
        assertEquals(emptyList<String>(), repo.translate("   "))
    }

    @Test fun downloadBuildsTheLookupIndexWikDictOmits() = runBlocking {
        // WikDict ships simple_translation with no index on written_rep, so
        // every lookup is a full scan until we add one.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staged = File(context.cacheDir, "src-index.sqlite3")
        writeWikDictLike(staged, listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(context, FakeDownloader(staged))
        repo.download(option())

        val installed = File(File(context.filesDir, "translations"), "en-es.sqlite3")
        SQLiteDatabase.openDatabase(installed.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='simple_translation'",
                null,
            ).use { c ->
                assertTrue("an index was created", c.moveToFirst())
            }
        }
    }

    @Test fun aFailedDownloadLeavesNothingInstalled() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = TranslationRepository(
            context,
            object : TranslationDownloader {
                override fun fetchTo(url: String, dest: File) = error("network down")
            },
        )

        val result = repo.download(option())

        assertTrue("the failure is reported, not swallowed", result.isFailure)
        assertNull("and nothing is marked installed", repo.observeInstalledId().first())
        // Crucially, no half-written file is left behind wearing the real name —
        // that would fail as a corrupt database on every lookup forever, rather
        // than as one failed download.
        val dir = File(context.filesDir, "translations")
        assertTrue("no leftovers", dir.listFiles().isNullOrEmpty())
        assertEquals(emptyList<String>(), repo.translate("water"))
    }

    @Test fun aCorruptFileYieldsNoTranslationRatherThanACrash() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staged = File(context.cacheDir, "src-ok.sqlite3")
        writeWikDictLike(staged, listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(context, FakeDownloader(staged))
        repo.download(option())

        // Something eats the file after install — storage pressure, a
        // half-finished restore. A reader long-pressing a word should see "no
        // translation", not a crash mid-article.
        File(File(context.filesDir, "translations"), "en-es.sqlite3").writeText("not a database")
        assertEquals(emptyList<String>(), repo.translate("water"))
    }

    @Test fun installingASecondDictionaryReplacesTheFirst() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = File(context.cacheDir, "src-first.sqlite3")
        writeWikDictLike(first, listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(context, FakeDownloader(first))
        repo.download(TranslationCatalog.byId("en-es")!!)

        val second = File(context.cacheDir, "src-second.sqlite3")
        writeWikDictLike(second, listOf(Triple("water", "eau", 3.0)))
        TranslationRepository(context, FakeDownloader(second)).download(TranslationCatalog.byId("en-fr")!!)

        val dir = File(context.filesDir, "translations")
        assertEquals("only one dictionary is kept", 1, dir.listFiles()!!.size)
        assertEquals("en-fr", repo.observeInstalledId().first())
        assertEquals(listOf("eau"), repo.translate("water"))
    }

    @Test fun removeForgetsAndDeletes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staged = File(context.cacheDir, "src-remove.sqlite3")
        writeWikDictLike(staged, listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(context, FakeDownloader(staged))
        repo.download(option())

        repo.remove()

        assertNull(repo.observeInstalledId().first())
        assertEquals(emptyList<String>(), repo.translate("water"))
        assertTrue(File(context.filesDir, "translations").listFiles().isNullOrEmpty())
    }
}
