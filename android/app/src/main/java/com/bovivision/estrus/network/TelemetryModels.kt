package com.bovivision.estrus.network

import com.google.gson.annotations.SerializedName

data class TelemetryPayload(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("cow_id") val cowId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("metrics") val metrics: MetricsData,
    @SerializedName("health") val health: HealthData
)

data class MetricsData(
    @SerializedName("fps") val fps: Double,
    @SerializedName("tail_angle_deg") val tailAngleDeg: Double,
    @SerializedName("exposure_rate_pct") val exposureRatePct: Double,
    @SerializedName("exposure_duration_sec") val exposureDurationSec: Double,
    @SerializedName("defecation_events_filtered") val defecationEventsFiltered: Int,
    @SerializedName("fly_swat_events_filtered") val flySwatEventsFiltered: Int
)

data class HealthData(
    @SerializedName("cpu_temp_c") val cpuTempC: Double,
    @SerializedName("ram_free_mb") val ramFreeMb: Long,
    @SerializedName("battery_pct") val batteryPct: Int
)

data class EstrusAlertPayload(
    @SerializedName("alert_id") val alertId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("cow_id") val cowId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("confidence_score") val confidenceScore: Double,
    @SerializedName("exposure_rate_5min") val exposureRate5min: Double,
    @SerializedName("total_exposed_duration_sec") val totalExposedDurationSec: Double,
    @SerializedName("action_required") val actionRequired: String,
    @SerializedName("snapshot_base64") val snapshotBase64: String? = null
)
