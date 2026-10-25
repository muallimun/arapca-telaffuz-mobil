# -*- coding: utf-8 -*-
"""
core/dictionary.py — Arapça Sarf (Morfoloji) Sözlüğü Motoru (sozluk.db & sozluk.json).
- 1.500+ sözlük kelimesini otomatik tematik kategorilere ve emojilere ayırır.
- Sülâsî kök (ك ت ب), Türev Ağacı (Müfret/Cemî & Mâzî/Muzâri/Masdar) ve Kök Ailesi sorgularını yönetir.
- Kelime Modu (kelimeler.db) ile çift yönlü senkronize çalışır.
"""

import os
import json
import sqlite3
from contextlib import contextmanager
from typing import List, Dict, Tuple, Optional

from core.config import BASE_DIR, DATA_DIR, WORDS_DB_PATH, log_error
from core.arabic_nlp import (
    sanitize_arabic_sentence, sanitize_turkish_sentence,
    normalize_arabic, strip_al, estimate_word_cefr, expand_arabic_variants
)

SOZLUK_DB_PATH = os.path.join(DATA_DIR, "sozluk.db")
DEFAULT_SOZLUK_JSON_PATH = os.path.join(DATA_DIR, "sozluk.json")
if not os.path.exists(DEFAULT_SOZLUK_JSON_PATH):
    _alt_szl = os.path.join(BASE_DIR, "sozluk.json")
    if os.path.exists(_alt_szl):
        DEFAULT_SOZLUK_JSON_PATH = _alt_szl

