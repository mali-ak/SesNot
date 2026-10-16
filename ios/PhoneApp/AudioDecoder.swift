import AVFoundation

/// m4a dosyasını 16 kHz mono Float32 örneklere çözer (konuşmacı ayrımı girişi).
enum AudioDecoder {
    static let sampleRate: Double = 16_000

    static func decodeToFloat(url: URL) throws -> [Float] {
        let file = try AVAudioFile(forReading: url)
        guard let target = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate,
                                         channels: 1, interleaved: false),
              let converter = AVAudioConverter(from: file.processingFormat, to: target) else {
            throw NSError(domain: "SesNot", code: 20, userInfo: [NSLocalizedDescriptionKey: "Ses dönüştürücü oluşturulamadı"])
        }
        let ratio = sampleRate / file.processingFormat.sampleRate
        let outCapacity = AVAudioFrameCount(Double(file.length) * ratio) + 1024
        guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: outCapacity) else {
            throw NSError(domain: "SesNot", code: 21, userInfo: [NSLocalizedDescriptionKey: "Tampon ayrılamadı"])
        }
        var readError: Error?
        var finished = false
        let status = converter.convert(to: out, error: &readError) { packetCount, outStatus in
            if finished { outStatus.pointee = .endOfStream; return nil }
            guard let buf = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: packetCount) else {
                outStatus.pointee = .endOfStream; return nil
            }
            do { try file.read(into: buf) } catch { readError = error; outStatus.pointee = .endOfStream; return nil }
            if buf.frameLength == 0 { finished = true; outStatus.pointee = .endOfStream; return nil }
            outStatus.pointee = .haveData
            return buf
        }
        if let readError { throw readError }
        guard status != .error, let ch = out.floatChannelData else {
            throw NSError(domain: "SesNot", code: 22, userInfo: [NSLocalizedDescriptionKey: "Ses çözülemedi"])
        }
        return Array(UnsafeBufferPointer(start: ch[0], count: Int(out.frameLength)))
    }
}
