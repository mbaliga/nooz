package xyz.mdhv.riverwip.inference.local.kokoro

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Writes Kokoro's raw float32 waveform (owner docs: 24kHz, mono) out as a real, playable 16-bit PCM WAV file — [android.media.MediaPlayer] needs real headers, not a bare sample dump. */
object KokoroAudio {
    private const val SAMPLE_RATE = 24_000

    fun writeWav(samples: FloatArray, destination: File) {
        val pcm = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }
        val dataSize = pcm.size * 2
        RandomAccessFile(destination, "rw").use { out ->
            out.setLength(0)
            out.writeAscii("RIFF")
            out.writeLeInt(36 + dataSize)
            out.writeAscii("WAVE")
            out.writeAscii("fmt ")
            out.writeLeInt(16) // PCM fmt chunk size
            out.writeLeShort(1) // PCM
            out.writeLeShort(1) // mono
            out.writeLeInt(SAMPLE_RATE)
            out.writeLeInt(SAMPLE_RATE * 2) // byte rate = rate * channels * bytesPerSample
            out.writeLeShort(2) // block align
            out.writeLeShort(16) // bits per sample
            out.writeAscii("data")
            out.writeLeInt(dataSize)
            val bytes = ByteArray(dataSize)
            for (i in pcm.indices) {
                val v = pcm[i].toInt()
                bytes[i * 2] = (v and 0xFF).toByte()
                bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            out.write(bytes)
        }
    }

    private fun RandomAccessFile.writeAscii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
    private fun RandomAccessFile.writeLeInt(v: Int) {
        write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()))
    }
    private fun RandomAccessFile.writeLeShort(v: Int) {
        write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
    }
}
