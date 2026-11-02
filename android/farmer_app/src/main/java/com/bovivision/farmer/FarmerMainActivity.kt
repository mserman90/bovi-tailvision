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
        // Ana Sekme Değiştirici: Büyükbaş (Bovi) vs Küçükbaş (OviScan)
        binding.btnTabBovi.setOnClickListener {
            binding.layoutBoviView.visibility = View.VISIBLE
            binding.layoutOviView.visibility = View.GONE
            binding.btnTabBovi.setBackgroundColor(Color.WHITE)
            binding.btnTabBovi.setTextColor(Color.parseColor("#1B5E20"))
            binding.btnTabOvi.setBackgroundColor(Color.TRANSPARENT)
            binding.btnTabOvi.setTextColor(Color.WHITE)
        }

        binding.btnTabOvi.setOnClickListener {
            binding.layoutBoviView.visibility = View.GONE
            binding.layoutOviView.visibility = View.VISIBLE
            binding.btnTabOvi.setBackgroundColor(Color.WHITE)
            binding.btnTabOvi.setTextColor(Color.parseColor("#1B5E20"))
            binding.btnTabBovi.setBackgroundColor(Color.TRANSPARENT)
            binding.btnTabBovi.setTextColor(Color.WHITE)
        }

        // Bovine Alt Sekme Değiştirici: Ahır Alarmları vs Elde Gebelik Taraması
        binding.btnSubTabBoviBarn.setOnClickListener {
            binding.layoutBoviBarnAlarms.visibility = View.VISIBLE
            binding.layoutBoviPregnancyScan.visibility = View.GONE
            binding.btnSubTabBoviBarn.setBackgroundColor(Color.parseColor("#1B5E20"))
            binding.btnSubTabBoviBarn.setTextColor(Color.WHITE)
            binding.btnSubTabBoviPregnancy.setBackgroundColor(Color.TRANSPARENT)
            binding.btnSubTabBoviPregnancy.setTextColor(Color.parseColor("#1B5E20"))
        }

        binding.btnSubTabBoviPregnancy.setOnClickListener {
            binding.layoutBoviBarnAlarms.visibility = View.GONE
            binding.layoutBoviPregnancyScan.visibility = View.VISIBLE
            binding.btnSubTabBoviPregnancy.setBackgroundColor(Color.parseColor("#1B5E20"))
            binding.btnSubTabBoviPregnancy.setTextColor(Color.WHITE)
            binding.btnSubTabBoviBarn.setBackgroundColor(Color.TRANSPARENT)
            binding.btnSubTabBoviBarn.setTextColor(Color.parseColor("#1B5E20"))
        }

        // Ahır Kamera İstasyonundan Canlı Alarm Dinleme
        FarmerAlertListenerService.onAlertReceivedListener = { alert ->
            runOnUiThread {
                adapter.addAlert(alert)
                updateDashboardCounters()
            }
        }

        // Broker bağlantı durumu
        FarmerAlertListenerService.onConnectionStateChanged = { connected ->
            runOnUiThread {
                if (connected) {
                    binding.tvConnectionStatus.text = "Ahır IoT Broker: Aktif (ReproScan Canlı)"
                    binding.tvConnectionStatus.setTextColor(Color.parseColor("#C8E6C9"))
                } else {
                    binding.tvConnectionStatus.text = "Ahır IoT Broker: Bağlantı Kesildi"
                    binding.tvConnectionStatus.setTextColor(Color.parseColor("#FFCDD2"))
                }
            }
        }
    }

    private fun setupBovinePregnancyIntegration() {
        // Sığır Cep Telefonuyla Gebelik Ön Tarama Tetikleyicisi
        binding.btnRunBoviPregnancyScan.setOnClickListener {
            val tag = binding.etCowTag.text.toString().trim().ifEmpty { "TR-06-3842" }
            val days = binding.etCowInsemDays.text.toString().trim().toIntOrNull() ?: 35

            // Telefon sensör verileri batch'i oluştur
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

            // Sığır Multimodal Late Fusion Sınıflandırıcıyı Çalıştır
            val result = BovineLateFusionClassifier.classify(batch)

            // UI'da Sonucu Göster
            binding.cardBoviPregnancyResult.visibility = View.VISIBLE
            binding.tvBoviCowTagTitle.text = result.earTag
            binding.tvBoviProbabilityBadge.text = String.format("%%%d GEBELİK OLASILIĞI", (result.probability * 100).toInt())

            when (result.riskLevel) {
                BovinePregnancyRiskLevel.HIGH -> {
                    binding.tvBoviProbabilityBadge.setBackgroundColor(Color.parseColor("#2E7D32"))
                    binding.cardBoviPregnancyResult.strokeColor = Color.parseColor("#2E7D32")
                }
                BovinePregnancyRiskLevel.INCONCLUSIVE -> {
                    binding.tvBoviProbabilityBadge.setBackgroundColor(Color.parseColor("#F57F17"))
                    binding.cardBoviPregnancyResult.strokeColor = Color.parseColor("#F57F17")
                }
                BovinePregnancyRiskLevel.LOW -> {
                    binding.tvBoviProbabilityBadge.setBackgroundColor(Color.parseColor("#C62828"))
                    binding.cardBoviPregnancyResult.strokeColor = Color.parseColor("#C62828")
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

            // Dahili telefon sensör simülasyon batch'i oluştur
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

            // Multimodal Late Fusion Sınıflandırıcıyı Çalıştır
            val result = LateFusionClassifier.classify(batch)

            // UI'da Sonucu Göster
            binding.cardOviResult.visibility = View.VISIBLE
            binding.tvOviTagTitle.text = result.earTag
            binding.tvOviProbabilityBadge.text = String.format("%%%d GEBELİK OLASILIĞI", (result.probability * 100).toInt())

            when (result.riskLevel) {
                PregnancyRiskLevel.HIGH -> {
                    binding.tvOviProbabilityBadge.setBackgroundColor(Color.parseColor("#2E7D32"))
                    binding.cardOviResult.strokeColor = Color.parseColor("#2E7D32")
                }
                PregnancyRiskLevel.INCONCLUSIVE -> {
                    binding.tvOviProbabilityBadge.setBackgroundColor(Color.parseColor("#F57F17"))
                    binding.cardOviResult.strokeColor = Color.parseColor("#F57F17")
                }
                PregnancyRiskLevel.LOW -> {
                    binding.tvOviProbabilityBadge.setBackgroundColor(Color.parseColor("#C62828"))
                    binding.cardOviResult.strokeColor = Color.parseColor("#C62828")
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