# ==============================================================================
# 1. OTOMATİK TEMATİK KATEGORİ VE EMOJİ SINIFLANDIRMA KURALLARI
# ==============================================================================
THEMATIC_RULES: List[Tuple[str, str, str, Tuple[str, ...]]] = [
    (
        "szl_saglik", "🏥 Sağlık & Vücut", "🏥",
        ("doktor", "hekim", "hasta", "ilaç", "ağrı", "muayene", "eczane", "eczacı", "reçete",
         "şifa", "tedavi", "ameliyat", "cerrah", "yara", "kan", "tansiyon", "ateş", "iğne",
         "hap", "şurup", "aşı", "klinik", "hemşire", "burun", "boğaz", "baş", "kafa", "ağız",
         "diş", "göz", "kulak", "dil", "گöğüs", "göğüs", "sırt", "akciğer", "mide", "karın",
         "kalp", "el", "ayak", "bacak", "kol", "omuz", "parmak", "kemik", "cilt", "deri",
         "saç", "yüz", "vücut", "beden", "organ", "nefes", "solunum", "شيش", "şiş", "hapşır",
         "öksür", "hazımsız", "iştah", "sağlık")
    ),
    (
        "szl_yemek", "🍽️ Yemek & Mutfak", "🍽️",
        ("yemek", "yiyecek", "içecek", "lokanta", "restoran", "garson", "menü", "et", "tavuk",
         "balık", "ekmek", "fırın", "hamur", "yufka", "süt", "muhallebi", "peynir", "tereyağı",
         "zeytin", "yumurta", "meyve", "sebze", "çay", "kahve", "شeker", "şeker", "tuz", "biber",
         "baharat", "tatlı", "şerbet", "pirinç", "pilav", "çorba", "kebap", "döner", "baklava",
         "tabak", "bardak", "kaşık", "çatal", "bıçak", "tencere", "tava", "demlik", "peçete",
         "mutfak", "aşçı", "pişir", "lezzet", "tat", "تغذ", "beslen", "gıda", "içmek", "çiğne", "yutmak")
    ),
    (
        "szl_seyahat", "✈️ Seyahat & Ulaşım", "✈️",
        ("seyahat", "yolculuk", "yolcu", "otobüs", "uçak", "tren", "feribot", "bilet", "rezerv",
         "otel", "pansiyon", "havalimanı", "istasyon", "durak", "peron", "valiz", "pasaport",
         "vize", "체", "bagaj", "تذكرة", "kalkış", "varış", "ulaşım", "trafik", "otoyol", "köprü",
         "tünel", "kaldırım", "yaya", "kaza", "araba", "sürücü", "şoför", "ehliyet", "binmek",
         "inmek", "harita", "rehber", "tur", "gezi", "tatil", "vapur", "kıyı", "boğaz")
    ),
    (
        "szl_alisveris", "🛍️ Alışveriş & Giyim", "🛍️",
        ("çarşı", "pazar", "market", "alışveriş", "avm", "dükkan", "mağaza", "satın", "satmak",
         "satıcı", "müşteri", "fiyat", "bedel", "pahalı", "ucuz", "indirim", "iskonto", "pazarlık",
         "nakit", "kredi kartı", "kasa", "vezne", "fiş", "fatura", "kumaş", "elbise", "giysi",
         "kıyafet", "giymek", "kazak", "bluz", "ceket", "mont", "palto", "etek", "gömlek",
         "pantolon", "ayakkabı", "çorap", "şapka", "kemer", "gözlük", "kol saati", "yüzük",
         "kolye", "pamuk", "yün", "ipek", "beden", "kabin", "terzi", "dikmek", "berber", "kuaför",
         "kasap", "banka", "atm", "döviz", "havale", "borç", "taksit", "vade", "tasarruf", "maliyet")
    ),
    (
        "szl_ev", "🏠 Ev & Eşya", "🏠",
        ("ev", "konut", "daire", "bina", "apartman", "kat", "merdiven", "asansör", "tavan",
         "zemin", "duvar", "pencere", "kapı", "kilit", "anahtar", "balkon", "avlu", "mobilya",
         "eşya", "yatak", "dolap", "raf", "masa", "sandalye", "koltuk", "kanepe", "halı",
         "perde", "ayna", "lamba", "şofben", "ütü", "süpürge", "buzdolabı", "çamaşır", "bulaşık",
         "banyo", "lavabo", "temiz", "kirli", "kira", "kiracı", "ikamet", "oturmak", "komşu",
         "elektrik", "doğalgaz", "yakıt", "ısıtma", "تدفئة")
    ),
    (
        "szl_aile", "👨‍👩‍👧‍👦 Aile & İnsan", "👨‍👩‍👧‍👦",
        ("aile", "anne", "baba", "ebeveyn", "kardeş", "oğul", "kız", "çocuk", "bebek", "dede",
         "nine", "torun", "amca", "hala", "dayı", "teyze", "kuzen", "eş", "koca", "hanım",
         "evlen", "evlilik", "bekar", "gelin", "damat", "düğün", "akraba", "arkadaş", "dost",
         "genç", "yaşlı", "ihtiyar", "doğum", "ölüm", "vefat", "misafir", "konuk", "ziyaret")
    ),
    (
        "szl_egitim", "🎓 Eğitim & İlim", "🎓",
        ("ders", "okul", "öğretmen", "hoca", "öğrenci", "talebe", "üniversite", "fakülte",
         "bölüm", "sınav", "imtihan", "test", "sonuç", "başarı", "mezun", "ilim", "bilim",
         "öğren", "öğret", "eğitim", "ezber", "hafıza", "araştırma", "makale", "diploma",
         "sertifika", "kütüphane", "kitap", "defter", "kalem", "sayfa", "kağıt", "dil",
         "lisan", "arapça", "tercüme", "çeviri", "soru", "cevap", "açıklama", "izah", "not", "puan")
    ),
    (
        "szl_is", "💼 İş, Meslek & Yönetim", "💼",
        ("iş", "çalışma", "işçi", "memur", "görevli", "meslek", "görev", "mülakat", "maaş",
         "tecrübe", "deneyim", "uzman", "başvuru", "şirket", "firma", "ortak", "fabrika",
         "sanayi", "üretim", "ticaret", "tüccar", "müdür", "yönetici", "idare", "toplantı",
         "anlaşma", "sözleşme", "rapor", "imza", "form", "belge", "evrak", "ruhsat", "devlet",
         "hükümet", "bakan", "elçi", "konsolos", "belediye", "vatandaş", "polis", "itfaiye",
         "suç", "mahkeme", "hakim", "mesai")
    ),
    (
        "szl_teknoloji", "💻 Teknoloji & İletişim", "💻",
        ("telefon", "cep telefonu", "mobil", "mesaj", "mektup", "e-posta", "internet", "ağ",
         "bilgisayar", "laptop", "ekran", "klavye", "fare", "yazıcı", "web", "şifre", "parola",
         "uygulama", "dosya", "yayın", "radyo", "televizyon", "kanal", "gazete", "dergi",
         "haber", "bülten", "cihaz", "alet", "şarj", "arıza", "tamir")
    ),
    (
        "szl_sanat", "🎨 Sanat, Kültür & Spor", "🎨",
        ("sinema", "film", "tiyatro", "sahne", "oyuncu", "aktör", "sanat", "müzik", "şarkı",
         "enstrüman", "çalgı", "resim", "ressam", "tablo", "sergi", "fuar", "müze", "tarih",
         "kültür", "medeniyet", "miras", "gelenek", "fotoğraf", "kamera", "spor", "antrenman",
         "koç", "stadyum", "saha", "yüzme", "havuz", "koşu", "koşmak", "zıpla", "dans",
         "futbol", "basketbol", "voleybol", "satranç", "oyun", "yarış", "şampiyon", "zafer")
    ),
    (
        "szl_doga", "🌿 Doğa, Hava & Hayvanlar", "🌿",
        ("doğa", "tabiat", "hava", "iklim", "yağmur", "kar", "soğuk", "sıcak", "kış", "yaz",
         "sonbahar", "ilkbahar", "mevsim", "güneş", "ay", "yıldız", "gökyüzü", "toprak",
         "dünya", "gezegen", "orman", "çöl", "dağ", "tepe", "nehir", "ırmak", "göl", "deniz",
         "ada", "bahçe", "park", "hayvan", "maymun", "ördek", "timsah", "aslan", "köpek",
         "kedi", "kuş", "serçe", "at", "inek", "koyun", "kuzu", "kafes", "yüzgeç", "kanat",
         "renk", "beyaz", "siyah", "yeşil", "mavi", "sarı", "kırmızı", "kahverengi", "gri", "mor", "pembe")
    ),
    (
        "szl_zaman", "⏰ Zaman & Yönler", "⏰",
        ("gün", "hafta", "sene", "yıl", "zaman", "vakit", "saat", "dakika", "gece", "sabah",
         "akşam", "şafak", "dün", "yarın", "şimdi", "her zaman", "daima", "genellikle",
         "bazen", "nadiren", "asla", "hemen", "derhal", "sonunda", "öncelikle", "erken",
         "geç", "yakın", "uzak", "yön", "sağ", "sol", "ön", "arka", "üst", "alt", "orta",
         "مركز", "kuzey", "güney", "doğu", "batı", "içinde", "dışında", "karşısında", "arasında")
    ),
    (
        "szl_duygu", "💭 Duygu, Düşünce & İletişim", "💭",
        ("kaygı", "endişe", "huzur", "rahat", "hüzün", "keder", "üzül", "sevinç", "neşe",
         "mutlu", "öfke", "kızgın", "korku", "cesur", "cesaret", "utangaç", "şaşır", "sabır",
         "güven", "pişman", "umut", "ümit", "özlem", "hasret", "sevgi", "nefret", "duygu",
         "his", "fikir", "düşün", "görüş", "karar", "شüphe", "şüphe", "zan", "emin", "gayret",
         "çaba", "tembel", "yorgun", "uyku", "uyan", "unut", "hatırla", "anla", "konuş",
         "söyle", "سؤال", "teşekkür", "özür", "affet", "hoşgörü", "saygı", "tavsiye", "uyarı",
         "سöz", "vaat", "도ğru", "yalan", "گülme", "gülme")
    ),
]


