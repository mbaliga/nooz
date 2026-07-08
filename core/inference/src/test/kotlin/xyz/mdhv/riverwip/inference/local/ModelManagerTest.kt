package xyz.mdhv.riverwip.inference.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class ModelManagerTest {

    @Test fun checksumMatchesKnownContent() {
        val file = Files.createTempFile("model-test", ".bin").toFile()
        file.writeText("hello world")
        val expected = MessageDigest.getInstance("SHA-256").digest("hello world".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, ChecksumVerifier.sha256Hex(file))
        assertTrue(ChecksumVerifier.verify(file, expected))
        assertFalse(ChecksumVerifier.verify(file, "0".repeat(64)))
        file.delete()
    }

    @Test fun blankExpectedChecksumNeverVerifies() {
        val file = Files.createTempFile("model-test", ".bin").toFile()
        file.writeText("x")
        assertFalse(ChecksumVerifier.verify(file, ""))
        file.delete()
    }

    @Test fun storageBudgetRespectsMargin() {
        val spec = ModelCatalog.QWEN3_4B_INSTRUCT_Q4
        assertTrue(StorageBudget.canDownload(spec, availableBytes = spec.approxSizeBytes + 1_000_000_000L))
        assertFalse(StorageBudget.canDownload(spec, availableBytes = spec.approxSizeBytes)) // no margin left
        assertFalse(StorageBudget.canDownload(spec, availableBytes = spec.approxSizeBytes / 2))
    }

    @Test fun humanReadableSizes() {
        assertEquals("512 B", StorageBudget.humanReadable(512))
        assertEquals("1.0 KB", StorageBudget.humanReadable(1024))
        assertEquals("2.5 GB", StorageBudget.humanReadable((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test fun cataloguesBothNamedModels() {
        assertEquals(2, ModelCatalog.all.size)
        assertTrue(ModelCatalog.all.any { it.id == "qwen3-4b-instruct-q4" })
        assertTrue(ModelCatalog.all.any { it.id == "gemma3-4b-q4" })
    }
}
