package xyz.mdhv.riverwip.inference.local

import ai.onnxruntime.OrtEnvironment
import java.io.File
import xyz.mdhv.riverwip.inference.SynthesisRequest
import xyz.mdhv.riverwip.inference.SynthesisResult
import xyz.mdhv.riverwip.inference.TtsProvider

/**
 * On-device narration via a locally downloaded Kokoro-82M ONNX model (owner's
 * Nooz Cast ask). Independent of [LocalLlamaProvider]'s own model/download —
 * Cast has its own catalogue entry, its own files on disk, and its own gate,
 * never Flash's LLM. Loading the ONNX session below is genuinely wired
 * (onnxruntime-android is a real Maven Central dependency, unlike llama.cpp's
 * absent JNI binding — see this module's build.gradle.kts), but a loaded
 * session alone can't speak: Kokoro's graph expects a phoneme-token tensor,
 * and this build has no text-to-phoneme (G2P) pipeline integrated. [synthesize]
 * honestly reports that gap once the real session step succeeds, rather than
 * feeding the model garbage token ids and fabricating a waveform — the same
 * refusal-to-fake reasoning [LocalLlamaProvider] applies to text.
 */
class LocalKokoroTtsProvider(private val modelDir: File) : TtsProvider {
    override val id: String = "local-kokoro"

    /** The ONNX model and at least one voice embedding must both be on disk — Kokoro needs both to run. */
    fun hasModelOnDisk(): Boolean = modelFile().exists() && voiceFile().exists()

    private fun modelFile(): File = File(modelDir, MODEL_FILE_NAME)
    private fun voiceFile(): File = File(modelDir, DEFAULT_VOICE_FILE_NAME)

    /**
     * Available only once a phonemizer is wired (see [synthesize]) — a
     * downloaded model with no way to feed it real tokens isn't "available"
     * to a router, same reasoning as [LocalLlamaProvider.isAvailable].
     */
    override suspend fun isAvailable(): Boolean = PHONEMIZER_WIRED && hasModelOnDisk()

    override suspend fun synthesize(request: SynthesisRequest): SynthesisResult {
        if (!hasModelOnDisk()) return SynthesisResult.Failed(NOT_ON_DISK)
        return runCatching {
            OrtEnvironment.getEnvironment().createSession(modelFile().absolutePath).use {
                // The line above is real: it actually opens the ONNX graph on
                // this device and validates it loads. What's still missing is
                // turning `request.text` into the phoneme-token input tensor
                // Kokoro's graph expects (a G2P step) — not integrated in this
                // build, so we stop here rather than invent audio.
                SynthesisResult.Failed(NOT_WIRED)
            }
        }.getOrElse { e -> SynthesisResult.Failed(e.message ?: NOT_WIRED) }
    }

    companion object {
        /** Flip to true when a real text-to-phoneme (G2P) pipeline is integrated and verified. */
        private const val PHONEMIZER_WIRED = false
        // These must match ModelCatalogueRepository.fileFor()'s naming exactly
        // ("${model.id}.${extension}", flat under the shared models/ dir) for
        // the two Kokoro catalogue entries (kokoro-82m-q8f16, kokoro-82m-voice-
        // af-heart) — not an arbitrary filename this class gets to pick itself.
        private const val MODEL_FILE_NAME = "kokoro-82m-q8f16.onnx"
        private const val DEFAULT_VOICE_FILE_NAME = "kokoro-82m-voice-af-heart.bin"
        private const val NOT_ON_DISK = "Kokoro narration model is not downloaded yet"
        private const val NOT_WIRED =
            "on-device narration is not yet wired in this build (no text-to-phoneme pipeline integrated for Kokoro)"
    }
}
