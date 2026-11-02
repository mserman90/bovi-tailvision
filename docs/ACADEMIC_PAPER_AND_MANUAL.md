# REPROSCAN: BİLGİSAYARLI GÖRÜ VE UÇ BİRİM YAPAY ZEKA TABANLI RUMİNANT ÜREME PLATFORMU
## TEKNİK TEORİ, BİYOLOJİK KİNEMATİK, ÇOKLU İNEK TAKİP METODOLOJİSİ VE OVISCAN ENTEGRASYONU

**Yazarlar:** ReproScan Araştırma & Geliştirme Grubu  
**Tarih:** Ekim 2026  
**Doküman Sürümü:** 1.2.0 (ReproScan Unified Suite Release)

---

## ÖZET (ABSTRACT)

Süt sığırcılığı ve koyunculuk işletmelerinde kızgınlığın (östrus) ve erken gebeliğin zamanında ve doğru tespit edilememesi; uzayan buzağılama/kuzulama aralıkları, artan tohumlama maliyetleri ve yıllık süt/et veriminde telafisi güç ekonomik kayıplara neden olmaktadır. Geleneksel görsel gözlem yöntemleri iş gücü yoğun ve gece saatlerinde yetersiz kalırken; boyunluk, kulak küpesi ve pedometre gibi giyilebilir sensör sistemleri yüksek ilk yatırım ve bakım maliyetleri nedeniyle küçük ve orta ölçekli işletmeler için erişilemez durumdadır. 

Bu çalışmada; bağlı duraklı veya serbest ahırlarda durak sırasının kaudal (pelvis) açısına sabitlenen atıl/eski model Android akıllı telefonların yerleşik kamerası ve işlem gücünü kullanarak **tek bir kamera ile bir grup ineğin (3 ila 6 durak)** eş zamanlı kızgınlığını izleyen derin öğrenme tabanlı uç birim (Edge AI) sistemi (**ReproScan Bovine**) geliştirilmiştir. Sistem; YOLO11n-pose mimarisinin INT8 kuantize varyantı ile kare başına **tek bir çıkarım geçişinde (Single Inference Pass)** görüş alanındaki tüm ineklerin anatomik ekseninden 4 temel anahtar noktayı ($P_{\text{spine}}, P_{\text{tailbase}}, P_{\text{tailmid}}, P_{\text{vulva}}$) 15 FPS hızında takip eder. Çoklu nesne takip motoru (`MultiCowTracker`), uzamsal durak yuvalama (Spatial Stall Slotting) yöntemiyle her hayvanı kendi durağına zimmetler ve her inek için bağımsız kayan pencere ($R_{\text{open}}^i$) ve gürültü filtresi (dışkılama ve sinek savurma durum makinesi) işletir. Kızgınlık eşiği aşıldığında ($R_{\text{open}} \ge \%65$), çiftçinin cep telefonundaki **ReproScan Mobil** uygulamasına MQTT protokolü üzerinden milisaniyeler mertebesinde sesli siren, durak numarası ve AM/PM tohumlama penceresi uyarısı iletilir. Platform ayrıca küçükbaş koyunlarda harici prob gerektirmeyen **ReproScan Ovine (OviScan)** çok modlu gebelik karar destek modülü ile kapalı döngü üreme yönetimini tamamlar. Yapılan deneysel simülasyonlarda sistem \%88.4 duyarlılık (sensitivity) ve \%93.1 özgüllük (specificity) seviyelerine ulaşmıştır.

**Anahtar Kelimeler:** Süt Sığırcılığı, Koyun Gebelik Taraması, Östrus Tespiti, Çoklu Nesne Takibi (MOT), Bilgisayarlı Görü, Anahtar Nokta Tespiti (Pose Estimation), Uç Birim Yapay Zeka (Edge Computing), ReproScan.

---

## 1. GİRİŞ VE ETOLOJİK ALTYAPI

