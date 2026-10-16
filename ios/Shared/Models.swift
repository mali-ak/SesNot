import Foundation

/// Kullanıcının kayıt öncesi seçtiği özet şablonu.
enum NoteTemplate: String, Codable, CaseIterable, Identifiable {
    case meeting, shopping, note

    var id: String { rawValue }

    var title: String {
        switch self {
        case .meeting:  return "Toplantı"
        case .shopping: return "Alışveriş"
        case .note:     return "Sesli Not"
        }
    }

    var symbol: String {
        switch self {
        case .meeting:  return "person.3.fill"
        case .shopping: return "cart.fill"
        case .note:     return "mic.fill"
        }
    }
}

/// Saatten telefona giden dosyanın yanındaki üst veri.
/// `WCSession.transferFile(_:metadata:)` yalnızca property-list tipleri kabul eder,
/// bu yüzden sözlük dönüşümü vardır.
struct RecordingMetadata: Codable {
    let id: UUID
    let template: NoteTemplate
    let startedAt: Date
    let duration: TimeInterval
    /// Uzun kayıtlar parçalanırsa: parça sırası ve toplam parça sayısı.
    let partIndex: Int
    let partCount: Int

    var asDictionary: [String: Any] {
        [
            "id": id.uuidString,
            "template": template.rawValue,
            "startedAt": startedAt.timeIntervalSince1970,
            "duration": duration,
            "partIndex": partIndex,
            "partCount": partCount,
        ]
    }

    init(id: UUID = UUID(), template: NoteTemplate, startedAt: Date, duration: TimeInterval,
         partIndex: Int = 0, partCount: Int = 1) {
        self.id = id; self.template = template; self.startedAt = startedAt
        self.duration = duration; self.partIndex = partIndex; self.partCount = partCount
    }

    init?(dictionary: [String: Any]?) {
        guard let d = dictionary,
              let idString = d["id"] as? String, let id = UUID(uuidString: idString),
              let templateRaw = d["template"] as? String, let template = NoteTemplate(rawValue: templateRaw),
              let started = d["startedAt"] as? TimeInterval,
              let duration = d["duration"] as? TimeInterval else { return nil }
        self.id = id; self.template = template
        self.startedAt = Date(timeIntervalSince1970: started)
        self.duration = duration
        self.partIndex = d["partIndex"] as? Int ?? 0
        self.partCount = d["partCount"] as? Int ?? 1
    }
}

/// Telefonda saklanan nihai not.
struct Note: Codable, Identifiable, Equatable {
    enum Status: String, Codable { case received, transcribing, summarizing, ready, failed }

    let id: UUID
    let template: NoteTemplate
    let createdAt: Date
    var duration: TimeInterval
    var status: Status
    /// Düz transkript. Konuşmacı ayrımı yapıldıysa "Kişi 1: ..." satırlarından oluşur.
    var transcript: String = ""
    /// Konuşmacı etiketli bölütler; ayrım yapılmadıysa tek bölüt, speaker = 0.
    var segments: [TranscriptSegment] = []
    var speakerCount: Int = 1
    var summary: Summary? = nil
    var errorMessage: String? = nil
    /// Ses dosyasının Documents/Recordings altındaki dosya adı.
    var audioFileName: String
}

/// Konuşmacı etiketli transkript parçası. speaker 0 tabanlı; kullanıcıya "Kişi N" gösterilir.
struct TranscriptSegment: Codable, Equatable, Identifiable {
    var id = UUID()
    var speaker: Int
    var start: TimeInterval
    var end: TimeInterval
    var text: String

    var speakerLabel: String { "Kişi \(speaker + 1)" }
}

extension Array where Element == TranscriptSegment {
    /// Bölütleri "Kişi 1: ..." satırlarına çevirir; ardışık aynı konuşmacıları birleştirir.
    func labeledTranscript() -> String {
        var out = ""
        var last = -1
        for s in self where !s.text.trimmingCharacters(in: .whitespaces).isEmpty {
            if s.speaker != last {
                if !out.isEmpty { out += "\n" }
                out += s.speakerLabel + ": "
                last = s.speaker
            } else {
                out += " "
            }
            out += s.text.trimmingCharacters(in: .whitespaces)
        }
        return out
    }
}

/// Şablondan bağımsız, yapılandırılmış özet.
struct Summary: Codable, Equatable {
    var title: String
    /// Kısa düz metin özet (not şablonu için ana çıktı).
    var abstract: String
    /// Toplantı: kararlar. Alışveriş: ürünler. Not: anahtar noktalar.
    var items: [SummaryItem]
    /// Toplantı için aksiyon maddeleri; diğer şablonlarda boş.
    var actions: [SummaryItem]
    /// Metinde geçen tarih/saat ifadeleri.
    var dates: [String]

    var isEmpty: Bool { abstract.isEmpty && items.isEmpty && actions.isEmpty }

    /// Paylaşım için düz metin.
    func plainText(template: NoteTemplate) -> String {
        var lines: [String] = [title, ""]
        if !abstract.isEmpty { lines += [abstract, ""] }
        if !items.isEmpty {
            lines.append(template == .meeting ? "Kararlar:" : (template == .shopping ? "Liste:" : "Anahtar noktalar:"))
            lines += items.map { (template == .shopping ? ($0.checked ? "☑ " : "☐ ") : "• ") + $0.text }
            lines.append("")
        }
        if !actions.isEmpty {
            lines.append("Yapılacaklar:")
            lines += actions.map { "→ " + $0.text }
            lines.append("")
        }
        if !dates.isEmpty { lines.append("Tarihler: " + dates.joined(separator: ", ")) }
        return lines.joined(separator: "\n")
    }
}

struct SummaryItem: Codable, Equatable, Identifiable {
    var id = UUID()
    var text: String
    /// Alışveriş listesi için işaretleme.
    var checked: Bool = false
    /// Aksiyon maddesi için sorumlu kişi (bulunabildiyse).
    var owner: String? = nil
}
