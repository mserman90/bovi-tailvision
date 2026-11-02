package com.bovivision.farmer.boviscan.domain.model

data class BovineScreeningResult(
    val earTag: String,
    val probability: Float,
    val riskLevel: BovinePregnancyRiskLevel,
    val daysSinceInsemination: Int,
    val xaiReasons: List<String>,
    val veterinaryRecommendation: String,
    val optimalUltrasoundWindow: String
)
