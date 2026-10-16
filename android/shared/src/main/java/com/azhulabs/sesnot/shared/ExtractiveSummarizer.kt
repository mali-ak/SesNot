package com.azhulabs.sesnot.shared

/**
 * LLM bulunmayan cihazlar için kural tabanlı, tamamen çevrimdışı özetleyici.
 * iOS `ExtractiveSummarizer.swift` ile aynı algoritma: kelime sıklığına dayalı cümle puanlama,
 * karar/aksiyon ipucu kelimeleri, tarih regex'i, alışveriş için virgül/"ve" ayrıştırması.
 */
object ExtractiveSummarizer {

    private val stopWords = setOf(
        "ve", "bir", "bu", "şu", "da", "de", "ki", "mi", "mı", "mu", "mü", "ile", "için", "gibi",
        "çok", "daha", "en", "ama", "fakat", "ancak", "yani", "işte", "şey", "evet", "hayır", "tamam",
        "ben", "sen", "biz", "siz", "onlar", "bunu", "şunu", "onu", "ne", "nasıl", "neden", "ya", "hani",
        "var", "yok", "olan", "oldu", "olur", "olarak", "kadar", "sonra", "önce", "böyle", "şöyle",
    )
    private val decisionCues = listOf("karar", "kararlaştır", "anlaştık", "mutabık", "kabul", "onayla", "netleş")
    private val actionCues = listOf("yapacak", "yapılacak", "gönder", "hazırla", "takip", "sorumlu", "görev",
        "hallet", "ilet", "tamamla", "bitir", "kontrol", "yazacak", "edecek", "arayacak")
    private val shoppingFillers = listOf("almayı unutma", "almam lazım", "almalıyım", "listeye ekle",
        "alacağım", "alınacak", "lazım", "alalım")
    private val dateRegex = Regex(
        "(\\d{1,2}[./]\\d{1,2}([./]\\d{2,4})?)|(\\d{1,2}[:.]\\d{2})" +
        "|(\\b\\d{1,2}\\s+(ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık)\\b)" +
        "|(\\b(pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar|yarın|bugün|haftaya|gelecek hafta|ay sonu)\\b)",
        RegexOption.IGNORE_CASE)

    fun summarize(transcript: String, template: NoteTemplate): Summary {
        val sentences = splitSentences(transcript)
        return when (template) {
            NoteTemplate.SHOPPING -> shopping(sentences)
            NoteTemplate.MEETING -> meeting(sentences, transcript)
            NoteTemplate.NOTE -> note(sentences, transcript)
        }
    }

    private fun shopping(sentences: List<String>): Summary {
        val seen = HashSet<String>()
        val items = ArrayList<SummaryItem>()
        for (s in sentences) {
            val normalized = s.replace(" ve ", ", ").replace(" bir de ", ", ").replace(" ayrıca ", ", ")
            for (raw in normalized.split(',', ';')) {
                var p = raw.trim().trim { !it.isLetterOrDigit() }
                for (f in shoppingFillers) p = p.replace(f, "", ignoreCase = true)
                p = p.trim()
                val key = p.lowercase()
                if (p.length >= 2 && seen.add(key)) {
                    items += SummaryItem(text = p.replaceFirstChar { it.uppercase() })
                }
            }
        }
        return Summary("Alışveriş Listesi", "", items, emptyList(), emptyList())
    }

    private fun meeting(sentences: List<String>, transcript: String): Summary {
        val scored = score(sentences)
        val decisions = sentences.filter { s -> decisionCues.any { s.lowercase().contains(it) } }
        val actions = sentences.filter { s -> actionCues.any { s.lowercase().contains(it) } && s !in decisions }
        return Summary(
            title = title(scored, "Toplantı Notu"),
            abstract = abstract(scored, sentences, 3),
            items = decisions.take(8).map { SummaryItem(text = it) },
            actions = actions.take(10).map { SummaryItem(text = it, owner = guessOwner(it)) },
            dates = dates(transcript),
        )
    }

    private fun note(sentences: List<String>, transcript: String): Summary {
        val scored = score(sentences)
        val count = (sentences.size / 4).coerceIn(2, 4)
        val actions = sentences.filter { s -> actionCues.any { s.lowercase().contains(it) } }
        return Summary(
            title = title(scored, "Sesli Not"),
            abstract = abstract(scored, sentences, count),
            items = scored.drop(count).take(3).map { SummaryItem(text = it.sentence) },
            actions = actions.take(5).map { SummaryItem(text = it) },
            dates = dates(transcript),
        )
    }

    // ---- Yardımcılar ----

    fun splitSentences(text: String): List<String> {
        val out = ArrayList<String>()
        for (chunk in text.split('.', '!', '?', '\n')) {
            val c = chunk.trim()
            if (c.isEmpty()) continue
            if (c.split(' ').size > 25) out += c.split(" sonra ").map { it.trim() }.filter { it.isNotEmpty() }
            else out += c
        }
        return out
    }

    data class Scored(val sentence: String, val score: Double)

    fun score(sentences: List<String>): List<Scored> {
        val tokenized = sentences.map { tokens(it) }
        val freq = HashMap<String, Int>()
        tokenized.forEach { t -> t.forEach { w -> freq[w] = (freq[w] ?: 0) + 1 } }
        return sentences.zip(tokenized).map { (s, t) ->
            if (t.isEmpty()) Scored(s, 0.0)
            else {
                val sum = t.sumOf { (freq[it] ?: 0).toDouble() }
                val lengthFactor = if (t.size < 4) 0.5 else 1.0
                Scored(s, sum / t.size * lengthFactor)
            }
        }.sortedByDescending { it.score }
    }

    private fun abstract(scored: List<Scored>, original: List<String>, count: Int): String {
        val chosen = scored.take(count).map { it.sentence }.toSet()
        return original.filter { it in chosen }.joinToString(". ")
    }

    private fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in stopWords }

    private fun title(scored: List<Scored>, fallback: String): String {
        val best = scored.firstOrNull()?.sentence ?: return fallback
        return best.split(' ').take(7).joinToString(" ").ifEmpty { fallback }
    }

    private fun dates(text: String): List<String> {
        val seen = HashSet<String>()
        return dateRegex.findAll(text).map { it.value }.filter { seen.add(it.lowercase()) }.toList()
    }

    private fun guessOwner(sentence: String): String? =
        sentence.split(' ').take(3).firstOrNull { w ->
            w.length > 2 && w.first().isUpperCase() && w.lowercase() !in stopWords
        }
}
