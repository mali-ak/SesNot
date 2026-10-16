package com.azhulabs.sesnot.mobile

/** Konuşmacı ayrımı sonucu: [startMs, endMs) aralığında `speaker` konuşuyor. */
data class SpeakerTurn(val speaker: Int, val startMs: Long, val endMs: Long)

/** Aynı konuşmacının 1 sn'den kısa aralıklı ardışık turlarını birleştir: daha az, daha uzun tanıma çağrısı. */
fun List<SpeakerTurn>.mergeAdjacent(): List<SpeakerTurn> {
    val out = ArrayList<SpeakerTurn>()
    for (t in this) {
        val last = out.lastOrNull()
        if (last != null && last.speaker == t.speaker && t.startMs - last.endMs < 1000)
            out[out.size - 1] = last.copy(endMs = t.endMs)
        else out += t
    }
    return out
}
