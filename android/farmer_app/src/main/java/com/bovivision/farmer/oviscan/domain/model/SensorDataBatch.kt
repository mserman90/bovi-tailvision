package com.bovivision.farmer.oviscan.domain.model

data class SensorDataBatch(
    val earTag: String,
    val daysSinceInsemination: Int,
    val breed: String = "Akkaraman",
    val ageYears: Int = 3,
    val mode: MeasurementMode = MeasurementMode.SPOT_CHECK,

    // 1. Dahili Mikrofon Özellikleri (AudioRecord 44.1 kHz Mono)
    val audioDurationSeconds: Float = 30f,
    val audioAverageRms: Float = 0.04f,
    val audioPeakRms: Float = 0.18f,
    val audioSnrDb: Float = 14.5f,
    val acousticEventCount: Int = 3, // Sakin geviş getirme patlaması
    val mfccCoefficients: FloatArray = FloatArray(13),
    val estimatedPitchHz: Float = 180f, // <400Hz sakin rumen tonu
    val harmonicToNoiseRatio: Float = 8.5f,

    // 2. Dahili İvmeölçer Özellikleri (SensorManager 50 Hz)
    val motionDurationSeconds: Float = 30f,
    val motionAverageMagnitude: Float = 0.65f, // Sakin duruş
    val motionStdDev: Float = 0.12f,
    val motionActivityIndex: Float = 0.28f,
    val motionEntropy: Float = 1.15f,
    val lyingRatio: Float = 0.45f,

    // 3. Dahili Kamera Özellikleri (Optik Solunum ve Karın Salınımı)
    val cameraRespirationBpm: Float? = 30.5f, // 26-38 BPM gebelik metabolik artışı
    val cameraFlankExpansionRatio: Float? = 1.18f
)
