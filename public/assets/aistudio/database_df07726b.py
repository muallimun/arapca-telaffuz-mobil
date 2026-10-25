# -*- coding: utf-8 -*-
"""
core/database.py — Context Manager ve Performans İndeksli SQLite Mimarisi,
Dinamik Ana Giriş Ekranı Sayaç Motoru, Gelişmiş Dinle & Bul Sınav Üreticisi,
Excel/Panodan Kopyala-Yapıştır (Clipboard) Aktarıcı, Detaylı Yükleme Hata Raporlayıcı,
Toplu Görsel/Ses Klasörü Eşleştirici ve Gerçek SM-2 Aralıklı Tekrar (SRS) Motoru (v40 Final).
"""

import os
import re
import csv
import json
import time
import shutil
import random
import zipfile
import tempfile
import sqlite3
import datetime
from contextlib import contextmanager
from dataclasses import dataclass
from typing import List, Tuple, Dict, Optional

from core.config import (
    BASE_DIR, DATA_DIR, ASSETS_DIR, CATEGORIES_DIR, CUSTOM_AUDIO_DIR,
    WORDS_DB_PATH, DIALOGUES_DB_PATH, SENTENCES_DB_PATH, SETTINGS_DB_PATH,
    JSON_DIALOGUES_PATH, log_error
)
from core.arabic_nlp import (
    clean_invisible_marks, sanitize_arabic_sentence, sanitize_turkish_sentence,
    check_item_quality, estimate_word_cefr, estimate_sentence_cefr, estimate_dialogue_cefr
)
from core.dictionary import init_dictionary_db, import_sozluk_json, SOZLUK_DB_PATH

# Son içe aktarma işleminin detaylı hata/atlama raporu
_LAST_IMPORT_REPORT: Dict = {
    "added": 0,
    "skipped_count": 0,
    "skipped_lines": []
}


def get_last_import_report() -> Dict:
    """Son paket/dosya yükleme işleminde eklenen ve hatalı olduğu için atlanan satırların raporunu döndürür."""
    return dict(_LAST_IMPORT_REPORT)


def _reset_import_report() -> None:
    _LAST_IMPORT_REPORT["added"] = 0
    _LAST_IMPORT_REPORT["skipped_count"] = 0
    _LAST_IMPORT_REPORT["skipped_lines"] = []


def _record_skipped_line(line_no: int, reason: str) -> None:
    _LAST_IMPORT_REPORT["skipped_count"] += 1
    if len(_LAST_IMPORT_REPORT["skipped_lines"]) < 50:
        _LAST_IMPORT_REPORT["skipped_lines"].append(f"Satır {line_no} ({reason})")


def _slugify_tr(text: str) -> str:
    """Türkçe kelime veya dosya adlarını eşleştirme için normalize eder ('Arı' -> 'ari', 'Köpek' -> 'kopek')."""
    if not text:
        return ""
    tr_map = str.maketrans("çğıöşüÇĞİÖŞÜâîûÂÎÛ", "cgiosuCGIOSUaiuAIU")
    s = text.translate(tr_map).lower().strip()
    s = re.sub(r"[^a-z0-9_]", "", s)
    return s


def _resolve_media_path_light(rel_or_abs_path: Optional[str]) -> Optional[str]:
    """Ses motorunu (pygame) yüklemeden yerel görsel/ses dosya yolunu hızlıca doğrular."""
    if not rel_or_abs_path:
        return None
    p = rel_or_abs_path.strip()
    if not p or p.startswith(("http://", "https://")):
        return None
    for candidate in (p, os.path.join(ASSETS_DIR, p), os.path.join(BASE_DIR, p)):
        if os.path.exists(candidate) and os.path.isfile(candidate):
            return candidate
    return None


# ==============================================================================
# 1. MODÜL DÜZEYİNDE VERİ MODELLERİ (@dataclass) VE GÜVENLİ DB OTURUM YÖNETİCİSİ
# ==============================================================================
@dataclass
class WordItem:
    id: str
    paket_id: str
    arapca: str
    turkce: str
    gorsel: Optional[str] = None
    ses: Optional[str] = None
    dogru: int = 0
    yanlis: int = 0
    is_fav: int = 0
    is_learned: int = 0
    seviye: str = "A1"
    interval_days: int = 0
    ease_factor: float = 2.5
    next_review: str = ""

    @property
    def name_ar(self) -> str:
        return self.arapca

    @property
    def name_tr(self) -> str:
        return self.turkce


@dataclass
class SentenceItem:
    id: str
    paket_id: str
    arapca: str
    turkce: str
    ses: Optional[str] = None
    is_fav: int = 0
    is_learned: int = 0
    dogru: int = 0
    yanlis: int = 0
    seviye: str = "A1"
    interval_days: int = 0
    ease_factor: float = 2.5
    next_review: str = ""

    @property
    def text_ar(self) -> str:
        return self.arapca

    @property
    def text_tr(self) -> str:
        return self.turkce


@contextmanager
def db_session(db_path: str):
    conn = sqlite3.connect(db_path, timeout=15.0)
    try:
        yield conn
        conn.commit()
    except Exception as ex:
        conn.rollback()
        log_error(f"Veritabanı işlem hatası ({os.path.basename(db_path)})", ex)
        raise
    finally:
        conn.close()


_ALLOWED_TABLES = {"paketler", "kelimeler", "diyaloglar", "diyalog_satirlari", "cumleler", "config"}
_ALLOWED_COLUMNS = {
    "seviye": "TEXT DEFAULT 'A1'",
    "konu": "TEXT DEFAULT 'Genel'",
    "is_fav": "INTEGER DEFAULT 0",
    "is_learned": "INTEGER DEFAULT 0",
    "last_practiced": "TEXT DEFAULT ''",
    "interval_days": "INTEGER DEFAULT 0",
    "ease_factor": "REAL DEFAULT 2.5",
    "next_review": "TEXT DEFAULT ''"
}


def _ensure_column(cursor: sqlite3.Cursor, table_name: str, col_name: str, col_def: str = "") -> None:
    if table_name not in _ALLOWED_TABLES or col_name not in _ALLOWED_COLUMNS:
        return
    safe_def = col_def if col_def else _ALLOWED_COLUMNS[col_name]
    existing = [r[1] for r in cursor.execute(f"PRAGMA table_info({table_name})").fetchall()]
    if col_name not in existing:
        cursor.execute(f"ALTER TABLE {table_name} ADD COLUMN {col_name} {safe_def}")


# ==============================================================================
# 2. VERİTABANI BAŞLATMA, ŞEMA YÜKSELTME VE CANLI SAYAÇ SORGULAYICI
# ==============================================================================
def init_all_dbs() -> None:
    with db_session(SETTINGS_DB_PATH) as conn_cfg:
        conn_cfg.execute("CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT)")

    # 1. KELİMELER VERİTABANI
    with db_session(WORDS_DB_PATH) as conn_w:
        c_w = conn_w.cursor()
        c_w.execute("CREATE TABLE IF NOT EXISTS paketler (id TEXT PRIMARY KEY, ad TEXT)")
        _ensure_column(c_w, "paketler", "seviye", "TEXT DEFAULT 'A1'")
        _ensure_column(c_w, "paketler", "konu", "TEXT DEFAULT 'Genel'")

        c_w.execute("""
            CREATE TABLE IF NOT EXISTS kelimeler (
                id TEXT PRIMARY KEY, paket_id TEXT, arapca TEXT NOT NULL,
                turkce TEXT NOT NULL, gorsel TEXT, ses TEXT,
                dogru INTEGER DEFAULT 0, yanlis INTEGER DEFAULT 0,
                is_fav INTEGER DEFAULT 0, is_learned INTEGER DEFAULT 0,
                last_practiced TEXT DEFAULT '', seviye TEXT DEFAULT 'A1',
                interval_days INTEGER DEFAULT 0, ease_factor REAL DEFAULT 2.5,
                next_review TEXT DEFAULT ''
            )
        """)
        for col_n in ("is_fav", "is_learned", "last_practiced", "seviye", "interval_days", "ease_factor", "next_review"):
            _ensure_column(c_w, "kelimeler", col_n)

        c_w.execute("CREATE INDEX IF NOT EXISTS idx_words_paket ON kelimeler(paket_id)")
        c_w.execute("CREATE INDEX IF NOT EXISTS idx_words_seviye ON kelimeler(seviye)")
        c_w.execute("CREATE INDEX IF NOT EXISTS idx_words_next_review ON kelimeler(next_review)")
        c_w.execute("CREATE INDEX IF NOT EXISTS idx_words_yanlis ON kelimeler(yanlis DESC)")
        c_w.execute("CREATE INDEX IF NOT EXISTS idx_words_fav ON kelimeler(is_fav)")

    # 2. DİYALOGLAR VERİTABANI
    d_cnt = 0
    with db_session(DIALOGUES_DB_PATH) as conn_d:
        c_d = conn_d.cursor()
        c_d.execute("CREATE TABLE IF NOT EXISTS paketler (id TEXT PRIMARY KEY, ad TEXT)")
        _ensure_column(c_d, "paketler", "seviye", "TEXT DEFAULT 'A1'")
        _ensure_column(c_d, "paketler", "konu", "TEXT DEFAULT 'Günlük Hayat'")

        c_d.execute("CREATE TABLE IF NOT EXISTS diyaloglar (id TEXT PRIMARY KEY, paket_id TEXT, baslik_ar TEXT, baslik_tr TEXT)")
        _ensure_column(c_d, "diyaloglar", "seviye", "TEXT DEFAULT 'A1'")
        _ensure_column(c_d, "diyaloglar", "konu", "TEXT DEFAULT 'Günlük Hayat'")

        c_d.execute("""
            CREATE TABLE IF NOT EXISTS diyalog_satirlari (
                id INTEGER PRIMARY KEY AUTOINCREMENT, diyalog_id TEXT, sira INTEGER,
                konusmaci TEXT, konusmaci_ar TEXT, cinsiyet TEXT DEFAULT 'E',
                arapca TEXT NOT NULL, turkce TEXT, ses TEXT,
                dogru INTEGER DEFAULT 0, yanlis INTEGER DEFAULT 0
            )
        """)
        c_d.execute("CREATE INDEX IF NOT EXISTS idx_dlg_seviye ON diyaloglar(seviye)")
        c_d.execute("CREATE INDEX IF NOT EXISTS idx_dlglines_did ON diyalog_satirlari(diyalog_id, sira)")
        d_cnt = c_d.execute("SELECT COUNT(*) FROM diyaloglar").fetchone()[0]

    # 3. CÜMLELER VERİTABANI
    with db_session(SENTENCES_DB_PATH) as conn_s:
        c_s = conn_s.cursor()
        c_s.execute("CREATE TABLE IF NOT EXISTS paketler (id TEXT PRIMARY KEY, ad TEXT)")
        _ensure_column(c_s, "paketler", "seviye", "TEXT DEFAULT 'A1'")
        _ensure_column(c_s, "paketler", "konu", "TEXT DEFAULT 'Anahtar Kalıplar'")

        c_s.execute("""
            CREATE TABLE IF NOT EXISTS cumleler (
                id TEXT PRIMARY KEY, paket_id TEXT, arapca TEXT NOT NULL,
                turkce TEXT, ses TEXT, is_fav INTEGER DEFAULT 0, is_learned INTEGER DEFAULT 0,
                dogru INTEGER DEFAULT 0, yanlis INTEGER DEFAULT 0,
                last_practiced TEXT DEFAULT '', seviye TEXT DEFAULT 'A1',
                interval_days INTEGER DEFAULT 0, ease_factor REAL DEFAULT 2.5,
                next_review TEXT DEFAULT ''
            )
        """)
        for col_n in ("last_practiced", "seviye", "interval_days", "ease_factor", "next_review"):
            _ensure_column(c_s, "cumleler", col_n)

        c_s.execute("CREATE INDEX IF NOT EXISTS idx_sents_paket ON cumleler(paket_id)")
        c_s.execute("CREATE INDEX IF NOT EXISTS idx_sents_seviye ON cumleler(seviye)")
        c_s.execute("CREATE INDEX IF NOT EXISTS idx_sents_next_review ON cumleler(next_review)")
        c_s.execute("CREATE INDEX IF NOT EXISTS idx_sents_yanlis ON cumleler(yanlis DESC)")
        c_s.execute("CREATE INDEX IF NOT EXISTS idx_sents_fav ON cumleler(is_fav)")

    yeni_dlg_cands = [
        os.path.join(DATA_DIR, "diyalog_paketi_yeni.json"),
        os.path.join(BASE_DIR, "diyalog_paketi_yeni.json")
    ]
    yeni_dlg_path = next((p for p in yeni_dlg_cands if os.path.exists(p)), None)

    if yeni_dlg_path and get_config_val("dlg_yeni_v36_loaded", "0") != "1":
        try:
            import_dialogue_json_package(
                yeni_dlg_path, "muhadesat_paketi", "Muhâdesât (Günlük Diyaloglar)",
                clear_existing=False
            )
            set_config_val("dlg_yeni_v36_loaded", "1")
        except Exception as ex:
            log_error("diyalog_paketi_yeni.json otomatik yükleme hatası", ex)
    elif d_cnt == 0 and os.path.exists(JSON_DIALOGUES_PATH):
        try:
            import_dialogue_json_package(
                JSON_DIALOGUES_PATH, "muhadesat_paketi", "Muhâdesât (Günlük Diyaloglar)",
                clear_existing=False
            )
        except Exception as ex:
            log_error("Varsayılan diyalog paketi içe aktarma hatası", ex)

    # 4. SARF SÖZLÜĞÜ VERİTABANI
    try:
        init_dictionary_db()
    except Exception as ex:
        log_error("Sözlük DB başlatma hatası", ex)

    if get_config_val("text_doctor_v2", "0") != "1":
        repair_all_databases()
        set_config_val("text_doctor_v2", "1")


