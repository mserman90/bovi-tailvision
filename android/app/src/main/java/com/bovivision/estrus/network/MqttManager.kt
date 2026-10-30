package com.bovivision.estrus.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.LinkedList
import java.util.Queue

class MqttManager(
    private val context: Context,
    private val serverUri: String,
    private val clientId: String
) {
    companion object {
        private const val TAG = "MqttManager"
        private const val MAX_OFFLINE_QUEUE = 200
    }

    private var client: MqttAsyncClient? = null
    private val gson = Gson()
    private val offlineQueue: Queue<Pair<String, MqttMessage>> = LinkedList()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isConnected = false

    fun connect() {
        try {
            client = MqttAsyncClient(serverUri, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false
                connectionTimeout = 10
                keepAliveInterval = 30
            }

            client?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "MQTT bağlantısı başarılı: $serverUri")
                    isConnected = true
                    flushOfflineQueue()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT bağlantı hatası: ${exception?.message}")
                    isConnected = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT başlatılamadı: ${e.message}")
        }
    }

    fun publishTelemetry(cowId: String, payload: TelemetryPayload) {
        val topic = "farm/tailvision/$cowId/telemetry"
        val json = gson.toJson(payload)
        val message = MqttMessage(json.toByteArray(Charsets.UTF_8)).apply {
            qos = 1
            isRetained = false
        }
        dispatchMessage(topic, message)
    }

    fun publishAlert(cowId: String, alert: EstrusAlertPayload) {
        val topic = "farm/tailvision/$cowId/estrus_alert"
        val json = gson.toJson(alert)
        val message = MqttMessage(json.toByteArray(Charsets.UTF_8)).apply {
            qos = 2 // Yüksek öncelik
            isRetained = true
        }
        dispatchMessage(topic, message)
    }

    private fun dispatchMessage(topic: String, message: MqttMessage) {
        scope.launch {
            if (isConnected && client?.isConnected == true) {
                try {
                    client?.publish(topic, message)
                    Log.d(TAG, "Mesaj yayınlandı -> $topic")
                } catch (e: Exception) {
                    Log.e(TAG, "Mesaj yayınlanamadı, kuyruğa alınıyor: ${e.message}")
                    enqueueOffline(topic, message)
                }
            } else {
                enqueueOffline(topic, message)
            }
        }
    }

    @Synchronized
    private fun enqueueOffline(topic: String, message: MqttMessage) {
        if (offlineQueue.size >= MAX_OFFLINE_QUEUE) {
            offlineQueue.poll() // En eskiyi at
        }
        offlineQueue.offer(Pair(topic, message))
    }

    @Synchronized
    private fun flushOfflineQueue() {
        while (!offlineQueue.isEmpty() && client?.isConnected == true) {
            val item = offlineQueue.poll() ?: break
            try {
                client?.publish(item.first, item.second)
            } catch (e: Exception) {
                Log.e(TAG, "Kuyruk boşaltılırken hata: ${e.message}")
                break
            }
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
            client?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Kapatma hatası: ${e.message}")
        } finally {
            isConnected = false
        }
    }
}
