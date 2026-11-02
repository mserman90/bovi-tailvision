package com.bovivision.farmer.boviscan.domain.model

enum class BovineMeasurementMode {
    HANDHELD_RAPID_SCAN,   // Elde hızlı tarama (Tohumlama takvimi + Flank solunumu + Vulva morfolojisi)
    FULL_MULTIMODAL,       // Tam çok modlu (Mikrofon rumen geviş dinleme + Kamera optik solunum + İvmeölçer)
    VULVA_OPTICAL_CHECK,   // Yalnızca kamera ile vulva açıklık/kuruluk ve mukus kontrolü
    ACOUSTIC_RUMINATION    // Yalnızca göğüs/açlık çukuru geviş çiğneme akustiği
}
