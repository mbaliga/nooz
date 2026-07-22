package xyz.mdhv.riverwip.inference.mlkit

import xyz.mdhv.riverwip.inference.DigestRequest
import xyz.mdhv.riverwip.inference.DigestResult
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult

/**
 * ML Kit GenAI rewriting (brief §5, `full` flavor only — Pixel-class devices).
 * Lives in `src/full`, not `src/main`, so the `foss` flavor never references
 * ML Kit at all (brief: "`full` only adds an inference provider").
 *
 * This build does not yet depend on the ML Kit GenAI artifact — it's a newer,
 * evolving API surface this session had no real device to verify capability
 * detection against, so [isAvailable] conservatively reports false rather than
 * guess (brief §5: "Detect support at runtime; absent support hides the
 * provider, never errors"). Wiring the real dependency + capability check is a
 * logged follow-up (see STATE.md).
 */
class MlKitProvider : InferenceProvider {
    override val id: String = "mlkit"

    override suspend fun isAvailable(): Boolean = false

    override suspend fun rewrite(request: RewriteRequest): RewriteResult =
        RewriteResult.Failed("ML Kit GenAI is not yet integrated in this build")

    override suspend fun digest(request: DigestRequest): DigestResult =
        DigestResult.Failed("ML Kit GenAI is not yet integrated in this build")
}
