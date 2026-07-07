package xyz.mdhv.riverwip.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's Room database. Manual DI: constructed once in the composition root
 * and handed to repositories. `exportSchema = false` for now (schema is v1 and
 * unshipped); flip to true with a schema dir before the first release migration.
 */
@Database(
    entities = [SourceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RiverDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao

    companion object {
        fun build(context: Context): RiverDatabase =
            Room.databaseBuilder(context, RiverDatabase::class.java, "river.db")
                // v1 schema is unshipped; destructive fallback is fine until the
                // first release. Real migrations land before P7.
                .fallbackToDestructiveMigration()
                .build()
    }
}
