# -*- coding: utf-8 -*-
"""
core/audio_engine.py — Çok Kanallı Ses Motoru (Konuşma / Efekt / Bip Ayrımı),
Mikrofondan Referans Ses Kaydedici, Dalga Analizi, Edge-TTS ve Whisper 16kHz Motoru.
"""

import os
import sys
import time
import wave
import hashlib
import asyncio
import threading
import subprocess
import urllib.request
from typing import Optional, Tuple, List

import numpy as np
import pygame
import edge_tts

from core.config import (
    BASE_DIR, DATA_DIR, ASSETS_DIR, CUSTOM_AUDIO_DIR, TTS_DIR,
    BEEP_PATH, SAMPLERATE, FEMALE_SPEAKERS, log_error
)
from core.arabic_nlp import clean_invisible_marks


# ==============================================================================
# 1. C-MODÜL GÜVENLİK KONTROLÜ (WHISPER & SOUNDDEVICE — .EXE UYUMLU DOĞRUDAN IMPORT)
# ==============================================================================
WHISPER_AVAILABLE: bool = False
WhisperModel = None
try:
    from faster_whisper import WhisperModel
    WHISPER_AVAILABLE = True
except Exception as ex:
    log_error("Faster-Whisper import hatası", ex)

AUDIO_REC_AVAILABLE: bool = False
sd = None
try:
    import sounddevice as sd
    AUDIO_REC_AVAILABLE = True
except Exception as ex:
    log_error("sounddevice import hatası", ex)

# Kısa kritik bölümler (load/play/stop) için kilit
audio_lock = threading.Lock()
_cached_beep_sound: Optional[pygame.mixer.Sound] = None
_cached_effects: dict = {}


# ==============================================================================
# 2. ÇOK KANALLI SES YÖNETİMİ (KONUŞMA / EFEKT / BİP AYRIMI)
# ==============================================================================
def ensure_mixer_initialized() -> bool:
    """Pygame mixer motorunu çok kanallı (en az 4 kanal) olarak başlatır."""
    try:
        if not pygame.mixer.get_init():
            pygame.mixer.init(frequency=SAMPLERATE, size=-16, channels=2, buffer=1024)
        if pygame.mixer.get_num_channels() < 4:
            pygame.mixer.set_num_channels(4)
        return True
    except Exception as ex:
        log_error("Pygame mixer başlatılamadı", ex)
        return False


def generate_beep() -> None:
    if os.path.exists(BEEP_PATH):
        return
    try:
        fs = 44100
        duration = 0.15
        frequency = 880.0
        t = np.linspace(0, duration, int(fs * duration), False)
        tone = np.sin(frequency * t * 2 * np.pi)
        audio = np.int16(tone * 32767 * 0.3)
        with wave.open(BEEP_PATH, "w") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(fs)
            wf.writeframes(audio.tobytes())
    except Exception as ex:
        log_error("Bip sesi oluşturulamadı", ex)


def play_beep_nonblocking() -> None:
    """
    Mikrofon açılış bip sesini Channel(1) üzerinden ana ses kanalını (music) bozmadan
    ve arayüzü bloklamadan çalar.
    """
    global _cached_beep_sound
    if not os.path.exists(BEEP_PATH):
        generate_beep()
    if not ensure_mixer_initialized():
        return
    try:
        with audio_lock:
            if _cached_beep_sound is None and os.path.exists(BEEP_PATH):
                _cached_beep_sound = pygame.mixer.Sound(BEEP_PATH)
            if _cached_beep_sound is not None:
                ch = pygame.mixer.Channel(1)
                ch.play(_cached_beep_sound)
    except Exception as ex:
        log_error("Bip çalma hatası", ex)


def play_effect_nonblocking(effect_full_path: str) -> None:
    """
    Övgü/ödül ses efektlerini (ahsent, mumtaz vb.) Channel(2) üzerinden çalar;
    böylece ana konuşma sesiyle (music) ve kart geçişleriyle çakışmaz.
    """
    if not effect_full_path or not os.path.exists(effect_full_path):
        return
    if not ensure_mixer_initialized():
        return
    try:
        with audio_lock:
            snd = _cached_effects.get(effect_full_path)
            if snd is None:
                snd = pygame.mixer.Sound(effect_full_path)
                _cached_effects[effect_full_path] = snd
            ch = pygame.mixer.Channel(2)
            ch.play(snd)
    except Exception as ex:
        log_error(f"Efekt çalma hatası ({effect_full_path})", ex)


