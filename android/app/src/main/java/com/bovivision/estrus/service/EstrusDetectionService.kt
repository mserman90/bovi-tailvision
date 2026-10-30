package com.bovivision.estrus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bovivision.estrus.MainActivity
import com.bovivision.estrus.R
import com.bovivision.estrus.analyzer.BoviTailAnalyzer
import com.bovivision.estrus.network.HealthData
import com.bovivision.estrus.network.MetricsData
import com.bovivision.estrus.network.MqttManager
import com.bovivision.estrus.network.TelemetryPayload
import com.bovivision.estrus.network.EstrusAlertPayload
import com.bovivision.estrus.utils.DeviceHealthMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.Executors

class EstrusDetectionService : LifecycleService() {

    companion object {
        private const val TAG = "EstrusService"
        private const val NOTIFICATION_CHANNEL_ID = "bovi_vision_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_COW_ID = "EXTRA_COW_ID"
        const val EXTRA_BROKER_URI = "EXTRA_BROKER_URI"
    }

    inner class LocalBinder : Binder() {
        fun getService(): EstrusDetectionService = this@EstrusDetectionService
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private var tfliteInterpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var mqttManager: MqttManager? = null
    private lateinit var healthMonitor: DeviceHealthMonitor

    private var cowId = "TR-06-3842"
    private var brokerUri = "tcp://192.168.1.100:1883"
    private var deviceId = "DEVICE_" + Build.SERIAL.take(6)

    private var lastMetrics: BoviTailAnalyzer.AnalysisMetrics? = null
    var onMetricsUpdated: ((BoviTailAnalyzer.AnalysisMetrics) -> Unit)? = null
    var previewSurfaceProvider: Preview.SurfaceProvider? = null

    private var telemetryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        healthMonitor = DeviceHealthMonitor(this)
        acquireWakeLock()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                cowId = intent.getStringExtra(EXTRA_COW_ID) ?: cowId
                brokerUri = intent.getStringExtra(EXTRA_BROKER_URI) ?: brokerUri
                startForeground(NOTIFICATION_ID, buildForegroundNotification("Sistem Başlatılıyor..."))
                initializeEdgeSystem()
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BoviTailVision:DetectionWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun initializeEdgeSystem() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                initMqtt()
                initTflite()
                startCameraStream()
                startPeriodicTelemetry()
            } catch (e: Exception) {
                Log.e(TAG, "Başlatma hatası: ${e.message}", e)
            }
        }
    }

    private fun initMqtt() {
        mqttManager = MqttManager(this, brokerUri, "BoviEdge_$deviceId").also {
            it.connect()
        }
    }

    private fun initTflite() {
        try {
            val assetFileDescriptor = assets.openFd("bovi_tail_pose.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )

            val options = Interpreter.Options().apply {
                setNumThreads(2) // 2 CPU çekirdeği (aşırı ısınmayı önlemek için)
                try {
                    nnApiDelegate = NnApiDelegate()
                    addDelegate(nnApiDelegate)
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI desteklenmiyor, CPU moduna geçiliyor.")
                }
            }
            tfliteInterpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "TFLite Model Başarıyla Yüklendi.")
        } catch (e: Exception) {
            Log.e(TAG, "TFLite yüklenemedi: ${e.message}")
        }
    }

    private fun startCameraStream() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val preview = Preview.Builder().build()
                previewSurfaceProvider?.let { preview.setSurfaceProvider(it) }

                val interpreter = tfliteInterpreter ?: return@addListener

                val analyzer = BoviTailAnalyzer(
                    tflite = interpreter,
                    targetFps = 15,
                    onFrameAnalyzed = { metrics ->
                        lastMetrics = metrics
                        onMetricsUpdated?.invoke(metrics)
                    },
                    onEstrusAlertTriggered = { rate, duration ->
                        triggerEstrusAlert(rate, duration)
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(320, 320))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

                updateNotification("Kamera Aktif: $cowId izleniyor")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bağlama hatası: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startPeriodicTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5 * 60 * 1000) // 5 dakikada bir telemetri
                lastMetrics?.let { m ->
                    val payload = TelemetryPayload(
                        deviceId = deviceId,
                        cowId = cowId,
                        timestamp = System.currentTimeMillis() / 1000,
                        metrics = MetricsData(
                            fps = m.fps,
                            tailAngleDeg = m.tailAngleDeg,
                            exposureRatePct = m.exposureRatePct,
                            exposureDurationSec = m.totalExposedSec,
                            defecationEventsFiltered = m.defecationCount,
                            flySwatEventsFiltered = m.flySwatCount
                        ),
                        health = HealthData(
                            cpuTempC = healthMonitor.getCpuTemperature(),
                            ramFreeMb = healthMonitor.getFreeRamMb(),
                            batteryPct = healthMonitor.getBatteryPercentage()
                        )
                    )
                    mqttManager?.publishTelemetry(cowId, payload)
                }
            }
        }
    }

    private fun triggerEstrusAlert(exposureRate: Double, totalExposedSec: Double) {
        val alert = EstrusAlertPayload(
            alertId = "ALT-" + UUID.randomUUID().toString().take(8),
            deviceId = deviceId,
            cowId = cowId,
            timestamp = System.currentTimeMillis() / 1000,
            confidenceScore = (exposureRate / 100.0).coerceAtMost(0.99),
            exposureRate5min = exposureRate,
            totalExposedDurationSec = totalExposedSec,
            actionRequired = "Tohumlama Penceresi: 12-18 Saat İçi (AM/PM Kuralı)"
        )
        mqttManager?.publishAlert(cowId, alert)
        updateNotification("KIZGINLIK ALARMI! (%${String.format("%.1f", exposureRate)})")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bovi-TailVision Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sığır kızgınlık analizi arka plan servisi"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Bovi-TailVision Edge AI")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        telemetryJob?.cancel()
        mqttManager?.disconnect()
        tfliteInterpreter?.close()
        nnApiDelegate?.close()
        cameraExecutor.shutdown()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
