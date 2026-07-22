package xyz.mdhv.riverwip.inference.local

import java.io.File
import xyz.mdhv.riverwip.inference.DigestRequest
import xyz.mdhv.riverwip.inference.DigestResult
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult

/**
 * On-device inference via a locally downloaded GGUF model (brief §5,
 * `foss`-safe — no proprietary dependency, no model bundled in the APK).
 * Downloading a model (real catalogue, checksum, storage budget, delete) is
 * `:core:data`'s `ModelCatalogueRepository`'s job; this provider only asks "is
 * anything on disk to run" and, if so, tries to run it. **Actual model
 * execution is not yet wired**: this build has no llama.cpp JNI binding
 * integrated, so [rewrite] honestly reports that gap rather than fabricating a
 * result. The whole point of [xyz.mdhv.riverwip.model.FidelityGuard] is that
 * small models measurably fabricate numbers, entities, and negations —
 * silently faking a "successful" rewrite here would be worse than admitting
 * the gap plainly.
 */
class LocalLlamaProvider(private val modelDir: File) : InferenceProvider {
    override val id: String = "local-llama"

    /** A downloaded GGUF is on disk — necessary for on-device inference, but not sufficient without a runtime. */
    fun hasModelOnDisk(): Boolean =
        modelDir.listFiles { f -> f.extension == "gguf" }?.isNotEmpty() == true

    /**
     * Available only when a model is on disk **and** a runtime can actually run
     * it. No llama.cpp JNI binding is integrated yet, so this stays false even
     * after a download — which is the honest state for the *router*: it then
     * skips this provider cleanly (falling through to a configured key, or an
     * honest "no provider" message) instead of picking it and surfacing a
     * per-call failure every time (owner: the downloaded-model "wiring error").
     * The download still matters — it's ready on disk for when [RUNTIME_WIRED]
     * flips as the binding lands.
     */
    override suspend fun isAvailable(): Boolean = RUNTIME_WIRED && hasModelOnDisk()

    override suspend fun rewrite(request: RewriteRequest): RewriteResult =
        RewriteResult.Failed(NOT_WIRED)

    override suspend fun digest(request: DigestRequest): DigestResult =
        DigestResult.Failed(NOT_WIRED)

    companion object {
        /** Flip to true when a real llama.cpp binding is integrated and verified. */
        private const val RUNTIME_WIRED = false
        private const val NOT_WIRED =
            "on-device model runtime is not yet wired in this build (no llama.cpp binding integrated)"
    }
}