def get_live_dashboard_counts() -> Dict[str, int]:
    counts = {
        "visual_words": 0,
        "visual_word_pkgs": 0,
        "sozluk_entries": 0,
        "sozluk_cards": 0,
        "sozluk_categories": 0,
        "dialogues": 0,
        "dialogue_lines": 0,
        "sentences": 0,
        "sentence_pkgs": 0,
        "total_words": 0
    }
    try:
        with db_session(WORDS_DB_PATH) as conn_w:
            c_w = conn_w.cursor()
            counts["visual_words"] = c_w.execute(
                "SELECT COUNT(*) FROM kelimeler WHERE paket_id NOT LIKE 'szl_%'"
            ).fetchone()[0]
            counts["visual_word_pkgs"] = c_w.execute(
                "SELECT COUNT(*) FROM paketler WHERE id NOT LIKE 'szl_%' AND id != 'sozluk_tumu'"
            ).fetchone()[0]
            counts["sozluk_cards"] = c_w.execute(
                "SELECT COUNT(*) FROM kelimeler WHERE paket_id LIKE 'szl_%'"
            ).fetchone()[0]
            counts["total_words"] = counts["visual_words"] + counts["sozluk_cards"]

        if os.path.exists(SOZLUK_DB_PATH):
            with db_session(SOZLUK_DB_PATH) as conn_szl:
                c_szl = conn_szl.cursor()
                counts["sozluk_entries"] = c_szl.execute("SELECT COUNT(*) FROM sozluk").fetchone()[0]
                counts["sozluk_categories"] = c_szl.execute(
                    "SELECT COUNT(DISTINCT kategori_id) FROM sozluk"
                ).fetchone()[0]

        with db_session(DIALOGUES_DB_PATH) as conn_d:
            c_d = conn_d.cursor()
            counts["dialogues"] = c_d.execute("SELECT COUNT(*) FROM diyaloglar").fetchone()[0]
            counts["dialogue_lines"] = c_d.execute("SELECT COUNT(*) FROM diyalog_satirlari").fetchone()[0]

        with db_session(SENTENCES_DB_PATH) as conn_s:
            c_s = conn_s.cursor()
            counts["sentences"] = c_s.execute("SELECT COUNT(*) FROM cumleler").fetchone()[0]
            counts["sentence_pkgs"] = c_s.execute("SELECT COUNT(*) FROM paketler").fetchone()[0]
    except Exception as ex:
        log_error("Canlı dashboard sayaç hatası", ex)

    return counts


def repair_all_databases() -> int:
    fixed_count = 0

    with db_session(DIALOGUES_DB_PATH) as conn_d:
        c_d = conn_d.cursor()
        for did, b_ar, b_tr in c_d.execute("SELECT id, baslik_ar, baslik_tr FROM diyaloglar").fetchall():
            new_b_ar = sanitize_arabic_sentence(b_ar)
            new_b_tr = sanitize_turkish_sentence(b_tr)
            if new_b_ar != (b_ar or "") or new_b_tr != (b_tr or ""):
                c_d.execute("UPDATE diyaloglar SET baslik_ar=?, baslik_tr=? WHERE id=?", (new_b_ar, new_b_tr, did))
                fixed_count += 1

        for lid, ar, tr in c_d.execute("SELECT id, arapca, turkce FROM diyalog_satirlari").fetchall():
            new_ar = sanitize_arabic_sentence(ar)
            new_tr = sanitize_turkish_sentence(tr)
            if new_ar != ar or new_tr != tr:
                c_d.execute("UPDATE diyalog_satirlari SET arapca=?, turkce=? WHERE id=?", (new_ar, new_tr, lid))
                fixed_count += 1

    with db_session(SENTENCES_DB_PATH) as conn_s:
        c_s = conn_s.cursor()
        for sid, ar, tr in c_s.execute("SELECT id, arapca, turkce FROM cumleler").fetchall():
            new_ar = sanitize_arabic_sentence(ar)
            new_tr = sanitize_turkish_sentence(tr)
            if new_ar != ar or new_tr != tr:
                c_s.execute("UPDATE cumleler SET arapca=?, turkce=? WHERE id=?", (new_ar, new_tr, sid))
                fixed_count += 1

    with db_session(WORDS_DB_PATH) as conn_w:
        c_w = conn_w.cursor()
        for wid, ar, tr in c_w.execute("SELECT id, arapca, turkce FROM kelimeler").fetchall():
            new_ar = sanitize_arabic_sentence(ar)
            new_tr = sanitize_turkish_sentence(tr)
            if new_ar != ar or new_tr != tr:
                c_w.execute("UPDATE kelimeler SET arapca=?, turkce=? WHERE id=?", (new_ar, new_tr, wid))
                fixed_count += 1

    return fixed_count


# ==============================================================================
# 3. SARF VEZİNLİ VE İLETİŞİMSEL KALIPLI CEFR TAHMİN MOTORU (A1 - C1)
# ==============================================================================
def auto_assign_cefr_levels(target_mode: str = "all") -> Dict[str, Dict[str, int]]:
    stats = {
        "words": {"A1": 0, "A2": 0, "B1": 0, "B2": 0, "C1": 0},
        "dialogue": {"A1": 0, "A2": 0, "B1": 0, "B2": 0, "C1": 0},
        "sentences": {"A1": 0, "A2": 0, "B1": 0, "B2": 0, "C1": 0},
    }

    if target_mode in ("all", "words"):
        with db_session(WORDS_DB_PATH) as conn_w:
            c_w = conn_w.cursor()
            rows_w = c_w.execute("SELECT id, arapca, gorsel FROM kelimeler").fetchall()
            updates_w = []
            for wid, ar, gorsel in rows_w:
                lvl = estimate_word_cefr(ar or "", gorsel)
                stats["words"][lvl] += 1
                updates_w.append((lvl, wid))
            c_w.executemany("UPDATE kelimeler SET seviye=? WHERE id=?", updates_w)

    if target_mode in ("all", "dialogue"):
        with db_session(DIALOGUES_DB_PATH) as conn_d:
            c_d = conn_d.cursor()
            dlgs = c_d.execute("SELECT id FROM diyaloglar").fetchall()
            for (did,) in dlgs:
                lines = [r[0] for r in c_d.execute("SELECT arapca FROM diyalog_satirlari WHERE diyalog_id=?", (did,)).fetchall()]
                lvl = estimate_dialogue_cefr(lines)
                stats["dialogue"][lvl] += 1
                c_d.execute("UPDATE diyaloglar SET seviye=? WHERE id=?", (lvl, did))

    if target_mode in ("all", "sentences"):
        with db_session(SENTENCES_DB_PATH) as conn_s:
            c_s = conn_s.cursor()
            rows_s = c_s.execute("SELECT id, arapca FROM cumleler").fetchall()
            updates_s = []
            for sid, ar in rows_s:
                lvl = estimate_sentence_cefr(ar or "")
                stats["sentences"][lvl] += 1
                updates_s.append((lvl, sid))
            c_s.executemany("UPDATE cumleler SET seviye=? WHERE id=?", updates_s)

    return stats


def get_package_level_and_topic(mode: str, target_id: str) -> Tuple[str, str]:
    if not target_id or target_id == "all":
        return "A1", "Genel"
    try:
        if mode == "words":
            with db_session(WORDS_DB_PATH) as conn:
                row = conn.execute("SELECT COALESCE(seviye,'A1'), COALESCE(konu,'Genel') FROM paketler WHERE id=?", (target_id,)).fetchone()
        elif mode == "dialogue":
            with db_session(DIALOGUES_DB_PATH) as conn:
                row = conn.execute("SELECT COALESCE(seviye,'A1'), COALESCE(konu,'Günlük Hayat') FROM diyaloglar WHERE id=?", (target_id,)).fetchone()
                if not row:
                    row = conn.execute("SELECT COALESCE(seviye,'A1'), COALESCE(konu,'Günlük Hayat') FROM paketler WHERE id=?", (target_id,)).fetchone()
        else:
            with db_session(SENTENCES_DB_PATH) as conn:
                row = conn.execute("SELECT COALESCE(seviye,'A1'), COALESCE(konu,'Anahtar Kalıplar') FROM paketler WHERE id=?", (target_id,)).fetchone()
        return (row[0] or "A1", row[1] or "Genel") if row else ("A1", "Genel")
    except Exception as ex:
        log_error(f"Seviye/Konu okuma hatası ({mode}:{target_id})", ex)
        return "A1", "Genel"


def update_package_level_and_topic(mode: str, target_id: str, seviye: str, konu: str, apply_to_items: bool = False) -> None:
    if not target_id or target_id == "all":
        return
    seviye = (seviye or "A1").strip().upper()
    konu = (konu or "Genel").strip()
    if mode == "words":
        with db_session(WORDS_DB_PATH) as conn:
            conn.execute("UPDATE paketler SET seviye=?, konu=? WHERE id=?", (seviye, konu, target_id))
            if apply_to_items:
                if target_id == "sozluk_tumu":
                    conn.execute("UPDATE kelimeler SET seviye=? WHERE paket_id LIKE 'szl_%'", (seviye,))
                else:
                    conn.execute("UPDATE kelimeler SET seviye=? WHERE paket_id=?", (seviye, target_id))
    elif mode == "dialogue":
        with db_session(DIALOGUES_DB_PATH) as conn:
            conn.execute("UPDATE diyaloglar SET seviye=?, konu=? WHERE id=?", (seviye, konu, target_id))
            conn.execute("UPDATE paketler SET seviye=?, konu=? WHERE id=?", (seviye, konu, target_id))
    else:
        with db_session(SENTENCES_DB_PATH) as conn:
            conn.execute("UPDATE paketler SET seviye=?, konu=? WHERE id=?", (seviye, konu, target_id))
            if apply_to_items:
                conn.execute("UPDATE cumleler SET seviye=? WHERE paket_id=?", (seviye, target_id))


def update_dialogue_level_by_id(dialogue_id: str, seviye: str) -> None:
    if not dialogue_id:
        return
    seviye = (seviye or "A1").strip().upper()
    with db_session(DIALOGUES_DB_PATH) as conn:
        conn.execute("UPDATE diyaloglar SET seviye=? WHERE id=?", (seviye, dialogue_id))


# ==============================================================================
# 4. PAKET KALİTE DENETLEYİCİSİ
# ==============================================================================
def audit_package_quality(mode: str, paket_id: str = "all", dialogue_id: str = "all") -> dict:
    issues_list = []
    total_checked = 0
    missing_haraka = 0
    missing_tr = 0
    broken_audio = 0

    if mode == "words":
        items = get_words(None if paket_id == "all" else paket_id)
        for w in items:
            total_checked += 1
            probs = check_item_quality(w.id, w.arapca, w.turkce, w.ses, _resolve_media_path_light)
            if probs:
                if any("Hareke" in p for p in probs):
                    missing_haraka += 1
                if any("Türkçe" in p for p in probs):
                    missing_tr += 1
                if any("Ses" in p for p in probs):
                    broken_audio += 1
                issues_list.append((str(w.id), f"[{w.seviye}] {w.paket_id}", w.turkce, w.arapca, " | ".join(probs)))

    elif mode == "dialogue":
        lines = get_dialogue_lines_with_titles(paket_id=paket_id, dialogue_id=dialogue_id)
        for lid, did, b_tr, sira, spk, cins, ar, tr, ses, d_sev in lines:
            total_checked += 1
            probs = check_item_quality(str(lid), ar, tr, ses, _resolve_media_path_light)
            if probs:
                if any("Hareke" in p for p in probs):
                    missing_haraka += 1
                if any("Türkçe" in p for p in probs):
                    missing_tr += 1
                if any("Ses" in p for p in probs):
                    broken_audio += 1
                issues_list.append((str(lid), f"[{did} | {d_sev}] {b_tr}", tr, ar, " | ".join(probs)))

    else:
        sents = get_key_sentences("all", paket_id=paket_id)
        for s in sents:
            total_checked += 1
            probs = check_item_quality(s.id, s.arapca, s.turkce, s.ses, _resolve_media_path_light)
            if probs:
                if any("Hareke" in p for p in probs):
                    missing_haraka += 1
                if any("Türkçe" in p for p in probs):
                    missing_tr += 1
                if any("Ses" in p for p in probs):
                    broken_audio += 1
                issues_list.append((str(s.id), f"[{s.seviye}] {s.paket_id}", s.turkce, s.arapca, " | ".join(probs)))

    return {
        "total_checked": total_checked,
        "issue_count": len(issues_list),
        "missing_haraka": missing_haraka,
        "missing_tr": missing_tr,
        "broken_audio": broken_audio,
        "issues": issues_list
    }


