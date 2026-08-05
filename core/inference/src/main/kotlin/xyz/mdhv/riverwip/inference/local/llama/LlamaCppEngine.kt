package xyz.mdhv.riverwip.inference.local.llama

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * JNI bridge to the vendored llama.cpp build in `core/inference/src/main/cpp`
 * (see that directory's CMakeLists.txt for exactly which upstream commit is
 * built, and nooz_llama_jni.cpp for the native side of every `native*` call
 * below) — the runtime behind [xyz.mdhv.riverwip.inference.local.LocalLlamaProvider],
 * this app's actual on-device model execution for Nooz Flash.
 *
 * Every native call is confined to [DISPATCHER], a single-thread dispatcher:
 * llama.cpp keeps its model/context/sampler state in C globals (see the
 * native file), which are not safe to touch from more than one thread at a
 * time. A `limitedParallelism(1)` dispatcher makes that true by construction
 * — [LocalLlamaProvider] never has to remember not to call this
 * concurrently — rather than relying on caller discipline.
 */
internal class LlamaCppEngine private constructor(context: Context) {
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir

    @Volatile private var backendReady = false
    @Volatile private var loadedModelPath: String? = null

    private external fun nativeInit(nativeLibDir: String)
    private external fun nativeLoad(modelPath: String): Int
    private external fun nativePrepare(): Int
    private external fun nativeProcessSystemPrompt(systemPrompt: String): Int
    private external fun nativeProcessUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun nativeGenerateNextToken(): String?
    private external fun nativeUnload()

    private fun ensureBackend() {
        if (backendReady) return
        System.loadLibrary(LIBRARY_NAME)
        nativeInit(nativeLibDir)
        backendReady = true
    }

    /** Loads [modelPath] only if it isn't already the resident model — repeated calls with the same path are cheap. */
    private fun ensureModel(modelPath: String): Boolean {
        ensureBackend()
        if (loadedModelPath == modelPath) return true
        if (loadedModelPath != null) nativeUnload()
        loadedModelPath = null
        if (nativeLoad(modelPath) != 0) return false
        if (nativePrepare() != 0) return false
        loadedModelPath = modelPath
        return true
    }

    /**
     * One-shot completion: [systemPrompt] + [userPrompt] in, the model's
     * full reply out, or null on any native-side failure (a corrupt/
     * unsupported-architecture GGUF, a decode error, an over-length prompt
     * the native side rejected). Every call re-sends the system prompt
     * first — [LocalLlamaProvider]'s two capabilities (rewrite, digest) are
     * each single-turn, never a back-and-forth conversation, so there's no
     * reason to keep, and every reason not to keep, state from a previous
     * call bleeding into the next.
     */
    suspend fun complete(modelPath: String, systemPrompt: String, userPrompt: String, maxTokens: Int): String? =
        withContext(DISPATCHER) {
            if (!ensureModel(modelPath)) return@withContext null
            if (nativeProcessSystemPrompt(systemPrompt) != 0) return@withContext null
            if (nativeProcessUserPrompt(userPrompt, maxTokens) != 0) return@withContext null
            val out = StringBuilder()
            while (true) {
                val piece = nativeGenerateNextToken() ?: break
                out.append(piece)
            }
            out.toString().trim()
        }

    companion object {
        private const val LIBRARY_NAME = "nooz-llama"

        @OptIn(ExperimentalCoroutinesApi::class)
        private val DISPATCHER = Dispatchers.IO.limitedParallelism(1)

        @Volatile private var instance: LlamaCppEngine? = null

        /** One engine, one loaded model, for the process's lifetime — matches llama.cpp's own process-global native state. */
        fun getInstance(context: Context): LlamaCppEngine =
            instance ?: synchronized(this) {
                instance ?: LlamaCppEngine(context.applicationContext).also { instance = it }
            }
    }
}
