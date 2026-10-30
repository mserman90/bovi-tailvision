package com.bovivision.estrus

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bovivision.estrus.databinding.ActivityMainBinding
import com.bovivision.estrus.service.EstrusDetectionService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var detectionService: EstrusDetectionService? = null
    private var isServiceBound = false
    private var isRunning = false
    private var isBlackScreenActive = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as EstrusDetectionService.LocalBinder
            detectionService = binder.getService()
            isServiceBound = true

            // UI Metrik Güncellemelerini Dinle
            detectionService?.onMetricsUpdated = { metrics ->
                runOnUiThread {
                    updateUiMetrics(metrics)
                }
            }

            // Preview SurfaceProvider'ı servise ilet
            if (binding.previewView.visibility == View.VISIBLE) {
                detectionService?.previewSurfaceProvider = binding.previewView.surfaceProvider
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            detectionService = null
            isServiceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            startDetectionService()
        } else {
            Toast.makeText(this, "Kamera izni gereklidir!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ekranın kapanmasını engelle (Kamera akışının durmaması için)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnToggleService.setOnClickListener {
            if (!isRunning) {
                checkPermissionsAndStart()
            } else {
                stopDetectionService()
            }
        }

        binding.btnTogglePreview.setOnClickListener {
            if (binding.previewView.visibility == View.VISIBLE) {
                binding.previewView.visibility = View.GONE
                binding.btnTogglePreview.text = "Montaj / Kamera Önizleme"
            } else {
                binding.previewView.visibility = View.VISIBLE
                binding.btnTogglePreview.text = "Önizlemeyi Gizle"
                detectionService?.previewSurfaceProvider = binding.previewView.surfaceProvider
            }
        }

        binding.btnToggleBlackScreen.setOnClickListener {
            activateBlackScreenMode()
        }

        // Çift tıklama ile siyah ekrandan çıkma dedektörü
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                deactivateBlackScreenMode()
                return true
            }
        })

        binding.blackOverlayView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startDetectionService()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startDetectionService() {
        val cowId = binding.etCowId.text.toString().trim().ifEmpty { "TR-06-3842" }
        val broker = binding.etBrokerIp.text.toString().trim().ifEmpty { "tcp://192.168.1.100:1883" }

        val intent = Intent(this, EstrusDetectionService::class.java).apply {
            action = EstrusDetectionService.ACTION_START
            putExtra(EstrusDetectionService.EXTRA_COW_ID, cowId)
            putExtra(EstrusDetectionService.EXTRA_BROKER_URI, broker)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        isRunning = true
        binding.btnToggleService.text = getString(R.string.btn_stop)
        binding.btnToggleService.setBackgroundColor(Color.parseColor("#C62828"))
        binding.tvDeviceStatus.text = getString(R.string.status_running)
    }

    private fun stopDetectionService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        val intent = Intent(this, EstrusDetectionService::class.java).apply {
            action = EstrusDetectionService.ACTION_STOP
        }
        startService(intent)
        isRunning = false
        binding.btnToggleService.text = getString(R.string.btn_start)
        binding.btnToggleService.setBackgroundColor(Color.parseColor("#2E7D32"))
        binding.tvDeviceStatus.text = getString(R.string.status_standby)
    }

    private fun updateUiMetrics(m: com.bovivision.estrus.analyzer.BoviTailAnalyzer.AnalysisMetrics) {
        binding.tvLiveAngle.text = String.format("Kuyruk Açısı: %.1f°", m.tailAngleDeg)
        binding.tvLiveFps.text = String.format("Kare Hızı: %.1f FPS", m.fps)
        binding.tvExposureRate.text = String.format("Açık Kalma (5dk): %%%.1f", m.exposureRatePct)
        binding.tvExposureTime.text = String.format("Açık Süre: %.1f sn", m.totalExposedSec)

        if (m.exposureRatePct >= 65.0) {
            binding.tvEstrusAlertBadge.apply {
                text = "KIZGINLIK ALARMI! (ÖSTRUS TESPİT EDİLDİ)"
                setBackgroundColor(Color.parseColor("#D32F2F")) // Kırmızı
            }
        } else {
            binding.tvEstrusAlertBadge.apply {
                text = "DURUM: NORMAL (DİÖSTRUS / ANÖSTRUS)"
                setBackgroundColor(Color.parseColor("#1B5E20")) // Yeşil
            }
        }
    }

    private fun activateBlackScreenMode() {
        isBlackScreenActive = true
        binding.blackOverlayView.visibility = View.VISIBLE
        // Ekran parlaklığını sıfıra indir (Termal koruma ve güç tasarrufu)
        val lp = window.attributes
        lp.screenBrightness = 0.0f
        window.attributes = lp
        Toast.makeText(this, "Termal Koruma Aktif. Çıkmak için ekrana çift tıklayın.", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateBlackScreenMode() {
        isBlackScreenActive = false
        binding.blackOverlayView.visibility = View.GONE
        // Parlaklığı varsayılana döndür
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
