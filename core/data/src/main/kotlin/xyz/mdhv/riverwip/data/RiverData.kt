package xyz.mdhv.riverwip.data

import android.content.Context
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.net.FeedProbe
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.repo.SourceRepository

/**
 * The data layer's assembly point. Constructs the Room database and wires the
 * repositories, exposing only domain-facing types (repositories) — never a Room
 * type. This keeps Room an `implementation` detail of `:core:data`: consumers
 * (the app's composition root) depend on this factory, not on `RoomDatabase`.
 */
class RiverData private constructor(
    val sourceRepository: SourceRepository,
) {
    companion object {
        fun create(context: Context): RiverData {
            val db = RiverDatabase.build(context.applicationContext)
            val http = HttpClient()
            val probe = FeedProbe(http)
            return RiverData(
                sourceRepository = SourceRepository(dao = db.sourceDao(), probe = probe),
            )
        }
    }
}
