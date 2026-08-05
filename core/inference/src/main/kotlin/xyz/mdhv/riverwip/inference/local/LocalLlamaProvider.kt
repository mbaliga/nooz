package xyz.mdhv.riverwip.inference.local

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.inference.DigestRequest
import xyz.mdhv.riverwip.inference.DigestResult
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.PromptTemplates
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult
import xyz.mdhv.riverwip.inference.local.llama.LlamaCppEngine

/**
 * On-device inference via a locally downloaded GGUF model (brief §5,
 * `foss`-safe — llama.cpp is MIT-licensed, no proprietary dependency, no
 * model bundled in the APK). Downloading a model (real catalogue, checksum,
 * storage budget, delete) is `:core:data`'s `ModelCatalogueRepository`'s job;
 * this provider loads whichever `.gguf` that landed and runs it through a
 * real, vendored llama.cpp build (`core/inference/src/main/cpp` — see that
 * directory's CMakeLists.txt for exactly which upstream commit is built and
 * why). [xyz.mdhv.riverwip.model.FidelityGuard] still vets every result
 * downstream, same as the cloud providers — a small on-device model can't
 * smuggle a fabrication past it either.
 */
class LocalLlamaProvider(
    private val context: Context,
    private val modelDir: File,
) : InferenceProvider {
    override val id: String = "local-llama"

    private val engine by lazy { LlamaCppEngine.getInstance(context) }

    /**
     * The `.gguf` this provider will actually run, or null if none is on
     * disk. `ModelCatalogueRepository` can leave more than one downloaded at
     * once, but this provider only ever loads one at a time — the most
     * recently downloaded, on the same "latest choice wins" assumption the
     * catalogue's own download flow already makes (there's no separate
     * "active model" setting to consult instead).
     */
    private fun selectedModel(): File? =
        modelDir.listFiles { f -> f.extension == "gguf" }?.maxByOrNull { it.lastModified() }

    fun hasModelOnDisk(): Boolean = selectedModel() != null

    override suspend fun isAvailable(): Boolean = hasModelOnDisk()

    override suspend fun rewrite(request: RewriteRequest): RewriteResult {
        val model = selectedModel() ?: return RewriteResult.Failed(NOT_ON_DISK)
        val reply = runCompletion(model, PromptTemplates.REWRITE_SYSTEM, PromptTemplates.rewriteUser(request), MAX_REWRITE_TOKENS)
        if (reply.isNullOrBlank()) return RewriteResult.Failed(GENERATION_FAILED)
        return RewriteResult.Success(reply, Provenance.NATIVE)
    }

    override suspend fun digest(request: DigestRequest): DigestResult {
        if (request.headlines.isEmpty()) return DigestResult.Failed("Nothing flowed yet to compress")
        val model = selectedModel() ?: return DigestResult.Failed(NOT_ON_DISK)
        val reply = runCompletion(model, PromptTemplates.DIGEST_SYSTEM, PromptTemplates.digestUser(request), MAX_DIGEST_TOKENS)
        if (reply.isNullOrBlank()) return DigestResult.Failed(GENERATION_FAILED)
        return DigestResult.Success(reply, Provenance.NATIVE)
    }

    /** Never lets a native-side failure (bad GGUF, decode error, OOM on a resource-constrained phone) escape as an unhandled exception — same "never silent, never a crash" contract every provider here follows. */
    private suspend fun runCompletion(model: File, systemPrompt: String, userPrompt: String, maxTokens: Int): String? =
        runCatching { engine.complete(model.absolutePath, systemPrompt, userPrompt, maxTokens) }.getOrNull()

    companion object {
        // Flash's own two capabilities are both short: a neutralized phrase,
        // a ten-word-or-fewer digest sentence. Generous headroom over the
        // realistic output length, not an arbitrary chat-length budget.
        private const val MAX_REWRITE_TOKENS = 128
        private const val MAX_DIGEST_TOKENS = 48
        private const val NOT_ON_DISK = "no on-device model is downloaded yet"
        private const val GENERATION_FAILED = "the on-device model couldn't produce a result"
    }
}
