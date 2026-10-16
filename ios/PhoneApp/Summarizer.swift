import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Önce Apple'ın cihaz üstü dil modelini (iOS 26+, Apple Intelligence) dener; yoksa veya
/// çıktı bozuksa kural tabanlı `ExtractiveSummarizer`a düşer. İnternet hiçbir durumda kullanılmaz.
struct Summarizer {
    enum Engine: String { case foundationModels, extractive }

    func summarize(_ transcript: String, template: NoteTemplate) async -> (Summary, Engine) {
        if let s = await foundationModelsSummary(transcript, template: template) {
            return (s, .foundationModels)
        }
        return (ExtractiveSummarizer().summarize(transcript, template: template), .extractive)
    }

    private func foundationModelsSummary(_ transcript: String, template: NoteTemplate) async -> Summary? {
        #if canImport(FoundationModels)
        guard #available(iOS 26.0, *) else { return nil }
        guard case .available = SystemLanguageModel.default.availability else { return nil }
        do {
            let session = LanguageModelSession(instructions:
                "Sen Türkçe konuşma transkriptlerini özetleyen bir asistansın. Yalnızca istenen JSON'u üret.")
            // Model bağlam penceresi sınırlı; uzun transkriptleri parçala ve birleştir.
            let chunks = Self.chunk(transcript, maxChars: 6_000)
            if chunks.count == 1 {
                let r = try await session.respond(to: SummaryTemplates.prompt(for: template, transcript: transcript))
                return SummaryTemplates.parse(llmOutput: r.content)
            }
            var merged = Summary(title: "", abstract: "", items: [], actions: [], dates: [])
            for c in chunks {
                let r = try await session.respond(to: SummaryTemplates.prompt(for: template, transcript: c))
                guard let part = SummaryTemplates.parse(llmOutput: r.content) else { continue }
                if merged.title.isEmpty { merged.title = part.title }
                merged.abstract += (merged.abstract.isEmpty ? "" : " ") + part.abstract
                merged.items += part.items
                merged.actions += part.actions
                merged.dates += part.dates
            }
            return merged.isEmpty ? nil : merged
        } catch {
            print("SesNot: FoundationModels hatası, yedek özetleyiciye geçiliyor: \(error)")
            return nil
        }
        #else
        return nil
        #endif
    }

    static func chunk(_ text: String, maxChars: Int) -> [String] {
        guard text.count > maxChars else { return [text] }
        var out: [String] = []
        var current = ""
        for s in ExtractiveSummarizer.splitSentences(text) {
            if current.count + s.count > maxChars, !current.isEmpty { out.append(current); current = "" }
            current += s + ". "
        }
        if !current.isEmpty { out.append(current) }
        return out
    }
}
