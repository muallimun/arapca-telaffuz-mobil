# -*- coding: utf-8 -*-
"""
core/arabic_nlp.py — Arapça RTL Hizalama, Kök Harf Korumalı Telaffuz Değerlendirici,
Ardışık Tekrar Tekilleştirici (نحلة نحلة -> نحلة), Evrensel Mahreç (Fonem) & Vezin/Med Koçu,
Çoklu Varyant (/) Eşleştirici, Pedagojik Hareke Rehberi ve Sarf Vezinli CEFR Tahmin Motoru (v38).
"""

import re
import difflib
import tkinter.font as tkfont
from typing import List, Tuple, Dict, Optional

# Sabit Eşik Değerleri
DEFAULT_WORD_SIMILARITY_THRESHOLD: float = 0.72
MAX_WIN32_SAFE_CODEPOINTS: int = 72
NATURAL_CLAUSE_BREAK_MIN_LEN: int = 25

# ==============================================================================
# 1. ARAPÇA MAHREÇ (FONEM) KARIŞIKLIK MATRİSİ VE PEDAGOJİK KOÇLUK REHBERİ
# ==============================================================================
MAKHRAJ_COACH_MAP: Dict[Tuple[str, str], Tuple[str, str]] = {
    # Boğaz (Halk) Harfleri
    ("ع", "ا"): (
        "ع ➔ ا/ء (Ayn - Hemze Karışıklığı)",
        "Ayn (ع) harfini Elif/Hemze gibi kesik okudunuz. Boğazın ortasını (vasatü'l-halk) hafifçe sıkarak sesi derinleştirin."
    ),
    ("ع", "ء"): (
        "ع ➔ ء (Ayn - Hemze Karışıklığı)",
        "Ayn (ع) sesini boğazın ortasından sıkıştırarak çıkarın; Hemze (ء) gibi göğse yakın kesmeyin."
    ),
    ("ع", "ه"): (
        "ع ➔ ه (Ayn - He Karışıklığı)",
        "Ayn (ع) harfini He (ه) gibi nefesli çıkardınız. Boğaz ortası kaslarını hafifçe daraltarak net bir 'Ayn' sesi verin."
    ),
    ("ح", "ه"): (
        "ح ➔ ه (Hâ - He Karışıklığı)",
        "Boğaz ortasından çıkan Hâ (ح) harfini göğüsten çıkan ince He (ه) gibi okudunuz. Boğazınızı hafifçe törpüler gibi nefes verin."
    ),
    ("ح", "خ"): (
        "ح ➔ خ (Hâ - Hı Karışıklığı)",
        "Hâ (ح) harfini hırıltılı Hı (خ) gibi okudunuz. Hırıltı yapmadan, boğazın ortasından temiz ve sıcak bir nefes çıkarın."
    ),
    ("خ", "ح"): (
        "خ ➔ ح (Hı - Hâ Karışıklığı)",
        "Hı (خ) harfini boğazın üst kısmından hafif hırıltılı çıkarmanız gerekirken düz Hâ (ح) gibi okudunuz."
    ),
    ("خ", "ك"): (
        "خ ➔ ك (Hı - Kef Karışıklığı)",
        "Hı (خ) harfi boğazın evvelinden hırıltılı çıkar; dilin damağa değmesiyle çıkan Kef (ك) sesine kaymayın."
    ),
    ("غ", "خ"): (
        "غ ➔ خ (Ğayn - Hı Karışıklığı)",
        "Ğayn (غ) ses tellerinin titreştiği yumuşak bir boğaz harfidir; sert ve nefessiz Hı (خ) gibi çıkarmayın."
    ),
    ("غ", "ق"): (
        "غ ➔ ق (Ğayn - Kâf Karışıklığı)",
        "Ğayn (غ) akıcı bir boğaz sesidir; Kâf (ق) gibi patlamalı ve kesik çıkarmayın."
    ),

    # Kalın (İsti'lâ / Itbak) ve İnce (İstifâl) Harf Karışıklıkları
    ("ص", "س"): (
        "ص ➔ س (Sâd - Sîn / Kalınlık Eksikliği)",
        "Kalın Sâd (ص) harfini ince Sîn (س) gibi okudunuz. Dil kökünü üst damağa doğru kaldırarak sesi toklaştırın (tefhim)."
    ),
    ("س", "ص"): (
        "س ➔ ص (Sîn - Sâd / Fazla Kalınlaştırma)",
        "İnce Sîn (س) harfini kalın Sâd (ص) gibi okudunuz. Dil kökünü indirin ve dudakları yayarak ince okuyun."
    ),
    ("ط", "ت"): (
        "ط ➔ ت (Tâ - Tê / Kalınlık Eksikliği)",
        "Kalın Tâ (ط) harfini ince Tê (ت) gibi nefesli okudunuz. Dilin arka kısmını damağa kaldırıp nefessiz ve tok çıkarın."
    ),
    ("ط", "د"): (
        "ط ➔ د (Tâ - Dâl Karışıklığı)",
        "Tâ (ط) harfini ince Dâl (د) gibi okudunuz. Dil sırtını üst damağa yaklaştırarak sesi kalınlaştırın."
    ),
    ("ض", "د"): (
        "ض ➔ د (Dâd - Dâl Karışıklığı)",
        "Arapçaya özgü kalın Dâd (ض) harfini ince Dâl (د) gibi okudunuz. Dilin yan kenarını üst azı dişlere temas ettirerek tok çıkarın."
    ),
    ("ض", "ظ"): (
        "ض ➔ ظ (Dâd - Zâ Karışıklığı)",
        "Dâd (ض) harfini peltek Zâ (ظ) gibi okudunuz. Dil ucunu dişlerin arasına çıkarmayın; dil yanını üst azı dişlere dayayın."
    ),
    ("ض", "ز"): (
        "ض ➔ ز (Dâd - Zê Karışıklığı)",
        "Dâd (ض) harfini keskin Zê (ز) gibi okudunuz. Islık sesi vermeden, dil yanını üst azı dişlere dayayarak tok çıkarın."
    ),
    ("ق", "ك"): (
        "ق ➔ ك (Kâf - Kef / Mahreç Kayması)",
        "Kalın Kâf (ق) harfini ince Kef (ك) gibi okudunuz. Sesi küçük dile yakın en arka damaktan tok bir şekilde çıkarın."
    ),
    ("ك", "ق"): (
        "ك ➔ ق (Kef - Kâf Karışıklığı)",
        "İnce Kef (ك) harfini kalın Kâf (ق) gibi okudunuz. Sesi boğaza kaçırmadan orta damaktan ince çıkarın."
    ),

    # Peltek (el-Hurûfü'l-Liseviyye) ve Keskin (Safîr) Harf Karışıklıkları
    ("ث", "س"): (
        "ث ➔ س (Peltek Sê - Keskin Sîn)",
        "Peltek Sê (ث) harfini keskin Sîn (س) gibi okudunuz. Dil ucunu üst ön dişlerin ucuna hafifçe değdirerek ıslıksız çıkarın."
    ),
    ("ث", "ت"): (
        "ث ➔ ت (Peltek Sê - Tê)",
        "Peltek Sê (ث) harfinde nefes akışı devam etmelidir; Tê (ت) gibi sesi tamamen kapatmayın."
    ),
    ("ذ", "ز"): (
        "ذ ➔ ز (Peltek Zâl - Keskin Zê)",
        "Peltek Zâl (ذ) harfini keskin Zê (ز) gibi okudunuz. Dilinizin ucunu üst ön dişlerinize hafifçe değdirerek yumuşak çıkarın."
    ),
    ("ذ", "د"): (
        "ذ ➔ د (Peltek Zâl - Dâl)",
        "Peltek Zâl (ذ) akıcı bir sestir; dil ucunu ön diş uçlarına değdirin, Dâl (د) gibi sert kapatmayın."
    ),
    ("ظ", "ز"): (
        "ظ ➔ ز (Kalın Peltek Zâ - İnce Keskin Zê)",
        "Zâ (ظ) hem peltek hem kalın bir harftir; dil ucunu ön dişlere değdirin ve dil kökünü kaldırarak sesi kalınlaştırın."
    ),
    ("ظ", "ذ"): (
        "ظ ➔ ذ (Kalın Zâ - İnce Zâl)",
        "Peltekliği doğru yaptınız ancak Zâ (ظ) harfini ince Zâl (ذ) gibi okudunuz. Dil kökünü kaldırarak sesi kalınlaştırın."
    ),

    # Dil Ucu (Zelakiyye: ن - ر - ل), Dudak (ب - م - ف) ve Diş Eti Harfleri
    ("ن", "ر"): (
        "ن ➔ ر (Nûn - Râ Mahreç / Kök Karışıklığı)",
        "Nûn (ن) harfini Râ (ر) gibi okudunuz. Dil ucunu üst diş etlerine sabit değdirip genizden (gunneli) net bir 'N' sesi verin; dili titretmeyin."
    ),
    ("ر", "ن"): (
        "ر ➔ ن (Râ - Nûn Mahreç Karışıklığı)",
        "Râ (ر) harfini Nûn (ن) gibi okudunuz. Sesi genze kaçırmadan dil ucunu üst damağa hafifçe değdirerek çıkarın."
    ),
    ("ن", "ل"): (
        "ن ➔ ل (Nûn - Lâm Karışıklığı)",
        "Nûn (ن) harfini Lâm (ل) gibi okudunuz. Dil ucunu diş etine bastırıp hafif geniz (gunne) tınısıyla 'N' sesini netleştirin."
    ),
    ("ل", "ن"): (
        "ل ➔ ن (Lâm - Nûn Karışıklığı)",
        "Lâm (ل) harfini genizden gelen Nûn (ن) gibi okudunuz. Geniz tınısı vermeden ağız içinden berrak bir 'L' sesi çıkarın."
    ),
    ("ر", "ل"): (
        "ر ➔ ل (Râ - Lâm Karışıklığı)",
        "Râ (ر) harfini Lâm (ل) gibi okudunuz. Dil ucunu damağa yapıştırmayın; hafif titreşimle 'R' sesini belirginleştirin."
    ),
    ("ل", "ر"): (
        "ل ➔ ر (Lâm - Râ Karışıklığı)",
        "Lâm (ل) harfini Râ (ر) gibi okudunuz. Dil ucunu titretmeden üst ön diş etlerine net bir şekilde değdirin."
    ),
    ("ب", "م"): (
        "ب ➔ م (Bê - Mîm Dudak Karışıklığı)",
        "Bê (ب) harfini Mîm (م) gibi genizden okudunuz. Dudakları birbirine kapatıp hızla açarak patlamalı bir 'B' sesi çıkarın."
    ),
    ("م", "ب"): (
        "م ➔ ب (Mîm - Bê Dudak Karışıklığı)",
        "Mîm (م) harfini Bê (ب) gibi sert patlattınız. Dudakları yumuşakça kapatıp sesi genizden (gunneli) akıtın."
    ),
    ("ف", "ب"): (
        "ف ➔ ب (Fê - Bê Dudak Karışıklığı)",
        "Fê (ف) harfinde üst ön dişler alt dudağın iç kısmına değmeli ve nefes akmalıdır; iki dudağı kapatarak 'B' demeyin."
    ),
    ("س", "ش"): (
        "س ➔ ش (Sîn - Şîn Karışıklığı)",
        "İnce ıslıklı Sîn (س) harfini yayvan Şîn (ش) gibi okudunuz. Dil ucunu alt ön dişlerin arkasına yaklaştırın."
    ),
    ("ش", "س"): (
        "ش ➔ س (Şîn - Sîn Karışıklığı)",
        "Şîn (ش) harfini Sîn (س) gibi okudunuz. Dil ortasını üst damağa yaklaştırarak sesi ağız içinde yayın (tefeşşî)."
    ),
    ("ج", "ش"): (
        "ج ➔ ش (Cîm - Şîn Karışıklığı)",
        "Cîm (ج) harfini gevşeterek Şîn (ش) gibi nefesli okudunuz. Dil ortasını damağa tam değdirerek tok bir 'C' sesi çıkarın."
    ),
    ("د", "ت"): (
        "د ➔ ت (Dâl - Tê Karışıklığı)",
        "Cehr (ötümlü) olan Dâl (د) harfini nefesli Tê (ت) gibi okudunuz. Ses tellerini titreştirerek net bir 'D' sesi verin."
    ),
    ("ت", "د"): (
        "ت ➔ د (Tê - Dâl Karışıklığı)",
        "İnce ve hafif nefesli (hems) olan Tê (ت) harfini sert Dâl (د) gibi okudunuz."
    ),
}

