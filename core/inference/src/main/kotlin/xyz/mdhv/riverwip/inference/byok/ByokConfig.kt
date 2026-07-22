package xyz.mdhv.riverwip.inference.byok

import android.content.Context

/**
 * Bring-your-own-key config (owner's #18): the user's own OpenAI-compatible
 * endpoint, key, and model. This routes a rewrite to *their* provider, on their
 * terms — always cloud-marked ([xyz.mdhv.riverwip.inference.Provenance.CLOUD]),
 * never on by default, and never included in the data export.
 */
data class ByokConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val isComplete: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** The chat-completions endpoint for this base URL (OpenAI-compatible). */
    val chatCompletionsUrl: String
        get() {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }
}

/**
 * Stores the BYOK config. Local, private preferences — the key never leaves the
 * device and is excluded from the data export. NOTE (follow-up): move the key
 * itself into the Android Keystore / EncryptedSharedPreferences before shipping
 * a release; plain private prefs are the v1 stopgap and are logged in STATE.md.
 */
class ByokConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("byok_config", Context.MODE_PRIVATE)

    fun load(): ByokConfig = ByokConfig(
        baseUrl = prefs.getString(KEY_URL, "").orEmpty(),
        apiKey = prefs.getString(KEY_KEY, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, "").orEmpty(),
    )

    fun save(config: ByokConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl.trim())
            .putString(KEY_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim())
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /** True when there's enough to attempt a call (checked by the provider's isAvailable). */
    val isConfigured: Boolean get() = load().isComplete

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
