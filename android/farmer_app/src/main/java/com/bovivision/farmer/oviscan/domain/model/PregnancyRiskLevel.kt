package com.bovivision.farmer.oviscan.domain.model

enum class PregnancyRiskLevel(
    val titleTr: String,
    val descriptionTr: String,
    val hexColor: Long,
    val recommendedActionTr: String
) {
    HIGH(
        titleTr = "Gebe Olma Olasılığı Yüksek",
        descriptionTr = "Sensör sinyalleri ve tohumlama sonrası davranış örüntüsü gebelik ile yüksek derecede uyumlu.",
        hexColor = 0xFF2E7D32, // Yeşil
        recommendedActionTr = "Sürü yönetiminde gebe rasyonuna geçiş planlayınız. Doğrulama için veteriner hekim ultrason randevusu alınız."
    ),
    INCONCLUSIVE(
        titleTr = "Belirsiz / Tekrar Tarama Gerekli",
        descriptionTr = "Sinyaller kesin ayrım için yetersiz; fizyolojik parametreler ara değer aralığında.",
        hexColor = 0xFFF57F17, // Turuncu
        recommendedActionTr = "7–10 gün sonra ölçümü tekrarlayınız veya şüpheli durumlarda doğrudan veteriner hekim muayenesine başvurunuz."
    ),
    LOW(
        titleTr = "Gebe Olma Olasılığı Düşük (Boş İhtimali)",
        descriptionTr = "Hareket ve akustik sinyaller östrus döngüsünün devam ettiğine veya gebeliğin şekillenmediğine işaret ediyor.",
        hexColor = 0xFFC62828, // Kırmızı
        recommendedActionTr = "Hayvanın tekrar kızgınlık (östrus) belirtilerini takip ediniz ve yeniden tohumlama protokolü için veteriner hekime danışınız."
    );

    companion object {
        fun fromProbability(probability: Float): PregnancyRiskLevel {
            return when {
                probability >= 0.70f -> HIGH
                probability >= 0.30f -> INCONCLUSIVE
                else -> LOW
            }
        }
    }
}
