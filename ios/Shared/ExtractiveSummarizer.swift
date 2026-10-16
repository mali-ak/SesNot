import Foundation

/// LLM bulunmayan cihazlar için kural tabanlı, tamamen çevrimdışı özetleyici.
/// Android tarafındaki `ExtractiveSummarizer.kt` ile aynı algoritmayı uygular.
struct ExtractiveSummarizer {

    static let stopWords: Set<String> = [
        "ve", "bir", "bu", "şu", "da", "de", "ki", "mi", "mı", "mu", "mü", "ile", "için", "gibi",
        "çok", "daha", "en", "ama", "fakat", "ancak", "yani", "işte", "şey", "evet", "hayır", "tamam",
        "ben", "sen", "biz", "siz", "onlar", "bunu", "şunu", "onu", "ne", "nasıl", "neden", "ya", "hani",
        "var", "yok", "olan", "oldu", "olur", "olarak", "kadar", "sonra", "önce", "böyle", "şöyle",
    ]

    static let decisionCues = ["karar", "kararlaştır", "anlaştık", "mutabık", "kabul", "onayla", "netleş"]
    static let actionCues   = ["yapacak", "yapılacak", "gönder", "hazırla", "takip", "sorumlu", "görev",
                               "hallet", "ilet", "tamamla", "bitir", "kontrol", "yazacak", "edecek", "arayacak"]
    static let shoppingFillers = ["almayı unutma", "almam lazım", "almalıyım", "listeye ekle",
                                  "alacağım", "alınacak", "lazım", "alalım"]

    static let dateRegex = try! NSRegularExpression(
        pattern: "(\\d{1,2}[./]\\d{1,2}([./]\\d{2,4})?)|(\\d{1,2}[:.]\\d{2})"
               + "|(\\b\\d{1,2}\\s+(ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık)\\b)"
               + "|(\\b(pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar|yarın|bugün|haftaya|gelecek hafta|ay sonu)\\b)",
        options: [.caseInsensitive])

    func summarize(_ transcript: String, template: NoteTemplate) -> Summary {
        let sentences = Self.splitSentences(transcript)
        switch template {
        case .shopping: return shopping(sentences)
        case .meeting:  return meeting(sentences, transcript: transcript)
        case .note:     return note(sentences, transcript: transcript)
        }
    }

    // MARK: - Şablonlar

    private func shopping(_ sentences: [String]) -> Summary {
        var seen = Set<String>()
        var items: [SummaryItem] = []
        for s in sentences {
            // "2 kilo domates, bir paket süt ve ekmek" → parçalara ayır
            let normalized = s.replacingOccurrences(of: " ve ", with: ", ")
                              .replacingOccurrences(of: " bir de ", with: ", ")
                              .replacingOccurrences(of: " ayrıca ", with: ", ")
            for raw in normalized.split(whereSeparator: { ",;".contains($0) }) {
                var p = raw.trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
                for filler in Self.shoppingFillers {
                    p = p.replacingOccurrences(of: filler, with: "", options: .caseInsensitive)
                }
                p = p.trimmingCharacters(in: .whitespaces)
                let key = p.lowercased()
                if p.count >= 2, !seen.contains(key) {
                    seen.insert(key)
                    items.append(SummaryItem(text: p.prefix(1).uppercased() + p.dropFirst()))
                }
            }
        }
        return Summary(title: "Alışveriş Listesi", abstract: "", items: items, actions: [], dates: [])
    }

    private func meeting(_ sentences: [String], transcript: String) -> Summary {
        let scored = Self.score(sentences)
        let decisions = sentences.filter { s in Self.decisionCues.contains { s.lowercased().contains($0) } }
        let actions = sentences.filter { s in
            Self.actionCues.contains { s.lowercased().contains($0) } && !decisions.contains(s)
        }
        return Summary(
            title: Self.title(from: scored, fallback: "Toplantı Notu"),
            abstract: Self.abstract(from: scored, original: sentences, count: 3),
            items: decisions.prefix(8).map { SummaryItem(text: $0) },
            actions: actions.prefix(10).map { SummaryItem(text: $0, owner: Self.guessOwner($0)) },
            dates: Self.dates(in: transcript)
        )
    }

