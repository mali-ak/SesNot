package com.azhulabs.sesnot.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Kullanıcının kayıt öncesi seçtiği özet şablonu. */
@Serializable
enum class NoteTemplate(val title: String) {
    MEETING("Toplantı"),
    SHOPPING("Alışveriş"),
    NOTE("Sesli Not");
}

/**
 * Saatten telefona giden dosyanın yanındaki üst veri. Data Layer kanalında dosyadan önce
 * tek satır JSON olarak gönderilir (bkz. TransferManager / WearListenerService).
 */
@Serializable
data class RecordingMetadata(
    val id: String = UUID.randomUUID().toString(),
    val template: NoteTemplate,
    val startedAtMillis: Long,
    val durationMillis: Long,
    /** Uzun kayıtlar parçalanırsa: parça sırası; toplam sayı yalnızca son parçada > 0. */
    val partIndex: Int = 0,
    val partCount: Int = 1,
) {
    fun toJson(): String = json.encodeToString(this)
    companion object {
        fun fromJson(s: String): RecordingMetadata? = runCatching { json.decodeFromString<RecordingMetadata>(s) }.getOrNull()
    }
}

/** Telefonda saklanan nihai not. */
@Serializable
data class Note(
    val id: String,
    val template: NoteTemplate,
    val createdAtMillis: Long,
    var durationMillis: Long,
    var status: Status,
    /** Düz transkript. Konuşmacı ayrımı yapıldıysa "Kişi 1: ..." satırlarından oluşur. */
    var transcript: String = "",
    /** Konuşmacı etiketli bölütler; ayrım yapılmadıysa tek bölüt, speaker = 0. */
    var segments: List<TranscriptSegment> = emptyList(),
    var speakerCount: Int = 1,
    var summary: Summary? = null,
    var errorMessage: String? = null,
    /** files/recordings altındaki dosya adı. */
    val audioFileName: String,
) {
    @Serializable
    enum class Status { RECEIVED, TRANSCRIBING, SUMMARIZING, READY, FAILED }
}

/** Konuşmacı etiketli transkript parçası. speaker 0 tabanlı; kullanıcıya "Kişi ${speaker+1}" gösterilir. */
@Serializable
data class TranscriptSegment(
    val speaker: Int,
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
) {
    val speakerLabel get() = "Kişi ${speaker + 1}"
}

/** Bölütleri "Kişi 1: ..." satırlarına çevirir; ardışık aynı konuşmacıları birleştirir. */
fun List<TranscriptSegment>.toLabeledTranscript(): String {
    val sb = StringBuilder()
    var last = -1
    for (s in this) {
        if (s.text.isBlank()) continue
        if (s.speaker != last) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(s.speakerLabel).append(": "); last = s.speaker }
        else sb.append(' ')
        sb.append(s.text.trim())
    }
    return sb.toString()
}

/** Şablondan bağımsız yapılandırılmış özet. */
@Serializable
data class Summary(
    var title: String,
    var abstract: String,
    var items: List<SummaryItem>,
    var actions: List<SummaryItem>,
    var dates: List<String>,
) {
    val isEmpty get() = abstract.isEmpty() && items.isEmpty() && actions.isEmpty()

    fun plainText(template: NoteTemplate): String = buildString {
        appendLine(title); appendLine()
        if (abstract.isNotEmpty()) { appendLine(abstract); appendLine() }
        if (items.isNotEmpty()) {
            appendLine(when (template) { NoteTemplate.MEETING -> "Kararlar:"; NoteTemplate.SHOPPING -> "Liste:"; NoteTemplate.NOTE -> "Anahtar noktalar:" })
            items.forEach { appendLine((if (template == NoteTemplate.SHOPPING) (if (it.checked) "☑ " else "☐ ") else "• ") + it.text) }
            appendLine()
        }
        if (actions.isNotEmpty()) { appendLine("Yapılacaklar:"); actions.forEach { appendLine("→ " + it.text) }; appendLine() }
        if (dates.isNotEmpty()) appendLine("Tarihler: " + dates.joinToString(", "))
    }
}

@Serializable
data class SummaryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var checked: Boolean = false,
    val owner: String? = null,
)

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
