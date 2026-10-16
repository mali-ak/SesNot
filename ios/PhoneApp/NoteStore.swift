import Combine
import Foundation

/// Notları Documents/notes.json içinde tutar; ses dosyaları Documents/Recordings altında.
/// Küçük veri kümesi için Core Data gereksiz; JSON yeterli ve dışa aktarımı kolay.
@MainActor
final class NoteStore: ObservableObject {
    @Published private(set) var notes: [Note] = []

    private let fileURL: URL
    static let recordingsDirectory: URL = {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Recordings", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()

    init() {
        fileURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("notes.json")
        load()
    }

    func note(id: UUID) -> Note? { notes.first { $0.id == id } }

    func upsert(_ note: Note) {
        if let i = notes.firstIndex(where: { $0.id == note.id }) { notes[i] = note } else { notes.insert(note, at: 0) }
        notes.sort { $0.createdAt > $1.createdAt }
        save()
    }

    func update(id: UUID, _ change: (inout Note) -> Void) {
        guard let i = notes.firstIndex(where: { $0.id == id }) else { return }
        change(&notes[i])
        save()
    }

    func delete(_ note: Note) {
        notes.removeAll { $0.id == note.id }
        try? FileManager.default.removeItem(at: audioURL(for: note))
        save()
    }

    func audioURL(for note: Note) -> URL {
        Self.recordingsDirectory.appendingPathComponent(note.audioFileName)
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([Note].self, from: data) else { return }
        notes = decoded
    }

    private func save() {
        if let data = try? JSONEncoder().encode(notes) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}
