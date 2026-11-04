package xyz.mdhv.riverwip.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository

/**
 * Manual-DI [WorkerFactory] (house style: constructor injection, no Hilt) —
 * constructs [FetchWorker] with its repositories. Registered via
 * `Configuration.Provider` on the Application (see `RiverApplication`).
 */
class RiverWorkerFactory(
    private val itemRepository: ItemRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    private val articleRepository: ArticleRepository,
    /** Runs at the end of every fetch, success or not; the app redraws its home-screen widgets here. */
    private val afterFetch: suspend () -> Unit = {},
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        FetchWorker::class.java.name -> FetchWorker(appContext, workerParameters, itemRepository, weeklyAggregateRepository, articleRepository, afterFetch)
        else -> null
    }
}