# ==============================================================================
# 5. KELİME VE SARF SÖZLÜĞÜ İŞLEMLERİ
# ==============================================================================
def get_word_packages(seviye: Optional[str] = None) -> List[Tuple[str, str]]:
    with db_session(WORDS_DB_PATH) as conn:
        rows = conn.execute("SELECT id, ad FROM paketler ORDER BY rowid").fetchall()
    regular_pkgs = [r for r in rows if not r[0].startswith("szl_") and r[0] != "sozluk_tumu"]
    szl_master = [r for r in rows if r[0] == "sozluk_tumu"]
    szl_cats = sorted([r for r in rows if r[0].startswith("szl_")], key=lambda x: x[1])
    return regular_pkgs + szl_master + szl_cats


def add_word_package(paket_id: str, paket_adi: str, seviye: str = "A1", konu: str = "Genel") -> None:
    with db_session(WORDS_DB_PATH) as conn:
        conn.execute(
            "INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, ?)",
            (paket_id, paket_adi, seviye, konu)
        )
    if not paket_id.startswith("szl_") and paket_id != "sozluk_tumu":
        os.makedirs(os.path.join(CATEGORIES_DIR, paket_id, "images"), exist_ok=True)
        os.makedirs(os.path.join(CATEGORIES_DIR, paket_id, "sounds"), exist_ok=True)


def delete_word_package(paket_id: str) -> None:
    with db_session(WORDS_DB_PATH) as conn:
        if paket_id == "sozluk_tumu":
            conn.execute("DELETE FROM kelimeler WHERE paket_id LIKE 'szl_%'")
            conn.execute("DELETE FROM paketler WHERE id LIKE 'szl_%' OR id='sozluk_tumu'")
        else:
            conn.execute("DELETE FROM kelimeler WHERE paket_id=?", (paket_id,))
            conn.execute("DELETE FROM paketler WHERE id=?", (paket_id,))


def get_words(paket_id: Optional[str] = None, search_query: str = "", seviye: Optional[str] = None) -> List[WordItem]:
    if paket_id == "gunluk_tekrar":
        items = get_daily_smart_review_items("words", limit=15)
        if seviye and seviye not in ("Tümü", "all"):
            items = [w for w in items if getattr(w, "seviye", "A1") == seviye]
        return items

    cols = (
        "id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, "
        "COALESCE(is_fav,0), COALESCE(is_learned,0), COALESCE(seviye,'A1'), "
        "COALESCE(interval_days,0), COALESCE(ease_factor,2.5), COALESCE(next_review,'')"
    )
    where_clauses = []
    params = []

    if paket_id == "zayif":
        where_clauses.append("yanlis > 0")
    elif paket_id == "tekrar":
        where_clauses.append("COALESCE(is_fav,0) = 1")
    elif paket_id == "ogrenilen":
        where_clauses.append("(COALESCE(is_learned,0) = 1 OR dogru > 0)")
    elif paket_id == "sozluk_tumu":
        where_clauses.append("paket_id LIKE 'szl_%'")
    elif paket_id == "gorselli_tumu":
        where_clauses.append("paket_id NOT LIKE 'szl_%'")
    elif paket_id and paket_id != "all":
        where_clauses.append("paket_id = ?")
        params.append(paket_id)

    if seviye and seviye not in ("Tümü", "all"):
        where_clauses.append("COALESCE(seviye,'A1') = ?")
        params.append(seviye)

    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    order_sql = "ORDER BY yanlis DESC, dogru ASC" if paket_id == "zayif" else "ORDER BY rowid"

    with db_session(WORDS_DB_PATH) as conn:
        rows = conn.execute(f"SELECT {cols} FROM kelimeler {where_sql} {order_sql}", params).fetchall()

    items = [WordItem(*r) for r in rows]
    if search_query:
        q = search_query.lower().strip()
        items = [w for w in items if q in w.turkce.lower() or q in w.arapca or q in str(w.id).lower() or q in w.seviye.lower()]
    return items


def update_word_meta(wid: str, is_fav: Optional[int] = None, is_learned: Optional[int] = None, seviye: Optional[str] = None) -> None:
    with db_session(WORDS_DB_PATH) as conn:
        if is_fav is not None:
            conn.execute("UPDATE kelimeler SET is_fav=? WHERE id=?", (is_fav, wid))
        if is_learned is not None:
            conn.execute("UPDATE kelimeler SET is_learned=? WHERE id=?", (is_learned, wid))
        if seviye is not None:
            conn.execute("UPDATE kelimeler SET seviye=? WHERE id=?", (seviye.strip().upper(), wid))


def delete_word_db(word_id: str) -> None:
    with db_session(WORDS_DB_PATH) as conn:
        conn.execute("DELETE FROM kelimeler WHERE id=?", (word_id,))


# ==============================================================================
# 6. DİYALOG İŞLEMLERİ
# ==============================================================================
def get_dialogue_packages() -> List[Tuple[str, str]]:
    with db_session(DIALOGUES_DB_PATH) as conn:
        return conn.execute("SELECT id, ad FROM paketler ORDER BY rowid").fetchall()


def get_dialogues(paket_id: Optional[str] = None, seviye: Optional[str] = None) -> List[Tuple[str, str, str, str]]:
    params = []
    where_clauses = []
    if paket_id and paket_id != "all":
        where_clauses.append("paket_id=?")
        params.append(paket_id)
    if seviye and seviye not in ("Tümü", "all"):
        where_clauses.append("COALESCE(seviye,'A1')=?")
        params.append(seviye)
    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    with db_session(DIALOGUES_DB_PATH) as conn:
        return conn.execute(f"SELECT id, paket_id, baslik_ar, baslik_tr FROM diyaloglar {where_sql} ORDER BY rowid", params).fetchall()


def get_dialogue_lines(dialogue_id: Optional[str] = None, search_query: str = "") -> list:
    with db_session(DIALOGUES_DB_PATH) as conn:
        if dialogue_id and dialogue_id != "all":
            rows = conn.execute("""
                SELECT id, sira, konusmaci, konusmaci_ar, cinsiyet, arapca, turkce, ses 
                FROM diyalog_satirlari WHERE diyalog_id=? ORDER BY sira, id
            """, (dialogue_id,)).fetchall()
        else:
            rows = conn.execute("""
                SELECT id, sira, konusmaci, konusmaci_ar, cinsiyet, arapca, turkce, ses 
                FROM diyalog_satirlari ORDER BY diyalog_id, sira, id
            """).fetchall()
    if search_query:
        q = search_query.lower().strip()
        rows = [r for r in rows if q in (r[2] or "").lower() or q in (r[5] or "") or q in (r[6] or "").lower()]
    return rows


def get_dialogue_id_for_line(line_id: int) -> str:
    with db_session(DIALOGUES_DB_PATH) as conn:
        row = conn.execute("SELECT diyalog_id FROM diyalog_satirlari WHERE id=?", (int(line_id),)).fetchone()
    return row[0] if row else ""


def update_single_dialogue_line_db(
    line_id: int, konusmaci: str, konusmaci_ar: str, cinsiyet: str,
    arapca: str, turkce: str, ses: Optional[str] = None, diyalog_seviye: Optional[str] = None
) -> None:
    with db_session(DIALOGUES_DB_PATH) as conn:
        conn.execute("""
            UPDATE diyalog_satirlari
            SET konusmaci=?, konusmaci_ar=?, cinsiyet=?, arapca=?, turkce=?, ses=?
            WHERE id=?
        """, (
            clean_invisible_marks(konusmaci),
            sanitize_arabic_sentence(konusmaci_ar),
            cinsiyet if cinsiyet in ("E", "K") else "E",
            sanitize_arabic_sentence(arapca),
            sanitize_turkish_sentence(turkce),
            ses if ses else None,
            int(line_id)
        ))
        if diyalog_seviye:
            row = conn.execute("SELECT diyalog_id FROM diyalog_satirlari WHERE id=?", (int(line_id),)).fetchone()
            if row and row[0]:
                conn.execute("UPDATE diyaloglar SET seviye=? WHERE id=?", (diyalog_seviye.strip().upper(), row[0]))


def delete_single_dialogue_line_db(line_id: int) -> None:
    with db_session(DIALOGUES_DB_PATH) as conn:
        conn.execute("DELETE FROM diyalog_satirlari WHERE id=?", (int(line_id),))


def get_dialogue_lines_with_titles(
    paket_id: str = "all",
    dialogue_id: str = "all",
    search_query: str = "",
    seviye: Optional[str] = None
) -> list:
    params = []
    where_clauses = []
    if paket_id and paket_id != "all":
        where_clauses.append("d.paket_id = ?")
        params.append(paket_id)
    if dialogue_id and dialogue_id != "all":
        where_clauses.append("s.diyalog_id = ?")
        params.append(dialogue_id)
    if seviye and seviye not in ("Tümü", "all"):
        where_clauses.append("COALESCE(d.seviye, 'A1') = ?")
        params.append(seviye)

    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    sql = f"""
        SELECT s.id, s.diyalog_id, COALESCE(d.baslik_tr, s.diyalog_id), s.sira,
               s.konusmaci, s.cinsiyet, s.arapca, s.turkce, s.ses, COALESCE(d.seviye, 'A1')
        FROM diyalog_satirlari s
        LEFT JOIN diyaloglar d ON s.diyalog_id = d.id
        {where_sql}
        ORDER BY d.rowid, s.sira, s.id
    """
    with db_session(DIALOGUES_DB_PATH) as conn:
        rows = conn.execute(sql, params).fetchall()

    if search_query:
        q = search_query.lower().strip()
        rows = [
            r for r in rows
            if q in str(r[1]).lower() or q in (r[2] or "").lower()
            or q in (r[4] or "").lower() or q in (r[6] or "") or q in (r[7] or "").lower()
            or q in (r[9] or "").lower()
        ]
    return rows


def delete_dialogue_db(dialogue_id: str) -> None:
    with db_session(DIALOGUES_DB_PATH) as conn:
        conn.execute("DELETE FROM diyaloglar WHERE id=?", (dialogue_id,))
        conn.execute("DELETE FROM diyalog_satirlari WHERE diyalog_id=?", (dialogue_id,))


def delete_dialogue_package(paket_id: str) -> None:
    with db_session(DIALOGUES_DB_PATH) as conn:
        c = conn.cursor()
        dlg_ids = [r[0] for r in c.execute("SELECT id FROM diyaloglar WHERE paket_id=?", (paket_id,)).fetchall()]
        for did in dlg_ids:
            c.execute("DELETE FROM diyalog_satirlari WHERE diyalog_id=?", (did,))
        c.execute("DELETE FROM diyaloglar WHERE paket_id=?", (paket_id,))
        c.execute("DELETE FROM paketler WHERE id=?", (paket_id,))


def _copy_custom_audio_from_dir(ses_val: str, media_dir: str, prefix_id: str) -> str:
    if not ses_val or not media_dir or ses_val.startswith(("http://", "https://")):
        return ses_val
    base_fn = os.path.basename(ses_val)
    for cand in (
        os.path.join(media_dir, ses_val),
        os.path.join(media_dir, "sounds", base_fn),
        os.path.join(media_dir, base_fn)
    ):
        if os.path.exists(cand) and os.path.isfile(cand):
            dst_name = f"{prefix_id}_{base_fn}"
            shutil.copy(cand, os.path.join(CUSTOM_AUDIO_DIR, dst_name))
            return f"custom_audio/{dst_name}"
    return ses_val


