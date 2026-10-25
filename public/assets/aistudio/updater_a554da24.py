# -*- coding: utf-8 -*-
"""
core/updater.py — GitHub Otomatik Güncelleme Kontrolcüsü ve Kurulum Yöneticisi (v40 Final)
Repo: https://github.com/muallimun/arapca-telaffuz-asistani

Çalışma Prensibi:
1) Arka planda GitHub deposundaki 'version.json' dosyasını ve GitHub Releases API'sini sorgular.
2) Uzak sürüm (remote_version) yerel sürümden (APP_VERSION) büyükse kullanıcıya haber verir.
3) Kullanıcı onayladığında yeni kurulum (.exe) veya kaynak paketini (.zip) indirip uygular.
"""

import os
import re
import sys
import json
import shutil
import zipfile
import tempfile
import threading
import subprocess
import webbrowser
import urllib.request
import urllib.error
import tkinter as tk
from tkinter import messagebox, ttk
from typing import Dict, Optional, Callable

from core.config import (
    BASE_DIR, APP_VERSION, GITHUB_REPO, GITHUB_REPO_URL,
    GREEN, RED, BLUE, YELLOW, PURPLE, FONT_UI, FONT_UI_B, log_error
)
from core.database import get_config_val, set_config_val


def parse_version_tuple(v_str: str) -> tuple:
    """'v40.1.2' veya '40.0' gibi sürüm metinlerini (40, 1, 2) şeklinde sayısal tuple'a çevirir."""
    if not v_str:
        return (0, 0, 0)
    nums = re.findall(r"\d+", str(v_str))
    if not nums:
        return (0, 0, 0)
    parts = [int(n) for n in nums[:4]]
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts)


def get_configured_repo() -> str:
    """Ayarlarda özel bir repo tanımlıysa onu, yoksa varsayılan muallimun reposunu döndürür."""
    custom = (get_config_val("github_repo", "") or "").strip()
    if custom and "/" in custom:
        custom = custom.replace("https://github.com/", "").strip("/")
        return custom
    return GITHUB_REPO