def stop_all_audio_channels(include_effects: bool = False) -> None:
    """
    Konuşma kanalını (music) ve bip kanalını (Channel 1) durdurur.
    include_effects=False (varsayılan) olduğunda Channel 2'deki övgü sesini kesmez;
    böylece sonraki karta geçilirken 'Ahsent / Mümtaz' sesi yarıda kalmaz.
    """
    try:
        if not pygame.mixer.get_init():
            return
        with audio_lock:
            if pygame.mixer.music.get_busy():
                pygame.mixer.music.stop()
            channels_to_stop = (1, 2) if include_effects else (1,)
            for ch_id in channels_to_stop:
                try:
                    ch = pygame.mixer.Channel(ch_id)
                    if ch.get_busy():
                        ch.stop()
                except Exception:
                    pass
    except Exception as ex:
        log_error("Ses durdurma hatası", ex)


# ==============================================================================
# 3. EDGE-TTS, URL İNDİRİCİ VE MEDYA ÇÖZÜMLEYİCİ
# ==============================================================================
def detect_voice_for_speaker(speaker_name: str, gender: str = "") -> str:
    if gender and gender.upper() == "K":
        return "ar-SA-ZariyahNeural"
    if gender and gender.upper() == "E":
        return "ar-SA-HamedNeural"
    if not speaker_name:
        return "ar-SA-HamedNeural"
    s_low = speaker_name.strip().lower()
    for fem in FEMALE_SPEAKERS:
        if fem in s_low:
            return "ar-SA-ZariyahNeural"
    return "ar-SA-HamedNeural"


def get_tts_audio(text: str, voice: str = "ar-SA-HamedNeural", slow: bool = False) -> Optional[str]:
    if not text:
        return None
    clean_text = clean_invisible_marks(text)
    if not clean_text:
        return None
    rate_str = "-25%" if slow else "+0%"
    hash_input = f"{voice}:{rate_str}:{clean_text}".encode("utf-8")
    text_hash = hashlib.md5(hash_input).hexdigest()
    tts_path = os.path.join(TTS_DIR, f"{text_hash}.mp3")
    if not os.path.exists(tts_path):
        async def _generate():
            communicate = edge_tts.Communicate(clean_text, voice, rate=rate_str)
            await communicate.save(tts_path)
        try:
            asyncio.run(_generate())
        except Exception as ex:
            log_error(f"Edge-TTS üretim hatası (Metin: {clean_text[:30]})", ex)
            return None
    return tts_path if os.path.exists(tts_path) else None


def download_audio_from_url(url: str, item_id: str = "aud") -> str:
    url = url.strip()
    if not url.startswith(("http://", "https://")):
        raise ValueError("Geçerli bir http:// veya https:// bağlantısı giriniz.")
    url_hash = hashlib.md5(url.encode("utf-8")).hexdigest()[:10]
    ext = ".wav" if ".wav" in url.lower() else ".mp3"
    filename = f"{item_id}_{url_hash}{ext}"
    full_dest = os.path.join(CUSTOM_AUDIO_DIR, filename)

    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as response, open(full_dest, "wb") as out_file:
            out_file.write(response.read())
    except Exception as ex:
        log_error(f"URL ses indirme hatası ({url})", ex)
        raise

    return f"custom_audio/{filename}"


def resolve_local_media_path(rel_or_abs_path: Optional[str]) -> Optional[str]:
    if not rel_or_abs_path:
        return None
    p = rel_or_abs_path.strip()
    if not p:
        return None
    if p.startswith(("http://", "https://")):
        try:
            rel_dl = download_audio_from_url(p, "url")
            p = rel_dl
        except Exception as ex:
            log_error(f"URL medya çözümleme hatası ({p})", ex)
            return None
    for candidate in (p, os.path.join(ASSETS_DIR, p), os.path.join(BASE_DIR, p)):
        if os.path.exists(candidate) and os.path.isfile(candidate):
            return candidate
    return None


def load_wav_as_16k_float32(wav_path: str) -> np.ndarray:
    with wave.open(wav_path, "rb") as wf:
        frames = wf.readframes(wf.getnframes())
        sr = wf.getframerate()
    audio_np = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
    if sr != 16000 and len(audio_np) > 0:
        duration = len(audio_np) / sr
        target_len = int(duration * 16000)
        audio_np = np.interp(
            np.linspace(0.0, 1.0, target_len, endpoint=False),
            np.linspace(0.0, 1.0, len(audio_np), endpoint=False),
            audio_np
        ).astype(np.float32)
    return audio_np


