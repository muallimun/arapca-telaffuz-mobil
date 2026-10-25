# -*- coding: utf-8 -*-
"""
Arapça Telaffuz ve Konuşma Asistanı — (Muallimun.Net Tam Sürüm)
Repo: https://github.com/muallimun/arapca-telaffuz-asistani
- 🔄 GitHub Üzerinden Arka Planda Otomatik Sürüm Kontrolü ve Tek Tıkla Güncelleme
- 🪟 Tekil Harici Pencere Yöneticisi (Yeni Pencere Açılınca Öncekini Otomatik Kapatma)
- 📐 Ekran Sınırını Algılayan Akıllı Tooltip (Sağ Panelde Ekran Dışına Taşmama Garantisi)
- 🏛️ 2x2 Öğrenci Odaklı Ana Giriş Ekranı + Gizlenebilir Öğretmen & Ölçme Araçları Barı
- 📂 Doğal Konumlu Akordiyon Menüler, Yeşil Mikrofon Çerçeve Parlaması ve Kırmızı Harf Vurgusu
"""

import os
import sys
import multiprocessing

# 1) PyInstaller (.exe) çoklu işlem kilidi — yeni pencere çoğalmasını kesin olarak engeller
# 2) Sanal ortam (.venv) yönlendirmesi SADECE kaynak koddan (.py) çalışırken devreye girer
if __name__ == "__main__":
    multiprocessing.freeze_support()
    if not getattr(sys, "frozen", False):
        _venv_python = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".venv", "Scripts", "python.exe")
        if os.path.exists(_venv_python) and os.path.normcase(sys.executable) != os.path.normcase(_venv_python):
            import subprocess
            sys.exit(subprocess.call([_venv_python] + sys.argv))

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
os.environ["OMP_NUM_THREADS"] = "4"

import math
import time
import wave
import random
import threading
import traceback
import webbrowser
import tkinter as tk
import tkinter.font as tkfont
from tkinter import messagebox, ttk

import numpy as np
from PIL import Image, ImageTk
import pygame

from core.config import (
    APP_VERSION, GITHUB_REPO_URL,
    BASE_DIR, ASSETS_DIR, RECORDINGS_DIR, BEEP_PATH,
    WHISPER_MODEL_NAME, SAMPLERATE, AR_FONT_FAMILY, ANALYSIS_FONT_FAMILY,
    BG_COLOR, CARD_COLOR, PANEL_COLOR, ACCENT, GREEN, RED, BLUE, YELLOW, PURPLE,
    TEXT_LIGHT, TEXT_DIM, TEXT_AR, CHAT_BG, THEMES,
    FONT_UI, FONT_UI_B, FONT_AR, FONT_VERDICT, FONT_PROMPT, log_error
)
from core.arabic_nlp import (
    remove_diacritics, compare_arabic, evaluate_sentence_words,
    shape_arabic, shape_arabic_by_pixels, analyze_phoneme_and_makhraj_errors,
    build_haraka_guide, expand_arabic_variants
)
from core.database import (
    init_all_dbs, get_word_packages, get_words, update_word_meta,
    get_key_sentences, update_sentence_meta, update_db, reset_stats, get_stats,
    get_config_val, set_config_val, get_streak_and_badges, get_package_completion_info,
    get_live_dashboard_counts
)
from core.dictionary import get_word_morphology_and_family
from core.audio_engine import (
    WHISPER_AVAILABLE, WhisperModel, AUDIO_REC_AVAILABLE, sd,
    audio_lock, generate_beep, get_tts_audio,
    resolve_local_media_path, load_wav_as_16k_float32,
    ensure_mixer_initialized, play_beep_nonblocking,
    play_effect_nonblocking, stop_all_audio_channels
)
from core.updater import check_for_updates_async, open_updater_window
from ui.admin_panel import open_unified_admin_panel
from ui.dialogue_mode import DialogueModeMixin
from ui.dialogs import (
    ConfettiParticle, open_student_name_dialog, open_user_guide_window, open_settings_window,
    open_quick_edit_word, open_quick_edit_sentence,
    open_analytics_and_reports_window,
    open_shadowing_trainer_window, open_listening_warmup_window,
    open_onboarding_tour, open_dictionary_explorer_window,
    open_printable_worksheet_generator,
    get_recordings_stats, cleanup_recordings_folder,
    open_about_info_window
)

MUALLIMUN_URL = "https://www.muallimun.com"
_NEXT_CEFR_MAP = {"A1": "A2", "A2": "B1", "B1": "B2", "B2": "C1", "C1": "C1"}

# Öğrenci Dostu Terim Açıklamaları (Fareyle üzerine gelindiğinde Tooltip olarak gösterilir)
_TERM_TOOLTIPS = {
    "Müfret (Tekil)": "Müfret: Kelimenin tekil (bir adet) hali.",
    "Cemî (Çoğul)": "Cemî: Kelimenin çoğul (üç ve daha fazla) hali.",
    "Mâzî": "Mâzî Fiil: Geçmiş zaman kipi (Örn: كَتَبَ - Yazdı).",
    "Muzâri": "Muzâri Fiil: Şimdiki veya geniş zaman kipi (Örn: يَكْتُبُ - Yazıyor / Yazar).",
    "Masdar": "Masdar: Fiilin zaman bildirmeyen isim hali (-mak / -mek).",
    "SÜLÂSÎ KÖK": "Sülâsî Kök: Arapça kelimenin türediği 3 asli (temel) harf. Tıklayarak bu kökten türeyen tüm akraba kelimeleri görebilirsiniz."
}