def check_for_updates_sync(custom_repo: Optional[str] = None) -> Dict:
    """
    GitHub üzerinden senkron sürüm kontrolü yapar.
    Önce main/master dalındaki version.json dosyasına, bulunamazsa GitHub Releases API'sine bakar.
    """
    repo = (custom_repo or get_configured_repo()).strip("/")
    local_tuple = parse_version_tuple(APP_VERSION)

    headers = {
        "User-Agent": f"Muallimun-Arapca-Asistani/{APP_VERSION}",
        "Accept": "application/json"
    }

    # 1. Aşama: GitHub raw version.json kontrolü (main ve master dalları)
    for branch in ("main", "master"):
        raw_url = f"https://raw.githubusercontent.com/{repo}/{branch}/version.json?t={int(os.times().elapsed)}"
        try:
            req = urllib.request.Request(raw_url, headers=headers)
            with urllib.request.urlopen(req, timeout=6) as resp:
                data = json.loads(resp.read().decode("utf-8-sig"))
                remote_ver = str(data.get("version", APP_VERSION)).strip()
                remote_tuple = parse_version_tuple(remote_ver)
                changelog = data.get("changelog", [])
                if isinstance(changelog, str):
                    changelog = [changelog]
                return {
                    "ok": True,
                    "has_update": remote_tuple > local_tuple,
                    "local_version": APP_VERSION,
                    "remote_version": remote_ver,
                    "release_date": data.get("release_date", ""),
                    "release_url": data.get("release_url") or f"https://github.com/{repo}/releases",
                    "download_url": data.get("download_url") or f"https://github.com/{repo}/archive/refs/heads/{branch}.zip",
                    "installer_url": data.get("installer_url", ""),
                    "changelog": changelog,
                    "repo": repo
                }
        except Exception:
            continue

    # 2. Aşama: GitHub Releases API (latest) kontrolü
    api_url = f"https://api.github.com/repos/{repo}/releases/latest"
    try:
        req = urllib.request.Request(api_url, headers=headers)
        with urllib.request.urlopen(req, timeout=6) as resp:
            rel_data = json.loads(resp.read().decode("utf-8"))
            tag_name = str(rel_data.get("tag_name", APP_VERSION)).lstrip("vV").strip()
            remote_tuple = parse_version_tuple(tag_name)
            body_txt = rel_data.get("body", "") or "Yeni sürüm iyileştirmeleri ve hata düzeltmeleri."
            changelog = [line.strip("-*• ") for line in body_txt.splitlines() if line.strip()]

            installer_url = ""
            zip_url = rel_data.get("zipball_url") or f"https://github.com/{repo}/archive/refs/heads/main.zip"
            for asset in rel_data.get("assets", []):
                b_url = asset.get("browser_download_url", "")
                if b_url.lower().endswith(".exe"):
                    installer_url = b_url
                    break
                elif b_url.lower().endswith(".zip"):
                    zip_url = b_url

            return {
                "ok": True,
                "has_update": remote_tuple > local_tuple,
                "local_version": APP_VERSION,
                "remote_version": tag_name,
                "release_date": (rel_data.get("published_at") or "")[:10],
                "release_url": rel_data.get("html_url") or f"https://github.com/{repo}/releases",
                "download_url": zip_url,
                "installer_url": installer_url,
                "changelog": changelog[:12],
                "repo": repo
            }
    except urllib.error.HTTPError as http_err:
        if http_err.code == 404:
            return {
                "ok": True,
                "has_update": False,
                "local_version": APP_VERSION,
                "remote_version": APP_VERSION,
                "release_date": "",
                "release_url": f"https://github.com/{repo}",
                "download_url": f"https://github.com/{repo}/archive/refs/heads/main.zip",
                "installer_url": "",
                "changelog": ["GitHub deposu bağlantısı hazır. Henüz yayınlanmış üst sürüm bulunmuyor."],
                "repo": repo
            }
        return {"ok": False, "error": f"HTTP {http_err.code}: GitHub sunucusuna ulaşılamadı.", "repo": repo}
    except Exception as ex:
        return {"ok": False, "error": f"Bağlantı hatası: {ex}", "repo": repo}


def check_for_updates_async(callback: Callable[[Dict], None], custom_repo: Optional[str] = None) -> None:
    """Arka planda sessizce güncelleme kontrolü yapar ve sonucu callback fonksiyonuna iletir."""
    def _worker():
        res = check_for_updates_sync(custom_repo)
        try:
            callback(res)
        except Exception as ex:
            log_error("Güncelleme callback hatası", ex)

    threading.Thread(target=_worker, daemon=True).start()


def _apply_source_zip_update(zip_path: str, target_dir: str) -> int:
    """
    İndirilen GitHub kaynak .zip arşivini çıkartır ve kullanıcının veritabanlarına (data/*.db)
    veya ses kayıtlarına (recordings/) zarar vermeden sadece kod/arayüz dosyalarını günceller.
    """
    updated_files = 0
    temp_extract = tempfile.mkdtemp(prefix="muallimun_upd_")
    try:
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(temp_extract)

        # GitHub zip arşivleri genelde tek bir kök klasör (örn. arapca-telaffuz-asistani-main/) içerir
        entries = os.listdir(temp_extract)
        src_root = temp_extract
        if len(entries) == 1 and os.path.isdir(os.path.join(temp_extract, entries[0])):
            src_root = os.path.join(temp_extract, entries[0])

        protected_rel_paths = {
            os.path.normcase(os.path.join("data", "kelimeler.db")),
            os.path.normcase(os.path.join("data", "diyaloglar.db")),
            os.path.normcase(os.path.join("data", "cumleler.db")),
            os.path.normcase(os.path.join("data", "ayarlar.db")),
            os.path.normcase(os.path.join("data", "sozluk.db")),
        }

        for root_d, _, files in os.walk(src_root):
            rel_dir = os.path.relpath(root_d, src_root)
            if rel_dir.startswith((".git", ".venv", "__pycache__", "recordings")):
                continue
            for fn in files:
                if fn.endswith((".pyc", ".log")):
                    continue
                rel_file = fn if rel_dir == "." else os.path.join(rel_dir, fn)
                if os.path.normcase(rel_file) in protected_rel_paths:
                    continue
                src_f = os.path.join(root_d, fn)
                dst_f = os.path.join(target_dir, rel_file)
                os.makedirs(os.path.dirname(dst_f), exist_ok=True)
                shutil.copy2(src_f, dst_f)
                updated_files += 1
    finally:
        shutil.rmtree(temp_extract, ignore_errors=True)

    return updated_files


