# -*- coding: utf-8 -*-
"""
core/config.py — Uygulama Sabitleri, GitHub Otomatik Güncelleme Adresleri,
Dizin Yolları, Konuşmacı & TTS Ses Listeleri, Font Aileleri,
Yüksek Kontrastlı Aydınlık/Karanlık Tema Paletleri ve Hata Günlüğü (v40 Final).
"""

import os
import datetime
import traceback

APP_VERSION = "40.0"
GITHUB_REPO = "muallimun/arapca-telaffuz-asistani"
GITHUB_REPO_URL = f"https://github.com/{GITHUB_REPO}"

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
ASSETS_DIR = os.path.join(BASE_DIR, "assets")
CATEGORIES_DIR = os.path.join(ASSETS_DIR, "categories")
CUSTOM_AUDIO_DIR = os.path.join(ASSETS_DIR, "custom_audio")
TTS_CACHE_DIR = os.path.join(ASSETS_DIR, "tts_cache")
TTS_DIR = TTS_CACHE_DIR  # core/audio_engine.py uyumluluğu
RECORDINGS_DIR = os.path.join(BASE_DIR, "recordings")
BEEP_PATH = os.path.join(ASSETS_DIR, "beep.wav")
LOG_FILE_PATH = os.path.join(BASE_DIR, "uygulama.log")
VERSION_JSON_PATH = os.path.join(BASE_DIR, "version.json")

for _d in (DATA_DIR, ASSETS_DIR, CATEGORIES_DIR, CUSTOM_AUDIO_DIR, TTS_CACHE_DIR, RECORDINGS_DIR):
    os.makedirs(_d, exist_ok=True)

WORDS_DB_PATH = os.path.join(DATA_DIR, "kelimeler.db")
DIALOGUES_DB_PATH = os.path.join(DATA_DIR, "diyaloglar.db")
SENTENCES_DB_PATH = os.path.join(DATA_DIR, "cumleler.db")
SETTINGS_DB_PATH = os.path.join(DATA_DIR, "ayarlar.db")
SOZLUK_DB_PATH = os.path.join(DATA_DIR, "sozluk.db")

JSON_DIALOGUES_PATH = os.path.join(DATA_DIR, "diyalog_paketi.json")
JSON_WORDS_PATH = os.path.join(DATA_DIR, "kelimeler.json")
CSV_SENTENCES_PATH = os.path.join(DATA_DIR, "anahtar_cumleler.csv")

WHISPER_MODEL_NAME = "small"
SAMPLERATE = 16000

# Diyalog ve TTS Seslendirme Sabitleri (core/audio_engine.py için)
FEMALE_SPEAKERS = {
    "deniz", "ayşe", "ayse", "fatma", "zeynep", "elif", "meryem", "سارة", "فاطمة",
    "عائشة", "زينب", "مريم", "ليلى", "نور", "سلمى", "هدى", "أم", "الأم", "المعلمة",
    "الطالبة", "الموظفة", "البائعة", "الطبيبة", "الممرضة", "مضيفة", "المضيفة", "خديجة",
    "رقية", "سمية", "حفصة", "أسماء", "اسماء", "درية", "duriye", "leyla", "sara",
    "merve", "esra", "büşra", "busra", "anne", "öğretmen (k)", "kız", "kadın"
}

MALE_SPEAKERS = {
    "ömer", "omer", "ahmet", "mehmet", "ali", "mustafa", "حسن", "حسين",
    "أحمد", "محمد", "علي", "عمر", "خالد", "طارق", "يوسف", "إبراهيم", "ابراهيم",
    "سعيد", "كريم", "أب", "الأب", "المعلم", "الطالب", "الموظف", "البائع", "الطبيب",
    "السائق", "المدير", "العميد", "تاجر", "التاجر", "baba", "öğretmen", "erkek"
}

FEMALE_VOICES = [
    "ar-SA-ZariyahNeural",
    "ar-EG-SalmaNeural",
    "ar-AE-FatimaNeural",
    "ar-JO-SanaNeural",
]

MALE_VOICES = [
    "ar-SA-HamedNeural",
    "ar-EG-ShakirNeural",
    "ar-AE-HamdanNeural",
    "ar-JO-TaimNeural",
]

DEFAULT_FEMALE_VOICE = "ar-SA-ZariyahNeural"
DEFAULT_MALE_VOICE = "ar-SA-HamedNeural"

AR_FONT_FAMILY = "Traditional Arabic"
ANALYSIS_FONT_FAMILY = "Segoe UI"

BG_COLOR = "#0f172a"
CARD_COLOR = "#1e293b"
PANEL_COLOR = "#162032"
ACCENT = "#ff6b6b"
GREEN = "#00e676"
RED = "#ef4444"
BLUE = "#3b82f6"
YELLOW = "#ffd369"
PURPLE = "#8b5cf6"
TEXT_LIGHT = "#f8fafc"
TEXT_DIM = "#94a3b8"
TEXT_AR = "#ffd369"
CHAT_BG = "#0b1322"

# Yüksek Kontrastlı Tema Paletleri (Açık temada gri butonlar üzerinde koyu lacivert metin garantisi)
THEMES = {
    "dark": {
        "BG_COLOR": "#0f172a",
        "CARD_COLOR": "#1e293b",
        "PANEL_COLOR": "#162032",
        "CHAT_BG": "#0b1322",
        "TEXT_MAIN": "#f8fafc",
        "TEXT_DIM": "#94a3b8",
        "TEXT_AR": "#ffd369",
        "ACCENT_HEAD": "#ffd369",
        "BORDER": "#2b3b55",
        "BTN_SEC": "#263859",
        "BUBBLE_LEFT": "#1b2838",
        "BUBBLE_RIGHT": "#1f3044",
    },
    "light": {
        "BG_COLOR": "#f1f5f9",
        "CARD_COLOR": "#ffffff",
        "PANEL_COLOR": "#e2e8f0",
        "CHAT_BG": "#f8fafc",
        "TEXT_MAIN": "#0f172a",
        "TEXT_DIM": "#334155",
        "TEXT_AR": "#1e3a8a",
        "ACCENT_HEAD": "#1e3a8a",
        "BORDER": "#94a3b8",
        "BTN_SEC": "#cbd5e1",
        "BUBBLE_LEFT": "#ffffff",
        "BUBBLE_RIGHT": "#eff6ff",
    }
}

FONT_UI = ("Segoe UI", 10)
FONT_UI_B = ("Segoe UI", 10, "bold")
FONT_AR = (AR_FONT_FAMILY, 44, "bold")
FONT_VERDICT = ("Segoe UI", 15, "bold")
FONT_PROMPT = ("Segoe UI", 14, "bold")


def log_error(context_msg: str, exc: Exception = None) -> None:
    try:
        ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with open(LOG_FILE_PATH, "a", encoding="utf-8") as f:
            f.write(f"[{ts}] {context_msg}\n")
            if exc is not None:
                f.write(traceback.format_exc() + "\n")
    except Exception:
        pass