package xyz.mdhv.riverwip.inference.local

import java.io.File
import java.security.MessageDigest

/**
 * Local model manager utilities (brief §5: checksum verification, storage
 * budget display). The catalogue itself — which models exist, their verified
 * download URL, size — now lives in `:core:data`'s `ModelCatalogueRepository`,
 * reading the constellation's shared `ai-catalogue/models.json` (real,
 * live-probed mirrors) rather than a hardcoded, permanently-unverified pair of
 * placeholder entries. This object stays here as pure, Android-free utilities
 * both that repository and [LocalLlamaProvider] can use.
 */
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
    /** Can a download of [sizeBytes] fit in [availableBytes] with [safetyMarginBytes] headroom left over? */
    fun canDownload(sizeBytes: Long, availableBytes: Long, safetyMarginBytes: Long = 500L * 1024 * 1024): Boolean =
        availableBytes - sizeBytes >= safetyMarginBytes

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