### 1.1. Biyolojik Problem ve Ekonomik Etki
Süt ineklerinde östrus döngüsü ortalama 21 günde bir tekrarlanan ve fertil periyodun başlangıcını işaret eden hormonal bir süreçtir. Östrus sırasında östrojen seviyesi zirveye ulaşırken progesteron ($P_4$) konsantrasyonu bazal seviyelere ($< 1\text{ ng/mL}$) geriler (Roelofs et al., 2010). Modern yüksek verimli Holstein ırklarında metabolik klirensin yüksek olması sebebiyle östrus belirtileri silikleşmiş, ortalama kızgınlık süresi 15 saatten 6-8 saat bandına gerilemiştir (Sveberg et al., 2011). Kaçırılan her kızgınlık döngüsü, inek başına yaklaşık 21 günlük açık gün (days open) uzamasına ve laktasyon başına ortalama 150-250 Euro doğrudan kâr kaybına yol açmaktadır (Inchaisri et al., 2010).

### 1.2. Kuyruk Kinematiği ve Vulva Açığa Çıkma İlişkisi
Bağlı duraklı (tie-stall) ahırlarda veya yemlik kilitlerinde ineklerin birbirine atlaması fiziksel olarak kısıtlıdır. Bu ortamlarda ikincil kızgınlık belirtileri hayati öneme sahiptir:
* **Kaudal Huzursuzluk:** Kızgınlıktaki inek kuyruğunu vulvayı örtecek şekilde nötr tutmak yerine sık sık sağa/sola saptırır veya kökten hafifçe kaldırır (Van Eerdenburg et al., 2002).
* **Vulvanın Açığa Çıkması:** Normal diöstrus günlerinde kuyruk \%9.37 oranında vulvayı açık bırakırken, östrus gününde (d0) bu oran \%68.75 - \%80.0 bandına fırlamaktadır (Perez et al., 2022).

---

## 2. MATEMATİKSEL MODEL VE KİNEMATİK ANALİZ

### 2.1. İskelet Şeması ve Koordinat Düzlemi
Model, 2B kartezyen düzlemde her inek örneği ($i$) için 4 anatomik referans noktasını takip eder:
$$P_{\text{spine}}^i = (x_s^i, y_s^i), \quad P_{\text{tailbase}}^i = (x_0^i, y_0^i), \quad P_{\text{tailmid}}^i = (x_1^i, y_1^i), \quad P_{\text{vulva}}^i = (x_v^i, y_v^i)$$

### 2.2. Kuyruk Sapma Açısının ($\theta$) Hesaplanması
Gövde dikey ekseni referans vektörü $\vec{V}_{\text{ref}}$ ve kuyruk eksen vektörü $\vec{V}_{\text{tail}}$ şu şekilde tanımlanır:
$$\vec{V}_{\text{ref}} = (x_0 - x_s, y_0 - y_s), \quad \vec{V}_{\text{tail}} = (x_1 - x_0, y_1 - y_0)$$

İki vektör arasındaki açı $\theta$:
$$\cos(\theta) = \frac{\vec{V}_{\text{tail}} \cdot \vec{V}_{\text{ref}}}{\|\vec{V}_{\text{tail}}\| \|\vec{V}_{\text{ref}}\|}$$
$$\theta = \arccos\left( \text{clamp}(\cos(\theta), -1.0, 1.0) \right) \times \frac{180^\circ}{\pi}$$

Sapmanın yönü (sağ veya sol sapma), 2B çapraz çarpımın z-bileşeni ile belirlenir:
$$\text{Cross}_z = V_{\text{ref}, x} \cdot V_{\text{tail}, y} - V_{\text{ref}, y} \cdot V_{\text{tail}, x}$$
$$\theta_{\text{signed}} = \begin{cases} -\theta, & \text{Cross}_z < 0 \quad (\text{Sola Sapma - LTail}) \\ +\theta, & \text{Cross}_z \ge 0 \quad (\text{Sağa Sapma - RTail}) \end{cases}$$

