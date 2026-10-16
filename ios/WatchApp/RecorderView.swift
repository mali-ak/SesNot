import SwiftUI

struct RecorderView: View {
    @EnvironmentObject var recorder: AudioRecorder
    @EnvironmentObject var session: WatchSessionManager

    var body: some View {
        NavigationStack {
            VStack(spacing: 10) {
                if recorder.isRecording {
                    Text(timeString(recorder.elapsed))
                        .font(.system(size: 34, weight: .medium, design: .rounded))
                        .monospacedDigit()
                    Label(recorder.template.title, systemImage: recorder.template.symbol)
                        .font(.caption2).foregroundStyle(.secondary)
                } else {
                    Picker("Şablon", selection: $recorder.template) {
                        ForEach(NoteTemplate.allCases) { t in
                            Label(t.title, systemImage: t.symbol).tag(t)
                        }
                    }
                    .pickerStyle(.navigationLink)
                }

                Button(action: recorder.toggle) {
                    Image(systemName: recorder.isRecording ? "stop.fill" : "mic.fill")
                        .font(.title2)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(recorder.isRecording ? .red : .accentColor)

                statusLine
            }
            .padding(.horizontal, 4)
            .navigationTitle("SesNot")
            .onAppear {
                recorder.onPartFinished = { url, meta in session.send(fileURL: url, metadata: meta) }
            }
        }
    }

    @ViewBuilder private var statusLine: some View {
        if let err = recorder.lastError {
            Text(err).font(.caption2).foregroundStyle(.red).lineLimit(2)
        } else if session.pendingCount > 0 {
            Label("\(session.pendingCount) dosya bekliyor", systemImage: "arrow.up.circle")
                .font(.caption2).foregroundStyle(.secondary)
        } else if let s = session.lastStatus {
            Text(s).font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func timeString(_ t: TimeInterval) -> String {
        let s = Int(t)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                         : String(format: "%02d:%02d", s / 60, s % 60)
    }
}
