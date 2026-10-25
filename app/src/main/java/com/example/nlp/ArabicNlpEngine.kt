package com.example.nlp

import kotlin.math.max
import kotlin.math.min

data class MakhrajTip(
    val title: String,
    val tip: String,
    val wordExp: String,
    val wordHeard: String
)

data class ComparisonResult(
    val score: Double,
    val expectedNorm: String,
    val heardNorm: String
)

data class SentenceWordsEvaluation(
    val wordResults: List<Pair<String, Boolean>>,
    val ratio: Double
)

object ArabicNlpEngine {
    const val DEFAULT_WORD_SIMILARITY_THRESHOLD = 0.72
    private val MED_LETTERS = setOf('ا', 'و', 'ي')

    val TERM_TOOLTIPS = mapOf(
        "Müfret (Tekil)" to "Müfret: Kelimenin tekil (bir adet) hali.",
        "Cemî (Çoğul)" to "Cemî: Kelimenin çoğul (üç ve daha fazla) hali.",
        "Mâzî (Geçmiş)" to "Mâzî Fiil: Geçmiş zaman kipi (Örn: كَتَبَ - Yazdı).",
        "Muzâri (Geniş/Şimdiki)" to "Muzâri Fiil: Şimdiki veya geniş zaman kipi (Örn: يَكْتُبُ - Yazıyor / Yazar).",
        "Masdar (İsim Fiil)" to "Masdar: Fiilin zaman bildirmeyen isim hali (-mak / -mek).",
        "SÜLÂSÎ KÖK" to "Sülâsî Kök: Arapça kelimenin türediği 3 asli (temel) harf. Dokunarak bu kökten türeyen tüm akraba kelimeleri görebilirsiniz."
    )

