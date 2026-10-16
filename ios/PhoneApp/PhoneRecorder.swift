import AVFoundation
import Combine
import Foundation

/// Telefondan kayıt modu: saat uygulaması olmayan (ör. Huawei GT) kullanıcılar için.
/// Giriş olarak bağlı Bluetooth arama cihazının (saatin) mikrofonu seçilebilir (HFP).
/// Kayıt bitince not oluşturulur ve saatten gelen kayıtla aynı işleme hattına girer.
@MainActor
final class PhoneRecorder: NSObject, ObservableObject {
    @Published private(set) var isRecording = false
    @Published private(set) var elapsed: TimeInterval = 0
    @Published var template: NoteTemplate = .note
    @Published var useBluetoothMic = true
    @Published private(set) var bluetoothInputName: String?
    @Published var lastError: String?

    private let store: NoteStore
    private let pipeline: ProcessingPipeline
    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var noteID = UUID()
    private var startedAt = Date()

    init(store: NoteStore, pipeline: ProcessingPipeline) {
        self.store = store
        self.pipeline = pipeline
        super.init()
        refreshInputs()
        NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.refreshInputs() }
        }
    }

    /// Bağlı Bluetooth HFP girişini (saat/kulaklık) bulur.
    private var bluetoothInput: AVAudioSessionPortDescription? {
        AVAudioSession.sharedInstance().availableInputs?.first { $0.portType == .bluetoothHFP }
    }

    func refreshInputs() {
        let session = AVAudioSession.sharedInstance()
        // Bluetooth girişlerinin listelenmesi için oturum kategorisinde allowBluetooth gerekir.
        try? session.setCategory(.playAndRecord, mode: .default, options: [.allowBluetooth, .defaultToSpeaker])
        bluetoothInputName = bluetoothInput?.portName
    }

    func toggle() { isRecording ? stop() : start() }

    func start() {
        guard !isRecording else { return }
        lastError = nil
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            Task { @MainActor in
                guard let self else { return }
                guard granted else { self.lastError = "Mikrofon izni verilmedi."; return }
                do { try self.beginRecording() } catch { self.lastError = error.localizedDescription }
            }
        }
    }

    private func beginRecording() throws {
        let session = AVAudioSession.sharedInstance()
        let usingBt = useBluetoothMic && bluetoothInput != nil
        // .voiceChat modu HFP mikrofon yolunu açar; aksi halde telefon mikrofonu kullanılır.
        try session.setCategory(.playAndRecord, mode: usingBt ? .voiceChat : .default,
                                options: usingBt ? [.allowBluetooth] : [.defaultToSpeaker])
        if usingBt, let bt = bluetoothInput { try session.setPreferredInput(bt) }
        try session.setActive(true)
        if useBluetoothMic && !usingBt { lastError = "Bluetooth mikrofon bulunamadı, telefon mikrofonu kullanılıyor" }

        noteID = UUID()
        startedAt = Date()
        let url = NoteStore.recordingsDirectory.appendingPathComponent("\(noteID.uuidString).m4a")
        let r = try AVAudioRecorder(url: url, settings: [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
            AVEncoderBitRateKey: 32_000,
        ])
        r.delegate = self
        guard r.record() else { throw NSError(domain: "SesNot", code: 30, userInfo: [NSLocalizedDescriptionKey: "Kayıt başlatılamadı."]) }
        recorder = r
        isRecording = true
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.elapsed = Date().timeIntervalSince(self?.startedAt ?? Date()) }
        }
    }

    func stop() {
        guard let r = recorder else { return }
        timer?.invalidate(); timer = nil
        r.stop()
        recorder = nil
        isRecording = false
        let duration = Date().timeIntervalSince(startedAt)
        elapsed = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        guard duration > 1 else { try? FileManager.default.removeItem(at: r.url); return }
        let note = Note(id: noteID, template: template, createdAt: startedAt, duration: duration,
                        status: .received, audioFileName: r.url.lastPathComponent)
        store.upsert(note)
        Task { await pipeline.process(noteID: note.id) }
    }
}

extension PhoneRecorder: AVAudioRecorderDelegate {
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        Task { @MainActor in
            self.lastError = error?.localizedDescription ?? "Kodlama hatası"
            self.stop()
        }
    }
}
