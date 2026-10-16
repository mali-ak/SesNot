import SwiftUI

/// Telefondan kayıt ekranı: şablon seç, istersen Bluetooth saat mikrofonunu kullan, kaydet.
struct RecordView: View {
    @EnvironmentObject var recorder: PhoneRecorder
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 28) {
                if recorder.isRecording {
                    Text(timeString(recorder.elapsed))
                        .font(.system(size: 56, weight: .light, design: .rounded)).monospacedDigit()
                    Text(recorder.template.title + " · " + (recorder.bluetoothInputName.map { recorder.useBluetoothMic ? $0 : "Telefon mikrofonu" } ?? "Telefon mikrofonu"))
                        .foregroundStyle(.secondary)
                } else {
                    Picker("Şablon", selection: $recorder.template) {
                        ForEach(NoteTemplate.allCases) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    Toggle(isOn: $recorder.useBluetoothMic) {
                        VStack(alignment: .leading) {
                            Text("Saat mikrofonu (Bluetooth)")
                            Text(recorder.bluetoothInputName ?? "Bağlı Bluetooth arama cihazı yok")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .disabled(recorder.bluetoothInputName == nil)
                }

                Button(action: recorder.toggle) {
                    Image(systemName: recorder.isRecording ? "stop.fill" : "mic.fill")
                        .font(.system(size: 36))
                        .frame(width: 96, height: 96)
                        .background(recorder.isRecording ? Color.red : Color.accentColor, in: Circle())
                        .foregroundStyle(.white)
                }

                if let err = recorder.lastError {
                    Text(err).font(.footnote).foregroundStyle(.orange).multilineTextAlignment(.center)
                }

                Text("İpucu: Kilit ekranı widget'ı veya \"Hey Siri, SesNot kaydı başlat\" ile telefona dokunmadan başlatabilirsiniz.")
                    .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            .padding(24)
            .navigationTitle("Telefondan kaydet")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { dismiss() }.disabled(recorder.isRecording)
                }
            }
            .onAppear { recorder.refreshInputs() }
        }
    }

    private func timeString(_ t: TimeInterval) -> String {
        let s = Int(t)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                         : String(format: "%02d:%02d", s / 60, s % 60)
    }
}
