package com.bovivision.farmer.boviscan.ml

import com.bovivision.farmer.boviscan.domain.model.BovinePregnancyRiskLevel
import com.bovivision.farmer.boviscan.domain.model.BovineScreeningResult
import com.bovivision.farmer.boviscan.domain.model.BovineSensorDataBatch
import kotlin.math.max
import kotlin.math.min

/**
 * ReproScan Bovine Smartphone Decision Support
 * Harici prob gerektirmeksizin, standart akıllı telefonun dahili donanımları (mikrofon, kamera,
 * ivmeölçer) ve tohumlama takvimini birleştirerek süt ve besi sığırlarında non-invaziv
 * erken gebelik ön tarama skorlaması yapar.
 */
object BovineLateFusionClassifier {

    private const val WEIGHT_CYCLE = 0.30f
    private const val WEIGHT_RUMINATION = 0.25f
    private const val WEIGHT_RESPIRATION = 0.15f
    private const val WEIGHT_VULVA = 0.20f
    private const val WEIGHT_MOTION = 0.10f

    fun classify(batch: BovineSensorDataBatch): BovineScreeningResult {
        val xaiList = mutableListOf<String>()

        // 1. Tohumlama Sonrası Takvim ve Non-Return Analizi (Sığırlarda 21 günlük östrus döngüsü)
        val cycleScore: Float
        when {
            batch.hadObservedEstrusSinceAI -> {
                cycleScore = 0.10f
                xaiList.add("Tohumlama Takvimi: Tohumlama sonrası kızgınlık/döngü tespiti bildirildi (gebelik olasılığı asgari).")
            }
            batch.daysSinceInsemination in 21..35 -> {
                cycleScore = 0.88f
                xaiList.add("Tohumlama Takvimi: ${batch.daysSinceInsemination}. gün (1. östrus döngüsü [21. gün] kızgınlık görülmeden aşılmış; IFN-tau maternal tanıma pozitif).")
            }
            batch.daysSinceInsemination in 36..60 -> {
                cycleScore = 0.94f
                xaiList.add("Tohumlama Takvimi: ${batch.daysSinceInsemination}. gün (2. östrus döngüsü [42. gün] de aşılmış; kalıcı korpus luteum göstergesi).")
            }
            batch.daysSinceInsemination < 18 -> {
                cycleScore = 0.40f
                xaiList.add("Tohumlama Takvimi: Erken dönem (<18 gün); fizyolojik olarak diöstrus ile gebelik ayrımı için erkendir.")
            }
            else -> {
                cycleScore = 0.70f
                xaiList.add("Tohumlama Takvimi: ${batch.daysSinceInsemination}. gün (İleri gebelik periyodu).")
            }
        }

        // 2. Akustik Analiz (Mikrofon): Rumen geviş çiğneme frekansı (bolus) ve böğürme tespiti
        val ruminationScore: Float
        if (batch.vocalizationDetected) {
            ruminationScore = 0.20f
            xaiList.add("Dahili Mikrofon: İnekte böğürme/vokalizasyon tespit edildi (östrus arayış huzursuzluğu belirtisi).")
        } else if (batch.chewsPerMinute in 45..62 && batch.audioSnrDb > 10.0f) {
            ruminationScore = 0.88f
            xaiList.add("Dahili Mikrofon: Düzenli geviş çiğneme ritmi (${batch.chewsPerMinute} çiğneme/dk, SNR: ${String.format("%.1f", batch.audioSnrDb)} dB) - stabil metabolizma.")
        } else if (batch.chewsPerMinute < 35) {
            ruminationScore = 0.32f
            xaiList.add("Dahili Mikrofon: Rumen geviş çiğneme hızında belirgin düşüş (${batch.chewsPerMinute}/dk) - östrus öncesi iştah/geviş azalması şüphesi.")
        } else {
            ruminationScore = 0.55f
            xaiList.add("Dahili Mikrofon: Rumen geviş akustiği nötr aralıkta (${batch.chewsPerMinute}/dk).")
        }

        // 3. Optik Solunum (Kamera - Paralumbar Fossa): İstirahat solunum frekansı
        val respirationScore: Float
        if (batch.cameraRespirationBpm in 16.0f..26.0f) {
            respirationScore = 0.85f
            xaiList.add("Dahili Kamera: Paralumbar fossa optik solunum frekansı ${String.format("%.1f", batch.cameraRespirationBpm)} BPM (sakin istirahat ritmi).")
        } else if (batch.cameraRespirationBpm > 36.0f) {
            respirationScore = 0.35f
            xaiList.add("Dahili Kamera: Hızlı solunum (${String.format("%.1f", batch.cameraRespirationBpm)} BPM) - ajitasyon veya sıcaklık stresi.")
        } else {
            respirationScore = 0.55f
            xaiList.add("Dahili Kamera: Solunum frekansı ${String.format("%.1f", batch.cameraRespirationBpm)} BPM.")
        }

        // 4. Vulva ve Kuyruk Bölgesi Morfolojisi (Kamera)
        val vulvaScore: Float = batch.vulvaTightnessScore
        if (vulvaScore >= 0.75f) {
            xaiList.add("Dahili Kamera: Vulva kuru, kapalı ve hiperemi/östrus mukusu bulunmuyor (gebelik korpus luteumu ile uyumlu).")
        } else if (vulvaScore <= 0.40f) {
            xaiList.add("Dahili Kamera: Vulva gevşek/ödemli veya seröz akıntı izlendi (östrus döngüsü şüphesi).")
        } else {
            xaiList.add("Dahili Kamera: Vulva morfolojik skoru ara düzeyde (${(vulvaScore * 100).toInt()}%).")
        }

        // 5. İvmeölçer / Hareket Paterni
        val motionScore: Float
        if (batch.motionRestingIndex >= 0.70f) {
            motionScore = 0.85f
            xaiList.add("Dahili İvmeölçer: Hayvan sakin ve istirahat süresi yüksek (östrus hiperaktivitesi yok).")
        } else if (batch.motionRestingIndex <= 0.40f) {
            motionScore = 0.25f
            xaiList.add("Dahili İvmeölçer: Yüksek ayakta kalma ve yerinde sayma/hareketlilik (östrus huzursuzluğu).")
        } else {
            motionScore = 0.50f
            xaiList.add("Dahili İvmeölçer: Hareket seviyesi normal.")
        }

        // Çok Modlu Late Fusion Formülü
        val fusedRaw = (WEIGHT_CYCLE * cycleScore) +
                       (WEIGHT_RUMINATION * ruminationScore) +
                       (WEIGHT_RESPIRATION * respirationScore) +
                       (WEIGHT_VULVA * vulvaScore) +
                       (WEIGHT_MOTION * motionScore)

        val finalProbability = min(0.97f, max(0.05f, fusedRaw))

        // Risk Seviyesi Belirleme
        val riskLevel: BovinePregnancyRiskLevel
        val ultrasoundWindow: String
        when {
            finalProbability >= 0.75f -> {
                riskLevel = BovinePregnancyRiskLevel.HIGH
                ultrasoundWindow = if (batch.daysSinceInsemination < 28) {
                    "Tohumlama sonrası 28-35. günler arası transrektal ultrason ile kesinleştirilmesi önerilir."
                } else {
                    "Derhal transrektal ultrason veya PAG kan testi ile teyit edilebilir."
                }
            }
            finalProbability <= 0.45f -> {
                riskLevel = BovinePregnancyRiskLevel.LOW
                ultrasoundWindow = "Gebelik beklenmemektedir. Ahır içi ReproScan kamerasından kızgınlık alarmlarını izleyiniz."
            }
            else -> {
                riskLevel = BovinePregnancyRiskLevel.INCONCLUSIVE
                ultrasoundWindow = "3-5 gün sonra taramayı tekrarlayınız; 30. gün sonrası veteriner muayenesi önerilir."
            }
        }

        return BovineScreeningResult(
            earTag = batch.earTag,
            probability = finalProbability,
            riskLevel = riskLevel,
            daysSinceInsemination = batch.daysSinceInsemination,
            xaiReasons = xaiList,
            veterinaryRecommendation = riskLevel.recommendedActionTr,
            optimalUltrasoundWindow = ultrasoundWindow
        )
    }
}
