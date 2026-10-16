# SesNot kadranı — Huawei Watch GT 3

SesNot temalı, koyu zeminli dijital kadran. Katmanlar `generate_layers.py` ile
Pillow üzerinden üretilir; Huawei **Theme Studio** içinde birleştirilip `.hwt`
paketi olarak dışa aktarılır ve Huawei Temalar'a gönderilir.

> Önemli sınır: Huawei kadranları yalnızca görsel + sistem verisi gösterir.
> Kadran, saat üzerinde ses kaydı başlatamaz ve telefon tarafındaki SesNot'u
> açamaz. Ayrıntı için aşağıdaki "Kısayol / uygulama başlatma" bölümüne bakın.

---

## 1. Doğrulanmış teknik bilgiler (Ekim 2026)

Kaynak: Huawei Developers resmi belgeleri (bağlantılar en altta).

| Konu | Durum |
|---|---|
| Resmi tasarım aracı | **Theme Studio 11.0.20.310** (Windows + macOS). Eski "Watch Face Designer" adı artık kullanılmıyor; Huawei'nin tek resmi kadran aracı Theme Studio. Ayrı bir "Watch Face Designer" indirmesi yok. |
| Paket biçimi | `.hwt` (hem kaynak projesi hem yayın paketi; 466 px için tek dosya) |
| GT 3 çözünürlüğü | **466 × 466 px** (çözünürlük kodu HWHD09). Hem 46 mm (1,43") hem **42 mm (1,32") GT 3 466 × 466'dır.** Huawei'nin 390 × 390 (HWHD01) seti **GT 2 42 mm** içindir, GT 3 42 mm için değildir. |
| Kadran sürümü | GT 3 için **2.1.z zorunlu** ("WATCH series + GT series" modu). 2.4.z ve üstü GT 4+ içindir; GT 3'e dağıtılmaz. |
| Dağıtım | 2.1.z paketi "GT 3 ve üstü, WATCH 3 ve üstü, GT Runner, GT Cyber, WATCH Ultimate, WATCH Buds" mağazalarına dağıtılır. 466 px yüklemesi zorunlu, diğer çözünürlükler isteğe bağlı. |
| Katmanlar | Background (zorunlu), Time (zorunlu), Date, Widget (veri). 15 kontrol türü: Single Res, Ordered Res, Selected Res, Combined Res, Text, Connection Text, Arc Text, Line Res, Arc Res, Hand Res, Selected Layer vb. GT 2.y.z modunda **video, GIF, düz Line/Arc çizimi yok**; tek görsel ≤ 750 KB; sequence frame ≤ 30 kare ve ≤ 200 × 200 px. |
| Veri alanları | Saat/dakika/saniye, AM/PM, tarih, ay, haftanın günü, **adım** (Steps, Steps Ratio), **nabız** (Heart Rate), **pil** (Power, Power Ratio, Power Enum), kalori, stres, uyku, hava durumu, basınç, rakım, mesafe, müzik vb. |
| AOD | GT serisi 2.1.z destekler. **Siyah olmayan piksel oranı < %10 ideal, %10–%20 arası kabul edilir, ≥ %20 reddedilir.** Script bu oranı hesaplayıp `aod_pixel_ratio.txt`'ye yazar (bu tasarım ≈ %5,4). |
| Yazı tipi | Text kontrolünde yalnızca sistem fontları: ROBOTO_MEDIUM 20/26/36/38, ROBOTOCONDENSED_REGULAR 20/26/36/38. Özel font yok → marka yazısı ve rakamlar PNG olarak üretildi. |

### Kısayol / uygulama başlatma — net cevap

- Huawei kadranlarında "komplikasyon kısayolu" **container redirection** adıyla
  vardır: bir veri kabına (ör. nabız) dokununca saat üstündeki **yerleşik**
  uygulama açılır. 466 px / 2.1.z için desteklenen hedefler: Dual Time, Heart
  Rate, Standup, Strength Time, Weather, Pressure, Altitude, Sunrise/Sunset,
  Distance, Sleep, Workout, Alarm, Stopwatch, Timer, Workout Record, Train
  Status, Active Record, Breathe, Call Contact, Music, Compass, Call Record,
  Moon Phase, Climb Stair (Emotion yalnız 2.9.z+).
- **Özel / üçüncü parti uygulama hedefi yoktur.** Liste sabittir; "telefonda
  SesNot'u aç" ya da "kayıt başlat" gibi bir eylem tanımlanamaz.
