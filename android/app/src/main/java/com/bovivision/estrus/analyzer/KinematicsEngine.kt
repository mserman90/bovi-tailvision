package com.bovivision.estrus.analyzer

import android.graphics.PointF
import kotlin.math.acos
import kotlin.math.sqrt

data class KinematicResult(
    val tailAngleDeg: Double,
    val isLeftDeviation: Boolean,
    val isRightDeviation: Boolean,
    val isCombinedDeviation: Boolean,
    val isRoiClear: Boolean,
    val isDefecationPosture: Boolean
)

object KinematicsEngine {

    private const val DEVIATION_THRESHOLD = 20.0
    private const val ROI_CLEAR_RADIUS = 35.0f // Model 320x320 piksel uzayındaki güvenlik yarıçapı

    fun computeKinematics(
        pSpine: PointF,
        pTailBase: PointF,
        pTailMid: PointF,
        pVulva: PointF
    ): KinematicResult {
        // Kuyruk vektörü: V_tail = (x1 - x0, y1 - y0)
        val vTailX = (pTailMid.x - pTailBase.x).toDouble()
        val vTailY = (pTailMid.y - pTailBase.y).toDouble()

        // Referans dikey eksen vektörü: V_ref = (x0 - xs, y0 - ys)
        val vRefX = (pTailBase.x - pSpine.x).toDouble()
        val vRefY = (pTailBase.y - pSpine.y).toDouble()

        val magTail = sqrt(vTailX * vTailX + vTailY * vTailY)
        val magRef = sqrt(vRefX * vRefX + vRefY * vRefY)

        val signedAngleDeg = if (magTail > 0.001 && magRef > 0.001) {
            val dot = vTailX * vRefX + vTailY * vRefY
            val cosTheta = (dot / (magTail * magRef)).coerceIn(-1.0, 1.0)
            val angle = acos(cosTheta) * (180.0 / Math.PI)
            
            // 2B Vektörel Çarpım (Z-bileşeni): Sağa / Sola sapma işareti
            val crossZ = vRefX * vTailY - vRefY * vTailX
            if (crossZ < 0) -angle else angle
        } else {
            0.0
        }

        val absAngle = Math.abs(signedAngleDeg)
        val isLeft = signedAngleDeg <= -DEVIATION_THRESHOLD
        val isRight = signedAngleDeg >= DEVIATION_THRESHOLD
        val isCombined = absAngle >= DEVIATION_THRESHOLD

        // Vulva ROI Açıklık Kontrolü: || P_tailmid - P_vulva || > R_roi
        val dx = pTailMid.x - pVulva.x
        val dy = pTailMid.y - pVulva.y
        val distVulva = sqrt(dx * dx + dy * dy)
        val isRoiClear = distVulva > ROI_CLEAR_RADIUS

        // Dışkılama / İdrar Postürü Kontrolü:
        // Kuyruk tabandan yukarı doğru dikleşir (görüntüde Y koordinatında yukarı çıkma)
        // ancak yatay açı sapması minimaldir (|theta| < 15 derece).
        val verticalLiftDistance = pTailBase.y - pTailMid.y
        val isDefecationPosture = verticalLiftDistance > 25.0f && absAngle < 15.0

        return KinematicResult(
            tailAngleDeg = signedAngleDeg,
            isLeftDeviation = isLeft,
            isRightDeviation = isRight,
            isCombinedDeviation = isCombined,
            isRoiClear = isRoiClear,
            isDefecationPosture = isDefecationPosture
        )
    }
}
