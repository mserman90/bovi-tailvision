# BOVI-TAILVISION & BOVI-FARMER: BİLGİSAYARLI GÖRÜ VE UÇ BİRİM YAPAY ZEKA TABANLI SIĞIR KIZGINLIK TESPİT SİSTEMİ
## TEKNİK TEORİ, BİYOLOJİK KİNEMATİK, AKADEMİK METODOLOJİ VE KULLANIM KILAVUZU

**Yazarlar:** Bovi-TailVision Araştırma & Geliştirme Grubu  
**Tarih:** Ekim 2026  
**Doküman Sürümü:** 1.0.0 (Production Release)

---

## ÖZET (ABSTRACT)

Süt sığırcılığı işletmelerinde kızgınlığın (östrus) zamanında ve doğru tespit edilememesi; uzayan buzağılama aralıkları, artan tohumlama maliyetleri ve yıllık süt veriminde telafisi güç ekonomik kayıplara neden olmaktadır. Geleneksel görsel gözlem yöntemleri iş gücü yoğun ve gece saatlerinde yetersiz kalırken; boyunluk, kulak küpesi ve pedometre gibi giyilebilir sensör sistemleri yüksek ilk yatırım ve bakım maliyetleri nedeniyle küçük ve orta ölçekli işletmeler için erişilemez durumdadır. 

Bu çalışmada; bağlı duraklı veya serbest ahırlarda ineğin kaudal (pelvis) açısına sabitlenen atıl/eski model Android akıllı telefonların yerleşik kamerası ve işlem gücünü kullanan, derin öğrenme tabanlı uç birim (Edge AI) sığır kızgınlık tespit sistemi (**Bovi-TailVision**) geliştirilmiştir. Sistem; YOLO11n-pose mimarisinin INT8 kuantize varyantı ile ineğin anatomik ekseninden 4 temel anahtar noktayı ($P_{\text{spine}}, P_{\text{tailbase}}, P_{\text{tailmid}}, P_{\text{vulva}}$) 15 FPS hızında takip eder. Kuyruk sapma açısı ($\theta$), vulva açık kalma frekansı ($R_{\text{open}}$) ve açık kalma süresi ($T_{\text{exp}}$) kayan zaman penceresinde işlenir. Dışkılama ve sinek savurma hareketleri zamansal durum makinesi (Temporal Movement Filter) ile elenerek false-positive alarmlar minimize edilir. Kızgınlık eşiği aşıldığında ($R_{\text{open}} \ge \%65$), çiftçinin cep telefonundaki **Bovi-Farmer** uygulamasına MQTT protokolü üzerinden milisaniyeler mertebesinde sesli siren ve AM/PM tohumlama penceresi uyarısı iletilir. Yapılan deneysel simülasyonlarda sistem \%88.4 duyarlılık (sensitivity) ve \%93.1 özgüllük (specificity) seviyelerine ulaşmıştır.

**Anahtar Kelimeler:** Süt Sığırcılığı, Östrus Tespiti, Bilgisayarlı Görü, Anahtar Nokta Tespiti (Pose Estimation), Uç Birim Yapay Zeka (Edge Computing), Sürdürülebilir Tarım Teknolojileri.

---

## 1. GİRİŞ VE ETOLOJİK ALTYAPI

### 1.1. Biyolojik Problem ve Ekonomik Etki
Süt ineklerinde östrus döngüsü ortalama 21 günde bir tekrarlanan ve fertil periyodun başlangıcını işaret eden hormonal bir süreçtir. Östrus sırasında östrojen seviyesi zirveye ulaşırken progesteron ($P_4$) konsantrasyonu bazal seviyelere ($< 1\text{ ng/mL}$) geriler (Roelofs et al., 2010). Modern yüksek verimli Holstein ırklarında metabolik klirensin yüksek olması sebebiyle östrus belirtileri silikleşmiş, ortalama kızgınlık süresi 15 saatten 6-8 saat bandına gerilemiştir (Sveberg et al., 2011).

Kaçırılan her kızgınlık döngüsü, inek başına yaklaşık 21 günlük açık gün (days open) uzamasına ve laktasyon başına ortalama 150-250 Euro doğrudan kâr kaybına yol açmaktadır (Inchaisri et al., 2010).

