import Combine
import Foundation

/// Alınan ses → (konuşmacı ayrımı) → transkript → özet. Durumu adım adım `NoteStore`a yazar,
/// böylece uygulama kapanırsa `resumeUnfinished` kaldığı yerden devam eder.
///
/// Konuşmacı ayrımı varsayılan olarak toplantı şablonunda açıktır; diğerlerinde kullanıcı
/// "Konuşmacıları ayır" ile sonradan isteyebilir. iOS'ta tüm kayıt bir kez tanınır ve kelime zaman
/// damgaları konuşmacı turlarına dağıtılır (Android'de tur başına tanıma yapılır).
@MainActor
final class ProcessingPipeline: ObservableObject {
    @Published private(set) var activeNoteIDs: Set<UUID> = []
    @Published private(set) var lastEngine: Summarizer.Engine?

    private let store: NoteStore
    private let transcriber = Transcriber()
    private let diarizer = SpeakerDiarizer()
    private let summarizer = Summarizer()

    init(store: NoteStore) { self.store = store }

    var diarizationAvailable: Bool { diarizer.isAvailable }

    func process(noteID: UUID, forceDiarization: Bool? = nil) async {
        guard let note = store.note(id: noteID), !activeNoteIDs.contains(noteID) else { return }
        activeNoteIDs.insert(noteID)
        defer { activeNoteIDs.remove(noteID) }

        do {
            if note.transcript.isEmpty {
                store.update(id: noteID) { $0.status = .transcribing; $0.errorMessage = nil }
                let url = store.audioURL(for: note)
                let words = try await transcriber.transcribe(fileURL: url)
                guard !words.isEmpty else {
                    throw NSError(domain: "SesNot", code: 10,
                                  userInfo: [NSLocalizedDescriptionKey: "Kayıtta anlaşılır konuşma bulunamadı."])
                }

                let useDiarization = (forceDiarization ?? (note.template == .meeting)) && diarizer.isAvailable
                var turns: [SpeakerTurn] = []
                if useDiarization {
                    let samples = try await Task.detached(priority: .userInitiated) { try AudioDecoder.decodeToFloat(url: url) }.value
                    let d = diarizer
                    turns = await Task.detached(priority: .userInitiated) { d.diarize(samples: samples) }.value
                }
                let segments = SpeakerDiarizer.assign(words: words, to: turns)
                let speakerCount = (segments.map(\.speaker).max() ?? 0) + 1
                store.update(id: noteID) {
                    $0.segments = segments
                    $0.speakerCount = speakerCount
                    $0.transcript = speakerCount > 1 ? segments.labeledTranscript()
                                                     : segments.map(\.text).joined(separator: " ")
                }
            }

            store.update(id: noteID) { $0.status = .summarizing }
            let transcript = store.note(id: noteID)?.transcript ?? ""
            let (summary, engine) = await summarizer.summarize(transcript, template: note.template)
            lastEngine = engine
            store.update(id: noteID) { $0.summary = summary; $0.status = .ready }
        } catch {
            store.update(id: noteID) { $0.status = .failed; $0.errorMessage = error.localizedDescription }
        }
    }

    /// Yeniden özetle; `withDiarization` verilirse transkript de yeniden üretilir.
    func reprocess(noteID: UUID, withDiarization: Bool? = nil) async {
        store.update(id: noteID) {
            if withDiarization != nil { $0.transcript = ""; $0.segments = []; $0.status = .received }
            $0.summary = nil
            if withDiarization == nil { $0.status = .summarizing }
        }
        await process(noteID: noteID, forceDiarization: withDiarization)
    }

    func resumeUnfinished() async {
        for n in store.notes where n.status == .received || n.status == .transcribing || n.status == .summarizing {
            await process(noteID: n.id)
        }
    }
}
