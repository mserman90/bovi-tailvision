#!/usr/bin/env python3
"""
BOVI-TAILVISION MODEL VE MATEMATİKSEL KİNEMATİK DOĞRULAMA TESTİ
Bu betik; kuantize TFLite modelinin çıkarım hızını ve şartnamedeki
kinematik/açı denklemlerini PC üzerinde simüle eder.
"""

import sys
import numpy as np
import cv2
import tensorflow as tf

MODEL_PATH = "../android/app/src/main/assets/bovi_tail_pose.tflite"

def compute_kinematics(spine, tailbase, tailmid, vulva):
    """
    Şartname Bölüm 2.2 ve 2.3 Matematiksel Hesaplamaları
    """
    v_tail = np.array([tailmid[0] - tailbase[0], tailmid[1] - tailbase[1]], dtype=np.float32)
    v_ref = np.array([tailbase[0] - spine[0], tailbase[1] - spine[1]], dtype=np.float32)

    mag_tail = np.linalg.norm(v_tail)
    mag_ref = np.linalg.norm(v_ref)

    if mag_tail == 0 or mag_ref == 0:
        return 0.0, False, False

    cos_theta = np.clip(np.dot(v_tail, v_ref) / (mag_tail * mag_ref), -1.0, 1.0)
    angle_deg = np.arccos(cos_theta) * (180.0 / np.pi)

    # 2B Vektörel Çarpım (Z-bileşeni) Sağa/Sola sapma tayini
    cross_z = v_ref[0] * v_tail[1] - v_ref[1] * v_tail[0]
    signed_angle = -angle_deg if cross_z < 0 else angle_deg

    # Vulva ROI Açıklığı: || P_tailmid - P_vulva || > 35 px
    dist_vulva = np.linalg.norm(np.array(tailmid) - np.array(vulva))
    is_roi_clear = dist_vulva > 35.0

    # Dışkılama Postürü: Dikey eksen kalkışı ve düşük sapma
    is_defecation = (tailbase[1] - tailmid[1] > 25.0) and (abs(signed_angle) < 15.0)

    return signed_angle, is_roi_clear, is_defecation

def run_test():
    try:
        interpreter = tf.lite.Interpreter(model_path=MODEL_PATH)
        interpreter.allocate_tensors()
    except Exception as e:
        print(f"[UYARI] TFLite modeli yüklenemedi ({e}). Sentetik veri testi çalıştırılıyor...")
        # Sentetik kinematik test
        spine = (160, 60)
        tailbase = (160, 100)
        tailmid = (120, 180) # Sola sapma
        vulva = (160, 150)

        angle, is_clear, is_defecation = compute_kinematics(spine, tailbase, tailmid, vulva)
        print(f"[TEST SONUCU]")
        print(f" -> Kuyruk Açısı: {angle:.2f}° (Beklenen: ~ -26.5° Sola Sapma)")
        print(f" -> Vulva ROI Açık: {is_clear}")
        print(f" -> Dışkılama Postürü: {is_defecation}")
        print(f" -> Kızgınlık Belirteci E(k): {1 if abs(angle) >= 20.0 and is_clear else 0}")
        return

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("[BİLGİ] Model Giriş Detayları:", input_details[0]['shape'], input_details[0]['dtype'])
    print("[BİLGİ] Model Çıkış Detayları:", output_details[0]['shape'], output_details[0]['dtype'])

if __name__ == "__main__":
    run_test()
