package com.azhulabs.sesnot.mobile

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cihaz üstü konuşma tanıma. Android 13+ `EXTRA_AUDIO_SOURCE` ile dosyadan (PCM boru) tanıma yapar;
 * `createOnDeviceSpeechRecognizer` sayesinde ses cihazdan çıkmaz.
 * Türkçe çevrimdışı paket: Ayarlar > Sistem > Diller > Konuşma tanıma > Türkçe indirilmeli.
 */
class Transcriber(private val context: Context) {

    class TranscriberException(msg: String) : Exception(msg)

    /** PCM16 16 kHz mono baytlarını metne çevirir. Ana iş parçacığında SpeechRecognizer gerektirir. */
    suspend fun transcribePcm(pcm: ByteArray): String {
        if (Build.VERSION.SDK_INT < 33)
            throw TranscriberException("Dosyadan çevrimdışı tanıma için Android 13 gerekir.")
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
            throw TranscriberException("Cihaz üstü tanıma yok. Ayarlar > Diller > Konuşma tanıma'dan Türkçe'yi indirin.")
        if (pcm.size < AudioDecoder.SAMPLE_RATE * 2 / 2) return "" // 0,5 sn'den kısa: boş

        return withContext(Dispatchers.Main) {
            val pipe = ParcelFileDescriptor.createPipe()
            val reader = pipe[0]; val writer = pipe[1]
            // Yazma işini ayrı iş parçacığına ver: boru dolunca bloklanır.
            Thread {
                ParcelFileDescriptor.AutoCloseOutputStream(writer).use { it.write(pcm) }
            }.start()

            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                suspendCancellableCoroutine { cont ->
                    val parts = StringBuilder()
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onResults(results: Bundle) {
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                                if (parts.isNotEmpty()) parts.append(' '); parts.append(it)
                            }
                            if (cont.isActive) cont.resume(parts.toString())
                        }
                        override fun onSegmentResults(segmentResults: Bundle) {
                            segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                                if (parts.isNotEmpty()) parts.append(' '); parts.append(it)
                            }
                        }
                        override fun onEndOfSegmentedSession() { if (cont.isActive) cont.resume(parts.toString()) }
                        override fun onError(error: Int) {
                            if (!cont.isActive) return
                            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                                cont.resume(parts.toString())
                            else cont.resumeWithException(TranscriberException("Tanıma hatası ($error)"))
                        }
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, reader)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioDecoder.SAMPLE_RATE)
                        // Uzun kayıtlar için bölütlü oturum: tanıyıcı sessizlikte kesmesin.
                        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                        putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                    }
                    recognizer.startListening(intent)
                    cont.invokeOnCancellation { recognizer.cancel() }
                }
            } finally {
                recognizer.destroy()
                runCatching { reader.close() }
            }
        }
    }
}
