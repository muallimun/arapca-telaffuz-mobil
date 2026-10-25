# -*- coding: utf-8 -*-
"""
ui/dialogue_mode.py — 💬 Diyalog Modu (Öğrenme & Karşılıklı Rol Yapma / Canlandırma Mixin Sınıfı)
- Tam genişlikli, sarsıntısız diyalog kartı düzeni
- 🎭 Ölçme / Canlandırma (Roleplay) modunda Türkçe çeviriyi otomatik gizleyip
  '💡 Türkçe İpucu Göster' butonuyla açan akıllı pedagojik yapı (v39).
"""

import os
import time
import threading
import tkinter as tk
import tkinter.font as tkfont
from dataclasses import dataclass

import pygame

from core.config import (
    AR_FONT_FAMILY, ACCENT, GREEN, RED, BLUE, YELLOW, PURPLE,
    FONT_UI_B, log_error
)
from core.arabic_nlp import (
    remove_diacritics, shape_arabic_by_pixels, evaluate_sentence_words,
    analyze_phoneme_and_makhraj_errors
)
from core.database import (
    get_dialogues, get_dialogue_lines, get_package_level_and_topic, update_db
)
from core.audio_engine import (
    audio_lock, resolve_local_media_path, get_tts_audio,
    detect_voice_for_speaker, ensure_mixer_initialized, play_beep_nonblocking
)
from ui.dialogs import (
    open_quick_edit_dialogue_line, open_roleplay_report_card,
    open_shadowing_trainer_window
)


@dataclass
class _DialogueEvalTarget:
    id: str
    arapca: str
    turkce: str
    ses: str = None
    seviye: str = "A1"


