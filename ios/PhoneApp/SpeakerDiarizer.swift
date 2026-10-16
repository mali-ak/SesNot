import Foundation

/// Konuşmacı ayrımı sonucu: [start, end) aralığında `speaker` konuşuyor.
struct SpeakerTurn { let speaker: Int; let start: TimeInterval; let end: TimeInterval }

/// Cihaz üstü konuşmacı ayrımı (sherpa-onnx: pyannote segmentation-3.0 + 3D-Speaker ERes2Net).
///
/// Kurulum: `sherpa-onnx.xcframework` + `SherpaOnnx.swift` sarmalayıcısını projeye ekleyin ve
/// `SESNOT_DIARIZATION` derleme bayrağını tanımlayın (project.yml'de hazır). Modeller uygulama
/// paketinde `Diarization/` klasöründe beklenir. Bayrak yoksa `isAvailable` false döner ve
/// pipeline tek konuşmacı varsayar. Model dil bağımsızdır; Türkçe için ek eğitim gerekmez.
struct SpeakerDiarizer {
    static let segmentationModel = "sherpa-onnx-pyannote-segmentation-3-0.onnx"
    static let embeddingModel = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    /// Daha küçük → daha çok konuşmacı. 0.5 iki-dört kişilik toplantılar için makul başlangıç.
    static let clusterThreshold: Float = 0.5

    var isAvailable: Bool {
        #if SESNOT_DIARIZATION
        return modelPath(Self.segmentationModel) != nil && modelPath(Self.embeddingModel) != nil
        #else
        return false
        #endif
    }

    private func modelPath(_ name: String) -> String? {
        Bundle.main.path(forResource: (name as NSString).deletingPathExtension, ofType: "onnx", inDirectory: "Diarization")
    }

    /// - Parameters:
    ///   - samples: 16 kHz mono Float32.
    ///   - expectedSpeakers: bilinmiyorsa -1 (eşikle otomatik kümeleme).
    func diarize(samples: [Float], expectedSpeakers: Int = -1) -> [SpeakerTurn] {
        #if SESNOT_DIARIZATION
        guard let seg = modelPath(Self.segmentationModel), let emb = modelPath(Self.embeddingModel) else { return [] }
        var config = sherpaOnnxOfflineSpeakerDiarizationConfig(
            segmentation: sherpaOnnxOfflineSpeakerSegmentationModelConfig(
                pyannote: sherpaOnnxOfflineSpeakerSegmentationPyannoteModelConfig(model: seg), numThreads: 2),
            embedding: sherpaOnnxSpeakerEmbeddingExtractorConfig(model: emb, numThreads: 2),
            clustering: sherpaOnnxFastClusteringConfig(numClusters: Int32(expectedSpeakers), threshold: Self.clusterThreshold),
            minDurationOn: 0.3, minDurationOff: 0.5)
        let sd = SherpaOnnxOfflineSpeakerDiarizationWrapper(config: &config)
        let turns = sd.process(samples: samples).map {
            SpeakerTurn(speaker: Int($0.speaker), start: TimeInterval($0.start), end: TimeInterval($0.end))
        }.sorted { $0.start < $1.start }
        return Self.mergeAdjacent(turns)
        #else
        return []
        #endif
    }

    /// Aynı konuşmacının 1 sn'den kısa aralıklı ardışık turlarını birleştir.
    static func mergeAdjacent(_ turns: [SpeakerTurn]) -> [SpeakerTurn] {
        var out: [SpeakerTurn] = []
        for t in turns {
            if let last = out.last, last.speaker == t.speaker, t.start - last.end < 1.0 {
                out[out.count - 1] = SpeakerTurn(speaker: last.speaker, start: last.start, end: t.end)
            } else {
                out.append(t)
            }
        }
        return out
    }

    /// Kelime zaman damgalı transkripti konuşmacı turlarına dağıtır: her kelime, orta noktasının
    /// düştüğü tura (yoksa en yakın tura) atanır.
    static func assign(words: [TimedWord], to turns: [SpeakerTurn]) -> [TranscriptSegment] {
        guard !turns.isEmpty else {
            let text = words.map(\.text).joined(separator: " ")
            return [TranscriptSegment(speaker: 0, start: words.first?.start ?? 0, end: words.last?.end ?? 0, text: text)]
        }
        var buckets = Array(repeating: [TimedWord](), count: turns.count)
        for w in words {
            let mid = (w.start + w.end) / 2
            let idx = turns.firstIndex { mid >= $0.start && mid < $0.end }
                ?? turns.indices.min { abs(turns[$0].start - mid) < abs(turns[$1].start - mid) }!
            buckets[idx].append(w)
        }
        return zip(turns, buckets).compactMap { turn, ws in
            guard !ws.isEmpty else { return nil }
            return TranscriptSegment(speaker: turn.speaker, start: turn.start, end: turn.end,
                                     text: ws.map(\.text).joined(separator: " "))
        }
    }
}

/// `SFTranscriptionSegment`in sadeleştirilmiş hali.
struct TimedWord { let text: String; let start: TimeInterval; let end: TimeInterval }
