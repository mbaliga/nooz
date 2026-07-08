package xyz.mdhv.riverwip.inference.local

import java.io.File
import java.security.MessageDigest

/**
 * Local model manager types (brief §5: "Model manager UI: download quantized
 * Qwen3-4B-Instruct or Gemma-3-4B (~Q4) with checksum verification, storage
 * budget display, delete control. No model bundled in the APK.")
 *
 * Pure — no Android dependency, so the checksum/budget logic is unit-tested.
 */
sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelState
    data class Ready(val sizeBytes: Long) : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * A downloadable model. [downloadUrl]/[sha256] are placeholders here — brief
 * §0 requires verifying feed/API URLs live at build time, and the same
 * standard applies to a multi-gigabyte model mirror: this session did not
 * verify a live, checksummed GGUF download URL for either named model, so
 * fabricating one would silently substitute an unverified value for a real
 * decision. Populate both fields (and re-run [ChecksumVerifier]) once a
 * specific mirror is chosen and verified — see STATE.md.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val sha256: String,
    val approxSizeBytes: Long,
)

object ModelCatalog {
    val QWEN3_4B_INSTRUCT_Q4 = ModelSpec(
        id = "qwen3-4b-instruct-q4",
        displayName = "Qwen3 4B Instruct (Q4)",
        downloadUrl = "", // unverified — see kdoc above
        sha256 = "",
        approxSizeBytes = 2_600_000_000L,
    )
    val GEMMA3_4B_Q4 = ModelSpec(
        id = "gemma3-4b-q4",
        displayName = "Gemma 3 4B (Q4)",
        downloadUrl = "",
        sha256 = "",
        approxSizeBytes = 2_900_000_000L,
    )

    val all: List<ModelSpec> = listOf(QWEN3_4B_INSTRUCT_Q4, GEMMA3_4B_Q4)
}

/** SHA-256 checksum verification for a downloaded model file. Pure java.security + java.io. */
object ChecksumVerifier {
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false
        return sha256Hex(file).equals(expectedSha256, ignoreCase = true)
    }
}

/** Storage-budget checks (brief §5: "storage budget display"). Pure arithmetic. */
object StorageBudget {
    /** Can [spec] fit in [availableBytes] with [safetyMarginBytes] headroom left over? */
    fun canDownload(spec: ModelSpec, availableBytes: Long, safetyMarginBytes: Long = 500L * 1024 * 1024): Boolean =
        availableBytes - spec.approxSizeBytes >= safetyMarginBytes

    fun humanReadable(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex.coerceAtLeast(0)])
    }
}
