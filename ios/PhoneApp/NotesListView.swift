import SwiftUI

struct NotesListView: View {
    @EnvironmentObject var store: NoteStore
    @EnvironmentObject var pipeline: ProcessingPipeline
    @State private var search = ""
    @State private var filter: NoteTemplate?

    var filtered: [Note] {
        store.notes.filter { n in
            (filter == nil || n.template == filter) &&
            (search.isEmpty || n.transcript.localizedCaseInsensitiveContains(search)
             || (n.summary?.title.localizedCaseInsensitiveContains(search) ?? false))
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if store.notes.isEmpty {
                    ContentUnavailableView("Henüz not yok",
                                           systemImage: "applewatch.radiowaves.left.and.right",
                                           description: Text("Saatinizden bir kayıt başlatın; bittiğinde burada görünür."))
                } else {
                    List {
                        ForEach(filtered) { note in
                            NavigationLink(value: note.id) { NoteRow(note: note) }
                        }
                        .onDelete { idx in idx.map { filtered[$0] }.forEach(store.delete) }
                    }
                    .searchable(text: $search, prompt: "Notlarda ara")
                }
            }
            .navigationTitle("SesNot")
            .navigationDestination(for: UUID.self) { id in NoteDetailView(noteID: id) }
            .toolbar {
                Menu {
                    Picker("Filtre", selection: $filter) {
                        Text("Tümü").tag(NoteTemplate?.none)
                        ForEach(NoteTemplate.allCases) { Text($0.title).tag(NoteTemplate?.some($0)) }
                    }
                } label: { Image(systemName: "line.3.horizontal.decrease.circle") }
            }
        }
    }
}

struct NoteRow: View {
    let note: Note
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: note.template.symbol)
                .frame(width: 28).foregroundStyle(.tint)
            VStack(alignment: .leading, spacing: 3) {
                Text(note.summary?.title.nilIfEmpty ?? note.template.title).font(.headline).lineLimit(1)
                HStack(spacing: 6) {
                    Text(note.createdAt, format: .dateTime.day().month().hour().minute())
                    if note.speakerCount > 1 {
                        Text("· \(note.speakerCount) konuşmacı")
                    }
                }
                .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            StatusBadge(status: note.status)
        }
    }
}

struct StatusBadge: View {
    let status: Note.Status
    var body: some View {
        switch status {
        case .ready: EmptyView()
        case .failed: Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
        default: ProgressView().controlSize(.small)
        }
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