def classify_dictionary_category_and_emoji(anlam_tr: str, tur: str = "isim") -> Tuple[str, str, str]:
    """
    Kelimenin Türkçe anlamından otomatik olarak (paket_id, kategori_adi, emoji) döndürür.
    """
    low = (anlam_tr or "").lower()
    for pkg_id, cat_name, emoji, keywords in THEMATIC_RULES:
        for kw in keywords:
            if kw in low:
                return pkg_id, cat_name, emoji

    if (tur or "").lower() == "fiil":
        return "szl_fiiller", "⚡ Genel Fiiller & Eylemler", "⚡"
    return "szl_genel", "📘 Genel Kavramlar & İsimler", "📘"


@contextmanager
def sozluk_session():
    conn = sqlite3.connect(SOZLUK_DB_PATH, timeout=15.0)
    try:
        yield conn
        conn.commit()
    except Exception as ex:
        conn.rollback()
        log_error("Sözlük DB hatası", ex)
        raise
    finally:
        conn.close()


def init_dictionary_db() -> None:
    """sozluk.db şemasını oluşturur; eğer boşsa ve sozluk.json mevcutsa otomatik içe aktarır."""
    with sozluk_session() as conn:
        c = conn.cursor()
        c.execute("""
            CREATE TABLE IF NOT EXISTS sozluk (
                id TEXT PRIMARY KEY,
                tur TEXT NOT NULL,
                kok TEXT NOT NULL,
                mufret TEXT DEFAULT '',
                cemi TEXT DEFAULT '',
                mazi TEXT DEFAULT '',
                muzari TEXT DEFAULT '',
                masdar TEXT DEFAULT '',
                anlam_tr TEXT NOT NULL,
                kategori_id TEXT DEFAULT 'szl_genel',
                kategori TEXT DEFAULT '📘 Genel Kavramlar & İsimler',
                emoji TEXT DEFAULT '📘',
                seviye TEXT DEFAULT 'A1',
                word_id TEXT DEFAULT ''
            )
        """)
        c.execute("CREATE INDEX IF NOT EXISTS idx_szl_kok ON sozluk(kok)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_szl_tur ON sozluk(tur)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_szl_kat ON sozluk(kategori_id)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_szl_wid ON sozluk(word_id)")
        cnt = c.execute("SELECT COUNT(*) FROM sozluk").fetchone()[0]

    if cnt == 0 and os.path.exists(DEFAULT_SOZLUK_JSON_PATH):
        try:
            import_sozluk_json(DEFAULT_SOZLUK_JSON_PATH, clear_existing=False)
        except Exception as ex:
            log_error("Varsayılan sozluk.json içe aktarma hatası", ex)


