package com.azhulabs.sesnot.mobile

import android.content.Context

/**
 * sherpa-onnx AAR'ı (`mobile/libs/sherpa-onnx.aar`) yokken derlenen boş uygulama.
 * Pipeline `isAvailable == false` görünce tek konuşmacı varsayar. Gerçek uygulama: `src/sherpa/`.
 */
class SpeakerDiarizer(@Suppress("UNUSED_PARAMETER") context: Context) {
    val isAvailable: Boolean get() = false
    fun diarize(samples: FloatArray, expectedSpeakers: Int = -1): List<SpeakerTurn> = emptyList()
}