- GT 3 (HarmonyOS 4 lite) zaten üçüncü parti uygulama kurmaz; dolayısıyla
  kadran üzerinden SesNot'a geçiş GT 3'te **mümkün değildir**. SesNot kaydı
  için pratik yol: GT 3'ün kendi **Ses Kaydedici** uygulaması yok (GT 3 Pro'da
  da yok); kayıt telefonda başlatılır. Kadran yalnızca marka/tema sağlar.
- Bu tasarımda adım, nabız ve pil kapları yerleşik Steps/Heart Rate/Battery
  ekranlarına yönlendirme yapar (Theme Studio kap tipi atanınca otomatik).

---

## 2. Üretim

```bash
pip install pillow
python generate_layers.py          # out/466x466 ve out/390x390
python generate_layers.py --size 466
```

Tüm görseller koddan çizilir; dış dosya yok, çıktı tekrar üretilebilir.
Fontlar: Windows'ta Segoe UI; yoksa Arial / DejaVu'ya düşer (rakam PNG'leri
buna göre değişebilir, konumlar değişmez).

### Çıktı yapısı (`out/466x466/`)

| Dosya | Theme Studio'da kullanım |
|---|---|
| `active/01_background.png` | Background → Single Res (tam ekran, dairesel kesilmiş koyu zemin + turuncu parlama) |
| `active/02_waveform.png` | Background → Single Res (ses dalgası çubukları) |
| `active/03_mic.png` | Background → Single Res (mikrofon motifi) |
| `active/04_brand.png` | Background → Single Res ("SesNot" yazısı) |
| `active/05_steps_arc_track.png` | Widget → Arc Res **arka plan** (soluk yay) |
| `active/06_steps_arc_fill.png` | Widget → Arc Res **ön plan**, değer: Steps Ratio |
| `digits/0.png … 9.png`, `digits/colon.png` | Time → Combined Res (Hour High/Low, Minute High/Low) |
| `digits_aod/…` | AOD modunda aynı rakamlar (gri) |
| `icons/steps.png, heart.png, battery.png` | Widget kapları içinde Single Res ikon (40 × 40) |
| `icons/*_aod.png` | AOD ikonları (isteğe bağlı; piksel oranını artırır) |
| `aod/01_background.png` | AOD Background (saf siyah) |
| `aod/02_mic.png`, `aod/03_waveform.png` | AOD çizgisel mikrofon + kısa dalga |
| `preview_active.png`, `preview_aod.png` | **Yüklenmez.** Yerleşim referansı; Theme Studio kendi önizlemelerini (res.png 250², cover.jpg 960², icon.jpg 390², aod.jpg 960²) ekran görüntüsünden üretir. |
| `aod_pixel_ratio.txt` | AOD siyah olmayan piksel oranı |

### Yerleşim (466 px referans; 390 için orantılı)

| Öğe | Konum (x, y üst) | Not |
|---|---|---|
| Mikrofon + dalga | merkez x=233, y≈70–200 | |
| "SesNot" | x=233, y=204 | 22 px |
| Saat `HH:MM` | x=233, y=232 | rakam hücresi 72 × 112, iki nokta 34 px |
| Tarih | x=233, y=318 | Text: Week Text + Date, ROBOTOCONDENSED 26 |
| Adım / Nabız / Pil ikonları | x=128 / 233 / 338, y=372 | 40 × 40 |
| Adım / Nabız / Pil değerleri | x=128 / 233 / 338, y=396 | Text ROBOTO_MEDIUM 26 |
| Adım yayı | pad 22, 150°→390° | Arc Res |

---

## 3. Theme Studio'ya aktarım (adım adım)

1. **İndir ve kur:** Theme Studio 11.0.20.310 (Windows zip, SHA-256
   `fae20359…8e51`). Kurulum için Huawei ID ile giriş gerekebilir.
2. **Yeni proje:** `+` → Watch → **466 × 466 px**. Üstteki *Toggle Type*
   içinde **Watch GT series seçili kalsın** (2.y.z modu). Bunu kaldırırsanız
   paket 1.y.z olur ve GT 3'e dağıtılmaz.
3. **Background grubu:** `active/01…04` PNG'lerini sırayla *Single Res*
   olarak ekleyin (hepsi 466 × 466, konum 0,0).
4. **Time grubu:** *Combined Res* ekleyin; `digits/0–9.png` yükleyin;
   dört örnek oluşturun: Hour High, Hour Low, Minute High, Minute Low.
   İki nokta için ayrı *Single Res* (`digits/colon.png`). Rakamları
   y=232'ye, toplam genişlik ~300 px olacak şekilde ortalayın.
   (Alternatif: Text kontrolü + ROBOTO_MEDIUM 38; daha küçük görünür.)
