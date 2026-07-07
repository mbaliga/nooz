package xyz.mdhv.riverwip

import android.content.Context
import xyz.mdhv.riverwip.data.RiverData
import xyz.mdhv.riverwip.data.repo.SourceRepository

/**
 * The manual DI composition root (house style: constructor injection, no
 * framework). The data layer is assembled behind [RiverData] so the app never
 * references a Room type (Room stays an implementation detail of `:core:data`).
 * Each phase grows this graph:
 *  - P1 sources: RiverData → SourceRepository.  ← here
 *  - P2 ingest:  fetch scheduler, dedup, classifier.
 *  - P3 reader:  article repository, full-text extractor.
 *  - P4 river:   aggregate store + analysis.
 *  - P5 lens:    inference router + fidelity guard.
 */
class AppContainer(appContext: Context) {

    private val data: RiverData = RiverData.create(appContext)

    val sourceRepository: SourceRepository = data.sourceRepository
}
