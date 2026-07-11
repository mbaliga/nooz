package xyz.mdhv.riverwip

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.data.RiverData
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.data.work.RiverWorkerFactory
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.ProviderFactory
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore

/**
 * The manual DI composition root (house style: constructor injection, no
 * framework). The data layer is assembled behind [RiverData] so the app never
 * references a Room type (Room stays an implementation detail of `:core:data`).
 * Each phase grows this graph:
 *  - P1 sources: RiverData → SourceRepository.
 *  - P2 ingest:  RiverData → ItemRepository, ReadEventRepository,
 *                WeeklyAggregateRepository, workerFactory (WorkManager).
 *  - P3 reader:  articleRepository (full-text extraction + LRU cache).
 *  - P4 river:   aggregate store + analysis (uses weeklyAggregateRepository).
 *  - P5 lens:    inferenceRouter (default order: Urbana → local llama.cpp →
 *                ML Kit — the last only in the `full` flavor).
 *  - P6 catalogue: catalogueRepository (remote refresh, no baked-in default
 *                  URL — see STATE.md decision D4).  ← here
 */
class AppContainer(appContext: Context) {

    private val data: RiverData = RiverData.create(appContext)

    val sourceRepository: SourceRepository = data.sourceRepository
    val itemRepository: ItemRepository = data.itemRepository
    val readEventRepository: ReadEventRepository = data.readEventRepository
    val weeklyAggregateRepository: WeeklyAggregateRepository = data.weeklyAggregateRepository
    val articleRepository: ArticleRepository = data.articleRepository
    val catalogueRepository: CatalogueRepository = data.catalogueRepository
    val clippingRepository: ClippingRepository = data.clippingRepository
    val dictionaryRepository: DictionaryRepository = data.dictionaryRepository
    val settingsRepository: SettingsRepository = data.settingsRepository
    val dataExporter: DataExporter = data.dataExporter

    /** Downloaded models live in persistent storage (never purged like a cache), never bundled in the APK. */
    val inferenceRouter: InferenceRouter = InferenceRouter(
        ProviderFactory.build(appContext, File(appContext.filesDir, "models")),
    )

    /** The user's own OpenAI-compatible endpoint config (BYOK, #18). Shared with the provider by prefs name. */
    val byokConfigStore: ByokConfigStore = ByokConfigStore(appContext)

    /** For `Configuration.Provider` on [RiverApplication] — never touched by feature UI. */
    val workerFactory: RiverWorkerFactory = data.workerFactory
}