    private func note(_ sentences: [String], transcript: String) -> Summary {
        let scored = Self.score(sentences)
        let count = min(max(2, sentences.count / 4), 4)
        let actions = sentences.filter { s in Self.actionCues.contains { s.lowercased().contains($0) } }
        return Summary(
            title: Self.title(from: scored, fallback: "Sesli Not"),
            abstract: Self.abstract(from: scored, original: sentences, count: count),
            items: scored.dropFirst(count).prefix(3).map { SummaryItem(text: $0.sentence) },
            actions: actions.prefix(5).map { SummaryItem(text: $0) },
            dates: Self.dates(in: transcript)
        )
    }

    // MARK: - Yardımcılar

    static func splitSentences(_ text: String) -> [String] {
        // Konuşma tanıma genelde noktalama koymaz; uzun parçaları bağlaçlardan da böl.
        var out: [String] = []
        for chunk in text.components(separatedBy: CharacterSet(charactersIn: ".!?\n")) {
            let c = chunk.trimmingCharacters(in: .whitespaces)
            guard !c.isEmpty else { continue }
            if c.split(separator: " ").count > 25 {
                out += c.components(separatedBy: " sonra ")
                        .map { $0.trimmingCharacters(in: .whitespaces) }
                        .filter { !$0.isEmpty }
            } else {
                out.append(c)
            }
        }
        return out
    }

    struct Scored { let sentence: String; let score: Double }

    /// Kelime sıklığına dayalı cümle puanlama (TextRank'in basit hali).
    static func score(_ sentences: [String]) -> [Scored] {
        var freq: [String: Int] = [:]
        let tokenized = sentences.map { tokens($0) }
        for t in tokenized { for w in t { freq[w, default: 0] += 1 } }
        return zip(sentences, tokenized).map { s, t in
            guard !t.isEmpty else { return Scored(sentence: s, score: 0) }
            let sum = t.reduce(0.0) { $0 + Double(freq[$1] ?? 0) }
            let lengthFactor = t.count < 4 ? 0.5 : 1.0   // çok kısa cümleleri cezalandır
            return Scored(sentence: s, score: sum / Double(t.count) * lengthFactor)
        }.sorted { $0.score > $1.score }
    }

    /// En yüksek puanlı `count` cümleyi orijinal sırayla birleştirir.
    static func abstract(from scored: [Scored], original: [String], count: Int) -> String {
        let chosen = Set(scored.prefix(count).map(\.sentence))
        return original.filter { chosen.contains($0) }.joined(separator: ". ")
    }

    static func tokens(_ s: String) -> [String] {
        s.lowercased()
         .components(separatedBy: CharacterSet.alphanumerics.inverted)
         .filter { $0.count > 2 && !stopWords.contains($0) }
    }

    static func title(from scored: [Scored], fallback: String) -> String {
        guard let best = scored.first?.sentence else { return fallback }
        let words = best.split(separator: " ").prefix(7).joined(separator: " ")
        return words.isEmpty ? fallback : words
    }

    static func dates(in text: String) -> [String] {
        let range = NSRange(text.startIndex..., in: text)
        var seen = Set<String>()
        return dateRegex.matches(in: text, range: range).compactMap {
            guard let r = Range($0.range, in: text) else { return nil }
            let m = String(text[r])
            return seen.insert(m.lowercased()).inserted ? m : nil
        }
    }

    /// "Ahmet raporu gönderecek" → "Ahmet" (büyük harfle başlayan ilk kelime).
    static func guessOwner(_ sentence: String) -> String? {
        for w in sentence.split(separator: " ").prefix(3) {
            let s = String(w)
            if let f = s.first, f.isUppercase, s.count > 2, !stopWords.contains(s.lowercased()) { return s }
        }
        return nil
    }
}