class DialogueModeMixin:
    """App sınıfına Diyalog Öğrenme ve Karşılıklı Canlandırma (Roleplay) yeteneklerini kazandıran Mixin."""

    def _detect_voice(self, speaker_name: str, gender: str = "E") -> str:
        return detect_voice_for_speaker(speaker_name, gender)

    def _load_dialogues_ui(self):
        lvl = self.dlg_level_var.get()
        self.dialogues_list = get_dialogues(seviye=lvl)
        display_names = []
        self._dlg_display_to_id = {}

        for did, _, b_ar, b_tr in self.dialogues_list:
            d_sev, _ = get_package_level_and_topic("dialogue", did)
            disp = f"[{d_sev}] {b_tr} — {b_ar}" if b_ar else f"[{d_sev}] {b_tr}"
            display_names.append(disp)
            self._dlg_display_to_id[disp] = did

        self.dlg_combo["values"] = display_names
        if display_names:
            cur_sel = self.dlg_combo.get()
            if cur_sel not in self._dlg_display_to_id:
                self.dlg_combo.set(display_names[0])
        else:
            self.dlg_combo.set("")

        self._populate_role_combo()
        self._refresh_chat()
        self._update_stats_ui()

    def _get_selected_dialogue_id(self) -> str:
        if not hasattr(self, "_dlg_display_to_id"):
            return ""
        return self._dlg_display_to_id.get(self.dlg_combo.get(), "")

    def _on_dialogue_change(self, event=None):
        self._stop_all_audio(include_effects=True)
        if self.roleplay_active:
            self._stop_roleplay_session()
        self._populate_role_combo()
        self._refresh_chat()
        self._update_stats_ui()

    def _populate_role_combo(self):
        did = self._get_selected_dialogue_id()
        lines = get_dialogue_lines(did) if did else []
        speakers = []
        for _, _, spk, _, _, _, _, _ in lines:
            if spk and spk not in speakers:
                speakers.append(spk)
        self.role_combo["values"] = speakers
        if speakers:
            if self.dlg_role_var.get() not in speakers:
                self.dlg_role_var.set(speakers[0])
        else:
            self.dlg_role_var.set("")

    def _switch_dlg_submode(self, submode: str):
        self._stop_all_audio(include_effects=True)
        if self.roleplay_active:
            self._stop_roleplay_session()

        self.dlg_submode = submode
        c = self.colors
        if submode == "learn":
            self.btn_sub_learn.config(bg=GREEN, fg="#0f172a")
            self.btn_sub_roleplay.config(bg=c["BTN_SEC"], fg=c["TEXT_MAIN"])
            self.roleplay_controls_frame.pack_forget()
            self.learn_controls_frame.pack(side="left", padx=10)
            self.lbl_roleplay_status.config(text="")
        else:
            self.btn_sub_roleplay.config(bg=YELLOW, fg="#0f172a")
            self.btn_sub_learn.config(bg=c["BTN_SEC"], fg=c["TEXT_MAIN"])
            self.learn_controls_frame.pack_forget()
            self.roleplay_controls_frame.pack(side="left", padx=8)
            self.lbl_roleplay_status.config(
                text="Rolünüzü seçip '🎬 Canlandırmayı Başlat'a basın",
                fg=c["ACCENT_HEAD"]
            )

        self._refresh_chat()

    def _refresh_chat(self):
        if not hasattr(self, "chat_frame") or not self.chat_frame.winfo_exists():
            return

        for w in self.chat_frame.winfo_children():
            w.destroy()

        self._line_card_widgets = {}
        c = self.colors
        is_dark = (self.theme_mode == "dark")

        did = self._get_selected_dialogue_id()
        if not did:
            tk.Label(
                self.chat_frame, text="Seçili seviyede diyalog bulunamadı.",
                bg=c["CHAT_BG"], fg=c["TEXT_DIM"], font=("Segoe UI", 13, "bold"), pady=30
            ).pack()
            return

        lines = get_dialogue_lines(did)
        self.roleplay_lines = lines
        if not lines:
            return

        speakers_order = []
        for row in lines:
            spk = row[2]
            if spk not in speakers_order:
                speakers_order.append(spk)

        canvas_w = self.chat_canvas.winfo_width()
        max_px = max(380, canvas_w - 110) if canvas_w > 200 else 680
        ar_font_obj = tkfont.Font(family=AR_FONT_FAMILY, size=self.chat_font_size, weight="bold")

        is_roleplay_mode = (self.dlg_submode == "roleplay")
        chosen_role = self.dlg_role_var.get().strip()
        is_edit = self.edit_mode_var.get()
        ok_fg = GREEN if is_dark else "#047857"

        for idx, (lid, sira, spk, spk_ar, gender, text_ar, text_tr, ses_p) in enumerate(lines):
            is_first_spk = (speakers_order.index(spk) % 2 == 0) if spk in speakers_order else (idx % 2 == 0)
            is_user_role = is_roleplay_mode and (spk == chosen_role)
            is_active_turn = self.roleplay_active and (self.roleplay_turn_index == idx)

            if is_user_role:
                card_bg = "#132a2e" if is_dark else "#ecfdf5"
                border_col = GREEN if is_active_turn else ("#0d9488" if is_dark else "#34d399")
            else:
                card_bg = c["BUBBLE_LEFT"] if is_first_spk else c["BUBBLE_RIGHT"]
                border_col = YELLOW if is_active_turn else c["BORDER"]

            bw = 2 if is_active_turn else 1
            bubble = tk.Frame(
                self.chat_frame, bg=card_bg, padx=14, pady=10,
                highlightthickness=bw, highlightbackground=border_col
            )
            bubble.pack(fill="x", padx=8, pady=5)
            self._line_card_widgets[idx] = (bubble, card_bg, border_col, bw)

            # Üst Satır: Sol Aksiyon Butonları + Sağ Konuşmacı Başlığı
            top_row = tk.Frame(bubble, bg=card_bg)
            top_row.pack(fill="x", pady=(0, 4))

            voice_name = self._detect_voice(spk, gender)
            tk.Button(
                top_row, text="🔊 Dinle",
                command=lambda t=text_ar, v=voice_name, sp=ses_p: self._play_tts_text_async(t, v, sp),
                bg=ACCENT, fg="white", font=("Segoe UI", 9, "bold"),
                relief="flat", padx=9, pady=2, cursor="hand2"
            ).pack(side="left", padx=(0, 4))

            tk.Button(
                top_row, text="🎤 Telaffuz Et",
                command=lambda l_id=lid, t_ar=text_ar, t_tr=text_tr, sp=ses_p: self._eval_dialogue_line(l_id, t_ar, t_tr, sp),
                bg=BLUE, fg="white", font=("Segoe UI", 9, "bold"),
                relief="flat", padx=9, pady=2, cursor="hand2"
            ).pack(side="left", padx=4)

            tk.Button(
                top_row, text="🗣️ Gölgele",
                command=lambda l_id=lid, t_ar=text_ar, t_tr=text_tr, v=voice_name, sp=ses_p: open_shadowing_trainer_window(self, t_ar, t_tr, sp, v, f"dlg_{l_id}"),
                bg=PURPLE, fg="white", font=("Segoe UI", 9, "bold"),
                relief="flat", padx=9, pady=2, cursor="hand2"
            ).pack(side="left", padx=4)

            if is_edit:
                tk.Button(
                    top_row, text="✏️ Düzenle",
                    command=lambda l_id=lid, s=spk, sa=spk_ar, g=gender, ar=text_ar, tr=text_tr, sp=ses_p: open_quick_edit_dialogue_line(self, l_id, s, sa, g, ar, tr, sp),
                    bg=YELLOW, fg="#0f172a", font=("Segoe UI", 8, "bold"),
                    relief="flat", padx=8, pady=2, cursor="hand2"
                ).pack(side="left", padx=4)

            spk_badge = f"👤 {spk}" + (f" ({spk_ar})" if spk_ar else "") + ("  [Sizin Rolünüz]" if is_user_role else "")
            spk_fg = ok_fg if is_user_role else (YELLOW if is_dark else "#1e3a8a")
            tk.Label(
                top_row, text=spk_badge, bg=card_bg, fg=spk_fg,
                font=("Segoe UI", 10, "bold")
            ).pack(side="right")

            # Orta Satır: Arapça Metin veya Roleplay Kelime Maskesi
            ar_holder = tk.Frame(bubble, bg=card_bg)
            ar_holder.pack(fill="x", pady=2)

            disp_ar = text_ar if self.chat_show_tashkeel.get() else remove_diacritics(text_ar)
            line_done = (idx in self.roleplay_final_results)

            if is_user_role and self.roleplay_active and not line_done:
                revealed_mask = self.roleplay_revealed_words.get(idx, [])
                words_raw = disp_ar.split()
                if revealed_mask and any(revealed_mask):
                    wrap_w = tk.Frame(ar_holder, bg=card_bg)
                    wrap_w.pack(anchor="e")
                    for w_i, w_str in enumerate(words_raw):
                        is_rev = revealed_mask[w_i] if w_i < len(revealed_mask) else False
                        lbl_txt = f"\u200f{w_str}\u200f" if is_rev else "❓"
                        lbl_col = ok_fg if is_rev else c["TEXT_DIM"]
                        tk.Label(
                            wrap_w, text=lbl_txt, bg=card_bg, fg=lbl_col,
                            font=(AR_FONT_FAMILY, self.chat_font_size, "bold"), padx=3
                        ).pack(side="right")
                else:
                    prompt_msg = "🎙️ [ Sıra Sizde — Türkçe İpucundan Yararlanarak Arapçasını Söyleyin ]" if is_active_turn else "🔒 [ Sizin Rolünüz — Sıra Geldiğinde Arapça Söyleyeceksiniz ]"
                    tk.Label(
                        ar_holder, text=prompt_msg, bg=card_bg,
                        fg=ok_fg if is_active_turn else c["TEXT_DIM"],
                        font=("Segoe UI", 11, "bold"), anchor="e"
                    ).pack(fill="x")
            elif is_user_role and line_done:
                word_res_list = self.roleplay_final_results[idx]
                wrap_w = tk.Frame(ar_holder, bg=card_bg)
                wrap_w.pack(anchor="e")
                for w_str, is_ok in word_res_list:
                    w_disp = w_str if self.chat_show_tashkeel.get() else remove_diacritics(w_str)
                    tk.Label(
                        wrap_w, text=f"\u200f{w_disp}\u200f", bg=card_bg,
                        fg=ok_fg if is_ok else RED,
                        font=(AR_FONT_FAMILY, self.chat_font_size, "bold"), padx=3
                    ).pack(side="right")
            else:
                shaped_line = shape_arabic_by_pixels(disp_ar, ar_font_obj, max_px)
                tk.Label(
                    ar_holder, text=shaped_line, bg=card_bg, fg=c["TEXT_AR"],
                    font=(AR_FONT_FAMILY, self.chat_font_size, "bold"),
                    justify="right", anchor="e"
                ).pack(fill="x")

            # Alt Satır: Türkçe Çeviri (Roleplay Modunda Akıllı Gizleme + '💡 Türkçe İpucu Göster' Butonu)
            if text_tr and self.chat_show_tr.get():
                tr_row = tk.Frame(bubble, bg=card_bg)
                tr_row.pack(fill="x", pady=(3, 0))

                if is_user_role and not line_done:
                    hint_set = getattr(self, "_roleplay_hint_shown_lines", set())
                    if idx in hint_set:
                        tk.Label(
                            tr_row, text=f"💡 İpucu (Türkçe): {text_tr}",
                            bg=card_bg, fg=c["TEXT_DIM"], font=("Segoe UI", 10, "italic"),
                            anchor="e", justify="right", wraplength=max_px
                        ).pack(side="right")
                    else:
                        lbl_hidden_tr = tk.Label(
                            tr_row, text="", bg=card_bg, fg=c["TEXT_DIM"],
                            font=("Segoe UI", 10, "italic"), anchor="e", justify="right", wraplength=max_px
                        )

                        def _reveal_tr_hint(line_i=idx, tr_t=text_tr, btn_ref_list=None, lbl_ref=lbl_hidden_tr):
                            if not hasattr(self, "_roleplay_hint_shown_lines"):
                                self._roleplay_hint_shown_lines = set()
                            self._roleplay_hint_shown_lines.add(line_i)
                            if btn_ref_list and btn_ref_list[0].winfo_exists():
                                btn_ref_list[0].destroy()
                            if lbl_ref.winfo_exists():
                                lbl_ref.config(text=f"💡 İpucu (Türkçe): {tr_t}")
                                lbl_ref.pack(side="right")

                        btn_holder = []
                        btn_hint = tk.Button(
                            tr_row, text="💡 Türkçe İpucu Göster",
                            command=lambda i=idx, t=text_tr, bh=btn_holder, lr=lbl_hidden_tr: _reveal_tr_hint(i, t, bh, lr),
                            bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 8, "bold"),
                            relief="flat", padx=8, pady=2, cursor="hand2"
                        )
                        btn_holder.append(btn_hint)
                        btn_hint.pack(side="right")
                else:
                    tk.Label(
                        tr_row, text=text_tr, bg=card_bg, fg=c["TEXT_DIM"],
                        font=("Segoe UI", max(10, self.chat_font_size - 7)),
                        anchor="e", justify="right", wraplength=max_px
                    ).pack(side="right")

        self._bind_mousewheel(self.chat_canvas, self.chat_frame)

    def _highlight_and_scroll_to_line(self, active_idx):
        if not hasattr(self, "_line_card_widgets"):
            return
        total = max(1, len(self._line_card_widgets))
        for idx, (frame_w, orig_bg, orig_border, orig_bw) in self._line_card_widgets.items():
            if not frame_w.winfo_exists():
                continue
            if active_idx is not None and idx == active_idx:
                frame_w.configure(highlightbackground=YELLOW, highlightthickness=3)
                frac = max(0.0, min(1.0, (idx - 1) / total))
                self.chat_canvas.yview_moveto(frac)
            else:
                frame_w.configure(highlightbackground=orig_border, highlightthickness=orig_bw)

    def _play_dialogue(self):
        if self.dialogue_playing:
            self._stop_all_audio(include_effects=False)
            return

        did = self._get_selected_dialogue_id()
        lines = get_dialogue_lines(did) if did else []
        if not lines:
            return

        self._stop_all_audio(include_effects=False)
        session_token = self._audio_session_id
        self.dialogue_playing = True
        self.btn_play_all_dlg.config(text="⏹️ Diyaloğu Durdur", bg=RED)

        def worker():
            try:
                for idx, (_, _, spk, _, gender, text_ar, _, ses_p) in enumerate(lines):
                    if not self.dialogue_playing or session_token != self._audio_session_id or self._closing:
                        break
                    self._safe_ui(lambda i=idx: self._highlight_and_scroll_to_line(i))
                    path = resolve_local_media_path(ses_p) if ses_p else None
                    if not path:
                        v = self._detect_voice(spk, gender)
                        path = get_tts_audio(text_ar, voice=v, slow=self.slow_tts_var.get())
                    if path and os.path.exists(path) and session_token == self._audio_session_id:
                        if ensure_mixer_initialized():
                            with audio_lock:
                                if pygame.mixer.music.get_busy():
                                    pygame.mixer.music.stop()
                                pygame.mixer.music.load(path)
                                pygame.mixer.music.play()
                            while pygame.mixer.music.get_busy() and self.dialogue_playing and session_token == self._audio_session_id and not self._closing:
                                time.sleep(0.05)
                    time.sleep(0.28)
            except Exception as ex:
                log_error("Diyalog tümünü oynatma hatası", ex)
            finally:
                if session_token == self._audio_session_id:
                    self.dialogue_playing = False
                    self._safe_ui(lambda: self.btn_play_all_dlg.config(text="▶️ Diyaloğun Tümünü Oynat", bg=ACCENT))
                    self._safe_ui(lambda: self._highlight_and_scroll_to_line(None))

        threading.Thread(target=worker, daemon=True).start()

    def _eval_dialogue_line(self, line_id: int, text_ar: str, text_tr: str, ses_path: str = None):
        if self.recording or self.transcribing:
            return
        self._stop_all_audio(include_effects=False)
        self._custom_eval_target = _DialogueEvalTarget(
            id=f"dlgline_{line_id}", arapca=text_ar, turkce=text_tr or "", ses=ses_path
        )
        self._update_char_analysis(text_ar, "")

        def flow():
            play_beep_nonblocking()
            time.sleep(0.20)
            self._safe_ui(lambda: self._show_toast("🎤 Şimdi diyalog cümlesini söyleyin!", GREEN, 1800))
            self._safe_ui(self._start_recording)

        threading.Thread(target=flow, daemon=True).start()

    # =========================================================================
    # 🎭 KARŞILIKLI DİYALOG CANLANDIRMA (ROLEPLAY) MOTORU
    # =========================================================================
    def _toggle_roleplay_session(self):
        if self.roleplay_active:
            self._stop_roleplay_session()
        else:
            self._start_roleplay_session()

    def _start_roleplay_session(self):
        did = self._get_selected_dialogue_id()
        lines = get_dialogue_lines(did) if did else []
        if not lines:
            return
        if not self.dlg_role_var.get():
            self._populate_role_combo()

        self._stop_all_audio(include_effects=True)
        self.roleplay_active = True
        self.roleplay_lines = lines
        self.roleplay_turn_index = 0
        self.roleplay_attempt = 0
        self.roleplay_revealed_words = {}
        self.roleplay_final_results = {}
        self._roleplay_hint_shown_lines = set()
        self.roleplay_waiting_user = False

        self.btn_start_roleplay.config(text="⏹️ Canlandırmayı Bitir", bg=RED, fg="white")
        self.btn_roleplay_mic.config(state="disabled", bg=BLUE)
        self._refresh_chat()
        self.root.after(200, self._process_roleplay_turn)

    def _stop_roleplay_session(self):
        self.roleplay_active = False
        self.roleplay_waiting_user = False
        self._stop_all_audio(include_effects=False)
        if self.recording:
            try:
                self._stop_recording()
            except Exception:
                pass
        if hasattr(self, "btn_start_roleplay"):
            self.btn_start_roleplay.config(text="🎬 Canlandırmayı Başlat", bg=GREEN, fg="#0f172a")
        if hasattr(self, "btn_roleplay_mic"):
            self.btn_roleplay_mic.config(text="🎤 Konuş / Durdur", state="disabled", bg=BLUE)
        if hasattr(self, "lbl_roleplay_status"):
            self.lbl_roleplay_status.config(text="Canlandırma durduruldu.", fg=self.colors["TEXT_DIM"])
        self._refresh_chat()

    def _process_roleplay_turn(self):
        if not self.roleplay_active or self._closing:
            return

        if self.roleplay_turn_index >= len(self.roleplay_lines):
            self._finish_roleplay_and_show_report()
            return

        idx = self.roleplay_turn_index
        _, sira, spk, _, gender, text_ar, _, ses_p = self.roleplay_lines[idx]
        chosen_role = self.dlg_role_var.get().strip()

        self._refresh_chat()
        self._highlight_and_scroll_to_line(idx)

        if spk == chosen_role:
            self.roleplay_waiting_user = True
            self.roleplay_attempt = 0
            self.btn_roleplay_mic.config(state="normal", bg=BLUE, text="🎤 Konuş / Durdur")
            self.lbl_roleplay_status.config(
                text=f"🎙️ Sıra Sizde ({sira}. Cümle) — Bip sesinden sonra konuşun...",
                fg=GREEN if self.theme_mode == "dark" else "#047857"
            )
            self._update_char_analysis(text_ar, "")
            self.root.after(350, self._trigger_roleplay_user_mic)
        else:
            self.roleplay_waiting_user = False
            self.btn_roleplay_mic.config(state="disabled")
            self.lbl_roleplay_status.config(text=f"🔊 {spk} konuşuyor ({sira}. Cümle)...", fg=self.colors["ACCENT_HEAD"])
            session_token = self._audio_session_id

            def play_partner():
                path = resolve_local_media_path(ses_p) if ses_p else None
                if not path:
                    v = self._detect_voice(spk, gender)
                    path = get_tts_audio(text_ar, voice=v, slow=self.slow_tts_var.get())
                if path and os.path.exists(path) and session_token == self._audio_session_id and self.roleplay_active:
                    if ensure_mixer_initialized():
                        with audio_lock:
                            if pygame.mixer.music.get_busy():
                                pygame.mixer.music.stop()
                            pygame.mixer.music.load(path)
                            pygame.mixer.music.play()
                        while pygame.mixer.music.get_busy() and self.roleplay_active and session_token == self._audio_session_id and not self._closing:
                            time.sleep(0.05)
                time.sleep(0.25)
                if self.roleplay_active and session_token == self._audio_session_id and not self._closing:
                    self.roleplay_turn_index += 1
                    self._safe_ui(self._process_roleplay_turn)

            threading.Thread(target=play_partner, daemon=True).start()

    def _trigger_roleplay_user_mic(self):
        if not self.roleplay_active or not self.roleplay_waiting_user:
            return
        if self.recording:
            threading.Thread(target=self._stop_recording, daemon=True).start()
            return
        if self.transcribing:
            return

        def start_rp_mic():
            play_beep_nonblocking()
            time.sleep(0.18)
            if self.roleplay_active and self.roleplay_waiting_user and not self._closing:
                self._safe_ui(self._start_recording)

        threading.Thread(target=start_rp_mic, daemon=True).start()

    def _handle_roleplay_transcription(self, heard_text: str):
        if not self.roleplay_active or self.roleplay_turn_index >= len(self.roleplay_lines):
            return

        idx = self.roleplay_turn_index
        lid, sira, _, _, _, text_ar, _, _ = self.roleplay_lines[idx]
        self.roleplay_attempt += 1
        max_attempts = max(1, int(self.roleplay_max_attempts_var.get() or 3))

        word_res, ratio = evaluate_sentence_words(text_ar, heard_text, word_threshold=0.72)
        makhraj_tips = analyze_phoneme_and_makhraj_errors(text_ar, heard_text)

        prev_mask = self.roleplay_revealed_words.get(idx, [False] * len(word_res))
        if len(prev_mask) != len(word_res):
            prev_mask = [False] * len(word_res)

        new_mask = []
        combined_results = []
        for w_i, (w_str, is_ok) in enumerate(word_res):
            merged_ok = is_ok or prev_mask[w_i]
            new_mask.append(merged_ok)
            combined_results.append((w_str, merged_ok))

        if self.roleplay_progressive_reveal_var.get():
            self.roleplay_revealed_words[idx] = new_mask

        all_passed = all(new_mask) or (ratio >= 0.80)
        self._update_char_analysis(text_ar, heard_text, makhraj_tips, is_correct=all_passed)

        if all_passed or self.roleplay_attempt >= max_attempts:
            self.roleplay_waiting_user = False
            self.roleplay_final_results[idx] = combined_results
            update_db(f"dlgline_{lid}", "dialogue", all_passed)
            self._update_stats_ui()

            if all_passed:
                self._play_praise_sound()
                self.lbl_roleplay_status.config(text=f"✅ {sira}. Cümle Başarılı! Sonraki satıra geçiliyor...", fg=GREEN)
            else:
                self.lbl_roleplay_status.config(text=f"⏭️ {sira}. Cümle tamamlandı, sonraki satıra geçiliyor...", fg=YELLOW)

            self._refresh_chat()
            self.roleplay_turn_index += 1
            self.root.after(1200, self._process_roleplay_turn)
        else:
            rem = max_attempts - self.roleplay_attempt
            self.lbl_roleplay_status.config(
                text=f"⚠️ Eksik kelimeler var (Kalan Hak: {rem}) — Tekrar söylemek için '🎤 Konuş'a basın.",
                fg=YELLOW
            )
            self._refresh_chat()

    def _finish_roleplay_and_show_report(self):
        role_name = self.dlg_role_var.get().strip()
        lines = list(self.roleplay_lines)
        results_copy = dict(self.roleplay_final_results)

        total_w, ok_w = 0, 0
        for w_list in results_copy.values():
            for _, is_ok in w_list:
                total_w += 1
                if is_ok:
                    ok_w += 1
        score_pct = int(round((ok_w / total_w) * 100)) if total_w > 0 else 100

        all_roles = list(self.role_combo["values"])
        next_role = None
        if len(all_roles) > 1 and role_name in all_roles:
            cur_ri = all_roles.index(role_name)
            next_role = all_roles[(cur_ri + 1) % len(all_roles)]

        self._stop_roleplay_session()
        open_roleplay_report_card(self, role_name, score_pct, results_copy, lines, next_role=next_role)