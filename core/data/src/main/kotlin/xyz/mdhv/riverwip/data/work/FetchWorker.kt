package xyz.mdhv.riverwip.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository

/**
 * Scheduled ingest (brief §P2): fetch every enabled source, ingest, roll the
 * result into weekly aggregates, then prune Item rows past retention and the
 * search-index rows that belonged to them. Per-source
 * failures are isolated inside [ItemRepository.fetchAndIngestAllEnabled] — one
 * source going down never fails the whole run.
 */
class FetchWorker(
    context: Context,
    params: WorkerParameters,
    private val itemRepository: ItemRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    private val articleRepository: ArticleRepository,
    private val afterFetch: suspend () -> Unit = {},
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Offline mode: nothing to fetch, and nothing to retry -- the next
        // periodic run after the reader turns it off picks up as normal.
        if (xyz.mdhv.riverwip.data.net.OfflineMode.enabled) return Result.success()
        val result = try {
            itemRepository.fetchAndIngestAllEnabled()
            weeklyAggregateRepository.recompute()
            itemRepository.pruneOlderThan()
            // Retention just removed items; the search index must follow them
            // out or it keeps prose for stories that can no longer be opened.
            articleRepository.pruneIndexOrphans()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
        // Whatever this run left behind, the home-screen widgets redraw from
        // it now, while the worker still holds the process -- a failed run
        // changes source health too. A widget that cannot redraw never fails
        // the fetch.
        try {
            afterFetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to do: the widgets keep their last drawing.
        }
        return result
    }

    companion object {
        const val UNIQUE_WORK_NAME = "river-fetch"
    }
}
