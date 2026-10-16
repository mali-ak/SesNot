package com.azhulabs.sesnot.mobile

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * m4a/AAC dosyasını 16 kHz mono 16-bit PCM'e çözer. Hem konuşmacı ayrımı (float örnekler)
 * hem SpeechRecognizer dosya kaynağı (PCM baytları) bunu kullanır.
 */
object AudioDecoder {
    const val SAMPLE_RATE = 16_000

    /** 16-bit PCM baytları, mono, 16 kHz. */
    fun decodeToPcm16(file: File): ByteArray {
        val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        val track = (0 until extractor.trackCount).first {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0); codec.start()

        val out = java.io.ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false; var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val idx = codec.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    val buf = codec.getInputBuffer(idx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) { codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            val idx = codec.dequeueOutputBuffer(info, 10_000)
            if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx)!!
                val chunk = ByteArray(info.size); buf.get(chunk); buf.clear()
                out.write(downmixAndResample(chunk, srcChannels, srcRate))
                codec.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop(); codec.release(); extractor.release()
        return out.toByteArray()
    }

    /** PCM16 → [-1, 1] float örnekler (sherpa-onnx girişi). */
    fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val sb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val f = FloatArray(sb.remaining())
        for (i in f.indices) f[i] = sb.get(i) / 32768f
        return f
    }

    /** PCM16 dizisinden [startMs, endMs) aralığını kes. */
    fun slice(pcm: ByteArray, startMs: Long, endMs: Long): ByteArray {
        val bytesPerMs = SAMPLE_RATE * 2 / 1000
        val s = (startMs * bytesPerMs).toInt().coerceIn(0, pcm.size) and 1.inv()
        val e = (endMs * bytesPerMs).toInt().coerceIn(s, pcm.size) and 1.inv()
        return pcm.copyOfRange(s, e)
    }

    /** Çok kanallı / farklı örnekleme hızlı girişleri mono 16 kHz'e indir (basit doğrusal yeniden örnekleme). */
    private fun downmixAndResample(chunk: ByteArray, channels: Int, rate: Int): ByteArray {
        if (channels == 1 && rate == SAMPLE_RATE) return chunk
        val src = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = src.remaining() / channels
        val mono = ShortArray(frames) { i ->
            var sum = 0; for (c in 0 until channels) sum += src.get(i * channels + c)
            (sum / channels).toShort()
        }
        if (rate == SAMPLE_RATE) return shortsToBytes(mono)
        val outFrames = (frames.toLong() * SAMPLE_RATE / rate).toInt()
        val res = ShortArray(outFrames) { i ->
            val pos = i.toDouble() * rate / SAMPLE_RATE
            val i0 = pos.toInt().coerceAtMost(frames - 1); val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val t = pos - i0
            (mono[i0] * (1 - t) + mono[i1] * t).toInt().toShort()
        }
        return shortsToBytes(res)
    }

    private fun shortsToBytes(s: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        s.forEach { bb.putShort(it) }
        return bb.array()
    }
}
