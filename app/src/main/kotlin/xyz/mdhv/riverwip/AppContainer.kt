package xyz.mdhv.riverwip

import android.content.Context

/**
 * The manual DI composition root (house style: constructor injection, no
 * framework). Dependencies are lazily constructed here and passed explicitly to
 * the surfaces that need them. Each phase grows this graph:
 *  - P1 sources: SourceRepository, feed autodiscovery, OPML.
 *  - P2 ingest:  fetch scheduler, dedup, classifier.
 *  - P3 reader:  article repository, full-text extractor.
 *  - P4 river:   aggregate store + analysis.
 *  - P5 lens:    inference router + fidelity guard.
 */
class AppContainer(private val appContext: Context) {
    // Intentionally empty at P0 (themed shell only). Wiring lands per phase.
}
