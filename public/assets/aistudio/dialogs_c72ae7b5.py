# -*- coding: utf-8 -*-
"""
ui/dialogs.py — 🖨️ Karma Kaynaklı (Kelime+Diyalog+Cümle) Yazılı Sınav & 📖 'Ders Notu / Okuma Metni' PDF Üretici,
🎧 '▶️ SINAVI BAŞLAT' Butonlu Dinle & Bul Sınav Merkezi,
🖼️ Görsel Seçme/Silme Destekli Kelime Düzenleyici, 🔤 Sütun Sıralamalı (A➔Z / Z➔A) Sarf Sözlüğü,
📊 Raporlarda Öğrenci Ses Kaydını (.wav) Dinleme Aracı,
👋 7 Adımlı Başlangıç Turu (Onboarding) ve Zenginleştirilmiş Kullanım Kılavuzu (v40 Final).
"""

import os
import html
import math
import time
import wave
import base64
import shutil
import random
import zipfile
import datetime
import threading
import webbrowser
import tkinter as tk
from tkinter import messagebox, filedialog, ttk
import numpy as np
import pygame

from core.config import (
    APP_VERSION, GITHUB_REPO_URL,
    BASE_DIR, ASSETS_DIR, DATA_DIR, RECORDINGS_DIR, CATEGORIES_DIR, CUSTOM_AUDIO_DIR, SAMPLERATE,
    GREEN, RED, BLUE, YELLOW, PURPLE, ACCENT,
    FONT_UI, FONT_UI_B, AR_FONT_FAMILY,
    WORDS_DB_PATH, SENTENCES_DB_PATH, log_error
)
from core.arabic_nlp import (
    remove_diacritics, sanitize_arabic_sentence, sanitize_turkish_sentence,
    shape_arabic, compare_arabic, evaluate_sentence_words,
    analyze_phoneme_and_makhraj_errors
)
from core.database import (
    db_session, get_words, get_word_packages, delete_word_db,
    get_dialogues, get_dialogue_lines, get_dialogue_lines_with_titles,
    update_single_dialogue_line_db, delete_single_dialogue_line_db,
    get_dialogue_id_for_line, get_package_level_and_topic,
    get_key_sentences, get_sentence_packages,
    get_streak_and_badges, get_detailed_analytics_report, export_analytics_to_csv,
    repair_all_databases, generate_listening_warmup_questions,
    update_db, record_daily_activity, get_config_val, set_config_val
)
from core.dictionary import (
    get_word_morphology_and_family, search_dictionary_entries, THEMATIC_RULES
)
from core.audio_engine import (
    AUDIO_REC_AVAILABLE, sd, audio_lock,
    download_audio_from_url, resolve_local_media_path, get_tts_audio,
    AdminMicRecorder, extract_audio_envelope, load_wav_as_16k_float32,
    play_beep_nonblocking, ensure_mixer_initialized
)
from core.updater import open_updater_window
from PIL import Image, ImageTk


class ConfettiParticle:
    def __init__(self, canvas, x, y):
        self.canvas = canvas
        self.x, self.y = x, y
        self.vx, self.vy = random.uniform(-3, 3), random.uniform(-8, -3)
        self.gravity, self.drag = 0.25, 0.98
        self.color = random.choice([RED, GREEN, BLUE, YELLOW, "#ff00ff", "#00ffff"])
        self.size = random.randint(4, 8)
        self.angle, self.spin = random.uniform(0, 360), random.uniform(-10, 10)
        self.id = self.canvas.create_polygon(
            x, y, x + self.size, y, x + self.size, y + self.size, x, y + self.size,
            fill=self.color, outline="", tags="confetti"
        )

    def update(self):
        self.vy += self.gravity
        self.vx *= self.drag
        self.x += self.vx
        self.y += self.vy
        self.angle += self.spin
        s = self.size * math.cos(math.radians(self.angle))
        if s == 0:
            s = 0.1
        self.canvas.coords(
            self.id, self.x, self.y, self.x + s, self.y,
            self.x + s, self.y + self.size, self.x, self.y + self.size
        )
        return self.y < 800


def get_recordings_stats() -> tuple:
    if not os.path.exists(RECORDINGS_DIR):
        return 0, 0.0
    files = [
        os.path.join(RECORDINGS_DIR, f)
        for f in os.listdir(RECORDINGS_DIR)
        if f.lower().endswith(".wav")
    ]
    total_bytes = sum(os.path.getsize(p) for p in files if os.path.isfile(p))
    return len(files), round(total_bytes / (1024 * 1024), 2)


def cleanup_recordings_folder(keep_latest: int = 0, exclude_path: str = None) -> int:
    if not os.path.exists(RECORDINGS_DIR):
        return 0
    files = [
        os.path.join(RECORDINGS_DIR, f)
        for f in os.listdir(RECORDINGS_DIR)
        if f.lower().endswith(".wav")
    ]
    files.sort(key=lambda p: os.path.getmtime(p), reverse=True)
    to_delete = files[keep_latest:] if keep_latest > 0 else files
    deleted = 0
    for p in to_delete:
        if exclude_path and os.path.normcase(p) == os.path.normcase(exclude_path):
            continue
        try:
            os.remove(p)
            deleted += 1
        except Exception as ex:
            log_error(f"Kayıt silme hatası ({p})", ex)
    return deleted


def find_latest_student_recording_for_id(item_id: str) -> str:
    """Öğrencinin belirli bir kelime/cümle ID'si için yaptığı en son .wav ses kaydını bulur."""
    if not item_id or not os.path.exists(RECORDINGS_DIR):
        return ""
    iid_clean = str(item_id).strip()
    prefixes = (f"{iid_clean}_", f"shadow_{iid_clean}_", f"rp_{iid_clean}_")
    matches = []
    for fn in os.listdir(RECORDINGS_DIR):
        if fn.lower().endswith(".wav") and fn.startswith(prefixes):
            full_p = os.path.join(RECORDINGS_DIR, fn)
            if os.path.isfile(full_p):
                matches.append(full_p)
    if not matches:
        return ""
    matches.sort(key=lambda p: os.path.getmtime(p), reverse=True)
    return matches[0]


def _get_brand_logo_base64_html() -> str:
    """Çalışma kağıdı, ders notu ve yazılı sınav anteti için logoyu Base64 IMG etiketi olarak döndürür."""
    for candidate in (
        os.path.join(BASE_DIR, "ui", "muallimun_logo.png"),
        os.path.join(ASSETS_DIR, "muallimun_logo.png"),
        os.path.join(BASE_DIR, "muallimun_logo.png")
    ):
        if os.path.exists(candidate) and os.path.isfile(candidate):
            try:
                with open(candidate, "rb") as f:
                    b64 = base64.b64encode(f.read()).decode("ascii")
                return f"<img src='data:image/png;base64,{b64}' style='height:54px;width:auto;margin-right:14px;' alt='Logo'/>"
            except Exception:
                pass
    return "<div style='font-size:38px;margin-right:12px;'>🕌</div>"

# ==============================================================================
# 0.0) 👤 KULLANICI / ÖĞRENCİ İSMİ KAYIT VE DÜZENLEME PENCERESİ (ayarlar.db)
# ==============================================================================
def open_student_name_dialog(app, force_show: bool = False):
    """
    Uygulama açıldığında 'student_name' boşsa VEYA hala varsayılan 'Öğrenci' olarak
    kalmışsa otomatik açılır; kullanıcı kendi adını girdiğinde bir sonraki açılışta sormaz.
    """
    current_name = (get_config_val("student_name", "") or "").strip()
    is_default_or_empty = (not current_name) or (current_name.lower() in ("öğrenci", "ogrenci"))

    if not is_default_or_empty and not force_show:
        return

    c = app.colors
    win = tk.Toplevel(app.root)
    win.title("👤 Öğrenci / Kullanıcı Bilgisi — Muallimun.Net")

    w_px, h_px = 540, 290
    sw = win.winfo_screenwidth()
    sh = win.winfo_screenheight()
    pos_x = max(0, (sw - w_px) // 2)
    pos_y = max(0, (sh - h_px) // 2 - 30)
    win.geometry(f"{w_px}x{h_px}+{pos_x}+{pos_y}")
    win.resizable(False, False)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()
    win.lift()
    win.focus_force()

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=12)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text="👋 HOŞ GELDİNİZ — ÖĞRENCİ / KULLANICI KAYDI",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold")
    ).pack(side="left")

    body = tk.Frame(win, bg=c["CARD_COLOR"], padx=24, pady=16)
    body.pack(fill="both", expand=True)

    tk.Label(
        body,
        text="Lütfen adınızı ve soyadınızı girin.\n(Bu isim ana ekranda, başarı karnenizde ve öğretmen raporlarında yer alacaktır):",
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI,
        justify="left", anchor="w", wraplength=490
    ).pack(anchor="w", fill="x", pady=(0, 12))

    default_disp = current_name if current_name else "Öğrenci"
    name_var = tk.StringVar(value=default_disp)
    ent_name = tk.Entry(
        body, textvariable=name_var, bg=c["BG_COLOR"], fg=c["TEXT_AR"],
        font=("Segoe UI", 13, "bold"), insertbackground=c["TEXT_MAIN"]
    )
    ent_name.pack(fill="x", ipady=5, pady=(0, 14))
    ent_name.focus_set()
    ent_name.select_range(0, tk.END)

    def save_name(event=None):
        val = name_var.get().strip() or "Öğrenci"
        set_config_val("student_name", val)
        if hasattr(app, "student_name_var"):
            app.student_name_var.set(val)
        win.destroy()
        if getattr(app, "current_mode", "") == "home":
            app._render_home_dashboard()
        app._update_stats_ui()
        app._show_toast(f"👋 Hoş geldin {val}!", GREEN, 2500)

    def on_close_name_win():
        val = name_var.get().strip() or "Öğrenci"
        set_config_val("student_name", val)
        if hasattr(app, "student_name_var"):
            app.student_name_var.set(val)
        win.destroy()
        if getattr(app, "current_mode", "") == "home":
            app._render_home_dashboard()

    ent_name.bind("<Return>", save_name)
    win.protocol("WM_DELETE_WINDOW", on_close_name_win)

    tk.Button(
        body, text="✅ KAYDET VE BAŞLA", command=save_name,
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 11, "bold"),
        relief="flat", pady=8, cursor="hand2"
    ).pack(fill="x")


# ==============================================================================
# 0.1) 👋 7 ADIMLI KAPSAMLI İNTERAKTİF BAŞLANGIÇ TURU (ONBOARDING)
# ==============================================================================
def open_onboarding_tour(app, force_show: bool = False):
    """
    Uygulamayı ilk kez açan bir kullanıcının 'Bu programın amacı nedir ve hangi ekranda
    ne yapabilirim?' sorularına net, anlaşılır yanıt veren 7 adımlı başlangıç rehberi.
    Tur kapandığında öğrenci ismi henüz girilmemişse ('Öğrenci' ise) isim penceresini açar.
    """
    if not force_show and get_config_val("onboarding_seen_v35", "0") == "1":
        app.root.after(150, lambda: open_student_name_dialog(app, force_show=False))
        return

    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("👋 Hoş Geldiniz — Uygulama Amacı ve Ekran Tanıtım Turu (7 Adım)")
    win.geometry("780x520")
    win.minsize(720, 480)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    steps = [
        {
            "badge": "ADIM 1 / 7 — UYGULAMANIN AMACI NEDİR?",
            "icon": "🎙️  🕌  🤖",
            "title": "Arapçayı Sadece Okuyarak Değil, Sesli Konuşarak Öğrenin!",
            "desc": (
                "• Bu uygulamanın temel amacı; Arapça öğrenenlerin en çok zorlandığı 'doğru telaffuz etme (mahreç)', "
                "'kelime köklerini tanıma (sarf)' ve 'akıcı konuşma (muhâdese)' becerilerini yapay zekâ desteğiyle geliştirmektir.\n\n"
                "• Uygulamadaki tüm kelime, cümle ve diyalogları önce doğru referans sesle dinler, ardından mikrofona "
                "kendiniz okursunuz. Yapay zekâ sesinizi analiz ederek doğrularınızı ve hatalarınızı anında ekranda gösterir."
            ),
            "tip": "💡 Üst menü çubuğundaki renkli butonları kullanarak istediğiniz çalışma ekranına her an geçiş yapabilirsiniz."
        },
        {
            "badge": "ADIM 2 / 7 — 🏛️ ANA SAYFA EKRANINDA NE YAPABİLİRSİNİZ?",
            "icon": "🏛️  📍  🔥",
            "title": "Başlangıç Merkezini Kullanma ve Kaldığınız Yerden Devam Etme",
            "desc": (
                "• Uygulama açıldığında karşınıza gelen '🏛️ Ana Sayfa' sizin çalışma merkezinizdir.\n\n"
                "• Sağ üst köşeden adınızı, günlük çalışma serinizi (🔥 Streak) ve puanınızı takip edebilir; "
                "'▶️ Kaldığım Yerden Devam Et' butonuyla son çalıştığınız ekrana tek tıkla dönebilir veya ortadaki "
                "4 büyük modül kartından bugün çalışmak istediğiniz bölümü seçebilirsiniz."
            ),
            "tip": "💡 İpucu: Sağ üstteki ☀️/🌙 butonuyla Aydınlık veya Karanlık temayı seçebilirsiniz."
        },
        {
            "badge": "ADIM 3 / 7 — 🏠 KELİME MODU EKRANINDA NE YAPABİLİRSİNİZ?",
            "icon": "🏠  🔊  🎤",
            "title": "Kelimeleri Dinleyin, Mikrofona Okuyun ve Gölgeleme Yapın",
            "desc": (
                "• Üstten bir kelime paketi ve seviye (A1-C1) seçerek kelime kartlarıyla çalışacağınız ekrandır:\n"
                "  1) '🔊 Kadın / Erkek' butonlarıyla kelimenin doğru telaffuzunu dinleyin.\n"
                "  2) '🎤 BAŞLA VE KONUŞ' butonuna (veya Boşluk tuşuna) basıp yeşil çerçeve parladığında mikrofona okuyun.\n"
                "  3) '🗣️ Gölgele' ile sesi duyduktan hemen sonra aynı ritimle tekrar etme antrenmanı yapın.\n"
                "  4) '📅 Günün 15 Tekrarı' ile unutmak üzere olduğunuz kelimeleri akıllı sırayla tekrar edin."
            ),
            "tip": "💡 Kısayol: Klavyeden [Boşluk] tuşu ses kaydını başlatır, [Sağ / Sol Ok] tuşları sonraki/önceki karta geçer."
        },
        {
            "badge": "ADIM 4 / 7 — 📖 SARF SÖZLÜĞÜ EKRANINDA NE YAPABİLİRSİNİZ?",
            "icon": "📖  🌿  🔍",
            "title": "Kelimelerin 3 Harfli Köklerini ve Türev Ailelerini Keşfedin",
            "desc": (
                "• Arapça kelimelerin hangi 3 asli harften (Sülâsî Kök) türediğini öğrenmek için kullanacağınız ekrandır.\n\n"
                "• Bu ekranda 1.500+ kelime arasında arama ve sıralama yapabilir; isimlerin Tekil/Çoğul (Müfret/Cemî), "
                "fiillerin ise Geçmiş/Şimdiki Zaman (Mâzî/Muzâri/Masdar) çekimlerini dinleyebilir, aynı kökten türeyen "
                "akraba kelimeleri inceleyip '🎤 Bu Kelimeyi Kartta Çalış' diyerek telaffuz pratiğine geçebilirsiniz."
            ),
            "tip": "💡 İpucu: Kelime Modunda da sözlük paketlerini seçtiğinizde kart üzerinde Altıgen Kök Rozeti açılır."
        },
        {
            "badge": "ADIM 5 / 7 — 💬 DİYALOG VE 📜 CÜMLE EKRANLARINDA NE YAPABİLİRSİNİZ?",
            "icon": "💬  🎭  📜",
            "title": "Karşılıklı Rol Yaparak Konuşun ve Cümle Telaffuzu Çalışın",
            "desc": (
                "• 💬 Diyalog Modu Ekranı: '📖 Öğrenme Modu'nda günlük hayat diyaloglarını satır satır dinleyebilirsiniz. "
                "'🎭 Ölçme / Canlandırma' modunda ise diyalogdaki bir kişiyi seçersiniz; karşı rolü bilgisayar seslendirir, "
                "sıra size geldiğinde mikrofona konuşarak diyaloğu canlı olarak tamamlarsınız.\n\n"
                "• 📜 Cümle Modu Ekranı: Binlerce anahtar cümleyi sesli okuyup cümle içindeki hangi kelimeleri doğru (yeşil), "
                "hangilerini hatalı (kırmızı) söylediğinizin analizini görebilirsiniz."
            ),
            "tip": "💡 İpucu: Cümle veya diyalog çalışırken '🙈 Arapçayı Gizle' seçeneğiyle kendinizi sınayabilirsiniz."
        },
        {
            "badge": "ADIM 6 / 7 — 🎯 SAĞ PANEL (KOÇ) VE 🎧 DİNLE & BUL SINAV EKRANI",
            "icon": "🎯  🎧  🏆",
            "title": "Harf Hatalarınızı Düzeltin ve 4 Şıklı Dinleme Sınavı Çözün",
            "desc": (
                "• 🎯 Sağ Panel (Canlı Mahreç & Hareke Koçu): Siz mikrofona konuştuğunuzda birbirine karıştırdığınız harfleri "
                "(ص-س, ذ-ز, ع-ء, ن-ر vb.) veya eksik uzatmaları kırmızıyla gösterip nasıl düzeltmeniz gerektiğini anlatır.\n\n"
                "• 🎧 Dinle & Bul Ekranı: Kelime, Sözlük, Cümle veya Diyalog havuzlarından 10, 20, 50 soruluk veya Sınırsız "
                "4 şıklı sesli dinleme-anlama testi çözebileceğiniz sınav ekranıdır."
            ),
            "tip": "💡 İpucu: Daha geniş bir çalışma alanı isterseniz üstteki '◧ Paneli Gizle' butonuna basabilirsiniz."
        },
        {
            "badge": "ADIM 7 / 7 — 📊 RAPORLAR, 🖨️ SINAV/DERS NOTU VE 🛠️ YÖNETİM EKRANI",
            "icon": "📊  🖨️  🛠️",
            "title": "Gelişim Karnenizi İnceleyin, A4 Sınav/Ders Notu Üretin ve İçerik Ekleyin",
            "desc": (
                "• 📊 Raporlar Ekranı: Kazandığınız başarı rozetlerini, paket ilerleme grafiklerinizi ve en çok hata yapılan "
                "kelimeleri görebileceğiniz, öğretmeninize göndermek için '📦 Ödev & Ses Paketi (.ZIP)' üretebileceğiniz ekrandır.\n\n"
                "• 🖨️ Sınav & Ders Notu / 🛠️ Yönetim Paneli: Öğretmenlerin A4 yazılı sınav kağıdı veya temiz okuma metni (PDF) "
                "çıktısı alabileceği; Excel'den kopyala-yapıştır veya dosya yükleme ile yeni içerik ekleyebileceği ekranlardır."
            ),
            "tip": "💡 Şimdi '🚀 Hemen Başla!' butonuna basarak adınızı kaydedip ilk Arapça pratiğinize başlayabilirsiniz!"
        }
    ]

    cur_step = [0]
    dont_show_var = tk.BooleanVar(value=True)

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=12)
    hdr.pack(fill="x")
    lbl_badge = tk.Label(hdr, text="", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold"))
    lbl_badge.pack(side="left")
    lbl_dots = tk.Label(hdr, text="", bg=c["PANEL_COLOR"], fg=GREEN if is_dark else "#047857", font=("Segoe UI", 13, "bold"))
    lbl_dots.pack(side="right")

    card = tk.Frame(win, bg=c["BG_COLOR"], padx=24, pady=16, highlightthickness=1, highlightbackground=c["BORDER"])
    card.pack(fill="both", expand=True, padx=20, pady=12)

    lbl_icon = tk.Label(card, text="", bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=("Segoe UI", 24, "bold"))
    lbl_icon.pack(pady=(2, 4))

    lbl_title = tk.Label(card, text="", bg=c["BG_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold"))
    lbl_title.pack(pady=(0, 8))

    lbl_desc = tk.Label(card, text="", bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 10), justify="left", wraplength=680)
    lbl_desc.pack(anchor="w", pady=(0, 10))

    lbl_tip = tk.Label(card, text="", bg=c["PANEL_COLOR"], fg=YELLOW if is_dark else "#1e3a8a", font=("Segoe UI", 9, "bold"), padx=12, pady=6, wraplength=670, justify="left")
    lbl_tip.pack(fill="x", side="bottom")

    footer = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=8)
    footer.pack(fill="x", side="bottom")

    chk_dont_show = tk.Checkbutton(
        footer, text="Bir daha açılışta gösterme", variable=dont_show_var,
        bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI
    )
    chk_dont_show.pack(side="left")

    def finish_tour():
        if dont_show_var.get():
            set_config_val("onboarding_seen_v35", "1")
        else:
            set_config_val("onboarding_seen_v35", "0")
        win.destroy()
        app.root.after(150, lambda: open_student_name_dialog(app, force_show=False))

    win.protocol("WM_DELETE_WINDOW", finish_tour)

    btn_next = tk.Button(footer, text="Sonraki Adım ▶", bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=16, pady=6, cursor="hand2")
    btn_next.pack(side="right", padx=(6, 0))

    btn_prev = tk.Button(footer, text="◀ Önceki", bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=12, pady=6, cursor="hand2")
    btn_prev.pack(side="right", padx=4)

    btn_skip = tk.Button(
        footer, text="⏭️ Turu Şimdi Atla", command=finish_tour,
        bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B, relief="flat", padx=12, pady=6, cursor="hand2"
    )
    btn_skip.pack(side="right", padx=8)

    def render_step():
        idx = cur_step[0]
        st = steps[idx]
        lbl_badge.config(text=st["badge"])
        dots = " ".join(["●" if i == idx else "○" for i in range(len(steps))])
        lbl_dots.config(text=dots)
        lbl_icon.config(text=st["icon"])
        lbl_title.config(text=st["title"])
        lbl_desc.config(text=st["desc"])
        lbl_tip.config(text=st["tip"])

        btn_prev.config(state="normal" if idx > 0 else "disabled")
        if idx == len(steps) - 1:
            btn_next.config(text="🚀 Hemen Başla!", bg=GREEN, fg="#0f172a", command=finish_tour)
            btn_skip.pack_forget()
        else:
            btn_next.config(text="Sonraki Adım ▶", bg=BLUE, fg="white", command=lambda: go_step(1))
            if not btn_skip.winfo_ismapped():
                btn_skip.pack(side="right", padx=8)

    def go_step(delta: int):
        cur_step[0] = max(0, min(len(steps) - 1, cur_step[0] + delta))
        render_step()

    btn_prev.config(command=lambda: go_step(-1))
    render_step()


