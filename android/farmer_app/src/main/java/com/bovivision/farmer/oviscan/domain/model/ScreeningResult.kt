package com.bovivision.farmer.oviscan.domain.model

data class ScreeningResult(
    val earTag: String,
    val probability: Float,
    val riskLevel: PregnancyRiskLevel,
    val confidence: Float,
    val audioScore: Float,
    val motionScore: Float,
    val cameraScore: Float?,
    val morphologyScore: Float?,
    val xaiReasons: List<String>,
    val measurementDurationSec: Int,
    val mode: MeasurementMode,
    val timestamp: Long = System.currentTimeMillis()
)
