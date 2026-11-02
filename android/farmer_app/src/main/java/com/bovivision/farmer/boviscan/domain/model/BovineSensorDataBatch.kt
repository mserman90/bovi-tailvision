package com.bovivision.farmer.boviscan.domain.model

data class BovineSensorDataBatch(
    val earTag: String,
    val daysSinceInsemination: Int,
    val mode: BovineMeasurementMode = BovineMeasurementMode.HANDHELD_RAPID_SCAN,
    
    // 1. Akustik (Mikrofon): Rumen geviş çiğneme frekansı ve bolus kalitesi
    val chewsPerMinute: Int = 52,             // İneklerde geviş çiğneme sayısı (Normal: 45-60 / dk)
    val audioSnrDb: Float = 14.5f,            // Rumen geviş ses netliği
    val vocalizationDetected: Boolean = false, // Östrus böğürmesi / huzursuzluk sesi var mı?
    
    // 2. Optik Solunum (Kamera - Paralumbar Fossa): Solunum sayısı (BPM)
    val cameraRespirationBpm: Float = 22.0f,  // Yetişkin inek istirahat solunumu (Normal: 15-28 BPM)
    
    // 3. Vulva & Kuyruk Morfolojisi (Kamera): 0.0f (açık/ödemli/akıntılı) -> 1.0f (kapalı/kuru/soluk)
    val vulvaTightnessScore: Float = 0.85f,
    
    // 4. İvmeölçer (Telefon Hayvanın Sırtına/Bölmesine Konduğunda): Aktivite & Yatma oranı
    val motionRestingIndex: Float = 0.78f,    // 0.0 = yüksek ajitasyon/ayakta, 1.0 = sakin/yatıyor
    
    // 5. Takvim Verisi: 21 ve 42 günlük döngü durumu
    val hadObservedEstrusSinceAI: Boolean = false
)
