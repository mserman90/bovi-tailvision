package com.bovivision.farmer.oviscan.ml

import com.bovivision.farmer.oviscan.domain.model.MeasurementMode
import com.bovivision.farmer.oviscan.domain.model.PregnancyRiskLevel
import com.bovivision.farmer.oviscan.domain.model.ScreeningResult
import com.bovivision.farmer.oviscan.domain.model.SensorDataBatch

/**
 * OviScan Multimodal Late Fusion Sınıflandırıcı
 * Harici ultrason probu gerektirmeden, standart akıllı telefonun dahili donanımlarını
 * (mikrofon, ivmeölçer, optik kamera) ve tohumlama takvimini birleştirerek koyunlarda
 * gebelik ön tarama skorlaması yapar.
 */
object LateFusionClassifier {

    fun classify(batch: SensorDataBatch): ScreeningResult {
        val xaiList = mutableListOf<String>()

        // 1. Akustik Analiz (Mikrofon): Rumen geviş ritmi vs kızgınlık melemesi
        val audioPregnancyScore: Float
        if (batch.acousticEventCount > 6 && batch.estimatedPitchHz > 400f) {
            audioPregnancyScore = 0.22f
            xaiList.add("Dahili Mikrofon: Sık ve tiz meleme tespit edildi (östrus/kızgınlık huzursuzluğu şüphesi, gebe olma olasılığını düşürüyor).")
        } else if (batch.acousticEventCount in 1..4 && batch.audioSnrDb > 10f) {
            audioPregnancyScore = 0.80f
            xaiList.add("Dahili Mikrofon: Düzenli geviş getirme/solunum örüntüsü tespit edildi (gebelik ile uyumlu sakin metabolik akustik).")
        } else {
            audioPregnancyScore = 0.50f
            xaiList.add("Dahili Mikrofon: Akustik profil nötr/ara seviyede.")
        }

        // 2. Hareket Analizi (İvmeölçer): Aktivite vs dinlenme/yatma oranı
        val motionPregnancyScore: Float
        if (batch.mode == MeasurementMode.WEARABLE_COLLAR) {
            if (batch.motionActivityIndex < 0.35f && batch.lyingRatio > 0.40f) {
                motionPregnancyScore = 0.84f
                xaiList.add("Dahili İvmeölçer (Tasma): Yüksek dinlenme ve yatma oranı (%${(batch.lyingRatio * 100).toInt()}), gebelik ile uyumlu.")
            } else if (batch.motionActivityIndex > 0.65f) {
                motionPregnancyScore = 0.18f
                xaiList.add("Dahili İvmeölçer (Tasma): Yüksek aktivite ve ajitasyon (östrus hareketliliği olabilir).")
            } else {
                motionPregnancyScore = 0.50f
                xaiList.add("Dahili İvmeölçer (Tasma): Aktivite düzeyi standart seviyede.")
            }
        } else {
            // Elde hızlı tarama
            if (batch.motionAverageMagnitude < 0.8f) {
                motionPregnancyScore = 0.72f
                xaiList.add("Dahili İvmeölçer (Elde): Hayvan ölçüm penceresinde sakin durdu.")
            } else {
                motionPregnancyScore = 0.44f
                xaiList.add("Dahili İvmeölçer (Elde): Ölçüm sırasında hayvan hareketliydi.")
            }
        }

        // 3. Tohumlama Sonrası Takvim (Non-return biyolojik periyodu: 17 günlük döngü)
        var temporalBias = 0.0f
        when {
            batch.daysSinceInsemination in 25..35 -> {
                temporalBias = 0.09f
                xaiList.add("Tohumlama Takvimi: ${batch.daysSinceInsemination}. gün (1. kızgınlık döngüsü aşılmış, non-return pozitif).")
            }
            batch.daysSinceInsemination in 36..55 -> {
                temporalBias = 0.14f
                xaiList.add("Tohumlama Takvimi: ${batch.daysSinceInsemination}. gün (2. kızgınlık döngüsü de aşılmış).")
            }
            batch.daysSinceInsemination < 25 -> {
                temporalBias = -0.12f
                xaiList.add("Tohumlama Takvimi: Erken dönem (<25. gün), kesinlik düşüktür.")
            }
        }

        // 4. Optik Kamera Analizi: Karın/Böğür solunum frekansı (BPM)
        var cameraScore: Float? = null
        if (batch.cameraRespirationBpm != null) {
            val bpm = batch.cameraRespirationBpm
            if (bpm in 26.0f..38.0f) {
                cameraScore = 0.80f
                xaiList.add("Dahili Kamera (Optik Solunum): Karın solunum frekansı dakikada ${bpm.toInt()} nefes (gebelik metabolik artışı ile uyumlu).")
            } else if (bpm < 24.0f) {
                cameraScore = 0.48f
                xaiList.add("Dahili Kamera (Optik Solunum): Standart dinlenme solunumu (${bpm.toInt()} BPM).")
            } else {
                cameraScore = 0.35f
                xaiList.add("Dahili Kamera (Optik Solunum): Hızlı/düzensiz solunum (${bpm.toInt()} BPM).")
            }
        }

        // 5. Çok Modlu Füzyon Ağırlıklandırması
        val audioWeight = if (cameraScore != null) 0.45f else 0.50f
        val motionWeight = if (cameraScore != null) 0.40f else 0.50f
        val cameraWeight = if (cameraScore != null) 0.15f else 0.0f

        var fusedProb = (audioPregnancyScore * audioWeight) +
                (motionPregnancyScore * motionWeight) +
                ((cameraScore ?: 0f) * cameraWeight) +
                temporalBias

        fusedProb = fusedProb.coerceIn(0.05f, 0.95f)

        val durationConfidence = (batch.audioDurationSeconds / 30f).coerceIn(0.5f, 1.0f)
        val snrConfidence = (batch.audioSnrDb / 30f).coerceIn(0.6f, 1.0f)
        val confidence = (0.7f * durationConfidence + 0.3f * snrConfidence).coerceIn(0.50f, 0.95f)

        val riskLevel = PregnancyRiskLevel.fromProbability(fusedProb)

        return ScreeningResult(
            earTag = batch.earTag,
            probability = fusedProb,
            riskLevel = riskLevel,
            confidence = confidence,
            audioScore = audioPregnancyScore,
            motionScore = motionPregnancyScore,
            cameraScore = cameraScore,
            morphologyScore = null,
            xaiReasons = xaiList,
            measurementDurationSec = batch.audioDurationSeconds.toInt(),
            mode = batch.mode
        )
    }
}