# ==============================================================================
# 4. DOĞRUDAN MİKROFONDAN REFERANS SES KAYDEDİCİ VE DALGA ANALİZİ
# ==============================================================================
class AdminMicRecorder:
    def __init__(self):
        self.recording: bool = False
        self.frames: list = []
        self.stream = None

    def start(self) -> None:
        if not AUDIO_REC_AVAILABLE:
            raise RuntimeError("Ses kayıt modülü (sounddevice) bulunamadı.")
        if self.recording:
            return
        stop_all_audio_channels(include_effects=True)

        self.frames = []
        self.recording = True

        def _cb(indata, frame_count, time_info, status):
            if self.recording:
                self.frames.append(indata.copy())

        self.stream = sd.InputStream(samplerate=SAMPLERATE, channels=1, callback=_cb)
        self.stream.start()

    def stop_and_save(self, item_prefix: str = "ref") -> str:
        self.recording = False
        if self.stream:
            try:
                self.stream.stop()
                self.stream.close()
            except Exception as ex:
                log_error("AdminMicRecorder stream kapatma hatası", ex)
            self.stream = None

        if not self.frames:
            raise ValueError("Ses kaydedilemedi (mikrofondan veri gelmedi).")

        audio = np.concatenate(self.frames, axis=0).flatten()
        self.frames = []
        duration = len(audio) / SAMPLERATE
        if duration < 0.3:
            raise ValueError("Kayıt çok kısa sürdü (en az 0.3 saniye konuşun).")

        abs_a = np.abs(audio)
        peak = np.max(abs_a)
        if peak > 0.01:
            active_idx = np.where(abs_a > (peak * 0.06))[0]
            if len(active_idx) > 100:
                pad = int(SAMPLERATE * 0.12)
                s_i = max(0, active_idx[0] - pad)
                e_i = min(len(audio), active_idx[-1] + pad)
                audio = audio[s_i:e_i]
            audio = (audio / peak) * 0.88

        safe_prefix = "".join(ch for ch in str(item_prefix) if ch.isalnum() or ch == "_")[:25] or "ref"
        filename = f"{safe_prefix}_{int(time.time())}.wav"
        full_path = os.path.join(CUSTOM_AUDIO_DIR, filename)

        audio_int16 = np.int16(np.clip(audio, -1.0, 1.0) * 32767)
        with wave.open(full_path, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLERATE)
            wf.writeframes(audio_int16.tobytes())

        return f"custom_audio/{filename}"


def extract_audio_envelope(audio_path: str, num_bars: int = 48) -> Tuple[List[float], float]:
    if not audio_path or not os.path.exists(audio_path):
        return [0.1] * num_bars, 0.0

    samples = None
    duration = 0.0

    if audio_path.lower().endswith(".wav"):
        try:
            with wave.open(audio_path, "rb") as wf:
                n_frames = wf.getnframes()
                sr = wf.getframerate()
                duration = n_frames / float(sr) if sr > 0 else 0.0
                raw = wf.readframes(n_frames)
                samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
                if wf.getnchannels() > 1:
                    samples = samples[::wf.getnchannels()]
        except Exception as ex:
            log_error(f"WAV dalga analizi hatası ({audio_path})", ex)
            samples = None

    if samples is None:
        try:
            ensure_mixer_initialized()
            snd = pygame.mixer.Sound(audio_path)
            duration = snd.get_length()
            arr = pygame.sndarray.array(snd).astype(np.float32)
            samples = arr[:, 0] if arr.ndim > 1 else arr
        except Exception as ex:
            log_error(f"MP3 dalga analizi hatası ({audio_path})", ex)
            samples = None

    if samples is None or len(samples) < num_bars:
        return [0.15] * num_bars, round(duration, 2)

    chunks = np.array_split(np.abs(samples), num_bars)
    rms_vals = [float(np.sqrt(np.mean(np.square(c)))) if len(c) > 0 else 0.0 for c in chunks]
    max_v = max(rms_vals) if rms_vals else 1.0
    if max_v <= 1e-6:
        max_v = 1.0

    normalized_bars = [max(0.06, min(1.0, v / max_v)) for v in rms_vals]
    return normalized_bars, round(duration, 2)