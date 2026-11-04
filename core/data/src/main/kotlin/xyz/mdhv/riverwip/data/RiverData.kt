package xyz.mdhv.riverwip.data

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.room.InvalidationTracker
import java.io.File
import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.net.FeedProbe
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.HomeWidgetRepository
import xyz.mdhv.riverwip.data.repo.HttpArticleFetcher
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.data.repo.TranslationRepository
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
    val translationRepository: TranslationRepository,
    val settingsRepository: SettingsRepository,
    val todayInHistoryRepository: TodayInHistoryRepository,
    val dataExporter: DataExporter,
    /** Bounded, read-only queries for the home-screen widgets. */
    val homeWidgetRepository: HomeWidgetRepository,
    /** For `Configuration.Provider` on the Application — see `RiverApplication`. */
    val workerFactory: RiverWorkerFactory,
    private val db: RiverDatabase,
) {
    /**
     * Calls [listener] after any committed write to a table the home-screen
     * widgets draw from, whichever code path made it: the fetch worker, a pull
     * to refresh, a read, a clipping, a source switched on or off. This is
     * Room's own invalidation signal, so it costs no query; the listener
     * decides what to re-read. Must be called off the main thread, as Room's
     * tracker touches the database when an observer is added.
     */
    @WorkerThread
    fun addChangeListener(listener: () -> Unit) {
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(WIDGET_TABLES) {
                override fun onInvalidated(tables: Set<String>) = listener()
            },
        )
    }

    companion object {
        /** The tables a home-screen widget draws from. */
        private val WIDGET_TABLES = arrayOf("sources", "items", "read_events", "weekly_aggregates", "clippings")

        /** Full-text cache budget (brief §4: "user-visible storage budget"). Working default; a settings screen can raise/lower it later. */
        const val DEFAULT_FULL_TEXT_CACHE_BYTES: Long = 200L * 1024 * 1024

        /**
         * @param afterFetch runs at the end of every background fetch, success
         *   or not, while the worker still holds the process. The app redraws
         *   its home-screen widgets there.
         */
        fun create(context: Context, afterFetch: suspend () -> Unit = {}): RiverData {
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
                articleTextDao = db.articleTextDao(),
            )
            val sourceRepository = SourceRepository(dao = db.sourceDao(), probe = probe)
            val readEventRepository = ReadEventRepository(dao = db.readEventDao())
            val clippingRepository = ClippingRepository(dao = db.clippingDao())
            val settingsRepository = SettingsRepository(appContext)
            return RiverData(
                sourceRepository = sourceRepository,
                itemRepository = itemRepository,
                readEventRepository = readEventRepository,
                weeklyAggregateRepository = weeklyAggregateRepository,
                articleRepository = articleRepository,
                catalogueRepository = CatalogueRepository(context = appContext, http = http),
                clippingRepository = clippingRepository,
                dictionaryRepository = DictionaryRepository(appContext),
                translationRepository = TranslationRepository(appContext),
                settingsRepository = settingsRepository,
                // Its own HttpClient, not the shared `http` above: Wikimedia's
                // user-agent policy wants a UA that identifies this app and
                // offers a contact, which feed fetches have no business
                // carrying to the reader's own sources.
                todayInHistoryRepository = TodayInHistoryRepository(appContext),
                dataExporter = DataExporter(
                    settingsRepository = settingsRepository,
                    sourceRepository = sourceRepository,
                    clippingRepository = clippingRepository,
                    readEventRepository = readEventRepository,
                ),
                homeWidgetRepository = HomeWidgetRepository(db.widgetDao()),
                workerFactory = RiverWorkerFactory(itemRepository, weeklyAggregateRepository, articleRepository, afterFetch),
                db = db,
            )
        }
    }
}
