package xyz.mdhv.riverwip.inference.local

import java.io.File
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

    override suspend fun isAvailable(): Boolean =
        modelDir.listFiles { f -> f.extension == "gguf" }?.isNotEmpty() == true

    override suspend fun rewrite(request: RewriteRequest): RewriteResult =
        RewriteResult.Failed("on-device model runtime is not yet wired in this build (no llama.cpp binding integrated)")
}
