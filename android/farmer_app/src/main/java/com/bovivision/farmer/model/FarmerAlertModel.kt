package com.bovivision.farmer.model

import com.google.gson.annotations.SerializedName

data class FarmerAlertModel(
    @SerializedName("alert_id") val alertId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("cow_id") val cowId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("confidence_score") val confidenceScore: Double,
    @SerializedName("exposure_rate_5min") val exposureRate5min: Double,
    @SerializedName("total_exposed_duration_sec") val totalExposedDurationSec: Double,
    @SerializedName("action_required") val actionRequired: String,
    var isAcknowledged: Boolean = false
) {
    /**
     * AM/PM Tohumlama Kuralı Hesabı:
     * Kızgınlık sabah fark edildiyse -> Aynı günün ikindisi/akşamı (12 saat sonra)
     * Kızgınlık akşamüstü/gece fark edildiyse -> Ertesi sabah (12 saat sonra)
     */
    fun getInseminationTimingText(): String {
        val alertDate = java.util.Date(timestamp * 1000)
        val cal = java.util.Calendar.getInstance().apply { time = alertDate }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)

        val calStart = java.util.Calendar.getInstance().apply {
            time = alertDate
            add(java.util.Calendar.HOUR_OF_DAY, 12)
        }
        val calEnd = java.util.Calendar.getInstance().apply {
            time = alertDate
            add(java.util.Calendar.HOUR_OF_DAY, 18)
        }

        val format = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("tr"))
        val startStr = format.format(calStart.time)
        val endStr = format.format(calEnd.time)

        val periodDesc = if (hour in 4..12) "Sabah Tespit (Akşam Tohumla)" else "Akşam Tespit (Ertesi Sabah Tohumla)"
        return "$startStr ile $endStr arası ($periodDesc)"
    }
}
