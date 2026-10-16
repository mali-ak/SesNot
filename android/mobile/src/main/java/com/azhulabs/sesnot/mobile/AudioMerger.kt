package com.azhulabs.sesnot.mobile

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** m4a parçalarını yeniden kodlamadan tek dosyada birleştirir (MediaMuxer, aynı kodek parametreleri). */
object AudioMerger {
    fun merge(parts: List<File>, output: File) {
        output.delete()
        if (parts.size == 1) { parts[0].copyTo(output); return }

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var timeOffsetUs = 0L
        val buffer = ByteBuffer.allocate(1 shl 20)
        val info = MediaCodec.BufferInfo()

        for (part in parts) {
            val extractor = MediaExtractor().apply { setDataSource(part.absolutePath) }
            val audioTrack = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            if (trackIndex < 0) { trackIndex = muxer.addTrack(format); muxer.start() }

            var lastPts = 0L
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime + timeOffsetUs, extractor.sampleFlags)
                lastPts = extractor.sampleTime
                muxer.writeSampleData(trackIndex, buffer, info)
                extractor.advance()
            }
            timeOffsetUs += lastPts + 20_000 // 20 ms boşluk; AAC çerçeve süresi ~64 ms @16 kHz
            extractor.release()
        }
        muxer.stop(); muxer.release()
    }
}
