package xyz.mdhv.riverwip.data

import android.content.Context
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.net.FeedProbe
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.data.work.RiverWorkerFactory

/**
 * The data layer's assembly point. Constructs the Room database and wires the
 * repositories, exposing only domain-facing types (repositories, [workerFactory])
 * — never a Room type. This keeps Room an `implementation` detail of
 * `:core:data`: consumers (the app's composition root) depend on this factory,
 * not on `RoomDatabase`.
 */
class RiverData private constructor(
    val sourceRepository: SourceRepository,
    val itemRepository: ItemRepository,
    val readEventRepository: ReadEventRepository,
    val weeklyAggregateRepository: WeeklyAggregateRepository,
    /** For `Configuration.Provider` on the Application — see `RiverApplication`. */
    val workerFactory: RiverWorkerFactory,
) {
    companion object {
        fun create(context: Context): RiverData {
            val db = RiverDatabase.build(context.applicationContext)
            val http = HttpClient()
            val probe = FeedProbe(http)
            val itemRepository = ItemRepository(sourceDao = db.sourceDao(), itemDao = db.itemDao(), http = http)
            val weeklyAggregateRepository = WeeklyAggregateRepository(
                itemDao = db.itemDao(), readEventDao = db.readEventDao(), weeklyAggregateDao = db.weeklyAggregateDao(),
            )
            return RiverData(
                sourceRepository = SourceRepository(dao = db.sourceDao(), probe = probe),
                itemRepository = itemRepository,
                readEventRepository = ReadEventRepository(dao = db.readEventDao()),
                weeklyAggregateRepository = weeklyAggregateRepository,
                workerFactory = RiverWorkerFactory(itemRepository, weeklyAggregateRepository),
            )
        }
    }
}
