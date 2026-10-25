# -*- coding: utf-8 -*-
"""
ui/admin_panel.py — 🔄 Birleştir/Üzerine Yaz ve 🧹 Tam Sıfırlama Destekli Paket Yükleyici (v40 Final),
📋 Excel / Panodan Doğrudan Kopyala-Yapıştır (Clipboard) Aktarıcı,
📊 Detaylı Yükleme & Atlanan Satır Raporu, 🖼️ Toplu Görsel/Ses Klasörü Eşleştirici,
🔤 Tüm Sütun Başlıklarına Tıklayınca A➔Z / Z➔A Sıralama ve Çoklu Seçimle Toplu Seviye Atama.
"""

import os
import re
import json
import time
import shutil
import threading
import tkinter as tk
from tkinter import messagebox, filedialog, ttk
from typing import Optional

from core.config import (
    BASE_DIR, DATA_DIR, ACCENT, GREEN, RED, BLUE, YELLOW, PURPLE,
    FONT_UI, FONT_UI_B, AR_FONT_FAMILY,
    CATEGORIES_DIR, CUSTOM_AUDIO_DIR, WORDS_DB_PATH, DIALOGUES_DB_PATH, SENTENCES_DB_PATH,
    log_error
)
from core.arabic_nlp import sanitize_arabic_sentence, sanitize_turkish_sentence
from core.database import (
    db_session,
    get_word_packages, add_word_package, get_words, delete_word_package,
    get_dialogue_packages, get_dialogues, get_dialogue_lines_with_titles, delete_dialogue_package,
    get_sentence_packages, get_key_sentences, delete_sentence_package,
    smart_import_package, export_full_package_zip, repair_all_databases,
    get_package_level_and_topic, update_package_level_and_topic,
    get_dialogue_id_for_line, audit_package_quality, auto_assign_cefr_levels,
    get_last_import_report, import_from_clipboard_text, bulk_match_media_folder
)
from core.dictionary import import_sozluk_json, DEFAULT_SOZLUK_JSON_PATH
from core.audio_engine import (
    download_audio_from_url, resolve_local_media_path,
    get_tts_audio, detect_voice_for_speaker, AdminMicRecorder
)
from ui.dialogs import open_printable_worksheet_generator


def _configure_admin_styles(root_win, app):
    """Treeview ve zarif (width=13) Scrollbar stillerini aktif temaya tam uyumlu hale getirir."""
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    style = ttk.Style(root_win)
    try:
        style.theme_use("clam")
    except Exception:
        pass

    tv_bg = "#161b26" if is_dark else "#ffffff"
    tv_fg = "#ffffff" if is_dark else "#0f172a"
    hd_bg = "#0b1a30" if is_dark else "#cbd5e1"
    hd_fg = YELLOW if is_dark else "#1e3a8a"

    style.configure(
        "Admin.Treeview",
        background=tv_bg,
        foreground=tv_fg,
        fieldbackground=tv_bg,
        rowheight=28,
        font=("Segoe UI", 10)
    )
    style.configure(
        "Admin.Treeview.Heading",
        background=hd_bg,
        foreground=hd_fg,
        font=("Segoe UI", 10, "bold"),
        padding=5
    )
    style.map(
        "Admin.Treeview",
        background=[("selected", BLUE)],
        foreground=[("selected", "#ffffff")]
    )

    style.configure(
        "Admin.Vertical.TScrollbar",
        gripcount=0,
        background="#475569" if is_dark else "#94a3b8",
        darkcolor=c["BG_COLOR"],
        lightcolor=c["BG_COLOR"],
        troughcolor=c["BG_COLOR"],
        bordercolor=c["BORDER"],
        arrowcolor=tv_fg,
        arrowsize=13,
        width=13
    )
    style.map(
        "Admin.Vertical.TScrollbar",
        background=[("active", BLUE), ("pressed", GREEN)]
    )


def _show_import_result_dialog(parent_win, title_str: str, pkg_name: str, report: dict):
    """Yükleme sonunda kaç kaydın eklendiğini ve hatalı olduğu için atlanan satır numaralarını gösterir."""
    added = report.get("added", 0)
    skipped_cnt = report.get("skipped_count", 0)
    skipped_lines = report.get("skipped_lines", [])

    if skipped_cnt == 0:
        messagebox.showinfo(
            title_str,
            f"✅ '{pkg_name}' başarıyla yüklendi!\n\n"
            f"• Eklenen / Güncellenen Kayıt: {added} adet\n"
            f"• Hatalı / Atlanan Kayıt: 0 (Tüm satırlar kusursuz aktarıldı)",
            parent=parent_win
        )
    else:
        lines_preview = "\n  - ".join(skipped_lines[:15])
        if len(skipped_lines) > 15:
            lines_preview += f"\n  ... ve {len(skipped_lines) - 15} satır daha."
        messagebox.showwarning(
            f"{title_str} — Kısmi Yükleme Raporu",
            f"📦 Paket: {pkg_name}\n\n"
            f"✅ Başarıyla Eklenen Kayıt: {added} adet\n"
            f"⚠️ Hatalı / Eksik Olduğu İçin Atlanan: {skipped_cnt} adet\n\n"
            f"Atlanan Satırlar ve Sebepleri:\n  - {lines_preview}\n\n"
            f"💡 İpucu: Atlanan satırlarda Arapça veya Türkçe çeviri sütununun boş olmadığından emin olun.",
            parent=parent_win
        )


def _ask_import_mode_dialog(parent_win, app, file_path: str, section_label: str) -> Optional[bool]:
    c = app.colors
    fname = os.path.basename(file_path)
    result: list = [None]

    dlg = tk.Toplevel(parent_win)
    dlg.title("📥 İçe Aktarma (Yükleme) Modunu Seçin")
    dlg.geometry("640x360")
    dlg.resizable(False, False)
    dlg.configure(bg=c["CARD_COLOR"])
    dlg.transient(parent_win)
    dlg.grab_set()

    hdr = tk.Frame(dlg, bg=c["PANEL_COLOR"], padx=18, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text=f"📥 PAKET YÜKLEME SEÇENEĞİ — {section_label}",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold")
    ).pack(anchor="w")

    body = tk.Frame(dlg, bg=c["CARD_COLOR"], padx=20, pady=12)
    body.pack(fill="both", expand=True)

    tk.Label(
        body,
        text=f"Seçilen Dosya: {fname}\nBu dosyayı veritabanına nasıl aktarmak istiyorsunuz?",
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, justify="left"
    ).pack(anchor="w", pady=(0, 10))

    def choose_merge():
        result[0] = False
        dlg.destroy()

    def choose_reset():
        if messagebox.askyesno(
            "Tam Sıfırlama Onayı",
            f"DİKKAT: '{section_label}' bölümündeki mevcut tüm kayıtlar silinecek ve yalnızca '{fname}' dosyasındaki veriler yüklenecektir.\n\nOnaylıyor musunuz?",
            parent=dlg
        ):
            result[0] = True
            dlg.destroy()

    btn_merge = tk.Button(
        body,
        text="🔄 1) Birleştir ve Güncelle (Önerilen)\n      Mevcut kayıtları korur, aynı ID'ye sahip olanların üzerine yazar",
        command=choose_merge, bg=GREEN, fg="#0f172a", font=("Segoe UI", 10, "bold"),
        relief="flat", pady=8, padx=14, cursor="hand2", anchor="w", justify="left"
    )
    btn_merge.pack(fill="x", pady=4)

    btn_reset = tk.Button(
        body,
        text="🧹 2) Mevcut Veritabanını Temizle ve Yalnızca Bu Dosyayı Yükle\n      Eski kayıtları tamamen siler, sıfırdan sadece bu paketi kurar",
        command=choose_reset, bg=RED, fg="white", font=("Segoe UI", 10, "bold"),
        relief="flat", pady=8, padx=14, cursor="hand2", anchor="w", justify="left"
    )
    btn_reset.pack(fill="x", pady=4)

    tk.Button(
        body, text="İptal / Vazgeç", command=dlg.destroy,
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B,
        relief="flat", pady=6, cursor="hand2"
    ).pack(fill="x", pady=(8, 0))

    parent_win.wait_window(dlg)
    return result[0]


def open_clipboard_paste_dialog(parent_win, app, mode: str, default_pkg_id: str, on_done_cb):
    """
    Öğretmenin Excel veya Word'den kopyaladığı satırları doğrudan yapıştırıp
    anında veritabanına aktarabileceği '📋 Excel / Panodan Yapıştır' penceresi.
    """
    c = app.colors
    pwin = tk.Toplevel(parent_win)
    pwin.title("📋 Excel veya Panodan (Clipboard) Doğrudan Yapıştır ve Ekle")
    pwin.geometry("780x560")
    pwin.minsize(700, 500)
    pwin.configure(bg=c["CARD_COLOR"])
    pwin.transient(parent_win)
    pwin.grab_set()

    mode_titles = {
        "words": "🏠 Kelime Paketi (Arapça [Tab/;] Türkçe)",
        "sentences": "📜 Anahtar Cümleler (Arapça Cümle [Tab/;] Türkçe Anlamı)",
        "dialogue": "💬 Diyalog Satırları (Konuşmacı [Tab/;] Arapça [Tab/;] Türkçe)"
    }

    hdr = tk.Frame(pwin, bg=c["PANEL_COLOR"], padx=20, pady=10)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text=f"📋 EXCEL / PANODAN HIZLI YAPIŞTIR — {mode_titles.get(mode, mode)}",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold")
    ).pack(side="left")

    top_cfg = tk.Frame(pwin, bg=c["CARD_COLOR"], padx=20, pady=8)
    top_cfg.pack(fill="x")

    tk.Label(top_cfg, text="Hedef Paket ID:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    init_pid = default_pkg_id if default_pkg_id and default_pkg_id != "all" else ("excel_kelimeler" if mode == "words" else ("excel_cumleler" if mode == "sentences" else "dlg_excel"))
    ent_pid = tk.Entry(top_cfg, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"], width=18)
    ent_pid.insert(0, init_pid)
    ent_pid.pack(side="left", padx=(4, 12))

    tk.Label(top_cfg, text="Paket Başlığı:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    ent_pad = tk.Entry(top_cfg, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"], width=22)
    ent_pad.insert(0, init_pid.replace("_", " ").title())
    ent_pad.pack(side="left", padx=(4, 12))

    tk.Label(top_cfg, text="Seviye:", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    lvl_var = tk.StringVar(value="A1")
    ttk.Combobox(top_cfg, textvariable=lvl_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=5, font=FONT_UI_B).pack(side="left", padx=4)

    info_lbl = tk.Label(
        pwin,
        text="💡 Excel tablonuzdaki satırları kopyalayıp (Ctrl+C) aşağıdaki alana yapıştırın (Ctrl+V).\n"
             "   Desteklenen formatlar: 'Arapça [Tab veya ;] Türkçe' ya da 'ID [Tab/;] Arapça [Tab/;] Türkçe [Tab/;] Seviye'",
        bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 9, "bold"), justify="left"
    )
    info_lbl.pack(anchor="w", padx=20, pady=(0, 6))

    txt_box_frame = tk.Frame(pwin, bg=c["BG_COLOR"], highlightthickness=1, highlightbackground=c["BORDER"])
    txt_box_frame.pack(fill="both", expand=True, padx=20, pady=4)

    txt_paste = tk.Text(
        txt_box_frame, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=("Consolas", 11),
        wrap="none", padx=10, pady=10, insertbackground=c["TEXT_MAIN"], undo=True
    )
    sb_v = ttk.Scrollbar(txt_box_frame, orient="vertical", command=txt_paste.yview)
    txt_paste.configure(yscrollcommand=sb_v.set)
    txt_paste.pack(side="left", fill="both", expand=True)
    sb_v.pack(side="right", fill="y")

    def pull_from_os_clipboard():
        try:
            clip_data = pwin.clipboard_get()
            if clip_data:
                txt_paste.insert(tk.END, clip_data + ("\n" if not clip_data.endswith("\n") else ""))
        except Exception:
            messagebox.showinfo("Pano Boş", "Panoda metin bulunamadı. Excel'den kopyalayıp tekrar deneyin.", parent=pwin)

    btn_bar = tk.Frame(pwin, bg=c["CARD_COLOR"], padx=20, pady=12)
    btn_bar.pack(fill="x", side="bottom")

    def run_paste_import():
        raw_content = txt_paste.get("1.0", tk.END).strip()
        if not raw_content:
            messagebox.showwarning("Metin Yok", "Lütfen önce Excel'den kopyaladığınız satırları metin alanına yapıştırın.", parent=pwin)
            return
        pid = re.sub(r"[^a-z0-9_]", "", ent_pid.get().strip().lower()) or "excel_paket"
        pad = ent_pad.get().strip() or pid.title()
        rep = import_from_clipboard_text(mode, raw_content, pid, pad, lvl_var.get())
        on_done_cb(pid)
        _show_import_result_dialog(pwin, "📋 Excel / Pano Aktarım Raporu", pad, rep)
        if rep.get("added", 0) > 0:
            pwin.destroy()

    tk.Button(
        btn_bar, text="📥 PANODAKİ VERİLERİ VERİTABANINA AKTAR", command=run_paste_import,
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 10, "bold"), relief="flat", padx=16, pady=8, cursor="hand2"
    ).pack(side="left", fill="x", expand=True, padx=(0, 6))

    tk.Button(
        btn_bar, text="📋 Panodan Otomatik Çek (Ctrl+V)", command=pull_from_os_clipboard,
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2"
    ).pack(side="left", padx=4)

    tk.Button(
        btn_bar, text="🧹 Temizle", command=lambda: txt_paste.delete("1.0", tk.END),
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=10, pady=8, cursor="hand2"
    ).pack(side="left", padx=4)

    tk.Button(
        btn_bar, text="Kapat", command=pwin.destroy,
        bg=RED, fg="white", font=FONT_UI_B, relief="flat", padx=14, pady=8, cursor="hand2"
    ).pack(side="right")