    val MAKHRAJ_COACH_MAP: Map<Pair<Char, Char>, Pair<String, String>> = mapOf(
        // Boğaz (Halk) Harfleri
        ('ع' to 'ا') to (
            "ع ➔ ا/ء (Ayn - Hemze Karışıklığı)" to
            "Ayn (ع) harfini Elif/Hemze gibi kesik okudunuz. Boğazın ortasını (vasatü'l-halk) hafifçe sıkarak sesi derinleştirin."
        ),
        ('ع' to 'ء') to (
            "ع ➔ ء (Ayn - Hemze Karışıklığı)" to
            "Ayn (ع) sesini boğazın ortasından sıkıştırarak çıkarın; Hemze (ء) gibi göğse yakın kesmeyin."
        ),
        ('ع' to 'ه') to (
            "ع ➔ ه (Ayn - He Karışıklığı)" to
            "Ayn (ع) harfini He (ه) gibi nefesli çıkardınız. Boğaz ortası kaslarını hafifçe daraltarak net bir 'Ayn' sesi verin."
        ),
        ('ح' to 'ه') to (
            "ح ➔ ه (Hâ - He Karışıklığı)" to
            "Boğaz ortasından çıkan Hâ (ح) harfini göğüsten çıkan ince He (ه) gibi okudunuz. Boğazınızı hafifçe törpüler gibi nefes verin."
        ),
        ('ح' to 'خ') to (
            "ح ➔ خ (Hâ - Hı Karışıklığı)" to
            "Hâ (ح) harfini hırıltılı Hı (خ) gibi okudunuz. Hırıltı yapmadan, boğazın ortasından temiz ve sıcak bir nefes çıkarın."
        ),
        ('خ' to 'ح') to (
            "خ ➔ ح (Hı - Hâ Karışıklığı)" to
            "Hı (خ) harfini boğazın üst kısmından hafif hırıltılı çıkarmanız gerekirken düz Hâ (ح) gibi okudunuz."
        ),
        ('خ' to 'ك') to (
            "خ ➔ ك (Hı - Kef Karışıklığı)" to
            "Hı (خ) harfi boğazın evvelinden hırıltılı çıkar; dilin damağa değmesiyle çıkan Kef (ك) sesine kaymayın."
        ),
        ('غ' to 'خ') to (
            "غ ➔ خ (Ğayn - Hı Karışıklığı)" to
            "Ğayn (غ) ses tellerinin titreştiği yumuşak bir boğaz harfidir; sert ve nefessiz Hı (خ) gibi çıkarmayın."
        ),
        ('غ' to 'ق') to (
            "غ ➔ ق (Ğayn - Kâf Karışıklığı)" to
            "Ğayn (غ) akıcı bir boğaz sesidir; Kâf (ق) gibi patlamalı ve kesik çıkarmayın."
        ),

        // Kalın (İsti'lâ / Itbak) ve İnce (İstifâl) Harf Karışıklıkları
        ('ص' to 'س') to (
            "ص ➔ س (Sâd - Sîn / Kalınlık Eksikliği)" to
            "Kalın Sâd (ص) harfini ince Sîn (س) gibi okudunuz. Dil kökünü üst damağa doğru kaldırarak sesi toklaştırın (tefhim)."
        ),
        ('س' to 'ص') to (
            "س ➔ ص (Sîn - Sâd / Fazla Kalınlaştırma)" to
            "İnce Sîn (س) harfini kalın Sâd (ص) gibi okudunuz. Dil kökünü indirin ve dudakları yayarak ince okuyun."
        ),
        ('ط' to 'ت') to (
            "ط ➔ ت (Tâ - Tê / Kalınlık Eksikliği)" to
            "Kalın Tâ (ط) harfini ince Tê (ت) gibi nefesli okudunuz. Dilin arka kısmını damağa kaldırıp nefessiz ve tok çıkarın."
        ),
        ('ط' to 'د') to (
            "ط ➔ د (Tâ - Dâl Karışıklığı)" to
            "Tâ (ط) harfini ince Dâl (د) gibi okudunuz. Dil sırtını üst damağa yaklaştırarak sesi kalınlaştırın."
        ),
        ('ض' to 'د') to (
            "ض ➔ د (Dâd - Dâl Karışıklığı)" to
            "Arapçaya özgü kalın Dâd (ض) harfini ince Dâl (د) gibi okudunuz. Dilin yan kenarını üst azı dişlere temas ettirerek tok çıkarın."
        ),
        ('ض' to 'ظ') to (
            "ض ➔ ظ (Dâd - Zâ Karışıklığı)" to
            "Dâd (ض) harfini peltek Zâ (ظ) gibi okudunuz. Dil ucunu dişlerin arasına çıkarmayın; dil yanını üst azı dişlere dayayın."
        ),
        ('ض' to 'ز') to (
            "ض ➔ ز (Dâd - Zê Karışıklığı)" to
            "Dâd (ض) harfini keskin Zê (ز) gibi okudunuz. Islık sesi vermeden, dil yanını üst azı dişlere dayayarak tok çıkarın."
        ),
        ('ق' to 'ك') to (
            "ق ➔ ك (Kâf - Kef / Mahreç Kayması)" to
            "Kalın Kâf (ق) harfini ince Kef (ك) gibi okudunuz. Sesi küçük dile yakın en arka damaktan tok bir şekilde çıkarın."
        ),
        ('ك' to 'ق') to (
            "ك ➔ ق (Kef - Kâf Karışıklığı)" to
            "İnce Kef (ك) harfini kalın Kâf (ق) gibi okudunuz. Sesi boğaza kaçırmadan orta damaktan ince çıkarın."
        ),

        // Peltek ve Keskin Harf Karışıklıkları
        ('ث' to 'س') to (
            "ث ➔ س (Peltek Sê - Keskin Sîn)" to
            "Peltek Sê (ث) harfini keskin Sîn (س) gibi okudunuz. Dil ucunu üst ön dişlerin ucuna hafifçe değdirerek ıslıksız çıkarın."
        ),
        ('ث' to 'ت') to (
            "ث ➔ ت (Peltek Sê - Tê)" to
            "Peltek Sê (ث) harfinde nefes akışı devam etmelidir; Tê (ت) gibi sesi tamamen kapatmayın."
        ),
        ('ذ' to 'ز') to (
            "ذ ➔ ز (Peltek Zâl - Keskin Zê)" to
            "Peltek Zâl (ذ) harfini keskin Zê (ز) gibi okudunuz. Dilinizin ucunu üst ön dişlerinize hafifçe değdirerek yumuşak çıkarın."
        ),
        ('ذ' to 'د') to (
            "ذ ➔ د (Peltek Zâl - Dâl)" to
            "Peltek Zâl (ذ) akıcı bir sestir; dil ucunu ön diş uçlarına değdirin, Dâl (د) gibi sert kapatmayın."
        ),
        ('ظ' to 'ز') to (
            "ظ ➔ ز (Kalın Peltek Zâ - İnce Keskin Zê)" to
            "Zâ (ظ) hem peltek hem kalın bir harftir; dil ucunu ön dişlere değdirin ve dil kökünü kaldırarak sesi kalınlaştırın."
        ),
        ('ظ' to 'ذ') to (
            "ظ ➔ ذ (Kalın Zâ - İnce Zâl)" to
            "Peltekliği doğru yaptınız ancak Zâ (ظ) harfini ince Zâl (ذ) gibi okudunuz. Dil kökünü kaldırarak sesi kalınlaştırın."
        ),

        // Dil Ucu, Dudak ve Diş Eti Harfleri
        ('ن' to 'ر') to (
            "ن ➔ ر (Nûn - Râ Mahreç / Kök Karışıklığı)" to
            "Nûn (ن) harfini Râ (ر) gibi okudunuz. Dil ucunu üst diş etlerine sabit değdirip genizden (gunneli) net bir 'N' sesi verin; dili titretmeyin."
        ),
        ('ر' to 'ن') to (
            "ر ➔ ن (Râ - Nûn Mahreç Karışıklığı)" to
            "Râ (ر) harfini Nûn (ن) gibi okudunuz. Sesi genze kaçırmadan dil ucunu üst damağa hafifçe değdirerek çıkarın."
        ),
        ('ن' to 'ل') to (
            "ن ➔ ل (Nûn - Lâm Karışıklığı)" to
            "Nûn (ن) harfini Lâm (ل) gibi okudunuz. Dil ucunu diş etine bastırıp hafif geniz (gunne) tınısıyla 'N' sesini netleştirin."
        ),
        ('ل' to 'ن') to (
            "ل ➔ ن (Lâm - Nûn Karışıklığı)" to
            "Lâm (ل) harfini genizden gelen Nûn (ن) gibi okudunuz. Geniz tınısı vermeden ağız içinden berrak bir 'L' sesi çıkarın."
        ),
        ('ر' to 'ل') to (
            "ر ➔ ل (Râ - Lâm Karışıklığı)" to
            "Râ (ر) harfini Lâm (ل) gibi okudunuz. Dil ucunu damağa yapıştırmayın; hafif titreşimle 'R' sesini belirginleştirin."
        ),
        ('ل' to 'ر') to (
            "ل ➔ ر (Lâm - Râ Karışıklığı)" to
            "Lâm (ل) harfini Râ (ر) gibi okudunuz. Dil ucunu titretmeden üst ön diş etlerine net bir şekilde değdirin."
        ),
        ('ب' to 'م') to (
            "ب ➔ م (Bê - Mîm Dudak Karışıklığı)" to
            "Bê (ب) harfini Mîm (م) gibi genizden okudunuz. Dudakları birbirine kapatıp hızla açarak patlamalı bir 'B' sesi çıkarın."
        ),
        ('م' to 'ب') to (
            "م ➔ ب (Mîm - Bê Dudak Karışıklığı)" to
            "Mîm (م) harfini Bê (ب) gibi sert patlattınız. Dudakları yumuşakça kapatıp sesi genizden (gunneli) akıtın."
        ),
        ('ف' to 'ب') to (
            "ف ➔ ب (Fê - Bê Dudak Karışıklığı)" to
            "Fê (ف) harfinde üst ön dişler alt dudağın iç kısmına değmeli ve nefes akmalıdır; iki dudağı kapatarak 'B' demeyin."
        ),
        ('س' to 'ش') to (
            "س ➔ ش (Sîn - Şîn Karışıklığı)" to
            "İnce ıslıklı Sîn (س) harfini yayvan Şîn (ش) gibi okudunuz. Dil ucunu alt ön dişlerin arkasına yaklaştırın."
        ),
        ('ش' to 'س') to (
            "ش ➔ س (Şîn - Sîn Karışıklığı)" to
            "Şîn (ش) harfini Sîn (س) gibi okudunuz. Dil ortasını üst damağa yaklaştırarak sesi ağız içinde yayın (tefeşşî)."
        ),
        ('ج' to 'ش') to (
            "ج ➔ ش (Cîm - Şîn Karışıklığı)" to
            "Cîm (ج) harfini gevşeterek Şîn (ش) gibi nefesli okudunuz. Dil ortasını damağa tam değdirerek tok bir 'C' sesi çıkarın."
        ),
        ('د' to 'ت') to (
            "د ➔ ت (Dâl - Tê Karışıklığı)" to
            "Cehr (ötümlü) olan Dâl (د) harfini nefesli Tê (ت) gibi okudunuz. Ses tellerini titreştirerek net bir 'D' sesi verin."
        ),
        ('ت' to 'د') to (
            "ت ➔ د (Tê - Dâl Karışıklığı)" to
            "İnce ve hafif nefesli (hems) olan Tê (ت) harfini sert Dâl (د) gibi okudunuz."
        )
    )

