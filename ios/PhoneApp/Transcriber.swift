import Speech

/// Cihaz üstü konuşma tanıma. `requiresOnDeviceRecognition = true` ile ses asla cihazdan çıkmaz.
/// Türkçe çevrimdışı model, Ayarlar > Genel > Klavye > Dikte altında Türkçe dikte indirilmişse gelir.
/// Kelime zaman damgaları döner; konuşmacı ayrımıyla eşleştirmek için kullanılır.
struct Transcriber {
    enum TranscriberError: LocalizedError {
        case notAuthorized, unavailable, onDeviceUnsupported
        var errorDescription: String? {
            switch self {
            case .notAuthorized: return "Konuşma tanıma izni verilmedi."
            case .unavailable: return "Konuşma tanıma şu an kullanılamıyor."
            case .onDeviceUnsupported: return "Bu cihazda Türkçe çevrimdışı tanıma yok. Ayarlar > Klavye > Dikte'den Türkçe'yi indirin."
            }
        }
    }

    let locale = Locale(identifier: "tr-TR")

    func transcribe(fileURL: URL) async throws -> [TimedWord] {
        try await requestAuthorization()
        guard let recognizer = SFSpeechRecognizer(locale: locale), recognizer.isAvailable else {
            throw TranscriberError.unavailable
        }
        guard recognizer.supportsOnDeviceRecognition else { throw TranscriberError.onDeviceUnsupported }

        let request = SFSpeechURLRecognitionRequest(url: fileURL)
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = false
        request.addsPunctuation = true
        request.taskHint = .dictation

        return try await withCheckedThrowingContinuation { cont in
            var finished = false
            recognizer.recognitionTask(with: request) { result, error in
                guard !finished else { return }
                if let error {
                    finished = true; cont.resume(throwing: error); return
                }
                if let result, result.isFinal {
                    finished = true
                    let words = result.bestTranscription.segments.map {
                        TimedWord(text: $0.substring, start: $0.timestamp, end: $0.timestamp + $0.duration)
                    }
                    cont.resume(returning: words)
                }
            }
        }
    }

    private func requestAuthorization() async throws {
        let status = await withCheckedContinuation { cont in
            SFSpeechRecognizer.requestAuthorization { cont.resume(returning: $0) }
        }
        guard status == .authorized else { throw TranscriberError.notAuthorized }
    }
}