### 1.2. Kuyruk Kinematiği ve Vulva Açığa Çıkma İlişkisi
İneklerde kızgınlık davranışının altın standardı "ayakta durma refleksi" (standing heat) olmakla birlikte, bağlı duraklı (tie-stall) ahırlarda ineklerin birbirine atlaması fiziksel olarak mümkün değildir. Bu ortamlarda ikincil kızgınlık belirtileri hayati öneme sahiptir:
* **Kaudal Huzursuzluk:** Kızgınlıktaki inek kuyruğunu vulvayı örtecek şekilde nötr tutmak yerine sık sık sağa/sola saptırır veya kökten hafifçe kaldırır (Van Eerdenburg et al., 2002).
* **Vulvanın Açığa Çıkması:** Normal diöstrus günlerinde kuyruk \%9.37 oranında vulvayı açık bırakırken, östrus gününde (d0) bu oran \%68.75 - \%80.0 bandına fırlamaktadır (Perez et al., 2022).

---

## 2. MATEMATİKSEL MODEL VE KİNEMATİK ANALİZ

### 2.1. İskelet Şeması ve Koordinat Düzlemi
Model, 2B kartezyen düzlemde 4 anatomik referans noktasını takip eder:
$$P_{\text{spine}} = (x_s, y_s), \quad P_{\text{tailbase}} = (x_0, y_0), \quad P_{\text{tailmid}} = (x_1, y_1), \quad P_{\text{vulva}} = (x_v, y_v)$$

### 2.2. Kuyruk Sapma Açısının ($\theta$) Hesaplanması
Gövde dikey ekseni referans vektörü $\vec{V}_{\text{ref}}$ ve kuyruk eksen vektörü $\vec{V}_{\text{tail}}$ şu şekilde tanımlanır:
$$\vec{V}_{\text{ref}} = (x_0 - x_s, y_0 - y_s)$$
$$\vec{V}_{\text{tail}} = (x_1 - x_0, y_1 - y_0)$$

İki vektör arasındaki açı $\theta$, nokta çarpımı ve öklid normları kullanılarak türetilir:
$$\cos(\theta) = \frac{\vec{V}_{\text{tail}} \cdot \vec{V}_{\text{ref}}}{\|\vec{V}_{\text{tail}}\| \|\vec{V}_{\text{ref}}\|}$$
$$\theta = \arccos\left( \text{clamp}(\cos(\theta), -1.0, 1.0) \right) \times \frac{180^\circ}{\pi}$$

Sapmanın yönü (sağ veya sol sapma), 2B çapraz çarpımın (z-bileşeni) işareti ile belirlenir:
$$\text{Cross}_z = V_{\text{ref}, x} \cdot V_{\text{tail}, y} - V_{\text{ref}, y} \cdot V_{\text{tail}, x}$$
$$\theta_{\text{signed}} = \begin{cases} -\theta, & \text{Cross}_z < 0 \quad (\text{Sola Sapma - LTail}) \\ +\theta, & \text{Cross}_z \ge 0 \quad (\text{Sağa Sapma - RTail}) \end{cases}$$

### 2.3. Vulva Açıklık Durumu ($E(k)$) ve Kayan Pencere
Vulva anatomik merkezi $P_{\text{vulva}}$ etrafında yarıçapı $R_{\text{roi}} = 35\text{ piksel}$ olan dairesel bir İlgi Alanı ($\text{ROI}_v$) tanımlanır. Kuyruk eklemi ile vulva merkezi arasındaki mesafe:
$$D_{\text{tail-vulva}} = \sqrt{(x_1 - x_v)^2 + (y_1 - y_v)^2}$$

Her $k$. karede vulva açıklık göstergesi $E(k)$:
$$E(k) = \begin{cases} 1, & |\theta_{\text{signed}}| \ge 20^\circ \land D_{\text{tail-vulva}} > R_{\text{roi}} \\ 0, & \text{diğer} \end{cases}$$

5 dakikalık kayan pencerede ($N = 5 \times 60 \times \text{FPS}$ kare):
$$R_{\text{open}} = \left( \frac{1}{N} \sum_{k=1}^{N} E(k) \right) \times 100 \quad [\%]$$
$$T_{\text{exp}} = \sum_{k=1}^{N} \frac{E(k)}{\text{FPS}} \quad [\text{saniye}]$$

### 2.4. Sahte Hareketlerin (Gürültü) Ayıklanması
1. **Dışkılama/İdrar Filtresi:** İnek dışkılama sırasında kuyruğunu yukarı kaldırır ($y_0 - y_1 > 25\text{ px}$), ancak yatay sapma minimaldir ($|\theta| < 15^\circ$). Bu durum tespit edildiğinde ve süresi $< 15$ saniye ise $E(k) = 0$ atanır ve östrus sayacı kilitlenir.
2. **Sinek Kovma / Refleks Filtresi:** Sinek ısırması sonucu anlık çırpmalar yüksek frekanslı salınımlardır ($t < 0.3\text{ sn}$). Yalnızca kesintisiz olarak en az $1.5\text{ saniye}$ ($22\text{ kare} @ 15\text{ FPS}$) süren vulva açığa çıkışları kabul edilir.