def open_updater_window(app, preloaded_info: Optional[Dict] = None):
    """
    Kullanıcıya mevcut sürümü, GitHub'daki son sürümü ve yenilikleri gösteren,
    tek tıkla indirme ve güncelleme yapabilen modern Güncelleme Merkezi penceresi.
    """
    c = app.colors
    is_dark = (app.theme_mode == "dark")

    win = tk.Toplevel(app.root)
    win.title("🔄 Otomatik Güncelleme ve Sürüm Merkezi — Muallimun.Net")
    win.geometry("680x500")
    win.minsize(640, 460)
    win.configure(bg=c["CARD_COLOR"])
    win.transient(app.root)
    win.grab_set()

    if hasattr(app, "_register_ singleton_dialog"):
        app._register_singleton_dialog(win)

    hdr = tk.Frame(win, bg=c["PANEL_COLOR"], padx=20, pady=12)
    hdr.pack(fill="x")
    tk.Label(
        hdr, text="🔄 GITHUB OTOMATİK GÜNCELLEME VE SÜRÜM MERKEZİ",
        bg=c["PANEL_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 13, "bold")
    ).pack(side="left")
    lbl_local_ver = tk.Label(
        hdr, text=f"Kurulu Sürüm: v{APP_VERSION}",
        bg=c["PANEL_COLOR"], fg=GREEN if is_dark else "#047857", font=FONT_UI_B
    )
    lbl_local_ver.pack(side="right")

    body = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=12)
    body.pack(fill="both", expand=True)

    status_card = tk.Frame(body, bg=c["BG_COLOR"], padx=16, pady=12, highlightthickness=1, highlightbackground=c["BORDER"])
    status_card.pack(fill="x", pady=(0, 10))

    lbl_status_title = tk.Label(
        status_card, text="🔍 GitHub deposu kontrol ediliyor...",
        bg=c["BG_COLOR"], fg=c["ACCENT_HEAD"], font=("Segoe UI", 12, "bold"), anchor="w"
    )
    lbl_status_title.pack(fill="x")

    lbl_repo_link = tk.Label(
        status_card, text=f"🌐 Depo: {GITHUB_REPO_URL}",
        bg=c["BG_COLOR"], fg=BLUE, font=("Segoe UI", 9, "underline"), anchor="w", cursor="hand2"
    )
    lbl_repo_link.pack(fill="x", pady=(3, 0))
    lbl_repo_link.bind("<Button-1>", lambda e: webbrowser.open(GITHUB_REPO_URL))

    tk.Label(
        body, text="📋 Sürüm Notları ve Yenilikler (Changelog):",
        bg=c["CARD_COLOR"], fg=c["TEXT_MAIN"], font=FONT_UI_B
    ).pack(anchor="w", pady=(2, 4))

    txt_frame = tk.Frame(body, bg=c["BG_COLOR"], highlightthickness=1, highlightbackground=c["BORDER"])
    txt_frame.pack(fill="both", expand=True, pady=(0, 10))

    txt_changelog = tk.Text(
        txt_frame, bg=c["BG_COLOR"], fg=c["TEXT_MAIN"], font=("Segoe UI", 10),
        wrap="word", padx=12, pady=10, bd=0, highlightthickness=0, height=8
    )
    sb_log = ttk.Scrollbar(txt_frame, orient="vertical", command=txt_changelog.yview)
    txt_changelog.configure(yscrollcommand=sb_log.set)
    txt_changelog.pack(side="left", fill="both", expand=True)
    sb_log.pack(side="right", fill="y")

    prog_frame = tk.Frame(body, bg=c["CARD_COLOR"])
    prog_var = tk.DoubleVar(value=0.0)
    prog_bar = ttk.Progressbar(prog_frame, variable=prog_var, maximum=100.0)
    prog_bar.pack(fill="x", pady=(0, 4))
    lbl_prog = tk.Label(prog_frame, text="", bg=c["CARD_COLOR"], fg=c["TEXT_DIM"], font=("Segoe UI", 9, "bold"))
    lbl_prog.pack(anchor="w")

    btn_bar = tk.Frame(win, bg=c["CARD_COLOR"], padx=20, pady=12)
    btn_bar.pack(fill="x", side="bottom")

    state_info = {"data": preloaded_info}

    def render_info(info: Dict):
        if not win.winfo_exists():
            return
        state_info["data"] = info
        txt_changelog.config(state="normal")
        txt_changelog.delete("1.0", tk.END)

        if not info.get("ok"):
            lbl_status_title.config(text=f"⚠️ Güncelleme sunucusuna ulaşılamadı", fg=RED)
            txt_changelog.insert(tk.END, f"Hata Detayı:\n{info.get('error', 'Bilinmeyen hata')}\n\n"
                                         f"İnternet bağlantınızı kontrol edip '🔄 Tekrar Denetle' butonuna basabilirsiniz.")
            btn_install.config(state="disabled")
        else:
            rem_v = info.get("remote_version", APP_VERSION)
            rel_d = info.get("release_date", "")
            date_suffix = f" ({rel_d})" if rel_d else ""
            if info.get("has_update"):
                lbl_status_title.config(
                    text=f"🎉 Yeni Sürüm Mevcut!  v{APP_VERSION} ➔ v{rem_v}{date_suffix}",
                    fg=GREEN if is_dark else "#047857"
                )
                btn_install.config(state="normal", bg=GREEN, fg="#0f172a", text=f"🚀 Şimdi v{rem_v} Sürümüne Güncelle")
            else:
                lbl_status_title.config(
                    text=f"✅ Uygulamanız Güncel! (En Son Sürüm: v{rem_v}{date_suffix})",
                    fg=GREEN if is_dark else "#047857"
                )
                btn_install.config(state="normal", bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], text="🔄 Dosyaları GitHub'dan Yeniden Onar / Güncelle")

            notes = info.get("changelog") or ["Sürüm notu belirtilmemiş."]
            for line in notes:
                txt_changelog.insert(tk.END, f"• {line}\n\n")

        txt_changelog.config(state="disabled")

    def trigger_download_and_update():
        info = state_info.get("data")
        if not info or not info.get("ok"):
            return

        installer_url = (info.get("installer_url") or "").strip()
        download_url = (info.get("download_url") or "").strip()
        is_frozen_exe = getattr(sys, "frozen", False)

        target_url = installer_url if (is_frozen_exe and installer_url) else (download_url or installer_url)
        if not target_url:
            webbrowser.open(info.get("release_url", GITHUB_REPO_URL))
            return

        btn_install.config(state="disabled", text="⏳ İndiriliyor...")
        btn_check.config(state="disabled")
        prog_frame.pack(fill="x", pady=(4, 0))

        def _dl_worker():
            try:
                is_exe = target_url.lower().endswith(".exe")
                suffix = ".exe" if is_exe else ".zip"
                tmp_fd, tmp_path = tempfile.mkstemp(prefix="muallimun_update_", suffix=suffix)
                os.close(tmp_fd)

                req = urllib.request.Request(target_url, headers={"User-Agent": f"Muallimun-Updater/{APP_VERSION}"})
                with urllib.request.urlopen(req, timeout=30) as resp, open(tmp_path, "wb") as out_f:
                    total_size = int(resp.info().get("Content-Length", 0) or 0)
                    downloaded = 0
                    block_sz = 32768
                    while True:
                        buf = resp.read(block_sz)
                        if not buf:
                            break
                        out_f.write(buf)
                        downloaded += len(buf)
                        if total_size > 0:
                            pct = min(99.0, (downloaded / total_size) * 100.0)
                            win.after(0, lambda p=pct, d=downloaded, t=total_size: (
                                prog_var.set(p),
                                lbl_prog.config(text=f"İndiriliyor: %{int(p)} ({d // 1024} KB / {t // 1024} KB)")
                            ))
                        else:
                            win.after(0, lambda d=downloaded: lbl_prog.config(text=f"İndirilen: {d // 1024} KB"))

                win.after(0, lambda: (prog_var.set(100.0), lbl_prog.config(text="✅ İndirme tamamlandı, uygulanıyor...")))

                if is_exe:
                    subprocess.Popen([tmp_path], shell=True)
                    win.after(0, lambda: (
                        messagebox.showinfo("Kurulum Başlatıldı", "Yeni sürüm kurulum sihirbazı başlatıldı. Uygulama şimdi kapanacak.", parent=win),
                        app._on_close()
                    ))
                else:
                    cnt_upd = _apply_source_zip_update(tmp_path, BASE_DIR)
                    try:
                        os.remove(tmp_path)
                    except Exception:
                        pass

                    def _finish_zip_upd():
                        if not win.winfo_exists():
                            return
                        if messagebox.askyesno(
                            "🎉 Güncelleme Tamamlandı",
                            f"GitHub üzerinden {cnt_upd} uygulama dosyası başarıyla güncellendi!\n"
                            f"(Veritabanlarınız ve ses kayıtlarınız korundu.)\n\n"
                            f"Değişikliklerin etkinleşmesi için uygulamayı şimdi yeniden başlatmak ister misiniz?",
                            parent=win
                        ):
                            win.destroy()
                            python_exe = sys.executable
                            subprocess.Popen([python_exe, os.path.join(BASE_DIR, "app.py")])
                            app._on_close()
                        else:
                            btn_install.config(state="normal", text="✅ Güncellendi")
                            btn_check.config(state="normal")

                    win.after(0, _finish_zip_upd)

            except Exception as ex:
                log_error("Güncelleme indirme hatası", ex)
                if win.winfo_exists():
                    win.after(0, lambda: (
                        lbl_prog.config(text=f"❌ Hata: {ex}", fg=RED),
                        btn_install.config(state="normal", text="🚀 Tekrar Dene"),
                        btn_check.config(state="normal")
                    ))

        threading.Thread(target=_dl_worker, daemon=True).start()

    def manual_recheck():
        lbl_status_title.config(text="🔍 GitHub deposu sorgulanıyor...", fg=YELLOW if is_dark else "#1e3a8a")
        btn_check.config(state="disabled")
        check_for_updates_async(lambda res: win.after(0, lambda: (btn_check.config(state="normal"), render_info(res))))

    btn_install = tk.Button(
        btn_bar, text="🚀 Güncelle", command=trigger_download_and_update,
        bg=GREEN, fg="#0f172a", font=("Segoe UI", 10, "bold"),
        relief="flat", padx=16, pady=8, cursor="hand2", state="disabled"
    )
    btn_install.pack(side="left", fill="x", expand=True, padx=(0, 6))

    btn_check = tk.Button(
        btn_bar, text="🔄 Tekrar Denetle", command=manual_recheck,
        bg=BLUE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2"
    )
    btn_check.pack(side="left", padx=4)

    tk.Button(
        btn_bar, text="🌐 GitHub Sayfası", command=lambda: webbrowser.open(GITHUB_REPO_URL),
        bg=PURPLE, fg="white", font=FONT_UI_B, relief="flat", padx=12, pady=8, cursor="hand2"
    ).pack(side="left", padx=4)

    tk.Button(
        btn_bar, text="Kapat", command=win.destroy,
        bg=c["BTN_SEC"], fg=c["TEXT_MAIN"], font=FONT_UI_B, relief="flat", padx=16, pady=8, cursor="hand2"
    ).pack(side="right", padx=(4, 0))

    if preloaded_info:
        render_info(preloaded_info)
    else:
        manual_recheck()