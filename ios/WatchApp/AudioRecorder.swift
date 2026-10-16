import AVFoundation
import Combine
import WatchKit

/// Saatte ses kaydı. AAC/m4a, 16 kHz mono: konuşma için yeterli ve Bluetooth aktarımı için küçük.
/// Uzun kayıtlar `maxPartDuration` ile parçalanır; her parça biter bitmez aktarıma gönderilir.
@MainActor
final class AudioRecorder: NSObject, ObservableObject {
    @Published private(set) var isRecording = false
    @Published private(set) var elapsed: TimeInterval = 0
    @Published var template: NoteTemplate = .note
    @Published var lastError: String?

    /// 10 dakikalık parçalar: pil ve aktarım güvenliği için.
    let maxPartDuration: TimeInterval = 10 * 60

    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var sessionID = UUID()
    private var sessionStart = Date()
    private var partIndex = 0
    private var partStart = Date()
    private var extendedSession: WKExtendedRuntimeSession?

    var onPartFinished: ((URL, RecordingMetadata) -> Void)?

    static let settings: [String: Any] = [
        AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
        AVSampleRateKey: 16_000,
        AVNumberOfChannelsKey: 1,
        AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
        AVEncoderBitRateKey: 32_000,
    ]

    func toggle() {
        isRecording ? stop() : start()
    }

    func start() {
        let audio = AVAudioSession.sharedInstance()
        audio.requestRecordPermission { [weak self] granted in
            Task { @MainActor in
                guard let self else { return }
                guard granted else { self.lastError = "Mikrofon izni verilmedi."; return }
                do {
                    try audio.setCategory(.record, mode: .default)
                    try audio.setActive(true)
                    self.sessionID = UUID()
                    self.sessionStart = Date()
                    self.partIndex = 0
                    self.beginExtendedSession()
                    try self.startPart()
                    self.isRecording = true
                    WKInterfaceDevice.current().play(.start)
                } catch {
                    self.lastError = error.localizedDescription
                }
            }
        }
    }

    func stop() {
        finishPart(isLast: true)
        timer?.invalidate(); timer = nil
        isRecording = false
        elapsed = 0
        extendedSession?.invalidate(); extendedSession = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        WKInterfaceDevice.current().play(.stop)
    }

    // MARK: - Parçalar

    private func startPart() throws {
        let url = Self.recordingsDirectory
            .appendingPathComponent("\(sessionID.uuidString)-\(partIndex).m4a")
        let r = try AVAudioRecorder(url: url, settings: Self.settings)
        r.delegate = self
        guard r.record() else { throw NSError(domain: "SesNot", code: 1,
                                              userInfo: [NSLocalizedDescriptionKey: "Kayıt başlatılamadı."]) }
        recorder = r
        partStart = Date()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    private func tick() {
        elapsed = Date().timeIntervalSince(sessionStart)
        if Date().timeIntervalSince(partStart) >= maxPartDuration {
            // Parçayı kapat, yeni parçaya devam et; partCount henüz bilinmediği için 0 gönderilir,
            // son parça gerçek sayıyı taşır.
            finishPart(isLast: false)
            partIndex += 1
            do { try startPart() } catch { lastError = error.localizedDescription; stop() }
        }
    }

    private func finishPart(isLast: Bool) {
        guard let r = recorder else { return }
        r.stop()
        let duration = Date().timeIntervalSince(partStart)
        let meta = RecordingMetadata(id: sessionID, template: template, startedAt: sessionStart,
                                     duration: duration, partIndex: partIndex,
                                     partCount: isLast ? partIndex + 1 : 0)
        recorder = nil
        onPartFinished?(r.url, meta)
    }

    /// Ekran kapansa da kaydın devam etmesi için.
    private func beginExtendedSession() {
        let s = WKExtendedRuntimeSession()
        s.start()
        extendedSession = s
    }

    static var recordingsDirectory: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Recordings", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}

extension AudioRecorder: AVAudioRecorderDelegate {
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        Task { @MainActor in
            self.lastError = error?.localizedDescription ?? "Kodlama hatası"
            self.stop()
        }
    }
}