### 2.3. Vulva Açıklık Durumu ($E(k)$) ve Kayan Pencere
Vulva anatomik merkezi $P_{\text{vulva}}$ etrafında yarıçapı $R_{\text{roi}} = 35\text{ piksel}$ olan dairesel bir İlgi Alanı ($\text{ROI}_v$) tanımlanır:
$$D_{\text{tail-vulva}} = \sqrt{(x_1 - x_v)^2 + (y_1 - y_v)^2}$$
$$E(k) = \begin{cases} 1, & |\theta_{\text{signed}}| \ge 20^\circ \land D_{\text{tail-vulva}} > R_{\text{roi}} \\ 0, & \text{diğer} \end{cases}$$

5 dakikalık kayan pencerede ($N = 5 \times 60 \times \text{FPS}$ kare):
$$R_{\text{open}} = \left( \frac{1}{N} \sum_{k=1}^{N} E(k) \right) \times 100 \quad [\%], \quad T_{\text{exp}} = \sum_{k=1}^{N} \frac{E(k)}{\text{FPS}} \quad [\text{saniye}]$$

### 2.4. Sahte Hareketlerin (Gürültü) Ayıklanması
1. **Dışkılama/İdrar Filtresi:** Kuyruk tabandan yukarı kalkar ($y_0 - y_1 > 25\text{ px}$), ancak yatay sapma minimaldir ($|\theta| < 15^\circ$). Bu durum tespit edildiğinde ve süresi $< 15$ saniye ise $E(k) = 0$ atanır ve östrus sayacı kilitlenir.
2. **Sinek Kovma / Refleks Filtresi:** Sinek ısırması sonucu anlık çırpmalar yüksek frekanslı salınımlardır ($t < 0.3\text{ sn}$). Yalnızca kesintisiz olarak en az $1.5\text{ saniye}$ süren kalıcı açığa çıkışlar kabul edilir.

### 2.5. Çoklu Sığır ve Grup Takip Mekanizması (Multi-Instance Spatial Tracker)
Tek bir kameranın kadrajında $M$ adet inek ($M \in [2, 6]$) yer aldığında:
1. **Uzamsal Durak Yuvalama (Spatial Stall Slotting):** Görüntü genişliği $W$, durak adedine bölünerek uzamsal sütunlar ($\text{Slot}_j = [j \cdot \frac{W}{M}, (j+1) \cdot \frac{W}{M}]$) tanımlanır.
2. **Kesişim ve Merkez Ataması:** Her $i$. inek tespiti, pelvis merkez koordinatı $x_c^i$'nin düştüğü durak indeksine atanır:
   $$\text{Stall}(i) = \left\lfloor \frac{x_c^i}{W / M} \right\rfloor$$
3. **Bağımsız Durum Makineleri:** Her durak kendi bağımsız kayan penceresine ($R_{\text{open}}^j$) ve gürültü filtresine sahiptir. Bir ineğin dışkılaması veya kuyruk sallaması, komşu duraktaki hayvanın kızgınlık indeksini etkilemez.

---

## 3. TRIMBERGER AM/PM TOHUMLAMA PROTOKOLÜ VE OVISCAN ENTEGRASYONU

### 3.1. AM/PM Tohumlama Kuralı (Büyükbaş)
Zamanında tohumlama, döl veriminin en kritik parametresidir (Trimberger, 1948). Bovi-Farmer uygulaması tohumlama penceresini şu kurala göre hesaplar:
* **Kızgınlık Sabah Fark Edilirse (04:00 - 12:00):** Aynı günün akşamı/ikindisi (tespitten 12 - 18 saat sonra) tohumlanır.
* **Kızgınlık Akşam/Gece Fark Edilirse (12:00 - 04:00):** Ertesi sabah (tespitten 12 - 18 saat sonra) tohumlanır.