def open_package_preparation_guide(parent_win, app):
    """
    Veritabanına içerik eklemenin tüm yöntemlerini (Tekil Ekleme, Excel Yapıştırma,
    CSV/JSON/ZIP Paket Formatları, Sarf Sözlüğü, Toplu Görsel/Ses Eşleştirme ve Seviye Araçları)
    örnek şablonlarla ayrıntılı olarak anlatan kapsamlı Paket ve İçerik Rehberi.
    """
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    gwin = tk.Toplevel(parent_win)
    gwin.title("📘 Veritabanına İçerik Ekleme, Paket, Sözlük ve Medya Hazırlama Rehberi")

    w_px, h_px = 940, 700
    sw = gwin.winfo_screenwidth()
    sh = gwin.winfo_screenheight()
    pos_x = max(0, (sw - w_px) // 2)
    pos_y = max(0, (sh - h_px) // 2 - 20)
    gwin.geometry(f"{w_px}x{h_px}+{pos_x}+{pos_y}")
    gwin.minsize(820, 560)
    gwin.configure(bg=c["CARD_COLOR"])
    gwin.transient(parent_win)
    gwin.grab_set()

    hdr = tk.Frame(gwin, bg=c["PANEL_COLOR"], padx=20, pady=12)
    hdr.pack(fill="x")
    tk.Label(
        hdr,
        text="📘 VERİTABANINA İÇERİK EKLEME, PAKET, SÖZLÜK VE MEDYA REHBERİ",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")
    ).pack(side="left")
    tk.Label(
        hdr,
        text="Kelime • Diyalog • Cümle • Sarf Sözlüğü",
        bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B
    ).pack(side="right")

    txt_holder = tk.Frame(
        gwin, bg=c["BG_COLOR"], padx=2, pady=2,
        highlightthickness=1, highlightbackground=c["BORDER"]
    )
    txt_holder.pack(fill="both", expand=True, padx=20, pady=10)

    txt = tk.Text(
        txt_holder, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 10),
        wrap="word", padx=20, pady=15, bd=0, highlightthickness=0
    )
    sb = ttk.Scrollbar(txt_holder, orient="vertical", command=txt.yview, style="Admin.Vertical.TScrollbar")
    txt.configure(yscrollcommand=sb.set)
    txt.pack(side="left", fill="both", expand=True)
    sb.pack(side="right", fill="y")

    h1_col = YELLOW if is_dark else "#1e3a8a"
    h2_col = GREEN if is_dark else "#047857"
    code_fg = "#60a5fa" if is_dark else "#1d4ed8"
    code_bg = "#162032" if is_dark else "#e2e8f0"
    warn_fg = "#fbbf24" if is_dark else "#b45309"

    txt.tag_configure("h1", foreground=h1_col, font=("Segoe UI", 12, "bold"), spacing1=14, spacing3=5)
    txt.tag_configure("h2", foreground=h2_col, font=("Segoe UI", 10, "bold"), spacing1=8, spacing3=3)
    txt.tag_configure("body", foreground=c["TEXT_MAIN"], font=("Segoe UI", 10), spacing2=3)
    txt.tag_configure("bullet", foreground=c["TEXT_MAIN"], font=("Segoe UI", 10), lmargin1=16, lmargin2=30, spacing2=3)
    txt.tag_configure("code", foreground=code_fg, background=code_bg, font=("Consolas", 10), lmargin1=16, lmargin2=16, spacing1=4, spacing3=6)
    txt.tag_configure("note", foreground=warn_fg, font=("Segoe UI", 9, "bold"), lmargin1=16, lmargin2=16, spacing1=4, spacing3=6)

    sections = [
        ("📌 GENEL BAKIŞ: VERİTABANINA İÇERİK EKLEMENİN 5 FARKLI YOLU\n", "h1"),
        ("Yönetim Panelinde 3 ayrı veritabanı sekmesi bulunur: 🏠 Kelimeler (kelimeler.db), 💬 Diyaloglar (diyaloglar.db) ve 📜 Anahtar Cümleler (cumleler.db). Bu veritabanlarına şu 5 yöntemden biriyle içerik ekleyebilirsiniz:\n"
         "  1) Alt Formdan Tekil Kayıt Ekleme ve Mikrofondan Ses Kaydetme\n"
         "  2) Excel veya Word Tablosundan Doğrudan Kopyala-Yapıştır (📋 Excel Yapıştır)\n"
         "  3) Hazır CSV veya JSON Dosyası Yükleme (📥 Paket Yükle)\n"
         "  4) Görselli ve Sesli Tam Paket Arşivi (.ZIP) Yükleme (📥 Paket Yükle)\n"
         "  5) Sarf Sözlüğü (sozluk.json) ve Klasörden Toplu Medya Eşleştirme (🖼️ Toplu Medya Eşleştir)\n", "body"),

        ("1. YÖNTEM: ALT FORMDAN TEKİL KAYIT EKLEME, GÜNCELLEME VE MİKROFON KAYDI\n", "h1"),
        ("• Yeni Kayıt Ekleme (➕ Yeni Kayıt Olarak Ekle): ", "h2"),
        ("Panelin en altındaki formda 'Konuşmacı / Paket' kutusuna paket adını (örn: hayvanlar, gunluk_kelimeler), '🏷️ Seviye' kutusuna CEFR seviyesini (A1-C1), 'Türkçe' ve 'Arapça (RTL)' kutularına metinleri yazıp '➕ Yeni Kayıt Olarak Ekle' butonuna basın.\n", "bullet"),
        ("• Mevcut Kaydı Düzenleme (💾 Seçili Satırı Güncelle): ", "h2"),
        ("Tablodan herhangi bir satıra tıkladığınızda bilgiler alt forma dolar. Değişiklikleri yapıp '💾 Seçili Satırı Güncelle' butonuna basın.\n", "bullet"),
        ("• Ses ve Görsel Bağlama (🎙️ Kaydet / 📁 Ses Seç / 🌐 URL / 🖼️ Seç): ", "h2"),
        ("Bir kayda kendi sesinizi mikrofondan doğrudan kaydetmek için '🎙️ Kaydet' butonuna basıp konuşun ve '⏹️ Kaydı Bitir' deyin. Ayrıca bilgisayarınızdan .mp3/.wav seçebilir veya doğrudan bir ses URL'si (https://.../ses.mp3) yapıştırıp '🌐 URL' butonuyla indirebilirsiniz.\n", "bullet"),

        ("2. YÖNTEM: EXCEL VEYA WORD TABLOSUNDAN DOĞRUDAN KOPYALA-YAPIŞTIR (📋 Excel Yapıştır)\n", "h1"),
        ("Dosya kaydetmekle uğraşmadan Excel'deki hücreleri seçip kopyalayarak (Ctrl+C) doğrudan sisteme yapıştırabilirsiniz:\n", "body"),
        ("• Kelime ve Cümleler İçin Excel Sütun Düzeni (2, 3 veya 4 Sütun): ", "h2"),
        ("Excel'de yan yana şu sütun biçimlerinden herhangi birini kullanabilirsiniz (Tab veya noktalı virgül otomatik tanınır):\n", "bullet"),
        ("  Biçim A (2 Sütun — Arapça | Türkçe):\n"
         "  كِتَابٌ\tKitap\n"
         "  مَدْرَسَةٌ\tOkul\n\n"
         "  Biçim B (3 Sütun — Arapça | Türkçe | Seviye):\n"
         "  مُحَاضَرَةٌ\tKonferans\tB1\n\n"
         "  Biçim C (4 Sütun — ID | Arapça | Türkçe | Seviye):\n"
         "  w_101\tمُسْتَشْفَى\tHastane\tA2\n", "code"),
        ("• Diyalog Satırları İçin Excel Sütun Düzeni: ", "h2"),
        ("Diyalog sekmesinde yapıştırırken 2 sütun (Arapça | Türkçe), 3 sütun (Konuşmacı | Arapça | Türkçe) veya 4 sütun (Konuşmacı | Cinsiyet E/K | Arapça | Türkçe) kullanabilirsiniz:\n", "bullet"),
        ("  Ömer\tE\tالسَّلَامُ عَلَيْكُمْ.\tSelamün aleyküm.\n"
         "  Deniz\tK\tوَعَلَيْكُمُ السَّلَامُ، أَهْلاً بِكَ.\tAleyküm selam, hoş geldiniz.\n", "code"),
        ("💡 Yükleme Raporu: Eksik veya boş bırakılan satırlar sistem tarafından atlanır ve işlem sonunda hangi satırların neden atlandığı (Örn: Satır 14 - Türkçe alanı boş) detaylı olarak raporlanır.\n", "note"),

        ("3. YÖNTEM: CSV VE JSON DOSYALARI İLE PAKET YÜKLEME (📥 Paket Yükle)\n", "h1"),
        ("• Birleştir / Güncelle vs. Tam Sıfırlama Seçeneği: ", "h2"),
        ("'📥 Paket Yükle' butonuna bastığınızda 2 seçenek sunulur:\n"
         "  1) 🔄 Birleştir ve Güncelle (Önerilen): Mevcut paketlerinizi silmez; yalnızca yeni dosyadaki kayıtları ekler ve aynı ID'ye sahip kayıtların üzerine yazar.\n"
         "  2) 🧹 Mevcut Veritabanını Temizle ve Yalnızca Bu Dosyayı Yükle: Seçili sekmedeki eski kayıtları tamamen siler ve sıfırdan sadece bu dosyayı kurar.\n", "bullet"),
        ("• A) Kelime Paketi CSV Şablonu ('📄 Şablon' butonuyla da indirebilirsiniz): ", "h2"),
        ("  id;arapca;turkce;gorsel;ses;seviye\n"
         "  ari;نَحْلَةٌ;Arı;ari.jpg;ari_f.mp3;A1\n"
         "  kitap;كِتَابٌ;Kitap;;;A1\n", "code"),
        ("• B) Anahtar Cümleler CSV Şablonu: ", "h2"),
        ("  id;arapca;turkce;ses;seviye\n"
         "  s_01;أُسْلُوبُ الْكَاتِبِ مُمَيَّزٌ.;Yazarın üslubu ayırt edicidir.;;B1\n"
         "  s_02;الْوَقْتُ كَالسَّيْفِ.;Vakit kılıç gibidir.;;A2\n", "code"),
        ("• C) Diyalog Paketi JSON Şablonu: ", "h2"),
        ("  [\n"
         "    {\n"
         "      \"id\": \"dlg_tanisma\",\n"
         "      \"baslik_ar\": \"اَلتَّعَارُفُ\",\n"
         "      \"baslik_tr\": \"Tanışma\",\n"
         "      \"seviye\": \"A1\",\n"
         "      \"konu\": \"Günlük Hayat\",\n"
         "      \"satirlar\": [\n"
         "        {\"sira\": 1, \"konusmaci\": \"Ömer\", \"cinsiyet\": \"E\", \"arapca\": \"مَرْحَباً.\", \"turkce\": \"Merhaba!\", \"ses\": null},\n"
         "        {\"sira\": 2, \"konusmaci\": \"Deniz\", \"cinsiyet\": \"K\", \"arapca\": \"مَرْحَباً، مَا اسْمُكَ؟\", \"turkce\": \"Merhaba, adınız ne?\", \"ses\": null}\n"
         "      ]\n"
         "    }\n"
         "  ]\n", "code"),

        ("4. YÖNTEM: GÖRSELLİ VE SESLİ TAM PAKET (.ZIP) HAZIRLAMA VE DIŞA AKTARMA\n", "h1"),
        ("• Bir paketi görselleri ve sesleriyle birlikte başka bir bilgisayara taşımak veya paylaşmak için:\n"
         "  - Bir klasör içinde 'kelimeler.csv' (veya 'paket.json') dosyanızı oluşturun.\n"
         "  - Aynı klasörün içine 'images/' (görseller için) ve 'sounds/' (sesler için) alt klasörlerini koyup tümünü .ZIP yapın.\n"
         "  - Kadın ve Erkek referans seslerini birlikte eklemek isterseniz ses dosyalarını 'id_f.mp3' (Kadın) ve 'id_m.mp3' (Erkek) şeklinde adlandırın (Örn: ari_f.mp3 ve ari_m.mp3).\n"
         "  - Ayrıca mevcut bir paketinizi '📤 Dışa Aktar (.ZIP)' butonuyla tek tıkla görsel ve sesleriyle birlikte hazır .ZIP paketine dönüştürebilirsiniz.\n", "body"),

        ("5. YÖNTEM: SARF SÖZLÜĞÜ (sozluk.json) YÜKLEME VE FORMATI (📖 Sözlük Yükle)\n", "h1"),
        ("• '📖 Sözlük Yükle (sozluk.json)' butonuyla yüklenen isim (Müfret/Cemî) ve fiil (Mâzî/Muzâri/Masdar) kayıtları hem 'sozluk.db' içine hem de Kelime Moduna otomatik entegre edilir.\n"
         "• Sistem Türkçe anlamlara göre kelimeleri otomatik olarak tematik kategorilere (🏥 Sağlık, 🍽️ Yemek, ✈️ Seyahat, 🎓 Eğitim vb.) ayırır ve kart üzerinde Altıgen Sülâsî Kök Rozeti + Tıklanabilir Türev Ağacı oluşturur.\n", "body"),
        ("  sozluk.json Örnek Formatı (İsim ve Fiil):\n"
         "  [\n"
         "    {\n"
         "      \"id\": \"kelime_001\", \"tur\": \"isim\", \"kok\": \"ط ب ب\",\n"
         "      \"mufret\": \"طَبِيبٌ\", \"cemi\": \"أَطِبَّاءُ\", \"anlam_tr\": \"doktor, hekim\"\n"
         "    },\n"
         "    {\n"
         "      \"id\": \"kelime_002\", \"tur\": \"fiil\", \"kok\": \"ك ت ب\",\n"
         "      \"mazi\": \"كَتَبَ\", \"muzari\": \"يَكْتُبُ\", \"masdar\": \"كِتَابَةٌ\", \"anlam_tr\": \"yazmak\"\n"
         "    }\n"
         "  ]\n", "code"),

        ("6. YÖNTEM: TOPLU GÖRSEL VE SES KLASÖRÜ EŞLEŞTİRME (🖼️ Toplu Medya Eşleştir)\n", "h1"),
        ("• Kelimeleri Excel veya CSV ile metin olarak yükledikten sonra görsel ve sesleri tek tek seçmek zorunda değilsiniz!\n"
         "• Bilgisayarınızda içinde görsel (.jpg, .png, .webp) ve ses (.mp3, .wav) dosyalarının bulunduğu bir klasör hazırlayın.\n"
         "• Dosya isimlerini kelimenin ID'si (örn: w_1.jpg) VEYA Türkçe adı (örn: ari.jpg, kopek.png, ari_f.mp3) olarak verin.\n"
         "• '🖼️ Toplu Medya Eşleştir' butonuna basıp klasörü seçtiğinizde sistem Türkçe karakterleri (ç->c, ğ->g, ı->i, ö->o, ş->s, ü->u) otomatik normalize ederek tüm görsel ve sesleri ilgili kelimelerle eşleştirir.\n", "body"),

        ("7. TOPLU SEVİYE ATAMA, SIRALAMA, KALİTE DENETİMİ VE METİN DOKTORU\n", "h1"),
        ("• 🔤 Sütun Başlığına Tıklayarak Sıralama (A➔Z / Z➔A): ", "h2"),
        ("Tablodaki ID, Seviye, Paket, Konuşmacı, Türkçe veya Arapça sütun başlıklarına tıklayarak listeyi artan (▲) veya azalan (▼) şekilde sıralayabilirsiniz.\n", "bullet"),
        ("• 🏷️ Çoklu Seçim (Shift / Ctrl) ile Toplu Seviye Değiştirme: ", "h2"),
        ("Tabloda Shift veya Ctrl tuşuna basılı tutarak birden fazla satır seçin, alt formdan hedef seviyeyi (A1-C1) belirleyip '🏷️ Seçililere Ata' butonuna basın.\n", "bullet"),
        ("• 🎯 Tahmini Seviye Ata (A1-C1): ", "h2"),
        ("Kelimelerin sarf vezinlerine (sülâsî/mezîd) ve cümlelerin bağlaç/uzunluk yapısına göre tüm veritabanını otomatik olarak A1..C1 arasında seviyelendirir.\n", "bullet"),
        ("• 🔍 Denetim (Kalite Denetleyicisi) ve 🪄 Metin Doktoru: ", "h2"),
        ("'🔍 Denetim' butonu harekesiz metinleri, eksik çevirileri ve kırık ses dosyalarını bulur (çift tıklayarak kayda gidebilirsiniz). '🪄 Metin Doktoru' ise Arapça metinlerde RTL hizalamayı bozan parantez içi sayıları ve Türkçe sonundaki dipnot rakamlarını tek tıkla temizler.\n", "bullet"),
        ("• ⚡ TTS Hazırla: ", "h2"),
        ("Listelenen kayıtların yapay zeka (Edge-TTS) seslerini önceden indirip önbelleğe alır; böylece sınıfta internet olmasa bile uygulama çevrimdışı (offline) seslendirme yapar.\n", "bullet")
    ]

    for content, tag in sections:
        txt.insert(tk.END, content, tag)
    txt.config(state="disabled")

    app._bind_mousewheel(txt, txt)

    tk.Button(
        gwin, text="✅ Anladım, Rehberi Kapat", command=gwin.destroy,
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 11, "bold"),
        relief="flat", pady=8, cursor="hand2"
    ).pack(fill="x", padx=20, pady=(0, 15))
    


