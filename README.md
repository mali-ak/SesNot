# SesNot — Saatten Kaydet, Telefonda Özetle

Akıllı saatte tek tuşla ses kaydı al, telefona aktar, telefon **cihaz üstünde** (internet olmadan)
metne çevirip seçilen şablona göre özetlesin: toplantı tutanağı, alışveriş listesi veya sesli not.

## Mimari

```
┌──────────────┐   ses dosyası (m4a/opus)   ┌──────────────────────────────────┐
│    SAAT      │ ─────────────────────────▶ │            TELEFON               │
│ kayıt + kuyruk│   watchOS: WatchConnectivity│ 1. Transkripsiyon (cihaz üstü)   │
│              │   Wear OS: Data Layer       │ 2. Özetleme (cihaz üstü)         │
│              │ ◀───────────────────────── │ 3. Kayıt, arama, paylaşım        │
└──────────────┘   durum: alındı / hazır    └──────────────────────────────────┘
```

**Saat tarafı sadece üç şey yapar:** kayıt başlat/durdur, dosyayı yerel kuyruğa yaz, telefon
erişilebilir olunca aktar. Hiçbir ağır işlem saatte yapılmaz; pil bu sayede korunur.

**Telefon tarafı** dosyayı alır ve bir boru hattından geçirir:

| Adım | iOS | Android |
|---|---|---|
| Saat iletişimi | `WCSession.transferFile` | `ChannelClient` (Wearable Data Layer) |
| Transkripsiyon | `SFSpeechRecognizer` (`requiresOnDeviceRecognition = true`) | `SpeechRecognizer` (`EXTRA_PREFER_OFFLINE`) |
| Özetleme (LLM) | Foundation Models (iOS 26+, Apple Intelligence cihazlar) | Gemini Nano Prompt API (AICore destekli cihazlar) |
| Özetleme (yedek) | Çıkarımsal özetleyici (`ExtractiveSummarizer`) | Aynı algoritma, Kotlin |
| Depolama | JSON dosya (NoteStore) | Room yerine basit JSON (NoteRepository) |

### Konuşmacı ayrımı (Kişi 1, Kişi 2, …)

Toplantılarda kimin ne söylediği ayrı tutulur. İki platformda da **sherpa-onnx** ile cihaz üstü
konuşmacı bölütleme yapılır (pyannote segmentation-3.0 + 3D-Speaker ERes2Net embedding, dil bağımsız,
~33 MB model). Konuşmacılar sesten kümelenir; isim bilinmediği için "Kişi 1", "Kişi 2" etiketlenir.

| | iOS | Android |
|---|---|---|
| Transkripsiyon stratejisi | Tüm kayıt bir kez tanınır, kelime zaman damgaları turlara dağıtılır | Her konuşmacı turu ayrı tanınır (SpeechRecognizer zaman damgası vermez) |
| Çıktı | `Note.segments: [TranscriptSegment]` + "Kişi N: …" etiketli `transcript` | Aynı |
| Özet | Toplantı istemi konuşmacı etiketlerini kullanır; kararlar ve aksiyonlar kişiye bağlanır | Aynı |

Varsayılan: toplantı şablonunda açık, alışveriş ve notta kapalı (hız). Not detayından sonradan
"Konuşmacıları ayır" ile tetiklenebilir. Model dosyaları yoksa uygulama tek konuşmacıya düşer.

Yedek özetleyici her iki platformda **aynı algoritmayı** uygular, böylece LLM olmayan cihazlarda da
tutarlı sonuç alınır. Şablon başına kural tabanlı sonradan işleme vardır (bkz. `SummaryTemplates`).

### Telefondan kayıt modu (saat uygulaması olmayanlar için)

Huawei GT serisi gibi üçüncü parti uygulama almayan saatlerde kayıt **telefondan** başlatılır; ses kaynağı
olarak bağlı Bluetooth arama cihazının (saatin) mikrofonu seçilebilir. Böylece telefon cepte kalır, ses
bilekten gelir. Kalan akış (transkripsiyon, özet, konuşmacı ayrımı) aynıdır.

| | iOS | Android |
|---|---|---|
| Bluetooth mikrofon | `AVAudioSession` `.allowBluetooth` + `.voiceChat`, `setPreferredInput(HFP)` | `AudioManager.setCommunicationDevice(SCO)` + `VOICE_COMMUNICATION` kaynağı |
| Hızlı tetikleme | Siri / Kısayollar ("SesNot kaydı başlat"), App Intents | Hızlı ayarlar kutucuğu, bildirimden "Durdur" |
| Sınır | Arama kalitesi ses (16 kHz mono); gelen arama kaydı keser | Aynı; Android 12+ gerekir |

## Şablonlar

| Şablon | Çıktı |
|---|---|
| `meeting` (Toplantı) | Başlık, katılımcı ipuçları, kararlar, aksiyon maddeleri, tarihler |
| `shopping` (Alışveriş) | Onay kutulu madde listesi; miktar ve birim ayrıştırılır |
| `note` (Sesli not) | 2-4 cümlelik özet + ham transkript |

## Klasörler

- `ios/` — `project.yml` (XcodeGen) ile iki hedef: `SesNot` (iPhone) ve `SesNot Watch`.
- `android/` — Gradle çoklu modül: `shared`, `wear`, `mobile`.

Kurulum adımları için her klasördeki `README.md` dosyasına bakın.

## Bilinen sınırlar ve riskler

- **Türkçe cihaz üstü tanıma kalitesi** sistem tanıyıcılarında orta düzeydedir. Daha iyi sonuç için
  ileride `whisper.cpp` entegrasyonu (her iki platformda da çalışır) planlanmalı.
- **Foundation Models** yalnızca Apple Intelligence destekli cihazlarda; **Gemini Nano** yalnızca
  AICore destekli Pixel/Samsung amiral gemilerinde çalışır. Diğer cihazlar yedek özetleyiciye düşer.
- **Konuşmacı ayrımı doğruluğu** iki-dört kişilik, az üst üste konuşmalı toplantılarda iyidir;
  kalabalık ve gürültülü ortamlarda konuşmacı sayısı yanlış çıkabilir. Kümeleme eşiği
  (`CLUSTER_THRESHOLD` / `clusterThreshold`) ayarlanabilir. Kişi isimleri sesten çıkarılamaz;
  ileride kullanıcı "Kişi 1 = Ahmet" eşlemesi yapabilsin (ses profili saklama) planlanabilir.
- **Uzun kayıtlar** (1 saat+) saatte parçalı dosya olarak tutulur ve sırayla aktarılır.
- **Hukuki uyarı:** toplantı kaydı için karşı tarafı bilgilendirme zorunluluğu olabilir;
  uygulama ilk toplantı kaydında bu uyarıyı gösterir.
