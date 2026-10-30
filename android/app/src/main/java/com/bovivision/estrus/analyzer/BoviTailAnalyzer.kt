package com.bovivision.estrus.analyzer

import android.graphics.PointF
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BoviTailAnalyzer(
    private val tflite: Interpreter,
    private val targetFps: Int = 15,
    private val onFrameAnalyzed: (AnalysisMetrics) -> Unit,
    private val onEstrusAlertTriggered: (exposureRate: Double, durationSec: Double) -> Unit
) : ImageAnalysis.Analyzer {

    data class AnalysisMetrics(
        val fps: Double,
        val tailAngleDeg: Double,
        val isVulvaExposed: Boolean,
        val exposureRatePct: Double,
        val totalExposedSec: Double,
        val isDefecating: Boolean,
        val defecationCount: Int,
        val flySwatCount: Int
    )

    companion object {
        const val MODEL_INPUT_SIZE = 320
        private const val WINDOW_MINUTES = 5
        private const val ESTRUS_EXPOSURE_THRESHOLD = 65.0 // Literatür: > %65.0 [Perez et al., 2022]
    }

    private val windowSize = WINDOW_MINUTES * 60 * targetFps
    private val eventHistory = ArrayDeque<Boolean>(windowSize)
    private val temporalFilter = TemporalMovementFilter(fps = targetFps)

    // Zero-allocation pre-allocated input buffer (INT8: 1 byte per channel)
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    // Model Çıktı Tensörü: [1 Batch, 4 Keypoint (spine, tailbase, tailmid, vulva), 2 Koordinat (x, y)]
    private val outputKeypoints = Array(1) { Array(4) { FloatArray(2) } }

    private var frameCounter = 0L
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = targetFps.toDouble()
    private var lastAlertTimestamp = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            frameCounter++
            val now = System.currentTimeMillis()
            if (now - lastFpsTimestamp >= 1000) {
                currentFps = (frameCounter * 1000.0) / (now - lastFpsTimestamp)
                frameCounter = 0
                lastFpsTimestamp = now
            }

            // 1. Donanım YUV Görüntüsünü Sıfır Tahsisle Tensöre Aktar
            convertYuvToTensor(mediaImage)

            // 2. TFLite Çıkarımı (Inference)
            tflite.run(inputBuffer, outputKeypoints)

            // 3. Keypoint Koordinatlarını Al (Normalize koordinatları 320x320 uzayına dönüştür)
            val pSpine = PointF(outputKeypoints[0][0][0] * MODEL_INPUT_SIZE, outputKeypoints[0][0][1] * MODEL_INPUT_SIZE)
            val pTailBase = PointF(outputKeypoints[0][1][0] * MODEL_INPUT_SIZE, outputKeypoints[0][1][1] * MODEL_INPUT_SIZE)
            val pTailMid = PointF(outputKeypoints[0][2][0] * MODEL_INPUT_SIZE, outputKeypoints[0][2][1] * MODEL_INPUT_SIZE)
            val pVulva = PointF(outputKeypoints[0][3][0] * MODEL_INPUT_SIZE, outputKeypoints[0][3][1] * MODEL_INPUT_SIZE)

            // 4. Kinematik Hesaplama
            val kinematics = KinematicsEngine.computeKinematics(pSpine, pTailBase, pTailMid, pVulva)
            val rawExposed = kinematics.isCombinedDeviation && kinematics.isRoiClear

            // 5. Zamansal Gürültü Filtresi (Sinek ve Dışkılama Ayrıştırma)
            val isExposedFiltered = temporalFilter.processFrame(rawExposed, kinematics.isDefecationPosture)

            // 6. Kayan Pencere Güncellemesi
            synchronized(eventHistory) {
                if (eventHistory.size >= windowSize) {
                    eventHistory.removeFirst()
                }
                eventHistory.addLast(isExposedFiltered)
            }

            // 7. İstatistiksel Hesaplama (R_open ve T_exp)
            val totalFrames = synchronized(eventHistory) { eventHistory.size }
            val exposedFrames = synchronized(eventHistory) { eventHistory.count { it } }
            val exposureRate = if (totalFrames > 0) (exposedFrames.toDouble() / totalFrames) * 100.0 else 0.0
            val totalExposedSec = exposedFrames.toDouble() / targetFps

            val metrics = AnalysisMetrics(
                fps = currentFps,
                tailAngleDeg = kinematics.tailAngleDeg,
                isVulvaExposed = isExposedFiltered,
                exposureRatePct = exposureRate,
                totalExposedSec = totalExposedSec,
                isDefecating = kinematics.isDefecationPosture,
                defecationCount = temporalFilter.totalDefecationEventsFiltered,
                flySwatCount = temporalFilter.totalFlySwatEventsFiltered
            )

            onFrameAnalyzed(metrics)

            // 8. Kızgınlık Alarmı Tetikleme (Minimum 3 dakika veri toplandıktan sonra)
            if (totalFrames >= targetFps * 60 * 3 && exposureRate >= ESTRUS_EXPOSURE_THRESHOLD) {
                // Alarm fırtınasını önlemek için 10 dakikada en fazla 1 alarm
                if (now - lastAlertTimestamp > 10 * 60 * 1000) {
                    lastAlertTimestamp = now
                    onEstrusAlertTriggered(exposureRate, totalExposedSec)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun convertYuvToTensor(image: Image) {
        inputBuffer.rewind()
        val yPlane = image.planes[0].buffer
        val srcWidth = image.width
        val srcHeight = image.height
        val rowStride = image.planes[0].rowStride

        val stepX = srcWidth / MODEL_INPUT_SIZE
        val stepY = srcHeight / MODEL_INPUT_SIZE

        for (y in 0 until MODEL_INPUT_SIZE) {
            val rowOffset = y * stepY * rowStride
            for (x in 0 until MODEL_INPUT_SIZE) {
                val pixelIndex = rowOffset + (x * stepX)
                val yVal = yPlane.get(pixelIndex).toInt() and 0xFF
                // INT8 Kuantizasyon: (yVal - 128) -> [-128, 127]
                val quantizedByte = (yVal - 128).toByte()
                inputBuffer.put(quantizedByte) // R
                inputBuffer.put(quantizedByte) // G
                inputBuffer.put(quantizedByte) // B
            }
        }
    }
}