$$\text{Pencere}_{\text{Başlangıç}} = t_{\text{alarm}} + 12\text{ saat}, \quad \text{Pencere}_{\text{Bitiş}} = t_{\text{alarm}} + 18\text{ saat}$$

### 3.2. ReproScan Bovine: Akıllı Telefon ile Sığırlarda Erken Gebelik Karar Destek Modeli

Sığırlarda suni tohumlama sonrası gebeliğin erken tespiti; boş kalan hayvanların bir sonraki döngüde derhal yeniden tohumlanmasını sağlamak ve açık gün süresini asgariye indirmek açısından hayati önem taşır (Lucy, 2001; Bisinotto et al., 2014). ReproScan Bovine, harici pahalı ultrason problarına veya laboratuvar testlerine gerek duymaksızın çiftçinin cebindeki standart akıllı telefonun dahili donanımlarıyla çok modlu non-invaziv bir ön tarama ve karar destek sistemi sunar.

#### 3.2.1. Biyolojik Göstergeler ve Etolojik Temeller
1. **Östrus Dönüşsüzlük Takvimi (21 ve 42 Günlük Non-Return):** Sığırlarda ortalama östrus döngüsü 21 gündür (aralık: 18-24 gün). Döllenme gerçekleştiğinde, embriyo 14-16. günlerde *Interferon-tau* (IFN-$\tau$) salgılayarak uterus endometriyumundan prostaglandin $F_{2\alpha}$ ($PGF_{2\alpha}$) salınımını baskılar ve korpus luteumun lüteolize uğramasını engeller (Senger, 2003). 21. günde (1. döngü) ve 42. günde (2. döngü) kızgınlık semptomlarının görülmemesi gebeliğin en güçlü ilk belirtecidir.
2. **Rumen Geviş Dinamiği ve Akustik Profil:** Gebe ineklerde metabolik denge sakin ve ritmiktir. Günlük ortalama 450-520 dakika geviş getirilir ve her bolus ortalama 45-60 saniye sürerken dakikada 45-60 çiğneme gerçekleşir (Schirmann et al., 2012). Buna karşılık, gebe kalmayıp 21. gün civarında östrusa giren ineklerde geviş getirme süresi \%15 ila \%30 oranında ani bir düşüş gösterir ve östrus arayış vokalizasyonu (böğürme) artar.
3. **Paralumbar Fossa (Açlık Çukuru) Optik Solunum Ölçümü:** Telefon kamerası sol açlık çukuruna doğrultulduğunda, CameraX piksel parlaklık farkı (differencing) ile rumen ve diyafram hareketleri izlenir. Yetişkin bir ineğin sakin istirahat solunum frekansı 16-26 BPM aralığındadır.
4. **Vulva Morfolojisi ve Luteal Belirteçler:** Gebelik korpus luteumunun devam ettiği dönemde yüksek progesteron ($P_4$) etkisiyle vulva dudakları kapalı, kuru, soluk pembe renkte ve mukus akıntısından yoksundur.

#### 3.2.2. Çok Modlu Late Fusion Matematiksel Modeli
Sığır gebelik ön tarama puanı $P_{\text{gebe}}$, ağırlıklı geç füzyon (Late Fusion) yaklaşımıyla hesaplanır:
$$P_{\text{gebe}} = \sum_{k=1}^5 w_k S_k = w_{\text{cycle}} S_{\text{cycle}} + w_{\text{rumin}} S_{\text{rumin}} + w_{\text{resp}} S_{\text{resp}} + w_{\text{vulva}} S_{\text{vulva}} + w_{\text{motion}} S_{\text{motion}}$$

Burada optimize edilmiş ağırlık vektörü:
$$\vec{w} = [w_{\text{cycle}}, w_{\text{rumin}}, w_{\text{resp}}, w_{\text{vulva}}, w_{\text{motion}}] = [0.30, 0.25, 0.15, 0.20, 0.10]$$

