package com.example.data

import com.example.nlp.ArabicNlpEngine
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MuallimunRepository(private val dao: MuallimunDao) {

    val allWordsFlow: Flow<List<WordEntity>> = dao.observeAllWords()
    val allSozlukFlow: Flow<List<SozlukEntity>> = dao.observeAllSozluk()
    val allDialoguesFlow: Flow<List<DialogueEntity>> = dao.observeAllDialogues()
    val allDialogueLinesFlow: Flow<List<DialogueLineEntity>> = dao.observeAllDialogueLines()
    val allSentencesFlow: Flow<List<SentenceEntity>> = dao.observeAllSentences()
    val allConfigFlow: Flow<List<ConfigEntity>> = dao.observeAllConfig()

    fun observePackages(mode: String): Flow<List<PackageEntity>> = dao.observePackagesByMode(mode)

    suspend fun ensureSeeded(context: android.content.Context? = null) {
        if (context != null) {
            val visualOrRegularWords = dao.getAllWords().count { !it.id.startsWith("szl_") }
            if (visualOrRegularWords < 100) {
                com.example.util.DesktopDatabaseImporter.importSingleAssetDbIfPresent(context, "kelimeler.db", dao)
            }
            val dictCount = dao.getAllSozluk().size
            if (dictCount < 100) {
                com.example.util.DesktopDatabaseImporter.importSingleAssetDbIfPresent(context, "sozluk.db", dao)
            }
            val dialogueCount = dao.getAllDialogues().size
            if (dialogueCount < 10) {
                com.example.util.DesktopDatabaseImporter.importSingleAssetDbIfPresent(context, "diyaloglar.db", dao)
            }
            val sentenceCount = dao.getAllSentences().size
            if (sentenceCount < 100) {
                com.example.util.DesktopDatabaseImporter.importSingleAssetDbIfPresent(context, "cumleler.db", dao)
            }
        }

        // Yalnızca ilgili tablo hâlâ tamamen boşsa (assets/ içinden yüklenmemişse) InitialSeedData yedeğini ekle
        if (dao.getAllWords().isEmpty()) {
            dao.insertPackages(InitialSeedData.initialPackages.filter { it.mode == "words" })
            val allWords = InitialSeedData.initialVisualWords + InitialSeedData.buildSozlukWordCards()
            dao.insertWords(allWords)
        }
        if (dao.getAllSozluk().isEmpty()) {
            dao.insertSozlukList(InitialSeedData.initialSozlukEntries)
        }
        if (dao.getAllDialogues().isEmpty()) {
            dao.insertDialogues(InitialSeedData.initialDialogues)
            dao.insertDialogueLines(InitialSeedData.initialDialogueLines)
        }
        if (dao.getAllSentences().isEmpty()) {
            dao.insertPackages(InitialSeedData.initialPackages.filter { it.mode == "sentences" })
            dao.insertSentences(InitialSeedData.initialSentences)
        }
    }

    suspend fun saveOrUpdatePackage(pkg: PackageEntity) {
        dao.insertPackage(pkg)
    }

    suspend fun saveOrUpdateDialogue(dialogue: DialogueEntity) {
        dao.insertDialogue(dialogue)
    }

    suspend fun importDesktopDatabaseUri(
        context: android.content.Context,
        uri: android.net.Uri,
        fileNameHint: String,
        clearExisting: Boolean = false
    ): ImportReport {
        return com.example.util.DesktopDatabaseImporter.importAnyFileUri(
            context = context,
            uri = uri,
            fileNameHint = fileNameHint,
            dao = dao,
            clearExisting = clearExisting
        )
    }

    suspend fun getConfig(key: String, default: String = ""): String {
        return dao.getConfigVal(key) ?: default
    }

    suspend fun setConfig(key: String, value: String) {
        dao.setConfig(ConfigEntity(key, value))
    }

    private fun todayIso(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun addDaysIso(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    // --- SM-2 SPACED REPETITION SCHEDULE ---
    fun computeSm2Schedule(curInterval: Int, curEase: Double, isCorrect: Boolean): Triple<Int, Double, String> {
        val iv = max(0, curInterval)
        val ef = if (curEase <= 0.0) 2.5 else curEase
        return if (isCorrect) {
            val newIv = when {
                iv <= 0 -> 1
                iv == 1 -> 3
                else -> min(180, max(2, (iv * ef).roundToInt()))
            }
            val newEf = min(3.0, ((ef + 0.10) * 100.0).roundToInt() / 100.0)
            Triple(newIv, newEf, addDaysIso(newIv))
        } else {
            val newEf = max(1.3, ((ef - 0.20) * 100.0).roundToInt() / 100.0)
            Triple(1, newEf, todayIso())
        }
    }

    suspend fun recordPracticeOutcome(itemId: String, mode: String, isCorrect: Boolean) {
        val today = todayIso()
        when (mode) {
            "words" -> {
                val word = dao.getWordById(itemId) ?: return
                val (newIv, newEf, nextRev) = computeSm2Schedule(word.intervalDays, word.easeFactor, isCorrect)
                val updated = word.copy(
                    dogru = if (isCorrect) word.dogru + 1 else word.dogru,
                    yanlis = if (!isCorrect) word.yanlis + 1 else word.yanlis,
                    isLearned = if (isCorrect) 1 else word.isLearned,
                    lastPracticed = today,
                    intervalDays = newIv,
                    easeFactor = newEf,
                    nextReview = nextRev
                )
                dao.insertWord(updated)
            }
            "sentences" -> {
                val sent = dao.getSentenceById(itemId) ?: return
                val (newIv, newEf, nextRev) = computeSm2Schedule(sent.intervalDays, sent.easeFactor, isCorrect)
                val updated = sent.copy(
                    dogru = if (isCorrect) sent.dogru + 1 else sent.dogru,
                    yanlis = if (!isCorrect) sent.yanlis + 1 else sent.yanlis,
                    isLearned = if (isCorrect) 1 else sent.isLearned,
                    lastPracticed = today,
                    intervalDays = newIv,
                    easeFactor = newEf,
                    nextReview = nextRev
                )
                dao.insertSentence(updated)
            }
            "dialogue" -> {
                val cleanId = itemId.removePrefix("dlgline_").toIntOrNull() ?: return
                val line = dao.getDialogueLineById(cleanId) ?: return
                val updated = line.copy(
                    dogru = if (isCorrect) line.dogru + 1 else line.dogru,
                    yanlis = if (!isCorrect) line.yanlis + 1 else line.yanlis
                )
                dao.insertDialogueLine(updated)
            }
        }
        recordDailyActivity(isCorrect)
    }

    suspend fun recordDailyActivity(isCorrect: Boolean) {
        val today = todayIso()
        val lastDate = getConfig("last_active_date", "")
        var streak = getConfig("streak_days", "0").toIntOrNull() ?: 0
        var dailyCnt = getConfig("daily_count", "0").toIntOrNull() ?: 0
        var totalPts = getConfig("total_points", "0").toIntOrNull() ?: 0

        if (lastDate == today) {
            dailyCnt += 1
        } else {
            val yesterday = addDaysIso(-1)
            streak = if (lastDate == yesterday) streak + 1 else 1
            dailyCnt = 1
            setConfig("last_active_date", today)
            setConfig("streak_days", streak.toString())
        }
        totalPts += if (isCorrect) 10 else 3
        setConfig("daily_count", dailyCnt.toString())
        setConfig("total_points", totalPts.toString())
    }

    suspend fun toggleWordFavorite(wordId: String) {
        val w = dao.getWordById(wordId) ?: return
        dao.insertWord(w.copy(isFav = if (w.isFav == 1) 0 else 1))
    }

    suspend fun toggleWordLearned(wordId: String) {
        val w = dao.getWordById(wordId) ?: return
        dao.insertWord(w.copy(isLearned = if (w.isLearned == 1) 0 else 1))
    }

    suspend fun updateWordLevel(wordId: String, level: String) {
        val w = dao.getWordById(wordId) ?: return
        dao.insertWord(w.copy(seviye = level))
    }

    suspend fun toggleSentenceFavorite(sentId: String) {
        val s = dao.getSentenceById(sentId) ?: return
        dao.insertSentence(s.copy(isFav = if (s.isFav == 1) 0 else 1))
    }

    suspend fun toggleSentenceLearned(sentId: String) {
        val s = dao.getSentenceById(sentId) ?: return
        dao.insertSentence(s.copy(isLearned = if (s.isLearned == 1) 0 else 1))
    }

    suspend fun updateSentenceLevel(sentId: String, level: String) {
        val s = dao.getSentenceById(sentId) ?: return
        dao.insertSentence(s.copy(seviye = level))
    }

    // --- DAILY 15 SMART REVIEW (SM-2) ---
    fun buildDailySmartReviewWords(allWords: List<WordEntity>, limit: Int = 15): List<WordEntity> {
        val today = todayIso()
        val selected = mutableListOf<WordEntity>()
        val seenIds = mutableSetOf<String>()

        fun addBatch(candidates: List<WordEntity>, maxTake: Int) {
            for (c in candidates.take(maxTake)) {
                if (selected.size >= limit) break
                if (seenIds.add(c.id)) selected.add(c)
            }
        }

        // 1. Due for SM-2 review today or earlier
        val due = allWords.filter { it.nextReview.isNotBlank() && it.nextReview <= today }
            .sortedWith(compareBy<WordEntity> { it.nextReview }.thenByDescending { it.yanlis })
        addBatch(due, 8)

        // 2. Difficult words
        val difficult = allWords.filter { it.yanlis > 0 }
            .sortedByDescending { it.yanlis - it.dogru }
        addBatch(difficult, 6)

        // 3. Favorites
        val favs = allWords.filter { it.isFav == 1 }.shuffled()
        addBatch(favs, 5)

        // 4. Unpracticed words
        val unpracticed = allWords.filter { it.dogru == 0 && it.yanlis == 0 }.shuffled()
        addBatch(unpracticed, limit)

        // 5. Fallback any
        addBatch(allWords.shuffled(), limit)
        return selected
    }

    fun buildDailySmartReviewSentences(allSentences: List<SentenceEntity>, limit: Int = 15): List<SentenceEntity> {
        val today = todayIso()
        val selected = mutableListOf<SentenceEntity>()
        val seenIds = mutableSetOf<String>()

        fun addBatch(candidates: List<SentenceEntity>, maxTake: Int) {
            for (c in candidates.take(maxTake)) {
                if (selected.size >= limit) break
                if (seenIds.add(c.id)) selected.add(c)
            }
        }

        val due = allSentences.filter { it.nextReview.isNotBlank() && it.nextReview <= today }
            .sortedWith(compareBy<SentenceEntity> { it.nextReview }.thenByDescending { it.yanlis })
        addBatch(due, 8)

        val difficult = allSentences.filter { it.yanlis > 0 }
            .sortedByDescending { it.yanlis - it.dogru }
        addBatch(difficult, 6)

        val favs = allSentences.filter { it.isFav == 1 }.shuffled()
        addBatch(favs, 5)

        val unpracticed = allSentences.filter { it.dogru == 0 && it.yanlis == 0 }.shuffled()
        addBatch(unpracticed, limit)

        addBatch(allSentences.shuffled(), limit)
        return selected
    }

    // --- MORPHOLOGY & SARF ROOT FAMILY ---
    fun resolveWordMorphology(
        wordIdOrArabic: String,
        allSozluk: List<SozlukEntity>
    ): WordMorphologyInfo? {
        if (wordIdOrArabic.isBlank() || allSozluk.isEmpty()) return null
        val cleanId = wordIdOrArabic.removePrefix("szl_")

        var match = allSozluk.firstOrNull { it.wordId == wordIdOrArabic || it.id == cleanId }
        if (match == null) {
            val normQ = ArabicNlpEngine.stripAl(
                ArabicNlpEngine.normalizeArabic(
                    ArabicNlpEngine.expandArabicVariants(wordIdOrArabic).firstOrNull() ?: ""
                )
            )
            if (normQ.isNotEmpty()) {
                match = allSozluk.firstOrNull { s ->
                    listOf(s.mufret, s.cemi, s.mazi, s.muzari, s.masdar).any { f ->
                        f.isNotBlank() && ArabicNlpEngine.stripAl(
                            ArabicNlpEngine.normalizeArabic(
                                ArabicNlpEngine.expandArabicVariants(f).firstOrNull() ?: ""
                            )
                        ) == normQ
                    }
                }
            }
        }
        val entry = match ?: return null

        val forms = mutableListOf<MorphFormItem>()
        if (entry.tur == "isim") {
            if (entry.mufret.isNotBlank() && entry.mufret != "-") {
                forms.add(MorphFormItem("Müfret (Tekil)", entry.mufret, "mufret", 0xFF38BDF8))
            }
            if (entry.cemi.isNotBlank() && entry.cemi != "-") {
                forms.add(MorphFormItem("Cemî (Çoğul)", entry.cemi, "cemi", 0xFFA78BFA))
            }
        } else {
            if (entry.mazi.isNotBlank() && entry.mazi != "-") {
                forms.add(MorphFormItem("Mâzî (Geçmiş)", entry.mazi, "mazi", 0xFFF87171))
            }
            if (entry.muzari.isNotBlank() && entry.muzari != "-") {
                forms.add(MorphFormItem("Muzâri (Geniş/Şimdiki)", entry.muzari, "muzari", 0xFF60A5FA))
            }
            if (entry.masdar.isNotBlank() && entry.masdar != "-") {
                forms.add(MorphFormItem("Masdar (İsim Fiil)", entry.masdar, "masdar", 0xFF34D399))
            }
        }

        val family = if (entry.kok.isNotBlank() && entry.kok != "-") {
            allSozluk.filter { it.kok == entry.kok && it.id != entry.id }.take(8)
        } else {
            emptyList()
        }

        return WordMorphologyInfo(entry, forms, family)
    }

    // --- LISTENING EXAM (DİNLE & BUL) GENERATOR ---
    fun generateListeningExamQuestions(
        sourceMode: String, // "words", "dictionary", "sentences", "dialogue"
        paketId: String,
        seviye: String,
        count: Int,
        allWords: List<WordEntity>,
        allSentences: List<SentenceEntity>,
        allDialogues: List<DialogueEntity>,
        allDialogueLines: List<DialogueLineEntity>
    ): List<ListeningQuestion> {
        data class RawCandidate(val id: String, val ar: String, val tr: String, val mode: String, val lvl: String)

        val candidates = when (sourceMode) {
            "words" -> allWords.filter { !it.paketId.startsWith("szl_") }
                .filter { paketId == "all" || it.paketId == paketId }
                .filter { seviye == "Tümü" || it.seviye == seviye }
                .map { RawCandidate(it.id, it.arapca, it.turkce, "words", it.seviye) }

            "dictionary" -> allWords.filter { it.paketId.startsWith("szl_") }
                .filter { paketId == "all" || it.paketId == paketId }
                .filter { seviye == "Tümü" || it.seviye == seviye }
                .map { RawCandidate(it.id, it.arapca, it.turkce, "words", it.seviye) }

            "dialogue" -> {
                val dlgLvlMap = allDialogues.associate { it.id to it.seviye }
                allDialogueLines
                    .filter { paketId == "all" || it.diyalogId == paketId }
                    .filter {
                        val dlvl = dlgLvlMap[it.diyalogId] ?: "A1"
                        seviye == "Tümü" || dlvl == seviye
                    }
                    .map {
                        RawCandidate(
                            "dlgline_${it.id}",
                            it.arapca,
                            it.turkce,
                            "dialogue",
                            dlgLvlMap[it.diyalogId] ?: "A1"
                        )
                    }
            }

            else -> allSentences
                .filter { paketId == "all" || it.paketId == paketId }
                .filter { seviye == "Tümü" || it.seviye == seviye }
                .map { RawCandidate(it.id, it.arapca, it.turkce, "sentences", it.seviye) }
        }.filter { it.ar.isNotBlank() && it.tr.isNotBlank() }

        val distinctTr = candidates.map { it.tr }.distinct()
        if (candidates.size < 4 || distinctTr.size < 4) return emptyList()

        val targets = mutableListOf<RawCandidate>()
        while (targets.size < count) {
            val need = count - targets.size
            val batch = candidates.shuffled().take(need)
            targets.addAll(batch)
        }

        return targets.mapNotNull { target ->
            val distractors = distinctTr.filter { it != target.tr }.shuffled().take(3)
            if (distractors.size < 3) null
            else {
                val opts = (distractors + target.tr).shuffled()
                ListeningQuestion(
                    id = target.id,
                    arapca = target.ar,
                    correctTr = target.tr,
                    options = opts,
                    mode = target.mode,
                    seviye = target.lvl
                )
            }
        }
    }

    // --- EXCEL / CLIPBOARD & CSV IMPORT ENGINE ---
    suspend fun importFromClipboardOrCsv(
        mode: String,
        rawText: String,
        paketId: String,
        paketAdi: String,
        defaultSeviye: String = "A1",
        clearExisting: Boolean = false
    ): ImportReport {
        val skippedDetails = mutableListOf<String>()
        var added = 0

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ImportReport(0, 0, listOf("Metin alanı boş."))

        val sampleBlock = lines.take(10).joinToString("\n")
        val delim = when {
            sampleBlock.contains("\t") -> "\t"
            sampleBlock.contains(";") -> ";"
            else -> ","
        }

        val cleanPid = paketId.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "ozel_paket" }
        val cleanPad = paketAdi.ifBlank { cleanPid.replace("_", " ").replaceFirstChar { it.uppercase() } }
        val defLvl = defaultSeviye.uppercase()
        val tsBase = System.currentTimeMillis()

        when (mode) {
            "words" -> {
                if (clearExisting) dao.deleteAllWords()
                dao.insertPackage(PackageEntity(cleanPid, cleanPad, "words", defLvl, "Genel"))
                val wordsToInsert = mutableListOf<WordEntity>()
                for ((idx, line) in lines.withIndex()) {
                    val lineNo = idx + 1
                    val cells = line.split(delim).map { it.trim() }
                    if (lineNo == 1 && cells.firstOrNull()?.lowercase()?.let {
                            it.contains("id") || it.contains("arapca") || it.contains("kelime")
                        } == true
                    ) continue

                    if (cells.size < 2) {
                        skippedDetails.add("Satır $lineNo (En az 2 sütun [Arapça ve Türkçe] olmalı)")
                        continue
                    }

                    val wid: String
                    val ar: String
                    val tr: String
                    val lvl: String

                    if (cells.size == 2) {
                        wid = "${cleanPid}_${tsBase}_$lineNo"
                        ar = ArabicNlpEngine.sanitizeArabicSentence(cells[0])
                        tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[1])
                        lvl = defLvl
                    } else if (cells.size == 3 && cells[2].uppercase() in listOf("A1", "A2", "B1", "B2", "C1")) {
                        wid = "${cleanPid}_${tsBase}_$lineNo"
                        ar = ArabicNlpEngine.sanitizeArabicSentence(cells[0])
                        tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[1])
                        lvl = cells[2].uppercase()
                    } else {
                        wid = cells[0].ifBlank { "${cleanPid}_${tsBase}_$lineNo" }
                        ar = ArabicNlpEngine.sanitizeArabicSentence(cells[1])
                        tr = ArabicNlpEngine.sanitizeTurkishSentence(cells.getOrNull(2) ?: "")
                        val candLvl = cells.getOrNull(3)?.uppercase() ?: ""
                        lvl = if (candLvl in listOf("A1", "A2", "B1", "B2", "C1")) candLvl else defLvl
                    }

                    if (ar.isEmpty() || tr.isEmpty()) {
                        skippedDetails.add("Satır $lineNo (Arapça veya Türkçe alanı boş)")
                        continue
                    }
                    wordsToInsert.add(WordEntity(id = wid, paketId = cleanPid, arapca = ar, turkce = tr, seviye = lvl))
                    added++
                }
                if (wordsToInsert.isNotEmpty()) dao.insertWords(wordsToInsert)
            }

            "sentences" -> {
                if (clearExisting) dao.deleteAllSentences()
                dao.insertPackage(PackageEntity(cleanPid, cleanPad, "sentences", defLvl, "Genel"))
                val sentsToInsert = mutableListOf<SentenceEntity>()
                for ((idx, line) in lines.withIndex()) {
                    val lineNo = idx + 1
                    val cells = line.split(delim).map { it.trim() }
                    if (lineNo == 1 && cells.firstOrNull()?.lowercase()?.let {
                            it.contains("id") || it.contains("arapca") || it.contains("cumle")
                        } == true
                    ) continue

                    if (cells.size < 2) {
                        skippedDetails.add("Satır $lineNo (En az 2 sütun [Arapça ve Türkçe] olmalı)")
                        continue
                    }

                    val sid: String
                    val ar: String
                    val tr: String
                    val lvl: String

                    if (cells.size == 2) {
                        sid = "${cleanPid}_${tsBase}_$lineNo"
                        ar = ArabicNlpEngine.sanitizeArabicSentence(cells[0])
                        tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[1])
                        lvl = defLvl
                    } else if (cells.size == 3 && cells[2].uppercase() in listOf("A1", "A2", "B1", "B2", "C1")) {
                        sid = "${cleanPid}_${tsBase}_$lineNo"
                        ar = ArabicNlpEngine.sanitizeArabicSentence(cells[0])
                        tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[1])
                        lvl = cells[2].uppercase()
                    } else {
                        sid = cells[0].ifBlank { "${cleanPid}_${tsBase}_$lineNo" }
                        ar = ArabicNlpEngine.sanitizeArabicSentence(cells[1])
                        tr = ArabicNlpEngine.sanitizeTurkishSentence(cells.getOrNull(2) ?: "")
                        val candLvl = cells.getOrNull(3)?.uppercase() ?: ""
                        lvl = if (candLvl in listOf("A1", "A2", "B1", "B2", "C1")) candLvl else defLvl
                    }

                    if (ar.isEmpty() || tr.isEmpty()) {
                        skippedDetails.add("Satır $lineNo (Arapça veya Türkçe cümle boş)")
                        continue
                    }
                    sentsToInsert.add(SentenceEntity(id = sid, paketId = cleanPid, arapca = ar, turkce = tr, seviye = lvl))
                    added++
                }
                if (sentsToInsert.isNotEmpty()) dao.insertSentences(sentsToInsert)
            }

            "dialogue" -> {
                if (clearExisting) {
                    dao.deleteAllDialogueLines()
                    dao.deleteAllDialogues()
                }
                val did = if (cleanPid.startsWith("dlg_")) cleanPid else "dlg_$cleanPid"
                dao.insertDialogue(DialogueEntity(did, "muhadesat_paketi", "", cleanPad, defLvl, "Günlük Hayat"))
                val linesToInsert = mutableListOf<DialogueLineEntity>()
                for ((idx, line) in lines.withIndex()) {
                    val lineNo = idx + 1
                    val cells = line.split(delim).map { it.trim() }
                    if (lineNo == 1 && cells.firstOrNull()?.lowercase()?.let {
                            it.contains("konusmaci") || it.contains("sira") || it.contains("arapca")
                        } == true
                    ) continue

                    if (cells.size < 2) {
                        skippedDetails.add("Satır $lineNo (En az 2 sütun [Arapça ve Türkçe] olmalı)")
                        continue
                    }

                    val spk: String
                    val cins: String
                    val ar: String
                    val tr: String

                    when (cells.size) {
                        2 -> {
                            spk = if (lineNo % 2 == 1) "Ömer" else "Deniz"
                            cins = if (lineNo % 2 == 1) "E" else "K"
                            ar = ArabicNlpEngine.sanitizeArabicSentence(cells[0])
                            tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[1])
                        }
                        3 -> {
                            spk = cells[0].ifBlank { "Kişi" }
                            cins = "E"
                            ar = ArabicNlpEngine.sanitizeArabicSentence(cells[1])
                            tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[2])
                        }
                        else -> {
                            spk = cells[0].ifBlank { "Kişi" }
                            cins = if (cells[1].uppercase() in listOf("E", "K")) cells[1].uppercase() else "E"
                            ar = ArabicNlpEngine.sanitizeArabicSentence(cells[2])
                            tr = ArabicNlpEngine.sanitizeTurkishSentence(cells[3])
                        }
                    }

                    if (ar.isEmpty()) {
                        skippedDetails.add("Satır $lineNo (Arapça cümle boş)")
                        continue
                    }
                    linesToInsert.add(
                        DialogueLineEntity(
                            diyalogId = did,
                            sira = lineNo,
                            konusmaci = spk,
                            cinsiyet = cins,
                            arapca = ar,
                            turkce = tr
                        )
                    )
                    added++
                }
                if (linesToInsert.isNotEmpty()) dao.insertDialogueLines(linesToInsert)
            }
        }

        return ImportReport(added, skippedDetails.size, skippedDetails)
    }

    // --- DATABASE TEXT DOCTOR & AUTO CEFR LEVELER ---
    suspend fun repairAllDatabases(): Int {
        var fixed = 0
        val words = dao.getAllWords()
        for (w in words) {
            val newAr = ArabicNlpEngine.sanitizeArabicSentence(w.arapca)
            val newTr = ArabicNlpEngine.sanitizeTurkishSentence(w.turkce)
            if (newAr != w.arapca || newTr != w.turkce) {
                dao.insertWord(w.copy(arapca = newAr, turkce = newTr))
                fixed++
            }
        }
        val sents = dao.getAllSentences()
        for (s in sents) {
            val newAr = ArabicNlpEngine.sanitizeArabicSentence(s.arapca)
            val newTr = ArabicNlpEngine.sanitizeTurkishSentence(s.turkce)
            if (newAr != s.arapca || newTr != s.turkce) {
                dao.insertSentence(s.copy(arapca = newAr, turkce = newTr))
                fixed++
            }
        }
        val lines = dao.getAllDialogueLines()
        for (l in lines) {
            val newAr = ArabicNlpEngine.sanitizeArabicSentence(l.arapca)
            val newTr = ArabicNlpEngine.sanitizeTurkishSentence(l.turkce)
            if (newAr != l.arapca || newTr != l.turkce) {
                dao.insertDialogueLine(l.copy(arapca = newAr, turkce = newTr))
                fixed++
            }
        }
        return fixed
    }

    suspend fun autoAssignCefrLevels(targetMode: String = "all"): Int {
        var updatedCount = 0
        if (targetMode == "all" || targetMode == "words") {
            val words = dao.getAllWords()
            val updated = words.map { w ->
                val lvl = ArabicNlpEngine.estimateWordCefr(w.arapca, w.gorsel)
                updatedCount++
                w.copy(seviye = lvl)
            }
            dao.insertWords(updated)
        }
        if (targetMode == "all" || targetMode == "sentences") {
            val sents = dao.getAllSentences()
            val updated = sents.map { s ->
                val lvl = ArabicNlpEngine.estimateSentenceCefr(s.arapca)
                updatedCount++
                s.copy(seviye = lvl)
            }
            dao.insertSentences(updated)
        }
        if (targetMode == "all" || targetMode == "dialogue") {
            val dlgs = dao.getAllDialogues()
            val allLines = dao.getAllDialogueLines()
            for (d in dlgs) {
                val dLines = allLines.filter { it.diyalogId == d.id }.map { it.arapca }
                val lvl = ArabicNlpEngine.estimateDialogueCefr(dLines)
                dao.insertDialogue(d.copy(seviye = lvl))
                updatedCount++
            }
        }
        return updatedCount
    }

    suspend fun resetStatsForMode(mode: String) {
        when (mode) {
            "words" -> dao.resetWordStats()
            "sentences" -> dao.resetSentenceStats()
            "dialogue" -> dao.resetDialogueStats()
        }
    }

    suspend fun saveOrUpdateWord(word: WordEntity) = dao.insertWord(word)
    suspend fun deleteWord(id: String) = dao.deleteWordById(id)

    suspend fun saveOrUpdateSentence(sentence: SentenceEntity) = dao.insertSentence(sentence)
    suspend fun deleteSentence(id: String) = dao.deleteSentenceById(id)

    suspend fun saveOrUpdateDialogueLine(line: DialogueLineEntity, dialogueLevel: String? = null) {
        dao.insertDialogueLine(line)
        if (!dialogueLevel.isNullOrBlank()) {
            val dlgs = dao.getAllDialogues()
            val parent = dlgs.firstOrNull { it.id == line.diyalogId }
            if (parent != null) {
                dao.insertDialogue(parent.copy(seviye = dialogueLevel))
            }
        }
    }

    suspend fun deleteDialogueLine(lineId: Int) = dao.deleteDialogueLineById(lineId)

    suspend fun deletePackage(mode: String, paketId: String) {
        when (mode) {
            "words" -> {
                dao.deleteWordsByPackage(paketId)
                dao.deletePackageById(paketId)
            }
            "sentences" -> {
                dao.deleteSentencesByPackage(paketId)
                dao.deletePackageById(paketId)
            }
            "dialogue" -> {
                dao.deleteLinesByDialogue(paketId)
                dao.deleteDialogueById(paketId)
            }
        }
    }
}
