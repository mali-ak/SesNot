package com.azhulabs.sesnot.mobile

import android.content.Context
import android.util.Log
import com.azhulabs.sesnot.shared.Note
import com.azhulabs.sesnot.shared.NoteTemplate
import com.azhulabs.sesnot.shared.TranscriptSegment
import com.azhulabs.sesnot.shared.toLabeledTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Ses → (konuşmacı ayrımı) → transkript → özet.
 *
 * Konuşmacı ayrımı varsayılan olarak toplantı şablonunda açıktır; alışveriş ve notta tek konuşmacı
 * varsayılır (hız için). Ayrım yapıldığında her konuşmacı turu ayrı tanınır ve "Kişi N: ..." etiketlenir.
 */
class ProcessingPipeline(private val context: Context, private val repo: NoteRepository) {

    private val transcriber = Transcriber(context)
    private val diarizer = SpeakerDiarizer(context)
    private val summarizer = Summarizer(context)
    private val lock = Mutex()

    private val _lastEngine = MutableStateFlow<Summarizer.Engine?>(null)
    val lastEngine: StateFlow<Summarizer.Engine?> = _lastEngine

    suspend fun process(noteId: String, forceDiarization: Boolean? = null) = lock.withLock {
        val note = repo.get(noteId) ?: return@withLock
        try {
            if (note.transcript.isEmpty()) {
                repo.update(noteId) { it.copy(status = Note.Status.TRANSCRIBING, errorMessage = null) }
                val pcm = withContext(Dispatchers.Default) { AudioDecoder.decodeToPcm16(repo.audioFile(note)) }
                val useDiarization = (forceDiarization ?: (note.template == NoteTemplate.MEETING)) && diarizer.isAvailable

                val segments: List<TranscriptSegment> = if (useDiarization) {
                    val turns = withContext(Dispatchers.Default) { diarizer.diarize(AudioDecoder.pcm16ToFloat(pcm)) }
                    if (turns.isEmpty()) listOf(single(pcm)) else turns.mapNotNull { t ->
                        val text = transcriber.transcribePcm(AudioDecoder.slice(pcm, t.startMs, t.endMs))
                        if (text.isBlank()) null else TranscriptSegment(t.speaker, t.startMs, t.endMs, text)
                    }
                } else listOf(single(pcm))

                if (segments.all { it.text.isBlank() })
                    throw Transcriber.TranscriberException("Kayıtta anlaşılır konuşma bulunamadı.")

                val speakerCount = (segments.maxOfOrNull { it.speaker } ?: 0) + 1
                repo.update(noteId) {
                    it.copy(segments = segments, speakerCount = speakerCount,
                            transcript = if (speakerCount > 1) segments.toLabeledTranscript() else segments.joinToString(" ") { s -> s.text })
                }
            }

            repo.update(noteId) { it.copy(status = Note.Status.SUMMARIZING) }
            val current = repo.get(noteId)!!
            val (summary, engine) = summarizer.summarize(current.transcript, current.template)
            _lastEngine.value = engine
            repo.update(noteId) { it.copy(summary = summary, status = Note.Status.READY) }
        } catch (e: Exception) {
            Log.e(TAG, "işleme hatası", e)
            repo.update(noteId) { it.copy(status = Note.Status.FAILED, errorMessage = e.message ?: "Bilinmeyen hata") }
        }
    }

    private suspend fun single(pcm: ByteArray): TranscriptSegment {
        val text = transcriber.transcribePcm(pcm)
        return TranscriptSegment(0, 0, pcm.size.toLong() * 1000 / (AudioDecoder.SAMPLE_RATE * 2), text)
    }

    /** Yeniden özetle; `withDiarization` verilirse transkript de yeniden üretilir. */
    suspend fun reprocess(noteId: String, withDiarization: Boolean? = null) {
        repo.update(noteId) {
            if (withDiarization != null) it.copy(transcript = "", segments = emptyList(), summary = null, status = Note.Status.RECEIVED)
            else it.copy(summary = null, status = Note.Status.SUMMARIZING)
        }
        process(noteId, withDiarization)
    }

    suspend fun resumeUnfinished() {
        repo.notes.value.filter { it.status != Note.Status.READY && it.status != Note.Status.FAILED }
            .forEach { process(it.id) }
    }

    companion object { private const val TAG = "SesNotPipeline" }
}