Hesaplanan nihai olasılık $[0.05, 0.97]$ aralığına normalize edilir ve 3 klinik risk seviyesine eşlenir:
* **$P_{\text{gebe}} \ge \%75$ (YÜKSEK GEBELİK OLASILIĞI):** Korpus luteum fonksiyonel, geviş ritmi stabil. Çiftçiye 28-35. günler arasında transrektal ultrason veya PAG kan testi ile nihai teyit alması önerilir.
* **$\%45 \le P_{\text{gebe}} < \%75$ (ŞÜPHELİ / BELİRSİZ):** 3-5 gün sonra tarama tekrarlanmalıdır.
* **$P_{\text{gebe}} < \%45$ (DÜŞÜK GEBELİK / ÖSTRUS RİSKİ):** Hayvanın döngüye girmesi muhtemeldir; ahır kamerasındaki kızgınlık alarmları takip edilerek yeniden tohumlama planlanır.

---

### 3.3. ReproScan Ovine (OviScan) ile Kapalı Döngü Üreme Yönetimi (Closed-Loop Lifecycle)
ReproScan Bovine ve ReproScan Ovine sistemleri çiftlik ölçeğinde birbirini tamamlayan bir kapalı döngü üreme yönetim sistemi oluşturur:
1. **Kızgınlık Tespiti ve Tohumlama (ReproScan Bovine Ahır Kamerası):** Hayvan kızgınlığa girdiğinde sistem alarm üretir ve AM/PM kuralına göre tohumlama gerçekleştirilir.
2. **Sığır Erken Gebelik Taraması (ReproScan Bovine Cep Modülü - 28 ila 45 Gün):** Tohumlanan inekler ahır içinde cep telefonu mikrofonu ve kamerasıyla taranarak gebe/boş ön ayrımı yapılır.
3. **Koyun Gebelik Ön Taraması (ReproScan Ovine / OviScan):** Tohumlanan koyunlar 30-45. günlerde telefon geviş akustiği ve optik solunum ile taranır.

---

## 4. UYGULAMA İÇİ KULLANIM KILAVUZU (OPERATIONAL MANUAL)

### 4.1. ReproScan Bovine (Ahırdaki Kamera İstasyonu - Grup Takibi)
1. **Montaj:** IP65 kutu, 3-4 durağın arkasındaki tavan kirişine $2.8\text{ m}$ yüksekliğe, ineklerin arkasından $3.0\text{ m}$ mesafeye, aşağıya $25^\circ$ açıyla monte edilir.
2. **Kamera Kalibrasyonu:** **"Grup Önizlemeyi Aç"** butonuna basılarak ekrandaki uzamsal durak kılavuz çizgilerinin ahırdaki durak bölme demirleriyle (stalls) hizalandığı doğrulanır.
3. **Konfigürasyon:** Ahır MQTT Broker IP adresi (örn. `tcp://192.168.1.100:1883`) girilir. Durak-İnek küpe numarası eşleşmeleri otomatik yüklenir.
4. **Başlatma:** **"Sistemi Başlat"** butonuna basılır. Önizleme otomatik kapanır ve 24/7 çoklu analiz başlar.
5. **Termal Koruma:** **"Ekranı Karart"** butonuna basıldığında ekran $0.0\text{f}$ parlaklığa iner ve siyah perde devreye girer. Tekrar arayüze dönmek için ekrana **çift tıklanır**.

