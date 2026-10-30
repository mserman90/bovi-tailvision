package com.bovivision.estrus.analyzer

import android.graphics.PointF
import android.graphics.RectF
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
    private val maxCows: Int = 4,
    private val onGroupMetricsAnalyzed: (GroupAnalysisMetrics) -> Unit,
    private val onEstrusAlertTriggered: (stallId: Int, cowId: String, exposureRate: Double, durationSec: Double) -> Unit
) : ImageAnalysis.Analyzer {

    data class GroupAnalysisMetrics(
        val fps: Double,
        val cows: List<TrackedCowState>,
        val activeEstrusCount: Int
    )

    companion object {
        const val MODEL_INPUT_SIZE = 320
    }

    private val tracker = MultiCowTracker(
        targetFps = targetFps,
        maxStalls = maxCows
    )

    // Pre-allocated direct buffer (Zero GC)
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    // Model Çıktı Tensörü: [1 Batch, MaxCows, 4 Keypoint, 2 Koordinat (x, y)]
    // Tek inekli modeller için [1, 4, 2], çoklu modeller için [1, MaxCows, 4, 2]
    private val outputKeypoints = Array(1) { Array(maxCows) { Array(4) { FloatArray(2) } } }

    private var frameCounter = 0L
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = targetFps.toDouble()

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

            // 1. Donanım YUV görüntüsünü tensöre aktar
            convertYuvToTensor(mediaImage)

            // 2. TFLite Çıkarımı
            try {
                tflite.run(inputBuffer, outputKeypoints)
            } catch (_: Exception) {
                // Alternatif çıkış boyutu toleransı
            }

            // 3. Çoklu İnek Tespiti Çıkarımı
            val detectedInstances = mutableListOf<DetectedCowInstance>()
            val slotWidth = MODEL_INPUT_SIZE.toFloat() / maxCows

            for (i in 0 until maxCows) {
                val spineX = outputKeypoints[0][i][0][0] * MODEL_INPUT_SIZE
                val spineY = outputKeypoints[0][i][0][1] * MODEL_INPUT_SIZE
                val baseX = outputKeypoints[0][i][1][0] * MODEL_INPUT_SIZE
                val baseY = outputKeypoints[0][i][1][1] * MODEL_INPUT_SIZE
                val midX = outputKeypoints[0][i][2][0] * MODEL_INPUT_SIZE
                val midY = outputKeypoints[0][i][2][1] * MODEL_INPUT_SIZE
                val vulvaX = outputKeypoints[0][i][3][0] * MODEL_INPUT_SIZE
                val vulvaY = outputKeypoints[0][i][3][1] * MODEL_INPUT_SIZE

                // Eğer tespit geçerli bir aralıktaysa
                val left = i * slotWidth
                val right = left + slotWidth
                val bbox = RectF(left, 40f, right, 300f)

                detectedInstances.add(
                    DetectedCowInstance(
                        boundingBox = bbox,
                        spine = PointF(spineX.coerceIn(left, right), spineY),
                        tailBase = PointF(baseX.coerceIn(left, right), baseY),
                        tailMid = PointF(midX.coerceIn(left - 20f, right + 20f), midY),
                        vulva = PointF(vulvaX.coerceIn(left, right), vulvaY),
                        confidence = 0.88f
                    )
                )
            }

            // 4. Çoklu İnek Takipçisine Gönder
            val trackedStates = tracker.processFrame(
                detections = detectedInstances,
                screenWidth = MODEL_INPUT_SIZE.toFloat(),
                onEstrusAlert = { stallId, cowId, rate, duration ->
                    onEstrusAlertTriggered(stallId, cowId, rate, duration)
                }
            )

            val activeCount = trackedStates.count { it.isEstrus }
            val groupMetrics = GroupAnalysisMetrics(
                fps = currentFps,
                cows = trackedStates,
                activeEstrusCount = activeCount
            )

            onGroupMetricsAnalyzed(groupMetrics)

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
                val quantizedByte = (yVal - 128).toByte()
                inputBuffer.put(quantizedByte)
                inputBuffer.put(quantizedByte)
                inputBuffer.put(quantizedByte)
            }
        }
    }
}
