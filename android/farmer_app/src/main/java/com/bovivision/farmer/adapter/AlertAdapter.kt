package com.bovivision.farmer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bovivision.farmer.databinding.ItemAlertCardBinding
import com.bovivision.farmer.model.FarmerAlertModel

class AlertAdapter(
    private val alerts: MutableList<FarmerAlertModel>,
    private val onAcknowledgeClick: (FarmerAlertModel) -> Unit
) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    inner class AlertViewHolder(val binding: ItemAlertCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alerts[position]
        val b = holder.binding

        b.tvCowId.text = "İNEK: ${alert.cowId}"
        b.tvExposureRateBadge.text = String.format("%%% .1f ÖSTRUS", alert.exposureRate5min)

        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(alert.timestamp * 1000))
        b.tvAlertTime.text = "Tespit: $timeStr | Vulva Açık Süre: ${alert.totalExposedDurationSec.toInt()} sn"

        b.tvInseminationWindow.text = alert.getInseminationTimingText()

        if (alert.isAcknowledged) {
            b.btnMarkDone.text = "Tohumlama Kaydedildi ✓"
            b.btnMarkDone.isEnabled = false
            b.btnMarkDone.alpha = 0.5f
        } else {
            b.btnMarkDone.text = "Tohumlama Yapıldı"
            b.btnMarkDone.isEnabled = true
            b.btnMarkDone.alpha = 1.0f
            b.btnMarkDone.setOnClickListener {
                alert.isAcknowledged = true
                notifyItemChanged(position)
                onAcknowledgeClick(alert)
            }
        }
    }

    override fun getItemCount(): Int = alerts.size

    fun addAlert(alert: FarmerAlertModel) {
        alerts.add(0, alert)
        notifyItemInserted(0)
    }
}
