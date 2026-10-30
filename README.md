# Bovi-TailVision: Eski Model Android Cihazlar İçin Görüntü İşleme Tabanlı Sığır Kızgınlık Tespit Sistemi

Bovi-TailVision; bağlı duraklı veya serbest ahırlarda, ineğin veya bir **grup ineğin (3 ila 6 durak/yemlik kilit sırası)** kaudal (pelvis) açısına sabitlenecek **eski model Android akıllı telefonların** kamera ve yerleşik işlem gücünü kullanarak kuyruk açısı sapması, vulvanın açığa çıkma süresi/frekansı ve mikro-hareket dinamiklerini gerçek zamanlı izleyip kızgınlık (östrus) alarmları üreten uç nokta (Edge Computing) sistemidir.

---

## 📁 Proje Dizin Yapısı

```
bovi-tailvision/
├── android/                   # Multi-Module Android Projesi
│   ├── app/                   # [UÇ NOKTA KAMERA CİHAZI] Ahırda gruba bakan telefon
│   │   ├── src/main/java/com/bovivision/estrus/
│   │   │   ├── MainActivity.kt               # Kontrol & termal ekran karartma
│   │   │   ├── analyzer/
│   │   │   │   ├── BoviTailAnalyzer.kt       # Çoklu nesne CameraX analizörü
│   │   │   │   ├── MultiCowTracker.kt        # Uzamsal durak eşleme ve MOT takip motoru
│   │   │   │   ├── KinematicsEngine.kt       # Vektörel açı & sapma motoru
│   │   │   │   └── TemporalMovementFilter.kt # Dışkılama ve sinek savurma filtresi
│   │   │   ├── service/
│   │   │   │   ├── EstrusDetectionService.kt # 7/24 Kesintisiz Foreground Servis
│   │   │   │   └── BootReceiver.kt           # Cihaz açıldığında oto-başlatıcı
│   │   │   ├── network/
│   │   │   │   ├── MqttManager.kt            # Uç birim MQTT yayıncısı
│   │   │   │   └── TelemetryModels.kt        # JSON telemetri & alarm modelleri
│   │   │   └── utils/
│   │   │       └── DeviceHealthMonitor.kt    # CPU sıcaklık, RAM ve batarya takibi
│   │   └── src/main/assets/bovi_tail_pose.tflite
│   │
│   ├── farmer_app/            # [ÇİFTÇİ CEP TELEFONU UYGULAMASI]
│   │   ├── src/main/java/com/bovivision/farmer/
│   │   │   ├── FarmerMainActivity.kt         # Sürü/grup alarm listesi & tohumlama takibi
│   │   │   ├── service/FarmerAlertListenerService.kt # Sesli siren/titreşimli uyarı servisi
│   │   │   ├── adapter/AlertAdapter.kt       # Alarm kartları & durak onay mekanizması
│   │   │   └── model/FarmerAlertModel.kt     # AM/PM kuralı hesaplayıcı
│   │   └── src/main/res/layout/
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
