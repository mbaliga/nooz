package xyz.mdhv.riverwip.inference.local.kokoro

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** CHARACTERIZATION (current behaviour, not a reference-Kokoro oracle) of [KokoroAudio.writeWav]. No playback is claimed. */
class KokoroWavWriterTest {

    private fun write(samples: FloatArray): ByteBuffer {
        val f = File.createTempFile("kokoro-wav", ".wav")
        try {
            KokoroAudio.writeWav(samples, f)
            return ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        } finally {
            f.delete()
        }
    }

    private fun ByteBuffer.ascii(at: Int) = String(ByteArray(4) { get(at + it) }, Charsets.US_ASCII)

    @Test fun headerIsCanonical16BitMono24k() {
        val b = write(floatArrayOf(0f, 0.5f, -0.5f))
        assertEquals(44 + 6, b.capacity())
        assertEquals("RIFF", b.ascii(0))
        assertEquals(36 + 6, b.getInt(4))
        assertEquals("WAVE", b.ascii(8))
        assertEquals("fmt ", b.ascii(12))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(24_000, b.getInt(24))
        assertEquals(48_000, b.getInt(28))
        assertEquals(2, b.getShort(32).toInt())
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", b.ascii(36))
        assertEquals(6, b.getInt(40))
    }

    @Test fun samplesAreScaledRoundedAndClamped() {
        val b = write(floatArrayOf(0f, 1f, -1f, 2f, -2f, 0.5f))
        val got = (0 until 6).map { b.getShort(44 + it * 2).toInt() }
        assertEquals(listOf(0, 32767, -32767, 32767, -32767, 16384), got)
    }

    @Test fun emptyInputWritesHeaderOnly() {
        val b = write(FloatArray(0))
        assertEquals(44, b.capacity())
        assertEquals(36, b.getInt(4))
        assertEquals(0, b.getInt(40))
    }

    @Test fun overwritesExistingFile() {
        val f = File.createTempFile("kokoro-wav", ".wav")
        try {
            KokoroAudio.writeWav(FloatArray(100), f)
            KokoroAudio.writeWav(FloatArray(1), f)
            assertEquals(46L, f.length())
        } finally {
            f.delete()
        }
    }
}