# ==============================================================================
# 0.2) 📖 İNTERAKTİF SARF SÖZLÜĞÜ VE KÖK AİLESİ KEŞİF PENCERESİ (SÜTUN SIRALAMALI)
# ==============================================================================
def open_dictionary_explorer_window(app, initial_query: str = ""):
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("📖 Arapça Sarf Sözlüğü ve Sülâsî Kök Ailesi Keşif Atölyesi")
    win.geometry("980x640")
    win.minsize(880, 560)
    win.configure(bg=c["CARD_COLOR"])

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    win.lift()
    win.focus_force()

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text="📖 SARF SÖZLÜĞÜ VE SÜLÂSÎ KÖK AİLESİ ATÖLYESİ (Sütun Başlıklarına Tıklayarak Sıralayabilirsiniz)",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold")
    ).pack(side="left")
    lbl_total = tk.Label(hdr, text="", bg=c["PANEL_COLOR"], fg=GREEN if is_dark else "#047857", font=FONT_UI_B)
    lbl_total.pack(side="right")

    f_bar = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=8)
    f_bar.pack(fill="x")

    tk.Label(f_bar, text="🔍 Kök / Kelime / Anlam Ara:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    q_var = tk.StringVar(value=initial_query)
    ent_q = tk.Entry(f_bar, textvariable=q_var, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=("Segoe UI", 11, "bold"), insertbackground=c["TEXT_MAIN"], width=24)
    ent_q.pack(side="left", padx=6)

    tk.Label(f_bar, text="Tür:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=(8, 2))
    tur_var = tk.StringVar(value="Tümü")
    cmb_tur = ttk.Combobox(f_bar, textvariable=tur_var, values=["Tümü", "isim", "fiil"], state="readonly", width=7, font=FONT_UI)
    cmb_tur.pack(side="left", padx=2)

    tk.Label(f_bar, text="Kategori:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=(8, 2))
    cat_names = ["Tümü"] + [r[1] for r in THEMATIC_RULES] + ["⚡ Genel Fiiller & Eylemler", "📘 Genel Kavramlar & İsimler"]
    cat_var = tk.StringVar(value="Tümü")
    cmb_cat = ttk.Combobox(f_bar, textvariable=cat_var, values=cat_names, state="readonly", width=24, font=FONT_UI)
    cmb_cat.pack(side="left", padx=2)

    tk.Button(
        f_bar, text="🧹 Filtreyi Temizle",
        command=lambda: (q_var.set(""), tur_var.set("Tümü"), cat_var.set("Tümü"), refresh_list()),
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=8, pady=3, cursor="hand2"
    ).pack(side="right")

    body = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=4)
    body.pack(fill="both", expand=True)

    left_f = tk.Frame(body, bg=c["BG_COLOR"], bd=1, relief="solid")
    left_f.pack(side="left", fill="both", expand=True, padx=(0, 10))

    cols = ("kok", "tur", "ar", "türev", "tr")
    col_headers = {
        "kok": "Kök",
        "tur": "Tür",
        "ar": "Müfret / Mâzî",
        "türev": "Cemî / Muzâri & Masdar",
        "tr": "Türkçe Anlamı"
    }
    sort_state = {"col": None, "desc": False}

    tree = ttk.Treeview(left_f, columns=cols, show="headings", height=16)

    def sort_dict_column(col_key: str):
        if sort_state["col"] == col_key:
            sort_state["desc"] = not sort_state["desc"]
        else:
            sort_state["col"] = col_key
            sort_state["desc"] = False

        def _extract_sort_val(entry: dict) -> str:
            if col_key == "kok":
                return str(entry.get("kok", "")).strip()
            elif col_key == "tur":
                return str(entry.get("tur", "")).strip().lower()
            elif col_key == "ar":
                return remove_diacritics(entry.get("mufret") if entry.get("tur") == "isim" else entry.get("mazi", ""))
            elif col_key == "türev":
                return remove_diacritics(entry.get("cemi") if entry.get("tur") == "isim" else f"{entry.get('muzari', '')} {entry.get('masdar', '')}")
            else:
                return str(entry.get("anlam_tr", "")).strip().lower()

        cached_entries.sort(key=_extract_sort_val, reverse=sort_state["desc"])
        for ck, h_txt in col_headers.items():
            arrow = (" ▼" if sort_state["desc"] else " ▲") if ck == col_key else " ⇅"
            tree.heading(ck, text=h_txt + arrow, command=lambda c_k=ck: sort_dict_column(c_k))
        populate_tree_rows()

    for ck, h_txt in col_headers.items():
        tree.heading(ck, text=h_txt + " ⇅", command=lambda c_k=ck: sort_dict_column(c_k))

    tree.column("kok", width=75, anchor="center")
    tree.column("tur", width=60, anchor="center")
    tree.column("ar", width=135, anchor="e")
    tree.column("türev", width=165, anchor="e")
    tree.column("tr", width=205, anchor="w")

    sb = ttk.Scrollbar(left_f, orient="vertical", command=tree.yview)
    tree.configure(yscrollcommand=sb.set)
    tree.pack(side="left", fill="both", expand=True)
    sb.pack(side="right", fill="y")

    right_f = tk.Frame(body, bg=c["PANEL_COLOR"], width=320, padx=14, pady=12, highlightthickness=1, highlightbackground=c["BORDER"])
    right_f.pack(side="right", fill="y")
    right_f.pack_propagate(False)

    cv_badge = tk.Canvas(right_f, width=280, height=110, bg=c["PANEL_COLOR"], highlightthickness=0)
    cv_badge.pack(pady=(0, 6))

    lbl_det_ar = tk.Label(right_f, text="", bg=c["PANEL_COLOR"], fg=c["TEXT_AR"], font=(AR_FONT_FAMILY, 24, "bold"))
    lbl_det_ar.pack()

    lbl_det_tr = tk.Label(right_f, text="Soldaki listeden bir kelime seçin", bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 10, "bold"), wraplength=280, justify="center")
    lbl_det_tr.pack(pady=(2, 8))

    forms_box = tk.Frame(right_f, bg=c["PANEL_COLOR"])
    forms_box.pack(fill="x", pady=4)

    tk.Label(right_f, text="🌿 Aynı Kökten Türeyen Akraba Kelimeler:", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(8, 2))
    fam_box = tk.Frame(right_f, bg=c["BG_COLOR"], padx=8, pady=6, highlightthickness=1, highlightbackground=c["BORDER"])
    fam_box.pack(fill="both", expand=True, pady=(0, 8))

    selected_entry_data = [None]

    def draw_hex_badge(kok_str: str, emoji_str: str, kat_str: str):
        cv_badge.delete("all")
        cx, cy, r = 140, 52, 42
        pts = []
        for i in range(6):
            ang = math.radians(60 * i - 30)
            pts.extend([cx + r * math.cos(ang), cy + r * math.sin(ang)])
        cv_badge.create_polygon(pts, fill="#1e3a8a" if is_dark else "#dbeafe", outline="#38bdf8", width=2)
        kok_font_sz = 16 if len(kok_str or "") <= 5 else 13
        cv_badge.create_text(
            cx, cy - 4, text=kok_str, fill=YELLOW if is_dark else "#1e3a8a",
            font=(AR_FONT_FAMILY, kok_font_sz, "bold"), width=76, justify="center"
        )
        cv_badge.create_text(cx, cy + 16, text="KÖK (Asıl Harfler)", fill="#93c5fd" if is_dark else "#1d4ed8", font=("Segoe UI", 6, "bold"))
        cv_badge.create_text(38, 24, text=emoji_str, font=("Segoe UI", 20))
        cv_badge.create_text(140, 102, text=kat_str, fill=c["TEXT_DIM"], font=("Segoe UI", 8, "bold"))

    def show_entry_details(entry_id: str):
        morph = get_word_morphology_and_family(entry_id)
        if not morph:
            return
        selected_entry_data[0] = morph
        draw_hex_badge(morph["kok"], morph["emoji"], morph["kategori"])
        primary_ar = morph["mufret"] if morph["tur"] == "isim" else morph["mazi"]
        lbl_det_ar.config(text=shape_arabic(primary_ar))
        lbl_det_tr.config(text=morph["anlam_tr"])

        for w in forms_box.winfo_children():
            w.destroy()
        for f_item in morph["forms"]:
            rf = tk.Frame(forms_box, bg=c["BG_COLOR"], padx=8, pady=3, highlightthickness=1, highlightbackground=c["BORDER"])
            rf.pack(fill="x", pady=2)
            tk.Label(rf, text=f_item["label"] + ":", bg=c["BG_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 8, "bold")).pack(side="left")
            tk.Button(
                rf, text=f"🔊 {f_item['arabic']}",
                command=lambda ar_t=f_item["arabic"]: app._play_tts_text_async(ar_t, "ar-SA-HamedNeural"),
                bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=(AR_FONT_FAMILY, 13, "bold"),
                relief="flat", cursor="hand2", bd=0
            ).pack(side="right")

        for w in fam_box.winfo_children():
            w.destroy()
        if not morph["family"]:
            tk.Label(fam_box, text="Bu kökten başka kayıt bulunamadı.", bg=c["BG_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 8)).pack(anchor="w")
        else:
            for fam in morph["family"][:5]:
                btn_f = tk.Button(
                    fam_box,
                    text=f"• {fam['arabic']} — {fam['anlam_tr'][:28]}",
                    command=lambda fid=fam["id"]: show_entry_details(fid),
                    bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 8, "bold"),
                    relief="flat", anchor="w", cursor="hand2", bd=0
                )
                btn_f.pack(fill="x", pady=1)

    def practice_selected_in_card():
        morph = selected_entry_data[0]
        if not morph:
            return
        wid = morph.get("word_id") or f"szl_{morph['id']}"
        win.destroy()
        app._switch_mode("words")
        app.current_cat_id = "sozluk_tumu"
        app.cat_combo.set("📖 Sarf Sözlüğü (Tüm Kelimeler)")
        app.word_level_var.set("Tümü")
        app._load_category_items(play_audio=False)
        for idx, it in enumerate(app.current_items):
            if str(it.id) == str(wid):
                app.current_index = idx
                app._show_card(play_audio=True)
                break

    btn_row_d = tk.Frame(right_f, bg=c["PANEL_COLOR"])
    btn_row_d.pack(fill="x", side="bottom")
    tk.Button(
        btn_row_d, text="🎤 Bu Kelimeyi Kartta Çalış", command=practice_selected_in_card,
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", pady=6, cursor="hand2"
    ).pack(fill="x")

    cached_entries = []

    def populate_tree_rows():
        for it in tree.get_children():
            tree.delete(it)
        for r in cached_entries[:800]:
            head_ar = r["mufret"] if r["tur"] == "isim" else r["mazi"]
            sub_ar = (r["cemi"] if r["tur"] == "isim" else f"{r['muzari']} | {r['masdar']}")
            tree.insert("", tk.END, iid=r["id"], values=(r["kok"], r["tur"].upper(), head_ar, sub_ar, r["anlam_tr"]))
        if cached_entries:
            first_id = cached_entries[0]["id"]
            if tree.exists(first_id):
                tree.selection_set(first_id)
                show_entry_details(first_id)

    def refresh_list(*_):
        res = search_dictionary_entries(q_var.get(), tur_var.get(), cat_var.get())
        cached_entries.clear()
        cached_entries.extend(res)
        sort_state["col"] = None
        for ck, h_txt in col_headers.items():
            tree.heading(ck, text=h_txt + " ⇅", command=lambda c_k=ck: sort_dict_column(c_k))
        lbl_total.config(text=f"Bulunan: {len(res)} sözlük maddesi")
        populate_tree_rows()

    def on_select(event):
        sel = tree.selection()
        if sel:
            show_entry_details(sel[0])

    tree.bind("<<TreeviewSelect>>", on_select)
    ent_q.bind("<KeyRelease>", refresh_list)
    cmb_tur.bind("<<ComboboxSelected>>", refresh_list)
    cmb_cat.bind("<<ComboboxSelected>>", refresh_list)

    refresh_list()


# ==============================================================================
# 0.3) 📦 ÖĞRETMEN İÇİN ÖDEV VE SES KAYITLARI .ZIP PAKETLEYİCİ
# ==============================================================================
def export_student_homework_zip(parent_win, app):
    student_name = (get_config_val("student_name", "") or "Ogrenci").strip()
    safe_s_name = "".join(ch if ch.isalnum() else "_" for ch in student_name).strip("_") or "Ogrenci"
    save_zip = filedialog.asksaveasfilename(
        title="📦 Öğretmen İçin Ödev ve Ses Paketini Kaydet (.ZIP)",
        defaultextension=".zip",
        filetypes=[("Ödev & Ses Arşivi (.zip)", "*.zip")],
        initialfile=f"Arapca_Odev_{safe_s_name}_{datetime.date.today().isoformat()}.zip",
        parent=parent_win
    )
    if not save_zip:
        return

    try:
        badge_info = get_streak_and_badges()
        rep_data = get_detailed_analytics_report()
        last_exam = get_config_val("last_listening_exam", "Henüz Dinle & Bul sınavı tamamlanmadı.")
        temp_csv = os.path.join(DATA_DIR, "_temp_odev_raporu.csv")
        export_analytics_to_csv(temp_csv)

        rec_files = []
        if os.path.exists(RECORDINGS_DIR):
            rec_files = [
                os.path.join(RECORDINGS_DIR, f)
                for f in os.listdir(RECORDINGS_DIR)
                if f.lower().endswith(".wav")
            ]
            rec_files.sort(key=lambda p: os.path.getmtime(p), reverse=True)
            rec_files = rec_files[:20]

        with zipfile.ZipFile(save_zip, "w", zipfile.ZIP_DEFLATED) as zf:
            if os.path.exists(temp_csv):
                zf.write(temp_csv, "ogrenci_basari_raporu.csv")

            for idx, wav_p in enumerate(rec_files, 1):
                zf.write(wav_p, f"ses_kayitlari/{idx:02d}_{os.path.basename(wav_p)}")

            diff_rows_html = ""
            for kind, iid, tr, ar, corr, wrg in rep_data["most_difficult"][:15]:
                diff_rows_html += (
                    f"<tr><td>{html.escape(str(kind))}</td>"
                    f"<td>{html.escape(str(tr))}</td>"
                    f"<td dir='rtl' style='font-size:18px;font-weight:bold;'>{html.escape(str(ar))}</td>"
                    f"<td>{corr}</td><td style='color:#dc2626;font-weight:bold;'>{wrg}</td></tr>"
                )

            summary_html = f"""<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8">
<title>Öğrenci Konuşma ve Telaffuz Ödev Karnesi</title>
<style>
body {{ font-family: 'Segoe UI', Tahoma, sans-serif; margin: 30px; color: #0f172a; background: #f8fafc; }}
.card {{ background: #fff; border: 1px solid #cbd5e1; border-radius: 8px; padding: 20px; margin-bottom: 20px; }}
h1 {{ color: #1e3a8a; margin-top: 0; }}
table {{ width: 100%; border-collapse: collapse; margin-top: 12px; }}
th, td {{ border: 1px solid #cbd5e1; padding: 8px 12px; text-align: left; }}
th {{ background: #e2e8f0; }}
</style>
</head>
<body>
<div class="card">
  <h1>📊 Arapça Telaffuz ve Konuşma Ödev Karnesi</h1>
  <p><b>👤 Öğrenci Adı Soyadı:</b> <span style="color:#1e3a8a;font-size:16px;font-weight:bold;">{html.escape(student_name)}</span></p>
  <p><b>Tarih:</b> {datetime.datetime.now().strftime('%d.%m.%Y %H:%M')} &nbsp;|&nbsp;
     <b>Seviye Unvanı:</b> {html.escape(badge_info['level_title'])} &nbsp;|&nbsp;
     <b>Günlük Seri:</b> {badge_info['streak']} Gün &nbsp;|&nbsp;
     <b>Toplam Puan:</b> {badge_info['total_points']}</p>
  <p><b>Son Dinle & Bul Sınav Sonucu:</b> {html.escape(last_exam)}</p>
  <p><b>Pakete Dahil Edilen Ses Kaydı Sayısı:</b> {len(rec_files)} adet (.wav)</p>
</div>
<div class="card">
  <h2>⚠️ Öğrencinin En Çok Zorlandığı Kelime ve Cümleler</h2>
  <table>
    <thead><tr><th>Tür</th><th>Türkçe Anlamı</th><th>Arapça Metin</th><th>Doğru</th><th>Yanlış</th></tr></thead>
    <tbody>{diff_rows_html or "<tr><td colspan='5'>Henüz hatalı kayıt bulunmuyor.</td></tr>"}</tbody>
  </table>
</div>
</body>
</html>"""
            zf.writestr("karne_ozeti.html", summary_html)

        if os.path.exists(temp_csv):
            try:
                os.remove(temp_csv)
            except Exception:
                pass

        messagebox.showinfo(
            "📦 Ödev Paketi Oluşturuldu",
            f"Öğretmen raporu ve son {len(rec_files)} ses kaydı tek bir .ZIP dosyasında paketlendi!\n\n"
            f"• Kaydedilen Dosya:\n  {save_zip}\n\n"
            f"💡 Bu .ZIP dosyasını öğretmeninize E-posta, WhatsApp, EBA veya USB bellek ile doğrudan iletebilirsiniz.",
            parent=parent_win
        )
    except Exception as ex:
        log_error("Ödev ZIP paketleme hatası", ex)
        messagebox.showerror("Hata", f"Ödev paketi oluşturulamadı:\n{ex}", parent=parent_win)


# ==============================================================================
# 0.5) 🖨️ KARMA KAYNAKLI YAZILI SINAV & 📖 'DERS NOTU / OKUMA METNİ' PDF ÜRETİCİ
# ==============================================================================
def open_printable_worksheet_generator(parent_win, app, initial_mode: str = "words", initial_pkg_id: str = None, initial_dlg_id: str = None):
    """
    Öğretmenler için 2'si 1 Arada A4 Materyal & Sınav Üretici (v40 Final):
    1) Karma Kaynak ('all_mixed') desteğiyle Kelime, Diyalog ve Cümlelerden aynı anda yazılı sınav üretir.
    2) Çoktan seçmeli test şıklarını (A, B, C, D) 2x2 CSS Grid ile milimetrik hizalar.
    3) Diyalog veya Cümleler seçildiğinde '📖 Ders Notu / Okuma Metni Formatı' (Handout) aktif olur:
       - Harekeli / Harekesiz gösterim
       - Türkçe Tercümeyi Dahil Et / Sadece Arapça Okuma Metni
       - Tercüme Yerleşimi: Yan Yana (İki Sütun %50-%50), Alt Alta veya Sonda Toplu.
    """
    c = app.colors
    win = tk.Toplevel(parent_win)
    win.title("🖨️ Yazdırılabilir Yazılı Sınav, Ders Notu (Okuma Metni) ve Quiz Üretici")
    win.geometry("820x740")
    win.minsize(760, 660)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(parent_win)
    win.grab_set()

    if parent_win == app.root and hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text="🖨️ A4 YAZILI SINAV, DERS NOTU (OKUMA METNİ) & QUİZ ÜRETİCİ",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")
    ).pack(side="left")

    body = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=8)
    body.pack(fill="both", expand=True)

    source_disp_map = {
        "words": "🏠 Kelimeler & Sarf Sözlüğü (words)",
        "dialogue": "💬 Günlük Diyaloglar (dialogue)",
        "sentences": "📜 Anahtar Cümleler (sentences)",
        "all_mixed": "🌟 Karma Sınav (Kelimeler + Diyaloglar + Cümleler Birlikte)"
    }
    disp_to_source = {v: k for k, v in source_disp_map.items()}

    # 1. Satır: Materyal Kaynağı ve Seviye
    r_type = tk.Frame(body, bg=c["CARD_COLOR"])
    r_type.pack(fill="x", pady=3)
    tk.Label(r_type, text="Materyal Kaynağı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, width=18, anchor="w").pack(side="left")
    init_key = initial_mode if initial_mode in source_disp_map else "words"
    mode_disp_var = tk.StringVar(value=source_disp_map[init_key])
    cmb_mode = ttk.Combobox(
        r_type, textvariable=mode_disp_var,
        values=list(source_disp_map.values()),
        state="readonly", width=44, font=FONT_UI_B
    )
    cmb_mode.pack(side="left", padx=6)

    tk.Label(r_type, text="Seviye:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=(10, 4))
    lvl_var = tk.StringVar(value="Tümü")
    ttk.Combobox(r_type, textvariable=lvl_var, values=["Tümü", "A1", "A2", "B1", "B2", "C1"], state="readonly", width=7, font=FONT_UI).pack(side="left")

    # 2. Satır: Paket / Diyalog
    r_src = tk.Frame(body, bg=c["CARD_COLOR"])
    r_src.pack(fill="x", pady=3)
    tk.Label(r_src, text="Paket / Diyalog:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, width=18, anchor="w").pack(side="left")
    src_combo = ttk.Combobox(r_src, state="readonly", width=52, font=FONT_UI)
    src_combo.pack(side="left", padx=6)

    # 3. Satır: Kağıt Başlığı
    r_head = tk.Frame(body, bg=c["CARD_COLOR"])
    r_head.pack(fill="x", pady=3)
    tk.Label(r_head, text="Sınav / Kağıt Başlığı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, width=18, anchor="w").pack(side="left")
    ent_title = tk.Entry(r_head, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"])
    ent_title.insert(0, "Arapça Dersi Kelime, Sarf ve Anlama Yazılı Sınavı / Çalışma Yaprağı")
    ent_title.pack(side="left", fill="x", expand=True, padx=6)

    # =========================================================================
    # BÖLÜM A: YAZILI SINAV / ÇALIŞMA YAPRAĞI SORU SAYILARI (SPINBOX 0-50)
    # =========================================================================
    counts_box = tk.LabelFrame(
        body, text=" 🔢 Yazılı Sınav & Quiz Soru Sayıları (İstemediğiniz Bölümü 0 Yapabilirsiniz) ",
        bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=6
    )
    counts_box.pack(fill="x", pady=5)

    ar_to_tr_cnt_var = tk.IntVar(value=10)
    tr_to_ar_cnt_var = tk.IntVar(value=10)
    fillblank_cnt_var = tk.IntVar(value=5)
    mc_quiz_cnt_var = tk.IntVar(value=5)
    matching_cnt_var = tk.IntVar(value=5)
    sarf_cnt_var = tk.IntVar(value=5)

    spin_widgets = []
    spin_specs = [
        ("1. Bölüm — Arapça ➔ Türkçe Yazma Soru Sayısı (0-50):", ar_to_tr_cnt_var, 50),
        ("2. Bölüm — Türkçe ➔ Arapça Yazma Soru Sayısı (0-50):", tr_to_ar_cnt_var, 50),
        ("3. Bölüm — Boşluk Doldurma Soru Sayısı (0-50):", fillblank_cnt_var, 50),
        ("4. Bölüm — Çoktan Seçmeli (4 Şıklı) Test Sayısı (0-50):", mc_quiz_cnt_var, 50),
        ("5. Bölüm — Eşleştirme Alıştırması Soru Sayısı (0-30):", matching_cnt_var, 30),
        ("6. Bölüm — Sarf (Kök & Müfret-Cemî / Fiil) Tablosu Sayısı (0-30):", sarf_cnt_var, 30),
    ]

    for s_label, s_var, s_max in spin_specs:
        row_s = tk.Frame(counts_box, bg=c["CARD_COLOR"])
        row_s.pack(fill="x", pady=1)
        tk.Label(row_s, text=s_label, bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI, anchor="w").pack(side="left")
        sp_w = tk.Spinbox(
            row_s, from_=0, to=s_max, textvariable=s_var, width=6,
            bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, justify="center"
        )
        sp_w.pack(side="right", padx=6)
        spin_widgets.append(sp_w)

    opt_box = tk.Frame(counts_box, bg=c["CARD_COLOR"])
    opt_box.pack(fill="x", pady=(4, 0))

    inc_summary_table = tk.BooleanVar(value=False)
    inc_answer_key = tk.BooleanVar(value=True)

    chk_sum = tk.Checkbutton(
        opt_box, text="📋 Sayfa Başına Konu Özeti Tablosu Ekle",
        variable=inc_summary_table, bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"],
        selectcolor=c["PANEL_COLOR"], activebackground=c["CARD_COLOR"], font=FONT_UI
    )
    chk_sum.pack(side="left", padx=(0, 14))

    chk_ans = tk.Checkbutton(
        opt_box, text="🔑 Cevap Anahtarını Ayrı Sayfada Bas (page-break)",
        variable=inc_answer_key, bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"],
        selectcolor=c["PANEL_COLOR"], activebackground=c["CARD_COLOR"], font=FONT_UI_B
    )
    chk_ans.pack(side="left")

    # =========================================================================
    # BÖLÜM B: DİYALOG VE CÜMLELER İÇİN "DERS NOTU / OKUMA METNİ" FORMATI
    # =========================================================================
    handout_box = tk.LabelFrame(
        body, text=" 📖 Ders Notu / Okuma Metni Formatı (Diyalog ve Cümleler İçin Sınavsız Temiz Çıktı) ",
        bg=c["CARD_COLOR"], fg=GREEN if app.theme_mode == "dark" else "#047857", font=FONT_UI_B, padx=14, pady=8
    )

    use_handout_mode_var = tk.BooleanVar(value=False)
    handout_haraka_var = tk.BooleanVar(value=True)
    handout_inc_tr_var = tk.BooleanVar(value=True)
    tr_layout_var = tk.StringVar(value="tr_layout_side")

    def _sync_handout_controls(*_):
        is_handout = use_handout_mode_var.get()
        st_spin = "disabled" if is_handout else "normal"
        for sp in spin_widgets:
            sp.config(state=st_spin)
        chk_sum.config(state=st_spin)
        chk_ans.config(state=st_spin)

        rb_state = "normal" if (is_handout and handout_inc_tr_var.get()) else "disabled"
        chk_h_har.config(state="normal" if is_handout else "disabled")
        chk_h_tr.config(state="normal" if is_handout else "disabled")
        for rb in handout_radios:
            rb.config(state=rb_state)

        default_exam_title = "Arapça Dersi Kelime, Sarf ve Anlama Yazılı Sınavı / Çalışma Yaprağı"
        default_handout_title = "Arapça Diyalog ve Okuma Metni Ders Notu"
        cur_t = ent_title.get().strip()

        if is_handout and (cur_t == default_exam_title or "Sınav" in cur_t):
            ent_title.delete(0, tk.END)
            ent_title.insert(0, default_handout_title)
        elif not is_handout and (cur_t == default_handout_title or "Ders Notu" in cur_t):
            ent_title.delete(0, tk.END)
            ent_title.insert(0, default_exam_title)

    chk_activate_handout = tk.Checkbutton(
        handout_box,
        text="📄 Sınav/Test Bölümlerini Yoksay ve Seçili Paketi 'Temiz Ders Notu / Okuma Metni (Handout)' Olarak Üret",
        variable=use_handout_mode_var, command=_sync_handout_controls,
        bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=("Segoe UI", 9, "bold")
    )
    chk_activate_handout.pack(anchor="w", pady=(0, 4))

    h_opts_row = tk.Frame(handout_box, bg=c["CARD_COLOR"])
    h_opts_row.pack(fill="x", pady=2)

    chk_h_har = tk.Checkbutton(
        h_opts_row, text="🔤 Harekeli Göster (Varsayılan: Seçili)",
        variable=handout_haraka_var, bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"],
        selectcolor=c["PANEL_COLOR"], activebackground=c["CARD_COLOR"], font=FONT_UI
    )
    chk_h_har.pack(side="left", padx=(0, 18))

    chk_h_tr = tk.Checkbutton(
        h_opts_row, text="🇹🇷 Türkçe Tercümeyi Dahil Et (Varsayılan: Seçili)",
        variable=handout_inc_tr_var, command=_sync_handout_controls,
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI
    )
    chk_h_tr.pack(side="left")

    h_radio_frame = tk.Frame(handout_box, bg=c["CARD_COLOR"])
    h_radio_frame.pack(fill="x", pady=(4, 0))
    tk.Label(h_radio_frame, text="Tercüme Yerleşimi:", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B).pack(anchor="w")

    handout_radios = []
    for r_val, r_txt in [
        ("tr_layout_side", "Yan Yana (İki Sütun: Sol Arapça %50 RTL, Sağ Türkçe %50 LTR)"),
        ("tr_layout_below", "Alt Alta (Her Arapça cümlenin hemen altında Türkçesi)"),
        ("tr_layout_end", "Sonda Toplu (Tüm Arapça metin bittikten sonra sayfa sonunda Türkçe çevirisi)")
    ]:
        rb = tk.Radiobutton(
            h_radio_frame, text=r_txt, variable=tr_layout_var, value=r_val,
            bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
            activebackground=c["CARD_COLOR"], font=("Segoe UI", 9)
        )
        rb.pack(anchor="w", padx=12)
        handout_radios.append(rb)

    src_map = {}

    def refresh_sources(*_):
        m = disp_to_source.get(mode_disp_var.get(), "words")
        src_map.clear()
        names = []
        if m == "words":
            src_map["Tüm Kelimeler (Görselli + Sözlük)"] = "all"
            names.append("Tüm Kelimeler (Görselli + Sözlük)")
            for pid, pad in get_word_packages():
                disp = f"{pad} ({pid})"
                src_map[disp] = pid
                names.append(disp)
            if initial_pkg_id:
                for disp_k, v_id in src_map.items():
                    if v_id == initial_pkg_id:
                        src_combo.set(disp_k)
                        break
        elif m == "dialogue":
            for did, _, b_ar, b_tr in get_dialogues():
                disp = f"[{did}] {b_tr} — {b_ar}" if b_ar else f"[{did}] {b_tr}"
                src_map[disp] = did
                names.append(disp)
            src_map["Tüm Diyaloglar"] = "all"
            names.append("Tüm Diyaloglar")
        elif m == "sentences":
            src_map["Tüm Anahtar Cümleler"] = "all"
            names.append("Tüm Anahtar Cümleler")
            for pid, pad in get_sentence_packages():
                disp = f"{pad} ({pid})"
                src_map[disp] = pid
                names.append(disp)
        else:
            src_map["🌟 Tüm Kaynaklar (Kelime + Diyalog + Cümle Havuzu)"] = "all"
            names.append("🌟 Tüm Kaynaklar (Kelime + Diyalog + Cümle Havuzu)")

        src_combo["values"] = names
        if names and src_combo.get() not in src_map:
            src_combo.set(names[0])

        # Diyalog veya Cümleler seçildiğinde Ders Notu / Okuma Metni bölümünü göster
        if m in ("dialogue", "sentences"):
            handout_box.pack(fill="x", pady=6)
        else:
            use_handout_mode_var.set(False)
            handout_box.pack_forget()
        _sync_handout_controls()

    cmb_mode.bind("<<ComboboxSelected>>", refresh_sources)
    refresh_sources()

    def build_and_open_worksheet():
        m = disp_to_source.get(mode_disp_var.get(), "words")
        sel_id = src_map.get(src_combo.get(), "all")
        lvl_f = lvl_var.get()
        sheet_title = ent_title.get().strip() or "Arapça Çalışma Yaprağı"
        logo_img_html = _get_brand_logo_base64_html()
        today_str = datetime.date.today().strftime("%d.%m.%Y")

        # =====================================================================
        # MOD 1: DİYALOG VE CÜMLELER İÇİN "DERS NOTU / OKUMA METNİ (HANDOUT)"
        # =====================================================================
        if use_handout_mode_var.get() and m in ("dialogue", "sentences"):
            keep_haraka = handout_haraka_var.get()
            inc_tr = handout_inc_tr_var.get()
            layout_mode = tr_layout_var.get()

            handout_rows = []  # [(speaker_or_num, ar_text, tr_text), ...]
            if m == "dialogue":
                if sel_id == "all":
                    lines = get_dialogue_lines_with_titles(seviye=lvl_f)[:120]
                    for _, _, b_tr, sira, spk, _, ar, tr, _, _ in lines:
                        ar_clean = ar if keep_haraka else remove_diacritics(ar)
                        handout_rows.append((f"{spk} ({b_tr})", ar_clean, tr or ""))
                else:
                    lines = get_dialogue_lines(sel_id)
                    for _, sira, spk, spk_ar, _, ar, tr, _ in lines:
                        ar_clean = ar if keep_haraka else remove_diacritics(ar)
                        spk_disp = f"{spk} ({spk_ar})" if spk_ar else spk
                        handout_rows.append((spk_disp, ar_clean, tr or ""))
            else:
                sents = get_key_sentences("all", paket_id=sel_id, seviye=lvl_f)[:80]
                for idx, s in enumerate(sents, 1):
                    ar_clean = s.arapca if keep_haraka else remove_diacritics(s.arapca)
                    handout_rows.append((f"{idx}.", ar_clean, s.turkce or ""))

            if not handout_rows:
                messagebox.showwarning("Veri Yok", "Seçili kaynakta ders notu oluşturulacak kayıt bulunamadı.", parent=win)
                return

            body_html = ""
            if not inc_tr:
                # Sadece temiz Arapça okuma metni (Hiçbir Türkçe sütun veya boş alan yok)
                lines_html = ""
                for spk_lbl, ar_t, _ in handout_rows:
                    spk_html = f"<span class='spk-ar'>{html.escape(spk_lbl)}:</span> " if spk_lbl else ""
                    lines_html += f"<div class='dialogue-line'><div class='ar-handout'>{spk_html}{html.escape(ar_t)}</div></div>"
                body_html = f"<div class='handout-container'>{lines_html}</div>"

            elif layout_mode == "tr_layout_side":
                # Yan Yana (İki Sütun: Sol %50 Arapça RTL, Sağ %50 Türkçe LTR)
                t_rows = ""
                for spk_lbl, ar_t, tr_t in handout_rows:
                    spk_ar = f"<b style='color:#1e3a8a;'>{html.escape(spk_lbl)}:</b> " if spk_lbl else ""
                    spk_tr = f"<b>{html.escape(spk_lbl.split('(')[0].strip())}:</b> " if (m == "dialogue" and spk_lbl) else ""
                    t_rows += (
                        f"<tr>"
                        f"<td class='ar-side'>{spk_ar}{html.escape(ar_t)}</td>"
                        f"<td class='tr-side'>{spk_tr}{html.escape(tr_t)}</td>"
                        f"</tr>"
                    )
                body_html = f"""
                <table class="side-table">
                  <thead>
                    <tr>
                      <th style="width:50%;text-align:right;direction:rtl;">النَّصُّ الْعَرَبِيُّ (Arapça Metin)</th>
                      <th style="width:50%;text-align:left;direction:ltr;">Türkçe Tercümesi</th>
                    </tr>
                  </thead>
                  <tbody>{t_rows}</tbody>
                </table>"""

            elif layout_mode == "tr_layout_below":
                # Alt Alta (Her Arapça cümlenin hemen altında Türkçesi)
                blocks_html = ""
                for spk_lbl, ar_t, tr_t in handout_rows:
                    spk_ar = f"<span class='spk-ar'>{html.escape(spk_lbl)}:</span> " if spk_lbl else ""
                    blocks_html += (
                        f"<div class='dialogue-line'>"
                        f"<div class='ar-handout'>{spk_ar}{html.escape(ar_t)}</div>"
                        f"<div class='tr-handout'>{html.escape(tr_t)}</div>"
                        f"</div>"
                    )
                body_html = f"<div class='handout-container'>{blocks_html}</div>"

            else:
                # Sonda Toplu (Önce tüm Arapça metin, sayfa sonunda toplu Türkçe çeviri)
                ar_part = ""
                tr_part = ""
                for idx, (spk_lbl, ar_t, tr_t) in enumerate(handout_rows, 1):
                    spk_ar = f"<span class='spk-ar'>{html.escape(spk_lbl)}:</span> " if spk_lbl else ""
                    ar_part += f"<div class='ar-handout' style='margin-bottom:10px;'>{spk_ar}{html.escape(ar_t)}</div>"
                    spk_tr = f"<b>{html.escape(spk_lbl)}:</b> " if spk_lbl else f"<b>{idx}.</b> "
                    tr_part += f"<div class='tr-end-line'>{spk_tr}{html.escape(tr_t)}</div>"

                body_html = f"""
                <div class="handout-container">{ar_part}</div>
                <div class="tr-end-section">
                  <h2>🇹🇷 Metnin Türkçe Çevirisi</h2>
                  {tr_part}
                </div>"""

            handout_html = f"""<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8">
<title>{html.escape(sheet_title)}</title>
<style>
  @page {{ size: A4; margin: 16mm; }}
  body {{ font-family: 'Segoe UI', Tahoma, sans-serif; color: #0f172a; background: #e2e8f0; margin: 0; padding: 20px; }}
  .page {{ max-width: 210mm; margin: 0 auto; background: #ffffff; padding: 16mm 18mm; box-shadow: 0 4px 20px rgba(0,0,0,0.12); border-radius: 6px; box-sizing: border-box; }}
  .top-actions {{ max-width: 210mm; margin: 0 auto 14px auto; display: flex; justify-content: space-between; align-items: center; background: #1e293b; color: #fff; padding: 12px 18px; border-radius: 6px; }}
  .btn-print {{ background: #10b981; color: #0f172a; border: none; padding: 10px 20px; font-size: 15px; font-weight: bold; border-radius: 5px; cursor: pointer; }}
  .header-box {{ border-bottom: 2px solid #1e3a8a; padding-bottom: 12px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; }}
  .brand-left {{ display: flex; align-items: center; }}
  h1 {{ font-size: 20px; color: #1e3a8a; margin: 0 0 4px 0; }}
  h2 {{ font-size: 16px; color: #1e3a8a; background: #f1f5f9; padding: 8px 12px; border-left: 4px solid #1e3a8a; margin-top: 24px; margin-bottom: 12px; }}
  .side-table {{ width: 100%; border-collapse: collapse; margin-top: 10px; }}
  .side-table th, .side-table td {{ border: 1px solid #cbd5e1; padding: 10px 14px; vertical-align: top; }}
  .side-table th {{ background: #f8fafc; color: #1e3a8a; font-size: 15px; }}
  .ar-side {{ width: 50%; direction: rtl; text-align: right; font-family: 'Arakom', 'Traditional Arabic', serif; font-size: 22px; font-weight: bold; line-height: 1.8; color: #0f172a; }}
  .tr-side {{ width: 50%; direction: ltr; text-align: left; font-family: 'Segoe UI', sans-serif; font-size: 14px; line-height: 1.8; color: #475569; }}
  .dialogue-line {{ border-bottom: 1px dashed #cbd5e1; padding: 10px 4px; page-break-inside: avoid; }}
  .ar-handout {{ direction: rtl; text-align: right; font-family: 'Arakom', 'Traditional Arabic', serif; font-size: 22px; font-weight: bold; line-height: 1.8; color: #0f172a; }}
  .tr-handout {{ direction: ltr; text-align: right; font-family: 'Segoe UI', sans-serif; font-size: 14px; line-height: 1.6; color: #475569; margin-top: 4px; }}
  .spk-ar {{ color: #1e3a8a; font-weight: bold; }}
  .tr-end-section {{ margin-top: 28px; padding-top: 12px; border-top: 2px solid #94a3b8; page-break-inside: avoid; }}
  .tr-end-line {{ font-family: 'Segoe UI', sans-serif; font-size: 14px; color: #475569; line-height: 1.8; margin-bottom: 6px; }}
  @media print {{
    body {{ background: #fff; padding: 0; }}
    .page {{ box-shadow: none; padding: 0; max-width: 100%; }}
    .top-actions {{ display: none !important; }}
  }}
</style>
</head>
<body>
  <div class="top-actions">
    <div><b>📖 Muallimun.Net Ders Notu / Okuma Metni Önizleme</b> — Yazdırmak veya PDF kaydetmek için sağdaki butona basın.</div>
    <button class="btn-print" onclick="window.print()">🖨️ Yazdır / PDF Olarak Kaydet (Ctrl+P)</button>
  </div>
  <div class="page">
    <div class="header-box">
      <div class="brand-left">
        {logo_img_html}
        <div>
          <h1>{html.escape(sheet_title)}</h1>
          <div style="font-size:12.5px;color:#475569;">Kaynak: {html.escape(src_combo.get())} &nbsp;|&nbsp; Seviye: {html.escape(lvl_f)} &nbsp;|&nbsp; Tarih: {today_str}</div>
        </div>
      </div>
      <div style="font-size:12.5px;color:#475569;text-align:right;">
        <b>Muallimun.Net</b><br>Arapça Eğitim Materyali
      </div>
    </div>
    {body_html}
  </div>
</body>
</html>"""

            out_path = os.path.join(DATA_DIR, "ders_notu_okuma_metni.html")
            with open(out_path, "w", encoding="utf-8") as f:
                f.write(handout_html)
            webbrowser.open(f"file:///{os.path.abspath(out_path).replace(os.sep, '/')}")
            app._show_toast("📖 Ders notu / okuma metni tarayıcıda açıldı!", GREEN, 2500)
            return

        # =====================================================================
        # MOD 2: YAZILI SINAV, ÇALIŞMA YAPRAĞI VE QUİZ ÜRETİCİ (KARMA DESTEKLİ)
        # =====================================================================
        n_ar_tr = max(0, int(ar_to_tr_cnt_var.get() or 0))
        n_tr_ar = max(0, int(tr_to_ar_cnt_var.get() or 0))
        n_fb = max(0, int(fillblank_cnt_var.get() or 0))
        n_mc = max(0, int(mc_quiz_cnt_var.get() or 0))
        n_match = max(0, int(matching_cnt_var.get() or 0))
        n_sarf = max(0, int(sarf_cnt_var.get() or 0))

        pool_entries = []  # [(id, arapca, turkce, meta_info), ...]
        raw_word_objs = []

        if m == "words":
            items = get_words(None if sel_id == "all" else sel_id, seviye=lvl_f)
            raw_word_objs = list(items)
            for w in items:
                if w.arapca and w.turkce:
                    pool_entries.append((f"w_{w.id}", w.arapca, w.turkce, w.seviye))
        elif m == "dialogue":
            if sel_id == "all":
                lines = get_dialogue_lines_with_titles(seviye=lvl_f)
                for lid, _, _, sira, spk, _, ar, tr, _, d_sev in lines:
                    if ar and tr:
                        pool_entries.append((f"d_{lid}", ar, tr, f"{d_sev} | {spk}"))
            else:
                lines = get_dialogue_lines(sel_id)
                for lid, sira, spk, _, _, ar, tr, _ in lines:
                    if ar and tr:
                        pool_entries.append((f"d_{lid}", ar, tr, f"{sira}. {spk}"))
        elif m == "sentences":
            sents = get_key_sentences("all", paket_id=sel_id, seviye=lvl_f)
            for s in sents:
                if s.arapca and s.turkce:
                    pool_entries.append((f"s_{s.id}", s.arapca, s.turkce, s.seviye))
        else:
            # 🌟 Karma Sınav (Kelimeler + Diyaloglar + Cümleler Birlikte)
            w_items = get_words(None, seviye=lvl_f)
            raw_word_objs = list(w_items)
            for w in w_items:
                if w.arapca and w.turkce:
                    pool_entries.append((f"w_{w.id}", w.arapca, w.turkce, f"Kelime ({w.seviye})"))
            d_lines = get_dialogue_lines_with_titles(seviye=lvl_f)
            for lid, _, _, _, spk, _, ar, tr, _, d_sev in d_lines:
                if ar and tr:
                    pool_entries.append((f"d_{lid}", ar, tr, f"Diyalog ({d_sev})"))
            s_items = get_key_sentences("all", seviye=lvl_f)
            for s in s_items:
                if s.arapca and s.turkce:
                    pool_entries.append((f"s_{s.id}", s.arapca, s.turkce, f"Cümle ({s.seviye})"))

        if len(pool_entries) < 4:
            messagebox.showwarning(
                "Yetersiz Veri",
                "Yazılı sınav veya çalışma kağıdı oluşturmak için seçili kaynakta en az 4 kayıt bulunmalıdır.",
                parent=win
            )
            return

        available_pool = list(pool_entries)
        ar_to_tr_pool = []
        if n_ar_tr > 0:
            take_1 = min(n_ar_tr, len(available_pool))
            ar_to_tr_pool = random.sample(available_pool, take_1)

        used_ids_1 = {it[0] for it in ar_to_tr_pool}
        remaining_after_1 = [it for it in available_pool if it[0] not in used_ids_1]

        tr_to_ar_pool = []
        if n_tr_ar > 0:
            if len(remaining_after_1) >= n_tr_ar:
                tr_to_ar_pool = random.sample(remaining_after_1, n_tr_ar)
            else:
                tr_to_ar_pool = list(remaining_after_1)
                still_need = n_tr_ar - len(tr_to_ar_pool)
                if still_need > 0 and used_ids_1:
                    fallback_cands = [it for it in available_pool if it[0] in used_ids_1]
                    tr_to_ar_pool.extend(random.sample(fallback_cands, min(still_need, len(fallback_cands))))

        used_ids_1_2 = used_ids_1 | {it[0] for it in tr_to_ar_pool}
        remaining_after_2 = [it for it in available_pool if it[0] not in used_ids_1_2]

        def _pick_pool(count_req: int, prefer_remaining: list) -> list:
            if count_req <= 0:
                return []
            if len(prefer_remaining) >= count_req:
                return random.sample(prefer_remaining, count_req)
            chosen = list(prefer_remaining)
            need_more = count_req - len(chosen)
            chosen_ids = {x[0] for x in chosen}
            others = [x for x in available_pool if x[0] not in chosen_ids]
            if others and need_more > 0:
                chosen.extend(random.sample(others, min(need_more, len(others))))
            return chosen

        fb_pool = _pick_pool(n_fb, remaining_after_2)
        mc_pool = _pick_pool(n_mc, [x for x in available_pool if x[0] not in {i[0] for i in fb_pool}])
        match_pool = _pick_pool(n_match, available_pool)

        sections_html = ""
        answers_html = ""
        sec_no = 0

        if inc_summary_table.get():
            sec_no += 1
            rows_t = ""
            for idx, (_, ar, tr, meta_s) in enumerate(available_pool[:40], 1):
                rows_t += (
                    f"<tr><td style='text-align:center;'>{idx}</td>"
                    f"<td style='text-align:center;'>{html.escape(str(meta_s))}</td>"
                    f"<td>{html.escape(tr)}</td>"
                    f"<td class='ar'>{html.escape(ar)}</td></tr>"
                )
            sections_html += f"""
            <div class="section">
              <h2>📋 Bölüm {sec_no}: Konu Özeti Çalışma Tablosu</h2>
              <table>
                <thead><tr><th style="width:45px;">No</th><th style="width:110px;">Tür / Seviye</th><th>Türkçe Anlamı</th><th>Arapça İfade (RTL)</th></tr></thead>
                <tbody>{rows_t}</tbody>
              </table>
            </div>"""

        if ar_to_tr_pool:
            sec_no += 1
            rows_ar_tr = ""
            ans_ar_tr = []
            for idx, (_, ar, tr, _) in enumerate(ar_to_tr_pool, 1):
                rows_ar_tr += (
                    f"<tr>"
                    f"<td style='width:42px;text-align:center;font-weight:bold;'>{idx}.</td>"
                    f"<td class='ar' style='width:44%;padding:10px 14px;'>{html.escape(ar)}</td>"
                    f"<td class='write-line' style='width:50%;color:#64748b;'>....................................................................................</td>"
                    f"</tr>"
                )
                ans_ar_tr.append(f"<b>{idx})</b> {html.escape(tr)}")
            sections_html += f"""
            <div class="section">
              <h2>✍️ Bölüm {sec_no}: Arapça ➔ Türkçe Anlam Yazma ({len(ar_to_tr_pool)} Soru)</h2>
              <p class="inst">Aşağıda verilen Arapça kelime ve ifadelerin Türkçe karşılıklarını karşılarındaki boşluklara yazınız.</p>
              <table>
                <thead><tr><th style="width:42px;">No</th><th>Arapça Kelime / İfade</th><th>Türkçe Anlamı (Öğrenci Yanıtı)</th></tr></thead>
                <tbody>{rows_ar_tr}</tbody>
              </table>
            </div>"""
            answers_html += f"<div class='ans-block'><h3>Bölüm {sec_no} (Arapça ➔ Türkçe) Cevapları:</h3><p>{' &nbsp;|&nbsp; '.join(ans_ar_tr)}</p></div>"

        if tr_to_ar_pool:
            sec_no += 1
            rows_tr_ar = ""
            ans_tr_ar = []
            for idx, (_, ar, tr, _) in enumerate(tr_to_ar_pool, 1):
                rows_tr_ar += (
                    f"<tr style='height:44px;'>"
                    f"<td style='width:42px;text-align:center;font-weight:bold;'>{idx}.</td>"
                    f"<td style='width:46%;font-weight:500;padding:10px 12px;'>{html.escape(tr)}</td>"
                    f"<td class='ar-write-box' style='width:48%;text-align:right;color:#94a3b8;padding-right:16px;'>........................................................................</td>"
                    f"</tr>"
                )
                ans_tr_ar.append(f"<b>{idx})</b> <span class='ar-inline'>{html.escape(ar)}</span>")
            sections_html += f"""
            <div class="section">
              <h2>🖋️ Bölüm {sec_no}: Türkçe ➔ Arapça Yazma ({len(tr_to_ar_pool)} Soru)</h2>
              <p class="inst">Aşağıda Türkçe anlamları verilen kelime ve ifadelerin Arapça karşılıklarını harekeli olarak sağdaki alana yazınız.</p>
              <table>
                <thead><tr><th style="width:42px;">No</th><th>Türkçe Anlamı</th><th>Arapça Yazılışı (Sağdan Sola Yazınız)</th></tr></thead>
                <tbody>{rows_tr_ar}</tbody>
              </table>
            </div>"""
            answers_html += f"<div class='ans-block'><h3>Bölüm {sec_no} (Türkçe ➔ Arapça) Cevapları:</h3><p dir='rtl'>{' &nbsp;&nbsp;|&nbsp;&nbsp; '.join(ans_tr_ar)}</p></div>"

        if fb_pool:
            sec_no += 1
            rows_fb = ""
            ans_fb = []
            for idx, (_, ar, tr, _) in enumerate(fb_pool, 1):
                words_ar = ar.split()
                if len(words_ar) > 1:
                    hide_i = random.randint(0, len(words_ar) - 1)
                    missing_w = words_ar[hide_i]
                    words_ar[hide_i] = "........................"
                    prompt_ar = " ".join(words_ar)
                else:
                    missing_w = ar
                    prompt_ar = f"{ar[0]} ........................" if len(ar) > 2 else "........................"
                ans_fb.append(f"<b>{idx})</b> <span class='ar-inline'>{html.escape(missing_w)}</span>")
                rows_fb += (
                    f"<div class='qbox'>"
                    f"<div><b>{idx}.</b> {html.escape(tr)}</div>"
                    f"<div class='ar' style='margin-top:6px;'>{html.escape(prompt_ar)}</div>"
                    f"</div>"
                )
            sections_html += f"""
            <div class="section">
              <h2>🧩 Bölüm {sec_no}: Boşluk Doldurma Alıştırması ({len(fb_pool)} Soru)</h2>
              <p class="inst">Türkçe anlamlarından yararlanarak boş bırakılan yerleri uygun Arapça kelime veya ifadeyle tamamlayınız.</p>
              {rows_fb}
            </div>"""
            answers_html += f"<div class='ans-block'><h3>Bölüm {sec_no} (Boşluk Doldurma) Cevapları:</h3><p dir='rtl'>{' &nbsp;&nbsp;|&nbsp;&nbsp; '.join(ans_fb)}</p></div>"

        # ÇOKTAN SEÇMELİ TEST (2x2 Jilet Gibi Hizalı CSS Grid Şıklar)
        if mc_pool and len(available_pool) >= 4:
            sec_no += 1
            mc_html = ""
            ans_mc = []
            opt_lets = ["A", "B", "C", "D"]
            for idx, (_, ar, tr, _) in enumerate(mc_pool, 1):
                distractors = list({p[2] for p in available_pool if p[2] != tr})
                if len(distractors) < 3:
                    continue
                opts = random.sample(distractors, 3) + [tr]
                random.shuffle(opts)
                corr_let = opt_lets[opts.index(tr)]
                ans_mc.append(f"<b>{idx}</b>-{corr_let}")
                opts_str = "".join([
                    f"<div class='opt-cell'><span class='opt-badge'>{opt_lets[i]})</span> <span>{html.escape(o)}</span></div>"
                    for i, o in enumerate(opts)
                ])
                mc_html += (
                    f"<div class='qbox'>"
                    f"<div class='q-title'><b>Soru {idx}:</b> &nbsp; <span class='ar-inline'>{html.escape(ar)}</span> &nbsp; ifadesinin doğru Türkçe karşılığı aşağıdakilerden hangisidir?</div>"
                    f"<div class='opts-grid'>{opts_str}</div>"
                    f"</div>"
                )
            sections_html += f"""
            <div class="section">
              <h2>📝 Bölüm {sec_no}: Çoktan Seçmeli Değerlendirme Testi ({len(mc_pool)} Soru)</h2>
              {mc_html}
            </div>"""
            answers_html += f"<div class='ans-block'><h3>Bölüm {sec_no} (Çoktan Seçmeli Test) Cevapları:</h3><p>{' &nbsp;|&nbsp; '.join(ans_mc)}</p></div>"

        if match_pool:
            sec_no += 1
            m_slice = match_pool[:min(len(match_pool), 26)]
            shuffled_tr = [(i + 1, p[2]) for i, p in enumerate(m_slice)]
            random.shuffle(shuffled_tr)
            rows_m = ""
            ans_m = []
            letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            for idx, (_, ar, tr, _) in enumerate(m_slice):
                let = letters[idx]
                orig_num, shuf_tr_txt = shuffled_tr[idx]
                correct_letter = letters[orig_num - 1]
                ans_m.append(f"<b>{idx+1}</b> ➔ {correct_letter}")
                rows_m += (
                    f"<tr>"
                    f"<td class='ar'><b>({let})</b> &nbsp; {html.escape(ar)}</td>"
                    f"<td style='text-align:center;width:90px;'>[ &nbsp;&nbsp;&nbsp;&nbsp; ]</td>"
                    f"<td><b>{idx+1}.</b> {html.escape(shuf_tr_txt)}</td>"
                    f"</tr>"
                )
            sections_html += f"""
            <div class="section">
              <h2>🔗 Bölüm {sec_no}: Eşleştirme Alıştırması ({len(m_slice)} Soru)</h2>
              <p class="inst">Sol sütundaki Arapça ifadelerin harflerini, sağdaki doğru Türkçe karşılıklarının yanındaki parantez içine yazınız.</p>
              <table>
                <thead><tr><th>Arapça İfade</th><th>Harf</th><th>Türkçe Karşılığı</th></tr></thead>
                <tbody>{rows_m}</tbody>
              </table>
            </div>"""
            answers_html += f"<div class='ans-block'><h3>Bölüm {sec_no} (Eşleştirme) Cevapları:</h3><p>{' &nbsp;|&nbsp; '.join(ans_m)}</p></div>"

        if n_sarf > 0 and m in ("words", "all_mixed") and raw_word_objs:
            sarf_rows = ""
            sarf_ans = []
            s_idx = 0
            shuffled_words = list(raw_word_objs)
            random.shuffle(shuffled_words)
            for w in shuffled_words:
                morph = get_word_morphology_and_family(w.id) or get_word_morphology_and_family(w.arapca)
                if not morph:
                    continue
                s_idx += 1
                if morph["tur"] == "isim":
                    cemi_val = morph["cemi"] if morph["cemi"] and morph["cemi"] != "-" else "—"
                    sarf_rows += (
                        f"<tr><td style='text-align:center;'>{s_idx}</td>"
                        f"<td class='ar' style='text-align:center;'>{html.escape(morph['kok'])}</td>"
                        f"<td>İsim (Müfret ➔ Cemî)</td>"
                        f"<td class='ar'>{html.escape(morph['mufret'])}</td>"
                        f"<td class='ar'>........................</td>"
                        f"<td>{html.escape(morph['anlam_tr'])}</td></tr>"
                    )
                    sarf_ans.append(f"<b>{s_idx})</b> ج: {html.escape(cemi_val)}")
                else:
                    sarf_rows += (
                        f"<tr><td style='text-align:center;'>{s_idx}</td>"
                        f"<td class='ar' style='text-align:center;'>{html.escape(morph['kok'])}</td>"
                        f"<td>Fiil (Mâzî ➔ Muzâri / Masdar)</td>"
                        f"<td class='ar'>{html.escape(morph['mazi'])}</td>"
                        f"<td class='ar'>................ / ................</td>"
                        f"<td>{html.escape(morph['anlam_tr'])}</td></tr>"
                    )
                    sarf_ans.append(f"<b>{s_idx})</b> {html.escape(morph['muzari'])} / {html.escape(morph['masdar'])}")
                if s_idx >= n_sarf:
                    break

            if sarf_rows:
                sec_no += 1
                sections_html += f"""
                <div class="section">
                  <h2>🌿 Bölüm {sec_no}: Sarf (Kök & Türev) Alıştırma Tablosu ({s_idx} Soru)</h2>
                  <p class="inst">Verilen kelimelerin sülâsî köklerini inceleyerek isimlerin çoğullarını (cemî), fiillerin ise muzâri ve masdarlarını boşluklara yazınız.</p>
                  <table>
                    <thead><tr><th>No</th><th>Sülâsî Kök</th><th>Tür</th><th>Ana Kelime</th><th>Cemî / Muzâri & Masdar</th><th>Anlamı</th></tr></thead>
                    <tbody>{sarf_rows}</tbody>
                  </table>
                </div>"""
                answers_html += f"<div class='ans-block'><h3>Bölüm {sec_no} (Sarf Tablosu) Cevapları:</h3><p dir='rtl'>{' &nbsp;&nbsp;|&nbsp;&nbsp; '.join(sarf_ans)}</p></div>"

        if not sections_html:
            messagebox.showwarning("Bölüm Seçilmedi", "Lütfen en az bir bölümün soru sayısını 1 veya daha yüksek seçin.", parent=win)
            return

        if inc_answer_key.get() and answers_html:
            sections_html += f"""
            <div class="answer-key-page">
              <h2>🔑 ÖĞRETMEN CEVAP ANAHTARI — {html.escape(sheet_title)}</h2>
              <p class="inst">Bu sayfa 'page-break-before: always' kuralı ile yazıcıdan otomatik olarak ayrı bir A4 sayfası halinde çıkar.</p>
              {answers_html}
            </div>"""

        full_html = f"""<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8">
<title>{html.escape(sheet_title)}</title>
<style>
  @page {{ size: A4; margin: 16mm; }}
  body {{ font-family: 'Segoe UI', Tahoma, sans-serif; color: #0f172a; background: #e2e8f0; margin: 0; padding: 20px; }}
  .page {{ max-width: 210mm; margin: 0 auto; background: #ffffff; padding: 16mm 18mm; box-shadow: 0 4px 20px rgba(0,0,0,0.12); border-radius: 6px; box-sizing: border-box; }}
  .top-actions {{ max-width: 210mm; margin: 0 auto 14px auto; display: flex; justify-content: space-between; align-items: center; background: #1e293b; color: #fff; padding: 12px 18px; border-radius: 6px; }}
  .btn-print {{ background: #10b981; color: #0f172a; border: none; padding: 10px 20px; font-size: 15px; font-weight: bold; border-radius: 5px; cursor: pointer; }}
  .header-box {{ border: 2px solid #1e3a8a; border-radius: 6px; padding: 12px 16px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; background: #f8fafc; }}
  .brand-left {{ display: flex; align-items: center; max-width: 60%; }}
  h1 {{ font-size: 18px; color: #1e3a8a; margin: 0 0 5px 0; line-height: 1.3; }}
  h2 {{ font-size: 15px; color: #1e3a8a; background: #f1f5f9; padding: 7px 12px; border-left: 4px solid #1e3a8a; margin-top: 22px; margin-bottom: 10px; }}
  h3 {{ font-size: 14px; color: #0f172a; margin: 10px 0 4px 0; }}
  .student-info {{ font-size: 13px; text-align: left; line-height: 1.85; border-left: 2px dashed #94a3b8; padding-left: 14px; min-width: 245px; }}
  .inst {{ font-size: 12.5px; color: #475569; margin-top: -4px; margin-bottom: 10px; font-style: italic; }}
  table {{ width: 100%; border-collapse: collapse; margin-bottom: 16px; font-size: 14px; }}
  th, td {{ border: 1px solid #94a3b8; padding: 8px 10px; vertical-align: middle; }}
  th {{ background: #f1f5f9; font-weight: bold; color: #1e293b; }}
  .ar {{ font-family: 'Arakom', 'Traditional Arabic', 'Amiri', 'Segoe UI', serif; font-size: 22px; font-weight: bold; direction: rtl; text-align: right; line-height: 1.55; }}
  .ar-inline {{ font-family: 'Arakom', 'Traditional Arabic', 'Amiri', 'Segoe UI', serif; font-size: 21px; font-weight: bold; direction: rtl; }}
  .qbox {{ border: 1px solid #cbd5e1; border-radius: 6px; padding: 12px 14px; margin-bottom: 12px; page-break-inside: avoid; background: #ffffff; }}
  .q-title {{ font-size: 14.5px; line-height: 1.5; margin-bottom: 8px; border-bottom: 1px dashed #e2e8f0; padding-bottom: 6px; }}
  .opts-grid {{ display: grid; grid-template-columns: 1fr 1fr; gap: 8px 18px; align-items: start; }}
  .opt-cell {{ display: flex; align-items: flex-start; font-size: 13.5px; line-height: 1.45; color: #1e293b; background: #f8fafc; padding: 6px 10px; border-radius: 4px; border: 1px solid #e2e8f0; }}
  .opt-badge {{ font-weight: bold; color: #1e3a8a; margin-right: 6px; min-width: 20px; }}
  .answer-key-page {{ page-break-before: always; break-before: page; padding-top: 10px; }}
  .ans-block {{ border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; margin-bottom: 8px; font-size: 13.5px; }}
  @media print {{
    body {{ background: #fff; padding: 0; }}
    .page {{ box-shadow: none; padding: 0; max-width: 100%; }}
    .top-actions {{ display: none !important; }}
    .answer-key-page {{ page-break-before: always !important; break-before: page !important; }}
  }}
</style>
</head>
<body>
  <div class="top-actions">
    <div><b>🖨️ Muallimun.Net Yazılı Sınav & Çalışma Kağıdı Önizleme</b> — Cevap anahtarı ayrı sayfaya basılır.</div>
    <button class="btn-print" onclick="window.print()">🖨️ Yazdır / PDF Olarak Kaydet (Ctrl+P)</button>
  </div>
  <div class="page">
    <div class="header-box">
      <div class="brand-left">
        {logo_img_html}
        <div>
          <h1>{html.escape(sheet_title)}</h1>
          <div style="font-size:12px;color:#475569;">Kaynak: {html.escape(src_combo.get())} &nbsp;|&nbsp; Seviye: {html.escape(lvl_f)} &nbsp;|&nbsp; Muallimun.Net</div>
        </div>
      </div>
      <div class="student-info">
        <b>Adı Soyadı:</b> ..............................................<br>
        <b>Sınıfı / No:</b> ........................ &nbsp; <b>Tarih:</b> {today_str}<br>
        <b>Aldığı Puan:</b> ............... / 100
      </div>
    </div>
    {sections_html}
  </div>
</body>
</html>"""

        out_path = os.path.join(DATA_DIR, "calisma_kagidi_ve_quiz.html")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(full_html)
        webbrowser.open(f"file:///{os.path.abspath(out_path).replace(os.sep, '/')}")
        app._show_toast("🖨️ Yazılı sınav ve çalışma kağıdı tarayıcıda açıldı!", GREEN, 2500)

    btn_bar = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=10)
    btn_bar.pack(fill="x", side="bottom")

    tk.Button(
        btn_bar, text="🖨️ MATERYALİ / SINAVI OLUŞTUR VE TARAYICIDA AÇ (A4 / PDF)",
        command=build_and_open_worksheet, bg=GREEN, fg="#0f172a",
        font=("Segoe UI", 11, "bold"), relief="flat", pady=9, cursor="hand2"
    ).pack(side="left", fill="x", expand=True, padx=(0, 8))

    tk.Button(
        btn_bar, text="Kapat", command=win.destroy,
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=18, pady=9, cursor="hand2"
    ).pack(side="right")


# ==============================================================================
# 1) 🗣️ GÖLGELEME (SHADOWING) AKICILIK ANTRENMANI PENCERESİ
# ==============================================================================
def open_shadowing_trainer_window(
    app,
    arabic_text: str,
    turkish_text: str = "",
    local_audio: str = None,
    voice: str = "ar-SA-HamedNeural",
    item_id: str = "shd"
):
    c = app.colors
    is_dark = (app.theme_mode == "dark")
    app._stop_all_audio()

    win = tk.Toplevel(app.root)
    win.title("🗣️ Gölgeleme (Shadowing) Akıcılık ve Ritim Antrenmanı")
    win.geometry("820x620")
    win.minsize(760, 580)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    state = {
        "running": False,
        "ref_path": None,
        "user_path": None,
        "ref_bars": [0.08] * 48,
        "user_bars": [0.08] * 48,
        "ref_dur": 0.0,
        "user_dur": 0.0
    }

    def _on_close():
        state["running"] = False
        app._stop_all_audio()
        win.destroy()

    win.protocol("WM_DELETE_WINDOW", _on_close)

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text="🗣️ GÖLGELEME (SHADOWING) AKICILIK ANTRENMANI",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")
    ).pack(side="left")
    tk.Label(
        hdr, text="Dinle ➔ Beklemeden Aynı Hız ve Tonlamayla Tekrar Et",
        bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B
    ).pack(side="right")

    text_card = tk.Frame(win, bg=c["BG_COLOR"], padx=16, pady=12, highlightthickness=1, highlightbackground=c["BORDER"])
    text_card.pack(fill="x", padx=20, pady=(12, 8))

    ar_holder = tk.Frame(text_card, bg=c["BG_COLOR"])
    ar_holder.pack(fill="x")
    lbl_ar = tk.Label(
        ar_holder, text=shape_arabic(arabic_text, max_chars_per_line=55),
        bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=(AR_FONT_FAMILY, 24, "bold"), justify="center"
    )
    lbl_ar.pack()

    if turkish_text:
        tk.Label(
            text_card, text=turkish_text, bg=c["BG_COLOR"], fg=c["TEXT_DIM"],
            font=("Segoe UI", 11), wraplength=740, justify="center"
        ).pack(pady=(4, 0))

    waves_box = tk.Frame(win, bg=c["PANEL_COLOR"], padx=16, pady=10, highlightthickness=1, highlightbackground=c["BORDER"])
    waves_box.pack(fill="x", padx=20, pady=6)

    lbl_ref_wave = tk.Label(waves_box, text="🔊 Referans Ses Dalga Boyu ve Ritmi (Süre: 0.0 sn)", bg=c["PANEL_COLOR"], fg=BLUE, font=FONT_UI_B)
    lbl_ref_wave.pack(anchor="w")
    cv_ref = tk.Canvas(waves_box, width=740, height=64, bg=c["BG_COLOR"], highlightthickness=0)
    cv_ref.pack(fill="x", pady=(2, 8))

    lbl_usr_wave = tk.Label(waves_box, text="🎙️ Sizin Ses Dalga Boyunuz ve Ritminiz (Süre: 0.0 sn)", bg=c["PANEL_COLOR"], fg=GREEN if is_dark else "#047857", font=FONT_UI_B)
    lbl_usr_wave.pack(anchor="w")
    cv_usr = tk.Canvas(waves_box, width=740, height=64, bg=c["BG_COLOR"], highlightthickness=0)
    cv_usr.pack(fill="x", pady=(2, 4))

    def draw_bars(canvas_obj, bars_list, bar_color):
        if not canvas_obj.winfo_exists():
            return
        canvas_obj.delete("all")
        w = max(680, canvas_obj.winfo_width())
        h = 64
        n = len(bars_list)
        gap = 3
        bar_w = max(4, (w - (n * gap)) // n)
        start_x = (w - (n * (bar_w + gap))) // 2
        for i, val in enumerate(bars_list):
            bh = max(4, int(val * (h - 10)))
            x0 = start_x + i * (bar_w + gap)
            y0 = (h - bh) // 2
            canvas_obj.create_rectangle(x0, y0, x0 + bar_w, y0 + bh, fill=bar_color, outline="")

    lbl_status = tk.Label(
        win, text="Hazır olduğunuzda '🎬 Gölgelemeyi Başlat' butonuna basın.",
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 12, "bold")
    )
    lbl_status.pack(pady=(6, 2))

    lbl_scores = tk.Label(
        win, text="", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 11, "bold")
    )
    lbl_scores.pack(pady=(0, 6))

    ctrl_bar = tk.Frame(win, bg=c["CARD_COLOR"])
    ctrl_bar.pack(fill="x", padx=20, pady=(4, 14))

    def play_saved_file(p_key):
        p = state.get(p_key)
        if p and os.path.exists(p):
            app._play_audio_file_async(p)

    def render_colored_words(word_results):
        for child in ar_holder.winfo_children():
            child.destroy()
        wrap = tk.Frame(ar_holder, bg=c["BG_COLOR"])
        wrap.pack()
        ok_col = GREEN if is_dark else "#047857"
        for w_str, is_ok in word_results:
            tk.Label(
                wrap, text=f"\u200f{w_str}\u200f", bg=c["BG_COLOR"],
                fg=ok_col if is_ok else RED, font=(AR_FONT_FAMILY, 22, "bold"), padx=3
            ).pack(side="right")

    def start_shadowing_cycle():
        if state["running"]:
            state["running"] = False
            return
        if not AUDIO_REC_AVAILABLE:
            messagebox.showerror("Hata", "Ses kayıt modülü (sounddevice) bulunamadı.", parent=win)
            return

        state["running"] = True
        btn_start.config(text="⏳ Gölgeleme Çalışıyor...", bg=RED, fg="white", state="disabled")
        lbl_scores.config(text="")

        def worker():
            try:
                win.after(0, lambda: lbl_status.config(text="🔊 1. Adım: Referans ses dinleniyor, dikkatle takip edin...", fg=BLUE))
                ref_p = resolve_local_media_path(local_audio) if local_audio else None
                if not ref_p:
                    ref_p = get_tts_audio(arabic_text, voice=voice, slow=app.slow_tts_var.get())

                if not ref_p or not os.path.exists(ref_p):
                    win.after(0, lambda: lbl_status.config(text="Referans ses üretilemedi.", fg=RED))
                    return

                state["ref_path"] = ref_p
                ref_bars, ref_dur = extract_audio_envelope(ref_p, num_bars=48)
                if ref_dur <= 0.2:
                    ref_dur = max(1.8, len(arabic_text.split()) * 0.65)
                state["ref_bars"] = ref_bars
                state["ref_dur"] = ref_dur

                win.after(0, lambda: (
                    lbl_ref_wave.config(text=f"🔊 Referans Ses Dalga Boyu ve Ritmi (Süre: {ref_dur:.1f} sn)"),
                    draw_bars(cv_ref, ref_bars, BLUE),
                    btn_listen_ref.config(state="normal")
                ))

                ensure_mixer_initialized()
                with audio_lock:
                    if pygame.mixer.music.get_busy():
                        pygame.mixer.music.stop()
                    pygame.mixer.music.load(ref_p)
                    pygame.mixer.music.play()
                while pygame.mixer.music.get_busy() and state["running"] and win.winfo_exists():
                    time.sleep(0.04)

                if not state["running"] or not win.winfo_exists():
                    return

                play_beep_nonblocking()
                time.sleep(0.18)

                win.after(0, lambda: lbl_status.config(text="🎙️ 2. Adım: ŞİMDİ AYNI HIZ VE TONLAMAYLA TEKRAR EDİN!", fg=GREEN if is_dark else "#047857"))

                max_rec_sec = max(3.2, min(14.0, ref_dur * 1.65 + 1.2))
                rec_frames = []
                speech_started = False
                silence_cnt = 0
                recording_flag = [True]

                def _rec_cb(indata, frame_count, time_info, status):
                    if not recording_flag[0]:
                        return
                    rec_frames.append(indata.copy())
                    rms = float(np.sqrt(np.mean(np.square(indata))))
                    nonlocal speech_started, silence_cnt
                    if rms > 0.02:
                        speech_started = True
                        silence_cnt = 0
                    elif speech_started:
                        silence_cnt += 1
                        if silence_cnt > 45:
                            recording_flag[0] = False

                stream = sd.InputStream(samplerate=SAMPLERATE, channels=1, callback=_rec_cb)
                stream.start()
                start_t = time.time()
                while recording_flag[0] and state["running"] and win.winfo_exists() and (time.time() - start_t) < max_rec_sec:
                    time.sleep(0.05)
                recording_flag[0] = False
                stream.stop()
                stream.close()

                if not rec_frames or not win.winfo_exists():
                    win.after(0, lambda: lbl_status.config(text="Ses algılanamadı, tekrar deneyin.", fg=RED))
                    return

                audio_arr = np.concatenate(rec_frames, axis=0).flatten()
                usr_path = os.path.join(RECORDINGS_DIR, f"shadow_{item_id}_{int(time.time())}.wav")
                audio_i16 = np.int16(np.clip(audio_arr, -1.0, 1.0) * 32767)
                with wave.open(usr_path, "wb") as wf:
                    wf.setnchannels(1)
                    wf.setsampwidth(2)
                    wf.setframerate(SAMPLERATE)
                    wf.writeframes(audio_i16.tobytes())

                state["user_path"] = usr_path
                usr_bars, usr_dur = extract_audio_envelope(usr_path, num_bars=48)
                state["user_bars"] = usr_bars
                state["user_dur"] = usr_dur

                win.after(0, lambda: (
                    lbl_usr_wave.config(text=f"🎙️ Sizin Ses Dalga Boyunuz ve Ritminiz (Süre: {usr_dur:.1f} sn)"),
                    draw_bars(cv_usr, usr_bars, GREEN if is_dark else "#047857"),
                    btn_listen_usr.config(state="normal"),
                    lbl_status.config(text="⏳ Telaffuz, Mahreç ve Ritim Uyumu Analiz Ediliyor...", fg=YELLOW)
                ))

                dur_diff = abs(ref_dur - usr_dur)
                tempo_score = max(20, int(100 * max(0.0, 1.0 - (dur_diff / max(1.5, ref_dur)))))

                r_np = np.array(ref_bars)
                u_np = np.array(usr_bars)
                corr = float(np.dot(r_np, u_np) / (np.linalg.norm(r_np) * np.linalg.norm(u_np) + 1e-6))
                rhythm_score = int(min(100, (tempo_score * 0.6) + (corr * 100 * 0.4)))

                pron_pct = 80
                word_res = []
                makhraj_hint = ""
                if app.model:
                    audio_16k = load_wav_as_16k_float32(usr_path)
                    segs, _ = app.model.transcribe(audio_16k, language="ar", beam_size=5, vad_filter=True)
                    heard_str = "".join([s.text for s in segs]).strip()
                    sim_score, _, _ = compare_arabic(arabic_text, heard_str)
                    word_res, w_ratio = evaluate_sentence_words(arabic_text, heard_str, word_threshold=0.72)
                    m_tips = analyze_phoneme_and_makhraj_errors(arabic_text, heard_str)
                    if m_tips:
                        makhraj_hint = f"  |  {m_tips[0]['title']}"
                    pron_pct = int(round(max(sim_score, w_ratio) * 100))

                fluency_total = int((pron_pct * 0.65) + (rhythm_score * 0.35))
                record_daily_activity(fluency_total >= 70)

                def _show_final():
                    if not win.winfo_exists():
                        return
                    if word_res:
                        render_colored_words(word_res)
                    verdict_col = (GREEN if is_dark else "#047857") if fluency_total >= 70 else RED
                    lbl_status.config(
                        text=f"{'✅ Mükemmel Akıcılık!' if fluency_total >= 70 else '⚠️ Tekrar Gölgeleyin'} — Genel Akıcılık Puanı: %{fluency_total}{makhraj_hint}",
                        fg=verdict_col
                    )
                    lbl_scores.config(
                        text=f"🎯 Telaffuz Doğruluğu: %{pron_pct}   |   🎵 Ritim & Süre Uyumu: %{rhythm_score} (Referans: {ref_dur:.1f}sn / Siz: {usr_dur:.1f}sn)"
                    )
                    app._update_stats_ui()

                win.after(0, _show_final)
            except Exception as ex:
                log_error("Shadowing döngüsü hatası", ex)
                if win.winfo_exists():
                    win.after(0, lambda: lbl_status.config(text=f"Hata: {ex}", fg=RED))
            finally:
                state["running"] = False
                if win.winfo_exists():
                    win.after(0, lambda: btn_start.config(text="🎬 Gölgelemeyi Tekrar Başlat", bg=BLUE, fg="white", state="normal"))

        threading.Thread(target=worker, daemon=True).start()

    btn_start = tk.Button(
        ctrl_bar, text="🎬 Gölgelemeyi Başlat (Dinle & Anında Tekrar Et)",
        command=start_shadowing_cycle, bg=BLUE, fg="white", font=("Segoe UI", 11, "bold"),
        relief="flat", padx=16, pady=8, cursor="hand2"
    )
    btn_start.pack(side="left", padx=(0, 6))

    btn_listen_ref = tk.Button(
        ctrl_bar, text="🔊 Referansı Dinle", command=lambda: play_saved_file("ref_path"),
        bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2", state="disabled"
    )
    btn_listen_ref.pack(side="left", padx=4)

    btn_listen_usr = tk.Button(
        ctrl_bar, text="🎧 Sesimi Dinle", command=lambda: play_saved_file("user_path"),
        bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2", state="disabled"
    )
    btn_listen_usr.pack(side="left", padx=4)

    tk.Button(
        ctrl_bar, text="Kapat", command=_on_close,
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=18, pady=8, cursor="hand2"
    ).pack(side="right")

    win.after(150, lambda: (draw_bars(cv_ref, state["ref_bars"], BLUE), draw_bars(cv_usr, state["user_bars"], GREEN)))


# ==============================================================================
# 2) 🎧 GELİŞMİŞ DİNLE VE BUL — SESLİ ANLAMA SINAVI VE PRATİK MERKEZİ
# ==============================================================================
def open_listening_warmup_window(app, mode: str = "words"):
    c = app.colors
    is_dark = (app.theme_mode == "dark")
    app._stop_all_audio()

    win = tk.Toplevel(app.root)
    win.title("🎧 Dinle ve Bul — Sesli Anlama Sınavı ve Pratik Merkezi")
    win.geometry("880x630")
    win.minsize(820, 560)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    def _on_exam_close():
        app._stop_all_audio()
        win.destroy()

    win.protocol("WM_DELETE_WINDOW", _on_exam_close)

    source_labels = {
        "words": "🏠 Görselli Kelimeler",
        "dictionary": "📖 Sarf Sözlüğü",
        "sentences": "📜 Anahtar Cümleler",
        "dialogue": "💬 Diyalog Cümleleri"
    }
    label_to_source = {v: k for k, v in source_labels.items()}

    init_src_key = mode if mode in source_labels else "words"
    if mode == "words" and getattr(app, "current_cat_id", "").startswith("szl_"):
        init_src_key = "dictionary"
    elif mode == "words" and getattr(app, "current_cat_id", "") == "sozluk_tumu":
        init_src_key = "dictionary"

    q_state = {
        "questions": [],
        "idx": 0,
        "correct": 0,
        "wrong": 0,
        "locked": True,
        "unlimited": False,
        "started": False
    }
    show_ar_hint = tk.BooleanVar(value=False)

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text="🎧 DİNLE & BUL — SESLİ SINAV VE PRATİK MERKEZİ",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")
    ).pack(side="left")
    lbl_score = tk.Label(
        hdr, text="Hazır  |  Doğru: 0  |  Yanlış: 0  |  Başarı: %0",
        bg=c["PANEL_COLOR"], fg=GREEN if is_dark else "#047857", font=("Segoe UI", 11, "bold")
    )
    lbl_score.pack(side="right")

    cfg_bar = tk.Frame(win, bg=c["BG_COLOR"], padx=16, pady=8, highlightthickness=1, highlightbackground=c["BORDER"])
    cfg_bar.pack(fill="x", padx=20, pady=(10, 4))

    tk.Label(cfg_bar, text="Kaynak:", bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    src_var = tk.StringVar(value=source_labels[init_src_key])
    cmb_src = ttk.Combobox(cfg_bar, textvariable=src_var, values=list(source_labels.values()), state="readonly", width=18, font=FONT_UI)
    cmb_src.pack(side="left", padx=(4, 8))

    tk.Label(cfg_bar, text="Paket:", bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    pkg_var = tk.StringVar(value="Tümü")
    cmb_pkg = ttk.Combobox(cfg_bar, textvariable=pkg_var, state="readonly", width=22, font=FONT_UI)
    cmb_pkg.pack(side="left", padx=(4, 8))

    tk.Label(cfg_bar, text="Seviye:", bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    lvl_var = tk.StringVar(value="Tümü")
    cmb_lvl = ttk.Combobox(cfg_bar, textvariable=lvl_var, values=["Tümü", "A1", "A2", "B1", "B2", "C1"], state="readonly", width=5, font=FONT_UI)
    cmb_lvl.pack(side="left", padx=(4, 8))

    tk.Label(cfg_bar, text="Hedef:", bg=c["BG_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    cnt_var = tk.StringVar(value="10 Soru")
    cmb_cnt = ttk.Combobox(
        cfg_bar, textvariable=cnt_var,
        values=["10 Soru", "20 Soru", "50 Soru", "♾️ Sınırsız"],
        state="readonly", width=11, font=FONT_UI_B
    )
    cmb_cnt.pack(side="left", padx=(4, 8))

    pkg_lookup = {}

    def populate_pkg_combo(*_):
        s_key = label_to_source.get(src_var.get(), "words")
        pkg_lookup.clear()
        names = ["Tümü"]
        pkg_lookup["Tümü"] = "all"
        if s_key == "words":
            for pid, pad in get_word_packages():
                if not pid.startswith("szl_") and pid != "sozluk_tumu":
                    pkg_lookup[pad] = pid
                    names.append(pad)
        elif s_key == "dictionary":
            for pid, pad in get_word_packages():
                if pid.startswith("szl_"):
                    clean_ad = pad.replace("📖 Sözlük: ", "")
                    pkg_lookup[clean_ad] = pid
                    names.append(clean_ad)
        elif s_key == "dialogue":
            for did, _, _, b_tr in get_dialogues():
                disp = f"[{did}] {b_tr}"
                pkg_lookup[disp] = did
                names.append(disp)
        else:
            for pid, pad in get_sentence_packages():
                pkg_lookup[pad] = pid
                names.append(pad)
        cmb_pkg["values"] = names
        cmb_pkg.set("Tümü")

    top_ctrl = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=8)
    top_ctrl.pack(fill="x")

    def play_current_question_audio():
        if not q_state["started"]:
            return
        qs = q_state["questions"]
        if not qs or q_state["idx"] >= len(qs):
            return
        target = qs[q_state["idx"]]["target"]
        ses_p = getattr(target, "ses", None)
        app._play_tts_text_async(target.arapca, "ar-SA-HamedNeural", ses_p)

    btn_start_exam = tk.Button(
        top_ctrl, text="▶️ SINAVI BAŞLAT", command=lambda: start_new_exam(),
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 11, "bold"), relief="flat", padx=16, pady=6, cursor="hand2"
    )
    btn_start_exam.pack(side="left", padx=(0, 6))

    btn_replay = tk.Button(
        top_ctrl, text="🔊 Sesi Dinle / Tekrar Çal", command=play_current_question_audio,
        bg=ACCENT, fg="white", font=("Segoe UI", 10, "bold"), relief="flat", padx=14, pady=6, cursor="hand2", state="disabled"
    )
    btn_replay.pack(side="left", padx=4)

    btn_finish = tk.Button(
        top_ctrl, text="🏁 Bitir & Karne", command=lambda: show_round_summary_bar(),
        bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=6, cursor="hand2", state="disabled"
    )
    btn_finish.pack(side="left", padx=4)

    tk.Checkbutton(
        top_ctrl, text="👁️ Arapça Yazılışı Göster (İpucu)", variable=show_ar_hint,
        command=lambda: update_hint_label(),
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI_B
    ).pack(side="right")

    lbl_ar_hint = tk.Label(
        win,
        text="🎯 Yukarıdan kaynak, paket ve soru hedefini seçip '▶️ SINAVI BAŞLAT' butonuna basın.",
        bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 14, "bold"),
        wraplength=820, justify="center"
    )
    lbl_ar_hint.pack(pady=(6, 10))

    options_frame = tk.Frame(win, bg=c["CARD_COLOR"], padx=24)
    options_frame.pack(fill="both", expand=True, pady=2)

    option_buttons = []
    for i in range(4):
        btn = tk.Button(
            options_frame, text=f"  {['A', 'B', 'C', 'D'][i]})   [ Sınav başladığında seçenekler burada görünecektir ]",
            bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"],
            font=("Segoe UI", 12, "bold"), relief="flat", pady=11, padx=14,
            anchor="w", cursor="hand2", wraplength=760, state="disabled"
        )
        btn.pack(fill="x", pady=4)
        option_buttons.append(btn)

    summary_bar = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10, highlightthickness=2, highlightbackground=GREEN)

    lbl_summary_msg = tk.Label(summary_bar, text="", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 11, "bold"))
    lbl_summary_msg.pack(side="left")

    tk.Button(
        summary_bar, text="✅ Kapat", command=_on_exam_close,
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=14, pady=6, cursor="hand2"
    ).pack(side="right", padx=(6, 0))

    tk.Button(
        summary_bar, text="🔄 Sıfırdan Başlat", command=lambda: start_new_exam(),
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=14, pady=6, cursor="hand2"
    ).pack(side="right", padx=6)

    tk.Button(
        summary_bar, text="➕ 10 Soru Daha Devam Et", command=lambda: append_more_questions(10),
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 10, "bold"), relief="flat", padx=16, pady=6, cursor="hand2"
    ).pack(side="right", padx=6)

    def prepare_ready_state(*_):
        app._stop_all_audio()
        summary_bar.pack_forget()
        q_state["started"] = False
        q_state["locked"] = True
        q_state["questions"] = []
        btn_start_exam.config(text="▶️ SINAVI BAŞLAT", bg=GREEN, fg="#0f172a")
        btn_replay.config(state="disabled")
        btn_finish.config(state="disabled")
        lbl_score.config(text=f"Hedef: {cnt_var.get()}  |  Başlamak için '▶️ SINAVI BAŞLAT'a basın")
        lbl_ar_hint.config(
            text="🎯 Seçimlerinizi yaptınız. Hazır olduğunuzda '▶️ SINAVI BAŞLAT' butonuna basın.",
            font=("Segoe UI", 14, "bold"), fg=c["ACCENT_HEAD"]
        )
        letters = ["A", "B", "C", "D"]
        for i, btn in enumerate(option_buttons):
            btn.config(
                state="disabled",
                text=f"  {letters[i]})   [ Sınav başladığında seçenekler burada görünecektir ]",
                bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"]
            )

    cmb_src.bind("<<ComboboxSelected>>", lambda e: (populate_pkg_combo(), prepare_ready_state()))
    cmb_pkg.bind("<<ComboboxSelected>>", prepare_ready_state)
    cmb_lvl.bind("<<ComboboxSelected>>", prepare_ready_state)
    cmb_cnt.bind("<<ComboboxSelected>>", prepare_ready_state)

    def _update_score_header():
        tot_answered = q_state["correct"] + q_state["wrong"]
        acc_pct = int((q_state["correct"] / tot_answered) * 100) if tot_answered > 0 else 0
        total_q_disp = "♾️" if q_state["unlimited"] else str(len(q_state["questions"]))
        cur_num = min(q_state["idx"] + 1, len(q_state["questions"])) if q_state["questions"] else 0
        lbl_score.config(
            text=f"Soru: {cur_num}/{total_q_disp}  |  Doğru: {q_state['correct']}  |  Yanlış: {q_state['wrong']}  |  Başarı: %{acc_pct}"
        )

    def update_hint_label():
        if not q_state["started"]:
            return
        qs = q_state["questions"]
        if not qs or q_state["idx"] >= len(qs):
            return
        target = qs[q_state["idx"]]["target"]
        if show_ar_hint.get():
            lbl_ar_hint.config(
                text=shape_arabic(target.arapca),
                font=(AR_FONT_FAMILY, 24, "bold"), fg=c["TEXT_AR"]
            )
        else:
            lbl_ar_hint.config(
                text="🔒 [ Seslendirilen İfadeyi Dinleyip Doğru Şıkkı Seçin ]",
                font=("Segoe UI", 15, "bold"), fg=c["TEXT_AR"]
            )

    def _fetch_question_batch(batch_count: int) -> list:
        s_key = label_to_source.get(src_var.get(), "words")
        sel_pkg_id = pkg_lookup.get(pkg_var.get(), "all")
        sel_lvl = lvl_var.get()
        return generate_listening_warmup_questions(
            mode=s_key, pool_items=None, count=batch_count,
            paket_id=sel_pkg_id, seviye=sel_lvl
        )

    def start_new_exam():
        summary_bar.pack_forget()
        cnt_str = cnt_var.get()
        is_unlim = ("Sınırsız" in cnt_str)
        batch_n = 25 if is_unlim else int(cnt_str.split()[0])

        new_qs = _fetch_question_batch(batch_n)
        if not new_qs:
            messagebox.showwarning(
                "Yetersiz İçerik",
                "Seçili paket ve seviyede 4 şıklı sınav oluşturmak için en az 4 farklı kayıt bulunmalıdır.\nLütfen seviyeyi 'Tümü' yapın veya başka bir paket seçin.",
                parent=win
            )
            return

        q_state["questions"] = new_qs
        q_state["idx"] = 0
        q_state["correct"] = 0
        q_state["wrong"] = 0
        q_state["locked"] = False
        q_state["unlimited"] = is_unlim
        q_state["started"] = True

        btn_start_exam.config(text="🔄 Yeniden Başlat", bg=BLUE, fg="white")
        btn_replay.config(state="normal")
        btn_finish.config(state="normal")
        load_question()

    def append_more_questions(extra_count: int = 10):
        summary_bar.pack_forget()
        more_qs = _fetch_question_batch(extra_count)
        if not more_qs:
            return
        q_state["questions"].extend(more_qs)
        q_state["locked"] = False
        q_state["started"] = True
        for btn in option_buttons:
            btn.config(state="normal")
        load_question()

    def show_round_summary_bar():
        q_state["locked"] = True
        tot = q_state["correct"] + q_state["wrong"]
        pct = int((q_state["correct"] / tot) * 100) if tot > 0 else 0
        summary_txt = f"{src_var.get()} ({pkg_var.get()}) — Çözülen: {tot} | Doğru: {q_state['correct']} | Yanlış: {q_state['wrong']} | Başarı: %{pct}"
        set_config_val("last_listening_exam", summary_txt)
        lbl_summary_msg.config(
            text=f"🏆 Tur Tamamlandı! Çözülen: {tot}  |  Doğru: {q_state['correct']}  |  Yanlış: {q_state['wrong']}  |  Başarı: %{pct}"
        )
        summary_bar.pack(fill="x", padx=20, pady=(4, 12), side="bottom")
        app._update_stats_ui()

    def load_question():
        qs = q_state["questions"]
        if q_state["idx"] >= len(qs):
            if q_state["unlimited"]:
                more_qs = _fetch_question_batch(20)
                if more_qs:
                    qs.extend(more_qs)
                else:
                    show_round_summary_bar()
                    return
            else:
                show_round_summary_bar()
                return

        q_state["locked"] = False
        q_data = qs[q_state["idx"]]
        _update_score_header()
        update_hint_label()

        letters = ["A", "B", "C", "D"]
        for idx, opt_item in enumerate(q_data["options"]):
            option_buttons[idx].config(
                state="normal",
                text=f"  {letters[idx]})   {opt_item.turkce}",
                bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"],
                command=lambda chosen_id=opt_item.id, b_idx=idx: check_answer(chosen_id, b_idx)
            )

        win.after(180, play_current_question_audio)

    def check_answer(chosen_id, btn_idx):
        if q_state["locked"] or not q_state["started"]:
            return
        q_state["locked"] = True
        q_data = q_state["questions"][q_state["idx"]]
        correct_id = q_data["correct_id"]
        target = q_data["target"]

        lbl_ar_hint.config(
            text=shape_arabic(target.arapca),
            font=(AR_FONT_FAMILY, 24, "bold"), fg=c["TEXT_AR"]
        )

        s_key = label_to_source.get(src_var.get(), "words")
        db_mode = "words" if s_key in ("words", "dictionary") else ("dialogue" if s_key == "dialogue" else "sentences")
        is_ok = (chosen_id == correct_id)
        try:
            update_db(target.id, db_mode, is_ok)
        except Exception as ex:
            log_error("Dinle & Bul DB güncelleme hatası", ex)

        if is_ok:
            q_state["correct"] += 1
            option_buttons[btn_idx].config(bg=GREEN, fg="#0f172a")
            app._play_praise_sound()
            _update_score_header()
            win.after(1000, next_q)
        else:
            q_state["wrong"] += 1
            option_buttons[btn_idx].config(bg=RED, fg="white")
            for i, opt_item in enumerate(q_data["options"]):
                if opt_item.id == correct_id:
                    option_buttons[i].config(bg=GREEN, fg="#0f172a")
            _update_score_header()
            win.after(1500, next_q)

    def next_q():
        if not win.winfo_exists() or not q_state["started"]:
            return
        q_state["idx"] += 1
        load_question()

    populate_pkg_combo()
    prepare_ready_state()


# ==============================================================================
# 3) DİYALOG MODU BİREYSEL SATIR VE BÜTÜNCÜL DİYALOG SEVİYESİ DÜZENLEYİCİ
# ==============================================================================
def open_quick_edit_dialogue_line(app, line_id: int, speaker: str, speaker_ar: str, gender: str, text_ar: str, text_tr: str, ses_path: str = None):
    c = app.colors
    win = tk.Toplevel(app.root)
    win.title(f"✏️ Diyalog Cümlesini Düzenle (Satır #{line_id})")
    win.geometry("680x460")
    win.minsize(600, 420)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    mic_rec = AdminMicRecorder()
    parent_dlg_id = get_dialogue_id_for_line(line_id)
    cur_dlg_sev, _ = get_package_level_and_topic("dialogue", parent_dlg_id)

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text=f"✏️ DİYALOG CÜMLESİNİ DÜZENLE — {speaker} (Diyalog: {parent_dlg_id})",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold")
    ).pack(anchor="w")

    form = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=12)
    form.pack(fill="both", expand=True)

    r1 = tk.Frame(form, bg=c["CARD_COLOR"])
    r1.pack(fill="x", pady=(0, 8))

    tk.Label(r1, text="Konuşmacı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    ent_spk = tk.Entry(r1, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"], width=13)
    ent_spk.insert(0, speaker or "")
    ent_spk.pack(side="left", padx=(4, 10))

    tk.Label(r1, text="Arapça Adı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    ent_spk_ar = tk.Entry(r1, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=(AR_FONT_FAMILY, 13, "bold"), justify="right", insertbackground=c["TEXT_MAIN"], width=10)
    ent_spk_ar.insert(0, speaker_ar or "")
    ent_spk_ar.pack(side="left", padx=(4, 10))

    tk.Label(r1, text="Cinsiyet:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    gender_var = tk.StringVar(value=gender if gender in ("E", "K") else "E")
    cmb_gender = ttk.Combobox(r1, textvariable=gender_var, values=["E", "K"], state="readonly", width=3, font=FONT_UI_B)
    cmb_gender.pack(side="left", padx=(4, 10))

    tk.Label(r1, text="🏷️ Diyalog Seviyesi:", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    dlg_lvl_var = tk.StringVar(value=cur_dlg_sev if cur_dlg_sev in ("A1", "A2", "B1", "B2", "C1") else "A1")
    ttk.Combobox(r1, textvariable=dlg_lvl_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=4, font=FONT_UI_B).pack(side="left", padx=4)

    tk.Label(form, text="Arapça Cümle (Harekeli / RTL):", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", pady=(4, 2))
    ent_ar = tk.Entry(form, font=(AR_FONT_FAMILY, 16, "bold"), bg=c["BG_COLOR"], fg=c["TEXT_AR"], justify="right", insertbackground=c["TEXT_MAIN"])
    ent_ar.insert(0, text_ar or "")
    ent_ar.pack(fill="x", pady=4, ipady=4)

    tk.Label(form, text="Türkçe Anlamı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", pady=(8, 2))
    ent_tr = tk.Entry(form, font=("Segoe UI", 11), bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    ent_tr.insert(0, text_tr or "")
    ent_tr.pack(fill="x", pady=4, ipady=3)

    tk.Label(form, text="🎵 Ses Dosyası (Mikrofondan kaydedebilir veya dosya/URL seçebilirsiniz):", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B).pack(anchor="w", pady=(8, 2))
    r_aud = tk.Frame(form, bg=c["CARD_COLOR"])
    r_aud.pack(fill="x", pady=2)

    ent_ses = tk.Entry(r_aud, font=FONT_UI, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    ent_ses.insert(0, ses_path or "")
    ent_ses.pack(side="left", fill="x", expand=True, padx=(0, 6), ipady=2)

    def toggle_mic_line():
        if not mic_rec.recording:
            try:
                app._stop_all_audio()
                mic_rec.start()
                btn_mic.config(text="⏹️ Bitir", bg=RED)
            except Exception as ex:
                messagebox.showerror("Hata", str(ex), parent=win)
        else:
            btn_mic.config(text="🎙️ Kaydet", bg="#0d9488")
            try:
                rel = mic_rec.stop_and_save(f"dlgline_{line_id}")
                ent_ses.delete(0, tk.END)
                ent_ses.insert(0, rel)
                app._show_toast("Ses kaydedildi!", GREEN)
            except Exception as ex:
                messagebox.showwarning("Uyarı", str(ex), parent=win)

    def browse_line_audio():
        p = filedialog.askopenfilename(title="Ses Dosyası Seç (.mp3 / .wav)", filetypes=[("Ses", "*.mp3 *.wav")], parent=win)
        if not p:
            return
        ext = os.path.splitext(p)[1].lower()
        dst_name = f"dlgline_{line_id}_{int(time.time())}{ext}"
        shutil.copy(p, os.path.join(CUSTOM_AUDIO_DIR, dst_name))
        ent_ses.delete(0, tk.END)
        ent_ses.insert(0, f"custom_audio/{dst_name}")

    def download_line_url():
        u = ent_ses.get().strip()
        if not u.startswith(("http://", "https://")):
            messagebox.showwarning("URL", "Lütfen ses kutusuna geçerli bir http/https ses bağlantısı yapıştırın.", parent=win)
            return
        try:
            rel = download_audio_from_url(u, f"dlgline_{line_id}")
            ent_ses.delete(0, tk.END)
            ent_ses.insert(0, rel)
            app._show_toast("Ses URL'den indirildi.", GREEN)
        except Exception as ex:
            messagebox.showerror("Hata", str(ex), parent=win)

    def preview_line_audio():
        s_val = ent_ses.get().strip()
        ar_val = ent_ar.get().strip()
        loc = resolve_local_media_path(s_val)
        if loc:
            app._play_audio_file_async(loc)
        elif ar_val:
            v = app._detect_voice(ent_spk.get().strip(), gender_var.get())
            app._play_tts_text_async(ar_val, v)

    btn_mic = tk.Button(r_aud, text="🎙️ Kaydet", command=toggle_mic_line, bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2")
    btn_mic.pack(side="left", padx=2)
    tk.Button(r_aud, text="📁 Gözat", command=browse_line_audio, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(r_aud, text="🌐 URL", command=download_line_url, bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(r_aud, text="🔊 Dinle", command=preview_line_audio, bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(r_aud, text="🧹", command=lambda: ent_ses.delete(0, tk.END), bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=6, pady=2, cursor="hand2").pack(side="left", padx=2)

    btn_row = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=12)
    btn_row.pack(fill="x", side="bottom")

    def save_changes():
        new_spk = ent_spk.get().strip() or "Kişi"
        new_spk_ar = ent_spk_ar.get().strip()
        new_g = gender_var.get()
        new_ar = ent_ar.get().strip()
        new_tr = ent_tr.get().strip()
        new_ses = ent_ses.get().strip() or None
        new_dlg_lvl = dlg_lvl_var.get().strip().upper()

        if not new_ar:
            messagebox.showwarning("Uyarı", "Arapça cümle boş bırakılamaz.", parent=win)
            return

        update_single_dialogue_line_db(line_id, new_spk, new_spk_ar, new_g, new_ar, new_tr, new_ses, diyalog_seviye=new_dlg_lvl)
        app._load_dialogues_ui()
        app._show_toast(f"✅ Diyalog cümlesi ve diyalog seviyesi ({new_dlg_lvl}) güncellendi!", GREEN)
        win.destroy()

    def delete_line():
        if messagebox.askyesno("Cümleyi Sil", "Bu diyalog satırını silmek istediğinize emin misiniz?", parent=win):
            delete_single_dialogue_line_db(line_id)
            app._populate_role_combo()
            app._refresh_chat()
            app._show_toast("🗑️ Diyalog satırı silindi.", RED)
            win.destroy()

    tk.Button(btn_row, text="💾 KAYDET VE GÜNCELLE", command=save_changes, bg=GREEN, fg="#0f172a", font=("Segoe UI", 11, "bold"), relief="flat", pady=8, cursor="hand2").pack(side="left", fill="x", expand=True, padx=(0, 6))
    tk.Button(btn_row, text="🗑️ SATIRI SİL", command=delete_line, bg=RED, fg="white", font=("Segoe UI", 11, "bold"), relief="flat", padx=16, pady=8, cursor="hand2").pack(side="right")


# ==============================================================================
# 4) FERAHLATILMIŞ SEKMELİ AYARLAR PENCERESİ (ÖĞRETMEN BAR GİZLEME & GITHUB GÜNCELLEME)
# ==============================================================================
def open_settings_window(app):
    """
    Sekme başlıklarının kesilmemesi için genişliği 860px yapılan,
    'Ana Ekranda Öğretmen Barını Göster' anahtarı ve '🔄 Güncelleme' sekmesi içeren Ayarlar Penceresi.
    """
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("⚙️ Uygulama ve Eğitim Ayarları")
    win.geometry("860x540")
    win.minsize(800, 480)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    style = ttk.Style(win)
    try:
        style.theme_use("clam")
    except Exception:
        pass
    style.configure("Settings.TNotebook", background=c["CARD_COLOR"], borderwidth=0)
    style.configure(
        "Settings.TNotebook.Tab",
        background=c["PANEL_COLOR"],
        foreground=c["TEXT_MAIN"],
        padding=[12, 7],
        font=("Segoe UI", 10, "bold")
    )
    style.map(
        "Settings.TNotebook.Tab",
        background=[("selected", BLUE)],
        foreground=[("selected", "#ffffff")]
    )

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(hdr, text="⚙️ UYGULAMA VE EĞİTİM AYARLARI", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")).pack(side="left")
    tk.Label(hdr, text=f"Sürüm: v{APP_VERSION}", bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B).pack(side="right")

    nb = ttk.Notebook(win, style="Settings.TNotebook")
    nb.pack(fill="both", expand=True, padx=18, pady=12)

    def make_tab_frame():
        f = tk.Frame(nb, bg=c["CARD_COLOR"], padx=18, pady=14)
        f.grid_columnconfigure(0, weight=1)
        f.grid_columnconfigure(1, weight=1)
        return f

    # 1. Sekme: 🏠 Genel & Çalışma
    tab_gen = make_tab_frame()
    nb.add(tab_gen, text="🏠 Genel & Çalışma")

    left_g = tk.LabelFrame(tab_gen, text=" Çalışma Akışı ve Arayüz ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    left_g.grid(row=0, column=0, sticky="nsew", padx=(0, 8), pady=4)

    for text, var, cmd in [
        ("⏩ Doğru Bilince Otomatik İlerle", app.auto_advance_var, None),
        ("🙌 Eller Serbest Mod (Otomatik Kayıt)", app.auto_start_var, None),
        ("🎲 Kartları Rastgele Sırala", app.random_order_var, app._refresh_current_mode_data),
        ("✏️ Düzenleme (Edit) Modunu Aç", app.edit_mode_var, app._apply_edit_mode_ui),
        ("👨‍🏫 Ana Ekranda 'Öğretmen & Ölçme Araçları' Barını Göster", app.show_teacher_bar_var, app._on_toggle_teacher_bar),
    ]:
        tk.Checkbutton(
            left_g, text=text, variable=var, command=cmd,
            bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
            activebackground=c["CARD_COLOR"], font=FONT_UI
        ).pack(anchor="w", pady=3)

    right_g = tk.LabelFrame(tab_gen, text=" Öğrenci Bilgisi & Ölçme Hedefleri ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    right_g.grid(row=0, column=1, sticky="nsew", padx=(8, 0), pady=4)

    tk.Label(right_g, text="👤 Öğrenci / Kullanıcı Adı Soyadı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", pady=(0, 3))
    ent_std_name = tk.Entry(right_g, textvariable=app.student_name_var, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"])
    ent_std_name.pack(fill="x", pady=(0, 8), ipady=2)

    tk.Label(right_g, text="Kelime/Cümle Deneme Hakkı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", pady=(2, 4))
    ttk.Combobox(right_g, values=["1 Kez", "2 Kez", "3 Kez", "5 Kez", "Sınırsız"], textvariable=app.max_attempts_var, state="readonly", width=18, font=FONT_UI).pack(anchor="w", pady=(0, 10))

    tk.Checkbutton(
        right_g, text="🙈 Sınav Modu (Arapçayı Gizle)", variable=app.test_mode_var,
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI
    ).pack(anchor="w", pady=4)

    # 2. Sekme: 🔊 Ses & TTS
    tab_aud = make_tab_frame()
    nb.add(tab_aud, text="🔊 Ses & TTS")

    left_a = tk.LabelFrame(tab_aud, text=" Seslendirme Tercihi ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    left_a.grid(row=0, column=0, sticky="nsew", padx=(0, 8), pady=4)

    for text in ["Kadın", "Erkek", "Sırayla"]:
        tk.Radiobutton(
            left_a, text=f"🎙️ {text} Sesi", variable=app.voice_pref_var, value=text,
            bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
            activebackground=c["CARD_COLOR"], font=FONT_UI
        ).pack(anchor="w", pady=4)

    right_a = tk.LabelFrame(tab_aud, text=" Hız & Tekrar ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    right_a.grid(row=0, column=1, sticky="nsew", padx=(8, 0), pady=4)

    tk.Checkbutton(
        right_a, text="🔊 Kelime Geçişlerinde Otomatik Seslendir", variable=app.auto_play_var,
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI
    ).pack(anchor="w", pady=4)

    tk.Checkbutton(
        right_a, text="🐢 Yavaş Seslendirme (TTS %25 Yavaş)", variable=app.slow_tts_var,
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI
    ).pack(anchor="w", pady=4)

    rep_f = tk.Frame(right_a, bg=c["CARD_COLOR"])
    rep_f.pack(anchor="w", pady=8)
    tk.Label(rep_f, text="Referans Ses Tekrar Sayısı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI).pack(side="left")
    tk.Spinbox(rep_f, from_=1, to=3, textvariable=app.repeat_count_var, width=5, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=8)

    # 3. Sekme: 💬 Diyalog & Ölçme
    tab_dlg = make_tab_frame()
    nb.add(tab_dlg, text="💬 Diyalog & Ölçme")

    box_d = tk.LabelFrame(tab_dlg, text=" 🎭 Karşılıklı Diyalog Canlandırma (Rol Yapma) Ayarları ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=16, pady=14)
    box_d.grid(row=0, column=0, columnspan=2, sticky="nsew", pady=4)

    rp_r = tk.Frame(box_d, bg=c["CARD_COLOR"])
    rp_r.pack(anchor="w", pady=6)
    tk.Label(rp_r, text="Canlandırma Modunda Cümle Başına Hak Sayısı (1 - 5):", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    tk.Spinbox(rp_r, from_=1, to=5, textvariable=app.roleplay_max_attempts_var, width=6, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B).pack(side="left", padx=12)

    tk.Checkbutton(
        box_d, text="✅ Cümle Bitmeden Kullanıcının Doğru Söylediği Kelimeleri Ekrana Yansıt (Yeşil)",
        variable=app.roleplay_progressive_reveal_var,
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI_B
    ).pack(anchor="w", pady=8)

    # 4. Sekme: 🎨 Görünüm & Tema
    tab_view = make_tab_frame()
    nb.add(tab_view, text="🎨 Görünüm & Tema")

    left_v = tk.LabelFrame(tab_view, text=" Tema Seçimi ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    left_v.grid(row=0, column=0, sticky="nsew", padx=(0, 8), pady=4)

    tk.Label(left_v, text=f"Aktif Tema: {'🌙 Karanlık Mod' if is_dark else '☀️ Aydınlık Mod'}", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", pady=6)
    tk.Button(
        left_v, text="🔄 Temayı Değiştir (Aydınlık / Karanlık)",
        command=lambda: (app._toggle_theme(), win.destroy(), open_settings_window(app)),
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=6, cursor="hand2"
    ).pack(anchor="w", pady=6)

    right_v = tk.LabelFrame(tab_view, text=" Metin & Dil Görünümü ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    right_v.grid(row=0, column=1, sticky="nsew", padx=(8, 0), pady=4)

    for text, var in [
        ("Kelime Modunda Harekeleri Göster", app.tashkeel_var),
        ("Kelime Modunda Türkçe Anlamı Göster", app.word_show_tr),
        ("Diyalog Modunda Türkçe Anlamı Göster", app.chat_show_tr),
        ("Cümle Modunda Türkçe Anlamı Göster", app.sent_show_tr),
    ]:
        tk.Checkbutton(
            right_v, text=text, variable=var,
            bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
            activebackground=c["CARD_COLOR"], font=FONT_UI
        ).pack(anchor="w", pady=3)

    # 5. Sekme: 🛠️ Depolama & Bakım
    tab_adv = make_tab_frame()
    nb.add(tab_adv, text="🛠️ Depolama & Bakım")

    left_adv = tk.LabelFrame(tab_adv, text=" 🧹 Ses Kayıtları (recordings/) Yönetimi ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    left_adv.grid(row=0, column=0, sticky="nsew", padx=(0, 8), pady=4)

    tk.Checkbutton(
        left_adv, text="Eski Kayıtları Otomatik Sil (Son 10 Kaydı Tut)",
        variable=app.auto_clean_recordings_var,
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], selectcolor=c["PANEL_COLOR"],
        activebackground=c["CARD_COLOR"], font=FONT_UI
    ).pack(anchor="w", pady=6)

    cnt_f, mb_f = get_recordings_stats()
    btn_clean_now = tk.Button(
        left_adv, text=f"🗑️ Kayıtları Şimdi Temizle ({cnt_f} dosya, {mb_f} MB)",
        bg=RED, fg="white", font=FONT_UI_B, relief="flat", padx=10, pady=6, cursor="hand2"
    )
    btn_clean_now.pack(anchor="w", pady=6)

    def _clean_act():
        deleted = cleanup_recordings_folder(keep_latest=0)
        app._update_recordings_btn_label()
        btn_clean_now.config(text="🗑️ Kayıt Klasörü Temizlendi (0 MB)")
        app._show_toast(f"{deleted} ses kaydı silindi.", GREEN)

    btn_clean_now.config(command=_clean_act)

    right_adv = tk.LabelFrame(tab_adv, text=" 🪄 Veritabanı Bakımı ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=14, pady=10)
    right_adv.grid(row=0, column=1, sticky="nsew", padx=(8, 0), pady=4)

    tk.Label(right_adv, text="Arapça metinlerdeki parantez içi rakamları ve\nTürkçe sonundaki sayfa numaralarını onarır:", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI, justify="left").pack(anchor="w", pady=4)
    tk.Button(
        right_adv, text="🪄 Metin Doktorunu Çalıştır",
        command=lambda: messagebox.showinfo("Metin Doktoru", f"{repair_all_databases()} kayıt onarıldı.", parent=win),
        bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=6, cursor="hand2"
    ).pack(anchor="w", pady=6)

    # 6. Sekme: 🔄 Güncelleme & GitHub
    tab_upd = make_tab_frame()
    nb.add(tab_upd, text="🔄 Güncelleme")

    upd_box = tk.LabelFrame(
        tab_upd, text=" 🌐 GitHub Otomatik Güncelleme ve Sürüm Kontrolü ",
        bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B, padx=16, pady=14
    )
    upd_box.grid(row=0, column=0, columnspan=2, sticky="nsew", pady=4)

    tk.Label(
        upd_box,
        text=f"Kurulu Sürüm: v{APP_VERSION}   |   Resmî GitHub Deposu: {GITHUB_REPO_URL}",
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B
    ).pack(anchor="w", pady=(0, 8))

    tk.Label(
        upd_box,
        text="Uygulama her açılışta GitHub deposunu arka planda kontrol eder ve yeni bir sürüm çıktığında üst çubukta bildirim gösterir.\n"
             "Aşağıdaki butonla istediğiniz zaman manuel güncelleme kontrolü yapabilirsiniz:",
        bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI, justify="left"
    ).pack(anchor="w", pady=(0, 10))

    upd_btns = tk.Frame(upd_box, bg=c["CARD_COLOR"])
    upd_btns.pack(anchor="w")

    tk.Button(
        upd_btns, text="🔄 Şimdi Güncellemeleri Denetle / Güncelleme Merkezini Aç",
        command=lambda: (win.destroy(), open_updater_window(app)),
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=14, pady=7, cursor="hand2"
    ).pack(side="left", padx=(0, 8))

    tk.Button(
        upd_btns, text="🌐 GitHub Sayfasını Ziyaret Et",
        command=lambda: webbrowser.open(GITHUB_REPO_URL),
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=7, cursor="hand2"
    ).pack(side="left")

    bottom_f = tk.Frame(win, bg=c["CARD_COLOR"], padx=18, pady=10)
    bottom_f.pack(fill="x", side="bottom")

    def save_and_close():
        clean_std = app.student_name_var.get().strip() or "Öğrenci"
        app.student_name_var.set(clean_std)
        set_config_val("student_name", clean_std)
        app._apply_edit_mode_ui()
        app._on_toggle_teacher_bar()
        win.destroy()
        app._refresh_current_mode_data()

    tk.Button(
        bottom_f, text="✅ AYARLARI UYGULA VE KAPAT", command=save_and_close,
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 11, "bold"), relief="flat", pady=9, cursor="hand2"
    ).pack(fill="x")


# ==============================================================================
# 5) ÖĞRENCİ & ÖĞRETMEN ANALİZ, ROZET, SES DİNLEME VE ÖDEV PAKETİ EKRANI
# ==============================================================================
def open_analytics_and_reports_window(app):
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("📊 Öğrenme İstatistikleri, Başarı Rozetleri ve Öğretmen Raporu")
    win.geometry("980x660")
    win.minsize(880, 580)
    win.configure(bg=c["CARD_COLOR"])

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    win.lift()
    win.focus_force()

    badge_data = get_streak_and_badges()
    rep_data = get_detailed_analytics_report()
    last_exam = get_config_val("last_listening_exam", "Henüz tamamlanan sınav yok")
    student_name = (get_config_val("student_name", "") or "Öğrenci").strip()

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=12)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text=f"👤 {student_name}   |   🏆 {badge_data['level_title']}",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")
    ).pack(side="left")
    tk.Label(
        hdr,
        text=f"🔥 Günlük Seri: {badge_data['streak']} Gün   |   🎯 Bugün: {badge_data['daily_count']}/{badge_data['daily_goal']}   |   ⭐ Toplam Puan: {badge_data['total_points']}",
        bg=c["PANEL_COLOR"], fg=GREEN if is_dark else "#047857", font=("Segoe UI", 11, "bold")
    ).pack(side="right")

    nb = ttk.Notebook(win)
    nb.pack(fill="both", expand=True, padx=18, pady=10)

    tab_mot = tk.Frame(nb, bg=c["CARD_COLOR"], padx=20, pady=16)
    nb.add(tab_mot, text="🏅 Rozetler & Kilometre Taşları")

    tk.Label(
        tab_mot, text=f"🎧 Son Dinle & Bul Sınav Sonucu:  {last_exam}",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 10, "bold"), padx=12, pady=6
    ).pack(fill="x", pady=(0, 10))

    tk.Label(tab_mot, text="Kazanılan Başarı Rozetleri ve Öğrenme Kilometre Taşları:", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold")).pack(anchor="w", pady=(0, 8))

    grid_b = tk.Frame(tab_mot, bg=c["CARD_COLOR"])
    grid_b.pack(fill="both", expand=True)
    grid_b.grid_columnconfigure(0, weight=1)
    grid_b.grid_columnconfigure(1, weight=1)

    for idx, (b_title, b_desc, unlocked) in enumerate(badge_data["badges"]):
        r_i, c_i = divmod(idx, 2)
        bg_b = c["PANEL_COLOR"] if unlocked else c["BG_COLOR"]
        border_b = GREEN if unlocked else c["BORDER"]
        card_b = tk.Frame(grid_b, bg=bg_b, padx=14, pady=12, highlightthickness=2 if unlocked else 1, highlightbackground=border_b)
        card_b.grid(row=r_i, column=c_i, sticky="nsew", padx=6, pady=6)

        status_txt = "✅ KAZANILDI" if unlocked else "🔒 KİLİTLİ"
        status_col = (GREEN if is_dark else "#047857") if unlocked else c["TEXT_DIM"]
        tk.Label(card_b, text=f"{b_title}   [{status_txt}]", bg=bg_b, fg=status_col, font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(card_b, text=b_desc, bg=bg_b, fg=c["TEXT_MAIN"] if unlocked else c["TEXT_DIM"], font=FONT_UI).pack(anchor="w", pady=(4, 0))

    tab_pkg = tk.Frame(nb, bg=c["CARD_COLOR"], padx=16, pady=12)
    nb.add(tab_pkg, text="📈 Paket & Diyalog Başarı Grafikleri")

    canvas_p = tk.Canvas(tab_pkg, bg=c["BG_COLOR"], highlightthickness=0)
    sb_p = ttk.Scrollbar(tab_pkg, orient="vertical", command=canvas_p.yview)
    inner_p = tk.Frame(canvas_p, bg=c["BG_COLOR"], padx=12, pady=10)
    inner_p.bind("<Configure>", lambda e: canvas_p.configure(scrollregion=canvas_p.bbox("all")))
    cw_p = canvas_p.create_window((0, 0), window=inner_p, anchor="nw")
    canvas_p.bind("<Configure>", lambda e: canvas_p.itemconfig(cw_p, width=e.width))
    canvas_p.pack(side="left", fill="both", expand=True)
    sb_p.pack(side="right", fill="y")
    canvas_p.configure(yscrollcommand=sb_p.set)

    def draw_section_bars(sec_title, rows_list):
        tk.Label(inner_p, text=sec_title, bg=c["BG_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 11, "bold")).pack(anchor="w", pady=(10, 4))
        for name, tot, lrn, corr, wrg, acc, comp in rows_list:
            row_f = tk.Frame(inner_p, bg=c["PANEL_COLOR"], padx=10, pady=6)
            row_f.pack(fill="x", pady=3)
            tk.Label(row_f, text=f"{name} ({lrn}/{tot} tamamlandı)", bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, width=38, anchor="w").pack(side="left")

            bar_c = tk.Canvas(row_f, width=220, height=16, bg=c["BG_COLOR"], highlightthickness=1, highlightbackground=c["BORDER"])
            bar_c.pack(side="left", padx=10)
            fill_w = int(2.2 * comp)
            if fill_w > 0:
                bar_c.create_rectangle(0, 0, fill_w, 16, fill=GREEN if comp >= 70 else BLUE, outline="")

            tk.Label(
                row_f, text=f"Tamamlama: %{comp}  |  Başarı: %{acc} (D:{corr} / Y:{wrg})",
                bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 9, "bold")
            ).pack(side="right")

    draw_section_bars("🏠 KELİME & SARF SÖZLÜĞÜ PAKETLERİ İLERLEME ORANI", rep_data["word_packages"])
    draw_section_bars("📜 CÜMLE PAKETLERİ İLERLEME VE BAŞARI ORANI", rep_data["sentence_packages"])
    draw_section_bars("💬 DİYALOG BAZLI BAŞARI ANALİZİ", rep_data["dialogues"][:25])
    app._bind_mousewheel(canvas_p, inner_p)

    tab_err = tk.Frame(nb, bg=c["CARD_COLOR"], padx=16, pady=10)
    nb.add(tab_err, text="⚠️ En Çok Hata Yapılanlar & Ses Dinleme")

    err_top_bar = tk.Frame(tab_err, bg=c["PANEL_COLOR"], padx=12, pady=6)
    err_top_bar.pack(fill="x", pady=(0, 6))
    tk.Label(
        err_top_bar,
        text="💡 Listeden bir satır seçip öğrencinin son telaffuz kaydını veya doğru referans sesi dinleyebilirsiniz:",
        bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold")
    ).pack(side="left")

    def listen_selected_student_wav():
        sel = tree_e.selection()
        if not sel:
            messagebox.showinfo("Seçim Yok", "Lütfen önce tablodan bir kayıt seçin.", parent=win)
            return
        vals = tree_e.item(sel[0], "values")
        iid = str(vals[1])
        wav_p = find_latest_student_recording_for_id(iid)
        if wav_p and os.path.exists(wav_p):
            app._play_audio_file_async(wav_p)
        else:
            messagebox.showinfo(
                "Ses Kaydı Bulunamadı",
                f"'{vals[2]}' ({iid}) kaydı için recordings/ klasöründe kayıtlı bir öğrenci ses dosyası (.wav) bulunamadı.",
                parent=win
            )

    def listen_selected_reference_tts():
        sel = tree_e.selection()
        if not sel:
            messagebox.showinfo("Seçim Yok", "Lütfen önce tablodan bir kayıt seçin.", parent=win)
            return
        vals = tree_e.item(sel[0], "values")
        ar_txt = str(vals[3])
        if ar_txt:
            app._play_tts_text_async(ar_txt, "ar-SA-HamedNeural")

    tk.Button(
        err_top_bar, text="🔊 Doğru Telaffuzu Dinle", command=listen_selected_reference_tts,
        bg=ACCENT, fg="white", font=("Segoe UI", 9, "bold"), relief="flat", padx=10, pady=3, cursor="hand2"
    ).pack(side="right", padx=(4, 0))

    tk.Button(
        err_top_bar, text="🎧 Öğrencinin Ses Kaydını Dinle (.wav)", command=listen_selected_student_wav,
        bg=BLUE, fg="white", font=("Segoe UI", 9, "bold"), relief="flat", padx=10, pady=3, cursor="hand2"
    ).pack(side="right", padx=4)

    tree_holder = tk.Frame(tab_err, bg=c["CARD_COLOR"])
    tree_holder.pack(fill="both", expand=True)

    cols_e = ("tur", "id", "tr", "ar", "dogru", "yanlis")
    tree_e = ttk.Treeview(tree_holder, columns=cols_e, show="headings", height=13)
    for cid, h_txt, w_px, anc in [
        ("tur", "Tür", 110, "w"),
        ("id", "ID", 70, "center"),
        ("tr", "Türkçe Anlamı", 280, "w"),
        ("ar", "Arapça Metin", 300, "e"),
        ("dogru", "Doğru", 65, "center"),
        ("yanlis", "Yanlış", 65, "center")
    ]:
        tree_e.heading(cid, text=h_txt)
        tree_e.column(cid, width=w_px, anchor=anc)

    sb_e = ttk.Scrollbar(tree_holder, orient="vertical", command=tree_e.yview)
    tree_e.configure(yscrollcommand=sb_e.set)
    tree_e.pack(side="left", fill="both", expand=True)
    sb_e.pack(side="right", fill="y")

    for row_item in rep_data["most_difficult"]:
        tree_e.insert("", tk.END, values=row_item)

    bot_bar = tk.Frame(win, bg=c["CARD_COLOR"], padx=18, pady=10)
    bot_bar.pack(fill="x", side="bottom")

    def export_csv_btn():
        p = filedialog.asksaveasfilename(
            title="Başarı Raporunu Kaydet (Excel / CSV)",
            defaultextension=".csv",
            filetypes=[("CSV / Excel Raporu", "*.csv")],
            initialfile="ogrenci_basari_raporu.csv",
            parent=win
        )
        if not p:
            return
        try:
            export_analytics_to_csv(p)
            messagebox.showinfo("Rapor Hazır", f"Başarı ve analiz raporu Excel uyumlu CSV olarak kaydedildi:\n{p}", parent=win)
        except Exception as ex:
            messagebox.showerror("Hata", str(ex), parent=win)

    tk.Button(
        bot_bar, text="📥 Raporu CSV / Excel Aktar", command=export_csv_btn,
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2"
    ).pack(side="left", padx=(0, 6))

    tk.Button(
        bot_bar, text="📦 Ödev & Ses Paketi Oluştur (.ZIP)",
        command=lambda: export_student_homework_zip(win, app),
        bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2"
    ).pack(side="left", padx=6)

    tk.Button(
        bot_bar, text="🖨️ Yazılı Sınav & Ders Notu Üret",
        command=lambda: open_printable_worksheet_generator(win, app, app.current_mode if app.current_mode != "home" else "words"),
        bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2"
    ).pack(side="left", padx=6)

    tk.Button(
        bot_bar, text="Kapat", command=win.destroy,
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=22, pady=8, cursor="hand2"
    ).pack(side="right")


def open_roleplay_report_card(app, role_name: str, score_pct: int, final_results_dict: dict, lines: list, next_role: str = None):
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("📊 Diyalog Canlandırma ve Konuşma Karnesi")
    win.geometry("780x580")
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], pady=12, padx=20)
    hdr.pack(fill="x")
    badge_col = GREEN if score_pct >= 75 else (YELLOW if score_pct >= 50 else RED)
    tk.Label(
        hdr, text=f"📊 KONUŞMA KARNESİ — Rol: {role_name}",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 14, "bold")
    ).pack(side="left")
    tk.Label(
        hdr, text=f"Genel Başarı: %{score_pct}",
        bg=c["PANEL_COLOR"], fg=badge_col if is_dark else "#047857", font=("Segoe UI", 14, "bold")
    ).pack(side="right")

    list_box = tk.Frame(win, bg=c["BG_COLOR"], highlightthickness=1, highlightbackground=c["BORDER"])
    list_box.pack(fill="both", expand=True, padx=20, pady=12)

    canvas = tk.Canvas(list_box, bg=c["BG_COLOR"], highlightthickness=0)
    sb = ttk.Scrollbar(list_box, orient="vertical", command=canvas.yview)
    inner = tk.Frame(canvas, bg=c["BG_COLOR"])
    inner.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
    c_win = canvas.create_window((0, 0), window=inner, anchor="nw")
    canvas.bind("<Configure>", lambda e: canvas.itemconfig(c_win, width=e.width))
    canvas.pack(side="left", fill="both", expand=True, padx=6, pady=6)
    sb.pack(side="right", fill="y")
    canvas.configure(yscrollcommand=sb.set)

    ok_fg = GREEN if is_dark else "#047857"
    for line_idx, word_list in final_results_dict.items():
        if line_idx >= len(lines):
            continue
        _, sira, spk, _, gender, text_ar, text_tr, ses_p = lines[line_idx]
        row_card = tk.Frame(inner, bg=c["PANEL_COLOR"], padx=12, pady=8, highlightthickness=1, highlightbackground=c["BORDER"])
        row_card.pack(fill="x", pady=4, padx=4)

        top_r = tk.Frame(row_card, bg=c["PANEL_COLOR"])
        top_r.pack(fill="x")
        tk.Button(
            top_r, text="🔊 Doğrusunu Dinle",
            command=lambda t=text_ar, s=spk, g=gender, sp=ses_p: app._play_tts_text_async(t, app._detect_voice(s, g), sp),
            bg=ACCENT, fg="white", font=("Segoe UI", 8, "bold"), relief="flat", padx=8, pady=2, cursor="hand2"
        ).pack(side="left")
        tk.Label(top_r, text=f"{sira}. Cümle ({text_tr})", bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 9, "bold")).pack(side="right")

        w_frame = tk.Frame(row_card, bg=c["PANEL_COLOR"])
        w_frame.pack(anchor="e", fill="x", pady=(4, 0))
        for w_str, is_ok in word_list:
            tk.Label(
                w_frame, text=f"\u200f{w_str}\u200f", bg=c["PANEL_COLOR"],
                fg=ok_fg if is_ok else RED, font=(AR_FONT_FAMILY, 15, "bold"), padx=2
            ).pack(side="right")

    app._bind_mousewheel(canvas, inner)

    btn_bar = tk.Frame(win, bg=c["CARD_COLOR"])
    btn_bar.pack(fill="x", padx=20, pady=(0, 15))

    tk.Button(
        btn_bar, text="📦 Ödev & Ses Paketi Oluştur (.ZIP)",
        command=lambda: export_student_homework_zip(win, app),
        bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=9, cursor="hand2"
    ).pack(side="left", padx=(0, 6))

    if next_role:
        def switch_and_restart():
            win.destroy()
            app.dlg_role_var.set(next_role)
            app._start_roleplay_session()

        tk.Button(
            btn_bar, text=f"🔄 '{next_role}' Rolüne Geçip Tekrar Canlandır",
            command=switch_and_restart, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", pady=9, cursor="hand2"
        ).pack(side="left", fill="x", expand=True, padx=(0, 6))

    tk.Button(
        btn_bar, text="✅ Kapat", command=win.destroy,
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=18, pady=9, cursor="hand2"
    ).pack(side="left", fill="x", expand=True)


# ==============================================================================
# 5.5) 📖 ZENGİNLEŞTİRİLMİŞ KULLANIM KILAVUZU VE UYGULAMA REHBERİ (8 BÖLÜM)
# ==============================================================================
def open_user_guide_window(app):
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("📖 Kullanım Kılavuzu ve Kapsamlı Uygulama Rehberi")
    win.geometry("920x680")
    win.minsize(820, 580)
    win.configure(bg=c["CARD_COLOR"])

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    win.lift()
    win.focus_force()

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], pady=10, padx=20)
    hdr.pack(fill="x")

    tk.Button(
        hdr, text="👋 7 Adımlı Başlangıç Turunu Başlat",
        command=lambda: (win.destroy(), open_onboarding_tour(app, force_show=True)),
        bg=BLUE, fg="white", font=("Segoe UI", 9, "bold"), relief="flat", padx=12, pady=5, cursor="hand2"
    ).pack(side="right", padx=(10, 0))

    tk.Label(
        hdr, text="📖 ARAPÇA TELAFFUZ VE KONUŞMA ASİSTANI — KAPSAMLI KULLANIM REHBERİ",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold"), anchor="w"
    ).pack(side="left", fill="x", expand=True)

    body_frame = tk.Frame(win, bg=c["BG_COLOR"], padx=2, pady=2, highlightthickness=1, highlightbackground=c["BORDER"])
    body_frame.pack(fill="both", expand=True, padx=20, pady=12)

    txt = tk.Text(
        body_frame, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 10),
        wrap="word", padx=20, pady=15, bd=0, highlightthickness=0
    )
    sb = ttk.Scrollbar(body_frame, orient="vertical", command=txt.yview)
    txt.configure(yscrollcommand=sb.set)
    txt.pack(side="left", fill="both", expand=True)
    sb.pack(side="right", fill="y")

    h1_fg = "#ffd369" if is_dark else "#1e3a8a"
    h2_fg = "#00e676" if is_dark else "#047857"
    hl_fg = "#60a5fa" if is_dark else "#1d4ed8"
    box_bg = "#1e293b" if is_dark else "#e2e8f0"

    txt.tag_configure("h1", foreground=h1_fg, font=("Segoe UI", 13, "bold"), spacing1=14, spacing3=6)
    txt.tag_configure("h2", foreground=h2_fg, font=("Segoe UI", 11, "bold"), spacing1=8, spacing3=3)
    txt.tag_configure("body", foreground=c["TEXT_MAIN"], font=("Segoe UI", 10), spacing2=4)
    txt.tag_configure("bullet", foreground=c["TEXT_MAIN"], font=("Segoe UI", 10), lmargin1=18, lmargin2=32, spacing2=4)
    txt.tag_configure("tip", foreground=hl_fg, background=box_bg, font=("Segoe UI", 10, "bold"), lmargin1=12, lmargin2=12, spacing1=6, spacing3=6)

    content_blocks = [
        ("🎯 1. UYGULAMANIN AMACI VE EĞİTİM FELSEFESİ\n", "h1"),
        ("Muallimun.Net Arapça Telaffuz ve Konuşma Asistanı; İmam Hatip Liseleri, İlahiyat Fakülteleri, TÖMER ve bireysel Arapça öğrenenler için geliştirilmiş yapay zeka destekli bir konuşma (المحادثة), sarf (kök/türev) ve doğru telaffuz (النطق الصحيح) laboratuvarıdır.\n", "body"),

        ("🏠 2. KELİME MODU, GÖLGELEME (SHADOWING) VE SM-2 AKILLI TEKRAR\n", "h1"),
        ("• Görselli Kelime Kartları ve Yeşil Mikrofon Çerçevesi: ", "h2"),
        ("'🎤 BAŞLA VE KONUŞ' butonuna (veya Boşluk tuşuna) bastığınızda bip sesinden sonra kartın dış çerçevesi parlak yeşil renkte parlar. Bu esnada kelimeyi okuduğunuzda Whisper yapay zeka motoru sesinizi analiz eder.\n", "bullet"),
        ("• 🗣️ Gölgeleme (Shadowing) Antrenmanı: ", "h2"),
        ("Referans ses dalga boyunu (mavi) ve kendi ses dalga boyunuzu (yeşil) alt alta görerek hem telaffuz doğruluğunuzu hem de konuşma ritminizi (tempo uyumu) ölçebilirsiniz.\n", "bullet"),
        ("• 📅 Günün 15 Tekrarı (SM-2 Algoritması): ", "h2"),
        ("Bilimsel aralıklı tekrar (Spaced Repetition) algoritması, zorlandığınız veya tekrar zamanı gelen 15 kartı her gün otomatik olarak seçer.\n", "bullet"),

        ("🌿 3. SARF SÖZLÜĞÜ, ALTIGEN KÖK ROZETİ VE SÜTUN SIRALAMA\n", "h1"),
        ("• Altıgen Sülâsî Kök Rozeti ve Türev Ağacı: ", "h2"),
        ("Sözlük paketlerinde kelimenin 3 asli kök harfi altıgen rozet içinde gösterilir. İsimlerde 'Müfret (Tekil) & Cemî (Çoğul)', fiillerde ise 'Mâzî • Muzâri • Masdar' formlarına tıklayarak her çekimi ayrı ayrı dinleyip telaffuz edebilirsiniz.\n", "bullet"),
        ("• Sütun Başlığına Tıklayarak Sıralama: ", "h2"),
        ("'📖 Sarf Sözlüğü' penceresinde Kök, Tür, Arapça veya Türkçe sütun başlıklarına tıklayarak listeyi A➔Z (▲) veya Z➔A (▼) sıralayabilirsiniz.\n", "bullet"),

        ("💬 4. GÜNLÜK DİYALOGLAR (MUHÂDESÂT) VE ROL YAPMA (ROLEPLAY)\n", "h1"),
        ("• 📖 Öğrenme Modu ve 🎭 Canlandırma Modu: ", "h2"),
        ("Öğrenme modunda diyalogları dinleyebilir; Canlandırma (Roleplay) modunda ise bir karakteri seçerek uygulama ile karşılıklı konuşabilirsiniz. Canlandırma modunda kendi cümlenizin Türkçe çevirisi otomatik gizlenir; ihtiyaç duyarsanız '💡 Türkçe İpucu Göster' butonuna basabilirsiniz.\n", "bullet"),

        ("🎯 5. CANLI MAHREÇ KOÇU VE KIRMIZI HARF VURGUSU\n", "h1"),
        ("• Harf ve Kök Koruma Motoru: ", "h2"),
        ("Sağ panelin en üstündeki Canlı Mahreç Koçu; birbirine yakın mahreçli harfleri (ص↔س, ط↔ت, ض↔د, ع↔ء, ح↔ه, ق↔ك, ذ↔ز, ث↔س, ن↔ر vb.), kök harf değişimlerini (نَحْلَة vs رحلة) ve vezin/med farklarını (كِتَاب vs كتب) yakalar ve hatalı kelimeyi kırmızıyla vurgular.\n", "bullet"),

        ("🖨️ 6. YAZILI SINAV, KARMA QUİZ VE 'DERS NOTU / OKUMA METNİ' PDF ÜRETİCİ\n", "h1"),
        ("• Ayrık Havuzlu Yazılı Sınav ve Karma Kaynak: ", "h2"),
        ("Arapça➔Türkçe, Türkçe➔Arapça, Boşluk Doldurma, 2x2 Hizalı Çoktan Seçmeli Test, Eşleştirme ve Sarf Tablosu soru sayılarını (0-50) ayrı ayrı belirleyebilirsiniz. '🌟 Karma Sınav' seçeneğiyle Kelime, Diyalog ve Cümlelerden aynı kağıtta sınav üretebilirsiniz.\n", "bullet"),
        ("• 📖 Ders Notu / Okuma Metni (Handout) Formatı: ", "h2"),
        ("Diyalog veya Cümle kaynaklarında 'Ders Notu / Okuma Metni' seçeneğini işaretleyerek sınav soruları olmadan; Yan Yana (İki Sütun), Alt Alta, Sonda Toplu Çevirili veya Sadece Arapça (Harekeli/Harekesiz) A4 okuma yaprakları hazırlayabilirsiniz.\n", "bullet"),

        ("🛠️ 7. YÖNETİM PANELİ, EXCEL YAPIŞTIRMA, TOPLU MEDYA VE SES DİNLEME\n", "h1"),
        ("• 📋 Excel / Panodan Yapıştır ve Detaylı Yükleme Raporu: ", "h2"),
        ("Excel'deki kelime veya cümlelerinizi doğrudan kopyalayıp Yönetim Paneline yapıştırabilirsiniz. Eksik/hatalı satırlar satır numarasıyla raporlanır.\n", "bullet"),
        ("• 🖼️ Toplu Medya Klasörü Eşleştirme ve 🎧 Öğrenci Sesini Dinleme: ", "h2"),
        ("Bilgisayarınızdaki bir klasörü seçerek görsel ve sesleri ID veya Türkçe ada göre otomatik eşleştirebilir; Raporlar ekranından öğrencinin hatalı okuduğu kelimelerdeki gerçek ses kaydını (.wav) dinleyebilirsiniz.\n", "bullet"),

        ("🔄 8. GITHUB OTOMATİK GÜNCELLEME VE KLAVYE KISAYOLLARI\n", "h1"),
        ("• Otomatik Sürüm Kontrolü: ", "h2"),
        (f"Uygulama her açılışta '{GITHUB_REPO_URL}' deposunu kontrol eder ve yeni sürüm çıktığında tek tıkla günceller.\n", "bullet"),
        ("⌨️ PRATİK KLAVYE KISAYOLLARI\n"
         "  • [Boşluk / Space] : Ses kaydını başlatır veya durdurur.\n"
         "  • [Sağ Ok ▶ / Sol Ok ◀] : Sonraki veya önceki kelime/cümle kartına geçer.\n"
         "  • [F1] : Bu Kullanım Kılavuzunu açar.\n", "tip")
    ]

    for text_str, tag in content_blocks:
        txt.insert(tk.END, text_str, tag)
    txt.config(state="disabled")

    tk.Button(
        win, text="Anladım, Kapat", command=win.destroy,
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", pady=8, cursor="hand2"
    ).pack(fill="x", padx=20, pady=(0, 15))


# ==============================================================================
# 6) KELİME (GÖRSEL SEÇME/SİLME DESTEKLİ) VE CÜMLE TEKİL DÜZENLEME PENCERELERİ
# ==============================================================================
def open_quick_edit_word(app):
    """
    Kelimeyi, CEFR seviyesini, referans sesini ve GÖRSELİNİ (seçme/ekleme/silme + canlı önizleme)
    düzenleyen hızlı düzenleme penceresi (Madde 1).
    """
    if not app.current_items:
        return
    item = app.current_items[app.current_index]
    saved_index = app.current_index
    c = app.colors
    mic_rec = AdminMicRecorder()

    win = tk.Toplevel(app.root)
    win.title("✏️ Kelimeyi, Görselini ve Seviyesini Düzenle / Sil")
    win.geometry("620x540")
    win.minsize(580, 500)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    top_r = tk.Frame(win, bg=c["CARD_COLOR"])
    top_r.pack(fill="x", padx=20, pady=(14, 4))
    tk.Label(top_r, text="🏷️ Kelime Seviyesi (CEFR):", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    cur_lvl = getattr(item, "seviye", "A1") or "A1"
    lvl_var = tk.StringVar(value=cur_lvl if cur_lvl in ("A1", "A2", "B1", "B2", "C1") else "A1")
    ttk.Combobox(top_r, textvariable=lvl_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=6, font=FONT_UI_B).pack(side="left", padx=8)

    lbl_preview = tk.Label(top_r, text="", bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 8, "bold"), padx=8, pady=2)
    lbl_preview.pack(side="right")
    preview_photo_ref = [None]

    tk.Label(win, text="Türkçe Karşılığı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(6, 0))
    tr_entry = tk.Entry(win, font=FONT_UI, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    tr_entry.insert(0, item.turkce)
    tr_entry.pack(fill="x", padx=20, pady=4, ipady=2)

    tk.Label(win, text="Arapça Kelime:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(6, 0))
    ar_entry = tk.Entry(win, font=(AR_FONT_FAMILY, 16, "bold"), bg=c["BG_COLOR"], fg=c["TEXT_AR"], justify="right", insertbackground=c["TEXT_MAIN"])
    ar_entry.insert(0, item.arapca)
    ar_entry.pack(fill="x", padx=20, pady=4, ipady=2)

    # 🖼️ Görsel Seçme / Ekleme / Silme Satırı
    tk.Label(win, text="🖼️ Kelime Görseli (Opsiyonel — Seçebilir veya Kaldırabilirsiniz):", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(6, 0))
    img_row = tk.Frame(win, bg=c["CARD_COLOR"])
    img_row.pack(fill="x", padx=20, pady=4)

    img_entry = tk.Entry(img_row, font=FONT_UI, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    img_entry.insert(0, getattr(item, "gorsel", "") or "")
    img_entry.pack(side="left", fill="x", expand=True, padx=(0, 6), ipady=2)

    def refresh_img_preview():
        g_val = img_entry.get().strip()
        full_g = resolve_local_media_path(g_val) if g_val else None
        if full_g and os.path.exists(full_g):
            try:
                im = Image.open(full_g)
                im.thumbnail((56, 42), Image.LANCZOS)
                preview_photo_ref[0] = ImageTk.PhotoImage(im)
                lbl_preview.config(image=preview_photo_ref[0], text="")
                return
            except Exception:
                pass
        preview_photo_ref[0] = None
        lbl_preview.config(image="", text="🖼️ Görsel Yok")

    def select_word_image():
        p = filedialog.askopenfilename(
            title="Kelime Görseli Seç (.png / .jpg / .webp)",
            filetypes=[("Görsel Dosyaları", "*.png *.jpg *.jpeg *.webp")],
            parent=win
        )
        if not p:
            return
        pkg_folder = item.paket_id if getattr(item, "paket_id", "") and not str(item.paket_id).startswith("szl_") else "genel"
        cat_img_dir = os.path.join(CATEGORIES_DIR, pkg_folder, "images")
        os.makedirs(cat_img_dir, exist_ok=True)
        ext = os.path.splitext(p)[1].lower()
        dst_name = f"{item.id}_{int(time.time())}{ext}"
        shutil.copy2(p, os.path.join(cat_img_dir, dst_name))
        rel_p = f"categories/{pkg_folder}/images/{dst_name}"
        img_entry.delete(0, tk.END)
        img_entry.insert(0, rel_p)
        refresh_img_preview()
        app._show_toast("🖼️ Görsel eklendi! Kaydetmek için '💾 GÜNCELLE'ye basın.", GREEN)

    def clear_word_image():
        img_entry.delete(0, tk.END)
        refresh_img_preview()

    tk.Button(img_row, text="📁 Görsel Seç", command=select_word_image, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=8, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(img_row, text="🧹 Görseli Sil", command=clear_word_image, bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=8, pady=2, cursor="hand2").pack(side="left", padx=2)

    refresh_img_preview()

    # 🎵 Referans Ses Satırı
    tk.Label(win, text="🎵 Referans Ses (Opsiyonel):", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(6, 0))
    aud_row = tk.Frame(win, bg=c["CARD_COLOR"])
    aud_row.pack(fill="x", padx=20, pady=4)
    ses_entry = tk.Entry(aud_row, font=FONT_UI, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    ses_entry.insert(0, getattr(item, "ses", "") or "")
    ses_entry.pack(side="left", fill="x", expand=True, padx=(0, 6), ipady=2)

    def toggle_mic_w():
        if not mic_rec.recording:
            try:
                app._stop_all_audio()
                mic_rec.start()
                btn_m.config(text="⏹️ Bitir", bg=RED)
            except Exception as ex:
                messagebox.showerror("Hata", str(ex), parent=win)
        else:
            btn_m.config(text="🎙️ Mikrofondan Kaydet", bg="#0d9488")
            try:
                rel = mic_rec.stop_and_save(f"word_{item.id}")
                ses_entry.delete(0, tk.END)
                ses_entry.insert(0, rel)
                app._show_toast("Ses kaydedildi!", GREEN)
            except Exception as ex:
                messagebox.showwarning("Uyarı", str(ex), parent=win)

    def browse_word_audio():
        p = filedialog.askopenfilename(title="Ses Dosyası Seç (.mp3 / .wav)", filetypes=[("Ses", "*.mp3 *.wav")], parent=win)
        if not p:
            return
        ext = os.path.splitext(p)[1].lower()
        dst_name = f"word_{item.id}_{int(time.time())}{ext}"
        shutil.copy2(p, os.path.join(CUSTOM_AUDIO_DIR, dst_name))
        ses_entry.delete(0, tk.END)
        ses_entry.insert(0, f"custom_audio/{dst_name}")

    btn_m = tk.Button(aud_row, text="🎙️ Mikrofondan Kaydet", command=toggle_mic_w, bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=8, pady=2, cursor="hand2")
    btn_m.pack(side="left", padx=2)
    tk.Button(aud_row, text="📁 Ses Seç", command=browse_word_audio, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=8, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(aud_row, text="🧹", command=lambda: ses_entry.delete(0, tk.END), bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=6, pady=2, cursor="hand2").pack(side="left", padx=2)

    def save():
        tr = sanitize_turkish_sentence(tr_entry.get())
        ar = sanitize_arabic_sentence(ar_entry.get())
        gorsel_v = img_entry.get().strip() or None
        ses_v = ses_entry.get().strip() or None
        new_lvl = lvl_var.get().strip().upper()
        if tr and ar:
            with db_session(WORDS_DB_PATH) as conn:
                conn.execute(
                    "UPDATE kelimeler SET turkce=?, arapca=?, gorsel=?, ses=?, seviye=? WHERE id=?",
                    (tr, ar, gorsel_v, ses_v, new_lvl, item.id)
                )
            app._load_category_items(play_audio=False)
            app.current_index = min(saved_index, max(0, len(app.current_items) - 1))
            app._show_card(play_audio=False)
            app._show_toast(f"✅ Kelime ({new_lvl}) ve görseli güncellendi.", GREEN)
            win.destroy()

    def delete():
        if messagebox.askyesno("Emin misiniz?", f"'{item.turkce}' kelimesini silmek istediğinize emin misiniz?", parent=win):
            delete_word_db(item.id)
            app._load_category_items(play_audio=False)
            app.current_index = min(saved_index, max(0, len(app.current_items) - 1))
            app._show_card(play_audio=False)
            app._show_toast("Kelime silindi.", RED)
            win.destroy()

    tk.Button(win, text="💾 GÜNCELLE VE KAYDET", command=save, bg=GREEN, fg="#0f172a", font=("Segoe UI", 12, "bold"), relief="flat", pady=8, cursor="hand2").pack(fill="x", padx=20, pady=(14, 5))
    tk.Button(win, text="🗑️ KELİMEYİ SİL", command=delete, bg=RED, fg="white", font=("Segoe UI", 11, "bold"), relief="flat", pady=7, cursor="hand2").pack(fill="x", padx=20)


def open_quick_edit_sentence(app):
    if not app.current_items:
        return
    item = app.current_items[app.current_index]
    saved_index = app.current_index
    c = app.colors
    mic_rec = AdminMicRecorder()

    win = tk.Toplevel(app.root)
    win.title("✏️ Cümleyi ve Seviyesini Düzenle / Sil")
    win.geometry("600x420")
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    top_r = tk.Frame(win, bg=c["CARD_COLOR"])
    top_r.pack(fill="x", padx=20, pady=(15, 4))
    tk.Label(top_r, text="🏷️ Cümle Seviyesi (CEFR):", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    cur_lvl = getattr(item, "seviye", "A1") or "A1"
    lvl_var = tk.StringVar(value=cur_lvl if cur_lvl in ("A1", "A2", "B1", "B2", "C1") else "A1")
    ttk.Combobox(top_r, textvariable=lvl_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=6, font=FONT_UI_B).pack(side="left", padx=8)

    tk.Label(win, text="Arapça Cümle:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(8, 0))
    ar_entry = tk.Entry(win, font=(AR_FONT_FAMILY, 16, "bold"), bg=c["BG_COLOR"], fg=c["TEXT_AR"], justify="right", insertbackground=c["TEXT_MAIN"])
    ar_entry.insert(0, item.arapca)
    ar_entry.pack(fill="x", padx=20, pady=5)

    tk.Label(win, text="Türkçe Tercümesi:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(8, 0))
    tr_entry = tk.Entry(win, font=FONT_UI, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    tr_entry.insert(0, item.turkce)
    tr_entry.pack(fill="x", padx=20, pady=5)

    tk.Label(win, text="🎵 Referans Ses (Opsiyonel):", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B).pack(anchor="w", padx=20, pady=(8, 0))
    aud_row = tk.Frame(win, bg=c["CARD_COLOR"])
    aud_row.pack(fill="x", padx=20, pady=4)
    ses_entry = tk.Entry(aud_row, font=FONT_UI, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], insertbackground=c["TEXT_MAIN"])
    ses_entry.insert(0, getattr(item, "ses", "") or "")
    ses_entry.pack(side="left", fill="x", expand=True, padx=(0, 6))

    def toggle_mic_s():
        if not mic_rec.recording:
            try:
                app._stop_all_audio()
                mic_rec.start()
                btn_m.config(text="⏹️ Bitir", bg=RED)
            except Exception as ex:
                messagebox.showerror("Hata", str(ex), parent=win)
        else:
            btn_m.config(text="🎙️ Mikrofondan Kaydet", bg="#0d9488")
            try:
                rel = mic_rec.stop_and_save(f"sent_{item.id}")
                ses_entry.delete(0, tk.END)
                ses_entry.insert(0, rel)
                app._show_toast("Ses kaydedildi!", GREEN)
            except Exception as ex:
                messagebox.showwarning("Uyarı", str(ex), parent=win)

    btn_m = tk.Button(aud_row, text="🎙️ Mikrofondan Kaydet", command=toggle_mic_s, bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=8, pady=2, cursor="hand2")
    btn_m.pack(side="left", padx=2)

    def save():
        ar, tr = sanitize_arabic_sentence(ar_entry.get()), sanitize_turkish_sentence(tr_entry.get())
        ses_v = ses_entry.get().strip() or None
        new_lvl = lvl_var.get().strip().upper()
        if ar:
            with db_session(SENTENCES_DB_PATH) as conn:
                conn.execute("UPDATE cumleler SET arapca=?, turkce=?, ses=?, seviye=? WHERE id=?", (ar, tr, ses_v, new_lvl, item.id))
            item.arapca = ar
            item.turkce = tr
            item.ses = ses_v
            item.seviye = new_lvl
            app.current_index = saved_index
            app._show_sentence_card()
            app._show_toast(f"✅ Cümle ({new_lvl}) güncellendi.", GREEN)
            win.destroy()

    def delete():
        if messagebox.askyesno("Emin misiniz?", "Bu cümleyi silmek istediğinize emin misiniz?", parent=win):
            with db_session(SENTENCES_DB_PATH) as conn:
                conn.execute("DELETE FROM cumleler WHERE id=?", (item.id,))
            del app.current_items[saved_index]
            if app.current_index >= len(app.current_items):
                app.current_index = max(0, len(app.current_items) - 1)
            app._save_last_index()
            app._show_sentence_card()
            app._show_toast("Cümle silindi.", RED)
            win.destroy()

    tk.Button(win, text="💾 GÜNCELLE", command=save, bg=GREEN, fg="#0f172a", font=("Segoe UI", 12, "bold"), relief="flat", pady=8, cursor="hand2").pack(fill="x", padx=20, pady=(16, 5))
    tk.Button(win, text="🗑️ SİL", command=delete, bg=RED, fg="white", font=("Segoe UI", 12, "bold"), relief="flat", pady=8, cursor="hand2").pack(fill="x", padx=20)
    
# ==============================================================================
# 7) ℹ️ UYGULAMA HAKKINDA, KILAVUZLAR (PDF / WEB / YOUTUBE) VE SİSTEM BİLGİSİ
# ==============================================================================
def open_about_info_window(app):
    """
    Global mağaza uygulamaları tarzında 'Uygulama Hakkında ve Kılavuz Merkezi' penceresi.
    - Üstte: Uygulama ikonu (app_icon.png), sürüm, mimari ve '%100 Özelleştirilebilir İçerik Motoru' bilgisi.
    - Ortada: 1) YouTube Tanıtım Videosu, 2) Web Tanıtım & Kılavuz Sayfası, 3) PDF Kullanım Kılavuzu kartları.
    - Altta: Küçük ve şık Muallimun.Net logosu ve web bağlantısı.
    """
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    WEB_GUIDE_URL = "https://arapca.muallimun.net/arapca-telaffuz-asistani/"
    PDF_GUIDE_URL = "https://arapca.muallimun.net/arapca-telaffuz-asistani/Arapca_Telaffuz_Asistani_Kullanim_Kilavuzu.pdf"
    YOUTUBE_GUIDE_URL = "https://www.youtube.com/watch?v=vqttBAoov00"
    MUALLIMUN_MAIN_URL = "https://www.muallimun.com"

    win = tk.Toplevel(app.root)
    win.title(f"ℹ️ Uygulama Hakkında ve Kılavuz Merkezi — v{APP_VERSION}")
    w_px, h_px = 680, 590
    sw = win.winfo_screenwidth()
    sh = win.winfo_screenheight()
    pos_x = max(0, (sw - w_px) // 2)
    pos_y = max(0, (sh - h_px) // 2 - 20)
    win.geometry(f"{w_px}x{h_px}+{pos_x}+{pos_y}")
    win.minsize(640, 560)
    win.resizable(False, False)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    # 1. ÜST BÖLÜM: GLOBAL STORE TARZI UYGULAMA KİMLİK KARTI
    hero = tk.Frame(win, bg=c["PANEL_COLOR"], padx=22, pady=16, highlightthickness=1, highlightbackground=c["BORDER"])
    hero.pack(fill="x")

    app_ico_photo = None
    for ico_cand in (
        os.path.join(BASE_DIR, "ui", "app_icon.png"),
        os.path.join(ASSETS_DIR, "app_icon.png")
    ):
        if os.path.exists(ico_cand):
            try:
                im = Image.open(ico_cand)
                im.thumbnail((72, 72), Image.LANCZOS)
                app_ico_photo = ImageTk.PhotoImage(im)
                break
            except Exception:
                pass

    if app_ico_photo:
        lbl_ico = tk.Label(hero, image=app_ico_photo, bg=c["PANEL_COLOR"])
        lbl_ico.image = app_ico_photo
        lbl_ico.pack(side="left", padx=(0, 16))

    title_col = tk.Frame(hero, bg=c["PANEL_COLOR"])
    title_col.pack(side="left", fill="x", expand=True)

    r_title = tk.Frame(title_col, bg=c["PANEL_COLOR"])
    r_title.pack(fill="x")
    tk.Label(
        r_title, text="Arapça Telaffuz ve Konuşma Asistanı",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 14, "bold")
    ).pack(side="left")
    tk.Label(
        r_title, text=f" v{APP_VERSION} ",
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 9, "bold"), padx=6, pady=1
    ).pack(side="left", padx=8)

    tk.Label(
        title_col,
        text="Yapay Zekâ Destekli Mahreç, Sarf Sözlüğü, Diyalog Canlandırma ve Sınav Üretim Platformu",
        bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"), anchor="w"
    ).pack(fill="x", pady=(3, 2))

    tk.Label(
        title_col,
        text="✨ %100 Özelleştirilebilir İçerik Motoru: Hazır veri setlerinin yanı sıra kullanıcılar kendi kelime,\n"
             "   diyalog, cümle, görsel ve ses paketlerini kod yazmadan kolayca ekleyip özelleştirebilir.",
        bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 8, "bold"), justify="left", anchor="w"
    ).pack(fill="x")

    # 2. ORTA BÖLÜM: 3 ANA KILAVUZ VE TANITIM BAĞLANTISI (YOUTUBE / WEB / PDF)
    body = tk.Frame(win, bg=c["CARD_COLOR"], padx=22, pady=12)
    body.pack(fill="both", expand=True)

    tk.Label(
        body, text="📚 RESMÎ KILAVUZLAR, TANITIM VİDEOSU VE EĞİTİM KAYNAKLARI",
        bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 10, "bold")
    ).pack(anchor="w", pady=(0, 8))

    def _open_pdf_guide():
        local_candidates = [
            os.path.join(BASE_DIR, "Kilavuz_ve_Tanitim", "Arapca_Telaffuz_Asistani_Kullanim_Kilavuzu.pdf"),
            os.path.join(BASE_DIR, "Arapca_Telaffuz_Asistani_Kullanim_Kilavuzu.pdf"),
            os.path.join(DATA_DIR, "Arapca_Telaffuz_Asistani_Kullanim_Kilavuzu.pdf"),
        ]
        for lp in local_candidates:
            if os.path.exists(lp):
                webbrowser.open("file:///" + os.path.abspath(lp).replace("\\", "/"))
                return
        webbrowser.open(PDF_GUIDE_URL)

    guide_cards = [
        (
            "🎬 YouTube Sesli & Görüntülü Tanıtım ve Kullanım Rehberi",
            "Uygulamanın tüm ekranlarını, mahreç koçunu ve paket hazırlama adımlarını anlatan video rehber.",
            "▶️ YouTube Videosunu İzle",
            RED, "white",
            lambda: webbrowser.open(YOUTUBE_GUIDE_URL)
        ),
        (
            "🌐 Web Tanıtım Sayfası ve Çevrimiçi Görsel Kılavuz",
            "Modül açıklamalarını, ekran galerisini, paket şablonlarını ve güncel kurulum bağlantısını içeren web sayfası.",
            "🌐 Web Sayfasını Aç",
            BLUE, "white",
            lambda: webbrowser.open(WEB_GUIDE_URL)
        ),
        (
            "📕 Kapsamlı PDF Kullanım ve Paket Hazırlama Kılavuzu (A4 Kitapçık)",
            "Tüm ekranların detaylı anlatımını ve içerik üreticileri için Excel/CSV/JSON paket şablonlarını içeren PDF kitapçık.",
            "📕 PDF Kılavuzu Aç",
            "#0d9488", "white",
            _open_pdf_guide
        )
    ]

    for g_title, g_desc, btn_txt, btn_bg, btn_fg, btn_cmd in guide_cards:
        row_c = tk.Frame(
            body, bg=c["BG_COLOR"], padx=14, pady=10,
            highlightthickness=1, highlightbackground=c["BORDER"]
        )
        row_c.pack(fill="x", pady=4)

        left_txt = tk.Frame(row_c, bg=c["BG_COLOR"])
        left_txt.pack(side="left", fill="x", expand=True, padx=(0, 10))
        tk.Label(
            left_txt, text=g_title, bg=c["BG_COLOR"], fg=c["ACCENT_HEAD"],
            font=("Segoe UI", 10, "bold"), anchor="w"
        ).pack(fill="x")
        tk.Label(
            left_txt, text=g_desc, bg=c["BG_COLOR"], fg=c["TEXT_DIM"],
            font=("Segoe UI", 8), anchor="w", justify="left", wraplength=420
        ).pack(fill="x", pady=(2, 0))

        tk.Button(
            row_c, text=btn_txt, command=btn_cmd,
            bg=btn_bg, fg=btn_fg, font=("Segoe UI", 9, "bold"),
            relief="flat", padx=12, pady=6, cursor="hand2", width=21
        ).pack(side="right")

    # Hızlı Uygulama İçi Araçlar Satırı
    quick_row = tk.Frame(body, bg=c["CARD_COLOR"])
    quick_row.pack(fill="x", pady=(10, 4))

    tk.Button(
        quick_row, text="📖 Uygulama İçi Rehber (F1)",
        command=lambda: (win.destroy(), open_user_guide_window(app)),
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"),
        relief="flat", padx=10, pady=5, cursor="hand2"
    ).pack(side="left", padx=(0, 4))

    tk.Button(
        quick_row, text="👋 7 Adımlı Başlangıç Turu",
        command=lambda: (win.destroy(), open_onboarding_tour(app, force_show=True)),
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"),
        relief="flat", padx=10, pady=5, cursor="hand2"
    ).pack(side="left", padx=4)

    tk.Button(
        quick_row, text="🔄 Güncellemeleri Denetle",
        command=lambda: (win.destroy(), open_updater_window(app)),
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"),
        relief="flat", padx=10, pady=5, cursor="hand2"
    ).pack(side="left", padx=4)

    tk.Button(
        quick_row, text="📦 GitHub",
        command=lambda: webbrowser.open(GITHUB_REPO_URL),
        bg=PURPLE, fg="white", font=("Segoe UI", 9, "bold"),
        relief="flat", padx=10, pady=5, cursor="hand2"
    ).pack(side="right")

    # 3. ALT BÖLÜM: ŞIK VE KOMPAKT MUALLİMUN.NET İMZA BARI + KAPAT BUTONU
    footer = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=8, highlightthickness=1, highlightbackground=c["BORDER"])
    footer.pack(fill="x", side="bottom")

    brand_box = tk.Frame(footer, bg=c["PANEL_COLOR"], cursor="hand2")
    brand_box.pack(side="left")
    brand_box.bind("<Button-1>", lambda e: webbrowser.open(MUALLIMUN_MAIN_URL))

    m_logo_photo = None
    for m_cand in (
        os.path.join(BASE_DIR, "ui", "muallimun_logo.png"),
        os.path.join(ASSETS_DIR, "muallimun_logo.png")
    ):
        if os.path.exists(m_cand):
            try:
                mim = Image.open(m_cand)
                mim.thumbnail((22, 22), Image.LANCZOS)
                m_logo_photo = ImageTk.PhotoImage(mim)
                break
            except Exception:
                pass

    if m_logo_photo:
        lbl_ml = tk.Label(brand_box, image=m_logo_photo, bg=c["PANEL_COLOR"], cursor="hand2")
        lbl_ml.image = m_logo_photo
        lbl_ml.pack(side="left", padx=(0, 6))
        lbl_ml.bind("<Button-1>", lambda e: webbrowser.open(MUALLIMUN_MAIN_URL))

    lbl_m_txt = tk.Label(
        brand_box,
        text="Geliştirici & Eğitim Portalı: www.muallimun.com  |  MIT Açık Kaynak Lisansı",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 8, "bold", "underline"), cursor="hand2"
    )
    lbl_m_txt.pack(side="left")
    lbl_m_txt.bind("<Button-1>", lambda e: webbrowser.open(MUALLIMUN_MAIN_URL))

    tk.Button(
        footer, text="Kapat", command=win.destroy,
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 9, "bold"),
        relief="flat", padx=18, pady=4, cursor="hand2"
    ).pack(side="right")