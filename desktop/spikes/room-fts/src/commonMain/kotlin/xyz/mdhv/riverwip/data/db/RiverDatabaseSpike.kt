package xyz.mdhv.riverwip.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * SPIKE copy of the app's RiverDatabase: same FQCN, entities, version and DAOs, so the
 * checked-in schemas/<fqcn>/N.json apply. Differences from core/data's original are the
 * API forced by Room KMP: @ConstructedBy, and Migration.migrate(SQLiteConnection).
 * The DDL string is copied verbatim from the original MIGRATION_3_4.
 */
@Database(
    entities = [
        SourceEntity::class,
        ItemEntity::class,
        ReadEventEntity::class,
        WeeklyAggregateEntity::class,
        ClippingEntity::class,
        ArticleTextEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(RiverDatabaseConstructor::class)
abstract class RiverDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun itemDao(): ItemDao
    abstract fun readEventDao(): ReadEventDao
    abstract fun weeklyAggregateDao(): WeeklyAggregateDao
    abstract fun clippingDao(): ClippingDao
    abstract fun articleTextDao(): ArticleTextDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `article_text` " +
                        "USING FTS4(`itemId` TEXT NOT NULL, `body` TEXT NOT NULL, tokenize=unicode61)",
                )
            }
        }
    }
}

// Room generates the actual.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object RiverDatabaseConstructor : RoomDatabaseConstructor<RiverDatabase> {
    override fun initialize(): RiverDatabase
}
