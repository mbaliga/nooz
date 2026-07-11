package xyz.mdhv.riverwip.data

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.net.FeedProbe
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.HttpArticleFetcher
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
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
    val articleRepository: ArticleRepository,
    val catalogueRepository: CatalogueRepository,
    val clippingRepository: ClippingRepository,
    val dictionaryRepository: DictionaryRepository,
    val settingsRepository: SettingsRepository,
    /** For `Configuration.Provider` on the Application — see `RiverApplication`. */
    val workerFactory: RiverWorkerFactory,
) {
    companion object {
        /** Full-text cache budget (brief §4: "user-visible storage budget"). Working default; a settings screen can raise/lower it later. */
        const val DEFAULT_FULL_TEXT_CACHE_BYTES: Long = 200L * 1024 * 1024

        fun create(context: Context): RiverData {
            val appContext = context.applicationContext
            val db = RiverDatabase.build(appContext)
            val http = HttpClient()
            val probe = FeedProbe(http)
            val itemRepository = ItemRepository(sourceDao = db.sourceDao(), itemDao = db.itemDao(), http = http)
            val weeklyAggregateRepository = WeeklyAggregateRepository(
                itemDao = db.itemDao(), readEventDao = db.readEventDao(), weeklyAggregateDao = db.weeklyAggregateDao(),
            )
            val fullTextCache = FullTextCache(File(appContext.cacheDir, "full-text"), DEFAULT_FULL_TEXT_CACHE_BYTES)
            val articleRepository = ArticleRepository(
                itemDao = db.itemDao(), fetcher = HttpArticleFetcher(http), cache = fullTextCache,
            )
            return RiverData(
                sourceRepository = SourceRepository(dao = db.sourceDao(), probe = probe),
                itemRepository = itemRepository,
                readEventRepository = ReadEventRepository(dao = db.readEventDao()),
                weeklyAggregateRepository = weeklyAggregateRepository,
                articleRepository = articleRepository,
                catalogueRepository = CatalogueRepository(context = appContext, http = http),
                clippingRepository = ClippingRepository(dao = db.clippingDao()),
                dictionaryRepository = DictionaryRepository(appContext),
                settingsRepository = SettingsRepository(appContext),
                workerFactory = RiverWorkerFactory(itemRepository, weeklyAggregateRepository),
            )
        }
    }
}
