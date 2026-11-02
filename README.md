# ReproScan: Akıllı Ruminant Üreme ve Karar Destek Platformu (ReproScan AI)

[![GitHub Repo](https://img.shields.io/badge/GitHub-mserman90%2Freproscan-blue?logo=github)](https://github.com/mserman90/reproscan)
[![Live Demo](https://img.shields.io/badge/GitHub%20Pages-Live%20Demo-emerald?logo=githubpages)](https://mserman90.github.io/reproscan/)
[![Companion App](https://img.shields.io/badge/OviScan-Companion%20Module-teal)](https://mserman90.github.io/oviscan-android/)

**ReproScan**, büyükbaş ve küçükbaş hayvancılıkta harici donanım maliyetlerini ortadan kaldırarak atıl ve standart Android akıllı telefonların yerleşik donanımlarını (kamera, mikrofon, ivmeölçer) uç birim yapay zeka (Edge AI) ile buluşturan birleşik bir üreme karar destek platformudur:

1. 🐄 **ReproScan Bovine (Büyükbaş Sığır):**
   * **Sabit Ahır Kamerası:** Durak sırasına (3-6 inek) bakan sabit telefonla 7/24 bilgisayarlı görü ve iskelet takibi yaparak kuyruk sapması ve vulva açığa çıkması üzerinden **kızgınlık (östrus) tespiti** ve AM/PM tohumlama penceresi üretir.
   * **Cep Telefonuyla Sığır Gebelik Taraması:** Harici prob gerektirmeksizin tohumlama sonrası 21 ve 42 günlük non-return takvimi, rumen geviş akustiği (45-60 çiğneme/dk), CameraX açlık çukuru optik solunumu ve vulva morfolojisi ile cep telefonundan **erken sığır gebelik ön taraması ve veteriner teyit desteği** sunar.
2. 🐑 **ReproScan Ovine (Küçükbaş Koyun & Keçi):** Harici prob gerektirmeksizin dahili telefon sensörleri (mikrofon geviş akustiği, kamera optik solunum BPM'i ve ivmeölçer) ile tohumlama sonrası **gebelik ön taraması, BCS kondisyon ve karar destek** sağlar ([OviScan Entegrasyonu](https://mserman90.github.io/oviscan-android/)).
3. 📱 **ReproScan Mobil (Birleşik Çiftçi Uygulaması):** Çiftçinin cebindeki tek bir mobil uygulamada hem ahır kızgınlık alarmlarını sesli sirenle karşılar hem de hem sığır hem de koyun gebelik ön tarama testlerini yönetir.

---

## 📁 Proje Dizin Yapısı

```
reproscan/
├── android/                   # Multi-Module Android Projesi
│   ├── app/                   # [REPROSCAN BOVINE] Ahırda gruba bakan sabit kamera istasyonu
│   │   ├── src/main/java/com/bovivision/estrus/
│   │   │   ├── MainActivity.kt               # Kontrol & termal ekran karartma
│   │   │   ├── analyzer/
│   │   │   │   ├── BoviTailAnalyzer.kt       # Çoklu nesne CameraX analizörü
│   │   │   │   ├── MultiCowTracker.kt        # Uzamsal durak eşleme ve MOT takip motoru
│   │   │   │   ├── KinematicsEngine.kt       # Vektörel açı & sapma motoru
│   │   │   │   └── TemporalMovementFilter.kt # Dışkılama ve sinek savurma filtresi
│   │   │   ├── service/                      # 7/24 Kesintisiz Foreground Servis
│   │   │   └── network/                      # Uç birim MQTT yayıncısı
│   │   └── src/main/assets/bovi_tail_pose.tflite
│   │
│   ├── farmer_app/            # [REPROSCAN MOBIL] Çiftçi Cep Takip, Sığır & Koyun Gebelik Modülü
│   │   ├── src/main/java/com/bovivision/farmer/
│   │   │   ├── FarmerMainActivity.kt         # Çift sekmeli arayüz (Bovine + Ovine)
│   │   │   ├── boviscan/                     # [SIĞIR GEBELİK ÖN TARAMA MODÜLÜ]
│   │   │   │   ├── domain/model/             # Sığır risk seviyeleri & sensör batch'i
│   │   │   │   └── ml/BovineLateFusionClassifier.kt # 21/42 günlük non-return & geviş füzyonu
│   │   │   ├── oviscan/                      # [KOYUN GEBELİK ÖN TARAMA MODÜLÜ]
│   │   │   │   ├── domain/model/             # Gebelik risk seviyeleri & sensör batch'i
│   │   │   │   └── ml/LateFusionClassifier.kt # Çok modlu gebelik ön tarama füzyonu
│   │   │   ├── service/FarmerAlertListenerService.kt # Sesli siren/titreşimli uyarı servisi
│   │   │   └── adapter/AlertAdapter.kt       # Alarm kartları & durak onay mekanizması
│   │   └── src/main/res/layout/activity_farmer_main.xml
│   │   │   │   ├── domain/model/             # Gebelik risk seviyeleri & sensör batch'i
│   │   │   │   └── ml/LateFusionClassifier.kt # Çok modlu gebelik ön tarama füzyonu
│   │   │   ├── service/FarmerAlertListenerService.kt # Sesli siren/titreşimli uyarı servisi
│   │   │   └── adapter/AlertAdapter.kt       # Alarm kartları & durak onay mekanizması
│   │   └── src/main/res/layout/activity_farmer_main.xml
│   │
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── ai_pipeline/               # Model eğitimi ve INT8 PTQ kuantizasyon hattı
│   ├── dataset_spec.yaml                     # YOLO-pose 4 anahtar nokta şeması
│   ├── train_and_export_tflite.py            # Ultralytics & TFLite INT8 dönüştürücü
│   └── test_quantized_inference.py           # Model doğrulama ve kinematik test
│
├── farm_server/               # Ahır yerel sunucusu & IoT Broker
│   ├── docker-compose.yml                    # Mosquitto MQTT & Listener servisi
│   ├── mosquitto.conf                        # Broker yapılandırması
│   └── farm_dashboard_listener.py            # Telemetri, alarm dinleyici & Telegram köprüsü
│
└── docs/                      # Mühendislik, teori ve donanım dokümanları
    ├── ACADEMIC_PAPER_AND_MANUAL.md          # Akademik makale, teori ve operasyon kılavuzu
    ├── ARCHITECTURE.md                       # Ayrıntılı çoklu inek sistem mimarisi
    ├── HARDWARE_SETUP.md                     # Batarya iptali, BSI bypass, IP65 grup montajı
    └── index.html                            # Çift cihazlı interaktif canlı web demosu
```

---

## 🚀 Hızlı Başlangıç

### 1. Android Uygulamalarını Derleme (APK)
1. `android/` klasörünü Android Studio ile açın.
2. Kamera veya Çiftçi uygulamasını derleyin:
   ```bash
   cd android

   # 1. Ahırdaki Kamera Cihazı (Grup Uç Nokta Algılayıcı)
   ./gradlew :app:assembleDebug

   # 2. Çiftçinin Cep Telefonu Uygulaması (Grup Alarm & Takip)
   ./gradlew :farmer_app:assembleDebug
   ```
3. Oluşan APK'ları cihazlara yükleyin:
   * **Ahırdaki eski telefona:** `app/build/outputs/apk/debug/app-debug.apk`
   * **Çiftçinin kendi telefonuna:** `farmer_app/build/outputs/apk/debug/farmer_app-debug.apk`

### 2. Ahır MQTT Sunucusunu Başlatma
Ahır yerel ağındaki bir Raspberry Pi veya Mini PC üzerinde:
```bash
cd farm_server
docker-compose up -d
```
veya doğrudan Python ile:
```bash
cd farm_server
pip install paho-mqtt
python farm_dashboard_listener.py
```

### 3. Model Eğitimi ve INT8 İhracı
Kendi sığır arka anatomisi veri setinizi eğitmek ve TFLite INT8 modelini Android assets klasörüne aktarmak için:
```bash
cd ai_pipeline
pip install ultralytics tensorflow opencv-python
python train_and_export_tflite.py
```

---

## ⚙️ Donanım Kurulumu (Önemli Güvenlik Adımları)

Detaylı bağlantı ve grup montaj şemaları için [HARDWARE_SETUP.md](file:///C:/Users/murat.erman/.gemini/antigravity/scratch/bovi-tailvision/docs/HARDWARE_SETUP.md) dokümanını inceleyin:
* **Batarya İptali:** 7/24 şarjda pil şişmesini önlemek için batarya çıkarılmalı; anakart BAT+ ve GND pinlerine **4.0V DC-DC (LM2596)** bağlanmalıdır.
* **BSI Direnci:** Cihazın açılabilmesi için GND ile BSI pinleri arasına **$10\text{ k}\Omega$ direnç** bağlanmalıdır.
* **Grup Montajı:** 3-4 duraklık bir sırayı izlemek için kamera yerden $2.8\text{ m}$ yüksekliğe, hayvanların $3.0\text{ m}$ arkasına $25^\circ$ eğimle yerleştirilir.
* **Yalıtım:** Amonyak ve neme karşı **IP65 buat kutusu** ve optik polikarbonat pencere kullanılmalıdır.