def import_dialogue_json_package(
    json_path: str,
    paket_id: str,
    paket_adi: str,
    media_dir: Optional[str] = None,
    clear_existing: bool = False
) -> int:
    _reset_import_report()
    if media_dir is None:
        media_dir = os.path.dirname(json_path)

    with open(json_path, "r", encoding="utf-8-sig") as f:
        data = json.load(f)

    pkg_seviye = "A1"
    pkg_konu = "Günlük Hayat"
    is_bare_list = False
    if isinstance(data, dict) and "diyaloglar" in data:
        paket_id = data.get("paket_id", paket_id)
        paket_adi = data.get("paket_adi", paket_adi)
        pkg_seviye = data.get("seviye", "A1")
        pkg_konu = data.get("konu", "Günlük Hayat")
        dlg_list = data["diyaloglar"]
    elif isinstance(data, list):
        dlg_list = data
        is_bare_list = True
    else:
        raise ValueError("Geçersiz JSON diyalog paketi formatı.")

    count = 0
    with db_session(DIALOGUES_DB_PATH) as conn:
        c = conn.cursor()
        if clear_existing:
            c.execute("DELETE FROM diyalog_satirlari")
            c.execute("DELETE FROM diyaloglar")
            c.execute("DELETE FROM paketler")
            if is_bare_list:
                paket_id = "muhadesat_paketi"
                paket_adi = "Muhâdesât (Günlük Diyaloglar)"
        elif is_bare_list:
            has_muhadesat = c.execute("SELECT 1 FROM paketler WHERE id='muhadesat_paketi'").fetchone()
            if has_muhadesat and any(str(d.get("id", "")).startswith("dlg_") for d in dlg_list[:5]):
                paket_id = "muhadesat_paketi"
                paket_adi = "Muhâdesât (Günlük Diyaloglar)"

        c.execute(
            "INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, ?)",
            (paket_id, paket_adi, pkg_seviye, pkg_konu)
        )

        for d_idx, dlg in enumerate(dlg_list, 1):
            did = clean_invisible_marks(str(dlg.get("id", f"dlg_{int(time.time()*1000)}_{count}")))
            b_ar = sanitize_arabic_sentence(dlg.get("baslik_ar", ""))
            b_tr = clean_invisible_marks(dlg.get("baslik_tr", dlg.get("title", "Diyalog")))
            d_sev = (dlg.get("seviye") or pkg_seviye or "A1").strip().upper()
            d_konu = dlg.get("konu", pkg_konu)
            if did == "dlg_33" and b_tr == "Seyahat Acentasında":
                b_tr = "Seyahat Acentasında (Karadeniz)"
            elif did == "dlg_46" and b_tr in ("Tur Şirketinde", "Seyahat Acentasında"):
                b_tr = "Tur Şirketinde (Çanakkale)"

            c.execute(
                "INSERT OR REPLACE INTO diyaloglar (id, paket_id, baslik_ar, baslik_tr, seviye, konu) VALUES (?, ?, ?, ?, ?, ?)",
                (did, paket_id, b_ar, b_tr, d_sev, d_konu)
            )
            c.execute("DELETE FROM diyalog_satirlari WHERE diyalog_id=?", (did,))

            for idx, line in enumerate(dlg.get("satirlar", []), 1):
                sira = int(line.get("sira", idx))
                spk = clean_invisible_marks(line.get("konusmaci", "Kişi"))
                spk_ar = sanitize_arabic_sentence(line.get("konusmaci_ar", ""))
                cins = clean_invisible_marks(line.get("cinsiyet", "E")).upper()
                if cins not in ("E", "K"):
                    cins = "E"
                ar = sanitize_arabic_sentence(line.get("arapca", ""))
                tr = sanitize_turkish_sentence(line.get("turkce", ""))
                ses_raw = line.get("ses", None)
                ses = _copy_custom_audio_from_dir(ses_raw, media_dir, f"{did}_{sira}") if ses_raw else None
                if ar:
                    c.execute("""
                        INSERT INTO diyalog_satirlari 
                        (diyalog_id, sira, konusmaci, konusmaci_ar, cinsiyet, arapca, turkce, ses, dogru, yanlis)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
                    """, (did, sira, spk, spk_ar, cins, ar, tr, ses))
                else:
                    _record_skipped_line(d_idx, f"{did} satır {sira}: Arapça metin boş")
            count += 1

    _LAST_IMPORT_REPORT["added"] = count
    return count


# ==============================================================================
# 7. CÜMLE İŞLEMLERİ
# ==============================================================================
def get_sentence_packages() -> List[Tuple[str, str]]:
    with db_session(SENTENCES_DB_PATH) as conn:
        return conn.execute("SELECT id, ad FROM paketler ORDER BY rowid").fetchall()


def delete_sentence_package(paket_id: str) -> None:
    with db_session(SENTENCES_DB_PATH) as conn:
        conn.execute("DELETE FROM cumleler WHERE paket_id=?", (paket_id,))
        conn.execute("DELETE FROM paketler WHERE id=?", (paket_id,))


def get_key_sentences(
    filter_type: str = "all",
    paket_id: Optional[str] = None,
    search_query: str = "",
    seviye: Optional[str] = None
) -> List[SentenceItem]:
    if filter_type == "daily_review":
        items = get_daily_smart_review_items("sentences", limit=15)
        if seviye and seviye not in ("Tümü", "all"):
            items = [s for s in items if getattr(s, "seviye", "A1") == seviye]
        return items

    params, where_clauses = [], []

    if paket_id and paket_id != "all":
        where_clauses.append("paket_id=?")
        params.append(paket_id)

    if filter_type == "fav":
        where_clauses.append("is_fav=1")
    elif filter_type == "learned":
        where_clauses.append("is_learned=1")
    elif filter_type == "difficult":
        where_clauses.append("yanlis > 0")

    if seviye and seviye not in ("Tümü", "all"):
        where_clauses.append("COALESCE(seviye,'A1')=?")
        params.append(seviye)

    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    order_sql = "ORDER BY yanlis DESC, dogru ASC" if filter_type == "difficult" else "ORDER BY rowid"
    cols = (
        "id, paket_id, arapca, turkce, ses, is_fav, is_learned, dogru, yanlis, "
        "COALESCE(seviye,'A1'), COALESCE(interval_days,0), COALESCE(ease_factor,2.5), COALESCE(next_review,'')"
    )

    with db_session(SENTENCES_DB_PATH) as conn:
        rows = conn.execute(f"SELECT {cols} FROM cumleler {where_sql} {order_sql}", params).fetchall()

    items = [SentenceItem(*r) for r in rows]
    if search_query:
        q = search_query.lower().strip()
        items = [s for s in items if q in (s.turkce or "").lower() or q in (s.arapca or "") or q in str(s.id).lower() or q in s.seviye.lower()]
    return items


def update_sentence_meta(
    sid: str,
    is_fav: Optional[int] = None,
    is_learned: Optional[int] = None,
    seviye: Optional[str] = None
) -> None:
    with db_session(SENTENCES_DB_PATH) as conn:
        if is_fav is not None:
            conn.execute("UPDATE cumleler SET is_fav=? WHERE id=?", (is_fav, sid))
        if is_learned is not None:
            conn.execute("UPDATE cumleler SET is_learned=? WHERE id=?", (is_learned, sid))
        if seviye is not None:
            conn.execute("UPDATE cumleler SET seviye=? WHERE id=?", (seviye.strip().upper(), sid))


# ==============================================================================
# 8. GERÇEK ARALIKLI TEKRAR (SM-2) VE GELİŞMİŞ DİNLE-BUL SINAV MOTORU
# ==============================================================================
def _compute_sm2_schedule(cur_interval: int, cur_ease: float, is_correct: bool) -> Tuple[int, float, str]:
    today = datetime.date.today()
    cur_interval = int(cur_interval or 0)
    cur_ease = float(cur_ease or 2.5)

    if is_correct:
        if cur_interval <= 0:
            new_interval = 1
        elif cur_interval == 1:
            new_interval = 3
        else:
            new_interval = max(2, int(round(cur_interval * cur_ease)))
        new_interval = min(180, new_interval)
        new_ease = min(3.0, round(cur_ease + 0.10, 2))
        next_date = (today + datetime.timedelta(days=new_interval)).isoformat()
    else:
        new_interval = 1
        new_ease = max(1.3, round(cur_ease - 0.20, 2))
        next_date = today.isoformat()

    return new_interval, new_ease, next_date


def get_daily_smart_review_items(mode: str = "words", limit: int = 15) -> list:
    today_str = datetime.date.today().isoformat()
    selected = []
    seen_ids = set()

    if mode == "words":
        cols = (
            "id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, "
            "COALESCE(is_fav,0), COALESCE(is_learned,0), COALESCE(seviye,'A1'), "
            "COALESCE(interval_days,0), COALESCE(ease_factor,2.5), COALESCE(next_review,'')"
        )
        queries = [
            (f"SELECT {cols} FROM kelimeler WHERE COALESCE(next_review,'') != '' AND next_review <= ? ORDER BY next_review ASC, yanlis DESC LIMIT ?", (today_str, 8)),
            (f"SELECT {cols} FROM kelimeler WHERE yanlis > 0 ORDER BY (yanlis - dogru) DESC, yanlis DESC LIMIT ?", (6,)),
            (f"SELECT {cols} FROM kelimeler WHERE COALESCE(is_fav,0)=1 ORDER BY RANDOM() LIMIT ?", (5,)),
            (f"SELECT {cols} FROM kelimeler WHERE dogru=0 AND yanlis=0 ORDER BY RANDOM() LIMIT ?", (limit,)),
            (f"SELECT {cols} FROM kelimeler ORDER BY RANDOM() LIMIT ?", (limit,))
        ]
        with db_session(WORDS_DB_PATH) as conn:
            c = conn.cursor()
            for sql, params in queries:
                if len(selected) >= limit:
                    break
                for r in c.execute(sql, params).fetchall():
                    if r[0] not in seen_ids:
                        seen_ids.add(r[0])
                        selected.append(WordItem(*r))
                        if len(selected) >= limit:
                            break
        return selected

    else:
        cols = (
            "id, paket_id, arapca, turkce, ses, is_fav, is_learned, dogru, yanlis, "
            "COALESCE(seviye,'A1'), COALESCE(interval_days,0), COALESCE(ease_factor,2.5), COALESCE(next_review,'')"
        )
        queries = [
            (f"SELECT {cols} FROM cumleler WHERE COALESCE(next_review,'') != '' AND next_review <= ? ORDER BY next_review ASC, yanlis DESC LIMIT ?", (today_str, 8)),
            (f"SELECT {cols} FROM cumleler WHERE yanlis > 0 ORDER BY (yanlis - dogru) DESC, yanlis DESC LIMIT ?", (6,)),
            (f"SELECT {cols} FROM cumleler WHERE is_fav=1 ORDER BY RANDOM() LIMIT ?", (5,)),
            (f"SELECT {cols} FROM cumleler WHERE dogru=0 AND yanlis=0 ORDER BY RANDOM() LIMIT ?", (limit,)),
            (f"SELECT {cols} FROM cumleler ORDER BY RANDOM() LIMIT ?", (limit,))
        ]
        with db_session(SENTENCES_DB_PATH) as conn:
            c = conn.cursor()
            for sql, params in queries:
                if len(selected) >= limit:
                    break
                for r in c.execute(sql, params).fetchall():
                    if r[0] not in seen_ids:
                        seen_ids.add(r[0])
                        selected.append(SentenceItem(*r))
                        if len(selected) >= limit:
                            break
        return selected


