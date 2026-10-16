import SwiftUI
import AVFoundation

struct NoteDetailView: View {
    let noteID: UUID
    @EnvironmentObject var store: NoteStore
    @EnvironmentObject var pipeline: ProcessingPipeline
    @State private var showTranscript = false
    @State private var player: AVAudioPlayer?

    var note: Note? { store.note(id: noteID) }
    static let speakerColors: [Color] = [.blue, .orange, .green, .purple, .pink]

    var body: some View {
        if let note {
            List {
                statusSection(note)
                if let s = note.summary { summarySections(s, note: note) }
                Section {
                    DisclosureGroup(note.speakerCount > 1 ? "Transkript (\(note.speakerCount) konuşmacı)" : "Ham transkript",
                                    isExpanded: $showTranscript) {
                        if note.speakerCount > 1 {
                            ForEach(note.segments) { seg in
                                HStack(alignment: .top, spacing: 8) {
                                    Text(seg.speakerLabel).font(.caption.bold())
                                        .foregroundStyle(Self.speakerColors[seg.speaker % Self.speakerColors.count])
                                        .frame(width: 52, alignment: .leading)
                                    Text(seg.text).font(.callout).textSelection(.enabled)
                                }
                            }
                        } else {
                            Text(note.transcript.isEmpty ? "—" : note.transcript).font(.callout).textSelection(.enabled)
                        }
                    }
                    if note.status == .ready, note.template != .meeting, pipeline.diarizationAvailable {
                        Button("Konuşmacıları ayır", systemImage: "person.2") {
                            Task { await pipeline.reprocess(noteID: noteID, withDiarization: true) }
                        }
                    }
                    Button { togglePlayback(note) } label: {
                        Label(player?.isPlaying == true ? "Durdur" : "Kaydı dinle (\(Int(note.duration)) sn)",
                              systemImage: player?.isPlaying == true ? "stop.fill" : "play.fill")
                    }
                }
            }
            .navigationTitle(note.summary?.title.nilIfEmpty ?? note.template.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if let s = note.summary {
                    ShareLink(item: s.plainText(template: note.template) + "\n\n— SesNot")
                }
                Menu {
                    Button("Yeniden özetle", systemImage: "arrow.clockwise") {
                        Task { await pipeline.reprocess(noteID: noteID) }
                    }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        } else {
            ContentUnavailableView("Not bulunamadı", systemImage: "questionmark")
        }
    }

    @ViewBuilder private func statusSection(_ note: Note) -> some View {
        switch note.status {
        case .ready: EmptyView()
        case .failed:
            Section {
                Label(note.errorMessage ?? "Bilinmeyen hata", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
                Button("Tekrar dene") { Task { await pipeline.process(noteID: noteID) } }
            }
        case .received: Section { Label("Saatten alındı, bekliyor…", systemImage: "clock") }
        case .transcribing: Section { HStack { ProgressView(); Text("Metne çevriliyor…") } }
        case .summarizing: Section { HStack { ProgressView(); Text("Özetleniyor…") } }
        }
    }

    @ViewBuilder private func summarySections(_ s: Summary, note: Note) -> some View {
        if !s.abstract.isEmpty {
            Section("Özet") { Text(s.abstract).textSelection(.enabled) }
        }
        if !s.items.isEmpty {
            Section(note.template == .meeting ? "Kararlar" : note.template == .shopping ? "Liste" : "Anahtar noktalar") {
                ForEach(s.items) { item in
                    if note.template == .shopping {
                        Button {
                            store.update(id: noteID) { n in
                                if let i = n.summary?.items.firstIndex(where: { $0.id == item.id }) {
                                    n.summary?.items[i].checked.toggle()
                                }
                            }
                        } label: {
                            Label(item.text, systemImage: item.checked ? "checkmark.circle.fill" : "circle")
                                .strikethrough(item.checked).foregroundStyle(item.checked ? .secondary : .primary)
                        }
                    } else {
                        Text(item.text)
                    }
                }
            }
        }
        if !s.actions.isEmpty {
            Section("Yapılacaklar") {
                ForEach(s.actions) { a in
                    HStack(alignment: .top) {
                        Image(systemName: "arrow.turn.down.right").foregroundStyle(.tint)
                        VStack(alignment: .leading) {
                            Text(a.text)
                            if let o = a.owner { Text(o).font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
            }
        }
        if !s.dates.isEmpty {
            Section("Tarihler") { Text(s.dates.joined(separator: " · ")) }
        }
    }

    private func togglePlayback(_ note: Note) {
        if let p = player, p.isPlaying { p.stop(); player = nil; return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        player = try? AVAudioPlayer(contentsOf: store.audioURL(for: note))
        player?.play()
    }
}
