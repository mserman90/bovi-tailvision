# REPROSCAN: DONANIM MODİFİKASYON VE MONTAJ KILAVUZU

Bu kılavuz, eski model bir Android akıllı telefonun ahır ortamında 7/24 kesintisiz ve yangın riski olmadan çalışabilmesi (**ReproScan Bovine** kamera istasyonu), tek veya **grup halindeki (3-6 durak)** inekleri yüksek doğrulukla izleyebilmesi için gerekli donanım modifikasyonlarını ve montaj geometrisini açıklar.

---

### 1. BATARYA İPTALİ VE DOĞRUDAN DC BESLEME (PİL ŞİŞMESİNİ ÖNLEME)

Eski lityum iyon/polimer piller, 7/24 şarj cihazına bağlı bırakıldığında kimyasal bozulmaya uğrar, gaz salarak şişer ve yangın tehlikesi oluşturur. Bu nedenle **orijinal batarya fiziksel olarak sökülmelidir**.

#### 1.1. BSI / Termistör Simülasyonu
Android telefonlar, batarya terminallerindeki üçüncü pin olan **BSI (Battery Status Indicator)** veya termistör hattından sıcaklık okuyamazsa güvenlik amacıyla açılışı durdurur.
* **Gerekli Malzeme:** 1 adet $10\text{ k}\Omega$ metal film direnç.
* **Bağlantı:** Direnci anakart üzerindeki **GND (-)** pini ile **BSI/TEMP** pini arasına lehimleyin.
* **Sonuç:** Cihaz pil sıcaklığını sürekli $+25^\circ\text{C}$ algılar ve hatasız açılır.

#### 1.2. LM2596 DC-DC Voltaj Regülatörü Bağlantısı
* Telefonun anakartındaki **BAT+** ve **GND** terminallerine harici regülatör bağlanmalıdır:
```
[220V AC / 12V 2A Ahır Adaptörü]
               │
               ▼
[LM2596 DC-DC Düşürücü Devre]
 (Trimpot ile hassas 4.0V ayarlanır)
               │
      ┌────────┴────────┐
      ▼ (+)             ▼ (-)
 [Anakart BAT+]    [Anakart GND]
```
> [!CAUTION]
> Gerilimi telefonun batarya terminallerine bağlamadan önce multimetre ile **tam 4.0V** seviyesine ayarladığınızdan emin olun. 4.4V üzeri kalıcı anakart hasarına, 3.6V altı ani kapanmalara yol açar.

---

### 2. IP65 / IP66 SIZDIRMAZ AHIR KUTUSU VE LENS YALITIMI

Ahır ortamı; amonyak gazı ($NH_3$), yüksek nem ($\%80+$), toz ve sineklerle doludur.

1. **Kutu Seçimi:** $150 \times 100 \times 70\text{ mm}$ ebatlarında şeffaf kapaklı veya contalı ABS/Polikarbonat IP65 buat kutusu tercih edilmelidir.
2. **Kamera Penceresi:** Kamera lensinin önüne gelen kutu yüzeyine optik distorsiyona yol açmayan $1.5\text{ mm}$ kalınlığında çizilmeye dayanıklı optik şeffaf akrilik/polikarbonat pencere yerleştirilmeli ve kenarları nötr silikon ile izole edilmelidir.
3. **Nem Alıcı:** Kutu içerisine 1 paket silika jel (nem alıcı poşet) yerleştirilerek nem yoğuşması engellenmelidir.

---

### 3. MONTAJ GEOMETRİSİ VE KAMERA AÇISI

#### 3.1. Grup / Çoklu İnek İzleme Montajı (3 ila 6 Bağlı Durak Sırası)
Geniş açılı tek bir kamera ile yan yana duran bir grup ineği izlemek için:
* **Mesafe:** Hayvanların arka ekseninden (pelvis hizasından) **$2.5\text{ m} - 3.5\text{ m}$ geriye** (arka servis koridoru tavan kirişine) sabitlenir.
* **Yükseklik:** Yerden **$2.4\text{ m} - 3.0\text{ m}$ yüksekliğe** monte edilir.
* **Eğim Açısı:** Aşağıya doğru **$25^\circ - 30^\circ$ açıyla** yönlendirilir.
* **Kapsama Alanı (Görüş Açısı - FOV):** Akıllı telefonun standart geniş açılı lensi ($75^\circ - 85^\circ$ FOV), $3\text{ metre}$ mesafeden yaklaşık **$4.5\text{ m} - 5.5\text{ m}$ genişliğindeki durak sırasını** (yan yana 3-4 bağlı durağı) net biçimde kadraja alır.

```
                  Kamera (IP65 Kutu - 2.8m Yükseklik)
                  [==============================]
                               /  │  \
                              /   │   \  ~25° Eğim
                             /    │    \
                            ▼     ▼     ▼
                        [Durak 1] [Durak 2] [Durak 3]
                        (İnek 1)  (İnek 2)  (İnek 3)
```

#### 3.2. Tek İnek Bireysel İzleme Montajı
* **Mesafe:** İneğin kuyruk sokumundan (pelvis) **$1.2\text{ m} - 1.8\text{ m}$** geriye.
* **Yükseklik:** İneğin sırt hizasının yaklaşık **$20 - 30\text{ cm}$** üzerine (yerden ~$1.5\text{ m}$).
* **Eğim Açısı:** Aşağıya doğru **$15^\circ - 20^\circ$** açıyla kuyruk kökü ve vulvayı tam karşıdan görecek şekilde ayarlanır.

---

### 4. ELDE AKILLI TELEFONLA ÖLÇÜM GEOMETRİSİ (GEBELİK ÖN TARAMASI)

ReproScan Mobil ile hem sığırlarda hem de koyunlarda elde yapılacak hızlı gebelik taramalarında sinyal kalitesini maksimize etmek için önerilen tutuş geometrisi:

#### 4.1. Sığırlarda Rumen Akustiği ve Açlık Çukuru Solunumu
1. **Mikrofon Konumu:** Telefonun alt ana mikrofonu, ineğin **sol açlık çukuruna (paralumbar fossa - rumen dorsal kesesi)** $5 - 10\text{ cm}$ mesafeye yaklaştırılır. Aşırı rüzgar veya gürültülü fan ortamında mikrofon üzerine standart köpük rüzgarlık takılması önerilir.
2. **Kamera Açısı:** Kamera lensi, açlık çukurunun solunumla birlikte içeri-dışarı hareketini profil/çapraz ($45^\circ$) açıdan görecek şekilde $0.5 - 1.0\text{ m}$ mesafeden 30 saniye sabit tutulur.
3. **Kaudal Vulva Kontrolü:** Ölçüm sonunda telefon hayvanın arkasına geçilerek vulva dudaklarına $30 - 50\text{ cm}$ mesafeden tutulur; kuruluk, ödem ve akıntı varlığı doğrulanır.

#### 4.2. Koyunlarda Göğüs / Flank Ölçümü
1. Hayvan hafifçe zapt edilirken telefon sağ veya sol böğür hizasında kaburgaların hemen arkasına yerleştirilir.
2. 30 saniyelik tarama süresince geviş çiğneme sesleri ve solunum dalgası kaydedilir.