def generate_listening_warmup_questions(
    mode: str = "words",
    pool_items: Optional[list] = None,
    count: int = 10,
    paket_id: str = "all",
    seviye: str = "Tümü"
) -> list:
    if pool_items is None:
        if mode == "words":
            target_pkg = "gorselli_tumu" if paket_id in ("all", "") else paket_id
            pool_items = get_words(target_pkg, seviye=seviye)
        elif mode == "dictionary":
            target_pkg = "sozluk_tumu" if paket_id in ("all", "") else paket_id
            pool_items = get_words(target_pkg, seviye=seviye)
        elif mode == "dialogue":
            lines = get_dialogue_lines_with_titles(
                paket_id="all",
                dialogue_id="all" if paket_id in ("all", "") else paket_id,
                seviye=seviye
            )
            pool_items = [
                SentenceItem(
                    id=f"dlgline_{lid}", paket_id=did, arapca=ar,
                    turkce=tr or "", ses=ses, seviye=d_sev
                )
                for (lid, did, _, _, _, _, ar, tr, ses, d_sev) in lines
                if ar and tr
            ]
        else:
            pool_items = get_key_sentences(
                "all",
                paket_id=None if paket_id in ("all", "") else paket_id,
                seviye=seviye
            )

    valid_pool = [it for it in (pool_items or []) if getattr(it, "arapca", "") and getattr(it, "turkce", "")]
    distinct_tr = {it.turkce for it in valid_pool}
    if len(valid_pool) < 4 or len(distinct_tr) < 4:
        return []

    targets = []
    while len(targets) < count:
        need = count - len(targets)
        if need >= len(valid_pool):
            shuffled_batch = list(valid_pool)
            random.shuffle(shuffled_batch)
            targets.extend(shuffled_batch)
        else:
            targets.extend(random.sample(valid_pool, need))

    questions = []
    for target in targets:
        distractors = [it for it in valid_pool if it.id != target.id and it.turkce != target.turkce]
        unique_dist_map = {}
        for d_it in distractors:
            if d_it.turkce not in unique_dist_map:
                unique_dist_map[d_it.turkce] = d_it
        unique_distractors = list(unique_dist_map.values())
        if len(unique_distractors) < 3:
            continue
        chosen_dist = random.sample(unique_distractors, 3)
        options = chosen_dist + [target]
        random.shuffle(options)
        questions.append({
            "target": target,
            "options": options,
            "correct_id": target.id
        })
    return questions


