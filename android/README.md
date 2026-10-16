# SesNot — Android / Wear OS

## Kurulum

1. Android Studio ile `android/` klasörünü açın (Gradle sarmalayıcı ilk senkronda üretilir;
   yoksa `gradle wrapper --gradle-version 8.9` çalıştırın).
2. İki uygulama modülü vardır: `mobile` (telefon) ve `wear` (saat). **İkisinin `applicationId`'si aynı
   olmalıdır** (`com.azhulabs.sesnot`) ve aynı anahtarla imzalanmalıdır; Data Layer eşleşmesi buna bağlıdır.
3. Konuşmacı ayrımı modellerini indirip `mobile/src/main/assets/diarization/` altına koyun:
   - `sherpa-onnx-pyannote-segmentation-3-0.onnx` (~6 MB)
   - `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx` (~27 MB)

   Kaynak: sherpa-onnx GitHub sürümleri, `speaker-segmentation-models` ve `speaker-recongition-models`.
   Modeller yoksa uygulama çalışır ama tek konuşmacı varsayar.

## Çalışma zamanı gereksinimleri

| Özellik | Gereksinim | Yoksa |
|---|---|---|
| Dosyadan çevrimdışı tanıma | Android 13+, Ayarlar > Sistem > Diller > Konuşma tanıma > Türkçe indirilmiş | Not "başarısız" olur, anlaşılır mesaj |
| Gemini Nano özeti | AICore destekli cihaz (Pixel 8+, Galaxy S24+ vb.) | `ExtractiveSummarizer` devreye girer |
| Konuşmacı ayrımı | assets altında iki model | Tek konuşmacı |

## Veri akışı

```
wear/RecorderService ──m4a parçaları──▶ wear/TransferManager ──ChannelClient──▶ mobile/WearListenerService
                                                                                     │ parçaları birleştir
                                                                                     ▼
                                                                          mobile/ProcessingService
                                                                                     │
                                       AudioDecoder (PCM) → SpeakerDiarizer → Transcriber (tur başına) → Summarizer
```

Kanal protokolü: ilk satır üst veri JSON (`RecordingMetadata`), ardından ham dosya baytları.

## Dosya haritası

| Modül/Dosya | Görev |
|---|---|
| `shared/Models.kt` | `NoteTemplate`, `RecordingMetadata`, `Note`, `TranscriptSegment`, `Summary` |
| `shared/SummaryTemplates.kt` | LLM istemleri (konuşmacı etiketli), JSON ayrıştırma |
| `shared/ExtractiveSummarizer.kt` | Çevrimdışı yedek özetleyici (iOS ile aynı algoritma) |
| `wear/RecorderService.kt` | Ön plan servisi, AAC 16 kHz, 10 dk parçalama, Ongoing Activity |
| `wear/TransferManager.kt` | Kanal açma, kuyruk, yeniden deneme |
| `mobile/WearListenerService.kt` | Kanalı okuma, parça birleştirme, işleme servisini tetikleme |
| `mobile/AudioDecoder.kt` | AAC → PCM16 16 kHz; dilimleme; float dönüşümü |
| `mobile/SpeakerDiarizer.kt` | sherpa-onnx pyannote + ERes2Net ile konuşmacı turları |
| `mobile/Transcriber.kt` | `SpeechRecognizer` cihaz üstü, PCM boru kaynağı |
| `mobile/Summarizer.kt` | Gemini Nano → yedek özetleyici |
| `mobile/ProcessingPipeline.kt` | Ayrım → tur başına tanıma → etiketli transkript → özet |
| `mobile/MainActivity.kt` | Compose liste/detay, konuşmacı renkleri, paylaşım |

## Bilinen eksikler

- Gradle sarmalayıcı dosyaları ve uygulama ikonları eklenmedi.
- `com.k2fsa.sherpa.onnx` ve `genai-prompt` API'leri hızlı değişiyor; sürüm sabitleri ilk derlemede doğrulanmalı.
- Birim testleri yok; `ExtractiveSummarizer` için JVM testi eklenmesi kolay.
