package com.bovivision.farmer.oviscan.domain.model

enum class PregnancyRiskLevel(
    val titleTr: String,
    val descriptionTr: String,
    val recommendedActionTr: String,
    val hexColor: String
) {
    HIGH(
        titleTr = "YÜKSEK GEBELİK OLASILIĞI",
        descriptionTr = "Sensör sinyalleri ve tohumlama sonrası davranış örüntüsü gebelik ile yüksek derecede uyumludur.",
        recommendedActionTr = "Sürü yönetiminde gebe rasyonuna geçiş planlayınız. Doğrulama için veteriner hekim ultrason randevusu alınız.",
        hexColor = "#225633" // OviScan Muted Earthy Green
    ),
    INCONCLUSIVE(
        titleTr = "ŞÜPHELİ / BELİRSİZ (TEKRAR TEST)",
        descriptionTr = "Biyolojik ve akustik göstergeler sınır değerdedir. Erken dönem veya gürültülü ortam nedeniyle kesin karara varılamamıştır.",
        recommendedActionTr = "7 ila 10 gün sonra sakin bir saatte taramayı tekrarlayınız veya veteriner hekime danışınız.",
        hexColor = "#825412" // OviScan Warm Ochre Amber
    ),
    LOW(
        titleTr = "DÜŞÜK GEBELİK (BOŞ İHTİMALİ)",
        descriptionTr = "Sık meleme, ajitasyon veya normal geviş getirme ritminin dışındaki örüntüler gebelik olasılığını düşürmektedir.",
        recommendedActionTr = "Hayvanı tekrar kızgınlık (östrus) takibine alınız ve yeniden tohumlama/koç katımı planlayınız.",
        hexColor = "#862B23" // OviScan Earthy Terra-cotta Red
    )
}
