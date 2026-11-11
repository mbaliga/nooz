package xyz.mdhv.riverwip.data.net

import java.io.IOException

/**
 * Process-wide offline switch (the reader's "Offline" toggle). While on, no
 * feed, article or image request leaves the device: [HttpClient] refuses with
 * [OfflineException], the background fetch worker does nothing, and image
 * loading is served from cache only. Everything already on the device — the
 * stored items, cached full text, cached images — keeps working.
 *
 * Kept as a tiny global rather than threaded through every repository because
 * [HttpClient] is constructed in many places and the whole point is that no
 * path can forget to honour it. It is driven from the persisted setting by
 * the Application at startup.
 */
object OfflineMode {
    @Volatile
    var enabled: Boolean = false
}

/** Thrown instead of touching the network while [OfflineMode] is on. */
class OfflineException : IOException("Offline mode is on")
