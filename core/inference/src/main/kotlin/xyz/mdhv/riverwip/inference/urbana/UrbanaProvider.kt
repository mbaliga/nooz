package xyz.mdhv.riverwip.inference.urbana

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.inference.DigestRequest
import xyz.mdhv.riverwip.inference.DigestResult
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult

/**
 * Routes to the ecosystem's Urbana routing daemon, if installed (brief §5).
 * Discovery is via the ContentProvider authority
 * `com.urbana.daemon.discovery`; if absent, [isAvailable] returns false —
 * never throws (brief: "absent support hides the provider, never errors").
 *
 * This build has no real Urbana daemon available to verify discovery or the
 * localhost OpenAI-compatible request/response shape against, so [rewrite]
 * honestly reports that gap once discovery succeeds, rather than guessing at
 * an unverified wire protocol. If Urbana ever does route a request to cloud,
 * the result must carry cloud provenance (brief §5) — that contract lives in
 * [xyz.mdhv.riverwip.inference.RewriteResult.Success.provenance] and is
 * enforced the moment a real daemon integration lands here.
 */
class UrbanaProvider(private val context: Context) : InferenceProvider {
    override val id: String = "urbana"

    private val discoveryUri: Uri = Uri.parse("content://$DISCOVERY_AUTHORITY/endpoint")

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(discoveryUri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun rewrite(request: RewriteRequest): RewriteResult = withContext(Dispatchers.IO) {
        val discovered = try {
            context.contentResolver.query(discoveryUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        if (discovered == null) {
            RewriteResult.Failed("Urbana daemon not discoverable")
        } else {
            RewriteResult.Failed("Urbana routing is not yet wired to a live daemon in this build")
        }
    }

    override suspend fun digest(request: DigestRequest): DigestResult = withContext(Dispatchers.IO) {
        val discovered = try {
            context.contentResolver.query(discoveryUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        if (discovered == null) {
            DigestResult.Failed("Urbana daemon not discoverable")
        } else {
            DigestResult.Failed("Urbana routing is not yet wired to a live daemon in this build")
        }
    }

    companion object {
        private const val DISCOVERY_AUTHORITY = "com.urbana.daemon.discovery"
    }
}