5. **Date grubu:** *Connection Text* → Week Text + " " + Date, font
   ROBOTOCONDENSED_REGULAR 26, renk `#969CAA`, y=318.
6. **Widget grubu (her veri için ayrı kap):**
   - *Widget*'a tıklayıp seçimi kaldırın → yeni kap açılır.
   - Kap 1 (Steps): `icons/steps.png` Single Res + Text (Steps, ROBOTO_MEDIUM 26)
     + Arc Res (`05_…track` arka plan, `06_…fill` ön plan, değer Steps Ratio).
   - Kap 2 (Heart Rate): `icons/heart.png` + Text (Heart Rate).
   - Kap 3 (Power): `icons/battery.png` + Connection Text (Power + "%").
   - Kapların dikdörtgenleri **çakışmamalı**; yoksa dokunma yönlendirmesi
     hatalı çalışır ve inceleme reddedebilir.
7. **AOD:** Proje panelinde AOD sekmesine geçin. `aod/01_background.png`,
   `aod/02_mic.png`, `aod/03_waveform.png` + `digits_aod/` ile saat.
   Theme Studio'nun AOD doğrulaması %20 sınırını kontrol eder; bu tasarım %5,4.
8. **Test:** Animasyon alanında değerleri sürükleyerek 0, 999 999 adım, 3
   haneli nabız, %100 pil gibi uç değerlerin taşmadığını kontrol edin.
   Gerçek cihazda test: Huawei Health (AppGallery sürümü) + **tasarımcı
   sertifikası almış Huawei ID** ile *Watch faces → Test* yüklemesi.
9. **Önizleme:** Değerleri varsayılana döndürün (↺) → *Preview → Screenshot*.
   res.png / cover.jpg / icon.jpg / aod.jpg otomatik üretilir.
10. **Export:** English Title `SesNot`, Chinese Title (zorunlu, ör. `声记`),
    Author/Designer = Huawei Temalar'da kayıtlı tasarımcı adı, BriefInfo
    EN+ZH. Çıktı: `SesNot.hwt`.

---

## 4. Huawei Temalar'a gönderim

1. **Geliştirici hesabı:** developer.huawei.com → Huawei ID kaydı ve kimlik
   doğrulaması (bireysel: kimlik + banka/kart doğrulaması; birkaç iş günü).
2. **Tasarımcı sertifikası:** Console → *Content services → HUAWEI Themes*
   → *Personal Center* → Personal information + **Qualification application**.
   Rol: *Designer*. Portfolyo/örnek işler istenir; özgünlük taahhüdü verilir.
   Reddedilirse bildirilen süre dolmadan yeniden başvurulamaz (aynı işle
   tekrar başvuru "kötü niyetli" sayılıp 1–3 ay engel getirebilir).
3. **Merchant Service:** Console → *Settings → Merchant Service* etkin
   olmalı (ücretli satış ve ödeme için). Yalnız ücretsiz yayınlayacaksanız da
   Huawei bu adımı istiyor.
4. **Yayıncı sözleşmesi:** *Content services → HUAWEI Themes* → HUAWEI
   Themes Publisher Agreement'ı imzalayın.
5. **Yükleme:** *Publish → Product Category: Watch Face → Create Product* →
   `SesNot.hwt` yükle → Publication Settings (ülke/bölge, fiyat, açıklama,
   etiketler) → Submit → durum **Pending Review**.
6. **Fiyat:** Publication Settings'te **Ücretsiz** veya Huawei'nin fiyat
   kademeleri. Kadranlar için küresel kademeler: Tier 2 (sade, veri yok),
   Tier 4/6/7/8 (**saat dışında ≥ 3 veri türü** gerekir; SesNot 3 veri ile
   Tier 4 koşulunu sağlar). Ücretli satışta tasarımcı payı **%70'e kadar**;
   ödeme Self-service Settlement üzerinden.
7. **İnceleme süresi:** Huawei herkese açık bir süre taahhüdü yayımlamıyor.
   Tasarımcı topluluğu raporları genelde birkaç iş günü ile ~2 hafta arası;
   ilk gönderimde sertifika incelemesini de hesaba katın. Durum Console'da
   izlenir; red gerekçesi yazılı gelir, düzeltip *Update* ile yeniden
   gönderilir.
8. **Yayın sonrası:** Kadran Huawei Health → *Watch faces* mağazasında GT 3
   ve diğer 2.1.z uyumlu modellere dağıtılır. Güncelleme: aynı ürün →
   *Update* → yeni `.hwt` → inceleme.