MED_LETTERS = {"ا", "و", "ي"}


# ==============================================================================
# 2. TEMEL METİN TEMİZLEME, VARYANT (/) ÇÖZÜMLEME VE NORMALİZASYON
# ==============================================================================
def remove_diacritics(text: str) -> str:
    if not text:
        return ""
    return re.sub(r"[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]", "", text)


def has_arabic_diacritics(text: str) -> bool:
    """Metinde en az bir Arapça hareke (fetha, damme, kesra, şedde, sükun vb.) olup olmadığını kontrol eder."""
    if not text:
        return False
    return bool(re.search(r"[\u064B-\u0652\u0670]", text))


def clean_invisible_marks(text: str) -> str:
    if not text:
        return ""
    for ch in ("\u200e", "\u200f", "\u202a", "\u202b", "\u202c", "\u2066", "\u2067", "\u2068", "\u2069", "\ufeff"):
        text = text.replace(ch, "")
    return text.strip()


def sanitize_arabic_sentence(text: str) -> str:
    """
    Veritabanı Metin Doktoru (Arapça):
    - Cümle ortasında RTL yönünü ikiye bölen (22), (11.30), (40%) gibi parantez içi sayıları temizler.
    - Tatweel (ـ) uzatma çizgilerini ve noktalama öncesi boşlukları kaldırır.
    """
    if not text:
        return ""
    cleaned = clean_invisible_marks(text).replace("\u0640", "")
    cleaned = re.sub(r"\(\s*[\d\u0660-\u0669\.\,\:\-\%\s]+\s*\)", " ", cleaned)
    cleaned = re.sub(r"\s+([؟،.!:])", r"\1", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def sanitize_turkish_sentence(text: str) -> str:
    """
    Veritabanı Metin Doktoru (Türkçe):
    - Cümle sonlarında kitaptan kalan '. 81', '. 47', '. 16' gibi dipnot/sayfa numaralarını temizler.
    """
    if not text:
        return ""
    cleaned = clean_invisible_marks(text)
    cleaned = re.sub(r"([.!?\"'])\s+\d{1,4}\s*$", r"\1", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def expand_arabic_variants(expected_raw: str) -> List[str]:
    """
    Sözlük ve kelime kartlarındaki 'سَلَامَة / سَلَام' veya 'أَلِمَ / أَلَّمَ' gibi
    '/' ile ayrılmış alternatifleri tekil varyant listesine dönüştürür.
    """
    cleaned = sanitize_arabic_sentence(expected_raw)
    if not cleaned:
        return [""]
    if "/" not in cleaned:
        return [cleaned]
    parts = [p.strip() for p in cleaned.split("/") if p.strip()]
    combined = " ".join(parts)
    return parts + ([combined] if len(parts) > 1 else [])


def check_item_quality(item_id: str, arapca: str, turkce: str, ses: Optional[str] = None, resolve_fn=None) -> List[str]:
    issues = []
    ar_clean = clean_invisible_marks(arapca or "")
    tr_clean = clean_invisible_marks(turkce or "")

    if not ar_clean:
        issues.append("Arapça metin boş")
    else:
        if not has_arabic_diacritics(ar_clean):
            issues.append("Hareke eksik (Harekesiz metin)")
        if re.search(r"\(\s*\d+\s*\)", ar_clean):
            issues.append("RTL bozan parantez içi rakam var")

    if not tr_clean:
        issues.append("Türkçe çeviri eksik")
    elif re.search(r"[.!?]\s+\d{1,4}\s*$", tr_clean):
        issues.append("Türkçe sonunda sayfa/dipnot numarası kalmış")

    if ses and resolve_fn:
        resolved = resolve_fn(ses)
        if not resolved:
            issues.append(f"Ses dosyası bulunamadı ({ses})")

    return issues


def normalize_arabic(text: str) -> str:
    if not text:
        return ""
    text = sanitize_arabic_sentence(text)
    text = remove_diacritics(text)
    text = re.sub(r"[إأآٱ]", "ا", text)
    text = text.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "ء").replace("ئ", "ء")
    text = re.sub(r"[^\u0600-\u06FF\s]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _phonetic_skeleton(word: str) -> str:
    """
    Mahreç analizi için harflerin fonetik kimliğini koruyarak harekeleri temizler.
    normalize_arabic ile tam tutarlılık için 'ة -> ه' dönüşümünü içerir.
    """
    if not word:
        return ""
    w = remove_diacritics(clean_invisible_marks(word))
    w = re.sub(r"[إأآٱ]", "ا", w)
    w = w.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "ء").replace("ئ", "ء")
    w = re.sub(r"[^\u0600-\u06FF]", "", w)
    return w


def _consonant_skeleton(skel_word: str) -> str:
    """
    Kelimenin ilk harfi hariç med harflerini (ا، و، ي) soyarak saf sessiz harf (kök) iskeletini döndürür.
    """
    if not skel_word:
        return ""
    w = strip_al(skel_word)
    if len(w) <= 1:
        return w
    return w[0] + "".join(ch for ch in w[1:] if ch not in MED_LETTERS)


def strip_al(text: str) -> str:
    words = text.split()
    return " ".join([w[2:] if w.startswith("ال") and len(w) > 3 else w for w in words])


def collapse_consecutive_repeats(text: str) -> str:
    """
    Öğrencinin tek bir kelimeyi peş peşe iki kez okuması veya mikrofon yankısı nedeniyle
    oluşan ardışık kelime tekrarlarını ('نحلة نحلة' -> 'نحلة') tekilleştirir.
    """
    if not text:
        return ""
    words = text.split()
    if len(words) <= 1:
        return text
    deduped = [words[0]]
    for w in words[1:]:
        if strip_al(normalize_arabic(w)) != strip_al(normalize_arabic(deduped[-1])):
            deduped.append(w)
    return " ".join(deduped)


def levenshtein(a: str, b: str) -> int:
    if len(a) < len(b):
        a, b = b, a
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def similarity(a: str, b: str) -> float:
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    return max(0.0, 1.0 - levenshtein(a, b) / max(len(a), len(b)))


def _apply_root_consonant_guard(e_word: str, h_word: str, raw_sim: float) -> float:
    """
    Kısa kelimelerde (<= 6 harf) asli/kök sessiz harflerden biri değiştiğinde veya
    yutulduğunda (Örn: نَحْلَة -> رحلة veya كَلْب -> قَلْب), %75 olan ham benzerliği
    geçme eşiğinin (%65) altına (~%53) düşürerek yanlışlıkla 'Doğru' sayılmasını önler.
    """
    if raw_sim >= 0.99 or raw_sim <= 0.0:
        return raw_sim
    ew_s = strip_al(_phonetic_skeleton(e_word))
    hw_s = strip_al(_phonetic_skeleton(h_word))
    if not ew_s or not hw_s:
        return raw_sim

    if len(ew_s) <= 6:
        e_cons = _consonant_skeleton(ew_s)
        h_cons = _consonant_skeleton(hw_s)
        if e_cons != h_cons:
            cons_dist = levenshtein(e_cons, h_cons)
            penalized = raw_sim - (0.22 * cons_dist)
            return max(0.0, min(0.58, penalized))
        elif ew_s != hw_s and len(ew_s) <= 4:
            return min(0.60, raw_sim)
    return raw_sim


def compare_arabic(expected_raw: str, heard_raw: str) -> Tuple[float, str, str]:
    """
    Beklenen Arapça metin ile duyulan metni karşılaştırır:
    - '/' ile ayrılmış sözlük varyantlarını destekler.
    - Ardışık kelime tekrarlarını ('نحلة نحلة' -> 'نحلة') otomatik tekilleştirir.
    - Tek kelimelik ifadelerde kök harf değişimlerine karşı '_apply_root_consonant_guard' uygular.
    """
    h_norm = normalize_arabic(heard_raw)
    h_collapsed = collapse_consecutive_repeats(h_norm)
    h_vars = {h_norm, strip_al(h_norm), h_collapsed, strip_al(h_collapsed)} - {""}
    variants = expand_arabic_variants(expected_raw)

    best_score = 0.0
    best_e_norm = normalize_arabic(variants[0]) if variants else ""

    for cand in variants:
        e_norm = normalize_arabic(cand)
        e_vars = {e_norm, strip_al(e_norm)} - {""}
        is_single_word = (len(e_norm.split()) == 1)
        for ev in e_vars:
            for hv in h_vars:
                sc = similarity(ev, hv)
                if is_single_word and len(hv.split()) == 1:
                    sc = _apply_root_consonant_guard(ev, hv, sc)
                if sc > best_score:
                    best_score = sc
                    best_e_norm = e_norm

    return best_score, best_e_norm, h_norm


def _evaluate_single_variant_words(
    cleaned_exp: str,
    heard_words: List[str],
    word_threshold: float = DEFAULT_WORD_SIMILARITY_THRESHOLD
) -> Tuple[List[Tuple[str, bool]], float]:
    exp_words = cleaned_exp.split()
    if not exp_words:
        return [], 1.0

    results = []
    used_heard_indices = set()
    matched_count = 0
    eval_word_count = 0
    is_single_target = (len(exp_words) == 1)

    for ew in exp_words:
        ew_norm = normalize_arabic(ew)
        if not ew_norm:
            results.append((ew, True))
            continue

        eval_word_count += 1
        ew_stripped = strip_al(ew_norm)
        best_sim = 0.0
        best_idx = -1

        for idx, hw in enumerate(heard_words):
            if idx in used_heard_indices:
                continue
            hw_stripped = strip_al(hw)
            sim = max(similarity(ew_norm, hw), similarity(ew_stripped, hw_stripped))
            if is_single_target or len(ew_stripped) <= 4:
                sim = _apply_root_consonant_guard(ew_stripped, hw_stripped, sim)
            if sim > best_sim:
                best_sim = sim
                best_idx = idx

        is_word_ok = best_sim >= word_threshold
        if is_word_ok and best_idx != -1:
            used_heard_indices.add(best_idx)
            matched_count += 1
        results.append((ew, is_word_ok))

    ratio = (matched_count / eval_word_count) if eval_word_count > 0 else 1.0
    return results, ratio


def evaluate_sentence_words(
    expected_raw: str,
    heard_raw: str,
    word_threshold: float = DEFAULT_WORD_SIMILARITY_THRESHOLD
) -> Tuple[List[Tuple[str, bool]], float]:
    heard_norm = collapse_consecutive_repeats(normalize_arabic(heard_raw))
    heard_words = heard_norm.split()
    variants = expand_arabic_variants(expected_raw)

    best_results: List[Tuple[str, bool]] = []
    best_ratio: float = -1.0

    for cand in variants:
        res, ratio = _evaluate_single_variant_words(cand, heard_words, word_threshold)
        if ratio > best_ratio:
            best_ratio = ratio
            best_results = res

    return best_results, max(0.0, best_ratio)


# ==============================================================================
# 3. KARAKTER HİZALAMALI EVRENSEL MAHREÇ, VEZİN VE MED KOÇU
# ==============================================================================
def analyze_phoneme_and_makhraj_errors(expected_raw: str, heard_raw: str) -> List[Dict[str, str]]:
    """
    Beklenen Arapça metin ile kullanıcının sesinden algılanan metni kelime ve harf düzeyinde hizalar:
    1) Özel mahreç çiftlerini (MAKHRAJ_COACH_MAP) yakalar.
    2) Özel listede olmasa bile her türlü kök/sessiz harf değişimini (Örn: نَحْلَة -> رحلة) yakalar.
    3) 3 harfli ecvef kelimelerdeki gerçek med yutmalarını (قَالَ -> قل) 'Med Eksikliği' olarak,
       4 harfli isimlerin 3 harfli fiile dönüşmesini (كِتَاب -> كتب, عَامِل -> عمل) ise
       'Sarf Kalıbı / Vezin Farklılığı' olarak ayrıştırır.
    """
    if not expected_raw or not heard_raw:
        return []

    variants = expand_arabic_variants(expected_raw)
    best_variant = variants[0]
    best_v_score = -1.0
    for cand in variants:
        sc, _, _ = compare_arabic(cand, heard_raw)
        if sc > best_v_score:
            best_v_score = sc
            best_variant = cand

    exp_words = sanitize_arabic_sentence(best_variant).split()
    heard_cleaned = collapse_consecutive_repeats(sanitize_arabic_sentence(heard_raw)) if len(exp_words) == 1 else sanitize_arabic_sentence(heard_raw)
    heard_words = heard_cleaned.split()
    if not exp_words or not heard_words:
        return []

    feedback_list: List[Dict[str, str]] = []
    seen_titles = set()
    used_h_idx = set()
    is_single_word_mode = (len(exp_words) == 1)

    for ew in exp_words:
        e_skel = _phonetic_skeleton(ew)
        if not e_skel:
            continue

        best_h_word = ""
        best_h_skel = ""
        best_sim = -1.0
        best_idx = -1

        for idx, hw in enumerate(heard_words):
            if idx in used_h_idx:
                continue
            h_skel = _phonetic_skeleton(hw)
            if not h_skel:
                continue
            sim = max(similarity(e_skel, h_skel), similarity(strip_al(e_skel), strip_al(h_skel)))
            if sim > best_sim:
                best_sim = sim
                best_h_word = hw
                best_h_skel = h_skel
                best_idx = idx

        if best_idx == -1:
            continue

        e_cmp = strip_al(e_skel) if e_skel.startswith("ال") and not best_h_skel.startswith("ال") else e_skel
        h_cmp = strip_al(best_h_skel) if best_h_skel.startswith("ال") and not e_skel.startswith("ال") else best_h_skel

        if e_cmp == h_cmp:
            used_h_idx.add(best_idx)
            continue

        min_sim_threshold = 0.45 if is_single_word_mode else (0.58 if len(e_cmp) <= 4 else 0.50)
        if best_sim >= min_sim_threshold:
            used_h_idx.add(best_idx)
            e_cons = _consonant_skeleton(e_cmp)
            h_cons = _consonant_skeleton(h_cmp)
            cons_sim = similarity(e_cons, h_cons)

            matcher = difflib.SequenceMatcher(None, e_cmp, h_cmp)
            for tag, i1, i2, j1, j2 in matcher.get_opcodes():
                if tag == "replace":
                    e_sub = e_cmp[i1:i2]
                    h_sub = h_cmp[j1:j2]
                    for ec, hc in zip(e_sub, h_sub):
                        if ec == hc:
                            continue
                        if (ec, hc) in MAKHRAJ_COACH_MAP:
                            title, tip = MAKHRAJ_COACH_MAP[(ec, hc)]
                            full_title = f"🎯 {title}"
                            if full_title not in seen_titles:
                                seen_titles.add(full_title)
                                feedback_list.append({
                                    "title": full_title,
                                    "tip": tip,
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })
                        elif ec in MED_LETTERS and hc in MED_LETTERS and cons_sim >= 0.75:
                            title = f"🔄 Vezin / Med Harfi Değişimi ({ec} ➔ {hc})"
                            if title not in seen_titles:
                                seen_titles.add(title)
                                feedback_list.append({
                                    "title": title,
                                    "tip": f"'{ew}' kelimesindeki '{ec}' sesini '{hc}' gibi okudunuz ('{best_h_word}' algılandı). Harfin ünlü/vezin yönüne dikkat edin.",
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })
                        else:
                            title = f"🎯 Harf / Kök Değişimi ({ec} ➔ {hc})"
                            if title not in seen_titles:
                                seen_titles.add(title)
                                feedback_list.append({
                                    "title": title,
                                    "tip": f"'{ew}' kelimesindeki '{ec}' harfi yerine '{hc}' sesi algılandı ('{best_h_word}'). Tek bir kök harfin değişmesi anlamı tamamen değiştirir; '{ec}' harfini net çıkarın.",
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })

                elif tag == "delete":
                    deleted_chars = e_cmp[i1:i2]
                    for dc in deleted_chars:
                        if dc in MED_LETTERS and i1 > 0 and e_cons == h_cons:
                            if dc == "ا" and len(e_cmp) == 4 and len(h_cmp) == 3 and len(h_cons) == 3:
                                title = f"🔄 Sarf Kalıbı / Vezin Farklılığı ({ew} ➔ {best_h_word})"
                                if title not in seen_titles:
                                    seen_titles.add(title)
                                    feedback_list.append({
                                        "title": title,
                                        "tip": f"'{ew}' ismini/sıfatını '{best_h_word}' (fiil/kök) kalıbında okudunuz. '{dc}' uzatmasını koruyarak kelime kalıbını bozmayın.",
                                        "word_exp": ew,
                                        "word_heard": best_h_word
                                    })
                            else:
                                title = f"📏 Med (Uzatma) Eksikliği ({dc})"
                                if title not in seen_titles:
                                    seen_titles.add(title)
                                    feedback_list.append({
                                        "title": title,
                                        "tip": f"'{ew}' kelimesindeki '{dc}' uzatma (med) harfini kısa okudunuz. Sesi bir elif miktarı uzatın.",
                                        "word_exp": ew,
                                        "word_heard": best_h_word
                                    })
                        elif dc == "ع":
                            title = "🎯 ع (Ayn) Harfi Yutuldu"
                            if title not in seen_titles:
                                seen_titles.add(title)
                                feedback_list.append({
                                    "title": title,
                                    "tip": f"'{ew}' kelimesindeki Ayn (ع) harfi duyulmadı. Boğaz ortasını hafifçe sıkarak harfi belirginleştirin.",
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })
                        elif dc not in MED_LETTERS:
                            title = f"🎯 Eksik / Yutulan Harf ({dc})"
                            if title not in seen_titles:
                                seen_titles.add(title)
                                feedback_list.append({
                                    "title": title,
                                    "tip": f"'{ew}' kelimesindeki '{dc}' harfi sesinizde duyulmadı ('{best_h_word}' algılandı). Harfi yutmadan net telaffuz edin.",
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })

                elif tag == "insert":
                    inserted_chars = h_cmp[j1:j2]
                    for ic in inserted_chars:
                        if ic in MED_LETTERS and j1 > 0 and e_cons == h_cons:
                            title = f"📏 Gereksiz Uzatma / Hareke Hatası ({ic})"
                            if title not in seen_titles:
                                seen_titles.add(title)
                                feedback_list.append({
                                    "title": title,
                                    "tip": f"'{ew}' kelimesindeki kısa harekeyi fazla uzatarak '{ic}' med harfine dönüştürdünüz. Harekeyi uzatmadan net okuyun.",
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })
                        elif ic not in MED_LETTERS:
                            title = f"🎯 Fazladan Harf Algılandı ({ic})"
                            if title not in seen_titles:
                                seen_titles.add(title)
                                feedback_list.append({
                                    "title": title,
                                    "tip": f"'{ew}' kelimesini okurken fazladan '{ic}' sesi algılandı ('{best_h_word}').",
                                    "word_exp": ew,
                                    "word_heard": best_h_word
                                })

            if not feedback_list and e_cmp != h_cmp:
                title = f"⚠️ Kelime / Kök Farklılığı ({ew} ➔ {best_h_word})"
                if title not in seen_titles:
                    seen_titles.add(title)
                    feedback_list.append({
                        "title": title,
                        "tip": f"Beklenen '{ew}' kelimesi yerine '{best_h_word}' algılandı. Kelimenin kök harflerine ve hareke akışına dikkat ederek tekrar okuyun.",
                        "word_exp": ew,
                        "word_heard": best_h_word
                    })

            if "\u0651" in ew and best_sim < 0.88 and "⚡ Şedde (Teşdîd) Vurgusu" not in seen_titles:
                seen_titles.add("⚡ Şedde (Teşdîd) Vurgusu")
                feedback_list.append({
                    "title": "⚡ Şedde (ّ ) Vurgusuna Dikkat",
                    "tip": f"'{ew}' kelimesinde şeddeli harf bulunuyor. Harfin mahrecinde kısa bir süre tutarak çift ses vurgusu yapın.",
                    "word_exp": ew,
                    "word_heard": best_h_word
                })

        elif is_single_word_mode and best_h_word:
            title = f"⚠️ Tamamen Farklı Kelime Algılandı ({ew} ➔ {best_h_word})"
            if title not in seen_titles:
                seen_titles.add(title)
                feedback_list.append({
                    "title": title,
                    "tip": f"Beklenen '{ew}' ifadesi yerine '{best_h_word}' duyuldu. Önce '🔊 Dinle' butonuna basıp doğru telaffuzu dinleyin.",
                    "word_exp": ew,
                    "word_heard": best_h_word
                })

    return feedback_list[:3]


def build_haraka_guide(arabic_text: str) -> str:
    """
    Katman 1 — Pedagojik Hareke & Vokal Farkındalık Rehberi:
    Beklenen metindeki hareke dizilimini (Fetha, Kesra, Damme, Şedde, Sükûn) analiz eder.
    """
    if not arabic_text:
        return ""
    primary = expand_arabic_variants(arabic_text)[0]
    fatha_cnt = primary.count("\u064e") + primary.count("\u064b")
    kasra_cnt = primary.count("\u0650") + primary.count("\u064d")
    damma_cnt = primary.count("\u064f") + primary.count("\u064c")
    shadda_cnt = primary.count("\u0651")
    sukun_cnt = primary.count("\u0652")

    parts = []
    if fatha_cnt:
        parts.append(f"Fetha (ـَ a/e): {fatha_cnt}")
    if kasra_cnt:
        parts.append(f"Kesra (ـِ i): {kasra_cnt}")
    if damma_cnt:
        parts.append(f"Damme (ـُ u/ü): {damma_cnt}")
    if shadda_cnt:
        parts.append(f"Şedde (ـّ çift vurgu): {shadda_cnt}")
    if sukun_cnt:
        parts.append(f"Cezim/Sükûn (ـْ ): {sukun_cnt}")

    if not parts:
        return ""
    return " • ".join(parts) + "\n💡 Kısa harekeleri uzatmadan (med yapmadan) net ve keskin seslendirin."


# ==============================================================================
# 4. BİLİMSEL SARF VEZİNLİ & İLETİŞİMSEL KALIPLI CEFR TAHMİN MOTORU (A1 - C1)
# ==============================================================================
CORE_A1_PHRASES = {
    "السلام عليكم", "وعليكم السلام", "الحمد لله", "ان شاء الله", "ما شاء الله",
    "صباح الخير", "صباح النور", "مساء الخير", "مساء النور", "كيف حالك",
    "اهلا وسهلا", "اهلا بك", "مع السلامه", "الى اللقاء", "شكرا جزيلا",
    "عفوا", "من فضلك", "لو سمحت", "بارك الله فيك", "جزاك الله خيرا",
    "تصبح على خير", "فرصه سعيده", "كل عام وانتم بخير"
}

B1_CONNECTORS = {"الذي", "التي", "الذين", "لان", "عندما", "حيث", "بينما", "منذ", "لذلك", "لكي", "حتى", "كي"}
B2_CONNECTORS = {"بالرغم", "رغم", "لولا", "إنما", "انما", "كأن", "حيثما", "كيفما", "فضلا", "سواء", "بيد", "غير"}
C1_CONNECTORS = {"لاسيما", "مما", "حيلولة", "بموجب", "نظرا", "انطلاقا", "علاوة", "بصدد", "حيال", "ازاء"}


def _detect_morphological_complexity(norm_word: str) -> int:
    w = strip_al(norm_word)
    if len(w) <= 4:
        return 0
    if w.startswith("است") and len(w) >= 6:
        return 2
    if w.endswith("يه") and len(w) >= 6:
        return 2
    if (w.startswith("ان") or w.startswith("ت")) and len(w) >= 6:
        return 1
    if len(w) >= 7:
        return 2
    return 1 if len(w) >= 5 else 0


def estimate_word_cefr(arapca: str, gorsel: Optional[str] = None) -> str:
    """Kelimeyi iletişimsel kalıp, sarf vezni ve kök uzunluğuna göre A1..C1 arasında sınıflandırır."""
    primary_ar = expand_arabic_variants(arapca)[0]
    norm = normalize_arabic(primary_ar)
    if not norm:
        return "A1"
    if norm in CORE_A1_PHRASES:
        return "A1"

    words = norm.split()
    w_count = len(words)
    morph_score = sum(_detect_morphological_complexity(w) for w in words)
    stripped_len = len(strip_al(norm).replace(" ", ""))

    if w_count == 1:
        if gorsel and stripped_len <= 5 and morph_score <= 1:
            return "A1"
        if stripped_len <= 4 and morph_score == 0:
            return "A1"
        if stripped_len <= 5 and morph_score <= 1:
            return "A2"
        if stripped_len <= 6 and morph_score <= 1:
            return "B1"
        if stripped_len <= 7 or morph_score == 2:
            return "B2"
        return "C1"
    elif w_count == 2:
        if stripped_len <= 7 and morph_score == 0:
            return "A1"
        if stripped_len <= 9 and morph_score <= 1:
            return "A2"
        if stripped_len <= 12 and morph_score <= 2:
            return "B1"
        return "B2" if morph_score <= 3 else "C1"
    else:
        if norm in CORE_A1_PHRASES:
            return "A1"
        return "B1" if stripped_len <= 13 else ("B2" if stripped_len <= 18 else "C1")


def estimate_sentence_cefr(arapca: str) -> str:
    norm = normalize_arabic(arapca)
    if not norm:
        return "A1"
    if norm in CORE_A1_PHRASES:
        return "A1"

    words = norm.split()
    w_count = len(words)
    base_chars = len(remove_diacritics(arapca).strip())

    for phrase in CORE_A1_PHRASES:
        if norm.startswith(phrase) and w_count <= 5:
            return "A1"

    complexity_bonus = 0.0
    for w in words:
        if w in C1_CONNECTORS:
            complexity_bonus += 4.0
        elif w in B2_CONNECTORS:
            complexity_bonus += 2.8
        elif w in B1_CONNECTORS:
            complexity_bonus += 1.8
        complexity_bonus += _detect_morphological_complexity(w) * 0.45

    effective_score = w_count + complexity_bonus

    if effective_score <= 4.5 and base_chars <= 25:
        return "A1"
    elif effective_score <= 7.5 and base_chars <= 44:
        return "A2"
    elif effective_score <= 11.5 and base_chars <= 70:
        return "B1"
    elif effective_score <= 16.5 and base_chars <= 100:
        return "B2"
    else:
        return "C1"


def estimate_dialogue_cefr(arabic_lines: List[str]) -> str:
    if not arabic_lines:
        return "A1"
    level_weights = {"A1": 1.0, "A2": 2.0, "B1": 3.0, "B2": 4.0, "C1": 5.0}
    total_weight = 0.0
    valid_lines = 0

    for line in arabic_lines:
        if not line or not line.strip():
            continue
        valid_lines += 1
        line_lvl = estimate_sentence_cefr(line)
        total_weight += level_weights.get(line_lvl, 1.0)

    if valid_lines == 0:
        return "A1"
    avg = total_weight / valid_lines
    if avg <= 1.45:
        return "A1"
    elif avg <= 2.25:
        return "A2"
    elif avg <= 3.10:
        return "B1"
    elif avg <= 3.95:
        return "B2"
    else:
        return "C1"


# ==============================================================================
# 5. RTL HİZALAMA VE 72-WCHAR KORUMALI SATIR BÖLÜCÜ
# ==============================================================================
def _lock_rtl_punctuation(line: str) -> str:
    if not line:
        return ""
    locked = re.sub(r"([.؟!،:؛])", lambda m: m.group(1) + "\u200f", line)
    words = locked.split()
    return "\u200f" + " \u200f".join(words) + "\u200f"


def shape_arabic(text: str, max_chars_per_line: int = 0) -> str:
    cleaned = sanitize_arabic_sentence(text)
    if not cleaned:
        return ""
    raw_paragraphs = cleaned.splitlines()
    rtl_lines = []

    for para in raw_paragraphs:
        para = para.strip()
        if not para:
            continue
        words = para.split()
        current_line = []
        current_base_len = 0
        logical_lines = []

        for w in words:
            w_base_len = len(remove_diacritics(w))
            cand_str = " ".join(current_line + [w])
            exceeds_char_limit = max_chars_per_line > 10 and (current_base_len + 1 + w_base_len > max_chars_per_line)
            exceeds_w32_limit = len(cand_str) > MAX_WIN32_SAFE_CODEPOINTS

            if current_line and (exceeds_char_limit or exceeds_w32_limit):
                logical_lines.append(" ".join(current_line))
                current_line = [w]
                current_base_len = w_base_len
            else:
                current_line.append(w)
                current_base_len += (1 + w_base_len) if current_base_len > 0 else w_base_len
        if current_line:
            logical_lines.append(" ".join(current_line))

        for line in logical_lines:
            rtl_lines.append(_lock_rtl_punctuation(line))
    return "\n".join(rtl_lines)


def shape_arabic_by_pixels(text: str, font_obj: tkfont.Font, max_pixel_width: int) -> str:
    cleaned = sanitize_arabic_sentence(text)
    if not cleaned:
        return ""

    max_pixel_width = max(300, max_pixel_width)
    words = cleaned.split()
    current_line = []
    rtl_lines = []

    for w in words:
        candidate = " ".join(current_line + [w])
        if current_line and (font_obj.measure(candidate) > max_pixel_width or len(candidate) > MAX_WIN32_SAFE_CODEPOINTS):
            rtl_lines.append(_lock_rtl_punctuation(" ".join(current_line)))
            current_line = [w]
        else:
            current_line.append(w)
            if w.endswith((".", "؟", "!")) and len(" ".join(current_line)) >= NATURAL_CLAUSE_BREAK_MIN_LEN:
                rtl_lines.append(_lock_rtl_punctuation(" ".join(current_line)))
                current_line = []

    if current_line:
        rtl_lines.append(_lock_rtl_punctuation(" ".join(current_line)))

    return "\n".join(rtl_lines)


def lookup_word_in_dictionary_stub(arabic_word: str) -> dict:
    try:
        from core.dictionary import lookup_word_morphology
        res = lookup_word_morphology(arabic_word)
        if res:
            return res
    except Exception:
        pass
    norm_w = strip_al(normalize_arabic(arabic_word))
    return {"word": arabic_word, "normalized": norm_w, "meaning": None, "ready": False}