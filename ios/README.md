# SesNot — iOS / watchOS

## Kurulum (Mac gerekir)

```bash
brew install xcodegen
cd ios && xcodegen generate
open SesNot.xcodeproj
```

Xcode'da Signing bölümünde kendi takımınızı seçin. İki hedef vardır: `SesNot` (iPhone) ve
`SesNotWatch` (Apple Watch). Watch hedefi telefondan bağımsız çalışır
(`WKRunsIndependentlyOfCompanionApp`), kayıtlar telefon ulaşılabilir olunca aktarılır.

## Çalışma zamanı gereksinimleri

- **Türkçe çevrimdışı dikte:** iPhone'da Ayarlar > Genel > Klavye > Dikte > Diller altında Türkçe'yi
  indirin. İndirilmemişse `Transcriber` anlaşılır bir hata verir ve not "başarısız" olur; indirme sonrası
  "Tekrar dene" ile devam edilir.
- **Foundation Models:** iOS 26+ ve Apple Intelligence destekli cihazlarda LLM özeti kullanılır.
  Aksi halde `ExtractiveSummarizer` devreye girer. Hangi motorun kullanıldığı `ProcessingPipeline.lastEngine`
  üzerinden okunabilir.

## Konuşmacı ayrımı (Kişi 1 / Kişi 2)

Tamamen cihaz üstü, sherpa-onnx ile (pyannote segmentation-3.0 + 3D-Speaker ERes2Net embedding).

1. sherpa-onnx sürümlerinden `sherpa-onnx.xcframework` indirip projeye ekleyin; repo içindeki
   `swift-api-examples/SherpaOnnx.swift` sarmalayıcısını `PhoneApp/` altına kopyalayın.
2. İki model dosyasını `PhoneApp/Diarization/` klasörüne koyun (klasör referansı olarak ekleyin):
   `sherpa-onnx-pyannote-segmentation-3-0.onnx`, `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx`.
3. `project.yml` içinde `SESNOT_DIARIZATION` satırının yorumunu kaldırıp `xcodegen generate` çalıştırın.

Akış: tüm kayıt bir kez `SFSpeechRecognizer` ile tanınır (kelime zaman damgalı), ses 16 kHz float'a
çözülür, konuşmacı turları bulunur, her kelime orta noktasına göre bir tura atanır. Toplantı şablonunda
varsayılan açık; diğer şablonlarda not detayından "Konuşmacıları ayır" ile sonradan istenebilir.

## Dosya haritası

| Dosya | Görev |
|---|---|
| `Shared/Models.swift` | `NoteTemplate`, `RecordingMetadata`, `Note`, `Summary` |
| `Shared/SummaryTemplates.swift` | LLM istemleri ve JSON ayrıştırma |
| `Shared/ExtractiveSummarizer.swift` | Çevrimdışı yedek özetleyici |
| `WatchApp/AudioRecorder.swift` | AAC 16 kHz kayıt, 10 dk'lık parçalama, uzatılmış oturum |
| `WatchApp/WatchSessionManager.swift` | `transferFile` kuyruğu, yeniden deneme, üst veri saklama |
| `PhoneApp/PhoneSessionManager.swift` | Dosya alımı, parçaları birleştirme, hattı tetikleme |
| `PhoneApp/Transcriber.swift` | `SFSpeechRecognizer` cihaz üstü, kelime zaman damgaları |
| `PhoneApp/AudioDecoder.swift` | m4a → 16 kHz Float32 |
| `PhoneApp/SpeakerDiarizer.swift` | Konuşmacı turları + kelime→konuşmacı atama |
| `PhoneApp/Summarizer.swift` | Foundation Models → yedek özetleyici |
| `PhoneApp/ProcessingPipeline.swift` | Durum makinesi, yarım kalanları sürdürme |
| `PhoneApp/NotesListView.swift`, `NoteDetailView.swift` | Arayüz, paylaşım, alışveriş onay kutuları |

## Henüz yapılmayanlar

- Simülatörde WatchConnectivity dosya aktarımı sınırlı çalışır; gerçek cihaz çiftinde test edin.
- `whisper.cpp` entegrasyonu (daha iyi Türkçe) henüz yok.
- Birim testleri: `ExtractiveSummarizer` için örnek transkriptlerle test hedefi eklenmeli.