def open_quality_audit_window(parent_win, app, mode: str, paket_id: str, dialogue_id: str, on_select_id_callback):
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    res = audit_package_quality(mode, paket_id, dialogue_id)

    awin = tk.Toplevel(parent_win)
    awin.title("🔍 Paket Kalite Denetimi Raporu")
    awin.geometry("920x560")
    awin.configure(bg=c["CARD_COLOR"])
    awin.transient(parent_win)
    awin.grab_set()

    hdr = tk.Frame(awin, bg=c["PANEL_COLOR"], padx=20, pady=12)
    hdr.pack(fill="x")
    tk.Label(hdr, text="🔍 PAKET KALİTE DENETLEYİCİSİ RAPORU", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")).pack(side="left")

    summary_col = GREEN if res["issue_count"] == 0 else YELLOW
    tk.Label(
        hdr,
        text=(
            f"Taranan: {res['total_checked']}   |   "
            f"Kusurlu Kayıt: {res['issue_count']}   |   "
            f"Harekesiz: {res['missing_haraka']}   |   "
            f"Çeviri Eksik/Hatalı: {res['missing_tr']}   |   "
            f"Kırık Ses: {res['broken_audio']}"
        ),
        bg=c["PANEL_COLOR"], fg=summary_col if is_dark else "#0f172a", font=FONT_UI_B
    ).pack(side="right")

    tk.Label(
        awin,
        text="💡 İpucu: Aşağıdaki listeden bir satıra çift tıklayarak Yönetim Panelinde doğrudan o kayda gidip düzeltebilirsiniz.",
        bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI
    ).pack(anchor="w", padx=20, pady=(8, 4))

    t_frame = tk.Frame(awin, bg=c["BG_COLOR"], bd=1, relief="solid")
    t_frame.pack(fill="both", expand=True, padx=20, pady=6)

    cols = ("id", "grp", "tr", "ar", "issues")
    tree_q = ttk.Treeview(t_frame, columns=cols, show="headings", height=13, style="Admin.Treeview")
    tree_q.heading("id", text="ID")
    tree_q.heading("grp", text="Seviye & Paket / Diyalog")
    tree_q.heading("tr", text="Türkçe")
    tree_q.heading("ar", text="Arapça Metin")
    tree_q.heading("issues", text="Tespit Edilen Kusurlar")

    tree_q.column("id", width=70, anchor="center")
    tree_q.column("grp", width=180, anchor="w")
    tree_q.column("tr", width=210, anchor="w")
    tree_q.column("ar", width=210, anchor="e")
    tree_q.column("issues", width=210, anchor="w")

    sb_q = ttk.Scrollbar(t_frame, orient="vertical", command=tree_q.yview, style="Admin.Vertical.TScrollbar")
    tree_q.configure(yscrollcommand=sb_q.set)
    tree_q.pack(side="left", fill="both", expand=True)
    sb_q.pack(side="right", fill="y")

    if res["issue_count"] == 0:
        tree_q.insert("", tk.END, values=("-", "-", "Tebrikler! Hiçbir kalite kusuru bulunmadı.", "مُمْتَازٌ", "✅ Tam Uyumlu"))
    else:
        for rid, grp, tr, ar, prob_str in res["issues"]:
            tree_q.insert("", tk.END, iid=str(rid), values=(rid, grp, tr, ar, prob_str))

    def on_double_click(event):
        sel = tree_q.selection()
        if not sel:
            return
        target_iid = sel[0]
        if target_iid and target_iid != "-":
            on_select_id_callback(target_iid)
            awin.destroy()

    tree_q.bind("<Double-1>", on_double_click)

    tk.Button(
        awin, text="Kapat", command=awin.destroy,
        bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", pady=8, cursor="hand2"
    ).pack(fill="x", padx=20, pady=(4, 14))


def open_unified_admin_panel(app, initial_tab="words"):
    c = app.colors
    is_dark = (app.theme_mode == "dark")
    if initial_tab not in ("words", "dialogue", "sentences"):
        initial_tab = "words"

    win = tk.Toplevel(app.root)
    win.title("🛠️ Yönetim & Paket Paneli (Admin)")

    if hasattr(app, "_register_singleton_dialog"):
        app._register_singleton_dialog(win)

    sw = win.winfo_screenwidth()
    sh = win.winfo_screenheight()
    safe_w = min(1180, int(sw * 0.90))
    safe_h = min(700, int(sh * 0.84))
    pos_x = max(0, (sw - safe_w) // 2)
    pos_y = max(0, (sh - safe_h) // 2 - 20)
    win.geometry(f"{safe_w}x{safe_h}+{pos_x}+{pos_y}")
    win.minsize(980, 620)
    win.resizable(True, True)
    win.configure(bg=c["CARD_COLOR"])

    try:
        win.state("zoomed")
    except Exception:
        pass

    win.lift()
    win.focus_force()

    _configure_admin_styles(win, app)
    mic_recorder = AdminMicRecorder()

    def _on_admin_close():
        if mic_recorder.recording:
            try:
                mic_recorder.stop_and_save("cancel")
            except Exception as ex:
                log_error("Admin kapanış mikrofon durdurma hatası", ex)
        win.destroy()

    win.protocol("WM_DELETE_WINDOW", _on_admin_close)

    header = tk.Frame(win, bg=c["CARD_COLOR"])
    header.pack(fill="x", padx=20, pady=(8, 2))

    tk.Label(header, text="🛠️ YÖNETİM & PAKET PANELİ", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 14, "bold")).pack(side="left")

    lbl_tts_progress = tk.Label(header, text="", bg=c["CARD_COLOR"], fg=GREEN if is_dark else "#047857", font=FONT_UI_B)
    lbl_tts_progress.pack(side="left", padx=14)

    def run_doctor():
        fixed = repair_all_databases()
        app._refresh_current_mode_data()
        load_data_and_render(reset_page=False)
        messagebox.showinfo(
            "🪄 Veritabanı Metin Doktoru",
            f"Veritabanı taraması tamamlandı!\n\n"
            f"• Onarılan Kayıt Sayısı: {fixed}\n"
            f"• Arapça parantez içi rakamlar ve Türkçe cümle sonundaki (. 81, . 47 vb.) sayfa numaraları temizlendi.",
            parent=win
        )

    def run_sozluk_importer():
        default_cand = DEFAULT_SOZLUK_JSON_PATH if os.path.exists(DEFAULT_SOZLUK_JSON_PATH) else ""
        use_default = False
        if default_cand:
            ans_def = messagebox.askyesnocancel(
                "📖 Sarf Sözlüğü Yükle / Güncelle (sozluk.json)",
                f"Proje klasörünüzde hazır 'sozluk.json' dosyası bulundu:\n{default_cand}\n\n"
                "• [EVET] : Bu sozluk.json dosyasını yükle / güncelle.\n"
                "• [HAYIR] : Bilgisayardan başka bir sözlük (.json) dosyası seç.\n"
                "• [İPTAL] : Vazgeç.",
                parent=win
            )
            if ans_def is None:
                return
            use_default = ans_def

        json_p = default_cand if use_default else filedialog.askopenfilename(
            title="Sarf Sözlüğü JSON Dosyasını Seçin (sozluk.json)",
            filetypes=[("Sözlük JSON Dosyası", "*.json")],
            parent=win
        )
        if not json_p:
            return

        clear_flag = _ask_import_mode_dialog(win, app, json_p, "📖 Sarf Sözlüğü (sozluk.db & Kelime Paketleri)")
        if clear_flag is None:
            return

        try:
            res = import_sozluk_json(json_p, clear_existing=clear_flag)
            switch_tab("words")
            populate_filters(keep_selection="sozluk_tumu")
            load_data_and_render(reset_page=True)
            app._refresh_current_mode_data()
            messagebox.showinfo(
                "📖 Sarf Sözlüğü Entegre Edildi",
                f"Sarf sözlüğü başarıyla işlendi ve Kelime Moduna bağlandı!\n\n"
                f"• İşlenen Sözlük Maddesi: {res['sozluk_entries']}\n"
                f"• Tekilleştirilen Kelime Kartı: {res['unique_word_cards']}\n"
                f"• Otomatik Oluşturulan Tematik Kategori: {res['categories']} adet",
                parent=win
            )
        except Exception as ex:
            log_error("Sözlük içe aktarma hatası", ex)
            messagebox.showerror("Sözlük Yükleme Hatası", str(ex), parent=win)

    def run_auto_leveler():
        tab_titles = {
            "words": "🏠 Kelimeler (kelimeler.db)",
            "dialogue": "💬 Diyaloglar (diyaloglar.db)",
            "sentences": "📜 Anahtar Cümleler (cumleler.db)"
        }
        cur_tab_key = current_tab.get()
        cur_tab_label = tab_titles.get(cur_tab_key, cur_tab_key)

        ans = messagebox.askyesnocancel(
            "🎯 Tahmini CEFR Seviye Ata (Ön Tasnif: A1 - C1)",
            "Veritabanındaki kayıtlar iletişimsel kalıp, Arapça sarf vezni (mezîd bâblar), bağlaç yapısı ve kelime sayısına göre otomatik olarak A1, A2, B1, B2 ve C1 seviyelerine tasnif edilecektir:\n\n"
            "• [EVET] : Her 3 veritabanını da (Kelimeler + Diyaloglar + Cümleler) şimdi tasnif et.\n"
            f"• [HAYIR] : Sadece şu an açık olan '{cur_tab_label}' sekmesini tasnif et.\n"
            "• [İPTAL] : İşlemden vazgeç.",
            parent=win
        )
        if ans is None:
            return

        target = "all" if ans else cur_tab_key
        st = auto_assign_cefr_levels(target)
        populate_filters(keep_selection=get_selected_dlg_id() if cur_tab_key == "dialogue" else get_selected_pkg_id())
        load_data_and_render(reset_page=False)
        app._refresh_current_mode_data()

        def _fmt_dist(d_map: dict) -> str:
            return f"A1: {d_map['A1']}  |  A2: {d_map['A2']}  |  B1: {d_map['B1']}  |  B2: {d_map['B2']}  |  C1: {d_map['C1']}"

        if target == "all":
            report_msg = (
                "Tüm veritabanları dilsel karmaşıklık ve sarf vezinlerine göre başarıyla tasnif edildi!\n\n"
                f"🏠 Kelimeler:\n   {_fmt_dist(st['words'])}\n\n"
                f"💬 Diyaloglar:\n   {_fmt_dist(st['dialogue'])}\n\n"
                f"📜 Anahtar Cümleler:\n   {_fmt_dist(st['sentences'])}"
            )
        else:
            report_msg = (
                f"Yalnızca seçili sekme ({cur_tab_label}) başarıyla tasnif edildi!\n\n"
                f"📊 Seviye Dağılımı:\n   {_fmt_dist(st[target])}"
            )

        messagebox.showinfo("🎯 Tahmini Seviye Tasnifi Tamamlandı", report_msg, parent=win)

    tk.Button(
        header, text="❌ Pencereyi Kapat",
        command=_on_admin_close, bg=RED, fg="white", font=FONT_UI_B, relief="flat", padx=10, pady=4, cursor="hand2"
    ).pack(side="right", padx=(5, 0))

    tk.Button(
        header, text="🪄 Metin Doktoru",
        command=run_doctor, bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=9, pady=4, cursor="hand2"
    ).pack(side="right", padx=(4, 0))

    tk.Button(
        header, text="🎯 Tahmini Seviye Ata (A1-C1)",
        command=run_auto_leveler, bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=9, pady=4, cursor="hand2"
    ).pack(side="right", padx=(4, 0))

    tk.Button(
        header, text="📖 Sözlük Yükle (sozluk.json)",
        command=run_sozluk_importer, bg=YELLOW, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=10, pady=4, cursor="hand2"
    ).pack(side="right", padx=(4, 0))

    tk.Button(
        header, text="📘 Paket Rehberi",
        command=lambda: open_package_preparation_guide(win, app),
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=9, pady=4, cursor="hand2"
    ).pack(side="right")

    # Sekmeler
    tab_bar = tk.Frame(win, bg=c["CARD_COLOR"])
    tab_bar.pack(fill="x", padx=20, pady=4)

    current_tab = tk.StringVar(value=initial_tab)
    btn_tab_w = tk.Button(tab_bar, text="🏠 Kelimeler & Sözlük (kelimeler.db)", command=lambda: switch_tab("words"), font=FONT_UI_B, relief="flat", padx=14, pady=5, cursor="hand2")
    btn_tab_w.pack(side="left", padx=(0, 5))
    btn_tab_d = tk.Button(tab_bar, text="💬 Diyalog Cümleleri (diyaloglar.db)", command=lambda: switch_tab("dialogue"), font=FONT_UI_B, relief="flat", padx=14, pady=5, cursor="hand2")
    btn_tab_d.pack(side="left", padx=5)
    btn_tab_s = tk.Button(tab_bar, text="📜 Anahtar Cümleler (cumleler.db)", command=lambda: switch_tab("sentences"), font=FONT_UI_B, relief="flat", padx=14, pady=5, cursor="hand2")
    btn_tab_s.pack(side="left", padx=5)

    # Paket, Diyalog ve CEFR Seviye/Konu Alanı
    pkg_container = tk.Frame(win, bg=c["PANEL_COLOR"], pady=8, padx=12, highlightthickness=1, highlightbackground=c["BORDER"])
    pkg_container.pack(fill="x", padx=20, pady=4)

    filter_row = tk.Frame(pkg_container, bg=c["PANEL_COLOR"])
    filter_row.pack(fill="x", pady=(0, 6))

    tk.Label(filter_row, text="📦 Paket:", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    pkg_combo = ttk.Combobox(filter_row, state="readonly", width=24, font=FONT_UI)
    pkg_combo.pack(side="left", padx=(4, 10))

    lbl_dlg_filter = tk.Label(filter_row, text="💬 Diyalog:", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B)
    dlg_filter_combo = ttk.Combobox(filter_row, state="readonly", width=30, font=FONT_UI)

    tag_frame = tk.Frame(filter_row, bg=c["PANEL_COLOR"])
    tag_frame.pack(side="right")

    lbl_top_tag = tk.Label(tag_frame, text="🏷️ Diyalog/Paket Seviyesi:", bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B)
    lbl_top_tag.pack(side="left", padx=(4, 2))
    level_var = tk.StringVar(value="A1")
    level_combo = ttk.Combobox(tag_frame, textvariable=level_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=4, font=FONT_UI_B)
    level_combo.pack(side="left", padx=2)

    tk.Label(tag_frame, text="Konu:", bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=(6, 2))
    topic_var = tk.StringVar(value="Genel")
    ent_topic = tk.Entry(tag_frame, textvariable=topic_var, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"], width=12)
    ent_topic.pack(side="left", padx=2)

    def save_pkg_tags():
        tab = current_tab.get()
        if tab == "dialogue":
            target_id = get_selected_dlg_id() if get_selected_dlg_id() != "all" else get_selected_pkg_id()
            if not target_id or target_id == "all":
                messagebox.showwarning("Uyarı", "Diyalog seviyesini değiştirmek için lütfen önce üstten belirli bir Diyalog seçin.", parent=win)
                return
            update_package_level_and_topic("dialogue", target_id, level_var.get(), topic_var.get())
            populate_filters(keep_selection=target_id)
            load_data_and_render(reset_page=False)
            app._refresh_current_mode_data()
            app._show_toast(f"🏷️ Diyalog seviyesi '{level_var.get()}' olarak güncellendi!", GREEN)
        else:
            target_id = get_selected_pkg_id()
            if not target_id or target_id == "all":
                messagebox.showwarning("Uyarı", "Paket etiketi kaydetmek için lütfen önce sol üstten belirli bir Paket seçin.", parent=win)
                return
            apply_all = messagebox.askyesno(
                "Paket Seviyesini Güncelle",
                f"'{pkg_combo.get()}' paketinin seviyesi '{level_var.get()}' olarak kaydedilecek.\n\n"
                f"Bu paketin içindeki TÜM kayıtların seviyesini de topluca '{level_var.get()}' yapmak ister misiniz?",
                parent=win
            )
            update_package_level_and_topic(tab, target_id, level_var.get(), topic_var.get(), apply_to_items=apply_all)
            populate_filters(keep_selection=target_id)
            load_data_and_render(reset_page=False)
            app._refresh_current_mode_data()
            app._show_toast(f"🏷️ Paket etiketi ({level_var.get()}) kaydedildi!", GREEN)

    tk.Button(tag_frame, text="💾 Etiketi Kaydet", command=save_pkg_tags, bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=("Segoe UI", 9, "bold"), relief="flat", padx=8, pady=2, cursor="hand2").pack(side="left", padx=(4, 0))

    action_row = tk.Frame(pkg_container, bg=c["PANEL_COLOR"])
    action_row.pack(fill="x")

    def one_click_import():
        tab = current_tab.get()
        tab_names = {
            "words": "🏠 Kelimeler (kelimeler.db)",
            "dialogue": "💬 Diyaloglar (diyaloglar.db)",
            "sentences": "📜 Anahtar Cümleler (cumleler.db)"
        }
        f_path = filedialog.askopenfilename(
            title="Yüklenecek Paketi Seçin (.zip, .json veya .csv)",
            filetypes=[("Tüm Paket Formatları", "*.zip *.json *.csv")],
            parent=win
        )
        if not f_path:
            return

        clear_flag = _ask_import_mode_dialog(win, app, f_path, tab_names.get(tab, tab))
        if clear_flag is None:
            return

        try:
            pid, pad, cnt = smart_import_package(tab, f_path, clear_existing=clear_flag)
            populate_filters(keep_selection=pid)
            load_data_and_render(reset_page=True)
            app._refresh_current_mode_data()
            rep = get_last_import_report()
            rep["added"] = cnt
            _show_import_result_dialog(win, "📥 Paket Yükleme Raporu", pad, rep)
        except Exception as ex:
            log_error("Paket içe aktarma hatası", ex)
            messagebox.showerror("Paket Yükleme Hatası", str(ex), parent=win)

    def one_click_clipboard_paste():
        tab = current_tab.get()
        cur_p = get_selected_dlg_id() if tab == "dialogue" else get_selected_pkg_id()
        open_clipboard_paste_dialog(
            win, app, tab, cur_p,
            on_done_cb=lambda new_pid: (
                populate_filters(keep_selection=new_pid),
                load_data_and_render(reset_page=True),
                app._refresh_current_mode_data()
            )
        )

    def one_click_bulk_media_match():
        tab = current_tab.get()
        folder = filedialog.askdirectory(
            title="🖼️ Görsel (.jpg/.png) ve Ses (.mp3/.wav) Dosyalarının Bulunduğu Klasörü Seçin",
            parent=win
        )
        if not folder:
            return
        sel_p = get_selected_pkg_id()
        res_m = bulk_match_media_folder(tab, folder, paket_id=sel_p)
        load_data_and_render(reset_page=False)
        app._refresh_current_mode_data()
        messagebox.showinfo(
            "🖼️ Toplu Görsel & Ses Eşleştirme Raporu",
            f"Seçilen klasör tarandı ve kayıtlarla (ID / Türkçe ad) eşleştirildi!\n\n"
            f"• Taranan Medya Dosyası: {res_m['scanned_files']} adet\n"
            f"• Eşleştirilen Görsel Sayısı: {res_m['matched_images']} adet\n"
            f"• Eşleştirilen Ses Dosyası: {res_m['matched_audio']} adet",
            parent=win
        )

    def one_click_template():
        tab = current_tab.get()
        default_name = f"ornek_{tab}_sablonu.csv" if tab != "dialogue" else "ornek_diyalog_sablonu.json"
        ext = ".csv" if tab != "dialogue" else ".json"
        p = filedialog.asksaveasfilename(defaultextension=ext, initialfile=default_name, parent=win)
        if not p:
            return
        with open(p, "w", encoding="utf-8-sig") as f:
            if tab == "words":
                f.write("id;arapca;turkce;gorsel;ses;seviye\nw_1;كِتَابٌ;Kitap;;;A1\nw_2;مُحَاضَرَةٌ;Konferans;;;B1\n")
            elif tab == "sentences":
                f.write("id;arapca;turkce;ses;seviye\ns_1;أُسْلُوبُ الكَاتِبِ مُمَيَّزٌ.;Yazarın üslubu ayırt edicidir.;;B1\n")
            else:
                sample = [{
                    "id": "dlg_ornek", "baslik_ar": "اَلتَّعَارُّفُ", "baslik_tr": "Tanışma", "seviye": "A1", "konu": "Tanışma",
                    "satirlar": [
                        {"sira": 1, "konusmaci": "Ömer", "cinsiyet": "E", "arapca": "مَرْحَباً.", "turkce": "Merhaba!", "ses": None},
                        {"sira": 2, "konusmaci": "Deniz", "cinsiyet": "K", "arapca": "مَرْحَباً، مَا اسْمُكَ؟", "turkce": "Merhaba, adınız ne?", "ses": None}
                    ]
                }]
                json.dump(sample, f, ensure_ascii=False, indent=2)
        messagebox.showinfo("Şablon Hazır", f"Örnek şablon kaydedildi:\n{p}", parent=win)

    def one_click_export():
        tab = current_tab.get()
        sel_pkg = get_selected_pkg_id()
        if not sel_pkg or sel_pkg == "all":
            messagebox.showwarning("Uyarı", "Lütfen dışa aktarmak için sol üstten belirli bir paket seçin.", parent=win)
            return
        save_p = filedialog.asksaveasfilename(
            title="Tam Paketi Kaydet (.ZIP — Görsel, Ses ve Seviyeler Dahil)",
            defaultextension=".zip",
            filetypes=[("Tam Paket Arşivi (.zip)", "*.zip")],
            initialfile=f"{sel_pkg}_tam_paket.zip",
            parent=win
        )
        if not save_p:
            return
        try:
            item_cnt, media_cnt = export_full_package_zip(tab, sel_pkg, pkg_combo.get(), save_p)
            messagebox.showinfo(
                "Tam Paket Dışa Aktarıldı",
                f"Paket tüm görsel, ses ve seviye bilgileriyle birlikte .ZIP olarak kaydedildi!\n\n"
                f"• Dosya: {save_p}\n"
                f"• Toplam Kayıt: {item_cnt}\n"
                f"• Dahil Edilen Görsel/Ses Dosyası: {media_cnt}",
                parent=win
            )
        except Exception as ex:
            log_error("Paket dışa aktarma hatası", ex)
            messagebox.showerror("Dışa Aktarma Hatası", str(ex), parent=win)

    def one_click_delete_pkg():
        tab = current_tab.get()
        sel_pkg = get_selected_pkg_id()
        if not sel_pkg or sel_pkg == "all":
            messagebox.showwarning("Uyarı", "Lütfen silmek için belirli bir paket seçin.", parent=win)
            return
        if messagebox.askyesno("Paketi Sil", f"'{pkg_combo.get()}' paketini ve içindeki tüm kayıtları silmek istediğinize emin misiniz?", parent=win):
            if tab == "words":
                delete_word_package(sel_pkg)
            elif tab == "dialogue":
                delete_dialogue_package(sel_pkg)
            else:
                delete_sentence_package(sel_pkg)
            populate_filters()
            load_data_and_render(reset_page=True)
            app._refresh_current_mode_data()

    def precache_package_tts():
        if not cached_rows:
            return
        if len(cached_rows) > 500:
            if not messagebox.askyesno(
                "Çevrimdışı Ses Hazırlama",
                f"Listede {len(cached_rows)} kayıt var. İlk 300 kaydın TTS seslerini şimdi önbelleğe indirmek ister misiniz?",
                parent=win
            ):
                return
            target_rows = cached_rows[:300]
        else:
            target_rows = list(cached_rows)

        tab = current_tab.get()

        def worker():
            total = len(target_rows)
            done = 0
            for _, vals in target_rows:
                if not win.winfo_exists():
                    break
                ar_txt = vals[5]
                spk_info = vals[3]
                done += 1
                win.after(0, lambda d=done, t=total: lbl_tts_progress.config(text=f"⚡ Çevrimdışı Ses İndiriliyor: {d} / {t}"))
                if tab == "words":
                    get_tts_audio(ar_txt, voice="ar-SA-ZariyahNeural", slow=False)
                    get_tts_audio(ar_txt, voice="ar-SA-HamedNeural", slow=False)
                elif tab == "dialogue":
                    gender = "K" if "(K)" in spk_info else "E"
                    v = detect_voice_for_speaker(spk_info, gender)
                    get_tts_audio(ar_txt, voice=v, slow=False)
                else:
                    get_tts_audio(ar_txt, voice="ar-SA-HamedNeural", slow=False)
            if win.winfo_exists():
                win.after(0, lambda: lbl_tts_progress.config(text=f"✅ {done} kaydın TTS sesi çevrimdışı kullanıma hazır!"))

        threading.Thread(target=worker, daemon=True).start()

    def jump_to_record_from_audit(target_id_str: str):
        for idx, (iid_s, _) in enumerate(cached_rows):
            if str(iid_s) == str(target_id_str):
                if not show_all_var.get():
                    current_page[0] = idx // page_size
                    render_current_page()
                if tree.exists(str(target_id_str)):
                    tree.selection_set(str(target_id_str))
                    tree.focus(str(target_id_str))
                    tree.see(str(target_id_str))
                    on_tree_select(None)
                break

    tk.Button(action_row, text="📥 Paket Yükle", command=one_click_import, bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2").pack(side="left", padx=(0, 3))
    tk.Button(action_row, text="📋 Excel Yapıştır", command=one_click_clipboard_paste, bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2").pack(side="left", padx=2)
    tk.Button(action_row, text="🖼️ Toplu Medya Eşleştir", command=one_click_bulk_media_match, bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2").pack(side="left", padx=2)
    tk.Button(action_row, text="📤 Dışa Aktar (.ZIP)", command=one_click_export, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2").pack(side="left", padx=2)
    tk.Button(action_row, text="📄 Şablon", command=one_click_template, bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=6, pady=4, cursor="hand2").pack(side="left", padx=2)
    tk.Button(
        action_row, text="🔍 Denetim",
        command=lambda: open_quality_audit_window(win, app, current_tab.get(), get_selected_pkg_id(), get_selected_dlg_id(), jump_to_record_from_audit),
        bg=YELLOW, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2"
    ).pack(side="left", padx=2)
    tk.Button(
        action_row, text="🖨️ Sınav & Ders Notu",
        command=lambda: open_printable_worksheet_generator(win, app, current_tab.get(), get_selected_pkg_id(), get_selected_dlg_id()),
        bg="#047857", fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2"
    ).pack(side="left", padx=2)
    tk.Button(action_row, text="⚡ TTS Hazırla", command=precache_package_tts, bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=4, cursor="hand2").pack(side="left", padx=2)
    tk.Button(action_row, text="🗑️ Paketi Sil", command=one_click_delete_pkg, bg=RED, fg="white", font=FONT_UI_B, relief="flat", padx=8, pady=4, cursor="hand2").pack(side="right")

    # Arama, Seviye Filtresi ve Sayfalama Çubuğu
    search_bar = tk.Frame(win, bg=c["CARD_COLOR"])
    search_bar.pack(fill="x", padx=20, pady=4)
    tk.Label(search_bar, text="🔍 Hızlı Ara:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left")
    search_var = tk.StringVar()
    search_ent = tk.Entry(search_bar, textvariable=search_var, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"], width=26)
    search_ent.pack(side="left", padx=6)
    search_ent.bind("<KeyRelease>", lambda e: load_data_and_render(reset_page=True))

    tk.Label(search_bar, text="Seviye Filtresi:", bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=(8, 2))
    filter_level_var = tk.StringVar(value="Tümü")
    filter_level_combo = ttk.Combobox(search_bar, textvariable=filter_level_var, values=["Tümü", "A1", "A2", "B1", "B2", "C1"], state="readonly", width=6, font=FONT_UI)
    filter_level_combo.pack(side="left", padx=4)
    filter_level_combo.bind("<<ComboboxSelected>>", lambda e: load_data_and_render(reset_page=True))

    page_size = 1000
    current_page = [0]
    cached_rows = []
    show_all_var = tk.BooleanVar(value=False)

    page_frame = tk.Frame(search_bar, bg=c["CARD_COLOR"])
    page_frame.pack(side="right")

    lbl_count = tk.Label(page_frame, text="0 kayıt", bg=c["CARD_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B)
    lbl_count.pack(side="right", padx=(10, 0))

    tk.Checkbutton(
        page_frame, text="Tümünü Tek Listede Göster", variable=show_all_var,
        command=lambda: render_current_page(), bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"],
        selectcolor=c["PANEL_COLOR"], activebackground=c["CARD_COLOR"], font=FONT_UI
    ).pack(side="right", padx=8)

    btn_next_p = tk.Button(page_frame, text="Sonraki ▶", command=lambda: change_page(1), bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=8, cursor="hand2")
    btn_next_p.pack(side="right", padx=2)
    lbl_page_info = tk.Label(page_frame, text="Sayfa 1/1", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B, width=18)
    lbl_page_info.pack(side="right", padx=4)
    btn_prev_p = tk.Button(page_frame, text="◀ Önceki", command=lambda: change_page(-1), bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=8, cursor="hand2")
    btn_prev_p.pack(side="right", padx=2)

    # =========================================================================
    # ALT DÜZENLEME FORMU VE TIKLANABİLİR SIRALAMALI (A➔Z / Z➔A) TABLO
    # =========================================================================
    edit_box = tk.Frame(win, bg=c["PANEL_COLOR"], padx=14, pady=8, highlightthickness=1, highlightbackground=c["BORDER"])
    edit_box.pack(side="bottom", fill="x", padx=20, pady=(4, 10))

    table_frame = tk.Frame(win, bg=c["BG_COLOR"], bd=1, relief="solid")
    table_frame.pack(fill="both", expand=True, padx=20, pady=2)

    columns = ("col_id", "col_lvl", "col_group", "col_meta", "col_tr", "col_ar")
    col_titles = {
        "col_id": "ID",
        "col_lvl": "Seviye",
        "col_group": "Paket / Diyalog Başlığı",
        "col_meta": "Konuşmacı / Medya Durumu",
        "col_tr": "Türkçe Karşılığı",
        "col_ar": "Arapça Metin (RTL)"
    }
    sort_state = {"col": None, "desc": False}

    tree = ttk.Treeview(table_frame, columns=columns, show="headings", selectmode="extended", height=8, style="Admin.Treeview")

    def sort_by_column(col_key: str):
        """Sütun başlığına tıklanınca cached_rows listesini A➔Z (▲) veya Z➔A (▼) sıralar."""
        col_idx = columns.index(col_key)
        if sort_state["col"] == col_key:
            sort_state["desc"] = not sort_state["desc"]
        else:
            sort_state["col"] = col_key
            sort_state["desc"] = False

        def _sort_key(item_tuple):
            val = str(item_tuple[1][col_idx] or "").strip()
            if col_key == "col_id":
                nums = re.findall(r"\d+", val)
                if nums:
                    return (0, int(nums[-1]), val.lower())
            return (1, 0, val.lower())

        cached_rows.sort(key=_sort_key, reverse=sort_state["desc"])
        for ck, base_title in col_titles.items():
            arrow = (" ▼" if sort_state["desc"] else " ▲") if ck == col_key else " ⇅"
            tree.heading(ck, text=base_title + arrow, command=lambda c_k=ck: sort_by_column(c_k))
        current_page[0] = 0
        render_current_page()

    for ck, base_title in col_titles.items():
        tree.heading(ck, text=base_title + " ⇅", command=lambda c_k=ck: sort_by_column(c_k))

    tree.column("col_id", width=65, anchor="center")
    tree.column("col_lvl", width=65, anchor="center")
    tree.column("col_group", width=210, anchor="w")
    tree.column("col_meta", width=155, anchor="w")
    tree.column("col_tr", width=285, anchor="w")
    tree.column("col_ar", width=300, anchor="e")

    if is_dark:
        tree.tag_configure("oddrow", background="#161d2a", foreground="#ffffff")
        tree.tag_configure("evenrow", background="#1e2738", foreground="#ffffff")
    else:
        tree.tag_configure("oddrow", background="#f8fafc", foreground="#0f172a")
        tree.tag_configure("evenrow", background="#eef2f6", foreground="#0f172a")

    sb = ttk.Scrollbar(table_frame, orient="vertical", command=tree.yview, style="Admin.Vertical.TScrollbar")
    tree.configure(yscrollcommand=sb.set)
    tree.pack(side="left", fill="both", expand=True)
    sb.pack(side="right", fill="y")

    selected_record_id = tk.StringVar(value="")

    # 1. Satır: Konuşmacı/Paket, Kayıt Seviyesi, Toplu Seviye Ata Butonu ve Türkçe Anlamı
    row1 = tk.Frame(edit_box, bg=c["PANEL_COLOR"])
    row1.pack(fill="x", pady=2)
    tk.Label(row1, text="Konuşmacı / Paket:", bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B).pack(side="left")
    ent_meta = tk.Entry(row1, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B, insertbackground=c["TEXT_MAIN"], width=15)
    ent_meta.pack(side="left", padx=4)

    lbl_item_level = tk.Label(row1, text="🏷️ Seviye:", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B)
    lbl_item_level.pack(side="left", padx=(6, 2))
    item_level_var = tk.StringVar(value="A1")
    item_level_combo = ttk.Combobox(row1, textvariable=item_level_var, values=["A1", "A2", "B1", "B2", "C1"], state="readonly", width=4, font=FONT_UI_B)
    item_level_combo.pack(side="left", padx=2)

    def bulk_assign_level_to_selected():
        sel_items = tree.selection()
        if not sel_items:
            messagebox.showwarning("Seçim Yok", "Lütfen önce tablodan (Shift veya Ctrl tuşuyla) bir veya daha fazla satır seçin.", parent=win)
            return
        target_lvl = (item_level_var.get() or "A1").strip().upper()
        tab = current_tab.get()
        count_sel = len(sel_items)

        if count_sel > 1:
            if not messagebox.askyesno(
                "Toplu Seviye Değiştirme",
                f"Tabloda seçili olan {count_sel} adet kaydın seviyesini topluca '{target_lvl}' yapmak istediğinize emin misiniz?",
                parent=win
            ):
                return

        if tab == "words":
            with db_session(WORDS_DB_PATH) as conn:
                conn.executemany("UPDATE kelimeler SET seviye=? WHERE id=?", [(target_lvl, str(iid)) for iid in sel_items])
        elif tab == "sentences":
            with db_session(SENTENCES_DB_PATH) as conn:
                conn.executemany("UPDATE cumleler SET seviye=? WHERE id=?", [(target_lvl, str(iid)) for iid in sel_items])
        else:
            dlg_ids = {row_dlg_id_map.get(str(iid)) for iid in sel_items if row_dlg_id_map.get(str(iid))}
            with db_session(DIALOGUES_DB_PATH) as conn:
                for did in dlg_ids:
                    conn.execute("UPDATE diyaloglar SET seviye=? WHERE id=?", (target_lvl, did))

        load_data_and_render(reset_page=False)
        app._refresh_current_mode_data()
        app._show_toast(f"🏷️ Seçili {count_sel} kaydın seviyesi '{target_lvl}' olarak güncellendi!", GREEN, 2500)

    tk.Button(
        row1, text="🏷️ Seçililere Ata", command=bulk_assign_level_to_selected,
        bg="#0d9488", fg="white", font=("Segoe UI", 8, "bold"), relief="flat", padx=7, pady=2, cursor="hand2"
    ).pack(side="left", padx=(2, 8))

    tk.Label(row1, text="Türkçe:", bg=c["PANEL_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B).pack(side="left", padx=(4, 2))
    ent_tr = tk.Entry(row1, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI, insertbackground=c["TEXT_MAIN"])
    ent_tr.pack(side="left", padx=4, fill="x", expand=True)

    # 2. Satır: Arapça (RTL) Metin Kutusu
    row2 = tk.Frame(edit_box, bg=c["PANEL_COLOR"])
    row2.pack(fill="x", pady=3)
    tk.Label(row2, text="Arapça (RTL):", bg=c["PANEL_COLOR"], fg=c["TEXT_AR"], font=FONT_UI_B).pack(side="left")
    ent_ar = tk.Entry(row2, bg=c["BG_COLOR"], fg=c["TEXT_AR"], font=(AR_FONT_FAMILY, 15, "bold"), justify="right", insertbackground=c["TEXT_MAIN"])
    ent_ar.pack(side="left", padx=6, fill="x", expand=True)

    # 3. Satır: Medya (Ses & Görsel) ve Kayıt Aksiyon Butonları
    row3_media = tk.Frame(edit_box, bg=c["PANEL_COLOR"])
    row3_media.pack(fill="x", pady=2)

    tk.Label(row3_media, text="🎵 Ses:", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B).pack(side="left")
    ent_ses = tk.Entry(row3_media, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI, insertbackground=c["TEXT_MAIN"], width=15)
    ent_ses.pack(side="left", padx=3)

    def toggle_admin_mic_record():
        if not mic_recorder.recording:
            try:
                app._stop_all_audio()
                mic_recorder.start()
                btn_mic_rec.config(text="⏹️ Kaydı Bitir", bg=RED, fg="white")
                app._show_toast("🎙️ Referans ses kaydediliyor... Konuşmanız bitince '⏹️ Kaydı Bitir'e basın.", YELLOW, 3000)
            except Exception as ex:
                messagebox.showerror("Mikrofon Hatası", str(ex), parent=win)
        else:
            btn_mic_rec.config(text="🎙️ Kaydet", bg="#0d9488", fg="white")
            rid = selected_record_id.get().strip() or f"new_{int(time.time())}"
            try:
                rel_saved = mic_recorder.stop_and_save(f"{current_tab.get()}_{rid}")
                ent_ses.delete(0, tk.END)
                ent_ses.insert(0, rel_saved)
                app._show_toast("✅ Sesiniz kaydedildi! '💾 Seçili Satırı Güncelle'ye basarak kaydedin.", GREEN, 2800)
            except Exception as ex:
                messagebox.showwarning("Kayıt Uyarı", str(ex), parent=win)

    def browse_audio_file():
        p = filedialog.askopenfilename(title="Ses Dosyası Seç (.mp3 / .wav)", filetypes=[("Ses Dosyaları", "*.mp3 *.wav")], parent=win)
        if not p:
            return
        rid = selected_record_id.get().strip() or f"aud_{int(time.time())}"
        ext = os.path.splitext(p)[1].lower()
        dst_name = f"{current_tab.get()}_{rid}{ext}"
        full_dst = os.path.join(CUSTOM_AUDIO_DIR, dst_name)
        shutil.copy(p, full_dst)
        ent_ses.delete(0, tk.END)
        ent_ses.insert(0, f"custom_audio/{dst_name}")
        app._show_toast("Ses dosyası kopyalandı. 'Güncelle'ye basarak kaydedin.", GREEN)

    def browse_image_file():
        if current_tab.get() != "words":
            return
        p = filedialog.askopenfilename(title="Görsel Seç (.png / .jpg)", filetypes=[("Görsel", "*.png *.jpg *.jpeg *.webp")], parent=win)
        if not p:
            return
        rid = selected_record_id.get().strip() or f"img_{int(time.time())}"
        pkg = get_selected_pkg_id()
        if pkg == "all":
            pkg = "genel"
        cat_img_dir = os.path.join(CATEGORIES_DIR, pkg, "images")
        os.makedirs(cat_img_dir, exist_ok=True)
        ext = os.path.splitext(p)[1].lower()
        dst_name = f"{rid}{ext}"
        shutil.copy(p, os.path.join(cat_img_dir, dst_name))
        ent_img.delete(0, tk.END)
        ent_img.insert(0, f"categories/{pkg}/images/{dst_name}")
        app._show_toast("Görsel seçildi. 'Güncelle'ye basarak kaydedin.", GREEN)

    def fetch_audio_from_url():
        url_val = ent_ses.get().strip()
        if not url_val.startswith(("http://", "https://")):
            messagebox.showwarning("URL Bilgisi", "Lütfen 'Ses' kutusuna doğrudan bir ses bağlantısı (https://.../ses.mp3) yapıştırıp bu butona basın.", parent=win)
            return
        rid = selected_record_id.get().strip() or f"aud_{int(time.time())}"
        try:
            rel_path = download_audio_from_url(url_val, f"{current_tab.get()}_{rid}")
            ent_ses.delete(0, tk.END)
            ent_ses.insert(0, rel_path)
            app._show_toast("Ses URL'den indirildi! 'Güncelle'ye basarak kaydedin.", GREEN)
        except Exception as ex:
            messagebox.showerror("URL İndirme Hatası", f"Ses indirilemedi:\n{ex}", parent=win)

    def test_linked_audio():
        """Seçili kaydın yerel sesini veya TTS sesini çalar (loc=local_p hatası giderildi)."""
        ses_val = ent_ses.get().strip()
        ar_val = ent_ar.get().strip()
        local_p = resolve_local_media_path(ses_val)
        if local_p:
            app._play_audio_file_async(local_p)
        elif ar_val:
            app._play_tts_text_async(ar_val)

    def clear_media_fields():
        ent_ses.delete(0, tk.END)
        ent_img.delete(0, tk.END)

    btn_mic_rec = tk.Button(row3_media, text="🎙️ Kaydet", command=toggle_admin_mic_record, bg="#0d9488", fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2")
    btn_mic_rec.pack(side="left", padx=2)
    tk.Button(row3_media, text="📁 Ses Seç", command=browse_audio_file, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(row3_media, text="🌐 URL", command=fetch_audio_from_url, bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="left", padx=2)
    tk.Button(row3_media, text="🔊 Dinle", command=test_linked_audio, bg=ACCENT, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="left", padx=2)

    lbl_img = tk.Label(row3_media, text="🖼️ Görsel:", bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=FONT_UI_B)
    ent_img = tk.Entry(row3_media, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI, insertbackground=c["TEXT_MAIN"], width=13)
    btn_browse_img = tk.Button(row3_media, text="🖼️ Seç", command=browse_image_file, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=7, pady=2, cursor="hand2")

    tk.Button(row3_media, text="🧹 Temizle", command=clear_media_fields, bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI, relief="flat", padx=7, pady=2, cursor="hand2").pack(side="right", padx=2)

    row4_actions = tk.Frame(edit_box, bg=c["PANEL_COLOR"])
    row4_actions.pack(fill="x", pady=(4, 1))

    lbl_sel_id = tk.Label(row4_actions, text="Seçili Kayıt ID: Yok (Sütun başlıklarına tıklayarak A➔Z sıralayabilir, Shift/Ctrl ile çoklu seçebilirsiniz)", bg=c["PANEL_COLOR"], fg=c["TEXT_DIM"], font=FONT_UI_B)
    lbl_sel_id.pack(side="left")

    pkg_map = {}
    dlg_map = {}
    row_audio_map = {}
    row_image_map = {}
    row_dlg_id_map = {}

    def refresh_level_topic_fields():
        tab = current_tab.get()
        target_id = get_selected_dlg_id() if (tab == "dialogue" and get_selected_dlg_id() != "all") else get_selected_pkg_id()
        sev, kon = get_package_level_and_topic(tab, target_id)
        level_var.set(sev)
        topic_var.set(kon)

    def populate_filters(keep_selection: str = None):
        tab = current_tab.get()
        pkg_map.clear()
        pkg_map["Tüm Paketler"] = "all"
        if tab == "words":
            pkgs = get_word_packages()
        elif tab == "dialogue":
            pkgs = get_dialogue_packages()
        else:
            pkgs = get_sentence_packages()
        names = ["Tüm Paketler"]
        selected_pkg_disp = names[0]
        for pid, pad in pkgs:
            sev, _ = get_package_level_and_topic(tab, pid)
            disp = f"[{sev}] {pad} ({pid})"
            pkg_map[disp] = pid
            names.append(disp)
            if keep_selection and pid == keep_selection:
                selected_pkg_disp = disp
        pkg_combo['values'] = names
        pkg_combo.set(selected_pkg_disp)

        if tab == "dialogue":
            lbl_top_tag.config(text="🏷️ Diyalog Seviyesi:")
            lbl_item_level.config(text="🏷️ Diyalog Seviyesi:")
            lbl_dlg_filter.pack(side="left", padx=(4, 4))
            dlg_filter_combo.pack(side="left", padx=4, fill="x", expand=True)
            dlg_map.clear()
            dlg_map["Tüm Diyaloglar"] = "all"
            d_names = ["Tüm Diyaloglar"]
            selected_dlg_disp = d_names[0]
            for did, _, b_ar, b_tr in get_dialogues():
                d_sev, _ = get_package_level_and_topic("dialogue", did)
                disp = f"[{did} | {d_sev}] {b_tr} — {b_ar}" if b_ar else f"[{did} | {d_sev}] {b_tr}"
                dlg_map[disp] = did
                d_names.append(disp)
                if keep_selection and did == keep_selection:
                    selected_dlg_disp = disp
            dlg_filter_combo['values'] = d_names
            dlg_filter_combo.set(selected_dlg_disp)
        else:
            lbl_top_tag.config(text="🏷️ Paket Seviyesi:")
            lbl_item_level.config(text="🏷️ Seviye:")
            lbl_dlg_filter.pack_forget()
            dlg_filter_combo.pack_forget()

        if tab == "words":
            lbl_img.pack(side="left", padx=(10, 3))
            ent_img.pack(side="left", padx=2)
            btn_browse_img.pack(side="left", padx=3)
        else:
            lbl_img.pack_forget()
            ent_img.pack_forget()
            btn_browse_img.pack_forget()

        refresh_level_topic_fields()

    def get_selected_pkg_id():
        return pkg_map.get(pkg_combo.get(), "all")

    def get_selected_dlg_id():
        return dlg_map.get(dlg_filter_combo.get(), "all")

    def load_data_and_render(reset_page=True):
        refresh_level_topic_fields()
        if reset_page:
            current_page[0] = 0
        cached_rows.clear()
        row_audio_map.clear()
        row_image_map.clear()
        row_dlg_id_map.clear()
        sort_state["col"] = None
        for ck, base_title in col_titles.items():
            tree.heading(ck, text=base_title + " ⇅", command=lambda c_k=ck: sort_by_column(c_k))

        tab = current_tab.get()
        pid = get_selected_pkg_id()
        q = search_var.get().strip()
        f_lvl = filter_level_var.get()

        if tab == "words":
            items = get_words(None if pid == "all" else pid, search_query=q, seviye=f_lvl)
            for w in items:
                is_szl = str(w.paket_id).startswith("szl_")
                media_info = ("📖 Sarf Kök Kartı" if is_szl and not w.gorsel else ("🖼️ " if w.gorsel else "")) + (" | 🎵 Ses" if w.ses else " | 🤖 TTS")
                row_audio_map[str(w.id)] = w.ses or ""
                row_image_map[str(w.id)] = w.gorsel or ""
                cached_rows.append((str(w.id), (w.id, w.seviye, w.paket_id, media_info, w.turkce, w.arapca)))
            lbl_count.config(text=f"Toplam: {len(cached_rows)} kelime")
        elif tab == "dialogue":
            did_filter = get_selected_dlg_id()
            lines = get_dialogue_lines_with_titles(paket_id=pid, dialogue_id=did_filter, search_query=q, seviye=f_lvl)
            for lid, did, b_tr, sira, spk, cins, ar, tr, ses, d_sev in lines:
                row_audio_map[str(lid)] = ses or ""
                row_dlg_id_map[str(lid)] = did
                dlg_label = f"[{did}] {b_tr}"
                spk_label = f"{spk} ({cins})" + (" 🎵" if ses else " 🤖")
                cached_rows.append((str(lid), (lid, d_sev, dlg_label, spk_label, tr, ar)))
            lbl_count.config(text=f"Toplam: {len(cached_rows)} diyalog cümlesi")
        else:
            sents = get_key_sentences("all", paket_id=pid, search_query=q, seviye=f_lvl)
            for s in sents:
                snd_tag = "🎵 Özel Ses" if s.ses else "🤖 TTS"
                row_audio_map[str(s.id)] = s.ses or ""
                cached_rows.append((str(s.id), (s.id, s.seviye, s.paket_id, snd_tag, s.turkce, s.arapca)))
            lbl_count.config(text=f"Toplam: {len(cached_rows)} cümle")

        render_current_page()

    def render_current_page():
        for item in tree.get_children():
            tree.delete(item)

        total = len(cached_rows)
        if total == 0:
            lbl_page_info.config(text="Sayfa 0/0")
            return

        if show_all_var.get():
            slice_rows = cached_rows
            lbl_page_info.config(text=f"Tümü ({total})")
            btn_prev_p.config(state="disabled")
            btn_next_p.config(state="disabled")
        else:
            total_pages = max(1, (total + page_size - 1) // page_size)
            current_page[0] = max(0, min(current_page[0], total_pages - 1))
            start_i = current_page[0] * page_size
            end_i = min(total, start_i + page_size)
            slice_rows = cached_rows[start_i:end_i]
            lbl_page_info.config(text=f"Sayfa {current_page[0]+1}/{total_pages} ({start_i+1}-{end_i})")
            btn_prev_p.config(state="normal" if current_page[0] > 0 else "disabled")
            btn_next_p.config(state="normal" if current_page[0] < total_pages - 1 else "disabled")

        for idx, (iid_str, vals) in enumerate(slice_rows):
            row_tag = "evenrow" if idx % 2 == 0 else "oddrow"
            tree.insert("", tk.END, iid=iid_str, values=vals, tags=(row_tag,))

    def change_page(delta):
        current_page[0] += delta
        render_current_page()

    def on_tree_select(event):
        sel = tree.selection()
        if not sel:
            return
        iid = sel[0]
        vals = tree.item(iid, "values")
        selected_record_id.set(str(vals[0]))
        item_level_var.set(str(vals[1]) if vals[1] in ("A1", "A2", "B1", "B2", "C1") else "A1")

        if len(sel) > 1:
            lbl_sel_id.config(text=f"🔥 Çoklu Seçim Aktif: {len(sel)} kayıt seçildi ('🏷️ Seçililere Ata' ile toplu seviye verebilirsiniz)")
        elif current_tab.get() == "dialogue":
            did = row_dlg_id_map.get(str(vals[0]), "")
            lbl_sel_id.config(text=f"Seçili Satır ID: {vals[0]}  (Diyalog: {did} — Seviye tüm diyaloğa uygulanır)")
            level_var.set(item_level_var.get())
        else:
            lbl_sel_id.config(text=f"Seçili Kayıt ID: {vals[0]}  (Tekil Seviye: {vals[1]})")

        ent_meta.delete(0, tk.END)
        clean_meta = vals[3].replace(" 🎵", "").replace(" 🤖", "") if current_tab.get() == "dialogue" else vals[2]
        ent_meta.insert(0, clean_meta)
        ent_tr.delete(0, tk.END)
        ent_tr.insert(0, vals[4])
        ent_ar.delete(0, tk.END)
        ent_ar.insert(0, vals[5])
        ent_ses.delete(0, tk.END)
        ent_ses.insert(0, row_audio_map.get(str(vals[0]), ""))
        ent_img.delete(0, tk.END)
        ent_img.insert(0, row_image_map.get(str(vals[0]), ""))

    tree.bind("<<TreeviewSelect>>", on_tree_select)
    pkg_combo.bind("<<ComboboxSelected>>", lambda e: load_data_and_render(reset_page=True))
    dlg_filter_combo.bind("<<ComboboxSelected>>", lambda e: load_data_and_render(reset_page=True))

    def save_selected_row():
        rid = selected_record_id.get().strip()
        if not rid:
            messagebox.showwarning("Uyarı", "Lütfen önce tablodan güncellenecek satırı seçin.", parent=win)
            return
        ar = sanitize_arabic_sentence(ent_ar.get())
        tr = sanitize_turkish_sentence(ent_tr.get())
        meta = ent_meta.get().strip()
        chosen_lvl = (item_level_var.get() or "A1").strip().upper()
        ses_val = ent_ses.get().strip() or None
        img_val = ent_img.get().strip() or None

        if ses_val and ses_val.startswith(("http://", "https://")):
            try:
                ses_val = download_audio_from_url(ses_val, f"{current_tab.get()}_{rid}")
            except Exception as ex:
                messagebox.showerror("URL Hatası", f"Ses URL'den indirilemedi:\n{ex}", parent=win)
                return

        tab = current_tab.get()
        if tab == "words":
            with db_session(WORDS_DB_PATH) as conn:
                conn.execute("UPDATE kelimeler SET arapca=?, turkce=?, gorsel=?, ses=?, seviye=? WHERE id=?", (ar, tr, img_val, ses_val, chosen_lvl, rid))
        elif tab == "dialogue":
            spk_clean = meta.split("(")[0].strip() or "Kişi"
            did = row_dlg_id_map.get(rid) or get_dialogue_id_for_line(int(rid))
            with db_session(DIALOGUES_DB_PATH) as conn:
                conn.execute("UPDATE diyalog_satirlari SET konusmaci=?, arapca=?, turkce=?, ses=? WHERE id=?", (spk_clean, ar, tr, ses_val, int(rid)))
                if did:
                    conn.execute("UPDATE diyaloglar SET seviye=? WHERE id=?", (chosen_lvl, did))
            populate_filters(keep_selection=get_selected_dlg_id())
        else:
            with db_session(SENTENCES_DB_PATH) as conn:
                conn.execute("UPDATE cumleler SET arapca=?, turkce=?, ses=?, seviye=? WHERE id=?", (ar, tr, ses_val, chosen_lvl, rid))

        load_data_and_render(reset_page=False)
        app._refresh_current_mode_data()
        app._show_toast(f"✅ Kayıt ({chosen_lvl}) güncellendi!", GREEN)

    def add_as_new_row():
        ar = sanitize_arabic_sentence(ent_ar.get())
        tr = sanitize_turkish_sentence(ent_tr.get())
        meta = ent_meta.get().strip()
        chosen_lvl = (item_level_var.get() or "A1").strip().upper()
        ses_val = ent_ses.get().strip() or None
        img_val = ent_img.get().strip() or None
        if not ar:
            messagebox.showwarning("Uyarı", "Arapça metin alanı boş bırakılamaz.", parent=win)
            return

        tab = current_tab.get()
        new_id = f"{tab[0]}_{int(time.time()*1000)}"
        if ses_val and ses_val.startswith(("http://", "https://")):
            try:
                ses_val = download_audio_from_url(ses_val, new_id)
            except Exception as ex:
                log_error("Yeni satır URL ses indirme hatası", ex)

        if tab == "words":
            pkg = meta if meta and meta not in ("all", "sozluk_tumu") else (get_selected_pkg_id() if get_selected_pkg_id() not in ("all", "sozluk_tumu") else "genel")
            add_word_package(pkg, pkg.title(), level_var.get(), topic_var.get())
            with db_session(WORDS_DB_PATH) as conn:
                conn.execute(
                    "INSERT INTO kelimeler (id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, is_fav, is_learned, seviye) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?)",
                    (new_id, pkg, ar, tr, img_val, ses_val, chosen_lvl)
                )
        elif tab == "dialogue":
            did = get_selected_dlg_id()
            if did == "all":
                dlgs = get_dialogues()
                did = dlgs[0][0] if dlgs else "dlg_01"
            spk_clean = meta.split("(")[0].strip() or "Kişi"
            with db_session(DIALOGUES_DB_PATH) as conn:
                conn.execute("""
                    INSERT INTO diyalog_satirlari (diyalog_id, sira, konusmaci, konusmaci_ar, cinsiyet, arapca, turkce, ses, dogru, yanlis)
                    VALUES (?, 99, ?, '', 'E', ?, ?, ?, 0, 0)
                """, (did, spk_clean, ar, tr, ses_val))
                conn.execute("UPDATE diyaloglar SET seviye=? WHERE id=?", (chosen_lvl, did))
        else:
            pkg = get_selected_pkg_id() if get_selected_pkg_id() != "all" else "anahtar_cumleler"
            with db_session(SENTENCES_DB_PATH) as conn:
                conn.execute("INSERT OR IGNORE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, ?)", (pkg, pkg.title(), level_var.get(), topic_var.get()))
                conn.execute(
                    "INSERT INTO cumleler (id, paket_id, arapca, turkce, ses, is_fav, is_learned, dogru, yanlis, seviye) VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, ?)",
                    (new_id, pkg, ar, tr, ses_val, chosen_lvl)
                )

        load_data_and_render(reset_page=False)
        app._refresh_current_mode_data()
        app._show_toast(f"➕ Yeni kayıt ({chosen_lvl}) eklendi!", GREEN)

    def delete_selected_row():
        sel_items = tree.selection()
        if not sel_items:
            return
        msg_del = f"Seçili {len(sel_items)} satırı silmek istediğinize emin misiniz?" if len(sel_items) > 1 else "Seçili satırı silmek istediğinize emin misiniz?"
        if not messagebox.askyesno("Kaydı Sil", msg_del, parent=win):
            return
        tab = current_tab.get()
        if tab == "words":
            with db_session(WORDS_DB_PATH) as conn:
                conn.executemany("DELETE FROM kelimeler WHERE id=?", [(str(iid),) for iid in sel_items])
        elif tab == "dialogue":
            with db_session(DIALOGUES_DB_PATH) as conn:
                conn.executemany("DELETE FROM diyalog_satirlari WHERE id=?", [(int(iid),) for iid in sel_items])
        else:
            with db_session(SENTENCES_DB_PATH) as conn:
                conn.executemany("DELETE FROM cumleler WHERE id=?", [(str(iid),) for iid in sel_items])
        selected_record_id.set("")
        lbl_sel_id.config(text="Seçili Kayıt ID: Yok")
        load_data_and_render(reset_page=False)
        app._refresh_current_mode_data()
        app._show_toast("Seçili kayıt(lar) silindi.", RED)

    tk.Button(row4_actions, text="❌ Kapat", command=_on_admin_close, bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=12, pady=4, cursor="hand2").pack(side="right", padx=(6, 0))
    tk.Button(row4_actions, text="🗑️ Seçili Satır(lar)ı Sil", command=delete_selected_row, bg=RED, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=4, cursor="hand2").pack(side="right", padx=3)
    tk.Button(row4_actions, text="➕ Yeni Kayıt Olarak Ekle", command=add_as_new_row, bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=4, cursor="hand2").pack(side="right", padx=3)
    tk.Button(row4_actions, text="💾 Seçili Satırı Güncelle", command=save_selected_row, bg=GREEN, fg="#0f172a", font=FONT_UI_B, relief="flat", padx=16, pady=4, cursor="hand2").pack(side="right", padx=3)

    def switch_tab(tab_name):
        current_tab.set(tab_name)
        inact_bg = c["BTN_SEC"]
        inact_fg = c["TEXT_MAIN"]
        btn_tab_w.config(bg=BLUE if tab_name == "words" else inact_bg, fg="white" if tab_name == "words" else inact_fg)
        btn_tab_d.config(bg=ACCENT if tab_name == "dialogue" else inact_bg, fg="white" if tab_name == "dialogue" else inact_fg)
        btn_tab_s.config(bg=GREEN if tab_name == "sentences" else inact_bg, fg="#0f172a" if tab_name == "sentences" else inact_fg)
        selected_record_id.set("")
        lbl_sel_id.config(text="Seçili Kayıt ID: Yok (Sütun başlıklarına tıklayarak A➔Z sıralayabilir, Shift/Ctrl ile çoklu seçebilirsiniz)")
        lbl_tts_progress.config(text="")
        populate_filters()
        load_data_and_render(reset_page=True)

    switch_tab(initial_tab)