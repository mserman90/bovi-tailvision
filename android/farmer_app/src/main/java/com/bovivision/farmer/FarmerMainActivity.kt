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
import com.bovivision.farmer.databinding.ActivityFarmerMainBinding
import com.bovivision.farmer.model.FarmerAlertModel
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
        // Servisten canlı alarm gelince UI'ı güncelle
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
                    binding.tvConnectionStatus.text = getString(R.string.status_connected)
                    binding.tvConnectionStatus.setTextColor(Color.parseColor("#C8E6C9"))
                } else {
                    binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                    binding.tvConnectionStatus.setTextColor(Color.parseColor("#FFCDD2"))
                }
            }
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
        // Servisteki mevcut alarmları yükle
        if (alertList.isEmpty() && FarmerAlertListenerService.activeAlerts.isNotEmpty()) {
            alertList.addAll(FarmerAlertListenerService.activeAlerts)
            adapter.notifyDataSetChanged()
        }
        updateDashboardCounters()
    }
}
