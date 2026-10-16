import AVFoundation

/// Birden fazla m4a parçasını tek dosyada birleştirir. Tek parça varsa sadece taşır.
enum AudioMerger {
    static func merge(parts: [URL], to output: URL) async throws {
        try? FileManager.default.removeItem(at: output)
        if parts.count == 1 {
            try FileManager.default.copyItem(at: parts[0], to: output)
            return
        }
        let composition = AVMutableComposition()
        guard let track = composition.addMutableTrack(withMediaType: .audio,
                                                      preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw NSError(domain: "SesNot", code: 2, userInfo: [NSLocalizedDescriptionKey: "Ses izi oluşturulamadı"])
        }
        var cursor = CMTime.zero
        for url in parts {
            let asset = AVURLAsset(url: url)
            guard let src = try await asset.loadTracks(withMediaType: .audio).first else { continue }
            let duration = try await asset.load(.duration)
            try track.insertTimeRange(CMTimeRange(start: .zero, duration: duration), of: src, at: cursor)
            cursor = CMTimeAdd(cursor, duration)
        }
        guard let export = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            throw NSError(domain: "SesNot", code: 3, userInfo: [NSLocalizedDescriptionKey: "Dışa aktarım başlatılamadı"])
        }
        export.outputURL = output
        export.outputFileType = .m4a
        await export.export()
        if let error = export.error { throw error }
    }
}
