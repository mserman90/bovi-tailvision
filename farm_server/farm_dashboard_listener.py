#!/usr/bin/env python3
"""
REPROSCAN ÇİFTLİK MERKEZİ SUNUCU VE BİRLEŞİK BİLDİRİM KÖPRÜSÜ
Akıllı Ruminant Üreme ve Karar Destek Platformu:
1. ReproScan Bovine (Sığır Kızgınlık / Östrus Alarmları)
2. ReproScan Ovine (Koyun Gebelik Ön Tarama Sonuçları)
verilerini toplar, çiftçinin mobil uygulamasına (MQTT) ve Telegram'a anlık iletir.
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

# MQTT Konuları (Bovine + Ovine)
TOPIC_BOVI_TELEMETRY = "farm/tailvision/+/telemetry"
TOPIC_BOVI_ALERT = "farm/tailvision/+/estrus_alert"
TOPIC_OVI_SCREENING = "farm/oviscan/+/pregnancy_screen"

def calculate_insemination_window(alert_timestamp):
    alert_time = datetime.fromtimestamp(alert_timestamp)
    start_window = alert_time + timedelta(hours=12)
    end_window = alert_time + timedelta(hours=18)
    period = "Sabah Tespit -> İkindi/Akşam Tohumla" if alert_time.hour in range(4, 13) else "Akşam Tespit -> Ertesi Sabah Tohumla"
    return f"{start_window.strftime('%d.%m %H:%M')} ile {end_window.strftime('%H:%M')} arası ({period})"

def send_telegram_alert(msg_text):
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        return
    try:
        import urllib.request
        import urllib.parse
        url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
        data = urllib.parse.urlencode({"chat_id": TELEGRAM_CHAT_ID, "text": msg_text, "parse_mode": "Markdown"}).encode()
        urllib.request.urlopen(url, data=data, timeout=5)
        print("[BİLGİ] Telegram bildirimi iletildi.")
    except Exception as e:
        print(f"[UYARI] Telegram bildirimi gönderilemedi: {e}")

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[BAŞARILI] ReproScan MQTT Broker'a bağlandı ({BROKER_HOST}:{BROKER_PORT})")
        client.subscribe(TOPIC_BOVI_TELEMETRY)
        client.subscribe(TOPIC_BOVI_ALERT)
        client.subscribe(TOPIC_OVI_SCREENING)
        print(f"[DİNLENİYOR] ReproScan Bovine & Ovine üreme kanalları aktif.")
    else:
        print(f"[HATA] Bağlantı başarısız, Kod: {rc}")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode('utf-8'))
        topic = msg.topic

        if "tailvision" in topic and "estrus_alert" in topic:
            handle_bovi_alert(payload)
        elif "oviscan" in topic and "pregnancy_screen" in topic:
            handle_ovi_screen(payload)
        elif "telemetry" in topic:
            handle_telemetry(payload)
    except Exception as e:
        print(f"[HATA] Mesaj işlenemedi: {e}")

def handle_bovi_alert(data):
    cow_id = data.get("cow_id", "Bilinmeyen")
    exposure_rate = data.get("exposure_rate_5min", 0.0)
    duration_sec = data.get("total_exposed_duration_sec", 0.0)
    timestamp = data.get("timestamp", time.time())
    timing_text = calculate_insemination_window(timestamp)

    print("\n" + "=" * 75)
    print(f"🚨 [REPROSCAN BOVINE: SIĞIR KIZGINLIK ALARMI] İnek No: {cow_id}")
    print(f"📈 5 Dakikalık Açık Kalma Oranı: %{exposure_rate:.1f} | Süre: {duration_sec:.1f} sn")
    print(f"⏰ AM/PM Tohumlama Penceresi: {timing_text}")
    print("=" * 75 + "\n")

    msg = (
        f"🚨 *REPROSCAN BOVINE KIZGINLIK ALARMI*\n\n"
        f"🐄 *İnek No:* `{cow_id}`\n"
        f"📊 *Vulva Açık Kalma:* %{exposure_rate:.1f}\n"
        f"⏰ *Tohumlama Zamanı:*\n{timing_text}"
    )
    send_telegram_alert(msg)

    with open("reproscan_reproductive_log.csv", "a", encoding="utf-8") as f:
        f.write(f"{datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d %H:%M:%S')},BOVINE_ESTRUS,{cow_id},%{exposure_rate:.1f},{timing_text}\n")

def handle_ovi_screen(data):
    ear_tag = data.get("ear_tag", "TR-06-KOYUN-01")
    prob_pct = data.get("probability_pct", 84.0)
    risk_level = data.get("risk_level", "HIGH")
    action = data.get("recommended_action", "Gebe rasyonuna geçiş planlayınız.")

    print("\n" + "-" * 75)
    print(f"🐑 [REPROSCAN OVINE: KOYUN GEBELİK ÖN TARAMASI] Küpe No: {ear_tag}")
    print(f"📊 Gebelik Olasılığı: %{prob_pct:.1f} ({risk_level})")
    print(f"💡 Öneri: {action}")
    print("-" * 75 + "\n")

    msg = (
        f"🐑 *REPROSCAN OVINE GEBELİK ÖN TARAMASI*\n\n"
        f"🏷️ *Koyun Küpe No:* `{ear_tag}`\n"
        f"📊 *Gebelik Olasılığı:* %{prob_pct:.1f} ({risk_level})\n"
        f"💡 *Öneri:* {action}"
    )
    send_telegram_alert(msg)

    with open("reproscan_reproductive_log.csv", "a", encoding="utf-8") as f:
        f.write(f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')},OVINE_PREGNANCY,{ear_tag},%{prob_pct:.1f},{risk_level}\n")

def handle_telemetry(data):
    cow_id = data.get("cow_id")
    metrics = data.get("metrics", {})
    fps = metrics.get("fps", 0.0)
    angle = metrics.get("tail_angle_deg", 0.0)
    print(f"[{time.strftime('%H:%M:%S')}] Telemetri -> İnek: {cow_id} | FPS: {fps:.1f} | Açı: {angle:.1f}°")

if __name__ == "__main__":
    client = mqtt.Client(client_id="ReproScanCentralServer")
    client.on_connect = on_connect
    client.on_message = on_message

    print("[BAŞLATILIYOR] ReproScan Akıllı Ruminant Çiftlik Sunucusu Dinleme Modunda...")
    try:
        client.connect(BROKER_HOST, BROKER_PORT, 60)
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n[DURDURULDU] Sunucu kapatıldı.")
    except Exception as e:
        print(f"[BAĞLANTI HATASI] {e}")
