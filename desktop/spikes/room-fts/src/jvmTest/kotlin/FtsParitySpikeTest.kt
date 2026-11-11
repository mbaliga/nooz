package xyz.mdhv.riverwip.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike K1 (CONTAINER-BUILD-ONLY, linux-x64 JVM): replays the cases of the Android
 * RiverDatabaseMigrationTest on Room KMP + BundledSQLiteDriver, against the app's real
 * Entities.kt and DAOs and its checked-in v3/v4 schema JSON.
 */
class FtsParitySpikeTest {

    private val driver = BundledSQLiteDriver()
    private val schemasIn = Paths.get(System.getProperty("spike.schemas.in"))

    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = schemasIn,
        databasePath = java.nio.file.Files.createTempDirectory("room-fts-spike").resolve("migration-test.db"),
        driver = driver,
        databaseClass = RiverDatabase::class,
        databaseFactory = { RiverDatabaseConstructor.initialize() },
    )

    private fun inMemory(): RiverDatabase =
        Room.inMemoryDatabaseBuilder<RiverDatabase>()
            .setDriver(driver)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun SQLiteConnection.scalarInt(sql: String): Int =
        prepare(sql).use { st -> check(st.step()); st.getInt(0) }

    private fun SQLiteConnection.scalarText(sql: String): String =
        prepare(sql).use { st -> check(st.step()); st.getText(0) }

    @Test
    fun migratingFromV3KeepsTheReadersOwnData() {
        helper.createDatabase(3).apply {
            execSQL(
                """
                INSERT INTO clippings
                  (itemId, title, sourceId, sourceTitle, author, canonicalUrl, topicKey, publishedAt, savedAt, excerpt)
                VALUES
                  ('item-1', 'Flash floods on the Nepal-Tibet border', 'src-1', 'The Guardian',
                   'A Reporter', 'https://example.invalid/a', 'conflict', 1000, 2000, 'An excerpt.')
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO read_events (itemId, openedAt, dwellBucket, viaRiver) " +
                    "VALUES ('item-1', 2000, 'MEDIUM', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(4, listOf(RiverDatabase.MIGRATION_3_4))

        assertEquals(
            "Flash floods on the Nepal-Tibet border",
            db.scalarText("SELECT title FROM clippings WHERE itemId = 'item-1'"),
        )
        assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM read_events"))
        assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM article_text"))
        db.close()
    }

    @Test
    fun ftsIndexFindsBothLatinAndIndicScriptsByPrefix() = runBlocking {
        val db = inMemory()
        val dao = db.articleTextDao()

        dao.insert(ArticleTextEntity(itemId = "en", body = "Two dozen states filed a fresh lawsuit on Wednesday."))
        dao.insert(ArticleTextEntity(itemId = "te", body = "ఆంధ్ర ప్రదేశ్ లో భారీ వర్షాలు కురిశాయి"))
        dao.insert(ArticleTextEntity(itemId = "hi", body = "मोदी और पुतिन के बीच बैठक हुई"))

        assertEquals(listOf("en"), dao.search("lawsuit*", 10).map { it.itemId })
        assertEquals(listOf("en"), dao.search("laws*", 10).map { it.itemId })
        assertEquals(listOf("te"), dao.search("ఆంధ్ర*", 10).map { it.itemId })
        assertEquals(listOf("hi"), dao.search("पुतिन*", 10).map { it.itemId })
        assertEquals(listOf("en"), dao.search("dozen* lawsuit*", 10).map { it.itemId })
        assertTrue(dao.search("dozen* monsoon*", 10).isEmpty())
        // DIVERGES from the Android test, which expects empty here: the bundled SQLite is built
        // with enhanced FTS query syntax, so an uppercase AND is an operator, not a literal term.
        // The app is unaffected in practice (ArticleSearch.toMatchQuery lowercases tokens and
        // drops "and"/"or" as stopwords), but the Android assertion cannot be copied verbatim.
        assertEquals(listOf("en"), dao.search("dozen* AND lawsuit*", 10).map { it.itemId })

        db.close()
    }

    @Test
    fun reindexingAnArticleDoesNotDuplicateIt() = runBlocking {
        val db = inMemory()
        val dao = db.articleTextDao()

        dao.insert(ArticleTextEntity(itemId = "a", body = "first extraction mentions monsoon"))
        dao.deleteFor("a")
        dao.insert(ArticleTextEntity(itemId = "a", body = "second extraction mentions monsoon"))

        val hits = dao.search("monsoon*", 10)
        assertEquals(1, hits.size)
        assertTrue(hits.single().body.startsWith("second"))
        assertEquals(1, dao.count())

        db.close()
    }

    @Test
    fun bundledSqliteReportsItsFtsBuild() {
        val conn = driver.open(":memory:")
        println("SPIKE-K1 sqlite_version=" + conn.scalarText("SELECT sqlite_version()"))
        for (opt in listOf("ENABLE_FTS3", "ENABLE_FTS3_PARENTHESIS", "ENABLE_FTS4", "ENABLE_FTS5")) {
            println("SPIKE-K1 compileoption $opt=" + conn.scalarInt("SELECT sqlite_compileoption_used('$opt')"))
        }
        assertEquals(1, conn.scalarInt("SELECT sqlite_compileoption_used('ENABLE_FTS3')"))
        conn.close()
    }

    /** Room KMP's exported v4 schema must carry the same identity hash as the Android-exported one. */
    @Test
    fun exportedSchemaHasTheSameIdentityHashAsTheCheckedInV4() {
        val fqcn = "xyz.mdhv.riverwip.data.db.RiverDatabase"
        fun hash(root: String) = Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"")
            .find(File(root, "$fqcn/4.json").readText())!!.groupValues[1]
        val kmp = hash(System.getProperty("spike.schemas.out"))
        val app = hash(System.getProperty("spike.schemas.app"))
        println("SPIKE-K1 identityHash app=$app kmp=$kmp")
        assertEquals(app, kmp)
    }
}
