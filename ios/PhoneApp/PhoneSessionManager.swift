import Combine
import WatchConnectivity

/// Saatten gelen dosyaları alır. Çok parçalı kayıtları birleştirir ve işleme hattına verir.
///
/// Önemli: `didReceive file` içinde verilen URL, delegate dönüşünden sonra sistem tarafından silinir;
/// dosya aynı çağrı içinde (senkron) taşınmalıdır.
final class PhoneSessionManager: NSObject, ObservableObject {
    private let store: NoteStore
    private let pipeline: ProcessingPipeline
    private let partsDirectory: URL

    init(store: NoteStore, pipeline: ProcessingPipeline) {
        self.store = store
        self.pipeline = pipeline
        partsDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Parts", isDirectory: true)
        try? FileManager.default.createDirectory(at: partsDirectory, withIntermediateDirectories: true)
        super.init()
    }

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Saate kısa durum mesajı (opsiyonel; saat erişilebilir değilse sessizce atlanır).
    func notifyWatch(_ status: String) {
        guard WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["status": status], replyHandler: nil)
    }
}

extension PhoneSessionManager: WCSessionDelegate {
    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {}
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }

    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard let meta = RecordingMetadata(dictionary: file.metadata) else {
            print("SesNot: üst veri okunamadı, dosya atlandı")
            return
        }
        // Dosyayı HEMEN kalıcı yere taşı.
        let partURL = partsDirectory.appendingPathComponent("\(meta.id.uuidString)-\(meta.partIndex).m4a")
        try? FileManager.default.removeItem(at: partURL)
        do { try FileManager.default.moveItem(at: file.fileURL, to: partURL) } catch {
            print("SesNot: dosya taşınamadı: \(error)"); return
        }
        // Parçalar sırasız gelebilir; son parça toplam sayıyı taşır, onu sakla.
        if meta.partCount > 0 {
            UserDefaults.standard.set(meta.partCount, forKey: "partCount-\(meta.id.uuidString)")
        }

        Task { @MainActor in
            // İlk parça geldiğinde notu "alındı" durumunda oluştur; kullanıcı ilerlemeyi görsün.
            if store.note(id: meta.id) == nil {
                store.upsert(Note(id: meta.id, template: meta.template, createdAt: meta.startedAt,
                                  duration: meta.duration, status: .received,
                                  audioFileName: "\(meta.id.uuidString).m4a"))
            } else {
                store.update(id: meta.id) { $0.duration += meta.duration }
            }

            // Tüm parçalar geldi mi? Son parça partCount'u taşır (ara parçalarda 0).
            let parts = self.partFiles(for: meta.id)
            let expected = meta.partCount > 0 ? meta.partCount : self.knownPartCount(for: meta.id)
            guard let expected, parts.count >= expected else { return }

            let finalURL = NoteStore.recordingsDirectory.appendingPathComponent("\(meta.id.uuidString).m4a")
            do {
                try await AudioMerger.merge(parts: parts.sorted { $0.lastPathComponent < $1.lastPathComponent }, to: finalURL)
                parts.forEach { try? FileManager.default.removeItem(at: $0) }
                self.notifyWatch("İşleniyor…")
                await self.pipeline.process(noteID: meta.id)
                self.notifyWatch("Özet hazır")
            } catch {
                store.update(id: meta.id) { $0.status = .failed; $0.errorMessage = error.localizedDescription }
            }
        }
    }

    private func partFiles(for id: UUID) -> [URL] {
        ((try? FileManager.default.contentsOfDirectory(at: partsDirectory, includingPropertiesForKeys: nil)) ?? [])
            .filter { $0.lastPathComponent.hasPrefix(id.uuidString) }
    }

    /// Son parça daha önce gelmiş ve partCount'u kaydetmişse onu kullan.
    private func knownPartCount(for id: UUID) -> Int? {
        let v = UserDefaults.standard.integer(forKey: "partCount-\(id.uuidString)")
        return v > 0 ? v : nil
    }
}