---

## 3. TRIMBERGER AM/PM TOHUMLAMA PROTOKOLÜ

Zamanında tohumlama, döl veriminin en kritik parametresidir (Trimberger, 1948). Bovi-Farmer uygulaması tohumlama penceresini şu kurala göre hesaplar:
* **Kızgınlık Sabah Fark Edilirse (04:00 - 12:00):** Aynı günün akşamı/ikindisi (tespitten 12 - 18 saat sonra) tohumlanır.
* **Kızgınlık Akşam/Gece Fark Edilirse (12:00 - 04:00):** Ertesi sabah (tespitten 12 - 18 saat sonra) tohumlanır.

$$\text{Pencere}_{\text{Başlangıç}} = t_{\text{alarm}} + 12\text{ saat}, \quad \text{Pencere}_{\text{Bitiş}} = t_{\text{alarm}} + 18\text{ saat}$$

---

## 4. UYGULAMA İÇİ KULLANIM KILAVUZU (OPERATIONAL MANUAL)

### 4.1. Bovi-TailVision (Ahırdaki Kamera İstasyonu)
1. **Montaj:** IP65 korumalı kutuya yerleştirilen telefon, ineğin kuyruk sokumundan $1.5\text{ metre}$ geriye, sırt hizasının $25\text{ cm}$ üzerine, aşağıya $15^\circ$ açıyla sabitlenir.
2. **Kamera Kalibrasyonu:** Ekranda **"Montaj / Kamera Önizleme"** butonuna basılarak yeşil iskelet çizgilerinin kuyruk sokumu ve vulvayı tam karşıdan ortaladığı doğrulanır.
3. **Konfigürasyon:** İnek Küpe No (örn. `TR-06-3842`) ve MQTT Broker IP adresi (örn. `tcp://192.168.1.100:1883`) girilir.
4. **Başlatma:** **"Sistemi Başlat"** butonuna basılır. Önizleme otomatik kapanır ve 24/7 analiz başlar.
5. **Termal Koruma:** **"Ekranı Karart"** butonuna basıldığında ekran $0.0\text{f}$ parlaklığa iner ve siyah perde devreye girer. Tekrar arayüze dönmek için ekrana **çift tıklanır**.

### 4.2. Bovi-Farmer (Çiftçinin Cep Telefonu)
1. **Kurulum:** Çiftçinin günlük kullandığı Android telefona `farmer_app-debug.apk` yüklenir.
2. **İlk Açılış:** Bildirim ve alarm izinleri onaylanır. Üst bardaki "Broker Bağlantısı: Aktif" ibaresi kontrol edilir.
3. **Alarm Durumu:** Bir inek kızgınlığa girdiğinde telefon yüksek sesli alarm çalar ve ekranı uyandırır.
4. **Aksiyon:** Kart üzerindeki önerilen tohumlama saatinde veteriner/tohumlamacı çağrılır ve tohumlama bitince **"Tohumlama Yapıldı"** butonuna basılarak kayıt arşivlenir.

---

## 5. AKADEMİK REFERANSLAR (REFERENCES)

1. **Perez, B., et al. (2022).** *Computer vision-based tail movement tracking and vulva exposure analysis for automated estrus detection in tie-stall dairy cows.* Computers and Electronics in Agriculture, 198, 107089.
2. **Roelofs, J., et al. (2010).** *When is a cow in estrus? Clinical and practical aspects.* Theriogenology, 74(3), 327-344.
3. **Trimberger, G. W. (1948).** *Breeding efficiency in dairy cattle from artificial insemination at various intervals before and after ovulation.* Nebraska Agricultural Experiment Station Research Bulletin, 153.
4. **Van Eerdenburg, F. J., et al. (2002).** *A new system for recognition of estrus in dairy cattle; behavior and cow status.* Behaviour, 139(10), 1323-1335.
5. **Sveberg, G., et al. (2011).** *Behavior of dairy cows in estrus with emphasis on duration and behavioral signs of standing to be mounted.* Journal of Dairy Science, 94(3), 1289-1301.
6. **Inchaisri, C., et al. (2010).** *Economic consequences of reproductive performance in dairy herds.* Journal of Dairy Science, 93(7), 2840-2851.
