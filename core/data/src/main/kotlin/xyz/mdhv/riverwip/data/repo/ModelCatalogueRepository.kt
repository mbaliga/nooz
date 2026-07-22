package xyz.mdhv.riverwip.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.mdhv.riverwip.data.net.HttpClient
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Context.modelCatalogueDataStore by preferencesDataStore(name = "model_catalogue")

@Serializable
data class CatalogueModel(
    val id: String,
    val name: String,
    val kind: String,
    val sizeBytes: Long,
    val downloadUrl: String? = null,
    val hfRepo: String? = null,
    val sha256: String? = null,
    val policySafe: Boolean = true,
    val verifiedAt: String? = null,
    val note: String? = null,
    /** Links companion files that must all be present together, e.g. a TTS model plus its required voice pack. */
    val groupId: String? = null,
)

@Serializable
private data class CatalogueFile(
    val schemaVersion: Int = 1,
    val lastUpdated: String = "",
    val models: List<CatalogueModel> = emptyList(),
)

/** A model's in-flight download state, keyed by id; absent from the map means idle/not-started. */
sealed interface ModelDownloadState {
    data class Downloading(val progress: Float) : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Failed(val reason: String) : ModelDownloadState
}

/**
 * The real, one-click downloadable model list (owner's #18 follow-up): reads
 * `ai-catalogue/models.json` — this repo's own honestly-maintained, live-probed
 * catalogue (see `ai-catalogue/README.md`) — instead of the placeholder,
 * always-empty entries the lens's on-device provider used to hardcode. Only
 * `policySafe` entries of the requested `kind` (default `LLM_GGUF`; Nooz Cast
 * asks for `TTS_ONNX` instead, its own independent gate) with a verified
 * `downloadUrl` are ever offered; a `null` URL is never rendered as
 * "downloadable" (the catalogue's own honesty rule 4).
 *
 * A bundled snapshot is the offline/first-run default; refreshing is an
 * explicit, user-initiated action (never automatic), matching the catalogue's
 * consumer contract.
 */
class ModelCatalogueRepository(
    private val context: Context,
    private val http: HttpClient = HttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private object Keys {
        val REFRESHED_JSON = stringPreferencesKey("refreshed_models_json")
        val REFRESHED_AT = longPreferencesKey("refreshed_at")
    }

    fun observeLastRefreshedAt(): Flow<Long?> = context.modelCatalogueDataStore.data.map { it[Keys.REFRESHED_AT] }

    /** The bundled snapshot, or a previously-fetched refresh if one exists and still parses. */
    fun observeCatalogue(): Flow<List<CatalogueModel>> = context.modelCatalogueDataStore.data.map { prefs ->
        val raw = prefs[Keys.REFRESHED_JSON]
        val parsed = raw?.let { runCatching { json.decodeFromString(CatalogueFile.serializer(), it) }.getOrNull() }
        (parsed ?: bundled()).models
    }

    private fun bundled(): CatalogueFile = runCatching {
        context.assets.open("ai_catalogue_models.json").bufferedReader().use {
            json.decodeFromString(CatalogueFile.serializer(), it.readText())
        }
    }.getOrDefault(CatalogueFile())

    /** Only what this app can actually offer for [kind]: official/licensed weights, a verified mirror. */
    fun downloadable(models: List<CatalogueModel>, kind: String = "LLM_GGUF"): List<CatalogueModel> =
        models.filter { it.kind == kind && it.policySafe && !it.downloadUrl.isNullOrBlank() }

    suspend fun refresh(clock: () -> Long = { System.currentTimeMillis() }): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = http.get(DEFAULT_CATALOGUE_URL)
            if (!resp.isSuccess) error("HTTP ${resp.code}")
            val parsed = json.decodeFromString(CatalogueFile.serializer(), resp.body)
            context.modelCatalogueDataStore.edit { prefs ->
                prefs[Keys.REFRESHED_JSON] = resp.body
                prefs[Keys.REFRESHED_AT] = clock()
            }
            parsed.models.size
        }
    }

    // ---- download / delete ----

    private fun modelsDir() = File(context.filesDir, "models").apply { mkdirs() }

    /** Extension follows the real download, not a hardcoded assumption — GGUF isn't the only [CatalogueModel.kind] on disk anymore (e.g. Kokoro's `.onnx`/`.bin`). */
    fun fileFor(model: CatalogueModel): File =
        File(modelsDir(), "${model.id}.${model.downloadUrl?.substringAfterLast('.', "bin") ?: "bin"}")
    fun isDownloaded(model: CatalogueModel): Boolean = fileFor(model).exists()

    /** Bytes free where the model would land, so a multi-gigabyte download can be refused honestly up front. */
    fun availableStorageBytes(): Long = modelsDir().usableSpace

    suspend fun download(model: CatalogueModel, onProgress: (Float) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = model.downloadUrl ?: error("No verified download URL for ${model.name}")
                val dir = modelsDir()
                val tmp = File(dir, "${model.id}.download")
                downloadWithProgress(url, tmp, model.sizeBytes, onProgress)
                val dest = fileFor(model)
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
        }

    fun delete(model: CatalogueModel) {
        fileFor(model).delete()
    }

    /** Streaming download with redirect-following (mirrors [DictionaryRepository]) plus periodic progress. */
    private fun downloadWithProgress(url: String, dest: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "river/0.1 (+news-omission-reader)")
            }
            try {
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308) && redirects < 5) {
                    val loc = conn.getHeaderField("Location") ?: error("redirect without Location")
                    current = URL(URL(current), loc).toString()
                    redirects++
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) error("HTTP $code downloading model")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
                var readBytes = 0L
                var lastReported = -1
                conn.inputStream.use { ins ->
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = ins.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            readBytes += n
                            if (total > 0) {
                                val pct = (readBytes.toFloat() / total).coerceIn(0f, 1f)
                                val reportable = (pct * 100).toInt()
                                if (reportable != lastReported) {
                                    lastReported = reportable
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
                onProgress(1f)
                return
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        /**
         * This constellation's accepted interim convention (D16): the catalogue's
         * canonical home is this repo's own default branch — not `main`, which
         * stays an empty placeholder here — mirroring how Aarso already points at
         * it. If that branch is ever renamed, repoint this alongside Aarso's
         * `SessionStore.DEFAULT_MC_URL`.
         */
        const val DEFAULT_CATALOGUE_URL: String =
            "https://raw.githubusercontent.com/mbaliga/nooz/claude/app-build-d1f9s6/ai-catalogue/models.json"
    }
}
