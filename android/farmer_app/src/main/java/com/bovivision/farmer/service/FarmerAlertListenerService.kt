package com.bovivision.farmer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.bovivision.farmer.FarmerMainActivity
import com.bovivision.farmer.R
import com.bovivision.farmer.model.FarmerAlertModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.CopyOnWriteArrayList

class FarmerAlertListenerService : LifecycleService() {

    companion object {
        private const val TAG = "FarmerAlertService"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val FOREGROUND_CHANNEL_ID = "farmer_status_channel"
        private const val HIGH_PRIORITY_CHANNEL_ID = "farmer_estrus_alert_channel"

        const val EXTRA_BROKER_URI = "EXTRA_BROKER_URI"
        val activeAlerts = CopyOnWriteArrayList<FarmerAlertModel>()
        var onAlertReceivedListener: ((FarmerAlertModel) -> Unit)? = null
        var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): FarmerAlertListenerService = this@FarmerAlertListenerService
    }

    private val binder = LocalBinder()
    private var mqttClient: MqttAsyncClient? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var brokerUri = "tcp://192.168.1.100:1883"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val uri = intent?.getStringExtra(EXTRA_BROKER_URI)
        if (!uri.isNullOrEmpty()) {
            brokerUri = uri
        }

        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification("Ahır izleniyor..."))
        connectToBroker()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun connectToBroker() {
        scope.launch {
            try {
                val clientId = "FarmerPhone_" + System.currentTimeMillis().toString().takeLast(6)
                mqttClient = MqttAsyncClient(brokerUri, clientId, MemoryPersistence())

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = false
                    connectionTimeout = 10
                    keepAliveInterval = 30
                }

                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "Broker bağlantısı kesildi: ${cause?.message}")
                        onConnectionStateChanged?.invoke(false)
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.payload?.let { String(it, Charsets.UTF_8) } ?: return
                        Log.i(TAG, "Alarm mesajı geldi: $payload")
                        handleIncomingAlert(payload)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient?.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "Çiftçi broker bağlantısı başarılı: $brokerUri")
                        onConnectionStateChanged?.invoke(true)
                        // Tüm ahırdaki kızgınlık alarmlarına abone ol
                        mqttClient?.subscribe("farm/tailvision/+/estrus_alert", 2)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Broker bağlantı hatası: ${exception?.message}")
                        onConnectionStateChanged?.invoke(false)
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "MQTT başlatma hatası: ${e.message}")
                onConnectionStateChanged?.invoke(false)
            }
        }
    }

    private fun handleIncomingAlert(json: String) {
        try {
            val alert = gson.fromJson(json, FarmerAlertModel::class.java) ?: return
            activeAlerts.add(0, alert) // En başa ekle

            // 1. Çiftçiyi Ses ve Titreşimle Uyar
            triggerAudibleAlert()

            // 2. Heads-Up Yüksek Öncelikli Bildirim Gönder
            showEstrusNotification(alert)

            // 3. UI Dinleyicisine İlet
            onAlertReceivedListener?.invoke(alert)

        } catch (e: Exception) {
            Log.e(TAG, "Alarm işleme hatası: ${e.message}")
        }
    }

    private fun triggerAudibleAlert() {
        try {
            // Titreşim deseni (Dikkat çekici aralıklı uyarı: 500ms dur, 1000ms titre, 500ms dur, 1000ms titre)
            val pattern = longArrayOf(0, 800, 400, 800, 400, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Titreşim hatası: ${e.message}")
        }
    }

    private fun showEstrusNotification(alert: FarmerAlertModel) {
        val intent = Intent(this, FarmerMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, alert.alertId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, HIGH_PRIORITY_CHANNEL_ID)
            .setSmallIcon(R.drawable.badge_alert_bg)
            .setContentTitle("🚨 KIZGINLIK TESPİT EDİLDİ: ${alert.cowId}")
            .setContentText("Açık Kalma: %${String.format("%.1f", alert.exposureRate5min)} | ${alert.getInseminationTimingText()}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("İnek No: ${alert.cowId}\n" +
                            "Vulva Açık Kalma Oranı: %${String.format("%.1f", alert.exposureRate5min)}\n" +
                            "Toplam Açık Süre: ${alert.totalExposedDurationSec} sn\n" +
                            "Önerilen Tohumlama Zamanı: ${alert.getInseminationTimingText()}")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alert.alertId.hashCode(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "ReproScan Takip Servisi",
                NotificationManager.IMPORTANCE_LOW
            )

            val alertSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alertChannel = NotificationChannel(
                HIGH_PRIORITY_CHANNEL_ID,
                getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alert_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800)
                setSound(alertSoundUri, audioAttributes)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundNotification(status: String): Notification {
        val intent = Intent(this, FarmerMainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("ReproScan Takip Aktif")
            .setContentText(status)
            .setSmallIcon(R.drawable.badge_alert_bg)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (_: Exception) {}
    }
}
