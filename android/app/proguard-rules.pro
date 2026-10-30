# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Paho MQTT
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# Gson Models
-keepclassmembers class com.bovivision.estrus.network.** { *; }
-keep class com.bovivision.estrus.network.** { *; }