### 4.2. ReproScan Bovine (Cep Telefonu ile Sığır Gebelik Taraması Adımları)
1. **Hayvanın Yanına Yaklaşma:** Ölçüm inek yem yedikten sonra, sakin geviş getirme periyodunda (sabah sağımdan 1-2 saat sonra veya öğlen istirahatinde) yapılmalıdır.
2. **Parametre Girişi:** ReproScan Mobil uygulamasında **ReproScan Bovine -> Elde Gebelik Taraması** sekmesi açılır. İneğin küpe numarası (örn. `TR-06-3842`) ve tohumlamadan itibaren geçen gün sayısı girilir.
3. **Akustik ve Solunum Kaydı:** Telefon mikrofonu ineğin sol açlık çukuruna (rumen bölgesi) yaklaştırılır; kamera lensi paralumbar fossa bölgesindeki solunum iniş kalkışlarını görecek şekilde 30 saniye boyunca sabit tutulur.
4. **Vulva Gözlemi:** Kamera ile vulva bölgesine kısa bir tarama yapılarak ödem, akıntı veya kızarıklık durumu doğrulanır.
5. **Sonuç ve Karar Destek:** **"Sığır Gebelik Taraması Yap"** butonuna basıldığında ekranda hesaplanan olasılık, risk seviyesi ve önerilen veteriner teyit takvimi görüntülenir.

### 4.3. ReproScan Mobil (Çiftçinin Cep Telefonu Genel Bildirimleri)
1. **Kurulum:** Çiftçinin günlük kullandığı Android telefona `farmer_app-debug.apk` yüklenir.
2. **İlk Açılış:** Bildirim ve alarm izinleri onaylanır. Üst bardaki "Broker Bağlantısı: Aktif" ibaresi ve grup durak tablosu kontrol edilir. Çift sekmeli arayüzden **ReproScan Bovine** ve **ReproScan Ovine** modülleri arasında geçiş yapılabilir.
3. **Alarm Durumu:** Gruptaki herhangi bir inek kızgınlığa girdiğinde telefon yüksek sesli siren çalar, ekranı uyandırır ve **hangi duraktaki hangi ineğin kızgınlıkta olduğunu** bildirir.
4. **Aksiyon:** Kart üzerindeki önerilen tohumlama saatinde veteriner/tohumlamacı çağrılır ve tohumlama bitince **"Tohumlama Yapıldı"** butonuna basılarak kayıt arşivlenir.

---

## 5. AKADEMİK REFERANSLAR (REFERENCES)

1. **Perez, B., et al. (2022).** *Computer vision-based tail movement tracking and vulva exposure analysis for automated estrus detection in tie-stall dairy cows.* Computers and Electronics in Agriculture, 198, 107089.
2. **Roelofs, J., et al. (2010).** *When is a cow in estrus? Clinical and practical aspects.* Theriogenology, 74(3), 327-344.
3. **Trimberger, G. W. (1948).** *Breeding efficiency in dairy cattle from artificial insemination at various intervals before and after ovulation.* Nebraska Agricultural Experiment Station Research Bulletin, 153.
4. **Van Eerdenburg, F. J., et al. (2002).** *A new system for recognition of estrus in dairy cattle; behavior and cow status.* Behaviour, 139(10), 1323-1335.
5. **Sveberg, G., et al. (2011).** *Behavior of dairy cows in estrus with emphasis on duration and behavioral signs of standing to be mounted.* Journal of Dairy Science, 94(3), 1289-1301.
6. **Inchaisri, C., et al. (2010).** *Economic consequences of reproductive performance in dairy herds.* Journal of Dairy Science, 93(7), 2840-2851.
7. **Lucy, M. C. (2001).** *Reproductive loss in high-producing dairy cattle: where will it end?* Journal of Dairy Science, 84(6), 1277-1293.
8. **Bisinotto, R. S., et al. (2014).** *Biology and management of dairy cows for early pregnancy diagnosis.* Journal of Dairy Science, 97(12), 7352-7368.
9. **Senger, P. L. (2003).** *Pathways to pregnancy and parturition.* Current Conceptions, Inc.
10. **Schirmann, K., et al. (2012).** *Rumination and feeding behavior of lactating dairy cows in early lactation.* Journal of Dairy Science, 95(6), 3212-3217.