# ==============================================================================
# 9. PAKET DIŞA/İÇE AKTARMA, EXCEL PANODAN YAPIŞTIRMA VE TOPLU MEDYA EŞLEŞTİRİCİ
# ==============================================================================
def export_full_package_zip(mode: str, paket_id: str, paket_adi: str, save_zip_path: str) -> Tuple[int, int]:
    media_copied = 0
    item_count = 0
    sev, kon = get_package_level_and_topic(mode, paket_id)

    with zipfile.ZipFile(save_zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        if mode == "words":
            words = get_words(paket_id)
            item_count = len(words)
            export_list = []
            for w in words:
                g_zip_rel, s_zip_rel = None, None
                if w.gorsel:
                    full_g = _resolve_media_path_light(w.gorsel)
                    if full_g and os.path.exists(full_g):
                        g_name = os.path.basename(full_g)
                        g_zip_rel = f"images/{g_name}"
                        zf.write(full_g, g_zip_rel)
                        media_copied += 1
                if w.ses:
                    full_s = _resolve_media_path_light(w.ses)
                    if full_s and os.path.exists(full_s):
                        s_name = os.path.basename(full_s)
                        s_zip_rel = f"sounds/{s_name}"
                        zf.write(full_s, s_zip_rel)
                        media_copied += 1
                        if "_f." in full_s:
                            m_cand = full_s.replace("_f.", "_m.")
                            if os.path.exists(m_cand):
                                zf.write(m_cand, f"sounds/{os.path.basename(m_cand)}")
                                media_copied += 1

                export_list.append({
                    "id": w.id, "arapca": w.arapca, "turkce": w.turkce,
                    "seviye": getattr(w, "seviye", "A1"),
                    "gorsel": g_zip_rel, "ses": s_zip_rel
                })
            payload = {"paket_id": paket_id, "paket_adi": paket_adi, "seviye": sev, "konu": kon, "tur": "words", "kelimeler": export_list}

        elif mode == "dialogue":
            dlgs = get_dialogues(paket_id)
            item_count = len(dlgs)
            dlg_list = []
            for did, _, b_ar, b_tr in dlgs:
                d_sev, d_kon = get_package_level_and_topic("dialogue", did)
                lines = get_dialogue_lines(did)
                satirlar = []
                for lid, sira, spk, spk_ar, cins, ar, tr, ses in lines:
                    s_zip_rel = None
                    if ses:
                        full_s = _resolve_media_path_light(ses)
                        if full_s and os.path.exists(full_s):
                            s_name = f"{did}_{sira}_{os.path.basename(full_s)}"
                            s_zip_rel = f"sounds/{s_name}"
                            zf.write(full_s, s_zip_rel)
                            media_copied += 1
                    satirlar.append({
                        "sira": sira, "konusmaci": spk, "konusmaci_ar": spk_ar,
                        "cinsiyet": cins, "arapca": ar, "turkce": tr, "ses": s_zip_rel
                    })
                dlg_list.append({"id": did, "baslik_ar": b_ar, "baslik_tr": b_tr, "seviye": d_sev, "konu": d_kon, "satirlar": satirlar})
            payload = {"paket_id": paket_id, "paket_adi": paket_adi, "seviye": sev, "konu": kon, "tur": "dialogue", "diyaloglar": dlg_list}

        else:
            sents = get_key_sentences("all", paket_id=paket_id)
            item_count = len(sents)
            sent_list = []
            for s in sents:
                s_zip_rel = None
                if s.ses:
                    full_s = _resolve_media_path_light(s.ses)
                    if full_s and os.path.exists(full_s):
                        s_name = f"{s.id}_{os.path.basename(full_s)}"
                        s_zip_rel = f"sounds/{s_name}"
                        zf.write(full_s, s_zip_rel)
                        media_copied += 1
                sent_list.append({
                    "id": s.id, "arapca": s.arapca, "turkce": s.turkce,
                    "seviye": getattr(s, "seviye", "A1"), "ses": s_zip_rel
                })
            payload = {"paket_id": paket_id, "paket_adi": paket_adi, "seviye": sev, "konu": kon, "tur": "sentences", "cumleler": sent_list}

        zf.writestr("paket.json", json.dumps(payload, ensure_ascii=False, indent=2))
        readme = (
            f"ARAPÇA TELAFFUZ VE KONUŞMA ASİSTANI — TAM PAKET ARŞİVİ\n"
            f"Paket Adı : {paket_adi} ({paket_id})\n"
            f"Seviye    : {sev} | Konu: {kon}\n"
            f"Kayıt     : {item_count} adet\n"
            f"Medya     : {media_copied} adet görsel/ses dosyası\n"
        )
        zf.writestr("BENIOKU.txt", readme)

    return item_count, media_copied


def smart_import_package(mode: str, file_path: str, clear_existing: bool = False) -> Tuple[str, str, int]:
    _reset_import_report()
    ext = os.path.splitext(file_path)[1].lower()
    base_name = os.path.splitext(os.path.basename(file_path))[0].replace("_tam_paket", "")
    paket_id = re.sub(r"[^a-z0-9_]", "", base_name.lower()) or f"pkg_{int(time.time())}"
    paket_adi = base_name.replace("_", " ").title()

    temp_dir = None
    data_file = file_path
    media_dir = os.path.dirname(file_path)

    try:
        if ext == ".zip":
            temp_dir = tempfile.mkdtemp(prefix="arapca_pkg_")
            with zipfile.ZipFile(file_path, "r") as zf:
                zf.extractall(temp_dir)
            media_dir = temp_dir
            found_data = None
            for root_d, _, files in os.walk(temp_dir):
                if "paket.json" in files:
                    found_data = os.path.join(root_d, "paket.json")
                    media_dir = root_d
                    break
                for fn in files:
                    if fn.lower().endswith((".json", ".csv")):
                        found_data = os.path.join(root_d, fn)
                        media_dir = root_d
                        break
                if found_data:
                    break
            if not found_data:
                raise ValueError("ZIP paketi içinde .json veya .csv veri dosyası bulunamadı.")
            data_file = found_data

        if data_file.lower().endswith(".json"):
            with open(data_file, "r", encoding="utf-8-sig") as f_chk:
                peek_data = json.load(f_chk)
            if isinstance(peek_data, list) and peek_data and isinstance(peek_data[0], dict):
                first_row = peek_data[0]
                if "kok" in first_row and ("mufret" in first_row or "mazi" in first_row):
                    res_szl = import_sozluk_json(data_file, clear_existing=clear_existing)
                    _LAST_IMPORT_REPORT["added"] = res_szl["unique_word_cards"]
                    return "sozluk_tumu", f"📖 Sarf Sözlüğü ({res_szl['categories']} Kategori)", res_szl["unique_word_cards"]

        if mode == "words":
            cnt = _import_words_from_file(data_file, media_dir, paket_id, paket_adi, clear_existing=clear_existing)
            return paket_id, paket_adi, cnt
        elif mode == "dialogue":
            if data_file.lower().endswith(".json"):
                cnt = import_dialogue_json_package(data_file, paket_id, paket_adi, media_dir=media_dir, clear_existing=clear_existing)
                return paket_id, paket_adi, cnt
            else:
                cnt = _import_dialogue_csv(data_file, paket_id, paket_adi, media_dir=media_dir, clear_existing=clear_existing)
                return paket_id, paket_adi, cnt
        else:
            cnt = _import_sentences_from_file(data_file, paket_id, paket_adi, media_dir=media_dir, clear_existing=clear_existing)
            return paket_id, paket_adi, cnt
    finally:
        if temp_dir and os.path.exists(temp_dir):
            shutil.rmtree(temp_dir, ignore_errors=True)


def _copy_media_if_exists(wid, gorsel, ses, media_dir, paket_id, cat_dir):
    g_rel, s_rel = gorsel, ses
    if media_dir and os.path.exists(media_dir):
        img_cands = []
        if gorsel and not gorsel.startswith(("http://", "https://")):
            img_cands.extend([
                os.path.join(media_dir, gorsel),
                os.path.join(media_dir, "images", os.path.basename(gorsel)),
                os.path.join(media_dir, os.path.basename(gorsel))
            ])
        img_cands.extend([
            os.path.join(media_dir, f"{wid}.png"),
            os.path.join(media_dir, "images", f"{wid}.png"),
            os.path.join(media_dir, f"{wid}.jpg"),
            os.path.join(media_dir, "images", f"{wid}.jpg")
        ])
        for src in img_cands:
            if os.path.exists(src) and os.path.isfile(src):
                dst_name = os.path.basename(src)
                shutil.copy(src, os.path.join(cat_dir, "images", dst_name))
                g_rel = f"categories/{paket_id}/images/{dst_name}"
                break

        snd_cands = []
        if ses and not ses.startswith(("http://", "https://")):
            snd_cands.extend([
                os.path.join(media_dir, ses),
                os.path.join(media_dir, "sounds", os.path.basename(ses)),
                os.path.join(media_dir, os.path.basename(ses))
            ])
        snd_cands.extend([
            os.path.join(media_dir, f"{wid}.mp3"),
            os.path.join(media_dir, "sounds", f"{wid}.mp3"),
            os.path.join(media_dir, f"{wid}_f.mp3"),
            os.path.join(media_dir, "sounds", f"{wid}_f.mp3"),
            os.path.join(media_dir, f"{wid}.wav"),
            os.path.join(media_dir, "sounds", f"{wid}.wav")
        ])
        for src in snd_cands:
            if os.path.exists(src) and os.path.isfile(src):
                dst_name = os.path.basename(src)
                shutil.copy(src, os.path.join(cat_dir, "sounds", dst_name))
                s_rel = f"categories/{paket_id}/sounds/{dst_name}"
                if "_f." in src:
                    m_src = src.replace("_f.", "_m.")
                    if os.path.exists(m_src):
                        shutil.copy(m_src, os.path.join(cat_dir, "sounds", os.path.basename(m_src)))
                break
    return g_rel, s_rel


def _import_words_from_file(data_file: str, media_dir: str, paket_id: str, paket_adi: str, clear_existing: bool = False) -> int:
    _reset_import_report()
    if clear_existing:
        with db_session(WORDS_DB_PATH) as conn_clr:
            conn_clr.execute("DELETE FROM kelimeler")
            conn_clr.execute("DELETE FROM paketler")

    add_word_package(paket_id, paket_adi)
    cat_dir = os.path.join(CATEGORIES_DIR, paket_id)
    added = 0
    pkg_sev = "A1"

    with db_session(WORDS_DB_PATH) as conn:
        c = conn.cursor()
        if data_file.lower().endswith(".json"):
            with open(data_file, "r", encoding="utf-8-sig") as f:
                data = json.load(f)
            if isinstance(data, dict):
                paket_id = data.get("paket_id", paket_id)
                paket_adi = data.get("paket_adi", paket_adi)
                pkg_sev = data.get("seviye", "A1")
                kon = data.get("konu", "Genel")
                add_word_package(paket_id, paket_adi, pkg_sev, kon)
                cat_dir = os.path.join(CATEGORIES_DIR, paket_id)
                items = data.get("kelimeler", [])
            else:
                items = data
            for idx, item in enumerate(items, 1):
                wid = str(item.get("id", f"{paket_id}_{idx}")).strip()
                ar = sanitize_arabic_sentence(item.get("arapca", ""))
                tr = sanitize_turkish_sentence(item.get("turkce", ""))
                w_sev = (item.get("seviye") or pkg_sev or "A1").strip().upper()
                if not ar or not tr:
                    _record_skipped_line(idx, "Arapça veya Türkçe alanı boş")
                    continue
                g_rel, s_rel = _copy_media_if_exists(wid, item.get("gorsel"), item.get("ses"), media_dir, paket_id, cat_dir)
                c.execute(
                    "INSERT OR REPLACE INTO kelimeler (id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, is_fav, is_learned, seviye) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?)",
                    (wid, paket_id, ar, tr, g_rel, s_rel, w_sev)
                )
                added += 1
        else:
            with open(data_file, "r", encoding="utf-8-sig") as f:
                content = f.read()
                delim = "\t" if "\t" in content else (";" if ";" in content else ",")
                rows = list(csv.reader(content.splitlines(), delimiter=delim))
            for idx, row in enumerate(rows, 1):
                if not any(cell.strip() for cell in row):
                    continue
                if len(row) < 2:
                    _record_skipped_line(idx, "Sütun sayısı yetersiz (<2)")
                    continue
                if idx == 1 and ("id" in row[0].lower() or "arapca" in row[0].lower() or (len(row) > 1 and "arapca" in row[1].lower())):
                    continue
                if len(row) == 2:
                    wid = f"{paket_id}_{int(time.time()*1000)}_{idx}"
                    ar = sanitize_arabic_sentence(row[0])
                    tr = sanitize_turkish_sentence(row[1])
                    gorsel, ses, w_sev = None, None, "A1"
                else:
                    wid = row[0].strip() or f"{paket_id}_{idx}"
                    ar = sanitize_arabic_sentence(row[1])
                    tr = sanitize_turkish_sentence(row[2])
                    gorsel = row[3].strip() if len(row) >= 4 and row[3].strip() else None
                    ses = row[4].strip() if len(row) >= 5 and row[4].strip() else None
                    w_sev = row[5].strip().upper() if len(row) >= 6 and row[5].strip() else "A1"
                if not ar or not tr:
                    _record_skipped_line(idx, "Arapça veya Türkçe alanı boş")
                    continue
                g_rel, s_rel = _copy_media_if_exists(wid, gorsel, ses, media_dir, paket_id, cat_dir)
                c.execute(
                    "INSERT OR REPLACE INTO kelimeler (id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, is_fav, is_learned, seviye) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?)",
                    (wid, paket_id, ar, tr, g_rel, s_rel, w_sev)
                )
                added += 1

    _LAST_IMPORT_REPORT["added"] = added
    return added


def _import_dialogue_csv(csv_file: str, paket_id: str, paket_adi: str, media_dir: Optional[str] = None, clear_existing: bool = False) -> int:
    _reset_import_report()
    with open(csv_file, "r", encoding="utf-8-sig") as f:
        content = f.read()
        delim = "\t" if "\t" in content else (";" if ";" in content else ",")
        rows = list(csv.reader(content.splitlines(), delimiter=delim))

    added = 0
    with db_session(DIALOGUES_DB_PATH) as conn:
        c = conn.cursor()
        if clear_existing:
            c.execute("DELETE FROM diyalog_satirlari")
            c.execute("DELETE FROM diyaloglar")
            c.execute("DELETE FROM paketler")
        c.execute("INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, 'A1', 'Genel')", (paket_id, paket_adi))
        for idx, row in enumerate(rows, 1):
            if not any(cell.strip() for cell in row):
                continue
            if len(row) < 5:
                _record_skipped_line(idx, "Diyalog için en az 5 sütun gerekli")
                continue
            if idx == 1 and ("diyalog" in row[0].lower() or "konusmaci" in row[2].lower()):
                continue
            did = row[0].strip() or f"dlg_{paket_id}"
            b_tr = row[1].strip() or paket_adi
            spk = row[2].strip() or "Kişi"
            cins = row[3].strip().upper() if row[3].strip().upper() in ("E", "K") else "E"
            ar = sanitize_arabic_sentence(row[4])
            tr = sanitize_turkish_sentence(row[5]) if len(row) >= 6 else ""
            ses_raw = row[6].strip() if len(row) >= 7 and row[6].strip() else None
            d_sev = row[7].strip().upper() if len(row) >= 8 and row[7].strip() else "A1"
            ses = _copy_custom_audio_from_dir(ses_raw, media_dir, f"{did}_{idx}") if ses_raw else None
            if ar:
                c.execute("INSERT OR IGNORE INTO diyaloglar (id, paket_id, baslik_ar, baslik_tr, seviye, konu) VALUES (?, ?, '', ?, ?, 'Genel')", (did, paket_id, b_tr, d_sev))
                c.execute("""
                    INSERT INTO diyalog_satirlari (diyalog_id, sira, konusmaci, konusmaci_ar, cinsiyet, arapca, turkce, ses, dogru, yanlis)
                    VALUES (?, ?, ?, '', ?, ?, ?, ?, 0, 0)
                """, (did, idx, spk, cins, ar, tr, ses))
                added += 1
            else:
                _record_skipped_line(idx, "Arapça cümle boş")

    _LAST_IMPORT_REPORT["added"] = added
    return added


def _import_sentences_from_file(data_file: str, paket_id: str, paket_adi: str, media_dir: Optional[str] = None, clear_existing: bool = False) -> int:
    _reset_import_report()
    added = 0
    pkg_sev = "A1"

    with db_session(SENTENCES_DB_PATH) as conn:
        c = conn.cursor()
        if clear_existing:
            c.execute("DELETE FROM cumleler")
            c.execute("DELETE FROM paketler")
        c.execute("INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, 'A1', 'Genel')", (paket_id, paket_adi))

        if data_file.lower().endswith(".json"):
            with open(data_file, "r", encoding="utf-8-sig") as f:
                data = json.load(f)
            if isinstance(data, dict):
                paket_id = data.get("paket_id", paket_id)
                paket_adi = data.get("paket_adi", paket_adi)
                pkg_sev = data.get("seviye", "A1")
                kon = data.get("konu", "Genel")
                c.execute("INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, ?)", (paket_id, paket_adi, pkg_sev, kon))
                items = data.get("cumleler", [])
            else:
                items = data
            for idx, item in enumerate(items, 1):
                sid = str(item.get("id", f"{paket_id}_{idx}")).strip()
                ar = sanitize_arabic_sentence(item.get("arapca", ""))
                tr = sanitize_turkish_sentence(item.get("turkce", ""))
                s_sev = (item.get("seviye") or pkg_sev or "A1").strip().upper()
                ses_raw = item.get("ses", None)
                ses = _copy_custom_audio_from_dir(ses_raw, media_dir, sid) if ses_raw else None
                if ar and tr:
                    c.execute(
                        "INSERT OR REPLACE INTO cumleler (id, paket_id, arapca, turkce, ses, is_fav, is_learned, dogru, yanlis, seviye) VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, ?)",
                        (sid, paket_id, ar, tr, ses, s_sev)
                    )
                    added += 1
                else:
                    _record_skipped_line(idx, "Arapça veya Türkçe cümle boş")
        else:
            with open(data_file, "r", encoding="utf-8-sig") as f:
                content = f.read()
                delim = "\t" if "\t" in content else (";" if ";" in content else ",")
                rows = list(csv.reader(content.splitlines(), delimiter=delim))
            for idx, row in enumerate(rows, 1):
                if not any(cell.strip() for cell in row):
                    continue
                if len(row) < 2:
                    _record_skipped_line(idx, "Sütun sayısı yetersiz (<2)")
                    continue
                if idx == 1 and ("id" in row[0].lower() or "ar_cumle" in row[1].lower() or "arapca" in row[0].lower() or "arapca" in row[1].lower()):
                    continue
                if len(row) == 2:
                    sid = f"{paket_id}_{int(time.time()*1000)}_{idx}"
                    ar = sanitize_arabic_sentence(row[0])
                    tr = sanitize_turkish_sentence(row[1])
                    ses_raw, s_sev = None, "A1"
                else:
                    sid = row[0].strip() or f"{paket_id}_{idx}"
                    ar = sanitize_arabic_sentence(row[1])
                    tr = sanitize_turkish_sentence(row[2])
                    ses_raw = row[3].strip() if len(row) >= 4 and row[3].strip() else None
                    s_sev = row[4].strip().upper() if len(row) >= 5 and row[4].strip() else "A1"
                ses = _copy_custom_audio_from_dir(ses_raw, media_dir, sid) if ses_raw else None
                if ar and tr:
                    c.execute(
                        "INSERT OR REPLACE INTO cumleler (id, paket_id, arapca, turkce, ses, is_fav, is_learned, dogru, yanlis, seviye) VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, ?)",
                        (sid, paket_id, ar, tr, ses, s_sev)
                    )
                    added += 1
                else:
                    _record_skipped_line(idx, "Arapça veya Türkçe cümle boş")

    _LAST_IMPORT_REPORT["added"] = added
    return added


def import_from_clipboard_text(
    mode: str,
    raw_text: str,
    paket_id: str,
    paket_adi: str,
    default_seviye: str = "A1"
) -> Dict:
    """
    Excel veya Word tablosundan kopyalanıp yapıştırılan metni (Tab, ; veya , ayrımını otomatik tanıyarak)
    ayrıştırır ve seçili veritabanına ekler. Eklenen ve hatalı olduğu için atlanan satırların raporunu döndürür.
    """
    _reset_import_report()
    if not raw_text or not raw_text.strip():
        return get_last_import_report()

    lines = [ln for ln in raw_text.splitlines() if ln.strip()]
    if not lines:
        return get_last_import_report()

    # Ayırıcıyı (Tab, noktalı virgül veya virgül) otomatik tespit et
    sample_block = "\n".join(lines[:10])
    if "\t" in sample_block:
        delim = "\t"
    elif ";" in sample_block:
        delim = ";"
    else:
        delim = ","

    rows = list(csv.reader(lines, delimiter=delim))
    added = 0
    default_seviye = (default_seviye or "A1").strip().upper()
    ts_base = int(time.time() * 1000)

    if mode == "words":
        add_word_package(paket_id, paket_adi, default_seviye, "Genel")
        with db_session(WORDS_DB_PATH) as conn:
            c = conn.cursor()
            for idx, row in enumerate(rows, 1):
                cells = [cl.strip() for cl in row]
                if not any(cells):
                    continue
                if idx == 1 and any(k in cells[0].lower() for k in ("id", "arapca", "kelime", "no")):
                    continue
                if len(cells) < 2:
                    _record_skipped_line(idx, "En az 2 sütun (Arapça ve Türkçe) olmalı")
                    continue
                if len(cells) == 2:
                    wid = f"{paket_id}_{ts_base}_{idx}"
                    ar, tr = sanitize_arabic_sentence(cells[0]), sanitize_turkish_sentence(cells[1])
                    lvl = default_seviye
                elif len(cells) == 3 and cells[2].upper() in ("A1", "A2", "B1", "B2", "C1"):
                    wid = f"{paket_id}_{ts_base}_{idx}"
                    ar, tr = sanitize_arabic_sentence(cells[0]), sanitize_turkish_sentence(cells[1])
                    lvl = cells[2].upper()
                else:
                    wid = cells[0] or f"{paket_id}_{ts_base}_{idx}"
                    ar = sanitize_arabic_sentence(cells[1])
                    tr = sanitize_turkish_sentence(cells[2]) if len(cells) >= 3 else ""
                    lvl = cells[3].upper() if len(cells) >= 4 and cells[3].upper() in ("A1", "A2", "B1", "B2", "C1") else default_seviye

                if not ar or not tr:
                    _record_skipped_line(idx, "Arapça veya Türkçe alanı boş")
                    continue
                c.execute(
                    "INSERT OR REPLACE INTO kelimeler (id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, is_fav, is_learned, seviye) VALUES (?, ?, ?, ?, NULL, NULL, 0, 0, 0, 0, ?)",
                    (wid, paket_id, ar, tr, lvl)
                )
                added += 1

    elif mode == "sentences":
        with db_session(SENTENCES_DB_PATH) as conn:
            c = conn.cursor()
            c.execute("INSERT OR IGNORE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, 'Genel')", (paket_id, paket_adi, default_seviye))
            for idx, row in enumerate(rows, 1):
                cells = [cl.strip() for cl in row]
                if not any(cells):
                    continue
                if idx == 1 and any(k in cells[0].lower() for k in ("id", "arapca", "cumle", "no")):
                    continue
                if len(cells) < 2:
                    _record_skipped_line(idx, "En az 2 sütun (Arapça ve Türkçe) olmalı")
                    continue
                if len(cells) == 2:
                    sid = f"{paket_id}_{ts_base}_{idx}"
                    ar, tr = sanitize_arabic_sentence(cells[0]), sanitize_turkish_sentence(cells[1])
                    lvl = default_seviye
                elif len(cells) == 3 and cells[2].upper() in ("A1", "A2", "B1", "B2", "C1"):
                    sid = f"{paket_id}_{ts_base}_{idx}"
                    ar, tr = sanitize_arabic_sentence(cells[0]), sanitize_turkish_sentence(cells[1])
                    lvl = cells[2].upper()
                else:
                    sid = cells[0] or f"{paket_id}_{ts_base}_{idx}"
                    ar = sanitize_arabic_sentence(cells[1])
                    tr = sanitize_turkish_sentence(cells[2]) if len(cells) >= 3 else ""
                    lvl = cells[3].upper() if len(cells) >= 4 and cells[3].upper() in ("A1", "A2", "B1", "B2", "C1") else default_seviye

                if not ar or not tr:
                    _record_skipped_line(idx, "Arapça veya Türkçe cümle boş")
                    continue
                c.execute(
                    "INSERT OR REPLACE INTO cumleler (id, paket_id, arapca, turkce, ses, is_fav, is_learned, dogru, yanlis, seviye) VALUES (?, ?, ?, ?, NULL, 0, 0, 0, 0, ?)",
                    (sid, paket_id, ar, tr, lvl)
                )
                added += 1

    else:
        did = paket_id if paket_id.startswith("dlg_") else f"dlg_{paket_id}"
        with db_session(DIALOGUES_DB_PATH) as conn:
            c = conn.cursor()
            c.execute("INSERT OR IGNORE INTO paketler (id, ad, seviye, konu) VALUES ('muhadesat_paketi', 'Muhâdesât (Günlük Diyaloglar)', ?, 'Genel')", (default_seviye,))
            c.execute("INSERT OR IGNORE INTO diyaloglar (id, paket_id, baslik_ar, baslik_tr, seviye, konu) VALUES (?, 'muhadesat_paketi', '', ?, ?, 'Genel')", (did, paket_adi, default_seviye))
            for idx, row in enumerate(rows, 1):
                cells = [cl.strip() for cl in row]
                if not any(cells):
                    continue
                if idx == 1 and any(k in cells[0].lower() for k in ("konusmaci", "sira", "arapca")):
                    continue
                if len(cells) < 2:
                    _record_skipped_line(idx, "En az 2 sütun (Arapça ve Türkçe) olmalı")
                    continue
                if len(cells) == 2:
                    spk, cins = "Kişi", "E"
                    ar, tr = sanitize_arabic_sentence(cells[0]), sanitize_turkish_sentence(cells[1])
                elif len(cells) == 3:
                    spk, cins = cells[0] or "Kişi", "E"
                    ar, tr = sanitize_arabic_sentence(cells[1]), sanitize_turkish_sentence(cells[2])
                else:
                    spk = cells[0] or "Kişi"
                    cins = cells[1].upper() if cells[1].upper() in ("E", "K") else "E"
                    ar, tr = sanitize_arabic_sentence(cells[2]), sanitize_turkish_sentence(cells[3])
                if not ar:
                    _record_skipped_line(idx, "Arapça cümle boş")
                    continue
                c.execute("""
                    INSERT INTO diyalog_satirlari (diyalog_id, sira, konusmaci, konusmaci_ar, cinsiyet, arapca, turkce, ses, dogru, yanlis)
                    VALUES (?, ?, ?, '', ?, ?, ?, NULL, 0, 0)
                """, (did, idx, spk, cins, ar, tr))
                added += 1

    _LAST_IMPORT_REPORT["added"] = added
    return get_last_import_report()


def bulk_match_media_folder(mode: str, folder_path: str, paket_id: str = "all") -> Dict[str, int]:
    """
    Öğretmenin seçtiği klasördeki görsel (.png, .jpg, .jpeg, .webp) ve ses (.mp3, .wav) dosyalarını
    veritabanındaki kayıtlarla ID veya Türkçe kelime adına göre ('Arı' -> 'ari.jpg') otomatik eşleştirir.
    """
    stats = {"scanned_files": 0, "matched_images": 0, "matched_audio": 0}
    if not folder_path or not os.path.isdir(folder_path):
        return stats

    img_exts = {".png", ".jpg", ".jpeg", ".webp"}
    aud_exts = {".mp3", ".wav"}

    image_map: Dict[str, str] = {}
    audio_map: Dict[str, str] = {}

    for root_d, _, files in os.walk(folder_path):
        for fn in files:
            ext = os.path.splitext(fn)[1].lower()
            stem = os.path.splitext(fn)[0].strip()
            if ext not in img_exts and ext not in aud_exts:
                continue
            stats["scanned_files"] += 1
            full_p = os.path.join(root_d, fn)
            key_exact = stem.lower()
            key_slug = _slugify_tr(stem)
            # '_f' veya '_m' ile biten ses dosyalarını ana kelimeyle de eşleştir ('ari_f.mp3' -> 'ari')
            key_base = re.sub(r"_[fm]$", "", key_slug)

            if ext in img_exts:
                for k in (key_exact, key_slug):
                    if k and k not in image_map:
                        image_map[k] = full_p
            elif ext in aud_exts:
                for k in (key_exact, key_slug, key_base):
                    if k and k not in audio_map:
                        audio_map[k] = full_p

    if mode == "words":
        items = get_words(None if paket_id == "all" else paket_id)
        with db_session(WORDS_DB_PATH) as conn:
            c = conn.cursor()
            for w in items:
                tr_first = w.turkce.split(",")[0].split("/")[0].split("(")[0].strip()
                cand_keys = [
                    str(w.id).lower(),
                    _slugify_tr(str(w.id)),
                    w.turkce.lower().strip(),
                    _slugify_tr(w.turkce),
                    _slugify_tr(tr_first)
                ]
                pkg_folder = w.paket_id if w.paket_id and not w.paket_id.startswith("szl_") else "genel"
                cat_img_dir = os.path.join(CATEGORIES_DIR, pkg_folder, "images")
                cat_snd_dir = os.path.join(CATEGORIES_DIR, pkg_folder, "sounds")
                os.makedirs(cat_img_dir, exist_ok=True)
                os.makedirs(cat_snd_dir, exist_ok=True)

                matched_g = next((image_map[k] for k in cand_keys if k in image_map), None)
                if matched_g:
                    dst_fn = os.path.basename(matched_g)
                    shutil.copy2(matched_g, os.path.join(cat_img_dir, dst_fn))
                    rel_g = f"categories/{pkg_folder}/images/{dst_fn}"
                    c.execute("UPDATE kelimeler SET gorsel=? WHERE id=?", (rel_g, w.id))
                    stats["matched_images"] += 1

                matched_s = next((audio_map[k] for k in cand_keys if k in audio_map), None)
                if matched_s:
                    dst_fn = os.path.basename(matched_s)
                    shutil.copy2(matched_s, os.path.join(cat_snd_dir, dst_fn))
                    if "_f." in matched_s:
                        m_cand = matched_s.replace("_f.", "_m.")
                        if os.path.exists(m_cand):
                            shutil.copy2(m_cand, os.path.join(cat_snd_dir, os.path.basename(m_cand)))
                    rel_s = f"categories/{pkg_folder}/sounds/{dst_fn}"
                    c.execute("UPDATE kelimeler SET ses=? WHERE id=?", (rel_s, w.id))
                    stats["matched_audio"] += 1

    elif mode == "sentences":
        sents = get_key_sentences("all", paket_id=paket_id)
        with db_session(SENTENCES_DB_PATH) as conn:
            c = conn.cursor()
            for s in sents:
                cand_keys = [str(s.id).lower(), _slugify_tr(str(s.id)), _slugify_tr(s.turkce or "")]
                matched_s = next((audio_map[k] for k in cand_keys if k and k in audio_map), None)
                if matched_s:
                    dst_fn = f"{s.id}_{os.path.basename(matched_s)}"
                    shutil.copy2(matched_s, os.path.join(CUSTOM_AUDIO_DIR, dst_fn))
                    c.execute("UPDATE cumleler SET ses=? WHERE id=?", (f"custom_audio/{dst_fn}", s.id))
                    stats["matched_audio"] += 1

    else:
        lines = get_dialogue_lines_with_titles(paket_id=paket_id)
        with db_session(DIALOGUES_DB_PATH) as conn:
            c = conn.cursor()
            for lid, did, _, sira, _, _, _, tr, _, _ in lines:
                cand_keys = [str(lid), f"{did}_{sira}".lower(), _slugify_tr(tr or "")]
                matched_s = next((audio_map[k] for k in cand_keys if k and k in audio_map), None)
                if matched_s:
                    dst_fn = f"{did}_{sira}_{os.path.basename(matched_s)}"
                    shutil.copy2(matched_s, os.path.join(CUSTOM_AUDIO_DIR, dst_fn))
                    c.execute("UPDATE diyalog_satirlari SET ses=? WHERE id=?", (f"custom_audio/{dst_fn}", int(lid)))
                    stats["matched_audio"] += 1

    return stats


# ==============================================================================
# 10. İSTATİSTİK, SM-2 GÜNCELLEME, STREAK (SERİ), ROZET VE RAPORLAMA MOTORU
# ==============================================================================
def update_db(item_id: str, mode: str, is_correct: bool) -> None:
    col = "dogru" if is_correct else "yanlis"
    today_str = datetime.date.today().isoformat()

    if mode == "words":
        with db_session(WORDS_DB_PATH) as conn:
            row = conn.execute(
                "SELECT COALESCE(interval_days,0), COALESCE(ease_factor,2.5) FROM kelimeler WHERE id=?",
                (item_id,)
            ).fetchone()
            cur_iv, cur_ef = row if row else (0, 2.5)
            new_iv, new_ef, next_rev = _compute_sm2_schedule(cur_iv, cur_ef, is_correct)
            if is_correct:
                conn.execute(
                    f"UPDATE kelimeler SET {col} = {col} + 1, is_learned = 1, last_practiced = ?, interval_days = ?, ease_factor = ?, next_review = ? WHERE id=?",
                    (today_str, new_iv, new_ef, next_rev, item_id)
                )
            else:
                conn.execute(
                    f"UPDATE kelimeler SET {col} = {col} + 1, last_practiced = ?, interval_days = ?, ease_factor = ?, next_review = ? WHERE id=?",
                    (today_str, new_iv, new_ef, next_rev, item_id)
                )
    elif mode == "sentences":
        with db_session(SENTENCES_DB_PATH) as conn:
            row = conn.execute(
                "SELECT COALESCE(interval_days,0), COALESCE(ease_factor,2.5) FROM cumleler WHERE id=?",
                (item_id,)
            ).fetchone()
            cur_iv, cur_ef = row if row else (0, 2.5)
            new_iv, new_ef, next_rev = _compute_sm2_schedule(cur_iv, cur_ef, is_correct)
            if is_correct:
                conn.execute(
                    f"UPDATE cumleler SET {col} = {col} + 1, is_learned = 1, last_practiced = ?, interval_days = ?, ease_factor = ?, next_review = ? WHERE id=?",
                    (today_str, new_iv, new_ef, next_rev, item_id)
                )
            else:
                conn.execute(
                    f"UPDATE cumleler SET {col} = {col} + 1, last_practiced = ?, interval_days = ?, ease_factor = ?, next_review = ? WHERE id=?",
                    (today_str, new_iv, new_ef, next_rev, item_id)
                )
    elif mode == "dialogue":
        line_id = str(item_id).replace("dlgline_", "")
        if line_id.isdigit():
            with db_session(DIALOGUES_DB_PATH) as conn:
                conn.execute(f"UPDATE diyalog_satirlari SET {col} = {col} + 1 WHERE id=?", (int(line_id),))

    record_daily_activity(is_correct)


def record_daily_activity(is_correct: bool = True) -> None:
    today_str = datetime.date.today().isoformat()
    last_date = get_config_val("last_active_date", "")
    streak = int(get_config_val("streak_days", "0") or 0)
    daily_cnt = int(get_config_val("daily_count", "0") or 0)
    total_pts = int(get_config_val("total_points", "0") or 0)

    if last_date == today_str:
        daily_cnt += 1
    else:
        try:
            prev_d = datetime.date.fromisoformat(last_date) if last_date else None
        except ValueError:
            prev_d = None
        if prev_d and (datetime.date.today() - prev_d).days == 1:
            streak += 1
        else:
            streak = 1
        daily_cnt = 1
        set_config_val("last_active_date", today_str)
        set_config_val("streak_days", streak)

    total_pts += (10 if is_correct else 3)
    set_config_val("daily_count", daily_cnt)
    set_config_val("total_points", total_pts)


def get_streak_and_badges() -> dict:
    today_str = datetime.date.today().isoformat()
    last_date = get_config_val("last_active_date", "")
    streak = int(get_config_val("streak_days", "0") or 0)
    daily_cnt = int(get_config_val("daily_count", "0") or 0) if last_date == today_str else 0
    daily_goal = int(get_config_val("daily_goal", "20") or 20)
    total_pts = int(get_config_val("total_points", "0") or 0)

    if total_pts >= 2500:
        level_title = "💎 Elmas Üstad (Seviye 5)"
    elif total_pts >= 1200:
        level_title = "🥇 Altın Muallim (Seviye 4)"
    elif total_pts >= 500:
        level_title = "🥈 Gümüş Hatip (Seviye 3)"
    elif total_pts >= 150:
        level_title = "🥉 Bronz Konuşmacı (Seviye 2)"
    else:
        level_title = "🌱 Başlangıç Talebesi (Seviye 1)"

    w_corr, _ = get_stats("words")
    d_corr, _ = get_stats("dialogue")
    s_corr, _ = get_stats("sentences")

    badges = [
        ("🔥 İlk Kıvılcım", "İlk sesli telaffuz pratiğini tamamla", (w_corr + d_corr + s_corr) >= 1),
        ("🎯 Kelime Avcısı", "En az 25 kelimeyi doğru telaffuz et", w_corr >= 25),
        ("💬 Muhâdese Yıldızı", "Diyaloglarda 30 cümleyi doğru seslendir", d_corr >= 30),
        ("📜 Cümle Üstadı", "Anahtar cümlelerde 50 doğru telaffuza ulaş", s_corr >= 50),
        ("⚡ İstikrar Abidesi", "3 gün üst üste çalışma serisi (Streak) yap", streak >= 3),
        ("👑 Arapça Hatibi", "Toplam 1000 başarı puanını geç", total_pts >= 1000),
    ]

    return {
        "streak": streak,
        "daily_count": daily_cnt,
        "daily_goal": daily_goal,
        "total_points": total_pts,
        "level_title": level_title,
        "badges": badges
    }


def get_package_completion_info(mode: str, paket_id: Optional[str] = None) -> dict:
    if mode == "words":
        with db_session(WORDS_DB_PATH) as conn:
            if paket_id == "sozluk_tumu":
                total = conn.execute("SELECT COUNT(*) FROM kelimeler WHERE paket_id LIKE 'szl_%'").fetchone()[0]
                learned = conn.execute("SELECT COUNT(*) FROM kelimeler WHERE paket_id LIKE 'szl_%' AND (COALESCE(is_learned,0)=1 OR dogru > 0)").fetchone()[0]
            elif paket_id and paket_id not in ("all", "zayif", "tekrar", "ogrenilen", "gunluk_tekrar"):
                total = conn.execute("SELECT COUNT(*) FROM kelimeler WHERE paket_id=?", (paket_id,)).fetchone()[0]
                learned = conn.execute("SELECT COUNT(*) FROM kelimeler WHERE paket_id=? AND (COALESCE(is_learned,0)=1 OR dogru > 0)", (paket_id,)).fetchone()[0]
            else:
                total = conn.execute("SELECT COUNT(*) FROM kelimeler").fetchone()[0]
                learned = conn.execute("SELECT COUNT(*) FROM kelimeler WHERE COALESCE(is_learned,0)=1 OR dogru > 0").fetchone()[0]
    elif mode == "sentences":
        with db_session(SENTENCES_DB_PATH) as conn:
            total = conn.execute("SELECT COUNT(*) FROM cumleler").fetchone()[0]
            learned = conn.execute("SELECT COUNT(*) FROM cumleler WHERE COALESCE(is_learned,0)=1 OR dogru > 0").fetchone()[0]
    else:
        with db_session(DIALOGUES_DB_PATH) as conn:
            if paket_id:
                total = conn.execute("SELECT COUNT(*) FROM diyalog_satirlari WHERE diyalog_id=?", (paket_id,)).fetchone()[0]
                learned = conn.execute("SELECT COUNT(*) FROM diyalog_satirlari WHERE diyalog_id=? AND dogru > 0", (paket_id,)).fetchone()[0]
            else:
                total = conn.execute("SELECT COUNT(*) FROM diyalog_satirlari").fetchone()[0]
                learned = conn.execute("SELECT COUNT(*) FROM diyalog_satirlari WHERE dogru > 0").fetchone()[0]

    pct = int((learned / total) * 100) if total > 0 else 0
    return {"total": total, "learned": learned, "pct": pct}


def get_detailed_analytics_report() -> dict:
    report = {"word_packages": [], "dialogues": [], "sentence_packages": [], "most_difficult": []}

    with db_session(WORDS_DB_PATH) as conn_w:
        for pid, pad in conn_w.execute("SELECT id, ad FROM paketler ORDER BY rowid").fetchall():
            if pid == "sozluk_tumu":
                row = conn_w.execute("""
                    SELECT COUNT(*),
                           SUM(CASE WHEN COALESCE(is_learned,0)=1 OR dogru > 0 THEN 1 ELSE 0 END),
                           COALESCE(SUM(dogru), 0),
                           COALESCE(SUM(yanlis), 0)
                    FROM kelimeler WHERE paket_id LIKE 'szl_%'
                """).fetchone()
            else:
                row = conn_w.execute("""
                    SELECT COUNT(*),
                           SUM(CASE WHEN COALESCE(is_learned,0)=1 OR dogru > 0 THEN 1 ELSE 0 END),
                           COALESCE(SUM(dogru), 0),
                           COALESCE(SUM(yanlis), 0)
                    FROM kelimeler WHERE paket_id=?
                """, (pid,)).fetchone()
            tot, lrn, corr, wrg = row if row else (0, 0, 0, 0)
            acc = int((corr / (corr + wrg)) * 100) if (corr + wrg) > 0 else 0
            comp = int(((lrn or 0) / tot) * 100) if tot > 0 else 0
            report["word_packages"].append((pad, tot, lrn or 0, corr, wrg, acc, comp))

        for wid, ar, tr, corr, wrg in conn_w.execute(
            "SELECT id, arapca, turkce, dogru, yanlis FROM kelimeler WHERE yanlis > 0 ORDER BY yanlis DESC LIMIT 30"
        ).fetchall():
            report["most_difficult"].append(("Kelime", wid, tr, ar, corr, wrg))

    with db_session(DIALOGUES_DB_PATH) as conn_d:
        for did, _, b_ar, b_tr, d_sev in conn_d.execute("SELECT id, paket_id, baslik_ar, baslik_tr, COALESCE(seviye,'A1') FROM diyaloglar ORDER BY rowid").fetchall():
            row = conn_d.execute("""
                SELECT COUNT(*),
                       SUM(CASE WHEN dogru > 0 THEN 1 ELSE 0 END),
                       COALESCE(SUM(dogru), 0),
                       COALESCE(SUM(yanlis), 0)
                FROM diyalog_satirlari WHERE diyalog_id=?
            """, (did,)).fetchone()
            tot, lrn, corr, wrg = row if row else (0, 0, 0, 0)
            if corr > 0 or wrg > 0 or tot > 0:
                acc = int((corr / (corr + wrg)) * 100) if (corr + wrg) > 0 else 0
                comp = int(((lrn or 0) / tot) * 100) if tot > 0 else 0
                report["dialogues"].append((f"[{did} | {d_sev}] {b_tr}", tot, lrn or 0, corr, wrg, acc, comp))

        for lid, spk, ar, tr, corr, wrg in conn_d.execute(
            "SELECT id, konusmaci, arapca, turkce, dogru, yanlis FROM diyalog_satirlari WHERE yanlis > 0 ORDER BY yanlis DESC LIMIT 30"
        ).fetchall():
            report["most_difficult"].append((f"Diyalog ({spk})", str(lid), tr, ar, corr, wrg))

    with db_session(SENTENCES_DB_PATH) as conn_s:
        for pid, pad in conn_s.execute("SELECT id, ad FROM paketler ORDER BY rowid").fetchall():
            row = conn_s.execute("""
                SELECT COUNT(*),
                       SUM(CASE WHEN COALESCE(is_learned,0)=1 OR dogru > 0 THEN 1 ELSE 0 END),
                       COALESCE(SUM(dogru), 0),
                       COALESCE(SUM(yanlis), 0)
                FROM cumleler WHERE paket_id=?
            """, (pid,)).fetchone()
            tot, lrn, corr, wrg = row if row else (0, 0, 0, 0)
            acc = int((corr / (corr + wrg)) * 100) if (corr + wrg) > 0 else 0
            comp = int(((lrn or 0) / tot) * 100) if tot > 0 else 0
            report["sentence_packages"].append((pad, tot, lrn or 0, corr, wrg, acc, comp))

        for sid, ar, tr, corr, wrg in conn_s.execute(
            "SELECT id, arapca, turkce, dogru, yanlis FROM cumleler WHERE yanlis > 0 ORDER BY yanlis DESC LIMIT 30"
        ).fetchall():
            report["most_difficult"].append(("Cümle", sid, tr, ar, corr, wrg))

    report["most_difficult"].sort(key=lambda x: x[5], reverse=True)
    return report


def export_analytics_to_csv(csv_path: str) -> None:
    rep = get_detailed_analytics_report()
    student_name = get_config_val("student_name", "Belirtilmedi") or "Belirtilmedi"
    b_info = get_streak_and_badges()
    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f, delimiter=";")
        writer.writerow(["OGRENCI ADI SOYADI", student_name, "SEVIYE UNVANI", b_info["level_title"], "TOPLAM PUAN", b_info["total_points"]])
        writer.writerow([])
        writer.writerow(["BOLUM", "PAKET / DIYALOG ADI", "TOPLAM KAYIT", "TAMAMLANAN", "DOGRU SAYISI", "YANLIS SAYISI", "BASARI YUZDESI (%)", "TAMAMLANMA (%)"])
        for name, tot, lrn, corr, wrg, acc, comp in rep["word_packages"]:
            writer.writerow(["Kelime Paketi", name, tot, lrn, corr, wrg, f"%{acc}", f"%{comp}"])
        for name, tot, lrn, corr, wrg, acc, comp in rep["sentence_packages"]:
            writer.writerow(["Cumle Paketi", name, tot, lrn, corr, wrg, f"%{acc}", f"%{comp}"])
        for name, tot, lrn, corr, wrg, acc, comp in rep["dialogues"]:
            writer.writerow(["Diyalog", name, tot, lrn, corr, wrg, f"%{acc}", f"%{comp}"])

        writer.writerow([])
        writer.writerow(["EN COK HATA YAPILAN KAYITLAR"])
        writer.writerow(["TUR", "ID", "TURKCE ANLAMI", "ARAPCA METIN", "DOGRU", "YANLIS"])
        for kind, iid, tr, ar, corr, wrg in rep["most_difficult"]:
            writer.writerow([kind, iid, tr, ar, corr, wrg])


