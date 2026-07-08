package xyz.mdhv.riverwip.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository

/**
 * Scheduled ingest (brief §P2): fetch every enabled source, ingest, roll the
 * result into weekly aggregates, then prune Item rows past retention. Per-source
 * failures are isolated inside [ItemRepository.fetchAndIngestAllEnabled] — one
 * source going down never fails the whole run.
 */
class FetchWorker(
    context: Context,
    params: WorkerParameters,
    private val itemRepository: ItemRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        itemRepository.fetchAndIngestAllEnabled()
        weeklyAggregateRepository.recompute()
        itemRepository.pruneOlderThan()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "river-fetch"
    }
}
