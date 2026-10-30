package com.bovivision.estrus.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

class DeviceHealthMonitor(private val context: Context) {

    fun getCpuTemperature(): Double {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/platform/soc/soc:qcom,tsens/temp"
        )

        for (path in thermalPaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                try {
                    val rawStr = file.readText().trim()
                    val rawVal = rawStr.toDoubleOrNull() ?: continue
                    return if (rawVal > 1000.0) rawVal / 1000.0 else rawVal
                } catch (_: Exception) { }
            }
        }
        return 38.5 // Varsayılan ortam sıcaklığı referansı
    }

    fun getFreeRamMb(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    fun getBatteryPercentage(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100 // Doğrudan DC bağlıysa
        }
    }
}
