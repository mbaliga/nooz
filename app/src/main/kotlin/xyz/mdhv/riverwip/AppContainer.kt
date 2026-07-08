package xyz.mdhv.riverwip

import android.content.Context
import xyz.mdhv.riverwip.data.RiverData
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.data.work.RiverWorkerFactory

/**
 * The manual DI composition root (house style: constructor injection, no
 * framework). The data layer is assembled behind [RiverData] so the app never
 * references a Room type (Room stays an implementation detail of `:core:data`).
 * Each phase grows this graph:
 *  - P1 sources: RiverData → SourceRepository.
 *  - P2 ingest:  RiverData → ItemRepository, ReadEventRepository,
 *                WeeklyAggregateRepository, workerFactory (WorkManager).
 *  - P3 reader:  articleRepository (full-text extraction + LRU cache).  ← here
 *  - P4 river:   aggregate store + analysis (uses weeklyAggregateRepository).
 *  - P5 lens:    inference router + fidelity guard.
 */
class AppContainer(appContext: Context) {

    private val data: RiverData = RiverData.create(appContext)

    val sourceRepository: SourceRepository = data.sourceRepository
    val itemRepository: ItemRepository = data.itemRepository
    val readEventRepository: ReadEventRepository = data.readEventRepository
    val weeklyAggregateRepository: WeeklyAggregateRepository = data.weeklyAggregateRepository
    val articleRepository: ArticleRepository = data.articleRepository

    /** For `Configuration.Provider` on [RiverApplication] — never touched by feature UI. */
    val workerFactory: RiverWorkerFactory = data.workerFactory
}
