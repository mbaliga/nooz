package xyz.mdhv.riverwip.inference.byok

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult
import java.net.HttpURLConnection
import java.net.URL

/**
 * The bring-your-own-key provider (owner's #18): routes a span rewrite to the
 * user's own OpenAI-compatible chat-completions endpoint. Available only when a
 * key is configured; the result always carries [Provenance.CLOUD] so the UI can
 * mark it (brief §5). The deterministic [xyz.mdhv.riverwip.model.FidelityGuard]
 * still vets the output downstream — a cloud model can't smuggle a fabrication
 * past it.
 */
class ByokProvider(private val store: ByokConfigStore) : InferenceProvider {
    override val id: String = "byok"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun isAvailable(): Boolean = store.isConfigured

    override suspend fun rewrite(request: RewriteRequest): RewriteResult = withContext(Dispatchers.IO) {
        val cfg = store.load()
        if (!cfg.isComplete) return@withContext RewriteResult.Failed("No API key configured")

        val payload = buildJsonObject {
            put("model", cfg.model)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userPrompt(request))
                }
            })
        }

        try {
            val conn = (URL(cfg.chatCompletionsUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 40_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            }
            try {
                conn.outputStream.use { it.write(json.encodeToString(JsonObject.serializer(), payload).toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
                    return@withContext RewriteResult.Failed("Your provider returned HTTP $code${if (err.isNullOrBlank()) "" else ": $err"}")
                }
                val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
                val content = parseContent(bodyText)
                if (content.isNullOrBlank()) {
                    RewriteResult.Failed("Your provider returned no rewrite")
                } else {
                    RewriteResult.Success(content.trim(), Provenance.CLOUD)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            RewriteResult.Failed("Couldn't reach your provider: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Pull choices[0].message.content out of a standard chat-completions response. */
    private fun parseContent(bodyText: String): String? = try {
        json.parseToJsonElement(bodyText)
            .jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    private fun userPrompt(request: RewriteRequest): String =
        "Sentence:\n${request.fullSentence}\n\n" +
            "Rewrite ONLY the phrase \"${request.spanText}\" to remove its charge, keeping every fact, " +
            "number, name, and negation identical. Return the full sentence, nothing else."

    companion object {
        private const val SYSTEM_PROMPT =
            "You neutralize loaded language in news sentences. Replace only the specified phrase with a plain, " +
                "neutral wording. Never add, drop, or change any fact, number, named entity, or negation. " +
                "Output only the rewritten sentence."
    }
}