class App(DialogueModeMixin):
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title(f"Arapça Telaffuz ve Konuşma Asistanı v{APP_VERSION} — Muallimun.Net")
        # Windows Görev Çubuğu ve Pencere İkonu Bağlantısı
        try:
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("muallimun.arapca_asistani.v40")
        except Exception:
            pass

        # Hem pencere başlığı (iconbitmap) hem Windows görev çubuğu (iconphoto) bağlantısı
        ico_path = os.path.join(BASE_DIR, "ui", "app_icon.ico")
        png_path = os.path.join(BASE_DIR, "ui", "app_icon.png")

        if os.path.exists(ico_path):
            try:
                self.root.iconbitmap(ico_path)
            except Exception:
                pass

        if os.path.exists(png_path):
            try:
                # Windows 10/11 görev çubuğu iconphoto ile verilen PNG'yi doğrudan ve pürüzsüz yakalar
                icon_img = ImageTk.PhotoImage(file=png_path)
                self.root.iconphoto(True, icon_img)
                self._app_taskbar_icon_ref = icon_img  # Garbage collector'dan korumak için referans tutulur
            except Exception:
                pass

        screen_w = root.winfo_screenwidth()
        screen_h = root.winfo_screenheight()
        win_w = min(1360, int(screen_w * 0.94))
        win_h = min(880, int(screen_h * 0.90))
        self.root.geometry(f"{win_w}x{win_h}")
        self.root.minsize(1100, 720)
        self._closing = False
        self._tooltip_win = None
        self._active_dialog_win = None
        self._latest_update_info = None

        generate_beep()
        ensure_mixer_initialized()
        init_all_dbs()

        self.theme_mode = get_config_val("theme_mode", "dark")
        if self.theme_mode not in THEMES:
            self.theme_mode = "dark"
        self.colors = THEMES[self.theme_mode]

        self.current_mode = "home"
        self._audio_session_id = 0
        self.dialogue_playing = False
        self.right_panel_visible = True
        self.quick_settings_open = (get_config_val("quick_settings_open", "0") == "1")
        self.stats_accordion_open = (get_config_val("stats_accordion_open", "0") == "1")
        self.show_teacher_bar_var = tk.BooleanVar(value=(get_config_val("show_teacher_bar", "1") != "0"))
        self.student_name_var = tk.StringVar(value=get_config_val("student_name", "") or "")
        self.recent_attempt_history = []  # [(item_id_str, is_correct_bool), ...]
        self._active_morph_ar = None
        self._active_morph_label = ""
        self._last_coach_summary = ""

        self.voice_pref_var = tk.StringVar(value="Sırayla")
        self.repeat_count_var = tk.IntVar(value=1)
        self.max_attempts_var = tk.StringVar(value="3 Kez")

        self.auto_play_var = tk.BooleanVar(value=True)
        self.test_mode_var = tk.BooleanVar(value=False)
        self.auto_advance_var = tk.BooleanVar(value=False)
        self.auto_start_var = tk.BooleanVar(value=False)
        self.tashkeel_var = tk.BooleanVar(value=True)
        self.word_show_tr = tk.BooleanVar(value=True)
        self.random_order_var = tk.BooleanVar(value=False)
        self.slow_tts_var = tk.BooleanVar(value=False)
        self.auto_clean_recordings_var = tk.BooleanVar(value=True)

        self.roleplay_max_attempts_var = tk.IntVar(value=3)
        self.roleplay_progressive_reveal_var = tk.BooleanVar(value=True)
        self.edit_mode_var = tk.BooleanVar(value=False)

        self.word_level_var = tk.StringVar(value="Tümü")
        self.dlg_level_var = tk.StringVar(value="Tümü")
        self.sent_level_var = tk.StringVar(value="Tümü")
        self.card_word_lvl_var = tk.StringVar(value="A1")
        self.card_sent_lvl_var = tk.StringVar(value="A1")

        self.word_ar_size = 44
        self.word_tr_size = 18

        self.chat_show_tr = tk.BooleanVar(value=True)
        self.chat_show_tashkeel = tk.BooleanVar(value=True)
        self.chat_font_size = 20
        self._last_chat_width = 0

        self.dlg_submode = "learn"
        self.dlg_role_var = tk.StringVar(value="")
        self.roleplay_active = False
        self.roleplay_lines = []
        self.roleplay_turn_index = 0
        self.roleplay_attempt = 0
        self.roleplay_revealed_words = {}
        self.roleplay_final_results = {}
        self.roleplay_waiting_user = False

        self.sent_show_tr = tk.BooleanVar(value=True)
        self.sent_show_tashkeel = tk.BooleanVar(value=True)
        self.sent_hide_ar_var = tk.BooleanVar(value=False)
        self.sent_word_mask = []
        self.sent_ar_size = 34
        self.sent_tr_size = 16
        self.sent_filter_var = tk.StringVar(value="Tümü")

        self.attempt_count = 0

        self.word_packages = get_word_packages()
        saved_cat = get_config_val("last_word_cat_id", "")
        valid_cat_ids = {c[0] for c in self.word_packages}
        if saved_cat and (saved_cat in valid_cat_ids or saved_cat in ("gunluk_tekrar", "tekrar", "zayif")):
            self.current_cat_id = saved_cat
        else:
            self.current_cat_id = self.word_packages[0][0] if self.word_packages else "sozluk_tumu"

        self.current_items = []
        self.current_index = 0
        self.current_photo = None
        self.logo_photo_top = None
        self.logo_photo_footer = None
        self.current_recording_path = None
        self.recording = False
        self.audio_frames = []
        self.audio_stream = None
        self.model = None
        self._model_loading = False
        self.transcribing = False
        self.volume_score = 0
        self.speech_started = False
        self.silence_chunks = 0
        self.confetti_particles = []
        self.wave_buffer = [0] * 20
        self._custom_eval_target = None

        if self.auto_clean_recordings_var.get():
            cleanup_recordings_folder(keep_latest=10)

        self._build_ui()
        self._restore_accordion_states()
        self._apply_theme()
        self._apply_edit_mode_ui()
        self._switch_mode("home")
        self._bind_shortcuts()

        if WHISPER_AVAILABLE:
            self.root.after(900, self._load_model_async)
        else:
            self.rec_status.config(text="Whisper devre dışı", fg=RED)
            self.sent_rec_status.config(text="Whisper devre dışı", fg=RED)

        self.root.after(600, lambda: open_onboarding_tour(self, force_show=False))
        self.root.after(2500, self._check_updates_in_background)
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    # =========================================================================
    # 🪟 TEKİL HARİCİ PENCERE YÖNETİCİSİ (ÖNCEKİ AÇIK PENCEREYİ OTOMATİK KAPATIR)
    # =========================================================================
    def _close_active_singleton_dialog(self):
        """Açık olan harici modül penceresi varsa güvenli şekilde kapatır."""
        if self._active_dialog_win is not None:
            try:
                if self._active_dialog_win.winfo_exists():
                    try:
                        self._active_dialog_win.grab_release()
                    except Exception:
                        pass
                    self._active_dialog_win.destroy()
            except Exception:
                pass
            self._active_dialog_win = None

    def _register_singleton_dialog(self, new_win: tk.Toplevel):
        """Yeni bir harici pencere açıldığında önceki açık pencereyi otomatik kapatır."""
        if self._active_dialog_win is not None and self._active_dialog_win != new_win:
            self._close_active_singleton_dialog()
        self._active_dialog_win = new_win

    def _open_updater_dialog(self):
        self._close_active_singleton_dialog()
        open_updater_window(self, self._latest_update_info)

    def _check_updates_in_background(self):
        """Uygulama açılışında GitHub deposunu arka planda sessizce kontrol eder."""
        def _on_checked(res: dict):
            if self._closing:
                return
            self._latest_update_info = res
            if res.get("ok") and res.get("has_update"):
                rem_v = res.get("remote_version", "")
                self._safe_ui(lambda: (
                    self.btn_update.config(
                        text=f"🔄 v{rem_v} Güncelle!",
                        bg=GREEN, fg="#0f172a", width=0, padx=10
                    ),
                    self._show_toast(f"🎉 Yeni sürüm (v{rem_v}) GitHub'da yayınlandı! Üst çubuktan güncelleyebilirsiniz.", GREEN, 4200)
                ))

        check_for_updates_async(_on_checked)

    def _on_toggle_teacher_bar(self):
        """Ayarlardan 'Öğretmen & Ölçme Araçları Barını Göster' değiştirildiğinde kaydeder ve Ana Sayfayı yeniler."""
        val = "1" if self.show_teacher_bar_var.get() else "0"
        set_config_val("show_teacher_bar", val)
        if self.current_mode == "home":
            self._render_home_dashboard()

    # =========================================================================
    # 💡 EKRAN SINIRINI ALGILAYAN AKILLI AÇIKLAMA BALONCUĞU (TOOLTIP)
    # =========================================================================
    def _attach_tooltip(self, widget: tk.Widget, text_or_fn):
        """
        Fareyle üzerine gelindiğinde açıklama baloncuğu gösterir.
        Sağ panel gibi ekranın sağ kenarına yakın bileşenlerde balonun ekran dışına taşmasını
        önlemek için kutu genişliğini hesaplar ve gerekirse imlecin soluna açar.
        """
        def _show(event):
            self._hide_tooltip()
            msg = text_or_fn() if callable(text_or_fn) else str(text_or_fn)
            if not msg:
                return
            tw = tk.Toplevel(self.root)
            tw.wm_overrideredirect(True)
            lbl = tk.Label(
                tw, text=msg, bg="#0f172a", fg="#ffd369",
                font=("Segoe UI", 9, "bold"), padx=10, pady=5,
                relief="solid", bd=1, wraplength=300, justify="left"
            )
            lbl.pack()
            tw.update_idletasks()

            req_w = tw.winfo_reqwidth()
            req_h = tw.winfo_reqheight()
            screen_w = self.root.winfo_screenwidth()
            screen_h = self.root.winfo_screenheight()

            x = event.x_root + 12
            y = event.y_root + 16

            # Sağ kenardan taşmayı önle: Ekran sınırını aşıyorsa imlecin sol tarafına hizala
            if x + req_w > screen_w - 16:
                x = max(10, event.x_root - req_w - 12)
            # Alt kenardan taşmayı önle
            if y + req_h > screen_h - 40:
                y = max(10, event.y_root - req_h - 10)

            tw.wm_geometry(f"+{x}+{y}")
            self._tooltip_win = tw

        def _hide(event=None):
            self._hide_tooltip()

        widget.bind("<Enter>", _show, add="+")
        widget.bind("<Leave>", _hide, add="+")

    def _hide_tooltip(self):
        if self._tooltip_win is not None:
            try:
                self._tooltip_win.destroy()
            except Exception:
                pass
            self._tooltip_win = None

    def _load_brand_logo(self, size=(34, 34)):
        for candidate in (
            os.path.join(BASE_DIR, "ui", "muallimun_logo.png"),
            os.path.join(ASSETS_DIR, "muallimun_logo.png"),
            os.path.join(BASE_DIR, "muallimun_logo.png")
        ):
            if os.path.exists(candidate):
                try:
                    img = Image.open(candidate)
                    img.thumbnail(size, Image.LANCZOS)
                    return ImageTk.PhotoImage(img)
                except Exception as ex:
                    log_error("Logo yükleme hatası", ex)
        return None

    def _load_app_icon_logo(self, size=(30, 30)):
        for candidate in (
            os.path.join(BASE_DIR, "ui", "app_icon.png"),
            os.path.join(ASSETS_DIR, "app_icon.png"),
            os.path.join(BASE_DIR, "ui", "muallimun_logo.png")
        ):
            if os.path.exists(candidate):
                try:
                    img = Image.open(candidate)
                    img.thumbnail(size, Image.LANCZOS)
                    return ImageTk.PhotoImage(img)
                except Exception as ex:
                    log_error("Uygulama ikonu yükleme hatası", ex)
        return None

    @staticmethod
    def _open_brand_website(event=None):
        try:
            webbrowser.open(MUALLIMUN_URL)
        except Exception as ex:
            log_error("Web sitesi açma hatası", ex)

    def _toggle_right_panel(self):
        if self.right_panel_visible:
            self.right_frame.pack_forget()
            self.right_panel_visible = False
            self.btn_toggle_panel.config(text="◨ Paneli Aç")
        else:
            self.right_frame.pack(side="right", fill="y", padx=(10, 0), before=self.left_frame)
            self.right_panel_visible = True
            self.btn_toggle_panel.config(text="◧ Paneli Gizle")

        self._sync_inline_coach_visibility()
        if self.current_mode == "dialogue":
            self.root.after(100, self._refresh_chat)
        elif self.current_mode == "sentences":
            self.root.after(100, self._show_sentence_card)

    def _sync_inline_coach_visibility(self):
        if not self.right_panel_visible and self._last_coach_summary:
            self.inline_coach_lbl.config(text=self._last_coach_summary)
            self.inline_coach_lbl.place(relx=0.5, rely=0.975, anchor="s")
            self.sent_inline_coach_lbl.config(text=self._last_coach_summary)
            self.sent_inline_coach_lbl.place(relx=0.5, rely=0.965, anchor="s")
        else:
            self.inline_coach_lbl.place_forget()
            self.sent_inline_coach_lbl.place_forget()

    def _restore_accordion_states(self):
        if self.quick_settings_open:
            self.btn_acc_quick.config(text="▼ ⚡ HIZLI AYARLAR (Kapat)")
            self.quick_settings_body.pack(side="top", fill="x", padx=6, pady=(3, 4))
        if self.stats_accordion_open:
            self.btn_acc_stats.config(text="▼ 📊 İSTATİSTİKLER & KAYITLAR (Kapat)")
            self.stats_accordion_body.pack(side="top", fill="x", padx=6, pady=(3, 4))

    def _toggle_quick_settings_accordion(self):
        self.quick_settings_open = not self.quick_settings_open
        set_config_val("quick_settings_open", "1" if self.quick_settings_open else "0")
        if self.quick_settings_open:
            self.btn_acc_quick.config(text="▼ ⚡ HIZLI AYARLAR (Kapat)")
            self.quick_settings_body.pack(side="top", fill="x", padx=6, pady=(3, 4))
        else:
            self.btn_acc_quick.config(text="▶ ⚡ HIZLI AYARLAR (Aç)")
            self.quick_settings_body.pack_forget()

    def _toggle_stats_accordion(self):
        self.stats_accordion_open = not self.stats_accordion_open
        set_config_val("stats_accordion_open", "1" if self.stats_accordion_open else "0")
        if self.stats_accordion_open:
            self.btn_acc_stats.config(text="▼ 📊 İSTATİSTİKLER & KAYITLAR (Kapat)")
            self.stats_accordion_body.pack(side="top", fill="x", padx=6, pady=(3, 4))
        else:
            self.btn_acc_stats.config(text="▶ 📊 İSTATİSTİKLER & KAYITLAR (Aç)")
            self.stats_accordion_body.pack_forget()

    def _set_recording_glow(self, active: bool):
        border_col = GREEN if active else self.colors["BORDER"]
        bw = 3 if active else 1
        if hasattr(self, "card") and self.card.winfo_exists():
            self.card.configure(highlightbackground=border_col, highlightthickness=bw)
        if hasattr(self, "sentence_card") and self.sentence_card.winfo_exists():
            self.sentence_card.configure(highlightbackground=border_col, highlightthickness=bw)

    def _create_standard_punto_bar(self, parent_bar, on_delta_cb):
        p_frame = tk.Frame(parent_bar, bg=BG_COLOR)
        p_frame.pack(side="right", padx=(6, 2))
        btn_plus = tk.Button(p_frame, text="A+", command=lambda: on_delta_cb(2), bg=PANEL_COLOR, fg="white", font=FONT_UI_B, relief="flat", width=3, cursor="hand2")
        btn_plus.pack(side="right", padx=1)
        lbl_p = tk.Label(p_frame, text="Punto", bg=BG_COLOR, fg=TEXT_DIM, font=("Segoe UI", 9, "bold"))
        lbl_p.pack(side="right", padx=3)
        btn_minus = tk.Button(p_frame, text="A-", command=lambda: on_delta_cb(-2), bg=PANEL_COLOR, fg="white", font=FONT_UI_B, relief="flat", width=3, cursor="hand2")
        btn_minus.pack(side="right", padx=1)
        self._punto_widgets.append((p_frame, lbl_p, btn_minus, btn_plus))

    def _stop_all_audio(self, include_effects: bool = False):
        self._audio_session_id += 1
        self.dialogue_playing = False
        stop_all_audio_channels(include_effects=include_effects)
        if hasattr(self, "btn_play_all_dlg"):
            self._safe_ui(lambda: self.btn_play_all_dlg.config(text="▶️ Diyaloğun Tümünü Oynat", bg=ACCENT))
        if hasattr(self, "_highlight_and_scroll_to_line"):
            self._safe_ui(lambda: self._highlight_and_scroll_to_line(None))

    def _toggle_theme(self):
        self.theme_mode = "light" if self.theme_mode == "dark" else "dark"
        self.colors = THEMES[self.theme_mode]
        set_config_val("theme_mode", self.theme_mode)
        self._apply_theme()
        self._refresh_current_mode_data()

    def _apply_theme(self):
        c = self.colors
        bg = c["BG_COLOR"]
        card = c["CARD_COLOR"]
        panel = c["PANEL_COLOR"]
        txt = c["TEXT_MAIN"]
        dim = c["TEXT_DIM"]
        ar_col = c["TEXT_AR"]
        head_col = c["ACCENT_HEAD"]
        border = c["BORDER"]
        btn_sec = c["BTN_SEC"]

        self.root.configure(bg=bg)
        self.top_bar.configure(bg=bg)
        self.brand_top_frame.configure(bg=bg)
        self.lbl_brand_top.configure(bg=btn_sec, fg=head_col)
        if self.lbl_logo_top:
            self.lbl_logo_top.configure(bg=bg)

        self.main_frame.configure(bg=bg)
        self.left_frame.configure(bg=bg)

        for bar in (self.home_container, self.words_container, self.top_words, self.chat_container, self.top_chat, self.sentences_container, self.top_sent, self.filter_frame_s):
            bar.configure(bg=bg)
        for lbl in (self.lbl_lvl_w, self.lbl_cat_w, self.lbl_lvl_c, self.lbl_dlg_c, self.lbl_filter_s, self.lbl_lvl_s):
            lbl.configure(bg=bg, fg=txt)
        for chk in (self.chk_tr_w, self.chk_har_w, self.chk_tr_c, self.chk_har_c, self.chk_tr_s, self.chk_har_s):
            chk.configure(bg=bg, fg=txt, selectcolor=panel, activebackground=bg, activeforeground=txt)
        self.chk_hide_s.configure(bg=bg, fg=ar_col, selectcolor=panel, activebackground=bg, activeforeground=ar_col)

        for pf, plbl, b_min, b_pls in self._punto_widgets:
            pf.configure(bg=bg)
            plbl.configure(bg=bg, fg=dim)
            b_min.configure(bg=btn_sec, fg=txt)
            b_pls.configure(bg=btn_sec, fg=txt)

        # Kelime Kartı
        self.card.configure(bg=card, highlightbackground=border)
        self.canvas.configure(bg=card)
        self.word_meta_bar.configure(bg=card)
        self.lbl_card_w_lvl.configure(bg=card, fg=head_col)
        self.image_label.configure(bg=card)
        self.morph_panel_w.configure(bg=card)
        self.arabic_label.configure(bg=card, fg=ar_col)
        self.turkish_label.configure(bg=card, fg=txt)
        self.audio_bar_w.configure(bg=card)
        self.rec_frame_w.configure(bg=card)
        self.wave_canvas.configure(bg=card)
        self.info_frame_w.configure(bg=card)
        self.attempt_label.configure(bg=card, fg=dim)
        self.result_frame.configure(bg=card)
        self.verdict_label.configure(bg=card)
        self.rec_status.configure(bg=card, fg=dim)
        self.detail_label.configure(bg=card, fg=txt)
        self.inline_coach_lbl.configure(bg=panel, fg=head_col)
        self.btn_prev.configure(bg=btn_sec, fg=txt)
        self.btn_next.configure(bg=btn_sec, fg=txt)
        self.counter_label.configure(bg=bg, fg=dim)

        # Diyalog Alanı
        self.submode_bar.configure(bg=panel, highlightbackground=border)
        self.learn_controls_frame.configure(bg=panel)
        self.roleplay_controls_frame.configure(bg=panel)
        self.lbl_role_title.configure(bg=panel, fg=head_col)
        self.lbl_roleplay_status.configure(bg=panel)
        self.chat_body.configure(bg=c["CHAT_BG"], highlightbackground=border)
        self.chat_canvas.configure(bg=c["CHAT_BG"])
        self.chat_frame.configure(bg=c["CHAT_BG"])

        # Cümle Kartı
        self.sentence_card.configure(bg=card, highlightbackground=border)
        self.sent_canvas.configure(bg=card)
        self.sent_top_controls.configure(bg=card)
        self.lbl_card_s_lvl.configure(bg=card, fg=head_col)
        self.sent_ar_holder.configure(bg=card)
        self.sent_turkish_label.configure(bg=card, fg=txt)
        self.nav_frame_s.configure(bg=card)
        for s_nav_b in self._sent_nav_buttons:
            s_nav_b.configure(bg=btn_sec, fg=txt)
        self.jump_entry.configure(bg=bg, fg=txt, insertbackground=txt)
        self.sent_rec_area.configure(bg=card)
        self.sent_wave_canvas.configure(bg=card)
        self.sent_btns_s.configure(bg=card)
        self.sent_rec_status.configure(bg=card, fg=dim)
        self.sent_verdict_label.configure(bg=card)
        self.sent_inline_coach_lbl.configure(bg=panel, fg=head_col)
        self.sent_counter_label.configure(bg=bg, fg=dim)

        # Sağ Panel
        self.right_frame.configure(bg=panel, highlightbackground=border)
        self.top_btn_frame_r.configure(bg=panel)
        self.btn_all_settings.configure(bg=btn_sec, fg=txt)
        self.streak_box.configure(bg=card, highlightbackground=border)
        self.lbl_streak_info.configure(bg=card, fg=head_col)
        self.lbl_pkg_prog.configure(bg=card, fg=dim)
        for sep in self.right_separators:
            sep.configure(bg=border)

        self.coach_header_row.configure(bg=panel)
        self.lbl_analysis_title.configure(bg=panel, fg=head_col)
        self.char_analysis_text.configure(bg=card, fg=txt, highlightbackground=border, highlightthickness=1)

        self.acc_quick_wrapper.configure(bg=panel, highlightbackground=border)
        self.btn_acc_quick.configure(bg=btn_sec, fg=head_col)
        self.quick_settings_body.configure(bg=panel)
        for q_chk in self.quick_checkbuttons:
            q_chk.configure(bg=panel, fg=txt, selectcolor=card, activebackground=panel, activeforeground=head_col)
        self.voice_row.configure(bg=panel)
        self.lbl_voice_pref.configure(bg=panel, fg=dim)

        self.acc_stats_wrapper.configure(bg=panel, highlightbackground=border)
        self.btn_acc_stats.configure(bg=btn_sec, fg=head_col)
        self.stats_accordion_body.configure(bg=panel)
        self.stat_hdr.configure(bg=panel)
        self.lbl_stat_title.configure(bg=panel, fg=head_col)
        self.btn_reset_stats.configure(bg=btn_sec, fg=txt)
        self.btn_clean_recs.configure(bg=btn_sec, fg=txt)
        self.lbl_correct.configure(bg=panel, fg="#047857" if self.theme_mode == "light" else GREEN)
        self.lbl_wrong.configure(bg=panel, fg=RED)
        self.lbl_acc.configure(bg=panel, fg=txt)

        self.brand_footer_frame.configure(bg=panel)
        self.lbl_brand_footer.configure(bg=panel, fg=head_col)
        if self.lbl_logo_footer:
            self.lbl_logo_footer.configure(bg=panel)

        self.btn_guide.configure(bg=btn_sec, fg=head_col)
        self.btn_guide.configure(bg=btn_sec, fg=head_col)
        if hasattr(self, "btn_info"):
            self.btn_info.configure(bg=btn_sec, fg=head_col)
            
        if not (self._latest_update_info and self._latest_update_info.get("has_update")):
            self.btn_update.configure(bg=btn_sec, fg=head_col)
        self.btn_toggle_panel.configure(bg=btn_sec, fg=txt)
        self.btn_theme.config(
            text="☀️" if self.theme_mode == "dark" else "🌙",
            bg=btn_sec, fg=head_col if self.theme_mode == "dark" else txt
        )
        self._update_mode_buttons_style()
        if self.current_mode == "home":
            self._render_home_dashboard()

    def _update_mode_buttons_style(self):
        c = self.colors
        inactive_bg = c["BTN_SEC"]
        inactive_fg = c["TEXT_MAIN"]
        self.btn_mode_home.config(
            bg=YELLOW if self.current_mode == "home" else inactive_bg,
            fg="#0f172a" if self.current_mode == "home" else inactive_fg
        )
        self.btn_mode_words.config(
            bg=BLUE if self.current_mode == "words" else inactive_bg,
            fg="white" if self.current_mode == "words" else inactive_fg
        )
        self.btn_mode_dialogue.config(
            bg=ACCENT if self.current_mode == "dialogue" else inactive_bg,
            fg="white" if self.current_mode == "dialogue" else inactive_fg
        )
        self.btn_mode_sentences.config(
            bg=GREEN if self.current_mode == "sentences" else inactive_bg,
            fg="#0f172a" if self.current_mode == "sentences" else inactive_fg
        )

    def _refresh_current_mode_data(self):
        if self.current_mode == "home":
            self._render_home_dashboard()
        elif self.current_mode == "words":
            self._load_category_items(play_audio=False)
        elif self.current_mode == "dialogue":
            self._load_dialogues_ui()
        else:
            self._load_sentences_ui(reset_index=False)

    def _is_typing_in_widget(self, event):
        if event and isinstance(event.widget, (tk.Entry, tk.Text, ttk.Combobox, tk.Spinbox)):
            return True
        if event and event.widget.winfo_toplevel() != self.root:
            return True
        return False

    def _bind_shortcuts(self):
        def on_space(event):
            if self._is_typing_in_widget(event):
                return
            if self.current_mode in ["words", "sentences"]:
                self._start_auto_flow()
            elif self.current_mode == "dialogue" and self.dlg_submode == "roleplay" and self.roleplay_waiting_user:
                self._trigger_roleplay_user_mic()

        def on_right(event):
            if self._is_typing_in_widget(event):
                return
            if self.current_mode in ["words", "sentences"]:
                self._next()

        def on_left(event):
            if self._is_typing_in_widget(event):
                return
            if self.current_mode in ["words", "sentences"]:
                self._prev()

        self.root.bind('<space>', on_space)
        self.root.bind('<Right>', on_right)
        self.root.bind('<Left>', on_left)
        self.root.bind('<F1>', lambda e: open_user_guide_window(self))

    @staticmethod
    def _bind_mousewheel(canvas: tk.Canvas, target_frame: tk.Widget):
        def _on_wheel(event):
            if canvas.winfo_exists():
                canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

        def _bind_to_children(widget):
            widget.bind("<MouseWheel>", _on_wheel, add="+")
            for child in widget.winfo_children():
                _bind_to_children(child)

        canvas.bind("<MouseWheel>", _on_wheel, add="+")
        _bind_to_children(target_frame)

    def _apply_edit_mode_ui(self):
        is_edit = self.edit_mode_var.get()
        if is_edit:
            self.btn_edit_word.pack(side="right", padx=5)
            self.btn_edit_dlg.pack(side="right", padx=5)
            self.btn_edit_sent.pack(side="right", padx=5)
        else:
            self.btn_edit_word.pack_forget()
            self.btn_edit_dlg.pack_forget()
            self.btn_edit_sent.pack_forget()

        if self.current_mode == "dialogue":
            self._refresh_chat()

    def _build_ui(self):
        self._punto_widgets = []
        self._sent_nav_buttons = []

        self.top_bar = tk.Frame(self.root, bg=BG_COLOR, height=50)
        self.top_bar.pack(fill="x", padx=15, pady=(10, 0))

        self.btn_mode_home = tk.Button(
            self.top_bar, text="🏛️ Ana Sayfa", command=lambda: self._switch_mode("home"),
            bg=YELLOW, fg="#0f172a", font=("Segoe UI", 10, "bold"),
            relief="flat", padx=12, pady=5, cursor="hand2"
        )
        self.btn_mode_home.pack(side="left", padx=(0, 3))

        self.btn_mode_words = tk.Button(
            self.top_bar, text="🏠 Kelime Modu", command=lambda: self._switch_mode("words"),
            bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 10, "bold"),
            relief="flat", padx=12, pady=5, cursor="hand2"
        )
        self.btn_mode_words.pack(side="left", padx=3)

        self.btn_mode_dialogue = tk.Button(
            self.top_bar, text="💬 Diyalog Modu", command=lambda: self._switch_mode("dialogue"),
            bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 10, "bold"),
            relief="flat", padx=12, pady=5, cursor="hand2"
        )
        self.btn_mode_dialogue.pack(side="left", padx=3)

        self.btn_mode_sentences = tk.Button(
            self.top_bar, text="📜 Cümle Modu", command=lambda: self._switch_mode("sentences"),
            bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 10, "bold"),
            relief="flat", padx=12, pady=5, cursor="hand2"
        )
        self.btn_mode_sentences.pack(side="left", padx=3)

        btn_top_sarf = tk.Button(
            self.top_bar, text="📖 Sarf Sözlüğü", command=lambda: open_dictionary_explorer_window(self),
            bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=11, pady=5, cursor="hand2"
        )
        btn_top_sarf.pack(side="left", padx=3)
        self._attach_tooltip(btn_top_sarf, "Arapça kelimelerin 3 harfli köklerini (Sülâsî) ve türev ailelerini keşfedin.")

        btn_top_exam = tk.Button(
            self.top_bar, text="🎧 Dinle & Bul", command=lambda: open_listening_warmup_window(self, self.current_mode),
            bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=11, pady=5, cursor="hand2"
        )
        btn_top_exam.pack(side="left", padx=3)
        self._attach_tooltip(btn_top_exam, "4 şıklı sesli dinleme-anlama (İstimâ) sınav merkezini açar.")

        btn_top_rep = tk.Button(
            self.top_bar, text="📊 Raporlar", command=lambda: open_analytics_and_reports_window(self),
            bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=11, pady=5, cursor="hand2"
        )
        btn_top_rep.pack(side="left", padx=3)
        self._attach_tooltip(btn_top_rep, "Başarı rozetlerini, gelişim karnesini ve öğrenci ses kayıtlarını görüntüler.")

        # Üst Bar Sağ Köşe: Sadece Dekoratif Uygulama Logosu (Tıklamasız)
        self.brand_top_frame = tk.Frame(self.top_bar, bg=BG_COLOR)
        self.brand_top_frame.pack(side="right", padx=(8, 0))

        self.logo_photo_top = self._load_app_icon_logo((30, 30))
        self.lbl_logo_top = None
        if self.logo_photo_top:
            self.lbl_logo_top = tk.Label(self.brand_top_frame, image=self.logo_photo_top, bg=BG_COLOR)
            self.lbl_logo_top.pack(side="left", padx=(0, 2))

        self.btn_toggle_panel = tk.Button(
            self.top_bar, text="◧ Paneli Gizle", command=self._toggle_right_panel,
            bg="#233554", fg="white", font=FONT_UI_B, relief="flat", padx=10, pady=5, cursor="hand2"
        )
        self.btn_toggle_panel.pack(side="right", padx=3)
        self._attach_tooltip(self.btn_toggle_panel, "Odaklanma modu için sağ paneli gizler veya tekrar açar.")

        # İkonlu Kompakt GitHub Güncelleme Butonu
        self.btn_update = tk.Button(
            self.top_bar, text="🔄", command=self._open_updater_dialog,
            bg="#233554", fg=YELLOW, font=("Segoe UI", 11, "bold"), relief="flat", width=3, pady=3, cursor="hand2"
        )
        self.btn_update.pack(side="right", padx=3)
        self._attach_tooltip(self.btn_update, f"🔄 Otomatik Güncelleme ve Sürüm Merkezi (Kurulu: v{APP_VERSION})")

        # İkonlu Kompakt Hakkında & Kılavuz Merkezi Butonu
        self.lbl_brand_top = tk.Button(
            self.top_bar, text="ℹ️", command=lambda: open_about_info_window(self),
            bg="#233554", fg=YELLOW, font=("Segoe UI", 12, "bold"), relief="flat", width=3, pady=3, cursor="hand2"
        )
        self.lbl_brand_top.pack(side="right", padx=3)
        self._attach_tooltip(
            self.lbl_brand_top,
            "ℹ️ Uygulama Hakkında ve Kılavuzlar (YouTube Videosu • Web Sayfası • PDF Kılavuz)"
        )

        # İkonlu Kompakt Kılavuz Butonu
        self.btn_guide = tk.Button(
            self.top_bar, text="📖", command=lambda: open_user_guide_window(self),
            bg="#233554", fg=YELLOW, font=("Segoe UI", 12, "bold"), relief="flat", width=3, pady=3, cursor="hand2"
        )
        self.btn_guide.pack(side="right", padx=3)
        self._attach_tooltip(self.btn_guide, "📖 Kullanım Kılavuzu ve 7 Adımlı Başlangıç Turu (Kısayol: F1)")

        # İkonlu Kompakt Tema Butonu
        self.btn_theme = tk.Button(
            self.top_bar, text="☀️", command=self._toggle_theme,
            bg="#233554", fg=YELLOW, font=("Segoe UI", 12, "bold"), relief="flat", width=3, pady=3, cursor="hand2"
        )
        self.btn_theme.pack(side="right", padx=3)
        self._attach_tooltip(
            self.btn_theme,
            lambda: "☀️ Aydınlık Moda Geç" if self.theme_mode == "dark" else "🌙 Karanlık Moda Geç"
        )

        self.main_frame = tk.Frame(self.root, bg=BG_COLOR)
        self.main_frame.pack(fill="both", expand=True, padx=10, pady=10)

        self.right_frame = tk.Frame(self.main_frame, bg=PANEL_COLOR, width=310, bd=0, highlightthickness=1, highlightbackground="#233554")
        self.right_frame.pack(side="right", fill="y", padx=(10, 0))
        self.right_frame.pack_propagate(False)

        self.left_frame = tk.Frame(self.main_frame, bg=BG_COLOR)
        self.left_frame.pack(side="left", fill="both", expand=True)
        self.left_frame.pack_propagate(False)

        # =====================================================================
        # 0. ANA GİRİŞ EKRANI (2x2 ÖĞRENCİ ODAKLI DASHBOARD) KONTEYNERİ
        # =====================================================================
        self.home_container = tk.Frame(self.left_frame, bg=BG_COLOR)

        # =====================================================================
        # 1. KELİME MODU (GÖRSELLİ KART + SARF KÖK & TÜREV AĞACI KARTI)
        # =====================================================================
        self.words_container = tk.Frame(self.left_frame, bg=BG_COLOR)
        self.top_words = tk.Frame(self.words_container, bg=BG_COLOR, height=40)
        self.top_words.pack(fill="x", pady=(0, 10))

        self.lbl_lvl_w = tk.Label(self.top_words, text="Seviye:", bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B)
        self.lbl_lvl_w.pack(side="left", padx=(0, 2))
        self.word_lvl_combo = ttk.Combobox(self.top_words, textvariable=self.word_level_var, values=["Tümü", "A1", "A2", "B1", "B2", "C1"], state="readonly", width=5, font=FONT_UI)
        self.word_lvl_combo.pack(side="left", padx=(0, 6))
        self.word_lvl_combo.bind("<<ComboboxSelected>>", self._on_word_level_change)

        self.lbl_cat_w = tk.Label(self.top_words, text="Paket:", bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B)
        self.lbl_cat_w.pack(side="left", padx=(0, 3))

        cat_names = [c[1] for c in self.word_packages]
        self.cat_combo = ttk.Combobox(self.top_words, values=cat_names, state="readonly", width=22, font=FONT_UI)
        self.cat_combo.set(self.word_packages[0][1] if self.word_packages else "")
        self.cat_combo.bind("<<ComboboxSelected>>", self._on_cat_combo_change)
        self.cat_combo.pack(side="left", padx=2)

        tk.Button(self.top_words, text="📅 Günün 15 Tekrarı", command=lambda: self._select_special_cat("gunluk_tekrar"), bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", padx=7).pack(side="left", padx=2)
        tk.Button(self.top_words, text="🎧 Dinle & Bul", command=lambda: open_listening_warmup_window(self, "words"), bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", padx=7).pack(side="left", padx=2)
        tk.Button(self.top_words, text="⭐ Tekrarım", command=lambda: self._select_special_cat("tekrar"), bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", padx=6).pack(side="left", padx=2)
        tk.Button(self.top_words, text="🎯 Zayıflar", command=lambda: self._select_special_cat("zayif"), bg=RED, fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", padx=6).pack(side="left", padx=2)

        self.btn_edit_word = tk.Button(
            self.top_words, text="✏️ Düzenle", command=lambda: open_quick_edit_word(self),
            bg=YELLOW, fg=BG_COLOR, font=FONT_UI_B, relief="flat", cursor="hand2", padx=8
        )

        self._create_standard_punto_bar(self.top_words, self._change_word_font_size)

        self.chk_tr_w = tk.Checkbutton(
            self.top_words, text="🇹🇷 Türkçe", variable=self.word_show_tr,
            command=lambda: self._show_card(play_audio=False),
            bg=BG_COLOR, fg=TEXT_LIGHT, selectcolor=PANEL_COLOR
        )
        self.chk_tr_w.pack(side="right", padx=2)

        self.chk_har_w = tk.Checkbutton(
            self.top_words, text="Hareke", variable=self.tashkeel_var,
            command=lambda: self._show_card(play_audio=False),
            bg=BG_COLOR, fg=TEXT_LIGHT, selectcolor=PANEL_COLOR
        )
        self.chk_har_w.pack(side="right", padx=2)

        self.card = tk.Frame(self.words_container, bg=CARD_COLOR, bd=0, highlightthickness=1, highlightbackground="#2b2b45")
        self.card.pack(fill="both", expand=True, pady=(0, 5))

        self.canvas = tk.Canvas(self.card, bg=CARD_COLOR, highlightthickness=0)
        self.canvas.place(relx=0, rely=0, relwidth=1, relheight=1)

        self.word_meta_bar = tk.Frame(self.card, bg=CARD_COLOR)
        self.word_meta_bar.place(relx=0.5, rely=0.05, anchor="center")
        self.btn_word_fav = tk.Button(self.word_meta_bar, text="☆ Tekrar Listeme Ekle", command=self._toggle_word_fav, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 9, "bold"), relief="flat", padx=10, pady=2, cursor="hand2")
        self.btn_word_fav.pack(side="left", padx=4)
        self.btn_word_learned = tk.Button(self.word_meta_bar, text="☑️ Öğrendim", command=self._toggle_word_learned, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 9, "bold"), relief="flat", padx=10, pady=2, cursor="hand2")
        self.btn_word_learned.pack(side="left", padx=4)

        self.lbl_card_w_lvl = tk.Label(self.word_meta_bar, text="🏷️ Seviye:", bg=CARD_COLOR, fg=YELLOW, font=("Segoe UI", 9, "bold"))
        self.lbl_card_w_lvl.pack(side="left", padx=(10, 2))
        self.card_word_lvl_combo = ttk.Combobox(self.word_meta_bar, textvariable=self.card_word_lvl_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=4, font=("Segoe UI", 9, "bold"))
        self.card_word_lvl_combo.pack(side="left", padx=2)
        self.card_word_lvl_combo.bind("<<ComboboxSelected>>", self._on_card_word_level_change)

        self.toast_label = tk.Label(self.root, text="", bg=YELLOW, fg=BG_COLOR, font=("Segoe UI", 11, "bold"), padx=14, pady=6)
        self.prompt_label = tk.Label(self.canvas, text="", bg=CARD_COLOR, fg=GREEN, font=FONT_PROMPT)

        self.image_label = tk.Label(self.canvas, bg=CARD_COLOR)
        self.image_label.place(relx=0.5, rely=0.21, anchor="center")

        self.morph_panel_w = tk.Frame(self.canvas, bg=CARD_COLOR)

        self.arabic_label = tk.Label(self.canvas, text="", bg=CARD_COLOR, fg=TEXT_AR, font=FONT_AR)
        self.arabic_label.place(relx=0.5, rely=0.40, anchor="center")
        self.turkish_label = tk.Label(self.canvas, text="", bg=CARD_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 18), wraplength=760, justify="center")
        self.turkish_label.place(relx=0.5, rely=0.51, anchor="center")

        self.audio_bar_w = tk.Frame(self.card, bg=CARD_COLOR)
        self.audio_bar_w.place(relx=0.5, rely=0.59, anchor="center")
        tk.Button(self.audio_bar_w, text="🔊 Kadın", command=lambda: self._play_reference_btn("female"), bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=14, pady=5, cursor="hand2", bd=0).pack(side="left", padx=4)
        tk.Button(self.audio_bar_w, text="🔊 Erkek", command=lambda: self._play_reference_btn("male"), bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=14, pady=5, cursor="hand2", bd=0).pack(side="left", padx=4)
        btn_shd_w = tk.Button(self.audio_bar_w, text="🗣️ Gölgele", command=self._open_word_shadowing, bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=14, pady=5, cursor="hand2", bd=0)
        btn_shd_w.pack(side="left", padx=4)
        self._attach_tooltip(btn_shd_w, "Referans sesi dinleyip beklemeden aynı hız ve tonlamayla tekrar etme antrenmanı.")
        self.btn_play_own_word = tk.Button(self.audio_bar_w, text="🎧 Sesimi Dinle", command=self._play_own_recording, bg=PANEL_COLOR, fg=TEXT_DIM, font=FONT_UI_B, relief="flat", padx=12, pady=5, cursor="hand2", bd=0, state="disabled")
        self.btn_play_own_word.pack(side="left", padx=4)

        self.rec_frame_w = tk.Frame(self.card, bg=CARD_COLOR)
        self.rec_frame_w.place(relx=0.5, rely=0.70, anchor="center")
        self.wave_canvas = tk.Canvas(self.rec_frame_w, width=200, height=28, bg=CARD_COLOR, highlightthickness=0)
        self.wave_canvas.pack(pady=(0, 4))
        self.btn_record = tk.Button(self.rec_frame_w, text="🎤 BAŞLA VE KONUŞ", command=self._start_auto_flow, bg=BLUE, fg="white", font=("Segoe UI", 14, "bold"), relief="flat", padx=30, pady=9, cursor="hand2", bd=0)
        self.btn_record.pack()
        self._attach_tooltip(self.btn_record, "Kaydı başlatmak için tıklayın veya klavyeden [Boşluk / Space] tuşuna basın.")

        self.info_frame_w = tk.Frame(self.card, bg=CARD_COLOR)
        self.info_frame_w.place(relx=0.5, rely=0.79, anchor="center")
        self.attempt_label = tk.Label(self.info_frame_w, text="", bg=CARD_COLOR, fg=TEXT_DIM, font=FONT_UI_B)
        self.attempt_label.pack(side="left", padx=10)
        self.btn_pass = tk.Button(self.info_frame_w, text="⏭️ Pas Geç", command=self._pass_word, bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=10, pady=2, cursor="hand2", bd=0)

        self.result_frame = tk.Frame(self.canvas, bg=CARD_COLOR)
        self.result_frame.place(relx=0.5, rely=0.88, anchor="center")

        self.verdict_label = tk.Label(self.result_frame, text="", bg=CARD_COLOR, fg=TEXT_DIM, font=FONT_VERDICT)
        self.verdict_label.pack(pady=(0, 1))

        self.rec_status = tk.Label(self.result_frame, text="", bg=CARD_COLOR, fg=TEXT_DIM, font=("Segoe UI", 10, "bold"))
        self.rec_status.pack(pady=1)
        self.rec_status.bind("<Button-1>", lambda e: self._retry_load_model())

        self.detail_label = tk.Label(self.result_frame, text="", bg=CARD_COLOR, fg=TEXT_DIM, font=("Segoe UI", 12, "bold"), justify="center", wraplength=680)
        self.detail_label.pack(pady=(1, 0))

        self.inline_coach_lbl = tk.Label(
            self.card, text="", bg=PANEL_COLOR, fg=YELLOW,
            font=("Segoe UI", 9, "bold"), padx=12, pady=3, wraplength=700, justify="center"
        )

        self.btn_prev = tk.Button(self.card, text="◀", command=self._prev, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 20, "bold"), relief="flat", width=3, cursor="hand2", bd=0)
        self.btn_prev.place(relx=0.03, rely=0.5, anchor="w")
        self.btn_next = tk.Button(self.card, text="▶", command=self._next, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 20, "bold"), relief="flat", width=3, cursor="hand2", bd=0)
        self.btn_next.place(relx=0.97, rely=0.5, anchor="e")

        self.counter_label = tk.Label(self.words_container, text="", bg=BG_COLOR, fg=TEXT_DIM, font=("Segoe UI", 11, "bold"))
        self.counter_label.pack(side="bottom", pady=5)

        # =====================================================================
        # 2. DİYALOG MODU
        # =====================================================================
        self.chat_container = tk.Frame(self.left_frame, bg=BG_COLOR)

        self.top_chat = tk.Frame(self.chat_container, bg=BG_COLOR, height=40)
        self.top_chat.pack(fill="x", pady=(0, 6))

        self.lbl_lvl_c = tk.Label(self.top_chat, text="Seviye:", bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B)
        self.lbl_lvl_c.pack(side="left", padx=(0, 2))
        self.dlg_lvl_combo = ttk.Combobox(self.top_chat, textvariable=self.dlg_level_var, values=["Tümü", "A1", "A2", "B1", "B2", "C1"], state="readonly", width=5, font=FONT_UI)
        self.dlg_lvl_combo.pack(side="left", padx=(0, 6))
        self.dlg_lvl_combo.bind("<<ComboboxSelected>>", lambda e: self._load_dialogues_ui())

        self.lbl_dlg_c = tk.Label(self.top_chat, text="Diyalog:", bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B)
        self.lbl_dlg_c.pack(side="left", padx=(0, 4))
        self.dlg_combo = ttk.Combobox(self.top_chat, state="readonly", width=28, font=FONT_UI)
        self.dlg_combo.pack(side="left", padx=4)
        self.dlg_combo.bind("<<ComboboxSelected>>", self._on_dialogue_change)

        tk.Button(self.top_chat, text="🛠️ Yönetim & Paketler", command=lambda: open_unified_admin_panel(self, "dialogue"), bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=10, cursor="hand2").pack(side="left", padx=4)

        self.btn_edit_dlg = tk.Button(
            self.top_chat, text="✏️ Düzenle/Sil", command=lambda: open_unified_admin_panel(self, "dialogue"),
            bg=YELLOW, fg=BG_COLOR, font=FONT_UI_B, relief="flat", cursor="hand2", padx=12
        )

        self._create_standard_punto_bar(self.top_chat, self._change_chat_font_size)

        self.chk_tr_c = tk.Checkbutton(self.top_chat, text="🇹🇷 Türkçe", variable=self.chat_show_tr, command=self._refresh_chat, bg=BG_COLOR, fg=TEXT_LIGHT, selectcolor=PANEL_COLOR)
        self.chk_tr_c.pack(side="right", padx=3)
        self.chk_har_c = tk.Checkbutton(self.top_chat, text="Hareke", variable=self.chat_show_tashkeel, command=self._refresh_chat, bg=BG_COLOR, fg=TEXT_LIGHT, selectcolor=PANEL_COLOR)
        self.chk_har_c.pack(side="right", padx=3)

        self.submode_bar = tk.Frame(self.chat_container, bg=PANEL_COLOR, height=44, pady=6, padx=10, highlightthickness=1, highlightbackground="#233554")
        self.submode_bar.pack(fill="x", pady=(0, 8))
        self.submode_bar.pack_propagate(False)

        self.btn_sub_learn = tk.Button(
            self.submode_bar, text="📖 Öğrenme Modu", command=lambda: self._switch_dlg_submode("learn"),
            bg=GREEN, fg=BG_COLOR, font=FONT_UI_B, relief="flat", padx=10, pady=3, cursor="hand2"
        )
        self.btn_sub_learn.pack(side="left", padx=(0, 4))

        self.btn_sub_roleplay = tk.Button(
            self.submode_bar, text="🎭 Ölçme / Canlandırma", command=lambda: self._switch_dlg_submode("roleplay"),
            bg="#1b2a4a", fg=TEXT_LIGHT, font=FONT_UI_B, relief="flat", padx=10, pady=3, cursor="hand2"
        )
        self.btn_sub_roleplay.pack(side="left", padx=4)

        self.learn_controls_frame = tk.Frame(self.submode_bar, bg=PANEL_COLOR)
        self.learn_controls_frame.pack(side="left", padx=10)
        self.btn_play_all_dlg = tk.Button(
            self.learn_controls_frame, text="▶️ Diyaloğun Tümünü Oynat", command=self._play_dialogue,
            bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=3, cursor="hand2"
        )
        self.btn_play_all_dlg.pack(side="left", padx=4)

        self.roleplay_controls_frame = tk.Frame(self.submode_bar, bg=PANEL_COLOR)
        self.lbl_role_title = tk.Label(self.roleplay_controls_frame, text="👤 Rol:", bg=PANEL_COLOR, fg=YELLOW, font=FONT_UI_B)
        self.lbl_role_title.pack(side="left", padx=(4, 2))
        self.role_combo = ttk.Combobox(self.roleplay_controls_frame, textvariable=self.dlg_role_var, state="readonly", width=13, font=FONT_UI_B)
        self.role_combo.pack(side="left", padx=3)
        self.role_combo.bind("<<ComboboxSelected>>", lambda e: self._refresh_chat())

        self.btn_start_roleplay = tk.Button(
            self.roleplay_controls_frame, text="🎬 Canlandırmayı Başlat", command=self._toggle_roleplay_session,
            bg=GREEN, fg=BG_COLOR, font=FONT_UI_B, relief="flat", padx=10, pady=3, cursor="hand2"
        )
        self.btn_start_roleplay.pack(side="left", padx=4)

        self.btn_roleplay_mic = tk.Button(
            self.roleplay_controls_frame, text="🎤 Konuş / Durdur", command=self._trigger_roleplay_user_mic,
            bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=9, pady=3, cursor="hand2", state="disabled"
        )
        self.btn_roleplay_mic.pack(side="left", padx=3)

        self.lbl_roleplay_status = tk.Label(self.submode_bar, text="", bg=PANEL_COLOR, fg=YELLOW, font=("Segoe UI", 9, "bold"), anchor="e")
        self.lbl_roleplay_status.pack(side="right", padx=6, fill="x", expand=True)

        self.chat_body = tk.Frame(self.chat_container, bg=CHAT_BG, bd=0, highlightthickness=1, highlightbackground="#2b2b45")
        self.chat_body.pack(fill="both", expand=True, pady=(0, 5))

        self.chat_canvas = tk.Canvas(self.chat_body, bg=CHAT_BG, highlightthickness=0)
        self.chat_scrollbar = ttk.Scrollbar(self.chat_body, orient="vertical", command=self.chat_canvas.yview)
        self.chat_frame = tk.Frame(self.chat_canvas, bg=CHAT_BG)

        self.chat_frame.bind("<Configure>", lambda e: self.chat_canvas.configure(scrollregion=self.chat_canvas.bbox("all")))
        self.chat_window = self.chat_canvas.create_window((0, 0), window=self.chat_frame, anchor="nw")
        self.chat_canvas.bind("<Configure>", self._on_chat_canvas_configure)

        self.chat_canvas.pack(side="left", fill="both", expand=True, padx=10, pady=5)
        self.chat_scrollbar.pack(side="right", fill="y")
        self.chat_canvas.configure(yscrollcommand=self.chat_scrollbar.set)

        # =====================================================================
        # 3. CÜMLE MODU
        # =====================================================================
        self.sentences_container = tk.Frame(self.left_frame, bg=BG_COLOR)
        self.top_sent = tk.Frame(self.sentences_container, bg=BG_COLOR, height=40)
        self.top_sent.pack(fill="x", pady=(0, 10))

        tk.Button(self.top_sent, text="🛠️ Yönetim & Paketler", command=lambda: open_unified_admin_panel(self, "sentences"), bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", padx=8).pack(side="left", padx=2)

        self.filter_frame_s = tk.Frame(self.top_sent, bg=BG_COLOR)
        self.filter_frame_s.pack(side="left", padx=4)
        self.lbl_filter_s = tk.Label(self.filter_frame_s, text="Filtre:", bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B)
        self.lbl_filter_s.pack(side="left")
        filter_combo = ttk.Combobox(
            self.filter_frame_s, textvariable=self.sent_filter_var,
            values=["Tümü", "📅 Günün 15 Tekrarı", "Favoriler", "Öğrendiklerim", "Zorlandıklarım"],
            state="readonly", width=16, font=FONT_UI
        )
        filter_combo.pack(side="left", padx=3)
        filter_combo.bind("<<ComboboxSelected>>", lambda e: (self.recent_attempt_history.clear(), self._load_sentences_ui(reset_index=True)))

        self.lbl_lvl_s = tk.Label(self.filter_frame_s, text="Seviye:", bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B)
        self.lbl_lvl_s.pack(side="left", padx=(4, 2))
        self.sent_lvl_combo = ttk.Combobox(
            self.filter_frame_s, textvariable=self.sent_level_var,
            values=["Tümü", "A1", "A2", "B1", "B2", "C1"],
            state="readonly", width=5, font=FONT_UI
        )
        self.sent_lvl_combo.pack(side="left", padx=2)
        self.sent_lvl_combo.bind("<<ComboboxSelected>>", lambda e: (self.recent_attempt_history.clear(), self._load_sentences_ui(reset_index=True)))

        tk.Button(self.top_sent, text="🎧 Dinle & Bul", command=lambda: open_listening_warmup_window(self, "sentences"), bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", padx=8).pack(side="left", padx=3)

        self.btn_edit_sent = tk.Button(
            self.top_sent, text="✏️ Düzenle", command=lambda: open_quick_edit_sentence(self),
            bg=YELLOW, fg=BG_COLOR, font=FONT_UI_B, relief="flat", cursor="hand2", padx=8
        )

        self._create_standard_punto_bar(self.top_sent, self._change_font_size)

        self.chk_tr_s = tk.Checkbutton(self.top_sent, text="🇹🇷 Türkçe", variable=self.sent_show_tr, command=self._update_sentence_ui, bg=BG_COLOR, fg=TEXT_LIGHT, selectcolor=PANEL_COLOR)
        self.chk_tr_s.pack(side="right", padx=2)
        self.chk_har_s = tk.Checkbutton(self.top_sent, text="Hareke", variable=self.sent_show_tashkeel, command=self._update_sentence_ui, bg=BG_COLOR, fg=TEXT_LIGHT, selectcolor=PANEL_COLOR)
        self.chk_har_s.pack(side="right", padx=2)
        self.chk_hide_s = tk.Checkbutton(
            self.top_sent, text="🙈 Arapçayı Gizle", variable=self.sent_hide_ar_var,
            command=self._update_sentence_ui, bg=BG_COLOR, fg=YELLOW, selectcolor=PANEL_COLOR, font=FONT_UI_B
        )
        self.chk_hide_s.pack(side="right", padx=3)

        self.sentence_card = tk.Frame(self.sentences_container, bg=CARD_COLOR, bd=0, highlightthickness=1, highlightbackground="#2b2b45")
        self.sentence_card.pack(fill="both", expand=True, pady=(0, 5))

        self.sent_canvas = tk.Canvas(self.sentence_card, bg=CARD_COLOR, highlightthickness=0)
        self.sent_canvas.place(relx=0, rely=0, relwidth=1, relheight=1)

        self.sent_top_controls = tk.Frame(self.sentence_card, bg=CARD_COLOR)
        self.sent_top_controls.place(relx=0.5, rely=0.07, anchor="center")

        self.btn_fav = tk.Button(self.sent_top_controls, text="⭐ Favori", command=self._toggle_fav, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B, relief="flat", padx=14, pady=4, cursor="hand2")
        self.btn_fav.pack(side="left", padx=5)
        self.btn_learned = tk.Button(self.sent_top_controls, text="✅ Öğrendim", command=self._toggle_learned, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B, relief="flat", padx=14, pady=4, cursor="hand2")
        self.btn_learned.pack(side="left", padx=5)

        self.lbl_card_s_lvl = tk.Label(self.sent_top_controls, text="🏷️ Seviye:", bg=CARD_COLOR, fg=YELLOW, font=FONT_UI_B)
        self.lbl_card_s_lvl.pack(side="left", padx=(10, 2))
        self.card_sent_lvl_combo = ttk.Combobox(self.sent_top_controls, textvariable=self.card_sent_lvl_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=4, font=FONT_UI_B)
        self.card_sent_lvl_combo.pack(side="left", padx=2)
        self.card_sent_lvl_combo.bind("<<ComboboxSelected>>", self._on_card_sent_level_change)

        self.sent_prompt_label = tk.Label(self.sentence_card, text="", bg=CARD_COLOR, fg=GREEN, font=FONT_PROMPT)

        self.sent_ar_holder = tk.Frame(self.sentence_card, bg=CARD_COLOR)
        self.sent_ar_holder.place(relx=0.5, rely=0.33, anchor="center")

        self.sent_arabic_label = tk.Label(
            self.sent_ar_holder, text="", bg=CARD_COLOR, fg=TEXT_AR,
            font=(AR_FONT_FAMILY, self.sent_ar_size, "bold"), justify="center"
        )
        self.sent_arabic_label.pack()

        self.sent_turkish_label = tk.Label(
            self.sentence_card, text="", bg=CARD_COLOR, fg=TEXT_LIGHT,
            font=("Segoe UI", self.sent_tr_size), wraplength=760, justify="center"
        )
        self.sent_turkish_label.place(relx=0.5, rely=0.51, anchor="center")

        self.nav_frame_s = tk.Frame(self.sentence_card, bg=CARD_COLOR)
        self.nav_frame_s.place(relx=0.5, rely=0.63, anchor="center")

        b_first = tk.Button(self.nav_frame_s, text="⏮ İlk", command=self._first_sent, bg=PANEL_COLOR, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=5, cursor="hand2")
        b_first.pack(side="left", padx=3)
        b_prev_s = tk.Button(self.nav_frame_s, text="◀ Önceki", command=self._prev, bg=PANEL_COLOR, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=5, cursor="hand2")
        b_prev_s.pack(side="left", padx=3)

        self.jump_entry = tk.Entry(self.nav_frame_s, width=6, bg=BG_COLOR, fg=TEXT_LIGHT, font=FONT_UI_B, justify="center", insertbackground="white")
        self.jump_entry.pack(side="left", padx=6, ipady=3)
        self.jump_entry.bind("<Return>", lambda e: self._jump_to_index())
        tk.Button(self.nav_frame_s, text="Git", command=self._jump_to_index, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=10, pady=5, cursor="hand2").pack(side="left", padx=3)

        b_next_s = tk.Button(self.nav_frame_s, text="Sonraki ▶", command=self._next, bg=PANEL_COLOR, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=5, cursor="hand2")
        b_next_s.pack(side="left", padx=3)
        b_last = tk.Button(self.nav_frame_s, text="Son ⏭", command=self._last_sent, bg=PANEL_COLOR, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=5, cursor="hand2")
        b_last.pack(side="left", padx=3)

        self.sent_rec_area = tk.Frame(self.sentence_card, bg=CARD_COLOR)
        self.sent_rec_area.place(relx=0.5, rely=0.75, anchor="center")

        self.sent_wave_canvas = tk.Canvas(self.sent_rec_area, width=200, height=26, bg=CARD_COLOR, highlightthickness=0)
        self.sent_wave_canvas.pack(pady=(0, 4))

        self.sent_btns_s = tk.Frame(self.sent_rec_area, bg=CARD_COLOR)
        self.sent_btns_s.pack()
        self.btn_sent_record = tk.Button(self.sent_btns_s, text="🎤 Telaffuz Et", command=self._start_auto_flow, bg=BLUE, fg="white", font=("Segoe UI", 12, "bold"), relief="flat", padx=18, pady=8, cursor="hand2")
        self.btn_sent_record.pack(side="left", padx=5)
        tk.Button(self.sent_btns_s, text="🔊 Dinle (TTS)", command=self._play_sentence_tts, bg=ACCENT, fg="white", font=("Segoe UI", 12, "bold"), relief="flat", padx=18, pady=8, cursor="hand2").pack(side="left", padx=5)
        tk.Button(self.sent_btns_s, text="🗣️ Gölgele (Shadowing)", command=self._open_sentence_shadowing, bg=PURPLE, fg="white", font=("Segoe UI", 12, "bold"), relief="flat", padx=16, pady=8, cursor="hand2").pack(side="left", padx=5)
        self.btn_play_own_sent = tk.Button(self.sent_btns_s, text="🎧 Sesimi Dinle", command=self._play_own_recording, bg=PANEL_COLOR, fg=TEXT_DIM, font=("Segoe UI", 11, "bold"), relief="flat", padx=14, pady=8, cursor="hand2", state="disabled")
        self.btn_play_own_sent.pack(side="left", padx=5)

        self.sent_verdict_label = tk.Label(self.sentence_card, text="Telaffuz değerlendirmesi için kayıt yapın", bg=CARD_COLOR, fg=TEXT_DIM, font=FONT_VERDICT)
        self.sent_verdict_label.place(relx=0.5, rely=0.86, anchor="center")

        self.sent_rec_status = tk.Label(self.sentence_card, text="", bg=CARD_COLOR, fg=TEXT_DIM, font=("Segoe UI", 12, "bold"))
        self.sent_rec_status.place(relx=0.5, rely=0.91, anchor="center")
        self.sent_rec_status.bind("<Button-1>", lambda e: self._retry_load_model())

        self.sent_inline_coach_lbl = tk.Label(
            self.sentence_card, text="", bg=PANEL_COLOR, fg=YELLOW,
            font=("Segoe UI", 9, "bold"), padx=12, pady=3, wraplength=700, justify="center"
        )

        b_side_l = tk.Button(self.sentence_card, text="◀", command=self._prev, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 20, "bold"), relief="flat", width=3, cursor="hand2", bd=0)
        b_side_l.place(relx=0.03, rely=0.5, anchor="w")
        b_side_r = tk.Button(self.sentence_card, text="▶", command=self._next, bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 20, "bold"), relief="flat", width=3, cursor="hand2", bd=0)
        b_side_r.place(relx=0.97, rely=0.5, anchor="e")

        self._sent_nav_buttons.extend([b_first, b_prev_s, b_next_s, b_last, b_side_l, b_side_r])

        self.sent_counter_label = tk.Label(self.sentences_container, text="", bg=BG_COLOR, fg=TEXT_DIM, font=("Segoe UI", 11, "bold"))
        self.sent_counter_label.pack(side="bottom", pady=5)

        # =====================================================================
        # SAĞ TARAF: ÜST KONUMLU MAHREÇ KOÇU + DOĞAL KONUMLU AKORDİYON MENÜLER
        # =====================================================================
        self.right_separators = []
        self.quick_checkbuttons = []

        self.top_btn_frame_r = tk.Frame(self.right_frame, bg=PANEL_COLOR)
        self.top_btn_frame_r.pack(fill="x", padx=10, pady=(10, 3))
        tk.Button(
            self.top_btn_frame_r, text="🛠️ Yönetim & Paket Paneli",
            command=lambda: open_unified_admin_panel(self, self.current_mode if self.current_mode != "home" else "words"),
            bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", cursor="hand2", pady=5
        ).pack(fill="x")

        self.btn_all_settings = tk.Button(
            self.right_frame, text="⚙️ Tüm Ayarlar (Sekmeli)", command=lambda: open_settings_window(self),
            bg="#233554", fg=TEXT_LIGHT, font=FONT_UI_B, relief="flat", cursor="hand2", pady=4
        )
        self.btn_all_settings.pack(fill="x", padx=10, pady=2)

        self.streak_box = tk.Frame(self.right_frame, bg=CARD_COLOR, padx=8, pady=6, highlightthickness=1, highlightbackground="#233554", cursor="hand2")
        self.streak_box.pack(fill="x", padx=10, pady=(4, 2))
        self.streak_box.bind("<Button-1>", lambda e: open_analytics_and_reports_window(self))

        self.lbl_streak_info = tk.Label(self.streak_box, text="🔥 Seri: 0 Gün | 🎯 Hedef: 0/20", bg=CARD_COLOR, fg=YELLOW, font=("Segoe UI", 9, "bold"), cursor="hand2")
        self.lbl_streak_info.pack(anchor="w")
        self.lbl_streak_info.bind("<Button-1>", lambda e: open_analytics_and_reports_window(self))

        self.lbl_pkg_prog = tk.Label(self.streak_box, text="📦 Paket Tamamlama: %0", bg=CARD_COLOR, fg=TEXT_DIM, font=("Segoe UI", 8, "bold"), cursor="hand2")
        self.lbl_pkg_prog.pack(anchor="w", pady=(2, 0))
        self.lbl_pkg_prog.bind("<Button-1>", lambda e: open_analytics_and_reports_window(self))

        sep1 = tk.Frame(self.right_frame, bg="#233554", height=1)
        sep1.pack(fill="x", padx=10, pady=4)
        self.right_separators.append(sep1)

        self.brand_footer_frame = tk.Frame(self.right_frame, bg=PANEL_COLOR, pady=5, cursor="hand2")
        self.brand_footer_frame.pack(side="bottom", fill="x", padx=10, pady=(2, 5))
        self.brand_footer_frame.bind("<Button-1>", self._open_brand_website)

        self.logo_photo_footer = self._load_brand_logo((26, 26))
        self.lbl_logo_footer = None
        if self.logo_photo_footer:
            self.lbl_logo_footer = tk.Label(self.brand_footer_frame, image=self.logo_photo_footer, bg=PANEL_COLOR, cursor="hand2")
            self.lbl_logo_footer.pack(side="left", padx=(15, 6))
            self.lbl_logo_footer.bind("<Button-1>", self._open_brand_website)

        self.lbl_brand_footer = tk.Label(
            self.brand_footer_frame, text="🌐 www.muallimun.com", bg=PANEL_COLOR, fg=YELLOW,
            font=("Segoe UI", 9, "bold"), cursor="hand2"
        )
        self.lbl_brand_footer.pack(side="left")
        self.lbl_brand_footer.bind("<Button-1>", self._open_brand_website)

        # DOĞAL AKORDİYON 2 (Başlık Üstte, İçerik Hemen Altında Açılır)
        self.acc_stats_wrapper = tk.Frame(self.right_frame, bg=PANEL_COLOR, highlightthickness=1, highlightbackground="#233554")
        self.acc_stats_wrapper.pack(side="bottom", fill="x", padx=10, pady=(2, 4))

        self.btn_acc_stats = tk.Button(
            self.acc_stats_wrapper, text="▶ 📊 İSTATİSTİKLER & KAYITLAR (Aç)",
            command=self._toggle_stats_accordion, bg="#233554", fg=YELLOW,
            font=("Segoe UI", 9, "bold"), relief="flat", anchor="w", padx=10, pady=5, cursor="hand2"
        )
        self.btn_acc_stats.pack(side="top", fill="x")

        self.stats_accordion_body = tk.Frame(self.acc_stats_wrapper, bg=PANEL_COLOR)
        self.stat_hdr = tk.Frame(self.stats_accordion_body, bg=PANEL_COLOR)
        self.stat_hdr.pack(fill="x", pady=(2, 2))
        self.lbl_stat_title = tk.Label(self.stat_hdr, text="KELİME İSTATİSTİKLERİ", bg=PANEL_COLOR, fg=YELLOW, font=("Segoe UI", 9, "bold"))
        self.lbl_stat_title.pack(side="left")
        self.btn_reset_stats = tk.Button(self.stat_hdr, text="🔄 Sıfırla", command=self._reset_current_stats, bg="#233554", fg=TEXT_LIGHT, font=("Segoe UI", 8), relief="flat", padx=5, cursor="hand2")
        self.btn_reset_stats.pack(side="right")

        self.lbl_correct = tk.Label(self.stats_accordion_body, text="Doğru: 0", bg=PANEL_COLOR, fg=GREEN, font=("Segoe UI", 9, "bold"))
        self.lbl_correct.pack(anchor="w")
        self.lbl_wrong = tk.Label(self.stats_accordion_body, text="Yanlış: 0", bg=PANEL_COLOR, fg=RED, font=("Segoe UI", 9, "bold"))
        self.lbl_wrong.pack(anchor="w")
        self.lbl_acc = tk.Label(self.stats_accordion_body, text="Başarı: %0", bg=PANEL_COLOR, fg=TEXT_LIGHT, font=("Segoe UI", 9, "bold"))
        self.lbl_acc.pack(anchor="w")

        self.btn_clean_recs = tk.Button(
            self.stats_accordion_body, text="🧹 Ses Kayıtlarını Temizle", command=self._manual_clean_recordings,
            bg="#233554", fg=TEXT_LIGHT, font=("Segoe UI", 8, "bold"), relief="flat", pady=3, cursor="hand2"
        )
        self.btn_clean_recs.pack(fill="x", pady=(4, 2))
        self._update_recordings_btn_label()

        # DOĞAL AKORDİYON 1 (Başlık Üstte, İçerik Hemen Altında Açılır)
        self.acc_quick_wrapper = tk.Frame(self.right_frame, bg=PANEL_COLOR, highlightthickness=1, highlightbackground="#233554")
        self.acc_quick_wrapper.pack(side="bottom", fill="x", padx=10, pady=(4, 2))

        self.btn_acc_quick = tk.Button(
            self.acc_quick_wrapper, text="▶ ⚡ HIZLI AYARLAR (Aç)",
            command=self._toggle_quick_settings_accordion, bg="#233554", fg=YELLOW,
            font=("Segoe UI", 9, "bold"), relief="flat", anchor="w", padx=10, pady=5, cursor="hand2"
        )
        self.btn_acc_quick.pack(side="top", fill="x")

        self.quick_settings_body = tk.Frame(self.acc_quick_wrapper, bg=PANEL_COLOR)
        for q_text, q_var, q_cmd in [
            ("🙌 Eller Serbest Mod (Otomatik Kayıt)", self.auto_start_var, None),
            ("🐢 Yavaş Seslendirme (%25)", self.slow_tts_var, None),
            ("🔊 Geçişte Otomatik Seslendir", self.auto_play_var, None),
            ("⏩ Doğru Bilince Otomatik İlerle", self.auto_advance_var, None),
            ("🎲 Rastgele Sıra", self.random_order_var, self._refresh_current_mode_data)
        ]:
            chk = tk.Checkbutton(
                self.quick_settings_body, text=q_text, variable=q_var, command=q_cmd,
                bg=PANEL_COLOR, fg=TEXT_LIGHT, selectcolor=BG_COLOR,
                activebackground=PANEL_COLOR, activeforeground=YELLOW, font=("Segoe UI", 9)
            )
            chk.pack(anchor="w", pady=0)
            self.quick_checkbuttons.append(chk)

        self.voice_row = tk.Frame(self.quick_settings_body, bg=PANEL_COLOR)
        self.voice_row.pack(fill="x", pady=3)
        self.lbl_voice_pref = tk.Label(self.voice_row, text="Ses Tercihi:", bg=PANEL_COLOR, fg=TEXT_DIM, font=("Segoe UI", 9, "bold"))
        self.lbl_voice_pref.pack(side="left")
        ttk.Combobox(self.voice_row, textvariable=self.voice_pref_var, values=["Sırayla", "Kadın", "Erkek"], state="readonly", width=10, font=("Segoe UI", 9)).pack(side="left", padx=6)

        # ANA MERKEZ BLOĞU: 🎯 MAHREÇ & HAREKE KOÇU
        self.coach_header_row = tk.Frame(self.right_frame, bg=PANEL_COLOR)
        self.coach_header_row.pack(fill="x", padx=10, pady=(2, 3))
        self.lbl_analysis_title = tk.Label(
            self.coach_header_row, text="🎯 CANLI MAHREÇ & HAREKE KOÇU ℹ️",
            bg=PANEL_COLOR, fg=YELLOW, font=("Segoe UI", 10, "bold"), cursor="question_arrow"
        )
        self.lbl_analysis_title.pack(side="left")
        self._attach_tooltip(
            self.lbl_analysis_title,
            "Mahreç: Arapça harflerin ağız ve boğazdaki çıkış noktasıdır. Konuştuğunuzda hatalı harfler burada kırmızıyla gösterilir."
        )

        self.char_analysis_text = tk.Text(
            self.right_frame, height=14, width=32, bg=CARD_COLOR, fg=TEXT_LIGHT,
            font=(ANALYSIS_FONT_FAMILY, 12, "bold"), bd=0, state="disabled",
            wrap="word", highlightthickness=0, padx=10, pady=8
        )
        self.char_analysis_text.pack(padx=10, pady=(0, 6), fill="both", expand=True)

    # =========================================================================
    # 🏛️ SADELEŞTİRİLMİŞ 2x2 ÖĞRENCİ ODAKLI ANA GİRİŞ EKRANI + GİZLENEBİLİR ÖĞRETMEN BARI
    # =========================================================================
    def _resume_last_session(self):
        last_m = get_config_val("last_active_mode", "words")
        if last_m not in ("words", "dialogue", "sentences"):
            last_m = "words"
        if last_m == "words":
            saved_cat = get_config_val("last_word_cat_id", "")
            if saved_cat:
                self.current_cat_id = saved_cat
        self._switch_mode(last_m)

    def _render_home_dashboard(self):
        for w in self.home_container.winfo_children():
            w.destroy()

        c = self.colors
        is_dark = (self.theme_mode == "dark")
        counts = get_live_dashboard_counts()
        badge_info = get_streak_and_badges()

        # 1. Üst Karşılama & Günlük Hedef Banner'ı
        hero = tk.Frame(
            self.home_container, bg=c["PANEL_COLOR"], padx=22, pady=12,
            highlightthickness=1, highlightbackground=c["BORDER"]
        )
        hero.pack(fill="x", pady=(0, 8))

        hero_left = tk.Frame(hero, bg=c["PANEL_COLOR"])
        hero_left.pack(side="left", fill="x", expand=True)

        tk.Label(
            hero_left, text=shape_arabic("أَهْلاً وَسَهْلاً بِكُمْ فِي مُخْتَبَرِ النُّطْقِ وَالْمُحَادَثَةِ"),
            bg=c["PANEL_COLOR"], fg=c["TEXT_AR"], font=(AR_FONT_FAMILY, 22, "bold"),
            wraplength=560, justify="right"
        ).pack(anchor="w")

        std_name = (self.student_name_var.get() or get_config_val("student_name", "") or "Öğrenci").strip()
        tk.Label(
            hero_left,
            text=f"👋 Hoş geldin {std_name}! Bugün hangi modda Arapça konuşma pratiği yapmak istersin?",
            bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 11, "bold"),
            wraplength=560, justify="left"
        ).pack(anchor="w", pady=(2, 0))

        hero_right = tk.Frame(hero, bg=c["CARD_COLOR"], padx=14, pady=8, highlightthickness=1, highlightbackground=c["BORDER"])
        hero_right.pack(side="right")

        name_badge_btn = tk.Button(
            hero_right, text=f"👤 {std_name} ✏️   |   {badge_info['level_title']}",
            command=lambda: open_student_name_dialog(self, force_show=True),
            bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 10, "bold"),
            relief="flat", bd=0, cursor="hand2", anchor="e"
        )
        name_badge_btn.pack(anchor="e")
        self._attach_tooltip(name_badge_btn, "Kullanıcı / Öğrenci adını değiştirmek için tıklayın.")
        tk.Label(
            hero_right,
            text=f"🔥 Seri: {badge_info['streak']} Gün  |  🎯 Bugün: {badge_info['daily_count']}/{badge_info['daily_goal']}  |  ⭐ Puan: {badge_info['total_points']}",
            bg=c["CARD_COLOR"], fg=GREEN if is_dark else "#047857", font=("Segoe UI", 9, "bold")
        ).pack(anchor="e", pady=(2, 0))

        # 2. "📍 Kaldığınız Yerden Devam Edin" + "🎯 Akıllı Öneri" Şeridi
        resume_bar = tk.Frame(
            self.home_container, bg=c["CARD_COLOR"], padx=16, pady=8,
            highlightthickness=1, highlightbackground=c["BORDER"]
        )
        resume_bar.pack(fill="x", pady=(0, 8))

        last_m = get_config_val("last_active_mode", "words")
        mode_names = {"words": "🏠 Kelime Modu", "dialogue": "💬 Diyalog Modu", "sentences": "📜 Cümle Modu"}
        last_cat_name = dict(self.word_packages).get(self.current_cat_id, self.current_cat_id)
        resume_detail = f"{mode_names.get(last_m, '🏠 Kelime Modu')}" + (f" ({last_cat_name})" if last_m == "words" else "")

        _, w_wrg = get_stats("words")
        if badge_info["daily_count"] == 0:
            smart_tip = "🎯 Bugünün Önerisi: Günlük serinizi başlatmak için '📅 Günün 15 Tekrarı' ile ısınma yapın."
        elif w_wrg >= 5:
            smart_tip = f"🎯 Akıllı Koç Önerisi: Zorlandığınız {w_wrg} kelime var; '🎯 Zayıflar' listesini tekrar edebilirsiniz."
        else:
            smart_tip = "🎯 Akıllı Koç Önerisi: Kelime köklerini keşfedip '🎧 Dinle & Bul' sınavıyla bilginizi pekiştirin."

        tk.Label(
            resume_bar, text=f"📍 Son Çalışılan: {resume_detail}   |   {smart_tip}",
            bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"), anchor="w"
        ).pack(side="left", fill="x", expand=True)

        tk.Button(
            resume_bar, text="▶️ Kaldığım Yerden Devam Et", command=self._resume_last_session,
            bg=GREEN, fg="#0f172a", font=("Segoe UI", 9, "bold"), relief="flat", padx=14, pady=4, cursor="hand2"
        ).pack(side="right")

        # 3. Ferah 2x2 Öğrenci Odaklı Modül Izgarası (4 Ana Kart)
        grid_f = tk.Frame(self.home_container, bg=c["BG_COLOR"])
        grid_f.pack(fill="both", expand=True)
        for col_i in range(2):
            grid_f.grid_columnconfigure(col_i, weight=1, uniform="home_col")
        for row_i in range(2):
            grid_f.grid_rowconfigure(row_i, weight=1, uniform="home_row")

        def open_visual_words_a1():
            regular_pkgs = [p[0] for p in get_word_packages() if not p[0].startswith("szl_") and p[0] != "sozluk_tumu"]
            if regular_pkgs:
                self.current_cat_id = regular_pkgs[0]
            self.word_level_var.set("A1")
            self._switch_mode("words")

        def open_sarf_words_in_card():
            self.current_cat_id = "sozluk_tumu"
            self._switch_mode("words")
            self.cat_combo.set("📖 Sarf Sözlüğü (Tüm Kelimeler)")

        def open_daily_15_srs():
            self.current_cat_id = "gunluk_tekrar"
            self._switch_mode("words")
            self.cat_combo.set("📅 Günün 15 Tekrarı")

        card_wrap = 380 if self.root.winfo_width() < 1260 else 430

        cards_spec = [
            {
                "row": 0, "col": 0,
                "icon": "🏠",
                "title": "1. Görselli Kelime Öğren & Konuş",
                "stat": f"📦 {counts['visual_word_pkgs']} Temel Paket  •  🖼️ {counts['visual_words']} Görselli Kelime Kartı",
                "desc": "Yeni başlayanlar için görsel destekli kelimeler, kadın/erkek telaffuzu, canlı mahreç koçu ve gölgeleme antrenmanı.",
                "btn1_txt": "🚀 Hemen Kelime Öğrenmeye Başla (A1)",
                "btn1_col": BLUE,
                "btn1_fg": "white",
                "btn1_cmd": open_visual_words_a1,
                "btn2_txt": "📅 Günün 15 Akıllı Tekrarı (SM-2)",
                "btn2_cmd": open_daily_15_srs
            },
            {
                "row": 0, "col": 1,
                "icon": "📖",
                "title": "2. Sarf Sözlüğü & Kelime Kökleri",
                "stat": f"🌿 {counts['sozluk_entries']} Sözlük Maddesi  •  🏷️ {counts['sozluk_categories']} Tematik Kategori",
                "desc": "Kelimelerin 3 harfli köklerini (Sülâsî), tekil/çoğul (Müfret/Cemî) ve geçmiş/şimdiki zaman (Mâzî/Muzâri) çekimlerini öğrenin.",
                "btn1_txt": "🎤 Sözlük Kartlarıyla Telaffuz Çalış",
                "btn1_col": "#0d9488",
                "btn1_fg": "white",
                "btn1_cmd": open_sarf_words_in_card,
                "btn2_txt": "🔍 Kök Ailesi Keşif Atölyesini Aç",
                "btn2_cmd": lambda: open_dictionary_explorer_window(self)
            },
            {
                "row": 1, "col": 0,
                "icon": "💬",
                "title": "3. Günlük Diyaloglar & Rol Yapma",
                "stat": f"🎭 {counts['dialogues']} Günlük Diyalog  •  💬 {counts['dialogue_lines']} Konuşma Satırı",
                "desc": "Günlük hayat diyaloglarını dinleyin; ardından bir rol seçip uygulama ile karşılıklı Arapça konuşma canlandırması yapın.",
                "btn1_txt": "💬 Diyalogları Dinle ve Çalış",
                "btn1_col": ACCENT,
                "btn1_fg": "white",
                "btn1_cmd": lambda: self._switch_mode("dialogue"),
                "btn2_txt": "🎭 Karşılıklı Canlandırma (Roleplay)",
                "btn2_cmd": lambda: (self._switch_mode("dialogue"), self._switch_dlg_submode("roleplay"))
            },
            {
                "row": 1, "col": 1,
                "icon": "📜",
                "title": "4. Anahtar Cümleler & Sesli Sınav",
                "stat": f"📜 {counts['sentences']} Cümle (A1-C1)  •  🎧 4 Şıklı Dinleme Sınavı",
                "desc": "Kelime kelime renkli doğruluk analiziyle cümle telaffuzu çalışın veya 10/20/50 soruluk Dinle & Bul sınavına girin.",
                "btn1_txt": "📜 Anahtar Cümle Moduna Başla",
                "btn1_col": GREEN,
                "btn1_fg": "#0f172a",
                "btn1_cmd": lambda: self._switch_mode("sentences"),
                "btn2_txt": "🎧 Dinle & Bul Sesli Sınav Merkezi",
                "btn2_cmd": lambda: open_listening_warmup_window(self, "words")
            }
        ]

        for spec in cards_spec:
            card_box = tk.Frame(
                grid_f, bg=c["CARD_COLOR"], padx=20, pady=14,
                highlightthickness=1, highlightbackground=c["BORDER"]
            )
            card_box.grid(row=spec["row"], column=spec["col"], sticky="nsew", padx=6, pady=6)

            top_r = tk.Frame(card_box, bg=c["CARD_COLOR"])
            top_r.pack(fill="x")
            tk.Label(
                top_r, text=spec["icon"], bg=c["CARD_COLOR"], fg=c["TEXT_AR"],
                font=("Segoe UI", 26, "bold")
            ).pack(side="left", padx=(0, 10))

            title_col = tk.Frame(top_r, bg=c["CARD_COLOR"])
            title_col.pack(side="left", fill="x", expand=True)
            tk.Label(
                title_col, text=spec["title"], bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"],
                font=("Segoe UI", 12, "bold"), anchor="w"
            ).pack(fill="x")
            tk.Label(
                title_col, text=spec["stat"], bg=c["CARD_COLOR"],
                fg=GREEN if is_dark else "#047857", font=("Segoe UI", 9, "bold"), anchor="w"
            ).pack(fill="x", pady=(2, 0))

            tk.Label(
                card_box, text=spec["desc"], bg=c["CARD_COLOR"], fg=c["TEXT_DIM"],
                font=("Segoe UI", 9), justify="left", wraplength=card_wrap
            ).pack(anchor="w", fill="x", expand=True, pady=(8, 8))

            btn_f = tk.Frame(card_box, bg=c["CARD_COLOR"])
            btn_f.pack(fill="x", side="bottom")

            tk.Button(
                btn_f, text=spec["btn1_txt"], command=spec["btn1_cmd"],
                bg=spec["btn1_col"], fg=spec["btn1_fg"], font=("Segoe UI", 10, "bold"),
                relief="flat", pady=6, cursor="hand2"
            ).pack(fill="x", pady=(0, 4))

            tk.Button(
                btn_f, text=spec["btn2_txt"], command=spec["btn2_cmd"],
                bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"),
                relief="flat", pady=5, cursor="hand2"
            ).pack(fill="x")

        # 4. Alt Şerit: 👨‍🏫 Öğretmen, Yazılı Sınav, Ders Notu ve Rapor Araçları (Ayarlardan Gizlenebilir)
        if self.show_teacher_bar_var.get():
            teacher_bar = tk.Frame(
                self.home_container, bg=c["PANEL_COLOR"], padx=16, pady=7,
                highlightthickness=1, highlightbackground=c["BORDER"]
            )
            teacher_bar.pack(fill="x", pady=(6, 0))

            tk.Label(
                teacher_bar,
                text="👨‍🏫 Öğretmen, Yazılı Sınav, Ders Notu & Yönetim Araçları:",
                bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 9, "bold")
            ).pack(side="left")

            tk.Button(
                teacher_bar, text="🛠️ Yönetim & Paket Paneli",
                command=lambda: open_unified_admin_panel(self, "words"),
                bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"),
                relief="flat", padx=10, pady=3, cursor="hand2"
            ).pack(side="right", padx=(4, 0))

            tk.Button(
                teacher_bar, text="📊 Gelişim Karnesi & Rozetler",
                command=lambda: open_analytics_and_reports_window(self),
                bg=PURPLE, fg="white", font=("Segoe UI", 9, "bold"),
                relief="flat", padx=10, pady=3, cursor="hand2"
            ).pack(side="right", padx=4)

            tk.Button(
                teacher_bar, text="🖨️ A4 Yazılı Sınav & Ders Notu (Handout) Üret",
                command=lambda: open_printable_worksheet_generator(self.root, self, "words"),
                bg=YELLOW, fg="#0f172a", font=("Segoe UI", 9, "bold"),
                relief="flat", padx=12, pady=3, cursor="hand2"
            ).pack(side="right", padx=4)

    # =========================================================================
    # 🌿 SARF SÖZLÜĞÜ: ALTIGEN KÖK ROZETİ + TOOLTIP DESTEKLİ TÜREV AĞACI
    # =========================================================================
    def _render_word_morphology_bar(self, morph_data: dict):
        for w in self.morph_panel_w.winfo_children():
            w.destroy()

        c = self.colors
        is_dark = (self.theme_mode == "dark")
        card_bg = c["CARD_COLOR"]
        panel_bg = c["PANEL_COLOR"]
        border_col = c["BORDER"]

        outer = tk.Frame(
            self.morph_panel_w, bg=panel_bg, padx=14, pady=7,
            highlightthickness=1, highlightbackground=border_col
        )
        outer.pack()

        cv_hex = tk.Canvas(outer, width=114, height=92, bg=panel_bg, highlightthickness=0, cursor="hand2")
        cv_hex.pack(side="left", padx=(0, 14))
        self._attach_tooltip(cv_hex, _TERM_TOOLTIPS["SÜLÂSÎ KÖK"])

        cx, cy, r = 57, 42, 36
        pts = []
        for i in range(6):
            ang = math.radians(60 * i - 30)
            pts.extend([cx + r * math.cos(ang), cy + r * math.sin(ang)])
        hex_fill = "#1e3a8a" if is_dark else "#dbeafe"
        hex_outline = "#38bdf8" if is_dark else "#2563eb"
        cv_hex.create_polygon(pts, fill=hex_fill, outline=hex_outline, width=2)
        kok_txt = morph_data.get("kok", "—")
        kok_sz = 16 if len(kok_txt) <= 5 else 13
        cv_hex.create_text(
            cx, cy - 3, text=kok_txt,
            fill=YELLOW if is_dark else "#1e3a8a",
            font=(AR_FONT_FAMILY, kok_sz, "bold"), width=74, justify="center"
        )
        cv_hex.create_text(
            cx, cy + 15, text="SÜLÂSÎ KÖK",
            fill="#93c5fd" if is_dark else "#1d4ed8", font=("Segoe UI", 6, "bold")
        )
        cv_hex.create_text(
            18, 15, text=morph_data.get("emoji", "📘"), font=("Segoe UI", 15)
        )
        cv_hex.create_text(
            cx, 85, text="🔍 Kök Ailesi",
            fill=c["TEXT_DIM"], font=("Segoe UI", 7, "underline")
        )
        cv_hex.bind("<Button-1>", lambda e, k=kok_txt: open_dictionary_explorer_window(self, k))

        right_col = tk.Frame(outer, bg=panel_bg)
        right_col.pack(side="left", fill="both", expand=True)

        hdr_row = tk.Frame(right_col, bg=panel_bg)
        hdr_row.pack(fill="x", pady=(0, 3))
        tur_badge = "🏷️ İSİM (Tekil & Çoğul)" if morph_data.get("tur") == "isim" else "⚡ FİİL (Geçmiş • Şimdiki • Masdar)"
        tk.Label(
            hdr_row, text=f"{morph_data.get('kategori', 'Genel')}   |   {tur_badge}",
            bg=panel_bg, fg=c["ACCENT_HEAD"], font=("Segoe UI", 9, "bold")
        ).pack(side="left")
        tk.Label(
            hdr_row, text="(Telaffuz edeceğiniz forma tıklayın 👇)",
            bg=panel_bg, fg=c["TEXT_DIM"], font=("Segoe UI", 8, "italic")
        ).pack(side="right", padx=(10, 0))

        forms_row = tk.Frame(right_col, bg=panel_bg)
        forms_row.pack(fill="x", pady=2)

        active_ar = self._active_morph_ar or (self.current_items[self.current_index].arapca if self.current_items else "")
        for f_obj in morph_data.get("forms", []):
            f_ar = f_obj["arabic"]
            f_lbl = f_obj["label"]
            is_selected = (f_ar == active_ar)
            btn_bg = GREEN if is_selected else card_bg
            btn_fg = "#0f172a" if is_selected else c["TEXT_AR"]
            bw = 2 if is_selected else 1
            b_col = GREEN if is_selected else f_obj.get("color", border_col)

            f_card = tk.Frame(
                forms_row, bg=btn_bg, padx=10, pady=2,
                highlightthickness=bw, highlightbackground=b_col, cursor="hand2"
            )
            f_card.pack(side="left", padx=4)

            l1 = tk.Label(
                f_card, text=f_lbl, bg=btn_bg,
                fg="#0f172a" if is_selected else c["TEXT_DIM"],
                font=("Segoe UI", 8, "bold"), cursor="hand2"
            )
            l1.pack()
            l2 = tk.Label(
                f_card, text=shape_arabic(f_ar), bg=btn_bg, fg=btn_fg,
                font=(AR_FONT_FAMILY, 14, "bold"), cursor="hand2"
            )
            l2.pack()

            tip_txt = _TERM_TOOLTIPS.get(f_lbl, f"{f_lbl} formunu dinlemek ve telaffuz etmek için tıklayın.")
            for w_click in (f_card, l1, l2):
                w_click.bind(
                    "<Button-1>",
                    lambda e, ar_v=f_ar, lb_v=f_lbl, m_d=morph_data: self._select_morph_form_on_card(ar_v, lb_v, m_d)
                )
                self._attach_tooltip(w_click, tip_txt)

        fam_list = morph_data.get("family", [])
        if fam_list:
            fam_str = "  •  ".join([f"{f['arabic']} ({f['anlam_tr'].split(',')[0][:18]})" for f in fam_list[:3]])
            fam_lbl = tk.Label(
                right_col, text=f"🌿 Kök Ailesi:  {fam_str}",
                bg=panel_bg, fg=c["TEXT_DIM"], font=("Segoe UI", 8, "bold"), cursor="hand2"
            )
            fam_lbl.pack(anchor="w", pady=(3, 0))
            fam_lbl.bind("<Button-1>", lambda e, k=kok_txt: open_dictionary_explorer_window(self, k))

    def _select_morph_form_on_card(self, form_ar: str, form_label: str, morph_data: dict):
        self._active_morph_ar = form_ar
        self._active_morph_label = form_label
        self._render_word_morphology_bar(morph_data)

        display_ar = form_ar if self.tashkeel_var.get() else remove_diacritics(form_ar)
        if self.test_mode_var.get():
            self.arabic_label.config(text="❓ ❓ ❓", fg=self.colors["TEXT_DIM"])
        else:
            self.arabic_label.config(text=shape_arabic(display_ar), fg=self.colors["TEXT_AR"])

        item = self.current_items[self.current_index]
        if self.word_show_tr.get():
            self.turkish_label.config(text=f"[{form_label}]  {item.turkce}", fg=self.colors["TEXT_MAIN"])

        self._update_char_analysis(form_ar, "")
        self._play_tts_text_async(expand_arabic_variants(form_ar)[0], "ar-SA-HamedNeural")

    def _get_active_word_expected_arabic(self) -> str:
        if self.current_mode == "words" and self._active_morph_ar:
            return self._active_morph_ar
        item = self._current_item()
        return getattr(item, "arapca", "") if item else ""

    def _on_card_word_level_change(self, event=None):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        new_lvl = self.card_word_lvl_var.get().strip().upper()
        item.seviye = new_lvl
        update_word_meta(item.id, seviye=new_lvl)
        self.counter_label.config(text=f"[{new_lvl}]  {self.current_index + 1} / {len(self.current_items)}")
        self._show_toast(f"🏷️ '{item.turkce}' kelimesinin seviyesi {new_lvl} olarak kaydedildi!", GREEN, 1800)

    def _on_card_sent_level_change(self, event=None):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        new_lvl = self.card_sent_lvl_var.get().strip().upper()
        item.seviye = new_lvl
        update_sentence_meta(item.id, seviye=new_lvl)
        self.sent_counter_label.config(text=f"[{new_lvl}]  {self.current_index + 1} / {len(self.current_items)}")
        self._show_toast(f"🏷️ Cümle seviyesi {new_lvl} olarak kaydedildi!", GREEN, 1800)

    def _open_word_shadowing(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        target_ar = self._get_active_word_expected_arabic()
        primary_tts = expand_arabic_variants(target_ar)[0]
        open_shadowing_trainer_window(self, primary_tts, item.turkce, item.ses if target_ar == item.arapca else None, "ar-SA-HamedNeural", str(item.id))

    def _open_sentence_shadowing(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        open_shadowing_trainer_window(self, item.arapca, item.turkce, item.ses, "ar-SA-HamedNeural", str(item.id))

    def _on_word_level_change(self, event=None):
        self._stop_all_audio(include_effects=True)
        self.recent_attempt_history.clear()
        self._load_category_items(play_audio=False)

    def _update_recordings_btn_label(self):
        cnt, mb = get_recordings_stats()
        if hasattr(self, "btn_clean_recs"):
            self.btn_clean_recs.config(text=f"🧹 Kayıtları Temizle ({cnt} dosya, {mb} MB)")

    def _manual_clean_recordings(self):
        cnt, mb = get_recordings_stats()
        if cnt == 0:
            self._show_toast("Temizlenecek ses kaydı yok.", YELLOW)
            return
        if messagebox.askyesno("Ses Kayıtlarını Temizle", f"recordings/ klasöründeki {cnt} adet ses kaydını ({mb} MB) silmek istediğinize emin misiniz?"):
            deleted = cleanup_recordings_folder(keep_latest=0)
            self.current_recording_path = None
            self.btn_play_own_word.config(state="disabled")
            self.btn_play_own_sent.config(state="disabled")
            self._update_recordings_btn_label()
            self._show_toast(f"{deleted} ses kaydı temizlendi!", GREEN)

    def _reset_current_stats(self):
        target_m = self.current_mode if self.current_mode != "home" else "words"
        if messagebox.askyesno("İstatistikleri Sıfırla", "Bu moda ait Doğru/Yanlış istatistiklerini ve SM-2 tekrar verilerini sıfırlamak istediğinize emin misiniz?"):
            reset_stats(target_m)
            self.recent_attempt_history.clear()
            self._update_stats_ui()
            self._show_toast("İstatistikler sıfırlandı.", GREEN)

    def _on_chat_canvas_configure(self, event):
        self.chat_canvas.itemconfig(self.chat_window, width=event.width)
        if abs(event.width - self._last_chat_width) > 40:
            self._last_chat_width = event.width
            if self.current_mode == "dialogue":
                self._refresh_chat()

    def _switch_mode(self, mode):
        self._close_active_singleton_dialog()
        self._stop_all_audio(include_effects=True)
        if self.roleplay_active:
            self._stop_roleplay_session()
        self.current_mode = mode
        if mode in ("words", "dialogue", "sentences"):
            set_config_val("last_active_mode", mode)
        self._custom_eval_target = None
        self.recent_attempt_history.clear()
        self.home_container.pack_forget()
        self.words_container.pack_forget()
        self.chat_container.pack_forget()
        self.sentences_container.pack_forget()

        self._update_mode_buttons_style()

        if mode == "home":
            self.home_container.pack(fill="both", expand=True)
            self._render_home_dashboard()
            self._update_char_analysis("", "")
        elif mode == "words":
            self.words_container.pack(fill="both", expand=True)
            self._load_category_items(play_audio=False)
        elif mode == "dialogue":
            self.chat_container.pack(fill="both", expand=True)
            self._load_dialogues_ui()
        else:
            self.sentences_container.pack(fill="both", expand=True)
            self._load_sentences_ui(reset_index=False)
        self._update_stats_ui()

    def _change_word_font_size(self, delta):
        self.word_ar_size = max(24, min(68, self.word_ar_size + delta))
        self.word_tr_size = max(12, min(34, self.word_tr_size + delta))
        self._show_card(play_audio=False)

    def _change_chat_font_size(self, delta):
        self.chat_font_size = max(12, min(36, self.chat_font_size + delta))
        self._refresh_chat()

    def _change_font_size(self, delta):
        self.sent_ar_size = max(18, min(54, self.sent_ar_size + delta))
        self.sent_tr_size = max(11, min(32, self.sent_tr_size + delta))
        self.sent_turkish_label.config(font=("Segoe UI", self.sent_tr_size))
        self._show_sentence_card()

    def _toggle_word_fav(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        item.is_fav = 0 if getattr(item, "is_fav", 0) else 1
        update_word_meta(item.id, is_fav=item.is_fav)
        self._show_card(play_audio=False)

    def _toggle_word_learned(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        item.is_learned = 0 if getattr(item, "is_learned", 0) else 1
        update_word_meta(item.id, is_learned=item.is_learned)
        self._show_card(play_audio=False)
        self._update_stats_ui()

    def _toggle_fav(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        item.is_fav = 0 if item.is_fav else 1
        update_sentence_meta(item.id, is_fav=item.is_fav)
        self._show_sentence_card()

    def _toggle_learned(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        item.is_learned = 0 if item.is_learned else 1
        update_sentence_meta(item.id, is_learned=item.is_learned)
        self._show_sentence_card()
        self._update_stats_ui()

    def _first_sent(self):
        if self.recording or not self.current_items:
            return
        self._stop_all_audio(include_effects=False)
        self.current_index = 0
        self._save_last_index()
        self._show_sentence_card()

    def _last_sent(self):
        if self.recording or not self.current_items:
            return
        self._stop_all_audio(include_effects=False)
        self.current_index = len(self.current_items) - 1
        self._save_last_index()
        self._show_sentence_card()

    def _jump_to_index(self):
        if self.recording or not self.current_items:
            return
        try:
            idx = int(self.jump_entry.get()) - 1
            if 0 <= idx < len(self.current_items):
                self._stop_all_audio(include_effects=False)
                self.current_index = idx
                self._save_last_index()
                self._show_sentence_card()
                self.root.focus_set()
        except ValueError:
            pass

    # ================== CÜMLE MODU ==================
    def _load_sentences_ui(self, reset_index=False):
        filter_val = self.sent_filter_var.get()
        lvl_val = self.sent_level_var.get()
        f_type = {
            "📅 Günün 15 Tekrarı": "daily_review",
            "Favoriler": "fav",
            "Öğrendiklerim": "learned",
            "Zorlandıklarım": "difficult"
        }.get(filter_val, "all")
        self.current_items = get_key_sentences(f_type, seviye=lvl_val)
        if self.random_order_var.get():
            random.shuffle(self.current_items)

        if reset_index or f_type == "daily_review" or lvl_val != "Tümü":
            self.current_index = 0
        else:
            try:
                self.current_index = int(get_config_val("last_sent_index", "0"))
            except ValueError:
                self.current_index = 0

        if self.current_index >= len(self.current_items):
            self.current_index = 0

        self._show_sentence_card()

    def _save_last_index(self):
        if self.sent_filter_var.get() == "Tümü" and self.sent_level_var.get() == "Tümü":
            set_config_val("last_sent_index", self.current_index)

    def _render_sentence_arabic_area(self, word_status_list=None):
        card_bg = self.colors["CARD_COLOR"]
        ar_fg = self.colors["TEXT_AR"]

        for w in self.sent_ar_holder.winfo_children():
            w.destroy()

        if not self.current_items:
            tk.Label(self.sent_ar_holder, text="Seçili filtre/seviyede cümle bulunamadı.", bg=card_bg, fg=self.colors["TEXT_DIM"], font=("Segoe UI", 16, "bold")).pack()
            return

        item = self.current_items[self.current_index]
        display_ar = item.arapca if self.sent_show_tashkeel.get() else remove_diacritics(item.arapca)

        if word_status_list is not None:
            wrap = tk.Frame(self.sent_ar_holder, bg=card_bg)
            wrap.pack()
            ok_col = GREEN if self.theme_mode == "dark" else "#047857"
            for w_text, is_ok in word_status_list:
                disp_w = w_text if self.sent_show_tashkeel.get() else remove_diacritics(w_text)
                fg_col = ok_col if is_ok else (ar_fg if w_text == "❓" else RED)
                tk.Label(
                    wrap, text=f"\u200f{disp_w}\u200f", bg=card_bg, fg=fg_col,
                    font=(AR_FONT_FAMILY, self.sent_ar_size, "bold"), padx=4
                ).pack(side="right")
            return

        if self.sent_hide_ar_var.get() or self.test_mode_var.get():
            tk.Label(
                self.sent_ar_holder,
                text="🔒 [ Arapça Cümle Gizli — Söyleyin veya '🔊 Dinle'ye Basın ]",
                bg=card_bg, fg=ar_fg, font=("Segoe UI", 16, "bold")
            ).pack()
        else:
            card_w = self.sentence_card.winfo_width()
            max_px = (card_w - 160) if card_w > 300 else 740
            sent_font_obj = tkfont.Font(family=AR_FONT_FAMILY, size=self.sent_ar_size, weight="bold")
            tk.Label(
                self.sent_ar_holder,
                text=shape_arabic_by_pixels(display_ar, sent_font_obj, max_px),
                bg=card_bg, fg=ar_fg, font=(AR_FONT_FAMILY, self.sent_ar_size, "bold"), justify="center"
            ).pack()

    def _show_sentence_card(self):
        self._set_recording_glow(False)
        self.sent_canvas.delete("confetti")
        self.sent_wave_canvas.delete("wave")
        self.sent_prompt_label.place_forget()
        self.attempt_count = 0
        self.sent_word_mask = []
        self.current_recording_path = None
        self.btn_play_own_sent.config(state="disabled", bg=self.colors["BTN_SEC"], fg=self.colors["TEXT_DIM"])

        if not self.current_items:
            self._render_sentence_arabic_area()
            self.sent_turkish_label.config(text="Seviye veya filtre seçimini değiştirerek cümleleri görüntüleyebilirsiniz.")
            self.sent_counter_label.config(text="0 / 0")
            self.btn_sent_record.config(state="disabled")
            return

        self.btn_sent_record.config(state="normal")
        if self.current_index >= len(self.current_items):
            self.current_index = 0
        item = self.current_items[self.current_index]

        self.btn_fav.config(
            text="⭐ Favori" if item.is_fav else "☆ Favori",
            bg=YELLOW if item.is_fav else self.colors["BTN_SEC"],
            fg="#0f172a" if item.is_fav else self.colors["TEXT_MAIN"]
        )
        self.btn_learned.config(
            text="✅ Öğrendim" if item.is_learned else "☑️ Öğrendim",
            bg=GREEN if item.is_learned else self.colors["BTN_SEC"],
            fg="#0f172a" if item.is_learned else self.colors["TEXT_MAIN"]
        )
        s_lvl = getattr(item, "seviye", "A1") or "A1"
        self.card_sent_lvl_var.set(s_lvl if s_lvl in ("A1", "A2", "B1", "B2", "C1") else "A1")

        self._render_sentence_arabic_area()

        self.sent_counter_label.config(text=f"[{s_lvl}]  {self.current_index + 1} / {len(self.current_items)}")
        self.jump_entry.delete(0, tk.END)
        self.jump_entry.insert(0, str(self.current_index + 1))

        self.sent_turkish_label.config(text=item.turkce if self.sent_show_tr.get() else "", fg=self.colors["TEXT_MAIN"])
        self.sent_rec_status.config(text="", cursor="")
        self.sent_verdict_label.config(text="🎤 Telaffuz değerlendirmesi için kayıt yapın", fg=self.colors["TEXT_DIM"])
        self._update_char_analysis(item.arapca, "")

    def _update_sentence_ui(self):
        self._show_sentence_card()

    def _play_sentence_tts(self):
        if not self.current_items:
            return
        item = self.current_items[self.current_index]
        local_snd = resolve_local_media_path(item.ses)
        if local_snd:
            self._play_audio_file_async(local_snd)
            return

        pref = self.voice_pref_var.get()
        if pref == "Kadın":
            voice = "ar-SA-ZariyahNeural"
        elif pref == "Erkek":
            voice = "ar-SA-HamedNeural"
        else:
            self._alt_sent_voice = getattr(self, "_alt_sent_voice", "ar-SA-HamedNeural")
            voice = self._alt_sent_voice
            self._alt_sent_voice = "ar-SA-ZariyahNeural" if voice == "ar-SA-HamedNeural" else "ar-SA-HamedNeural"

        self._play_tts_text_async(item.arapca, voice=voice)

    def _play_audio_file_async(self, audio_path: str):
        self._stop_all_audio(include_effects=False)
        session_token = self._audio_session_id

        def worker():
            if session_token != self._audio_session_id or not os.path.exists(audio_path):
                return
            if not ensure_mixer_initialized():
                return
            try:
                with audio_lock:
                    if pygame.mixer.music.get_busy():
                        pygame.mixer.music.stop()
                    pygame.mixer.music.load(audio_path)
                    pygame.mixer.music.play()
            except Exception as ex:
                log_error(f"Ses dosyası çalma hatası ({audio_path})", ex)

        threading.Thread(target=worker, daemon=True).start()

    def _play_tts_text_async(self, text, voice="ar-SA-HamedNeural", local_path=None):
        self._stop_all_audio(include_effects=False)
        session_token = self._audio_session_id
        slow_mode = self.slow_tts_var.get()
        resolved = resolve_local_media_path(local_path) if local_path else None
        primary_text = expand_arabic_variants(text)[0] if text else ""

        def worker():
            audio_path = resolved
            if not audio_path:
                self._safe_ui(lambda: self._show_toast("Seslendiriliyor...", YELLOW, 1500))
                audio_path = get_tts_audio(primary_text, voice=voice, slow=slow_mode)
            if audio_path and os.path.exists(audio_path) and session_token == self._audio_session_id:
                if not ensure_mixer_initialized():
                    return
                try:
                    with audio_lock:
                        if pygame.mixer.music.get_busy():
                            pygame.mixer.music.stop()
                        pygame.mixer.music.load(audio_path)
                        pygame.mixer.music.play()
                except Exception as ex:
                    log_error(f"TTS ses çalma hatası ({audio_path})", ex)

        threading.Thread(target=worker, daemon=True).start()

    # ================== KELİME MODU & SES KAYIT MOTORU ==================
    def _update_stats_ui(self):
        stat_m = self.current_mode if self.current_mode != "home" else "words"
        mode_map = {"words": "KELİME İSTATİSTİKLERİ", "dialogue": "DİYALOG İSTATİSTİKLERİ", "sentences": "CÜMLE İSTATİSTİKLERİ"}
        self.lbl_stat_title.config(text=mode_map.get(stat_m, "İSTATİSTİKLER"))

        c, w = get_stats(stat_m)
        a = int((c / (c + w)) * 100) if (c + w) > 0 else 0
        self.lbl_correct.config(text=f"Doğru: {c}")
        self.lbl_wrong.config(text=f"Yanlış: {w}")
        self.lbl_acc.config(text=f"Başarı: %{a}")

        try:
            b_info = get_streak_and_badges()
            self.lbl_streak_info.config(
                text=f"🔥 Seri: {b_info['streak']} Gün | 🎯 Bugün: {b_info['daily_count']}/{b_info['daily_goal']}"
            )
            active_pkg = self.current_cat_id if stat_m == "words" else (self._get_selected_dialogue_id() if stat_m == "dialogue" else None)
            comp_info = get_package_completion_info(stat_m, active_pkg)
            self.lbl_pkg_prog.config(
                text=f"📦 Tamamlanan: {comp_info['learned']}/{comp_info['total']} (%{comp_info['pct']})"
            )
        except Exception as ex:
            log_error("İstatistik arayüzü güncelleme hatası", ex)

    def _show_toast(self, msg, color=YELLOW, duration=2000):
        self.toast_label.config(text=msg, bg=color, fg=BG_COLOR if color in (YELLOW, GREEN) else TEXT_LIGHT)
        self.toast_label.place(relx=0.5, rely=0.04, anchor="center")
        self.toast_label.lift()
        self.root.after(duration, lambda: self.toast_label.place_forget())

    def _load_category_items(self, play_audio=False):
        lvl = self.word_level_var.get()
        self.word_packages = get_word_packages()
        self.cat_combo['values'] = [c[1] for c in self.word_packages]
        for cid, cname in self.word_packages:
            if cid == self.current_cat_id:
                self.cat_combo.set(cname)
                break
        set_config_val("last_word_cat_id", self.current_cat_id)
        self.current_items = get_words(self.current_cat_id, seviye=lvl)
        if self.random_order_var.get():
            random.shuffle(self.current_items)
        self.current_index = 0
        self.attempt_count = 0
        self._show_card(play_audio=play_audio)
        self._update_stats_ui()

    def _select_special_cat(self, cat_id):
        self._stop_all_audio(include_effects=True)
        self.recent_attempt_history.clear()
        self.current_cat_id = cat_id
        if cat_id == "gunluk_tekrar":
            self.cat_combo.set("📅 Günün 15 Tekrarı")
        elif cat_id == "tekrar":
            self.cat_combo.set("⭐ Tekrar Listem")
        else:
            self.cat_combo.set("🎯 Zayıf Kelimeler")
        self._load_category_items(play_audio=False)

    def _on_cat_combo_change(self, event):
        self._stop_all_audio(include_effects=True)
        self.recent_attempt_history.clear()
        selected_name = self.cat_combo.get()
        for cid, cname in self.word_packages:
            if cname == selected_name:
                self.current_cat_id = cid
                break
        self._load_category_items(play_audio=False)

    def _current_item(self):
        if self._custom_eval_target is not None:
            return self._custom_eval_target
        if not self.current_items:
            return None
        return self.current_items[self.current_index]

    def _show_card(self, play_audio=False):
        self._set_recording_glow(False)
        self.canvas.delete("confetti")
        self.confetti_particles.clear()
        self.wave_canvas.delete("wave")
        self.prompt_label.place_forget()
        self.btn_pass.pack_forget()
        self.attempt_count = 0
        self.attempt_label.config(text="")
        self.current_recording_path = None
        self._active_morph_ar = None
        self._active_morph_label = ""
        self.btn_play_own_word.config(state="disabled", bg=self.colors["BTN_SEC"], fg=self.colors["TEXT_DIM"])

        if not self.current_items:
            self.image_label.place_forget()
            self.morph_panel_w.place_forget()
            self.arabic_label.place(relx=0.5, rely=0.32, anchor="center")
            self.turkish_label.place(relx=0.5, rely=0.46, anchor="center")
            self.arabic_label.config(text="🎉", font=FONT_AR)
            self.turkish_label.config(text="Bu pakette seçili seviyede kelime bulunamadı (Seviyeyi 'Tümü' yapabilirsiniz).")
            self.counter_label.config(text="0 / 0")
            self.verdict_label.config(text="")
            self.detail_label.config(text="")
            self.btn_record.config(state="disabled")
            self.btn_word_fav.config(text="☆ Tekrar Listeme Ekle", bg=self.colors["BTN_SEC"], fg=self.colors["TEXT_MAIN"])
            self.btn_word_learned.config(text="☑️ Öğrendim", bg=self.colors["BTN_SEC"], fg=self.colors["TEXT_MAIN"])
            return

        self.btn_record.config(state="normal")
        self.verdict_label.config(text="🎤 Telaffuz için 'BAŞLA VE KONUŞ'a veya [Boşluk] tuşuna basın", fg=self.colors["TEXT_DIM"])
        self.detail_label.config(text="")

        if not self.transcribing and self.model is not None:
            self.rec_status.config(text="", cursor="")
        if self.current_index >= len(self.current_items):
            self.current_index = 0
        item = self.current_items[self.current_index]

        is_fav = getattr(item, "is_fav", 0)
        is_lrn = getattr(item, "is_learned", 0)
        w_lvl = getattr(item, "seviye", "A1") or "A1"
        self.card_word_lvl_var.set(w_lvl if w_lvl in ("A1", "A2", "B1", "B2", "C1") else "A1")

        self.btn_word_fav.config(
            text="⭐ Tekrar Listemde" if is_fav else "☆ Tekrar Listeme Ekle",
            bg=YELLOW if is_fav else self.colors["BTN_SEC"],
            fg="#0f172a" if is_fav else self.colors["TEXT_MAIN"]
        )
        self.btn_word_learned.config(
            text="✅ Öğrendim" if is_lrn else "☑️ Öğrendim",
            bg=GREEN if is_lrn else self.colors["BTN_SEC"],
            fg="#0f172a" if is_lrn else self.colors["TEXT_MAIN"]
        )

        img_full = resolve_local_media_path(item.gorsel)
        has_valid_image = False
        if img_full:
            try:
                img = Image.open(img_full)
                img.thumbnail((215, 145), Image.LANCZOS)
                self.current_photo = ImageTk.PhotoImage(img)
                self.image_label.config(image=self.current_photo, text="")
                has_valid_image = True
            except Exception as ex:
                log_error(f"Görsel yükleme hatası ({img_full})", ex)
                self.current_photo = None

        morph_info = None
        if not has_valid_image:
            morph_info = get_word_morphology_and_family(item.id) or get_word_morphology_and_family(item.arapca)

        if has_valid_image:
            self.morph_panel_w.place_forget()
            self.image_label.place(relx=0.5, rely=0.20, anchor="center")
            self.arabic_label.place(relx=0.5, rely=0.40, anchor="center")
            self.turkish_label.place(relx=0.5, rely=0.51, anchor="center")
            self.arabic_label.config(font=(AR_FONT_FAMILY, self.word_ar_size, "bold"))
            self.turkish_label.config(font=("Segoe UI", self.word_tr_size, "bold"))
        elif morph_info:
            self.image_label.place_forget()
            self._active_morph_ar = item.arapca
            self._render_word_morphology_bar(morph_info)
            self.morph_panel_w.place(relx=0.5, rely=0.21, anchor="center")
            self.arabic_label.place(relx=0.5, rely=0.41, anchor="center")
            self.turkish_label.place(relx=0.5, rely=0.51, anchor="center")
            self.arabic_label.config(font=(AR_FONT_FAMILY, self.word_ar_size, "bold"))
            self.turkish_label.config(font=("Segoe UI", self.word_tr_size, "bold"))
        else:
            self.image_label.place_forget()
            self.morph_panel_w.place_forget()
            self.arabic_label.place(relx=0.5, rely=0.30, anchor="center")
            self.turkish_label.place(relx=0.5, rely=0.46, anchor="center")
            self.arabic_label.config(font=(AR_FONT_FAMILY, self.word_ar_size + 6, "bold"))
            self.turkish_label.config(font=("Segoe UI", self.word_tr_size + 2, "bold"))

        if self.word_show_tr.get():
            self.turkish_label.config(text=item.turkce, fg=self.colors["TEXT_MAIN"])
        else:
            self.turkish_label.config(text="", fg=self.colors["TEXT_DIM"])

        self.counter_label.config(text=f"[{w_lvl}]  {self.current_index + 1} / {len(self.current_items)}")

        display_ar = item.arapca if self.tashkeel_var.get() else remove_diacritics(item.arapca)

        if self.test_mode_var.get():
            self.arabic_label.config(text="❓ ❓ ❓", fg=self.colors["TEXT_DIM"])
        else:
            self.arabic_label.config(text=shape_arabic(display_ar), fg=self.colors["TEXT_AR"])

        self._update_char_analysis(item.arapca, "")

        if play_audio:
            if self.auto_start_var.get():
                self.root.after(400, self._start_auto_flow)
            elif self.auto_play_var.get() and not self.test_mode_var.get():
                self._play_reference_async(None)

    def _play_own_recording(self):
        if not self.current_recording_path or not os.path.exists(self.current_recording_path):
            self._show_toast("Dinlenecek kayıt bulunamadı.", RED)
            return
        self._play_audio_file_async(self.current_recording_path)

    def _play_reference_async(self, voice_type):
        self._stop_all_audio(include_effects=False)
        session_token = self._audio_session_id
        threading.Thread(target=self._play_reference_sync, args=(voice_type, session_token), daemon=True).start()

    def _play_reference_btn(self, voice_type):
        self._play_reference_async(voice_type)

    def _play_reference_sync(self, voice_type, session_token=None):
        if session_token is None:
            session_token = self._audio_session_id
        item = self._current_item()
        if not item:
            return

        if voice_type is None:
            pref = self.voice_pref_var.get()
            if pref == "Kadın":
                voice_type = "female"
            elif pref == "Erkek":
                voice_type = "male"
            else:
                self._alternate_voice = getattr(self, "_alternate_voice", "female")
                voice_type = self._alternate_voice
                self._alternate_voice = "male" if self._alternate_voice == "female" else "female"

        target_ar = self._get_active_word_expected_arabic()
        ses_rel = getattr(item, "ses", None) if target_ar == getattr(item, "arapca", "") else None
        path = None
        if ses_rel:
            if voice_type == "male" and "_f." in ses_rel:
                male_cand = resolve_local_media_path(ses_rel.replace("_f.", "_m."))
                if male_cand:
                    path = male_cand
            if not path:
                path = resolve_local_media_path(ses_rel)

        if not path:
            voice_name = "ar-SA-ZariyahNeural" if voice_type == "female" else "ar-SA-HamedNeural"
            primary_tts_text = expand_arabic_variants(target_ar)[0]
            path = get_tts_audio(primary_tts_text, voice=voice_name, slow=self.slow_tts_var.get())

        if path and os.path.exists(path) and session_token == self._audio_session_id:
            if not ensure_mixer_initialized():
                return
            repeat = self.repeat_count_var.get()
            for _ in range(repeat):
                if self._closing or session_token != self._audio_session_id:
                    break
                try:
                    with audio_lock:
                        if pygame.mixer.music.get_busy():
                            pygame.mixer.music.stop()
                        pygame.mixer.music.load(path)
                        pygame.mixer.music.play()
                    while pygame.mixer.music.get_busy() and not self._closing and session_token == self._audio_session_id:
                        time.sleep(0.04)
                    time.sleep(0.22)
                except Exception as ex:
                    log_error(f"Referans ses çalma hatası ({path})", ex)

    def _start_auto_flow(self):
        if self.recording:
            threading.Thread(target=self._stop_recording, daemon=True).start()
            return
        if self.transcribing:
            return

        self._stop_all_audio(include_effects=False)
        session_token = self._audio_session_id
        self._custom_eval_target = None
        item = self._current_item()
        if not item:
            return

        max_a = self.max_attempts_var.get()
        if max_a != "Sınırsız" and self.attempt_count >= int(max_a[0]):
            self._show_toast("Deneme hakkınız doldu! Lütfen sonraki karta ilerleyin.", RED)
            return

        self.attempt_count += 1
        if self.current_mode == "words":
            self.attempt_label.config(text=f"Deneme: {self.attempt_count} / {max_a}", fg=YELLOW if self.theme_mode == "dark" else "#1e3a8a")
            if max_a != "Sınırsız" and self.attempt_count >= int(max_a[0]):
                self.btn_pass.pack(side="left", padx=10)

        def flow():
            self._safe_ui(lambda: self.prompt_label.place_forget())
            self._safe_ui(lambda: self.sent_prompt_label.place_forget())

            if self.current_mode == "words" and self.auto_play_var.get():
                self._safe_ui(lambda: self._show_toast("Referans ses dinleniyor...", YELLOW, 1500))
                self._play_reference_sync(None, session_token)

            if session_token != self._audio_session_id or self._closing:
                return
            time.sleep(0.20)
            if self.current_mode == "words":
                self._safe_ui(lambda: self.rec_status.config(text="🔔 Bip!", fg=YELLOW))
            elif self.current_mode == "sentences":
                self._safe_ui(lambda: self.sent_rec_status.config(text="🔔 Bip!", fg=YELLOW))
            self._play_beep()

            time.sleep(0.20)
            if session_token != self._audio_session_id or self._closing:
                return
            self._safe_ui(lambda: self._show_toast("🎤 Şimdi konuşun!", GREEN, 1800))
            if self.current_mode == "words":
                self._safe_ui(lambda: self.prompt_label.config(text="🎤 ŞİMDİ KONUŞUN!", fg=GREEN if self.theme_mode == "dark" else "#047857"))
                self._safe_ui(lambda: self.prompt_label.place(relx=0.5, rely=0.11, anchor="center"))
            elif self.current_mode == "sentences":
                self._safe_ui(lambda: self.sent_prompt_label.config(text="🎤 ŞİMDİ KONUŞUN!", fg=GREEN if self.theme_mode == "dark" else "#047857"))
                self._safe_ui(lambda: self.sent_prompt_label.place(relx=0.5, rely=0.15, anchor="center"))

            self._safe_ui(lambda: self._start_recording())

        threading.Thread(target=flow, daemon=True).start()

    def _pass_word(self):
        if self.recording:
            return
        self._show_toast("Pas geçildi.", YELLOW)
        self._next()

    def _play_beep(self):
        play_beep_nonblocking()

    def _audio_callback(self, indata, frames, time_info, status):
        if self.recording:
            self.audio_frames.append(indata.copy())
            rms = np.sqrt(np.mean(np.square(indata)))
            self.wave_buffer.append(rms)
            if len(self.wave_buffer) > 20:
                self.wave_buffer.pop(0)
            self._safe_ui(self._draw_waveform)

            if rms > 0.02:
                if not self.speech_started:
                    self._safe_ui(lambda: self.prompt_label.place_forget())
                    self._safe_ui(lambda: self.sent_prompt_label.place_forget())
                self.speech_started = True
                self.silence_chunks = 0
            elif self.speech_started:
                self.silence_chunks += 1
                if self.silence_chunks > 65:
                    self.speech_started = False
                    self.silence_chunks = 0
                    self.recording = False
                    threading.Thread(target=self._stop_recording, daemon=True).start()
            else:
                self.silence_chunks += 1
                if self.silence_chunks > 180:
                    self.silence_chunks = 0
                    self.recording = False
                    self._safe_ui(lambda: self.prompt_label.place_forget())
                    self._safe_ui(lambda: self.sent_prompt_label.place_forget())
                    threading.Thread(target=self._stop_recording, daemon=True).start()

    def _draw_waveform(self):
        target_wave = self.sent_wave_canvas if self.current_mode == "sentences" else self.wave_canvas
        target_wave.delete("wave")
        bar_w, gap = 6, 4
        start_x = 100 - (len(self.wave_buffer) * (bar_w + gap)) // 2
        for i, rms in enumerate(self.wave_buffer):
            h = max(4, min(26, int(rms * 600)))
            x = start_x + i * (bar_w + gap)
            y0, y1 = 13 - h / 2, 13 + h / 2
            color = GREEN if rms < 0.03 else (YELLOW if rms < 0.08 else RED)
            target_wave.create_rectangle(x, y0, x + bar_w, y1, fill=color, outline="", tags="wave")

    def _start_recording(self):
        if not AUDIO_REC_AVAILABLE:
            messagebox.showerror("Kayıt Hatası", "Ses kayıt modülü (sounddevice) bulunamadı.")
            return
        try:
            if pygame.mixer.get_init() and pygame.mixer.music.get_busy():
                with audio_lock:
                    pygame.mixer.music.stop()
            self.audio_frames = []
            self.speech_started = False
            self.silence_chunks = 0
            self.wave_buffer = [0] * 20
            self.audio_stream = sd.InputStream(samplerate=SAMPLERATE, channels=1, callback=self._audio_callback)
            self.audio_stream.start()
            self.recording = True
            self._set_recording_glow(True)
            if self.current_mode == "sentences":
                self.btn_sent_record.config(text="⏹️ DURDUR", bg=RED)
                self.sent_verdict_label.config(text="🎙️ ŞİMDİ DİNLİYORUM...", fg=GREEN if self.theme_mode == "dark" else "#047857")
                self.sent_rec_status.config(text="Konuşmanız bittiğinde otomatik durur veya DURDUR'a basın", fg=self.colors["TEXT_DIM"])
            elif self.current_mode == "words":
                self.btn_record.config(text="⏹️ DURDUR", bg=RED)
                self.verdict_label.config(text="🎙️ ŞİMDİ DİNLİYORUM...", fg=GREEN if self.theme_mode == "dark" else "#047857")
                self.rec_status.config(text="Yeşil çerçeve aktifken net bir sesle okuyun", fg=self.colors["TEXT_DIM"])
            elif self.current_mode == "dialogue" and self.roleplay_active:
                self.btn_roleplay_mic.config(text="⏹️ Durdur", bg=RED)
                self.lbl_roleplay_status.config(text="🎙️ Dinliyorum...", fg=RED)
        except Exception as ex:
            log_error("Kayıt başlatma hatası", ex)
            messagebox.showerror("Kayıt Hatası", str(ex))

    def _stop_recording(self):
        self.recording = False
        self._safe_ui(lambda: self._set_recording_glow(False))
        if self.current_mode == "sentences":
            self._safe_ui(lambda: self.btn_sent_record.config(text="🎤 Telaffuz Et", bg=BLUE))
            self._safe_ui(lambda: self.sent_verdict_label.config(text="⏳ Sesiniz Analiz Ediliyor...", fg=YELLOW if self.theme_mode == "dark" else "#1e3a8a"))
        elif self.current_mode == "words":
            self._safe_ui(lambda: self.btn_record.config(text="🎤 BAŞLA VE KONUŞ", bg=BLUE))
            self._safe_ui(lambda: self.rec_status.config(text="⏳ Sesiniz Analiz Ediliyor...", fg=YELLOW if self.theme_mode == "dark" else "#1e3a8a"))
        elif self.current_mode == "dialogue" and self.roleplay_active:
            self._safe_ui(lambda: self.btn_roleplay_mic.config(text="🎤 Konuş / Durdur", bg=BLUE))
            self._safe_ui(lambda: self.lbl_roleplay_status.config(text="⏳ Analiz ediliyor...", fg=YELLOW))

        self._safe_ui(lambda: self.wave_canvas.delete("wave"))
        self._safe_ui(lambda: self.sent_wave_canvas.delete("wave"))
        self._safe_ui(lambda: self.prompt_label.place_forget())
        self._safe_ui(lambda: self.sent_prompt_label.place_forget())

        def process_audio():
            if self.audio_stream:
                try:
                    self.audio_stream.stop()
                    self.audio_stream.close()
                except Exception as ex:
                    log_error("Audio stream kapatma hatası", ex)
                self.audio_stream = None

            if not self.audio_frames:
                if self.current_mode == "dialogue" and self.roleplay_active:
                    self._safe_ui(lambda: self._handle_roleplay_transcription(""))
                elif self.current_mode == "sentences":
                    self._safe_ui(lambda: self.sent_verdict_label.config(text="Ses duyulamadı.", fg=RED))
                else:
                    self._safe_ui(lambda: self.verdict_label.config(text="Ses duyulamadı.", fg=RED))
                return

            audio = np.concatenate(self.audio_frames, axis=0)
            duration = len(audio) / SAMPLERATE

            if duration < 0.35:
                self.audio_frames = []
                if self.current_mode == "dialogue" and self.roleplay_active:
                    self._safe_ui(lambda: self._handle_roleplay_transcription(""))
                elif self.current_mode == "sentences":
                    self._safe_ui(lambda: self.sent_verdict_label.config(text="Çok kısa, tekrar deneyin", fg=RED))
                else:
                    self._safe_ui(lambda: self.verdict_label.config(text="Çok kısa, tekrar deneyin", fg=RED))
                return

            rms = np.sqrt(np.mean(np.square(audio)))
            self.volume_score = min(100, int(rms * 1200))

            rec_id = f"rp_{self.roleplay_turn_index}" if (self.current_mode == "dialogue" and self.roleplay_active) else (getattr(self._current_item(), "id", "rec"))
            path = os.path.join(RECORDINGS_DIR, f"{rec_id}_{int(time.time())}.wav")

            audio_int16 = np.int16(np.clip(audio, -1.0, 1.0) * 32767)
            with wave.open(path, "wb") as wf:
                wf.setnchannels(1)
                wf.setsampwidth(2)
                wf.setframerate(SAMPLERATE)
                wf.writeframes(audio_int16.tobytes())

            self.current_recording_path = path
            self.audio_frames = []

            if self.auto_clean_recordings_var.get():
                cleanup_recordings_folder(keep_latest=10, exclude_path=path)

            self._safe_ui(self._update_recordings_btn_label)
            self._safe_ui(lambda: self.btn_play_own_word.config(state="normal", bg=self.colors["BTN_SEC"], fg=self.colors["TEXT_MAIN"]))
            self._safe_ui(lambda: self.btn_play_own_sent.config(state="normal", bg=self.colors["BTN_SEC"], fg=self.colors["TEXT_MAIN"]))
            self._transcribe_async(path)

        threading.Thread(target=process_audio, daemon=True).start()

    def _retry_load_model(self):
        if WHISPER_AVAILABLE and self.model is None and not self._model_loading:
            self._load_model_async()

    def _load_model_async(self):
        if self._model_loading:
            return
        self._model_loading = True
        self.rec_status.config(text="Yapay Zeka Hazırlanıyor...", fg=YELLOW if self.theme_mode == "dark" else "#1e3a8a", cursor="")
        self.sent_rec_status.config(text="Yapay Zeka Hazırlanıyor...", fg=YELLOW if self.theme_mode == "dark" else "#1e3a8a", cursor="")

        def worker():
            try:
                self.model = WhisperModel(WHISPER_MODEL_NAME, device="cpu", compute_type="int8")
                self._model_loading = False
                self._safe_ui(lambda: self.rec_status.config(text="Hazır ✓", fg=GREEN if self.theme_mode == "dark" else "#047857", cursor=""))
                self._safe_ui(lambda: self.sent_rec_status.config(text="Hazır ✓", fg=GREEN if self.theme_mode == "dark" else "#047857", cursor=""))
            except Exception as ex:
                self._model_loading = False
                log_error("Whisper model yükleme hatası", ex)
                self._safe_ui(lambda: self.rec_status.config(text="❌ Yapay Zeka Yüklenemedi (🔄 Yeniden Dene)", fg=RED, cursor="hand2"))
                self._safe_ui(lambda: self.sent_rec_status.config(text="❌ Yapay Zeka Yüklenemedi (🔄 Yeniden Dene)", fg=RED, cursor="hand2"))
        threading.Thread(target=worker, daemon=True).start()

    def _safe_ui(self, fn):
        if not self._closing:
            try:
                self.root.after(0, fn)
            except Exception:
                pass

    def _transcribe_async(self, wav_path: str):
        if not self.model:
            self._safe_ui(lambda: self._show_toast("Yapay zeka modeli henüz hazır değil. Durum yazısına tıklayarak yeniden deneyebilirsiniz.", RED))
            return
        self.transcribing = True
        self._safe_ui(lambda: self.btn_record.config(state="disabled"))
        self._safe_ui(lambda: self.btn_sent_record.config(state="disabled"))

        is_rp = (self.current_mode == "dialogue" and self.roleplay_active)
        expected_ar = ""
        if not is_rp:
            if self.current_mode == "words":
                expected_ar = self._get_active_word_expected_arabic()
            else:
                item = self._current_item()
                expected_ar = getattr(item, "arapca", "") if item else ""

        def worker():
            try:
                audio_np = load_wav_as_16k_float32(wav_path)
                segments_gen, _ = self.model.transcribe(audio_np, language="ar", beam_size=5, vad_filter=True)
                segments = list(segments_gen)

                if not segments:
                    if is_rp:
                        self._safe_ui(lambda: self._finish_rp_transcribe(""))
                    else:
                        self._safe_ui(lambda: self._show_result(expected_ar, "", 0.0, "", "", -1.0))
                    return

                heard_text = "".join([seg.text for seg in segments]).strip()
                avg_logprob = sum([seg.avg_logprob for seg in segments]) / len(segments)
                if len(heard_text) > 180:
                    heard_text = heard_text[:177] + "..."

                if is_rp:
                    self._safe_ui(lambda: self._finish_rp_transcribe(heard_text))
                else:
                    score, e_norm, h_norm = compare_arabic(expected_ar, heard_text)
                    self._safe_ui(lambda: self._show_result(expected_ar, heard_text, score, e_norm, h_norm, avg_logprob))
            except Exception as ex:
                log_error("Whisper transcribe hatası", ex)
                err_msg = f"Sistem Hatası: {str(ex)}"
                self._safe_ui(lambda: self._show_error(err_msg))

        threading.Thread(target=worker, daemon=True).start()

    def _finish_rp_transcribe(self, heard_text: str):
        self.transcribing = False
        self.btn_record.config(state="normal")
        self.btn_sent_record.config(state="normal")
        self._handle_roleplay_transcription(heard_text)

    def _evaluate_adaptive_coaching(self, item_id: str, is_correct: bool, makhraj_tips: list) -> bool:
        iid_str = str(item_id or "unk")
        self.recent_attempt_history.append((iid_str, is_correct))
        if len(self.recent_attempt_history) > 15:
            self.recent_attempt_history.pop(0)

        same_item_attempts = [ok for (k_id, ok) in self.recent_attempt_history if k_id == iid_str]
        if len(same_item_attempts) >= 3 and not any(same_item_attempts[-3:]):
            if not self.slow_tts_var.get():
                self.slow_tts_var.set(True)
                self._show_toast("💡 Koç Önerisi: Bu kartta zorlandığınız için '🐢 Yavaş Seslendirme (%25)' otomatik açıldı!", YELLOW, 3500)
            elif makhraj_tips:
                self._show_toast(f"💡 {makhraj_tips[0]['tip']}", YELLOW, 3500)
            else:
                self._show_toast("💡 İpucu: '🗣️ Gölgele' butonuyla ritim antrenmanı yapmayı deneyebilirsiniz.", YELLOW, 3200)
            self.recent_attempt_history = [(k_id, ok) for (k_id, ok) in self.recent_attempt_history if k_id != iid_str]
            return True

        if len(self.recent_attempt_history) >= 6:
            last_six = self.recent_attempt_history[-6:]
            distinct_ids = {k_id for (k_id, _) in last_six}
            if all(ok for (_, ok) in last_six) and len(distinct_ids) >= 4:
                cur_item = self._current_item()
                cur_lvl = getattr(cur_item, "seviye", "A1") if cur_item else "A1"
                next_lvl = _NEXT_CEFR_MAP.get(cur_lvl, "B1")
                if cur_lvl != next_lvl:
                    msg = f"🌟 Muhteşem Seri (6/6)! '{cur_lvl}' seviyesinde çok başarılısınız; üst çubuktan '{next_lvl}' seviyesine geçebilirsiniz!"
                else:
                    msg = "🌟 Muhteşem Seri (6/6)! C1 seviyesinde tam ustalık; '🙈 Sınav Modu'nu deneyebilirsiniz!"
                self._show_toast(msg, GREEN, 3600)
                self.recent_attempt_history.clear()
                return True

        return False

    def _show_result(self, expected_raw, heard_raw, score, e_norm, h_norm, avg_logprob):
        self.transcribing = False
        self.btn_record.config(state="normal")
        self.btn_sent_record.config(state="normal")

        if not h_norm or len(h_norm) < 2 or avg_logprob < -1.15:
            if self.current_mode == "sentences":
                self.sent_verdict_label.config(text="🤔 Sesiniz Net Algılanamadı", fg=RED)
                self.sent_rec_status.config(text="Lütfen mikrofona biraz daha yakın ve net konuşun", fg=RED)
            elif self.current_mode == "words":
                self.verdict_label.config(text="🤔 Sesiniz Net Algılanamadı", fg=RED)
                self.rec_status.config(text="Lütfen mikrofona biraz daha yakın ve net konuşun", fg=RED)
            else:
                self._show_toast("🤔 Sesiniz net algılanamadı, tekrar deneyin.", RED)

            self._update_char_analysis(expected_raw, "", None, is_correct=False)
            return

        pct = int(round(score * 100))
        word_results, word_ratio = evaluate_sentence_words(expected_raw, heard_raw, word_threshold=0.72)
        makhraj_tips = analyze_phoneme_and_makhraj_errors(expected_raw, heard_raw)

        if self.current_mode in ("sentences", "dialogue"):
            correct = (word_ratio >= 0.75) or (score >= 0.65)
        else:
            correct = (score >= 0.65) and (word_ratio >= 0.75)

        item = self._current_item()
        conf_score = max(0, int((1.0 + avg_logprob) * 100)) if avg_logprob > -1.0 else 0
        quality = max(0, min(100, int((pct * 0.5) + (self.volume_score * 0.2) + (conf_score * 0.3))))

        if item:
            update_db(item.id, self.current_mode, correct)
            if correct and hasattr(item, "is_learned"):
                item.is_learned = 1
        self._update_stats_ui()

        display_exp = expected_raw if self.tashkeel_var.get() else remove_diacritics(expected_raw)

        if self.current_mode == "sentences":
            prev_mask = self.sent_word_mask if len(self.sent_word_mask) == len(word_results) else [False] * len(word_results)
            combined_res, new_mask = [], []
            for idx, (w_str, is_ok) in enumerate(word_results):
                now_ok = is_ok or prev_mask[idx]
                new_mask.append(now_ok)
                combined_res.append((w_str, now_ok))
            self.sent_word_mask = new_mask

            max_a = self.max_attempts_var.get()
            attempts_exhausted = (max_a != "Sınırsız" and self.attempt_count >= int(max_a[0]))

            if correct or attempts_exhausted or not (self.sent_hide_ar_var.get() or self.test_mode_var.get()):
                self._render_sentence_arabic_area(combined_res)
            elif self.roleplay_progressive_reveal_var.get():
                partial = [(w, True) if ok else ("❓", False) for w, ok in combined_res]
                self._render_sentence_arabic_area(partial)

        ok_fg = "#047857" if self.theme_mode == "light" else GREEN
        coach_short = f"  •  {makhraj_tips[0]['title']}" if makhraj_tips else ""

        self._update_char_analysis(expected_raw, heard_raw, makhraj_tips, is_correct=correct)
        adaptive_triggered = self._evaluate_adaptive_coaching(getattr(item, "id", ""), correct, makhraj_tips)

        if correct:
            if self.current_mode == "sentences":
                self.sent_verdict_label.config(text=f"✅ HARİKA! — Kalite: %{quality} (Doğruluk: %{pct})", fg=ok_fg)
                self.sent_rec_status.config(text=f"Duyulan: {shape_arabic(heard_raw)}{coach_short}", fg=self.colors["TEXT_MAIN"])
            elif self.current_mode == "words":
                if self.test_mode_var.get():
                    self.arabic_label.config(text=shape_arabic(display_exp), fg=self.colors["TEXT_AR"])
                if not self.word_show_tr.get() and item and hasattr(item, "turkce"):
                    self.turkish_label.config(text=item.turkce, fg=ok_fg)
                self.btn_word_learned.config(text="✅ Öğrendim", bg=GREEN, fg="#0f172a")
                self.verdict_label.config(text=f"✅ HARİKA! — Kalite: %{quality}", fg=ok_fg)
                self.rec_status.config(text=f"Doğruluk: %{pct}  |  Ses: %{self.volume_score}  |  Güven: %{conf_score}{coach_short}", fg=self.colors["TEXT_DIM"])
                self.detail_label.config(text=f"Beklenen: {shape_arabic(display_exp)}   |   Duyulan: {shape_arabic(heard_raw)}", fg=self.colors["TEXT_MAIN"])
            else:
                self._show_toast(f"✅ HARİKA! Doğruluk: %{pct} — Kalite: %{quality}", GREEN, 3000)

            if quality >= 75:
                self._play_praise_sound()
                self._start_confetti_animation()

            if self.auto_advance_var.get() and self.current_mode in ("words", "sentences"):
                advance_delay = 3200 if (makhraj_tips or adaptive_triggered) else 1600
                self.root.after(advance_delay, self._next)
        else:
            warn_col = "#b45309" if self.theme_mode == "light" else YELLOW
            if self.current_mode == "sentences":
                ok_cnt = sum(1 for _, ok in word_results if ok)
                self.sent_verdict_label.config(
                    text=f"❌ {len(word_results)} kelimeden {ok_cnt}'i doğru{coach_short}",
                    fg=RED
                )
                hint_line = makhraj_tips[0]["tip"] if makhraj_tips else f"Duyulan: {shape_arabic(heard_raw)}"
                self.sent_rec_status.config(text=hint_line, fg=warn_col if makhraj_tips else RED)
            elif self.current_mode == "words":
                self.verdict_label.config(text=f"❌ TEKRAR DENE — Doğruluk: %{pct}{coach_short}", fg=RED)
                hint_line = makhraj_tips[0]["tip"] if makhraj_tips else f"Ses: %{self.volume_score}  |  Güven: %{conf_score} — Harf mahrecine dikkat ederek tekrar okuyun."
                self.rec_status.config(text=hint_line, fg=warn_col if makhraj_tips else RED)
                self.detail_label.config(text=f"Beklenen: {shape_arabic(display_exp)}   |   Duyulan: {shape_arabic(heard_raw)}", fg=self.colors["TEXT_MAIN"])
            else:
                self._show_toast(f"❌ Tekrar Deneyin (Doğruluk: %{pct}){coach_short}", RED, 3000)

        self._custom_eval_target = None

    def _start_confetti_animation(self):
        target_canvas = self.canvas if self.current_mode == "words" else (self.sent_canvas if self.current_mode == "sentences" else self.chat_canvas)
        if target_canvas:
            w = target_canvas.winfo_width()
            if w < 10:
                w = 800
            for _ in range(55):
                x = random.randint(0, w)
                y = random.randint(-150, 0)
                self.confetti_particles.append(ConfettiParticle(target_canvas, x, y))
            self._animate_confetti()

    def _animate_confetti(self):
        if not self.confetti_particles or self._closing:
            return
        alive = []
        for p in self.confetti_particles:
            if p.update():
                alive.append(p)
            else:
                p.canvas.delete(p.id)
        self.confetti_particles = alive
        if self.confetti_particles:
            self.root.after(20, self._animate_confetti)

    def _play_praise_sound(self):
        effects = [
            "sounds/effect/ahsent_male.mp3", "sounds/effect/ahsent_female.mp3",
            "sounds/effect/mumtaz_male.mp3", "sounds/effect/mumtaz_female.mp3",
            "sounds/effect/rai_male.mp3", "sounds/effect/rai_female.mp3"
        ]
        path = os.path.join(ASSETS_DIR, random.choice(effects))
        play_effect_nonblocking(path)

    def _update_char_analysis(self, expected: str, heard: str, makhraj_tips: list = None, is_correct: bool = False):
        ok_col = "#047857" if self.theme_mode == "light" else GREEN
        coach_col = "#b45309" if self.theme_mode == "light" else YELLOW

        self.char_analysis_text.config(state="normal")
        self.char_analysis_text.delete(1.0, tk.END)
        self.char_analysis_text.tag_configure("right", justify="right")
        self.char_analysis_text.tag_configure("green", foreground=ok_col, justify="right")
        self.char_analysis_text.tag_configure("red", foreground=RED, justify="right")
        self.char_analysis_text.tag_configure("red_char", foreground="#ffffff", background=RED, font=(AR_FONT_FAMILY, 14, "bold"))
        self.char_analysis_text.tag_configure("dim", foreground=self.colors["TEXT_DIM"], font=("Segoe UI", 9, "bold"))
        self.char_analysis_text.tag_configure("coach_title", foreground=coach_col, font=("Segoe UI", 10, "bold"))
        self.char_analysis_text.tag_configure("coach_body", foreground=self.colors["TEXT_MAIN"], font=("Segoe UI", 9))

        haraka_info = build_haraka_guide(expected)

        if not heard:
            self.char_analysis_text.insert(tk.END, "🎙️ Ses kaydı bekleniyor...\n", "dim")
            if haraka_info:
                self.char_analysis_text.insert(tk.END, "\n🎯 HAREKE & VOKAL REHBERİ:\n", "coach_title")
                self.char_analysis_text.insert(tk.END, f"{haraka_info}\n", "coach_body")
                self._last_coach_summary = "🔤 " + haraka_info.splitlines()[0]
            else:
                self.char_analysis_text.insert(tk.END, "Telaffuz ettiğinizde harf mahreci, kök harf ve uzatma (med) ipuçları burada görünecektir.", "dim")
                self._last_coach_summary = ""
        else:
            if makhraj_tips is None:
                makhraj_tips = analyze_phoneme_and_makhraj_errors(expected, heard)

            word_res, _ = evaluate_sentence_words(expected, heard, word_threshold=0.72)
            self.char_analysis_text.insert(tk.END, "Kelime Doğruluğu (Yeşil / Kırmızı):\n", "dim")
            for w_str, is_ok in word_res:
                tag = "green" if is_ok else "red"
                self.char_analysis_text.insert(tk.END, shape_arabic(w_str) + " ", tag)

            self.char_analysis_text.insert(tk.END, "\nAlgılanan Ses:\n", "dim")
            self.char_analysis_text.insert(tk.END, shape_arabic(heard) + "\n", "right")

            if makhraj_tips:
                self.char_analysis_text.insert(tk.END, "\n🎯 MAHREÇ & HARF KOÇU:\n", "coach_title")
                for tip_item in makhraj_tips:
                    t_title = tip_item["title"]
                    w_exp = tip_item.get("word_exp", "")
                    w_hrd = tip_item.get("word_heard", "")
                    self.char_analysis_text.insert(tk.END, f"• {t_title}:\n", "coach_title")
                    if w_exp and w_hrd:
                        self.char_analysis_text.insert(tk.END, "  Hedef: ", "dim")
                        self.char_analysis_text.insert(tk.END, f" {shape_arabic(w_exp)} ", "red_char")
                        self.char_analysis_text.insert(tk.END, f" ➔ Duyulan: {shape_arabic(w_hrd)}\n", "coach_body")
                    self.char_analysis_text.insert(tk.END, f"  {tip_item['tip']}\n", "coach_body")
                self._last_coach_summary = f"{makhraj_tips[0]['title']} — {makhraj_tips[0]['tip']}"
            elif is_correct:
                self.char_analysis_text.insert(tk.END, "\n✅ Mahreç, kök harf ve med uyumu gayet güzel!\n", "coach_title")
                self._last_coach_summary = "✅ Mahreç, kök harf ve med uyumu gayet güzel!"
            else:
                self.char_analysis_text.insert(tk.END, "\n⚠️ Kelime veya kök harf telaffuzu eşleşmedi. Doğru okunuşu dinleyip tekrar deneyin.\n", "coach_title")
                self._last_coach_summary = "⚠️ Kelime veya kök harf telaffuzu eşleşmedi; tekrar dinleyip okuyun."

            if haraka_info:
                self.char_analysis_text.insert(tk.END, "\n🔤 HAREKE DİZİLİMİ:\n", "dim")
                self.char_analysis_text.insert(tk.END, f"{haraka_info}", "coach_body")

        self.char_analysis_text.config(state="disabled")
        self._sync_inline_coach_visibility()

    def _show_error(self, err):
        self.transcribing = False
        self.btn_record.config(state="normal")
        self.btn_sent_record.config(state="normal")
        if self.current_mode == "sentences":
            self.sent_verdict_label.config(text="Sistem Hatası", fg=RED)
            self.sent_rec_status.config(text=err[:80], fg=RED)
        else:
            self.verdict_label.config(text="Sistem Hatası", fg=RED)
            self.detail_label.config(text=err[:80], fg=RED)

    def _prev(self):
        if self.recording or not self.current_items:
            return
        self._stop_all_audio(include_effects=False)
        self.current_index = (self.current_index - 1) % len(self.current_items)
        if self.current_mode == "sentences":
            self._save_last_index()
            self._show_sentence_card()
            if self.auto_start_var.get():
                self.root.after(400, self._start_auto_flow)
        else:
            self._show_card(play_audio=True)

    def _next(self):
        if self.recording or not self.current_items:
            return
        self._stop_all_audio(include_effects=False)
        self.current_index = (self.current_index + 1) % len(self.current_items)
        if self.current_mode == "sentences":
            self._save_last_index()
            self._show_sentence_card()
            if self.auto_start_var.get():
                self.root.after(400, self._start_auto_flow)
        else:
            self._show_card(play_audio=True)

    def _on_close(self):
        self._closing = True
        self._hide_tooltip()
        self._close_active_singleton_dialog()
        self.roleplay_active = False
        self._stop_all_audio(include_effects=True)
        if self.recording:
            try:
                self._stop_recording()
            except Exception as ex:
                log_error("Kapanışta kayıt durdurma hatası", ex)
        if self.auto_clean_recordings_var.get():
            cleanup_recordings_folder(keep_latest=5)
        self.root.destroy()


if __name__ == "__main__":
    try:
        import ctypes
        # 1. Windows Görev Çubuğu Özel Uygulama Kimliği
        try:
            myappid = "muallimun.arapca_asistani.v40"
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(myappid)
        except Exception:
            pass

        # 2. Tek Örnek (Single Instance) Kilidi — Uygulama açıkken tekrar açılmasını engeller
        _instance_mutex = ctypes.windll.kernel32.CreateMutexW(
            None, False, "Muallimun_ArapcaAsistani_SingleInstance_Mutex_v40"
        )
        if ctypes.windll.kernel32.GetLastError() == 183:  # ERROR_ALREADY_EXISTS (Zaten açık)
            # Arkada kalan mevcut pencereyi bulup öne getir
            hwnd = ctypes.windll.user32.FindWindowW(
                None, f"Arapça Telaffuz ve Konuşma Asistanı v{APP_VERSION} — Muallimun.Net"
            )
            if hwnd:
                ctypes.windll.user32.ShowWindow(hwnd, 9)  # SW_RESTORE (Simge durumundaysa büyüt)
                ctypes.windll.user32.SetForegroundWindow(hwnd)

            _warn_root = tk.Tk()
            _warn_root.withdraw()
            _warn_root.attributes("-topmost", True)
            messagebox.showwarning(
                "Uygulama Zaten Açık",
                "Arapça Telaffuz ve Konuşma Asistanı şu anda zaten çalışıyor!\n\n"
                "Açık olan uygulama penceresi ekrana getirildi.",
                parent=_warn_root
            )
            _warn_root.destroy()
            sys.exit(0)

        root = tk.Tk()
        app = App(root)
        root.lift()
        root.attributes("-topmost", True)
        root.after(200, lambda: root.attributes("-topmost", False))
        root.mainloop()
    except Exception as main_ex:
        log_error("Ana uygulama çökme hatası", main_ex)
        traceback.print_exc()
        if not getattr(sys, "frozen", False) and sys.stdin is not None:
            input("\nKapatmak için Enter tuşuna basın...")