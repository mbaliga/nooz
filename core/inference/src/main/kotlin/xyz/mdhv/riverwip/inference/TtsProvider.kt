package xyz.mdhv.riverwip.inference

import java.io.File

/**
 * A full-article narration request (Nooz Cast) — the text-to-audio analog of
 * [RewriteRequest]/[DigestRequest]. [voiceId] names one of the provider's
 * installed voice embeddings; defaults to the one voice this build's
 * catalogue entry actually ships (Kokoro's "af_heart").
 */
data class SynthesisRequest(val text: String, val voiceId: String = "af_heart")

sealed interface SynthesisResult {
    /**
     * [audioFile] is a rendered on-device waveform — always [Provenance.NATIVE];
     * Cast has no cloud path to mark otherwise. [skippedChunks] counts pieces
     * of the article that failed to synthesize and were left out of
     * [audioFile] rather than aborting the whole narration (a single
     * pathological chunk shouldn't discard everything already narrated
     * successfully) — 0 on the ordinary, fully-narrated path. A caller that
     * cares whether the article is only partially voiced checks this rather
     * than assuming success means complete, since "the provider ran but
     * declined or errored" (see [Failed]) never fires for a partial failure.
     */
    data class Success(val audioFile: File, val provenance: Provenance, val skippedChunks: Int = 0) : SynthesisResult
    /** The provider ran but declined or errored — never silent (brief §3). */
    data class Failed(val reason: String) : SynthesisResult
}

/**
 * Nooz Cast's provider contract (owner's ask: a natural-sounding reader, not
 * the robotic [android.speech.tts.TextToSpeech] "Play" already has). Kept
 * separate from [InferenceProvider] on purpose: Cast is on-device only by
 * design ("a private anchor voice should never leave the device"), so there's
 * no rewrite/digest/cloud shape here to invite one.
 */
interface TtsProvider {
    /** A stable id for logging/ordering, e.g. "local-kokoro". */
    val id: String

    /** Runtime capability check. Absent support must return false, never throw (brief §5). */
    suspend fun isAvailable(): Boolean

    suspend fun synthesize(request: SynthesisRequest): SynthesisResult
}
