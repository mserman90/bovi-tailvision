package com.bovivision.farmer.boviscan.domain.model

enum class BovinePregnancyRiskLevel(
    val titleTr: String,
    val descriptionTr: String,
    val recommendedActionTr: String,
    val hexColor: String
) {
    HIGH(
        titleTr = "YÜKSEK GEBELİK OLASILIĞI",
        descriptionTr = "21 ve 42 günlük östrus geri dönmeme (non-return) döngüleri aşılmış, geviş ritmi stabil, vulva morfolojisi kuru/kapalı ve dinlenme seviyesi gebelikle yüksek uyumludur.",
        recommendedActionTr = "Sürü yönetiminde hayvanı 'Olası Gebe' grubuna aktarınız. 28-35. günler arasında kesin teyit için transrektal veteriner ultrasonu veya PAG (Gebelik İlişkili Glikoprotein) kan testi planlayınız.",
        hexColor = "#225633" // OviScan Muted Earthy Green
    ),
    INCONCLUSIVE(
        titleTr = "ŞÜPHELİ / BELİRSİZ (TEKRAR TEST)",
        descriptionTr = "Biyolojik ve akustik göstergeler sınır değerdedir. Erken embriyonik dönem veya düzensiz geviş örüntüsü nedeniyle kesin karara varılamamıştır.",
        recommendedActionTr = "3 ila 5 gün sonra sakin bir saatte taramayı tekrarlayınız. Ahırdaki sabit ReproScan Bovine kamerasından hayvanın kızgınlık/kuyruk hareketlerini yakından izleyiniz.",
        hexColor = "#825412" // OviScan Warm Ochre Amber
    ),
    LOW(
        titleTr = "DÜŞÜK GEBELİK / KIZGINLIK TEKRARI RİSKİ",
        descriptionTr = "Geviş süresinde belirgin düşüş, artan ajitasyon/hareketlilik veya vulva bölgesinde östrus belirtileri (akıntı/kızarıklık) tespit edilmiştir. Hayvanın döngüye girmesi kuvvetle muhtemeldir.",
        recommendedActionTr = "Hayvanı 'Boş / Olası Kızgınlık' statüsüne alınız. Ahır kamerasındaki durak kızgınlık alarmlarını takip ederek yeni tohumlama için veteriner hekimle iletişime geçiniz.",
        hexColor = "#862B23" // OviScan Earthy Terra-cotta Red
    )
}
