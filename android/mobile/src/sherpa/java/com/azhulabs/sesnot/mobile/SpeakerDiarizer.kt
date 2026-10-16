package com.azhulabs.sesnot.mobile

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/**
 * Cihaz üstü konuşmacı ayrımı (sherpa-onnx: pyannote segmentation-3.0 + 3D-Speaker ERes2Net embedding).
 * Bu dosya yalnızca `mobile/libs/sherpa-onnx.aar` mevcutken derlenir (bkz. build.gradle.kts).
 * Modeller `assets/diarization/` altında beklenir; yoksa `isAvailable` false döner ve
 * pipeline tek konuşmacı varsayar. Dil bağımsızdır; Türkçe için ek eğitim gerekmez.
 */
class SpeakerDiarizer(private val context: Context) {

    private val modelDir = File(context.filesDir, "diarization")
    val isAvailable: Boolean get() = SEGMENTATION_MODEL in assetNames() && EMBEDDING_MODEL in assetNames()

    private fun assetNames(): List<String> =
        runCatching { context.assets.list("diarization")?.toList() }.getOrNull() ?: emptyList()

    /** Asset'leri filesDir'e kopyalar (sherpa-onnx dosya yolu ister). */
    private fun ensureModels(): Boolean {
        if (!isAvailable) return false
        modelDir.mkdirs()
        for (name in listOf(SEGMENTATION_MODEL, EMBEDDING_MODEL)) {
            val target = File(modelDir, name)
            if (!target.exists()) context.assets.open("diarization/$name").use { it.copyTo(target.outputStream()) }
        }
        return true
    }

    /**
     * @param samples 16 kHz mono float örnekler.
     * @param expectedSpeakers bilinmiyorsa -1 (eşikle otomatik kümeleme).
     */
    fun diarize(samples: FloatArray, expectedSpeakers: Int = -1): List<SpeakerTurn> {
        if (!ensureModels()) return emptyList()
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = File(modelDir, SEGMENTATION_MODEL).absolutePath),
                numThreads = 2,
            ),
            embedding = SpeakerEmbeddingExtractorConfig(model = File(modelDir, EMBEDDING_MODEL).absolutePath, numThreads = 2),
            clustering = FastClusteringConfig(numClusters = expectedSpeakers, threshold = CLUSTER_THRESHOLD),
            minDurationOn = 0.3f,
            minDurationOff = 0.5f,
        )
        val sd = OfflineSpeakerDiarization(config = config)
        return try {
            sd.process(samples)
                .map { SpeakerTurn(it.speaker, (it.start * 1000).toLong(), (it.end * 1000).toLong()) }
                .sortedBy { it.startMs }
                .mergeAdjacent()
        } catch (e: Exception) {
            Log.e(TAG, "diarization hatası", e); emptyList()
        } finally {
            sd.release()
        }
    }

    companion object {
        const val SEGMENTATION_MODEL = "sherpa-onnx-pyannote-segmentation-3-0.onnx"
        const val EMBEDDING_MODEL = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
        /** Daha küçük → daha çok konuşmacı. 0.5 iki-dört kişilik toplantılar için makul başlangıç. */
        const val CLUSTER_THRESHOLD = 0.5f
        private const val TAG = "SesNotDiarizer"
    }
}
