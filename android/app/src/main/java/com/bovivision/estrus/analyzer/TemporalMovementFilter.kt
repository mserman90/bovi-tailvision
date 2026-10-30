package com.bovivision.estrus.analyzer

class TemporalMovementFilter(
    private val fps: Int = 15
) {
    // 1.5 saniye kalıcı savrulma eşiği (Sinek kovma filtresi)
    private val minSustainedFrames: Int = (1.5 * fps).toInt()
    // 0.3 saniyelik mikro çırpma eşiği
    private val maxFlickerFrames: Int = (0.3 * fps).toInt()
    // 15 saniyelik dışkılama maksimum süre eşiği
    private val maxDefecationFrames: Int = (15.0 * fps).toInt()

    private var sustainedExposedCount = 0
    private var defecationFrameCount = 0
    private var wasDefecating = false

    var totalDefecationEventsFiltered = 0
        private set
    var totalFlySwatEventsFiltered = 0
        private set

    /**
     * Her video karesinde kinematik durumu işler ve filtrelenmiş E(k) kararını döner.
     * @return true ise vulva gerçek anlamda kızgınlık davranışı olarak açıktır (E(k) = 1).
     */
    fun processFrame(isRawExposed: Boolean, isDefecatingPosture: Boolean): Boolean {
        // 1. Dışkılama / Ürinasyon Durum Takibi
        if (isDefecatingPosture) {
            defecationFrameCount++
            wasDefecating = true
            sustainedExposedCount = 0
            // 15 saniyeden kısa süren dışkılama hareketi boyunca tüm açığa çıkmalar elenir
            if (defecationFrameCount <= maxDefecationFrames) {
                return false
            }
        } else {
            if (wasDefecating) {
                if (defecationFrameCount in 1..maxDefecationFrames) {
                    totalDefecationEventsFiltered++
                }
                defecationFrameCount = 0
                wasDefecating = false
            }
        }

        // 2. Sinek Kovma / Refleks Filtresi
        if (isRawExposed) {
            sustainedExposedCount++
            // Sadece en az 1.5 saniye (minSustainedFrames) boyunca kalıcı olan hareketler kabul edilir
            return sustainedExposedCount >= minSustainedFrames
        } else {
            // Hareket kesildi: 0.3 saniyenin altında çırpıp durduysa sinek kovmadır
            if (sustainedExposedCount in 1..maxFlickerFrames) {
                totalFlySwatEventsFiltered++
            }
            sustainedExposedCount = 0
            return false
        }
    }

    fun reset() {
        sustainedExposedCount = 0
        defecationFrameCount = 0
        wasDefecating = false
        totalDefecationEventsFiltered = 0
        totalFlySwatEventsFiltered = 0
    }
}