    private val CORE_A1_PHRASES = setOf(
        "السلام عليكم", "وعليكم السلام", "الحمد لله", "ان شاء الله", "ما شاء الله",
        "صباح الخير", "صباح النور", "مساء الخير", "مساء النور", "كيف حالك",
        "اهلا وسهلا", "اهلا بك", "مع السلامه", "الى اللقاء", "شكرا جزيلا",
        "عفوا", "من فضلك", "لو سمحت", "بارك الله فيك", "جزاك الله خيرا",
        "تصبح على خير", "فرصه سعيده", "كل عام وانتم بخير"
    )

    private val B1_CONNECTORS = setOf("الذي", "التي", "الذين", "لان", "عندما", "حيث", "بينما", "منذ", "لذلك", "لكي", "حتى", "كي")
    private val B2_CONNECTORS = setOf("بالرغم", "رغم", "لولا", "إنما", "انما", "كأن", "حيثما", "كيفما", "فضلا", "سواء", "بيد", "غير")
    private val C1_CONNECTORS = setOf("لاسيما", "مما", "حيلولة", "بموجب", "نظرا", "انطلاقا", "علاوة", "بصدد", "حيال", "ازاء")

    fun removeDiacritics(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text.replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
    }

    fun hasArabicDiacritics(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return Regex("[\\u064B-\\u0652\\u0670]").containsMatchIn(text)
    }