def reset_stats(mode: str) -> None:
    if mode == "words":
        with db_session(WORDS_DB_PATH) as conn:
            conn.execute("UPDATE kelimeler SET dogru=0, yanlis=0, is_learned=0, interval_days=0, ease_factor=2.5, next_review=''")
    elif mode == "sentences":
        with db_session(SENTENCES_DB_PATH) as conn:
            conn.execute("UPDATE cumleler SET dogru=0, yanlis=0, is_learned=0, interval_days=0, ease_factor=2.5, next_review=''")
    else:
        with db_session(DIALOGUES_DB_PATH) as conn:
            conn.execute("UPDATE diyalog_satirlari SET dogru=0, yanlis=0")


def get_stats(mode: str) -> Tuple[int, int]:
    if mode == "words":
        with db_session(WORDS_DB_PATH) as conn:
            row = conn.execute("SELECT SUM(dogru), SUM(yanlis) FROM kelimeler").fetchone()
    elif mode == "sentences":
        with db_session(SENTENCES_DB_PATH) as conn:
            row = conn.execute("SELECT SUM(dogru), SUM(yanlis) FROM cumleler").fetchone()
    else:
        with db_session(DIALOGUES_DB_PATH) as conn:
            row = conn.execute("SELECT SUM(dogru), SUM(yanlis) FROM diyalog_satirlari").fetchone()
    return (row[0] if row and row[0] else 0), (row[1] if row and row[1] else 0)


def get_config_val(key: str, default: Optional[str] = None) -> Optional[str]:
    with db_session(SETTINGS_DB_PATH) as conn:
        row = conn.execute("SELECT value FROM config WHERE key=?", (key,)).fetchone()
    return row[0] if row else default


def set_config_val(key: str, val) -> None:
    with db_session(SETTINGS_DB_PATH) as conn:
        conn.execute("INSERT OR REPLACE INTO config VALUES (?, ?)", (key, str(val)))