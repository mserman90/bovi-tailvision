package com.bovivision.estrus.analyzer

import android.graphics.PointF
import android.graphics.RectF
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class DetectedCowInstance(
    val boundingBox: RectF,
    val spine: PointF,
    val tailBase: PointF,
    val tailMid: PointF,
    val vulva: PointF,
    val confidence: Float
)

data class TrackedCowState(
    val stallId: Int,
    val cowId: String,
    val kinematics: KinematicResult,
    val exposureRatePct: Double,
    val totalExposedSec: Double,
    val isEstrus: Boolean,
    val isDefecating: Boolean
)

/**
 * Çoklu Sığır Takip ve Durum Yöneticisi (Multi-Cow Spatial Tracker)
 * Kameranın görüş açısındaki birden fazla ineği (bağlı durak veya yemlik kilit sırası)
 * uzamsal X-ekseni durak yuvalarına (Spatial Stall Slots) ve centroid yakınlığına göre
 * sürekli takip eder; her inek için bağımsız kayan pencere ve gürültü filtresi işletir.
 */
class MultiCowTracker(
    private val targetFps: Int = 15,
    private val maxStalls: Int = 4,
    private val cowIdMapping: Map<Int, String> = mapOf(
        0 to "TR-06-3841",
        1 to "TR-06-3842",
        2 to "TR-06-3843",
        3 to "TR-06-3844"
    )
) {
    companion object {
        private const val WINDOW_MINUTES = 5
        private const val ESTRUS_THRESHOLD = 65.0
    }

    private val windowCapacity = WINDOW_MINUTES * 60 * targetFps

    // Her durak/inek için bağımsız kayan pencere ve filtre nesnesi
    private class CowSession(
        val stallId: Int,
        val cowId: String,
        val windowCapacity: Int,
        val fps: Int
    ) {
        val eventHistory = ArrayDeque<Boolean>(windowCapacity)
        val filter = TemporalMovementFilter(fps = fps)
        var lastKinematics: KinematicResult? = null
        var lastAlertTimestamp: Long = 0L
    }

    private val activeSessions = ConcurrentHashMap<Int, CowSession>()

    init {
        for (i in 0 until maxStalls) {
            val id = cowIdMapping[i] ?: "TR-COW-${i + 1}"
            activeSessions[i] = CowSession(i, id, windowCapacity, targetFps)
        }
    }

    /**
     * Karedeki çoklu tespitleri duraklara (Stall Slots) göre eşler ve her ineği günceller.
     */
    fun processFrame(
        detections: List<DetectedCowInstance>,
        screenWidth: Float,
        onEstrusAlert: (stallId: Int, cowId: String, exposureRate: Double, durationSec: Double) -> Unit
    ): List<TrackedCowState> {
        val results = mutableListOf<TrackedCowState>()
        val slotWidth = screenWidth / maxStalls
        val now = System.currentTimeMillis()

        // 1. Her tespiti yatay merkez koordinatına (X) göre ilgili durak yuvasına ata
        val stallAssignments = mutableMapOf<Int, DetectedCowInstance>()
        for (det in detections) {
            val centerX = det.boundingBox.centerX()
            val stallIndex = (centerX / slotWidth).toInt().coerceIn(0, maxStalls - 1)
            // Eğer aynı yuvada iki tespit varsa daha yüksek güven skoruna sahip olanı al
            val existing = stallAssignments[stallIndex]
            if (existing == null || det.confidence > existing.confidence) {
                stallAssignments[stallIndex] = det
            }
        }

        // 2. Her duraktaki ineğin durumunu bağımsız analiz et
        for (i in 0 until maxStalls) {
            val session = activeSessions[i] ?: continue
            val det = stallAssignments[i]

            if (det != null) {
                // Kinematik hesaplama
                val kinematics = KinematicsEngine.computeKinematics(
                    pSpine = det.spine,
                    pTailBase = det.tailBase,
                    pTailMid = det.tailMid,
                    pVulva = det.vulva
                )
                session.lastKinematics = kinematics

                val rawExposed = kinematics.isCombinedDeviation && kinematics.isRoiClear
                val filteredExposed = session.filter.processFrame(rawExposed, kinematics.isDefecationPosture)

                synchronized(session.eventHistory) {
                    if (session.eventHistory.size >= session.windowCapacity) {
                        session.eventHistory.removeFirst()
                    }
                    session.eventHistory.addLast(filteredExposed)
                }

                val totalFrames = synchronized(session.eventHistory) { session.eventHistory.size }
                val exposedCount = synchronized(session.eventHistory) { session.eventHistory.count { it } }
                val exposureRate = if (totalFrames > 0) (exposedCount.toDouble() / totalFrames) * 100.0 else 0.0
                val totalExposedSec = exposedCount.toDouble() / targetFps
                val isEstrus = exposureRate >= ESTRUS_THRESHOLD

                results.add(
                    TrackedCowState(
                        stallId = i,
                        cowId = session.cowId,
                        kinematics = kinematics,
                        exposureRatePct = exposureRate,
                        totalExposedSec = totalExposedSec,
                        isEstrus = isEstrus,
                        isDefecating = kinematics.isDefecationPosture
                    )
                )

                // Kızgınlık alarmı tetikleme (minimum 2 dakika veri toplandıktan sonra ve 10 dakikada en fazla 1 alarm)
                if (totalFrames >= targetFps * 60 * 2 && isEstrus) {
                    if (now - session.lastAlertTimestamp > 10 * 60 * 1000) {
                        session.lastAlertTimestamp = now
                        onEstrusAlert(i, session.cowId, exposureRate, totalExposedSec)
                    }
                }
            } else {
                // Bu durakta inek tespit edilemedi (kamera önü boş veya inek kalktı)
                session.lastKinematics?.let { lastKin ->
                    val totalFrames = synchronized(session.eventHistory) { session.eventHistory.size }
                    val exposedCount = synchronized(session.eventHistory) { session.eventHistory.count { it } }
                    val rate = if (totalFrames > 0) (exposedCount.toDouble() / totalFrames) * 100.0 else 0.0
                    results.add(
                        TrackedCowState(
                            stallId = i,
                            cowId = session.cowId,
                            kinematics = lastKin,
                            exposureRatePct = rate,
                            totalExposedSec = exposedCount.toDouble() / targetFps,
                            isEstrus = rate >= ESTRUS_THRESHOLD,
                            isDefecating = false
                        )
                    )
                }
            }
        }

        return results
    }
}
