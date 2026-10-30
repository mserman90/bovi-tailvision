#!/usr/bin/env python3
"""
BOVI-TAILVISION ÇİFTLİK MERKEZİ SUNUCU VE ÇİFTÇİ BİLDİRİM KÖPRÜSÜ
Ahır yerel ağındaki Android uç cihazlardan (Bovi-TailVision) gelen
kızgınlık (östrus) alarmlarını toplar, çiftçinin mobil uygulamasına (MQTT),
ve isteğe bağlı olarak Telegram/SMS kanallarına anlık bildirim iletir.
"""

import json
import time
import os
from datetime import datetime, timedelta
import paho.mqtt.client as mqtt

BROKER_HOST = os.environ.get("BROKER_HOST", "localhost")
BROKER_PORT = int(os.environ.get("BROKER_PORT", 1883))
TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")

TOPIC_TELEMETRY = "farm/tailvision/+/telemetry"
TOPIC_ALERT = "farm/tailvision/+/estrus_alert"

def calculate_insemination_window(alert_timestamp):
    """
    AM/PM Tohumlama Kuralı:
    Kızgınlık başlangıcından 12-18 saat sonrası en yüksek döl tutma oranıdır.
    """
    alert_time = datetime.fromtimestamp(alert_timestamp)
    start_window = alert_time + timedelta(hours=12)
    end_window = alert_time + timedelta(hours=18)
    
    period = "Sabah Tespit Edildi -> İkindi/Akşam Tohumlayınız" if alert_time.hour in range(4, 13) else "Akşam Tespit Edildi -> Ertesi Sabah Tohumlayınız"
    return f"{start_window.strftime('%d.%m %H:%M')} ile {end_window.strftime('%H:%M')} arası ({period})"

def send_telegram_alert(cow_id, rate, duration, timing_text):
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        return
    try:
        import urllib.request
        import urllib.parse
        msg = (
            f"🚨 *BOVI-TAILVISION KIZGINLIK ALARMI*\n\n"
            f"🐄 *İnek No:* `{cow_id}`\n"
            f"📊 *Vulva Açık Kalma Oranı:* %{rate:.1f}\n"
            f"⏱️ *Toplam Açık Süre:* {duration:.0f} sn\n"
            f"⏰ *Önerilen Tohumlama Zamanı:*\n{timing_text}\n\n"
            f"_Lütfen tohumlama hazırlıklarını başlatın._"
        )
        url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
        data = urllib.parse.urlencode({"chat_id": TELEGRAM_CHAT_ID, "text": msg, "parse_mode": "Markdown"}).encode()
        urllib.request.urlopen(url, data=data, timeout=5)
        print("[BİLGİ] Telegram bildirimi çiftçinin cep telefonuna iletildi.")
    except Exception as e:
        print(f"[UYARI] Telegram bildirimi gönderilemedi: {e}")

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[BAŞARILI] MQTT Broker'a bağlandı ({BROKER_HOST}:{BROKER_PORT})")
        client.subscribe(TOPIC_TELEMETRY)
        client.subscribe(TOPIC_ALERT)
        print(f"[DİNLENİYOR] Konular: {TOPIC_TELEMETRY} | {TOPIC_ALERT}")
    else:
        print(f"[HATA] Bağlantı başarısız, Kod: {rc}")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode('utf-8'))
        topic = msg.topic

        if "estrus_alert" in topic:
            handle_estrus_alert(payload)
        elif "telemetry" in topic:
            handle_telemetry(payload)
    except Exception as e:
        print(f"[HATA] Mesaj işlenemedi: {e}")

def handle_estrus_alert(data):
    cow_id = data.get("cow_id", "Bilinmeyen")
    exposure_rate = data.get("exposure_rate_5min", 0.0)
    duration_sec = data.get("total_exposed_duration_sec", 0.0)
    timestamp = data.get("timestamp", time.time())
    timing_text = calculate_insemination_window(timestamp)

    print("\n" + "=" * 75)
    print(f"🚨🚨 [KIZGINLIK (ÖSTRUS) TESPİT EDİLDİ - ÇİFTÇİYE BİLDİRİLİYOR] 🚨🚨")
    print(f"🐄 İnek Küpe No: {cow_id}")
    print(f"📈 5 Dakikalık Açık Kalma Oranı: %{exposure_rate:.1f}")
    print(f"⌛ Açık Süre: {duration_sec:.1f} saniye")
    print(f"⏰ AM/PM Tohumlama Penceresi: {timing_text}")
    print("=" * 75 + "\n")

    # Çiftçinin telefonuna harici yedek bildirim (Telegram/SMS)
    send_telegram_alert(cow_id, exposure_rate, duration_sec, timing_text)

    # Günlüğe Kaydet
    with open("estrus_alerts.csv", "a", encoding="utf-8") as f:
        f.write(f"{datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d %H:%M:%S')},{cow_id},{exposure_rate:.1f},{duration_sec:.1f},{timing_text}\n")

def handle_telemetry(data):
    cow_id = data.get("cow_id")
    metrics = data.get("metrics", {})
    health = data.get("health", {})
    
    fps = metrics.get("fps", 0.0)
    angle = metrics.get("tail_angle_deg", 0.0)
    exposure_pct = metrics.get("exposure_rate_pct", 0.0)
    cpu_temp = health.get("cpu_temp_c", 0.0)

    print(f"[{time.strftime('%H:%M:%S')}] Telemetri -> İnek: {cow_id} | FPS: {fps:.1f} | Açı: {angle:.1f}° | Açık Oranı: %{exposure_pct:.1f} | CPU: {cpu_temp:.1f}°C")

if __name__ == "__main__":
    client = mqtt.Client(client_id="BoviFarmCentralServer")
    client.on_connect = on_connect
    client.on_message = on_message

    print("[BAŞLATILIYOR] Bovi-TailVision Çiftlik Sunucusu ve Bildirim Köprüsü Aktif...")
    try:
        client.connect(BROKER_HOST, BROKER_PORT, 60)
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n[DURDURULDU] Sunucu kapatıldı.")
    except Exception as e:
        print(f"[BAĞLANTI HATASI] {e}")
