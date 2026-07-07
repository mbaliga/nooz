package xyz.mdhv.riverwip

import android.content.Context
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.net.FeedProbe
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.repo.SourceRepository

/**
 * The manual DI composition root (house style: constructor injection, no
 * framework). Dependencies are constructed here and passed explicitly to the
 * surfaces that need them. Each phase grows this graph:
 *  - P1 sources: RiverDatabase, HttpClient, FeedProbe, SourceRepository.  ← here
 *  - P2 ingest:  fetch scheduler, dedup, classifier.
 *  - P3 reader:  article repository, full-text extractor.
 *  - P4 river:   aggregate store + analysis.
 *  - P5 lens:    inference router + fidelity guard.
 */
class AppContainer(appContext: Context) {

    private val database: RiverDatabase = RiverDatabase.build(appContext)
    private val httpClient: HttpClient = HttpClient()
    private val feedProbe: FeedProbe = FeedProbe(httpClient)

    val sourceRepository: SourceRepository = SourceRepository(
        dao = database.sourceDao(),
        probe = feedProbe,
    )
}
