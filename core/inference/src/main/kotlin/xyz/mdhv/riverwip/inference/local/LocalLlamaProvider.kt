package xyz.mdhv.riverwip.inference.local

import java.io.File
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult

/**
 * On-device inference via a locally downloaded GGUF model (brief §5,
 * `foss`-safe — no proprietary dependency, no model bundled in the APK). The
 * model manager (download state, checksum, storage budget, delete) is real —
 * see [ModelManager]. **Actual model execution is not yet wired**: this build
 * has no llama.cpp JNI binding integrated, so [rewrite] honestly reports that
 * gap rather than fabricating a result. The whole point of [xyz.mdhv.riverwip
 * .model.FidelityGuard] is that small models measurably fabricate numbers,
 * entities, and negations — silently faking a "successful" rewrite here would
 * be worse than admitting the gap plainly.
 */
class LocalLlamaProvider(private val modelDir: File) : InferenceProvider {
    override val id: String = "local-llama"

    /** Current state of [spec]'s model file (brief §5 model manager UI). */
    fun modelState(spec: ModelSpec): ModelState {
        val file = File(modelDir, "${spec.id}.gguf")
        if (!file.exists()) return ModelState.NotDownloaded
        return when {
            spec.sha256.isBlank() ->
                // No verified checksum populated for this build yet (see ModelSpec
                // kdoc — the download URL/checksum are an unverified gap, not a
                // silent substitution). Treat a present file as unverified.
                ModelState.Failed("model checksum not yet verified for this build")
            ChecksumVerifier.verify(file, spec.sha256) -> ModelState.Ready(file.length())
            else -> ModelState.Failed("checksum mismatch — delete and re-download")
        }
    }

    fun delete(spec: ModelSpec) {
        File(modelDir, "${spec.id}.gguf").delete()
    }

    override suspend fun isAvailable(): Boolean =
        ModelCatalog.all.any { modelState(it) is ModelState.Ready }

    override suspend fun rewrite(request: RewriteRequest): RewriteResult =
        RewriteResult.Failed("on-device model runtime is not yet wired in this build (no llama.cpp binding integrated)")
}
