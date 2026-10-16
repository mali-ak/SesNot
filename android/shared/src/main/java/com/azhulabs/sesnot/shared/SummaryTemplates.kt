package com.azhulabs.sesnot.shared

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Şablon başına LLM istemi ve LLM çıktısı ayrıştırma. iOS `SummaryTemplates.swift` ile aynı istemler. */
object SummaryTemplates {

    fun prompt(template: NoteTemplate, transcript: String): String {
        val common = """
            Aşağıdaki Türkçe konuşma transkriptini işle. YALNIZCA şu JSON'u döndür, başka hiçbir şey yazma:
            {"title": "...", "abstract": "...", "items": ["..."], "actions": ["..."], "dates": ["..."]}
        """.trimIndent()
        val specific = when (template) {
            NoteTemplate.MEETING -> """
                Bu bir toplantı kaydıdır. Satırlar "Kişi 1:", "Kişi 2:" gibi konuşmacı etiketleriyle başlayabilir;
                kim ne söylediyse ona göre değerlendir. title: 5-8 kelimelik toplantı başlığı. abstract: 2-3 cümlelik özet.
                items: alınan kararlar, kararı kim önerdiyse belirt ("Kişi 2: bütçe onaylandı").
                actions: yapılacak işler, sorumlu konuşmacıyla ("Kişi 1: raporu gönder").
                dates: konuşulan tarih ve saatler.
            """
            NoteTemplate.SHOPPING -> """
                Bu bir alışveriş listesi kaydıdır. title: "Alışveriş Listesi" veya mağaza adı. abstract: boş bırak.
                items: her ürün ayrı, miktar ve birimle ("2 kg domates", "1 paket süt"). Tekrarları birleştir.
                actions ve dates: boş dizi.
            """
            NoteTemplate.NOTE -> """
                Bu bir sesli nottur. title: 3-6 kelimelik başlık. abstract: notun 2-4 cümlelik özeti.
                items: en fazla 5 anahtar nokta. actions: varsa yapılacaklar. dates: geçen tarihler.
            """
        }.trimIndent()
        return "$common\n$specific\n\nTRANSKRİPT:\n$transcript"
    }

    /** LLM çıktısını ayrıştırır; bozuksa null döner ve yedek özetleyici devreye girer. */
    fun parse(llmOutput: String): Summary? {
        val start = llmOutput.indexOf('{'); val end = llmOutput.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(llmOutput.substring(start, end + 1)) as JsonObject }.getOrNull() ?: return null
        fun strings(key: String) = (obj[key] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?.filter { it.isNotEmpty() } ?: emptyList()
        fun string(key: String) = runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull() ?: ""
        val s = Summary(
            title = string("title"),
            abstract = string("abstract"),
            items = strings("items").map { SummaryItem(text = it) },
            actions = strings("actions").map { SummaryItem(text = it, owner = ownerPrefix(it)) },
            dates = strings("dates"),
        )
        return if (s.isEmpty) null else s
    }

    /** "Ahmet: raporu gönder" → "Ahmet" */
    fun ownerPrefix(text: String): String? {
        val colon = text.indexOf(':'); if (colon <= 0) return null
        val name = text.substring(0, colon).trim()
        return if (name.isNotEmpty() && name.split(' ').size <= 2) name else null
    }
}