    fun cleanInvisibleMarks(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var res: String = text
        val marks = listOf("\u200e", "\u200f", "\u202a", "\u202b", "\u202c", "\u2066", "\u2067", "\u2068", "\u2069", "\ufeff")
        for (m in marks) {
            res = res.replace(m, "")
        }
        return res.trim()
    }

    fun sanitizeArabicSentence(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var cleaned = cleanInvisibleMarks(text).replace("\u0640", "")
        cleaned = cleaned.replace(Regex("\\(\\s*[\\d\\u0660-\\u0669.,:\\-%\\s]+\\s*\\)"), " ")
        cleaned = cleaned.replace(Regex("\\s+([؟،.!:])"), "$1")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    fun sanitizeTurkishSentence(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var cleaned = cleanInvisibleMarks(text)
        cleaned = cleaned.replace(Regex("([.!?\"'])\\s+\\d{1,4}\\s*$"), "$1")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    fun expandArabicVariants(expectedRaw: String?): List<String> {
        val cleaned = sanitizeArabicSentence(expectedRaw)
        if (cleaned.isEmpty()) return listOf("")
        if (!cleaned.contains("/")) return listOf(cleaned)
        val parts = cleaned.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        val combined = parts.joinToString(" ")
        return if (parts.size > 1) parts + combined else parts
    }

    fun normalizeArabic(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var t = sanitizeArabicSentence(text)
        t = removeDiacritics(t)
        t = t.replace(Regex("[إأآٱ]"), "ا")
        t = t.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "ء").replace("ئ", "ء")
        t = t.replace(Regex("[^\\u0600-\\u06FF\\s]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun phoneticSkeleton(word: String?): String {
        if (word.isNullOrEmpty()) return ""
        var w = removeDiacritics(cleanInvisibleMarks(word))
        w = w.replace(Regex("[إأآٱ]"), "ا")
        w = w.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "ء").replace("ئ", "ء")
        return w.replace(Regex("[^\\u0600-\\u06FF]"), "")
    }

    fun stripAl(text: String): String {
        return text.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { w ->
                if (w.startsWith("ال") && w.length > 3) w.substring(2) else w
            }
    }

    fun consonantSkeleton(skelWord: String): String {
        if (skelWord.isEmpty()) return ""
        val w = stripAl(skelWord)
        if (w.length <= 1) return w
        val tail = w.substring(1).filter { it !in MED_LETTERS }
        return w[0] + tail
    }

    fun collapseConsecutiveRepeats(text: String): String {
        if (text.isBlank()) return ""
        val words = text.trim().split(Regex("\\s+"))
        if (words.size <= 1) return text.trim()
        val deduped = mutableListOf(words[0])
        for (w in words.drop(1)) {
            if (stripAl(normalizeArabic(w)) != stripAl(normalizeArabic(deduped.last()))) {
                deduped.add(w)
            }
        }
        return deduped.joinToString(" ")
    }

    fun levenshtein(a: String, b: String): Int {
        var s1 = a
        var s2 = b
        if (s1.length < s2.length) {
            val tmp = s1
            s1 = s2
            s2 = tmp
        }
        if (s2.isEmpty()) return s1.length
        var prev = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            val cur = IntArray(s2.length + 1)
            cur[0] = i
            val ca = s1[i - 1]
            for (j in 1..s2.length) {
                val cb = s2[j - 1]
                val cost = if (ca != cb) 1 else 0
                cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[s2.length]
    }

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dist = levenshtein(a, b)
        return max(0.0, 1.0 - dist.toDouble() / max(a.length, b.length).toDouble())
    }

    private fun applyRootConsonantGuard(eWord: String, hWord: String, rawSim: Double): Double {
        if (rawSim >= 0.99 || rawSim <= 0.0) return rawSim
        val ewS = stripAl(phoneticSkeleton(eWord))
        val hwS = stripAl(phoneticSkeleton(hWord))
        if (ewS.isEmpty() || hwS.isEmpty()) return rawSim

        if (ewS.length <= 6) {
            val eCons = consonantSkeleton(ewS)
            val hCons = consonantSkeleton(hwS)
            if (eCons != hCons) {
                val consDist = levenshtein(eCons, hCons)
                val penalized = rawSim - (0.22 * consDist)
                return max(0.0, min(0.58, penalized))
            } else if (ewS != hwS && ewS.length <= 4) {
                return min(0.60, rawSim)
            }
        }
        return rawSim
    }

    fun compareArabic(expectedRaw: String, heardRaw: String): ComparisonResult {
        val hNorm = normalizeArabic(heardRaw)
        val hCollapsed = collapseConsecutiveRepeats(hNorm)
        val hVars = setOf(hNorm, stripAl(hNorm), hCollapsed, stripAl(hCollapsed)).filter { it.isNotEmpty() }
        val variants = expandArabicVariants(expectedRaw)

        var bestScore = 0.0
        var bestENorm = if (variants.isNotEmpty()) normalizeArabic(variants[0]) else ""

        for (cand in variants) {
            val eNorm = normalizeArabic(cand)
            val eVars = setOf(eNorm, stripAl(eNorm)).filter { it.isNotEmpty() }
            val isSingleWord = eNorm.split(Regex("\\s+")).filter { it.isNotEmpty() }.size == 1
            for (ev in eVars) {
                for (hv in hVars) {
                    var sc = similarity(ev, hv)
                    if (isSingleWord && hv.split(Regex("\\s+")).filter { it.isNotEmpty() }.size == 1) {
                        sc = applyRootConsonantGuard(ev, hv, sc)
                    }
                    if (sc > bestScore) {
                        bestScore = sc
                        bestENorm = eNorm
                    }
                }
            }
        }
        return ComparisonResult(bestScore, bestENorm, hNorm)
    }

    private fun evaluateSingleVariantWords(
        cleanedExp: String,
        heardWords: List<String>,
        wordThreshold: Double = DEFAULT_WORD_SIMILARITY_THRESHOLD
    ): SentenceWordsEvaluation {
        val expWords = cleanedExp.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (expWords.isEmpty()) return SentenceWordsEvaluation(emptyList(), 1.0)

        val results = mutableListOf<Pair<String, Boolean>>()
        val usedHeardIndices = mutableSetOf<Int>()
        var matchedCount = 0
        var evalWordCount = 0
        val isSingleTarget = expWords.size == 1

        for (ew in expWords) {
            val ewNorm = normalizeArabic(ew)
            if (ewNorm.isEmpty()) {
                results.add(ew to true)
                continue
            }
            evalWordCount++
            val ewStripped = stripAl(ewNorm)
            var bestSim = 0.0
            var bestIdx = -1

            for ((idx, hw) in heardWords.withIndex()) {
                if (idx in usedHeardIndices) continue
                val hwStripped = stripAl(hw)
                var sim = max(similarity(ewNorm, hw), similarity(ewStripped, hwStripped))
                if (isSingleTarget || ewStripped.length <= 4) {
                    sim = applyRootConsonantGuard(ewStripped, hwStripped, sim)
                }
                if (sim > bestSim) {
                    bestSim = sim
                    bestIdx = idx
                }
            }

            val isWordOk = bestSim >= wordThreshold
            if (isWordOk && bestIdx != -1) {
                usedHeardIndices.add(bestIdx)
                matchedCount++
            }
            results.add(ew to isWordOk)
        }

        val ratio = if (evalWordCount > 0) matchedCount.toDouble() / evalWordCount.toDouble() else 1.0
        return SentenceWordsEvaluation(results, ratio)
    }

    fun evaluateSentenceWords(
        expectedRaw: String,
        heardRaw: String,
        wordThreshold: Double = DEFAULT_WORD_SIMILARITY_THRESHOLD
    ): SentenceWordsEvaluation {
        val heardNorm = collapseConsecutiveRepeats(normalizeArabic(heardRaw))
        val heardWords = heardNorm.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val variants = expandArabicVariants(expectedRaw)

        var bestResults = emptyList<Pair<String, Boolean>>()
        var bestRatio = -1.0

        for (cand in variants) {
            val eval = evaluateSingleVariantWords(cand, heardWords, wordThreshold)
            if (eval.ratio > bestRatio) {
                bestRatio = eval.ratio
                bestResults = eval.wordResults
            }
        }
        return SentenceWordsEvaluation(bestResults, max(0.0, bestRatio))
    }

    fun analyzePhonemeAndMakhrajErrors(expectedRaw: String, heardRaw: String): List<MakhrajTip> {
        if (expectedRaw.isBlank() || heardRaw.isBlank()) return emptyList()

        val variants = expandArabicVariants(expectedRaw)
        var bestVariant = variants.first()
        var bestVScore = -1.0
        for (cand in variants) {
            val sc = compareArabic(cand, heardRaw).score
            if (sc > bestVScore) {
                bestVScore = sc
                bestVariant = cand
            }
        }

        val expWords = sanitizeArabicSentence(bestVariant).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val heardCleaned = if (expWords.size == 1) {
            collapseConsecutiveRepeats(sanitizeArabicSentence(heardRaw))
        } else {
            sanitizeArabicSentence(heardRaw)
        }
        val heardWords = heardCleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (expWords.isEmpty() || heardWords.isEmpty()) return emptyList()

        val feedbackList = mutableListOf<MakhrajTip>()
        val seenTitles = mutableSetOf<String>()
        val usedHIdx = mutableSetOf<Int>()
        val isSingleWordMode = expWords.size == 1

        for (ew in expWords) {
            val eSkel = phoneticSkeleton(ew)
            if (eSkel.isEmpty()) continue

            var bestHWord = ""
            var bestHSkel = ""
            var bestSim = -1.0
            var bestIdx = -1

            for ((idx, hw) in heardWords.withIndex()) {
                if (idx in usedHIdx) continue
                val hSkel = phoneticSkeleton(hw)
                if (hSkel.isEmpty()) continue
                val sim = max(similarity(eSkel, hSkel), similarity(stripAl(eSkel), stripAl(hSkel)))
                if (sim > bestSim) {
                    bestSim = sim
                    bestHWord = hw
                    bestHSkel = hSkel
                    bestIdx = idx
                }
            }

            if (bestIdx == -1) continue

            val eCmp = if (eSkel.startsWith("ال") && !bestHSkel.startsWith("ال")) stripAl(eSkel) else eSkel
            val hCmp = if (bestHSkel.startsWith("ال") && !eSkel.startsWith("ال")) stripAl(bestHSkel) else bestHSkel

            if (eCmp == hCmp) {
                usedHIdx.add(bestIdx)
                continue
            }

            val minSimThreshold = when {
                isSingleWordMode -> 0.45
                eCmp.length <= 4 -> 0.58
                else -> 0.50
            }

            if (bestSim >= minSimThreshold) {
                usedHIdx.add(bestIdx)
                val eCons = consonantSkeleton(eCmp)
                val hCons = consonantSkeleton(hCmp)
                val consSim = similarity(eCons, hCons)

                // Character-by-character alignment inspection
                val minLen = min(eCmp.length, hCmp.length)
                for (i in 0 until minLen) {
                    val ec = eCmp[i]
                    val hc = hCmp[i]
                    if (ec == hc) continue

                    val pairKey = ec to hc
                    if (MAKHRAJ_COACH_MAP.containsKey(pairKey)) {
                        val (title, tip) = MAKHRAJ_COACH_MAP.getValue(pairKey)
                        val fullTitle = "🎯 $title"
                        if (seenTitles.add(fullTitle)) {
                            feedbackList.add(MakhrajTip(fullTitle, tip, ew, bestHWord))
                        }
                    } else if (ec in MED_LETTERS && hc in MED_LETTERS && consSim >= 0.75) {
                        val title = "🔄 Vezin / Med Harfi Değişimi ($ec ➔ $hc)"
                        if (seenTitles.add(title)) {
                            feedbackList.add(
                                MakhrajTip(
                                    title,
                                    "'$ew' kelimesindeki '$ec' sesini '$hc' gibi okudunuz ('$bestHWord' algılandı). Harfin ünlü/vezin yönüne dikkat edin.",
                                    ew,
                                    bestHWord
                                )
                            )
                        }
                    } else if (eCmp.length == hCmp.length) {
                        val title = "🎯 Harf / Kök Değişimi ($ec ➔ $hc)"
                        if (seenTitles.add(title)) {
                            feedbackList.add(
                                MakhrajTip(
                                    title,
                                    "'$ew' kelimesindeki '$ec' harfi yerine '$hc' sesi algılandı ('$bestHWord'). Tek bir kök harfin değişmesi anlamı tamamen değiştirir; '$ec' harfini net çıkarın.",
                                    ew,
                                    bestHWord
                                )
                            )
                        }
                    }
                }

                // Check for missing letters (Med / Wazn / Root consonant deletion)
                if (eCmp.length > hCmp.length) {
                    val missingChars = eCmp.toMutableList()
                    for (ch in hCmp) {
                        missingChars.remove(ch)
                    }
                    for (dc in missingChars) {
                        if (dc in MED_LETTERS && eCons == hCons) {
                            if (dc == 'ا' && eCmp.length == 4 && hCmp.length == 3 && hCons.length == 3) {
                                val title = "🔄 Sarf Kalıbı / Vezin Farklılığı ($ew ➔ $bestHWord)"
                                if (seenTitles.add(title)) {
                                    feedbackList.add(
                                        MakhrajTip(
                                            title,
                                            "'$ew' ismini/sıfatını '$bestHWord' (fiil/kök) kalıbında okudunuz. '$dc' uzatmasını koruyarak kelime kalıbını bozmayın.",
                                            ew,
                                            bestHWord
                                        )
                                    )
                                }
                            } else {
                                val title = "📏 Med (Uzatma) Eksikliği ($dc)"
                                if (seenTitles.add(title)) {
                                    feedbackList.add(
                                        MakhrajTip(
                                            title,
                                            "'$ew' kelimesindeki '$dc' uzatma (med) harfini kısa okudunuz. Sesi bir elif miktarı uzatın.",
                                            ew,
                                            bestHWord
                                        )
                                    )
                                }
                            }
                        } else if (dc == 'ع') {
                            val title = "🎯 ع (Ayn) Harfi Yutuldu"
                            if (seenTitles.add(title)) {
                                feedbackList.add(
                                    MakhrajTip(
                                        title,
                                        "'$ew' kelimesindeki Ayn (ع) harfi duyulmadı. Boğaz ortasını hafifçe sıkarak harfi belirginleştirin.",
                                        ew,
                                        bestHWord
                                    )
                                )
                            }
                        } else if (dc !in MED_LETTERS) {
                            val title = "🎯 Eksik / Yutulan Harf ($dc)"
                            if (seenTitles.add(title)) {
                                feedbackList.add(
                                    MakhrajTip(
                                        title,
                                        "'$ew' kelimesindeki '$dc' harfi sesinizde duyulmadı ('$bestHWord' algılandı). Harfi yutmadan net telaffuz edin.",
                                        ew,
                                        bestHWord
                                    )
                                )
                            }
                        }
                    }
                } else if (hCmp.length > eCmp.length) {
                    val insertedChars = hCmp.toMutableList()
                    for (ch in eCmp) {
                        insertedChars.remove(ch)
                    }
                    for (ic in insertedChars) {
                        if (ic in MED_LETTERS && eCons == hCons) {
                            val title = "📏 Gereksiz Uzatma / Hareke Hatası ($ic)"
                            if (seenTitles.add(title)) {
                                feedbackList.add(
                                    MakhrajTip(
                                        title,
                                        "'$ew' kelimesindeki kısa harekeyi fazla uzatarak '$ic' med harfine dönüştürdünüz. Harekeyi uzatmadan net okuyun.",
                                        ew,
                                        bestHWord
                                    )
                                )
                            }
                        } else if (ic !in MED_LETTERS) {
                            val title = "🎯 Fazladan Harf Algılandı ($ic)"
                            if (seenTitles.add(title)) {
                                feedbackList.add(
                                    MakhrajTip(
                                        title,
                                        "'$ew' kelimesini okurken fazladan '$ic' sesi algılandı ('$bestHWord').",
                                        ew,
                                        bestHWord
                                    )
                                )
                            }
                        }
                    }
                }

                if (feedbackList.isEmpty() && eCmp != hCmp) {
                    val title = "⚠️ Kelime / Kök Farklılığı ($ew ➔ $bestHWord)"
                    if (seenTitles.add(title)) {
                        feedbackList.add(
                            MakhrajTip(
                                title,
                                "Beklenen '$ew' kelimesi yerine '$bestHWord' algılandı. Kelimenin kök harflerine ve hareke akışına dikkat ederek tekrar okuyun.",
                                ew,
                                bestHWord
                            )
                        )
                    }
                }

                if (ew.contains('\u0651') && bestSim < 0.88 && seenTitles.add("⚡ Şedde (Teşdîd) Vurgusu")) {
                    feedbackList.add(
                        MakhrajTip(
                            "⚡ Şedde (ّ ) Vurgusuna Dikkat",
                            "'$ew' kelimesinde şeddeli harf bulunuyor. Harfin mahrecinde kısa bir süre tutarak çift ses vurgusu yapın.",
                            ew,
                            bestHWord
                        )
                    )
                }
            } else if (isSingleWordMode && bestHWord.isNotEmpty()) {
                val title = "⚠️ Tamamen Farklı Kelime Algılandı ($ew ➔ $bestHWord)"
                if (seenTitles.add(title)) {
                    feedbackList.add(
                        MakhrajTip(
                            title,
                            "Beklenen '$ew' ifadesi yerine '$bestHWord' duyuldu. Önce '🔊 Dinle' butonuna basıp doğru telaffuzu dinleyin.",
                            ew,
                            bestHWord
                        )
                    )
                }
            }
        }

        return feedbackList.take(3)
    }

    fun buildHarakaGuide(arabicText: String?): String {
        if (arabicText.isNullOrBlank()) return ""
        val primary = expandArabicVariants(arabicText).firstOrNull() ?: return ""
        val fathaCnt = primary.count { it == '\u064e' || it == '\u064b' }
        val kasraCnt = primary.count { it == '\u0650' || it == '\u064d' }
        val dammaCnt = primary.count { it == '\u064f' || it == '\u064c' }
        val shaddaCnt = primary.count { it == '\u0651' }
        val sukunCnt = primary.count { it == '\u0652' }

        val parts = mutableListOf<String>()
        if (fathaCnt > 0) parts.add("Fetha (ـَ a/e): $fathaCnt")
        if (kasraCnt > 0) parts.add("Kesra (ـِ i): $kasraCnt")
        if (dammaCnt > 0) parts.add("Damme (ـُ u/ü): $dammaCnt")
        if (shaddaCnt > 0) parts.add("Şedde (ـّ çift vurgu): $shaddaCnt")
        if (sukunCnt > 0) parts.add("Cezim/Sükûn (ـْ ): $sukunCnt")

        if (parts.isEmpty()) return ""
        return parts.joinToString(" • ") + "\n💡 Kısa harekeleri uzatmadan (med yapmadan) net ve keskin seslendirin."
    }

    private fun detectMorphologicalComplexity(normWord: String): Int {
        val w = stripAl(normWord)
        if (w.length <= 4) return 0
        if (w.startsWith("است") && w.length >= 6) return 2
        if (w.endsWith("يه") && w.length >= 6) return 2
        if ((w.startsWith("ان") || w.startsWith("ت")) && w.length >= 6) return 1
        if (w.length >= 7) return 2
        return if (w.length >= 5) 1 else 0
    }

    fun estimateWordCefr(arapca: String, gorsel: String? = null): String {
        val primaryAr = expandArabicVariants(arapca).firstOrNull() ?: ""
        val norm = normalizeArabic(primaryAr)
        if (norm.isEmpty()) return "A1"
        if (norm in CORE_A1_PHRASES) return "A1"

        val words = norm.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wCount = words.size
        val morphScore = words.sumOf { detectMorphologicalComplexity(it) }
        val strippedLen = stripAl(norm).replace(" ", "").length

        return when (wCount) {
            1 -> when {
                !gorsel.isNullOrBlank() && strippedLen <= 5 && morphScore <= 1 -> "A1"
                strippedLen <= 4 && morphScore == 0 -> "A1"
                strippedLen <= 5 && morphScore <= 1 -> "A2"
                strippedLen <= 6 && morphScore <= 1 -> "B1"
                strippedLen <= 7 || morphScore == 2 -> "B2"
                else -> "C1"
            }
            2 -> when {
                strippedLen <= 7 && morphScore == 0 -> "A1"
                strippedLen <= 9 && morphScore <= 1 -> "A2"
                strippedLen <= 12 && morphScore <= 2 -> "B1"
                morphScore <= 3 -> "B2"
                else -> "C1"
            }
            else -> when {
                norm in CORE_A1_PHRASES -> "A1"
                strippedLen <= 13 -> "B1"
                strippedLen <= 18 -> "B2"
                else -> "C1"
            }
        }
    }

    fun estimateSentenceCefr(arapca: String): String {
        val norm = normalizeArabic(arapca)
        if (norm.isEmpty()) return "A1"
        if (norm in CORE_A1_PHRASES) return "A1"

        val words = norm.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wCount = words.size
        val baseChars = removeDiacritics(arapca).trim().length

        for (phrase in CORE_A1_PHRASES) {
            if (norm.startsWith(phrase) && wCount <= 5) return "A1"
        }

        var complexityBonus = 0.0
        for (w in words) {
            when (w) {
                in C1_CONNECTORS -> complexityBonus += 4.0
                in B2_CONNECTORS -> complexityBonus += 2.8
                in B1_CONNECTORS -> complexityBonus += 1.8
            }
            complexityBonus += detectMorphologicalComplexity(w) * 0.45
        }

        val effectiveScore = wCount + complexityBonus
        return when {
            effectiveScore <= 4.5 && baseChars <= 25 -> "A1"
            effectiveScore <= 7.5 && baseChars <= 44 -> "A2"
            effectiveScore <= 11.5 && baseChars <= 70 -> "B1"
            effectiveScore <= 16.5 && baseChars <= 100 -> "B2"
            else -> "C1"
        }
    }

    fun estimateDialogueCefr(arabicLines: List<String>): String {
        if (arabicLines.isEmpty()) return "A1"
        val levelWeights = mapOf("A1" to 1.0, "A2" to 2.0, "B1" to 3.0, "B2" to 4.0, "C1" to 5.0)
        var totalWeight = 0.0
        var validLines = 0
        for (line in arabicLines) {
            if (line.isBlank()) continue
            validLines++
            val lvl = estimateSentenceCefr(line)
            totalWeight += levelWeights[lvl] ?: 1.0
        }
        if (validLines == 0) return "A1"
        val avg = totalWeight / validLines
        return when {
            avg <= 1.45 -> "A1"
            avg <= 2.25 -> "A2"
            avg <= 3.10 -> "B1"
            avg <= 3.95 -> "B2"
            else -> "C1"
        }
    }

    fun checkItemQuality(arapca: String, turkce: String): List<String> {
        val issues = mutableListOf<String>()
        val arClean = cleanInvisibleMarks(arapca)
        val trClean = cleanInvisibleMarks(turkce)
        if (arClean.isEmpty()) {
            issues.add("Arapça metin boş")
        } else {
            if (!hasArabicDiacritics(arClean)) {
                issues.add("Hareke eksik (Harekesiz metin)")
            }
            if (Regex("\\(\\s*\\d+\\s*\\)").containsMatchIn(arClean)) {
                issues.add("RTL bozan parantez içi rakam var")
            }
        }
        if (trClean.isEmpty()) {
            issues.add("Türkçe çeviri eksik")
        } else if (Regex("[.!?]\\s+\\d{1,4}\\s*$").containsMatchIn(trClean)) {
            issues.add("Türkçe sonunda sayfa/dipnot numarası kalmış")
        }
        return issues
    }
}