### İnceleme standartlarından öne çıkanlar
- Veri kapları çakışmamalı; önizlemeler varsayılan değerlerle alınmalı.
- AOD siyah olmayan piksel < %20 (ideal < %10).
- Tüm görseller özgün olmalı; marka/logolar için hak sahibi olduğunuzu
  kanıtlayabilmelisiniz (SesNot kendi markanız → sorun yok).
- Tek görsel ≤ 750 KB (GT modu).

---

## 5. Resmi olmayan sideload yolları (yalnızca bilgi, uygulanmadı)

Huawei GT 3'e `.hwt` kurmanın tek resmi yolu Huawei Health mağazası ve
sertifikalı tasarımcı test yüklemesidir. Toplulukta dolaşan yöntemler:

| Yöntem | Ne yapıyor | Riskler |
|---|---|---|
| **Modlanmış Huawei Health APK** ("HookUtils" sekmeli, faces4watch vb.) | Resmi Health yerine imzası değiştirilmiş APK; dosya seçiciden `.hwt` yükler | Huawei imzasını kırılmış, kaynağı doğrulanamayan APK → kötü amaçlı yazılım riski; sağlık verileri bu uygulamaya emanet; resmi Health + HMS Core kaldırılmalı; Huawei telefonlarda root gerekir; firmware güncellemesiyle bozulabilir; hesap kısıtlaması olasılığı; Huawei Health lisansını ihlal eder (yeniden dağıtım hakkı yok) |
| **Gadgetbridge** (açık kaynak, AGPL-3.0) | Huawei saatleri destekler; bazı modellerde kadran yüklemeyi dener | Lisans uygun, ama GT 3/HarmonyOS 4 kadran kurulumu belgelerde garanti edilmiyor; Health senkronu kaybolur; şifreleme/eşleşme sorunları yaygın |
| **Çin bölgesi Health beta + HiSuite** (eski GT dönemi) | Özel kadran menüsü Çin Huawei ID ile açılıyordu | Güncel GT 3 için geçerli değil; Çin ID gerektirir |
| **ADB** | GT 3 lite cihaz; ADB/HDC erişimi yok | Uygulanamaz |

Not: Korsan `.hwt` dosyaları (mağazadan kopyalanmış ücretli kadranlar)
paylaşan hesaplar Huawei tarafından kapatılıyor. Bu repo yalnızca kendi
tasarımımızı içerir; sideload için hiçbir araç veya paket eklenmemiştir.

---

## 6. Kaynaklar

- Theme Studio indirme (11.0.20.310): https://developer.huawei.com/consumer/en/doc/Tools-Library/theme_download-0000001050424897
- Geliştirme araçları sayfası: https://developer.huawei.com/consumer/en/doc/content/themes-design-tools-0000001054531194
- Kadran genel bakış (Background/Time/Date/Data, AOD): https://developer.huawei.com/consumer/en/doc/content/watch-face-introduction-0000001566918497
- Çözünürlükler, sürüm numaraları ve dağıtım kuralları (GT 3 = 466 px, 2.1.z): https://developer.huawei.com/consumer/en/doc/content/resolution-version-0000001252603441
- 466 × 466 yetenek seti (kontroller, veri tipleri, uygulama yönlendirme listesi, AOD oranı): https://developer.huawei.com/consumer/en/doc/content/466-capability-0000001881726154
- Theme Studio kadran oluşturma kılavuzu (kaplar, yönlendirme, export): https://developer.huawei.com/consumer/en/doc/content/watch-face-production-0000001530133996
- Kadran yükleme adımları: https://developer.huawei.com/consumer/en/doc/content/sportwatch-upload-0000001054469759
- Kayıt ve tasarımcı sertifikası: https://developer.huawei.com/consumer/en/doc/content/settlement-guidance-0000001056348857
- Fiyat kademeleri: https://developer.huawei.com/consumer/en/doc/content/content-pricing-specifications-0000001096951357
- Huawei Temalar geliştirici portalı (%70 gelir payı): https://developer.huawei.com/consumer/en/huaweithemes
- GT 3 42 mm ekran 1,32" (Huawei spesifikasyon): https://consumer.huawei.com/uk/wearables/watch-gt3/specs
- Sideload topluluk rehberi (riskleri için incelendi, önerilmez): https://faces4watch.com/download/how-to-install-custom-watch-faces-on-huawei-watch-gt-gt2-gt2e-gt2-pro-honor-watches
