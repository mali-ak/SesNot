package com.azhulabs.sesnot.mobile

import android.content.Context
import android.util.Log
import com.azhulabs.sesnot.shared.ExtractiveSummarizer
import com.azhulabs.sesnot.shared.NoteTemplate
import com.azhulabs.sesnot.shared.Summary
import com.azhulabs.sesnot.shared.SummaryTemplates
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.collect

/**
 * Önce Gemini Nano (ML Kit GenAI Prompt API, AICore) dener; cihaz desteklemiyorsa veya çıktı bozuksa
 * `ExtractiveSummarizer`a düşer. Her iki yol da tamamen cihaz üstündedir.
 */
class Summarizer(private val context: Context) {

    enum class Engine { GEMINI_NANO, EXTRACTIVE }

    suspend fun summarize(transcript: String, template: NoteTemplate): Pair<Summary, Engine> {
        geminiNano(transcript, template)?.let { return it to Engine.GEMINI_NANO }
        return ExtractiveSummarizer.summarize(transcript, template) to Engine.EXTRACTIVE
    }

    private suspend fun geminiNano(transcript: String, template: NoteTemplate): Summary? {
        return try {
            val client = Generation.getClient()
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> {}
                FeatureStatus.DOWNLOADABLE -> client.download().collect { }   // ilk kullanımda model iner (Wi-Fi)
                else -> return null
            }
            val chunks = chunk(transcript, 6_000)
            var merged: Summary? = null
            for (c in chunks) {
                val result = client.generateContent(SummaryTemplates.prompt(template, c))
                val part = SummaryTemplates.parse(result.candidates.firstOrNull()?.text ?: "") ?: continue
                merged = merged?.let {
                    Summary(
                        title = it.title.ifEmpty { part.title },
                        abstract = listOf(it.abstract, part.abstract).filter(String::isNotEmpty).joinToString(" "),
                        items = it.items + part.items, actions = it.actions + part.actions, dates = it.dates + part.dates,
                    )
                } ?: part
            }
            merged?.takeIf { !it.isEmpty }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano kullanılamadı, yedek özetleyiciye geçiliyor: ${e.message}")
            null
        }
    }

    private fun chunk(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = ArrayList<String>(); val cur = StringBuilder()
        for (line in text.split('\n')) {
            if (cur.length + line.length > maxChars && cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
            cur.append(line).append('\n')
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    companion object { private const val TAG = "SesNotSummarizer" }
}
