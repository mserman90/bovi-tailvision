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

            // Grup Analiz Metriklerini Dinle
            detectionService?.onGroupMetricsUpdated = { groupMetrics ->
                runOnUiThread {
                    updateGroupUiMetrics(groupMetrics)
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

        // Ekranın kapanmasını engelle
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
                binding.btnTogglePreview.text = "Grup Önizlemeyi Aç"
            } else {
                binding.previewView.visibility = View.VISIBLE
                binding.btnTogglePreview.text = "Önizlemeyi Gizle"
                detectionService?.previewSurfaceProvider = binding.previewView.surfaceProvider
            }
        }

        binding.btnToggleBlackScreen.setOnClickListener {
            activateBlackScreenMode()
        }

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
        val broker = binding.etBrokerIp.text.toString().trim().ifEmpty { "tcp://192.168.1.100:1883" }

        val intent = Intent(this, EstrusDetectionService::class.java).apply {
            action = EstrusDetectionService.ACTION_START
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
        binding.tvDeviceStatus.text = "Grup Analizi Aktif (4 Durak İzleniyor)"
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

    private fun updateGroupUiMetrics(gm: com.bovivision.estrus.analyzer.BoviTailAnalyzer.GroupAnalysisMetrics) {
        binding.tvLiveFps.text = String.format("Kare Hızı: %.1f FPS", gm.fps)

        if (gm.activeEstrusCount > 0) {
            val inHeat = gm.cows.filter { it.isEstrus }.joinToString(", ") { "D${it.stallId + 1}: ${it.cowId}" }
            binding.tvEstrusAlertBadge.apply {
                text = "🚨 KIZGINLIK TESPİT EDİLDİ! ($inHeat)"
                setBackgroundColor(Color.parseColor("#D32F2F"))
            }
        } else {
            binding.tvEstrusAlertBadge.apply {
                text = "DURUM: GRUP NORMAL (${gm.cows.size} İnek İzleniyor)"
                setBackgroundColor(Color.parseColor("#1B5E20"))
            }
        }

        // Özet metrikler (İlk inek veya en aktif inek)
        val primary = gm.cows.maxByOrNull { it.exposureRatePct } ?: return
        binding.tvLiveAngle.text = String.format("Kuyruk Açısı (D%d): %.1f°", primary.stallId + 1, primary.kinematics.tailAngleDeg)
        binding.tvExposureRate.text = String.format("Maks Açık Kalma: %%%.1f (%s)", primary.exposureRatePct, primary.cowId)
        binding.tvExposureTime.text = String.format("Açık Süre: %.1f sn", primary.totalExposedSec)
    }

    private fun activateBlackScreenMode() {
        isBlackScreenActive = true
        binding.blackOverlayView.visibility = View.VISIBLE
        val lp = window.attributes
        lp.screenBrightness = 0.0f
        window.attributes = lp
        Toast.makeText(this, "Termal Koruma Aktif (Grup İzleniyor). Çıkmak için çift tıklayın.", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateBlackScreenMode() {
        isBlackScreenActive = false
        binding.blackOverlayView.visibility = View.GONE
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