def import_sozluk_json(json_path: str, clear_existing: bool = False) -> Dict[str, int]:
    """
    sozluk.json dosyasını:
    1) Yapısal olarak data/sozluk.db içine kaydeder,
    2) Otomatik tematik kategorilere ve emojilere ayırır,
    3) Mükerrer (aynı Arapça yazılışa sahip) kelimeleri tekilleştirerek kelimeler.db içine
       tematik sözlük paketleri olarak aktarır.
    """
    if not os.path.exists(json_path):
        raise FileNotFoundError(f"Sözlük dosyası bulunamadı: {json_path}")

    with open(json_path, "r", encoding="utf-8-sig") as f:
        data = json.load(f)

    if not isinstance(data, list):
        raise ValueError("sozluk.json formatı bir JSON listesi ([...]) olmalıdır.")

    init_dictionary_db()
    sozluk_count = 0
    unique_words_count = 0
    categories_created = set()
    seen_norm_keys: Dict[str, str] = {}

    conn_w = sqlite3.connect(WORDS_DB_PATH, timeout=15.0)
    try:
        c_w = conn_w.cursor()
        with sozluk_session() as conn_s:
            c_s = conn_s.cursor()
            if clear_existing:
                c_s.execute("DELETE FROM sozluk")
                c_w.execute("DELETE FROM kelimeler WHERE paket_id LIKE 'szl_%'")
                c_w.execute("DELETE FROM paketler WHERE id LIKE 'szl_%'")

            # Ana kapsayıcı sanal paket başlığını kaydet
            c_w.execute(
                "INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, ?)",
                ("sozluk_tumu", "📖 Sarf Sözlüğü (Tüm Kelimeler)", "A2", "Sarf Sözlüğü")
            )

            for idx, entry in enumerate(data, 1):
                sid = str(entry.get("id") or f"kelime_{idx:04d}").strip()
                tur = str(entry.get("tur") or "isim").strip().lower()
                kok = sanitize_arabic_sentence(entry.get("kok") or "")
                mufret = sanitize_arabic_sentence(entry.get("mufret") or "")
                cemi = sanitize_arabic_sentence(entry.get("cemi") or "")
                mazi = sanitize_arabic_sentence(entry.get("mazi") or "")
                muzari = sanitize_arabic_sentence(entry.get("muzari") or "")
                masdar = sanitize_arabic_sentence(entry.get("masdar") or "")
                anlam_tr = sanitize_turkish_sentence(entry.get("anlam_tr") or "")

                primary_ar = mufret if tur == "isim" else mazi
                if not primary_ar or not anlam_tr:
                    continue

                kat_id, kat_name, emoji = classify_dictionary_category_and_emoji(anlam_tr, tur)
                if entry.get("kategori"):
                    kat_name = str(entry["kategori"]).strip()
                if entry.get("emoji"):
                    emoji = str(entry["emoji"]).strip()

                seviye = entry.get("seviye") or estimate_word_cefr(primary_ar)
                norm_key = f"{tur}:{strip_al(normalize_arabic(expand_arabic_variants(primary_ar)[0]))}"

                # Aynı kelimenin mükerrer kart oluşturmasını önle
                if norm_key in seen_norm_keys:
                    word_id = seen_norm_keys[norm_key]
                else:
                    word_id = f"szl_{sid}"
                    seen_norm_keys[norm_key] = word_id

                    if kat_id not in categories_created:
                        categories_created.add(kat_id)
                        c_w.execute(
                            "INSERT OR REPLACE INTO paketler (id, ad, seviye, konu) VALUES (?, ?, ?, ?)",
                            (kat_id, f"📖 Sözlük: {kat_name}", "A2", kat_name)
                        )

                    # Mevcut çalışma istatistiğini koruyarak ekle/güncelle
                    existing_w = c_w.execute(
                        "SELECT dogru, yanlis, is_fav, is_learned, interval_days, ease_factor, next_review, ses FROM kelimeler WHERE id=?",
                        (word_id,)
                    ).fetchone()
                    if existing_w:
                        c_w.execute(
                            "UPDATE kelimeler SET paket_id=?, arapca=?, turkce=?, seviye=? WHERE id=?",
                            (kat_id, primary_ar, anlam_tr, seviye, word_id)
                        )
                    else:
                        c_w.execute("""
                            INSERT INTO kelimeler
                            (id, paket_id, arapca, turkce, gorsel, ses, dogru, yanlis, is_fav, is_learned, seviye)
                            VALUES (?, ?, ?, ?, NULL, NULL, 0, 0, 0, 0, ?)
                        """, (word_id, kat_id, primary_ar, anlam_tr, seviye))
                    unique_words_count += 1

                c_s.execute("""
                    INSERT OR REPLACE INTO sozluk
                    (id, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kategori_id, kategori, emoji, seviye, word_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (sid, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kat_id, kat_name, emoji, seviye, word_id))
                sozluk_count += 1

        conn_w.commit()
    finally:
        conn_w.close()

    return {
        "sozluk_entries": sozluk_count,
        "unique_word_cards": unique_words_count,
        "categories": len(categories_created)
    }


# ==============================================================================
# 2. MORFOLOJİ (SARF), KÖK ROZETİ VE TÜREV AĞACI SORGULAMA MOTORU
# ==============================================================================
def get_word_morphology_and_family(word_id_or_arabic: str) -> Optional[Dict]:
    """
    Kelime kartında görsel olmadığında 'Altıgen Kök Rozeti + Türev Ağacı + Kök Ailesi'
    çizmek için kelimenin sarf bilgilerini ve aynı kökten türeyen diğer kelimeleri döndürür.
    """
    if not word_id_or_arabic:
        return None
    init_dictionary_db()

    with sozluk_session() as conn:
        c = conn.cursor()
        row = c.execute(
            "SELECT id, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kategori, emoji, seviye, word_id "
            "FROM sozluk WHERE word_id=? OR id=? LIMIT 1",
            (str(word_id_or_arabic), str(word_id_or_arabic).replace("szl_", ""))
        ).fetchone()

        if not row:
            # Arapça metin üzerinden eşleştir
            norm_q = strip_al(normalize_arabic(expand_arabic_variants(str(word_id_or_arabic))[0]))
            if norm_q:
                all_rows = c.execute(
                    "SELECT id, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kategori, emoji, seviye, word_id FROM sozluk"
                ).fetchall()
                for r in all_rows:
                    for field_val in (r[3], r[4], r[5], r[6], r[7]):
                        if field_val and strip_al(normalize_arabic(expand_arabic_variants(field_val)[0])) == norm_q:
                            row = r
                            break
                    if row:
                        break

        if not row:
            return None

        sid, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kategori, emoji, seviye, wid = row

        # Tıklanabilir Sarf Formları (Türev Ağacı)
        forms: List[Dict[str, str]] = []
        if tur == "isim":
            if mufret and mufret != "-":
                forms.append({"label": "Müfret (Tekil)", "arabic": mufret, "role": "mufret", "color": "#38bdf8"})
            if cemi and cemi != "-":
                forms.append({"label": "Cemî (Çoğul)", "arabic": cemi, "role": "cemi", "color": "#a78bfa"})
        else:
            if mazi and mazi != "-":
                forms.append({"label": "Mâzî (Geçmiş)", "arabic": mazi, "role": "mazi", "color": "#f87171"})
            if muzari and muzari != "-":
                forms.append({"label": "Muzâri (Geniş/Şimdiki)", "arabic": muzari, "role": "muzari", "color": "#60a5fa"})
            if masdar and masdar != "-":
                forms.append({"label": "Masdar (İsim Fiil)", "arabic": masdar, "role": "masdar", "color": "#34d399"})

        # Aynı sülâsî kökten (kok) türeyen diğer akraba kelimeler (İştikak Ailesi)
        family: List[Dict[str, str]] = []
        if kok and kok != "-":
            fam_rows = c.execute(
                "SELECT id, tur, mufret, cemi, mazi, muzari, masdar, anlam_tr FROM sozluk WHERE kok=? AND id!=? LIMIT 8",
                (kok, sid)
            ).fetchall()
            seen_fam = set()
            for f_id, f_tur, f_muf, f_cem, f_maz, f_muz, f_mas, f_tr in fam_rows:
                head_ar = f_muf if f_tur == "isim" else f_maz
                if not head_ar or head_ar in seen_fam:
                    continue
                seen_fam.add(head_ar)
                sub_ar = (f" (ج: {f_cem})" if f_cem and f_cem != "-" else "") if f_tur == "isim" else (f" ({f_muz})" if f_muz else "")
                family.append({
                    "id": f_id,
                    "tur": f_tur,
                    "arabic": head_ar,
                    "display_ar": f"{head_ar}{sub_ar}",
                    "anlam_tr": f_tr
                })

        return {
            "id": sid,
            "word_id": wid,
            "tur": tur,
            "kok": kok or "—",
            "mufret": mufret,
            "cemi": cemi,
            "mazi": mazi,
            "muzari": muzari,
            "masdar": masdar,
            "anlam_tr": anlam_tr,
            "kategori": kategori,
            "emoji": emoji,
            "seviye": seviye,
            "forms": forms,
            "family": family
        }


def lookup_word_morphology(arabic_word: str) -> Optional[Dict]:
    res = get_word_morphology_and_family(arabic_word)
    if not res:
        return None
    return {
        "word": arabic_word,
        "normalized": strip_al(normalize_arabic(arabic_word)),
        "meaning": res["anlam_tr"],
        "morphology": res,
        "ready": True
    }


def search_dictionary_entries(query: str = "", tur_filter: str = "Tümü", kategori_filter: str = "Tümü") -> List[Dict]:
    """Kök harfleri ('ك ت ب'), Arapça kelime veya Türkçe anlam üzerinden sözlükte arama yapar."""
    init_dictionary_db()
    where_clauses = []
    params = []

    if tur_filter in ("isim", "fiil"):
        where_clauses.append("tur = ?")
        params.append(tur_filter)

    if kategori_filter and kategori_filter != "Tümü":
        where_clauses.append("kategori = ?")
        params.append(kategori_filter)

    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    with sozluk_session() as conn:
        rows = conn.execute(
            f"SELECT id, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kategori, emoji, seviye, word_id "
            f"FROM sozluk {where_sql} ORDER BY id",
            params
        ).fetchall()

    results = []
    q_clean = (query or "").strip().lower()
    q_norm_ar = strip_al(normalize_arabic(q_clean))
    q_spaced_root = " ".join(list(q_norm_ar.replace(" ", ""))) if q_norm_ar and len(q_norm_ar.replace(" ", "")) == 3 else ""

    for r in rows:
        sid, tur, kok, mufret, cemi, mazi, muzari, masdar, anlam_tr, kategori, emoji, seviye, wid = r
        if q_clean:
            haystack_tr = f"{sid} {anlam_tr} {kategori}".lower()
            haystack_ar = f"{kok} {mufret} {cemi} {mazi} {muzari} {masdar}"
            haystack_norm = normalize_arabic(haystack_ar)
            match_tr = q_clean in haystack_tr
            match_ar = (q_clean in haystack_ar) or (q_norm_ar and q_norm_ar in haystack_norm)
            match_root = bool(q_spaced_root and q_spaced_root in normalize_arabic(kok))
            if not (match_tr or match_ar or match_root):
                continue

        results.append({
            "id": sid, "tur": tur, "kok": kok,
            "mufret": mufret, "cemi": cemi,
            "mazi": mazi, "muzari": muzari, "masdar": masdar,
            "anlam_tr": anlam_tr, "kategori": kategori,
            "emoji": emoji, "seviye": seviye, "word_id": wid
        })
    return results