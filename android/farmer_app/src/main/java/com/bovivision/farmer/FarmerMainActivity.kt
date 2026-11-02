package com.bovivision.farmer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bovivision.farmer.adapter.AlertAdapter
import com.bovivision.farmer.boviscan.domain.model.BovineMeasurementMode
import com.bovivision.farmer.boviscan.domain.model.BovinePregnancyRiskLevel
import com.bovivision.farmer.boviscan.domain.model.BovineSensorDataBatch
import com.bovivision.farmer.boviscan.ml.BovineLateFusionClassifier
import com.bovivision.farmer.databinding.ActivityFarmerMainBinding
import com.bovivision.farmer.model.FarmerAlertModel
import com.bovivision.farmer.oviscan.domain.model.MeasurementMode
import com.bovivision.farmer.oviscan.domain.model.PregnancyRiskLevel
import com.bovivision.farmer.oviscan.domain.model.SensorDataBatch
import com.bovivision.farmer.oviscan.ml.LateFusionClassifier
import com.bovivision.farmer.service.FarmerAlertListenerService

class FarmerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFarmerMainBinding
    private lateinit var adapter: AlertAdapter
    private val alertList = mutableListOf<FarmerAlertModel>()

    // OviScan Color Palette
    private val colorPrimaryText = Color.parseColor("#191817")
    private val colorSecondaryText = Color.parseColor("#58554F")
    private val colorStatusGood = Color.parseColor("#225633")
    private val colorStatusWarn = Color.parseColor("#825412")
    private val colorStatusAlert = Color.parseColor("#862B23")
    private val colorSurfaceAlt = Color.parseColor("#F3F1EC")

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Bildirim izni olmadan alarm sesleri çalmayabilir!", Toast.LENGTH_LONG).show()
        }
        startAlertService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarmerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        setupBovinePregnancyIntegration()
        setupOviScanIntegration()
        checkPermissionsAndStart()
    }

    private fun setupRecyclerView() {
        adapter = AlertAdapter(alertList) { acknowledgedAlert ->
            updateDashboardCounters()
            Toast.makeText(this, "${acknowledgedAlert.cowId} için tohumlama kaydedildi.", Toast.LENGTH_SHORT).show()
        }
        binding.rvAlerts.layoutManager = LinearLayoutManager(this)
        binding.rvAlerts.adapter = adapter
    }

    private fun setupListeners() {
        // Ana Sekme Değiştirici: Büyükbaş (Bovine) vs Küçükbaş (Ovine) - OviScan Pill Tarzı
        binding.btnTabBovi.setOnClickListener {
            binding.layoutBoviView.visibility = View.VISIBLE
            binding.layoutOviView.visibility = View.GONE
            binding.btnTabBovi.setBackgroundColor(colorPrimaryText)
            binding.btnTabBovi.setTextColor(Color.WHITE)
            binding.btnTabOvi.setBackgroundColor(Color.TRANSPARENT)
            binding.btnTabOvi.setTextColor(colorSecondaryText)
        }

        binding.btnTabOvi.setOnClickListener {
            binding.layoutBoviView.visibility = View.GONE
            binding.layoutOviView.visibility = View.VISIBLE
            binding.btnTabOvi.setBackgroundColor(colorPrimaryText)
            binding.btnTabOvi.setTextColor(Color.WHITE)
            binding.btnTabBovi.setBackgroundColor(Color.TRANSPARENT)
            binding.btnTabBovi.setTextColor(colorSecondaryText)
        }

        // Bovine Alt Sekme Değiştirici: Ahır Alarmları vs Elde Gebelik Taraması
        binding.btnSubTabBoviBarn.setOnClickListener {
            binding.layoutBoviBarnAlarms.visibility = View.VISIBLE
            binding.layoutBoviPregnancyScan.visibility = View.GONE
            binding.btnSubTabBoviBarn.setBackgroundColor(colorPrimaryText)
            binding.btnSubTabBoviBarn.setTextColor(Color.WHITE)
            binding.btnSubTabBoviPregnancy.setBackgroundColor(Color.TRANSPARENT)
            binding.btnSubTabBoviPregnancy.setTextColor(colorSecondaryText)
        }

        binding.btnSubTabBoviPregnancy.setOnClickListener {
            binding.layoutBoviBarnAlarms.visibility = View.GONE
            binding.layoutBoviPregnancyScan.visibility = View.VISIBLE
            binding.btnSubTabBoviPregnancy.setBackgroundColor(colorPrimaryText)
            binding.btnSubTabBoviPregnancy.setTextColor(Color.WHITE)
            binding.btnSubTabBoviBarn.setBackgroundColor(Color.TRANSPARENT)
            binding.btnSubTabBoviBarn.setTextColor(colorSecondaryText)
        }

        // Ahır Kamera İstasyonundan Canlı Alarm Dinleme
        FarmerAlertListenerService.onAlertReceivedListener = { alert ->
            runOnUiThread {
                adapter.addAlert(alert)
                updateDashboardCounters()
            }
        }

        // Broker bağlantı durumu (OviScan top bar status)
        FarmerAlertListenerService.onConnectionStateChanged = { connected ->
            runOnUiThread {
                if (connected) {
                    binding.tvConnectionStatus.text = "IoT Broker: Aktif (ReproScan Canlı)"
                    binding.tvConnectionStatus.setTextColor(colorStatusGood)
                } else {
                    binding.tvConnectionStatus.text = "IoT Broker: Bağlantı Kesildi"
                    binding.tvConnectionStatus.setTextColor(colorStatusAlert)
                }
            }
        }
    }

    private fun setupBovinePregnancyIntegration() {
        // Sığır Cep Telefonuyla Gebelik Ön Tarama Tetikleyicisi
        binding.btnRunBoviPregnancyScan.setOnClickListener {
            val tag = binding.etCowTag.text.toString().trim().ifEmpty { "TR-06-3842" }
            val days = binding.etCowInsemDays.text.toString().trim().toIntOrNull() ?: 35

            val batch = BovineSensorDataBatch(
                earTag = tag,
                daysSinceInsemination = days,
                mode = BovineMeasurementMode.HANDHELD_RAPID_SCAN,
                chewsPerMinute = 53,
                audioSnrDb = 13.8f,
                vocalizationDetected = false,
                cameraRespirationBpm = 21.5f,
                vulvaTightnessScore = 0.88f,
                motionRestingIndex = 0.82f,
                hadObservedEstrusSinceAI = false
            )

            val result = BovineLateFusionClassifier.classify(batch)

            binding.cardBoviPregnancyResult.visibility = View.VISIBLE
            binding.tvBoviCowTagTitle.text = result.earTag
            binding.tvBoviProbabilityBadge.text = String.format("%%%d GEBELİK OLASILIĞI", (result.probability * 100).toInt())

            when (result.riskLevel) {
                BovinePregnancyRiskLevel.HIGH -> {
                    binding.tvBoviProbabilityBadge.setBackgroundColor(colorStatusGood)
                    binding.cardBoviPregnancyResult.strokeColor = colorStatusGood
                }
                BovinePregnancyRiskLevel.INCONCLUSIVE -> {
                    binding.tvBoviProbabilityBadge.setBackgroundColor(colorStatusWarn)
                    binding.cardBoviPregnancyResult.strokeColor = colorStatusWarn
                }
                BovinePregnancyRiskLevel.LOW -> {
                    binding.tvBoviProbabilityBadge.setBackgroundColor(colorStatusAlert)
                    binding.cardBoviPregnancyResult.strokeColor = colorStatusAlert
                }
            }

            binding.tvBoviRiskDesc.text = result.riskLevel.descriptionTr
            binding.tvBoviUltrasoundWindow.text = "🎯 Ultrason Zamanı: " + result.optimalUltrasoundWindow
            binding.tvBoviRecommendation.text = "Öneri: " + result.veterinaryRecommendation
            binding.tvBoviXaiReasons.text = result.xaiReasons.joinToString("\n• ", prefix = "• ")

            Toast.makeText(this, "${result.earTag} için sığır gebelik taraması tamamlandı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOviScanIntegration() {
        // OviScan Hızlı Gebelik Ön Tarama Tetikleyicisi
        binding.btnRunOviScan.setOnClickListener {
            val tag = binding.etSheepTag.text.toString().trim().ifEmpty { "TR-06-KOYUN-01" }
            val days = binding.etInsemDays.text.toString().trim().toIntOrNull() ?: 42

            val batch = SensorDataBatch(
                earTag = tag,
                daysSinceInsemination = days,
                mode = MeasurementMode.SPOT_CHECK,
                acousticEventCount = 3,
                audioSnrDb = 14.2f,
                estimatedPitchHz = 185f,
                motionAverageMagnitude = 0.62f,
                lyingRatio = 0.44f,
                cameraRespirationBpm = 31.0f
            )

            val result = LateFusionClassifier.classify(batch)

            binding.cardOviResult.visibility = View.VISIBLE
            binding.tvOviTagTitle.text = result.earTag
            binding.tvOviProbabilityBadge.text = String.format("%%%d GEBELİK OLASILIĞI", (result.probability * 100).toInt())

            when (result.riskLevel) {
                PregnancyRiskLevel.HIGH -> {
                    binding.tvOviProbabilityBadge.setBackgroundColor(colorStatusGood)
                    binding.cardOviResult.strokeColor = colorStatusGood
                }
                PregnancyRiskLevel.INCONCLUSIVE -> {
                    binding.tvOviProbabilityBadge.setBackgroundColor(colorStatusWarn)
                    binding.cardOviResult.strokeColor = colorStatusWarn
                }
                PregnancyRiskLevel.LOW -> {
                    binding.tvOviProbabilityBadge.setBackgroundColor(colorStatusAlert)
                    binding.cardOviResult.strokeColor = colorStatusAlert
                }
            }

            binding.tvOviRiskDesc.text = result.riskLevel.descriptionTr
            binding.tvOviRecommendation.text = "Öneri: " + result.riskLevel.recommendedActionTr
            binding.tvOviXaiReasons.text = result.xaiReasons.joinToString("\n• ", prefix = "• ")

            Toast.makeText(this, "${result.earTag} için OviScan taraması tamamlandı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startAlertService()
    }

    private fun startAlertService() {
        val intent = Intent(this, FarmerAlertListenerService::class.java).apply {
            putExtra(FarmerAlertListenerService.EXTRA_BROKER_URI, "tcp://192.168.1.100:1883")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateDashboardCounters() {
        val totalAlerts = alertList.size
        val pendingInsemination = alertList.count { !it.isAcknowledged }

        binding.tvActiveAlertCount.text = totalAlerts.toString()
        binding.tvInseminationCount.text = pendingInsemination.toString()

        if (totalAlerts > 0) {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvAlerts.visibility = View.VISIBLE
        } else {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvAlerts.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (alertList.isEmpty() && FarmerAlertListenerService.activeAlerts.isNotEmpty()) {
            alertList.addAll(FarmerAlertListenerService.activeAlerts)
            adapter.notifyDataSetChanged()
        }
        updateDashboardCounters()
    }
}
