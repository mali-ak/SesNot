import Combine
import WatchConnectivity

/// Saat → iPhone dosya aktarımı. `transferFile` arka planda kuyruğa alınır ve iPhone erişilebilir
/// olduğunda sistem tarafından gönderilir; biz yalnızca tamamlanan dosyaları siler ve
/// başarısız olanları bir sonraki açılışta yeniden deneriz.
@MainActor
final class WatchSessionManager: NSObject, ObservableObject {
    static let shared = WatchSessionManager()

    @Published private(set) var pendingCount = 0
    @Published private(set) var isReachable = false
    @Published private(set) var lastStatus: String?

    private let metaStore = PendingMetadataStore()

    func activate() {
        guard WCSession.isSupported() else { return }
        let s = WCSession.default
        s.delegate = self
        s.activate()
    }

    /// Yeni biten parçayı aktarıma ver.
    func send(fileURL: URL, metadata: RecordingMetadata) {
        metaStore.save(metadata, for: fileURL.lastPathComponent)
        WCSession.default.transferFile(fileURL, metadata: metadata.asDictionary)
        refreshPending()
    }

    /// Önceki oturumlardan kalan ve hâlâ kuyrukta olmayan dosyaları yeniden gönder.
    func flushQueue() {
        let queued = Set(WCSession.default.outstandingFileTransfers.map { $0.file.fileURL.lastPathComponent })
        let files = (try? FileManager.default.contentsOfDirectory(at: AudioRecorder.recordingsDirectory,
                                                                  includingPropertiesForKeys: nil)) ?? []
        for url in files where url.pathExtension == "m4a" && !queued.contains(url.lastPathComponent) {
            if let meta = metaStore.load(for: url.lastPathComponent) {
                WCSession.default.transferFile(url, metadata: meta.asDictionary)
            } else {
                try? FileManager.default.removeItem(at: url)   // üst verisi kayıp dosya işe yaramaz
            }
        }
        refreshPending()
    }

    private func refreshPending() {
        pendingCount = WCSession.default.outstandingFileTransfers.count
    }
}

extension WatchSessionManager: WCSessionDelegate {
    nonisolated func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        Task { @MainActor in
            self.isReachable = session.isReachable
            self.refreshPending()
        }
    }

    nonisolated func sessionReachabilityDidChange(_ session: WCSession) {
        Task { @MainActor in self.isReachable = session.isReachable }
    }

    nonisolated func session(_ session: WCSession, didFinish fileTransfer: WCSessionFileTransfer, error: Error?) {
        Task { @MainActor in
            let name = fileTransfer.file.fileURL.lastPathComponent
            if let error {
                self.lastStatus = "Aktarım hatası: \(error.localizedDescription)"
                // Dosya diskte kalır; flushQueue bir sonraki açılışta yeniden dener.
            } else {
                try? FileManager.default.removeItem(at: fileTransfer.file.fileURL)
                self.metaStore.remove(for: name)
                self.lastStatus = "Telefona gönderildi"
            }
            self.refreshPending()
        }
    }

    /// Telefon "hazır" bildirimi gönderirse kısa durum göster.
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in
            if let status = message["status"] as? String { self.lastStatus = status }
        }
    }
}

/// Aktarım bekleyen dosyaların üst verisini UserDefaults'ta tutar; uygulama kapanıp açılsa da kaybolmaz.
struct PendingMetadataStore {
    private let key = "pendingMetadata"
    private var all: [String: Data] {
        get { UserDefaults.standard.dictionary(forKey: key) as? [String: Data] ?? [:] }
        nonmutating set { UserDefaults.standard.set(newValue, forKey: key) }
    }
    func save(_ meta: RecordingMetadata, for file: String) {
        var d = all; d[file] = try? JSONEncoder().encode(meta); all = d
    }
    func load(for file: String) -> RecordingMetadata? {
        all[file].flatMap { try? JSONDecoder().decode(RecordingMetadata.self, from: $0) }
    }
    func remove(for file: String) {
        var d = all; d.removeValue(forKey: file); all = d
    }
}
