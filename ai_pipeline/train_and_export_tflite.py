#!/usr/bin/env python3
"""
BOVI-TAILVISION MODEL TRAINING & INT8 PTQ QUANTIZATION PIPELINE
Bu betik; YOLOv8n-pose / YOLO11n-pose modelini sığır kuyruk/vulva veri seti üzerinde eğitir,
eski model Android cihazlar için INT8 Post-Training Quantization (PTQ) ile kuantize eder
ve Android assets klasörüne kopyalar.
"""

import os
import shutil
import glob
import cv2
import numpy as np
from ultralytics import YOLO

MODEL_INPUT_SIZE = 320
DATASET_YAML = "dataset_spec.yaml"
OUTPUT_ASSETS_DIR = os.path.abspath("../android/app/src/main/assets")

def train_cow_pose_model():
    print("=" * 60)
    print("[AŞAMA 1] YOLO11n-pose Modeli Eğitimi Başlatılıyor...")
    print("=" * 60)

    # Nano varyantı: Düşük parametre sayısı (< 3M), düşük bellek gereksinimi
    model = YOLO("yolo11n-pose.pt")

    # Eğitim Hiperparametreleri (Edge AI için optimize)
    results = model.train(
        data=DATASET_YAML,
        epochs=100,
        imgsz=MODEL_INPUT_SIZE,
        batch=16,
        device="0" if os.environ.get("CUDA_VISIBLE_DEVICES") else "cpu",
        workers=4,
        save=True,
        project="bovi_tail_runs",
        name="tail_pose_run"
    )
    print("[BİLGİ] Model eğitimi tamamlandı. En iyi ağırlıklar: runs/pose/train/weights/best.pt")
    return model

def export_int8_tflite(model):
    print("=" * 60)
    print("[AŞAMA 2] INT8 PTQ Kuantizasyonu ve TFLite İhracı...")
    print("=" * 60)

    # TFLite INT8 Export
    # Ultralytics, TensorFlow Lite Converter üzerinden representative dataset ile tam INT8 kuantizasyon yapar
    exported_path = model.export(
        format="tflite",
        imgsz=MODEL_INPUT_SIZE,
        int8=True,               # INT8 Kuantizasyon (Model boyutu < 4 MB)
        data=DATASET_YAML,       # Kuantizasyon kalibrasyonu için veri seti
        optimize=True
    )

    print(f"[BAŞARILI] Kuantize TFLite Modeli Oluşturuldu: {exported_path}")
    return exported_path

def deploy_to_android(tflite_path):
    print("=" * 60)
    print("[AŞAMA 3] Model Android Assets Dizinine Kopyalanıyor...")
    print("=" * 60)

    os.makedirs(OUTPUT_ASSETS_DIR, exist_ok=True)
    target_path = os.path.join(OUTPUT_ASSETS_DIR, "bovi_tail_pose.tflite")

    if os.path.exists(tflite_path):
        shutil.copy2(tflite_path, target_path)
        file_size_mb = os.path.getsize(target_path) / (1024 * 1024)
        print(f"[BAŞARILI] Model Hedefe Kopyalandı: {target_path}")
        print(f"[METRİK] Nihai Model Boyutu: {file_size_mb:.2f} MB (< 6 MB Şartname Kriteri)")
    else:
        print(f"[HATA] Dışa aktarılan dosya bulunamadı: {tflite_path}")

if __name__ == "__main__":
    # 1. Modeli Eğit (Eğer önceden eğitilmiş model varsa doğrudan yüklenebilir)
    best_weights = "bovi_tail_runs/tail_pose_run/weights/best.pt"
    if os.path.exists(best_weights):
        print(f"[BİLGİ] Mevcut eğitilmiş ağırlıklar bulundu: {best_weights}")
        model = YOLO(best_weights)
    else:
        model = train_cow_pose_model()

    # 2. TFLite INT8 Kuantizasyonu
    tflite_path = export_int8_tflite(model)

    # 3. Android Projesine Dağıtım
    deploy_to_android(tflite_path)
