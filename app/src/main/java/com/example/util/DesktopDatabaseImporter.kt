package com.example.util

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.data.DialogueEntity
import com.example.data.DialogueLineEntity
import com.example.data.ImportReport
import com.example.data.MuallimunDao
import com.example.data.PackageEntity
import com.example.data.SentenceEntity
import com.example.data.SozlukEntity
import com.example.data.WordEntity
import com.example.nlp.ArabicNlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object DesktopDatabaseImporter {

    private fun Cursor.str(col: String, def: String = ""): String {
        val idx = getColumnIndex(col)
        return if (idx != -1 && !isNull(idx)) getString(idx) ?: def else def
    }

    private fun Cursor.int(col: String, def: Int = 0): Int {
        val idx = getColumnIndex(col)
        return if (idx != -1 && !isNull(idx)) getInt(idx) else def
    }

    private fun Cursor.dbl(col: String, def: Double = 2.5): Double {
        val idx = getColumnIndex(col)
        return if (idx != -1 && !isNull(idx)) getDouble(idx) else def
    }

    private val DESKTOP_VISUAL_ICON_MAP = mapOf(
        // Hayvanlar (15)
        "ari" to "🐝", "aslan" to "🦁", "fil" to "🐘", "horoz" to "🐓", "inek" to "🐄",
        "kaplumbaga" to "🐢", "karinca" to "🐜", "kedi" to "🐱", "kelebek" to "🦋", "kopek" to "🐕",
        "koyun" to "🐑", "kus" to "🐦", "tavsan" to "🐇", "tavuk" to "🐔", "zurafa" to "🦒",
        // Meyveler & Sebzeler (50)
        "ananas" to "🍍", "armut" to "🍐", "avakado" to "🥑", "ayva" to "🍏", "bamya" to "🥬",
        "bezelye" to "🫛", "biber" to "🌶️", "burokoli" to "🥦", "cilek" to "🍓", "domates" to "🍅",
        "dut" to "🫐", "elma" to "🍎", "erik" to "🍑", "fasulye" to "🫘", "greyfurt" to "🍊",
        "havuc" to "🥕", "hindistan_cevizi" to "🥥", "hurma" to "🌴", "ispanak" to "🥬", "incir" to "🟣",
        "kabak" to "🎃", "karnibahar" to "🥦", "karpuz" to "🍉", "kavun" to "🍈", "kayisi" to "🍑",
        "kereviz" to "🥬", "kiraz" to "🍒", "kivi" to "🥝", "lahana" to "🥬", "limon" to "🍋",
        "mandalina" to "🍊", "mantar" to "🍄", "marul" to "🥗", "maydonoz" to "🌿", "misir" to "🌽",
        "muz" to "🍌", "nane" to "🌿", "nar" to "🍎", "patates" to "🥔", "patlican" to "🍆",
        "pirasa" to "🥬", "portakal" to "🍊", "salatalik" to "🥒", "sarimsak" to "🧄", "sogan" to "🧅",
        "salgam" to "🫜", "seftali" to "🍑", "turp" to "🫜", "uzum" to "🍇", "zeytin" to "🫒"
    )

    private fun resolveVisualIconOrEmoji(wordId: String, paketId: String, rawGorsel: String?): String? {
        if (rawGorsel.isNullOrBlank()) return null
        if (rawGorsel.length <= 4) return rawGorsel // Already an emoji
        val cleanId = wordId.lowercase().trim()
        DESKTOP_VISUAL_ICON_MAP[cleanId]?.let { return it }
        val fileStem = rawGorsel.substringAfterLast("/").substringBeforeLast(".").lowercase()
        DESKTOP_VISUAL_ICON_MAP[fileStem]?.let { return it }
        return when {
            paketId.contains("hayvan") -> "🐾"
            paketId.contains("meyve") -> "🍎"
            else -> null
        }
    }

    private fun getTableNames(db: SQLiteDatabase): Set<String> {
        val tables = mutableSetOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            while (c.moveToNext()) {
                tables.add(c.getString(0).lowercase())
            }
        }
        return tables
    }

    /**
     * Masaüstündeki kelimeler.db, sozluk.db, diyaloglar.db ve cumleler.db SQLite dosyalarını
     * tablo isimlerinden otomatik tanıyarak Android Room veritabanına aktarır.
     */
    suspend fun importSqliteDbFile(
        dbFile: File,
        dao: MuallimunDao,
        clearExisting: Boolean = false
    ): ImportReport = withContext(Dispatchers.IO) {
        if (!dbFile.exists() || dbFile.length() < 100) {
            return@withContext ImportReport(0, 1, listOf("${dbFile.name}: Geçersiz veya boş dosya"))
        }

        var addedTotal = 0
        val details = mutableListOf<String>()

        val sqlite = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            return@withContext ImportReport(0, 1, listOf("${dbFile.name} açılamadı: ${e.localizedMessage}"))
        }

        try {
            val tables = getTableNames(sqlite)

            // 1. KELİMELER VERİTABANI (kelimeler.db)
            if ("kelimeler" in tables) {
                if (clearExisting) dao.deleteAllWords()
                if ("paketler" in tables) {
                    val pkgs = mutableListOf<PackageEntity>()
                    sqlite.rawQuery("SELECT * FROM paketler", null).use { c ->
                        while (c.moveToNext()) {
                            val id = c.str("id")
                            val ad = c.str("ad", id)
                            val sev = c.str("seviye", "A1").ifBlank { "A1" }
                            val konu = c.str("konu", "Genel").ifBlank { "Genel" }
                            if (id.isNotBlank()) {
                                pkgs.add(PackageEntity(id, ad, "words", sev, konu))
                            }
                        }
                    }
                    if (pkgs.isNotEmpty()) dao.insertPackages(pkgs)
                }

                val words = mutableListOf<WordEntity>()
                sqlite.rawQuery("SELECT * FROM kelimeler", null).use { c ->
                    while (c.moveToNext()) {
                        val id = c.str("id")
                        val pid = c.str("paket_id", "genel").ifBlank { "genel" }
                        val ar = ArabicNlpEngine.sanitizeArabicSentence(c.str("arapca"))
                        val tr = ArabicNlpEngine.sanitizeTurkishSentence(c.str("turkce"))
                        val gorselRaw = c.str("gorsel").takeIf { it.isNotBlank() }
                        val gorsel = resolveVisualIconOrEmoji(id, pid, gorselRaw)
                        val ses = c.str("ses").takeIf { it.isNotBlank() }
                        val dogru = c.int("dogru", 0)
                        val yanlis = c.int("yanlis", 0)
                        val isFav = c.int("is_fav", 0)
                        val isLearned = c.int("is_learned", 0)
                        val lastPrac = c.str("last_practiced", "")
                        val sev = c.str("seviye", "A1").ifBlank { "A1" }
                        val ivDays = c.int("interval_days", 0)
                        val ef = c.dbl("ease_factor", 2.5)
                        val nextRev = c.str("next_review", "")

                        if (id.isNotBlank() && ar.isNotBlank() && tr.isNotBlank()) {
                            words.add(
                                WordEntity(
                                    id = id,
                                    paketId = pid,
                                    arapca = ar,
                                    turkce = tr,
                                    gorsel = gorsel,
                                    ses = ses,
                                    dogru = dogru,
                                    yanlis = yanlis,
                                    isFav = isFav,
                                    isLearned = isLearned,
                                    lastPracticed = lastPrac,
                                    seviye = sev,
                                    intervalDays = ivDays,
                                    easeFactor = ef,
                                    nextReview = nextRev
                                )
                            )
                        }
                    }
                }
                if (words.isNotEmpty()) {
                    words.chunked(500).forEach { dao.insertWords(it) }
                    addedTotal += words.size
                    details.add("kelimeler.db: ${words.size} kelime aktarıldı")
                }
            }

            // 2. SARF SÖZLÜĞÜ VERİTABANI (sozluk.db)
            if ("sozluk" in tables) {
                if (clearExisting) dao.deleteAllSozluk()
                val szlList = mutableListOf<SozlukEntity>()
                val szlWordCards = mutableListOf<WordEntity>()
                val szlPackages = mutableMapOf<String, PackageEntity>()
                szlPackages["sozluk_tumu"] = PackageEntity("sozluk_tumu", "📖 Sarf Sözlüğü (Tüm Kelimeler)", "words", "A2", "Sarf Sözlüğü")

                sqlite.rawQuery("SELECT * FROM sozluk", null).use { c ->
                    while (c.moveToNext()) {
                        val id = c.str("id")
                        val tur = c.str("tur", "isim").lowercase()
                        val kok = ArabicNlpEngine.sanitizeArabicSentence(c.str("kok"))
                        val mufret = ArabicNlpEngine.sanitizeArabicSentence(c.str("mufret"))
                        val cemi = ArabicNlpEngine.sanitizeArabicSentence(c.str("cemi"))
                        val mazi = ArabicNlpEngine.sanitizeArabicSentence(c.str("mazi"))
                        val muzari = ArabicNlpEngine.sanitizeArabicSentence(c.str("muzari"))
                        val masdar = ArabicNlpEngine.sanitizeArabicSentence(c.str("masdar"))
                        val anlamTr = ArabicNlpEngine.sanitizeTurkishSentence(c.str("anlam_tr"))
                        val katId = c.str("kategori_id", "szl_genel").ifBlank { "szl_genel" }
                        val kat = c.str("kategori", "📘 Genel Kavramlar & İsimler").ifBlank { "📘 Genel Kavramlar & İsimler" }
                        val emoji = c.str("emoji", "📘").ifBlank { "📘" }
                        val sev = c.str("seviye", "A1").ifBlank { "A1" }
                        val wordId = c.str("word_id", "szl_$id").ifBlank { "szl_$id" }

                        val primaryAr = if (tur == "isim") mufret else mazi
                        if (id.isNotBlank() && primaryAr.isNotBlank() && anlamTr.isNotBlank()) {
                            szlList.add(
                                SozlukEntity(
                                    id = id,
                                    tur = tur,
                                    kok = kok,
                                    mufret = mufret,
                                    cemi = cemi,
                                    mazi = mazi,
                                    muzari = muzari,
                                    masdar = masdar,
                                    anlamTr = anlamTr,
                                    kategoriId = katId,
                                    kategori = kat,
                                    emoji = emoji,
                                    seviye = sev,
                                    wordId = wordId
                                )
                            )
                            szlPackages[katId] = PackageEntity(katId, "📖 Sözlük: $kat", "words", "A2", kat)
                            szlWordCards.add(
                                WordEntity(
                                    id = wordId,
                                    paketId = katId,
                                    arapca = primaryAr,
                                    turkce = anlamTr,
                                    seviye = sev
                                )
                            )
                        }
                    }
                }
                if (szlList.isNotEmpty()) {
                    dao.insertPackages(szlPackages.values.toList())
                    szlList.chunked(500).forEach { dao.insertSozlukList(it) }
                    szlWordCards.chunked(500).forEach { dao.insertWords(it) }
                    addedTotal += szlList.size
                    details.add("sozluk.db: ${szlList.size} sözlük maddesi ve kök ağacı aktarıldı")
                }
            }

            // 3. DİYALOGLAR VERİTABANI (diyaloglar.db)
            if ("diyaloglar" in tables || "diyalog_satirlari" in tables) {
                if (clearExisting) {
                    dao.deleteAllDialogueLines()
                    dao.deleteAllDialogues()
                }
                if ("diyaloglar" in tables) {
                    val dlgs = mutableListOf<DialogueEntity>()
                    sqlite.rawQuery("SELECT * FROM diyaloglar", null).use { c ->
                        while (c.moveToNext()) {
                            val id = c.str("id")
                            val pid = c.str("paket_id", "muhadesat_paketi").ifBlank { "muhadesat_paketi" }
                            val bAr = ArabicNlpEngine.sanitizeArabicSentence(c.str("baslik_ar"))
                            val bTr = ArabicNlpEngine.sanitizeTurkishSentence(c.str("baslik_tr", id))
                            val sev = c.str("seviye", "A1").ifBlank { "A1" }
                            val konu = c.str("konu", "Günlük Hayat").ifBlank { "Günlük Hayat" }
                            if (id.isNotBlank()) {
                                dlgs.add(DialogueEntity(id, pid, bAr, bTr, sev, konu))
                            }
                        }
                    }
                    if (dlgs.isNotEmpty()) dao.insertDialogues(dlgs)
                }

                if ("diyalog_satirlari" in tables) {
                    val lines = mutableListOf<DialogueLineEntity>()
                    sqlite.rawQuery("SELECT * FROM diyalog_satirlari ORDER BY diyalog_id, sira, id", null).use { c ->
                        while (c.moveToNext()) {
                            val did = c.str("diyalog_id")
                            val sira = c.int("sira", 1)
                            val spk = c.str("konusmaci", "Kişi").ifBlank { "Kişi" }
                            val spkAr = ArabicNlpEngine.sanitizeArabicSentence(c.str("konusmaci_ar"))
                            val cins = c.str("cinsiyet", "E").uppercase().let { if (it in listOf("E", "K")) it else "E" }
                            val ar = ArabicNlpEngine.sanitizeArabicSentence(c.str("arapca"))
                            val tr = ArabicNlpEngine.sanitizeTurkishSentence(c.str("turkce"))
                            val ses = c.str("ses").takeIf { it.isNotBlank() }
                            val dogru = c.int("dogru", 0)
                            val yanlis = c.int("yanlis", 0)

                            if (did.isNotBlank() && ar.isNotBlank()) {
                                lines.add(
                                    DialogueLineEntity(
                                        diyalogId = did,
                                        sira = sira,
                                        konusmaci = spk,
                                        konusmaciAr = spkAr,
                                        cinsiyet = cins,
                                        arapca = ar,
                                        turkce = tr,
                                        ses = ses,
                                        dogru = dogru,
                                        yanlis = yanlis
                                    )
                                )
                            }
                        }
                    }
                    if (lines.isNotEmpty()) {
                        lines.chunked(500).forEach { dao.insertDialogueLines(it) }
                        addedTotal += lines.size
                        details.add("diyaloglar.db: ${lines.size} diyalog satırı aktarıldı")
                    }
                }
            }

            // 4. ANAHTAR CÜMLELER VERİTABANI (cumleler.db)
            if ("cumleler" in tables) {
                if (clearExisting) dao.deleteAllSentences()
                if ("paketler" in tables) {
                    val pkgs = mutableListOf<PackageEntity>()
                    sqlite.rawQuery("SELECT * FROM paketler", null).use { c ->
                        while (c.moveToNext()) {
                            val id = c.str("id")
                            val ad = c.str("ad", id)
                            val sev = c.str("seviye", "A1").ifBlank { "A1" }
                            val konu = c.str("konu", "Anahtar Kalıplar").ifBlank { "Anahtar Kalıplar" }
                            if (id.isNotBlank()) {
                                pkgs.add(PackageEntity(id, ad, "sentences", sev, konu))
                            }
                        }
                    }
                    if (pkgs.isNotEmpty()) dao.insertPackages(pkgs)
                }

                val sents = mutableListOf<SentenceEntity>()
                sqlite.rawQuery("SELECT * FROM cumleler", null).use { c ->
                    while (c.moveToNext()) {
                        val id = c.str("id")
                        val pid = c.str("paket_id", "anahtar_cumleler").ifBlank { "anahtar_cumleler" }
                        val ar = ArabicNlpEngine.sanitizeArabicSentence(c.str("arapca"))
                        val tr = ArabicNlpEngine.sanitizeTurkishSentence(c.str("turkce"))
                        val ses = c.str("ses").takeIf { it.isNotBlank() }
                        val isFav = c.int("is_fav", 0)
                        val isLearned = c.int("is_learned", 0)
                        val dogru = c.int("dogru", 0)
                        val yanlis = c.int("yanlis", 0)
                        val lastPrac = c.str("last_practiced", "")
                        val sev = c.str("seviye", "A1").ifBlank { "A1" }
                        val ivDays = c.int("interval_days", 0)
                        val ef = c.dbl("ease_factor", 2.5)
                        val nextRev = c.str("next_review", "")

                        if (id.isNotBlank() && ar.isNotBlank()) {
                            sents.add(
                                SentenceEntity(
                                    id = id,
                                    paketId = pid,
                                    arapca = ar,
                                    turkce = tr,
                                    ses = ses,
                                    isFav = isFav,
                                    isLearned = isLearned,
                                    dogru = dogru,
                                    yanlis = yanlis,
                                    lastPracticed = lastPrac,
                                    seviye = sev,
                                    intervalDays = ivDays,
                                    easeFactor = ef,
                                    nextReview = nextRev
                                )
                            )
                        }
                    }
                }
                if (sents.isNotEmpty()) {
                    sents.chunked(500).forEach { dao.insertSentences(it) }
                    addedTotal += sents.size
                    details.add("cumleler.db: ${sents.size} cümle aktarıldı")
                }
            }
        } finally {
            sqlite.close()
        }

        ImportReport(addedTotal, 0, details)
    }

    /**
     * Kullanıcının telefondan seçtiği .db, .zip (içinde birden fazla .db veya .json/.csv olan),
     * .json (sozluk.json / diyalog_paketi.json) dosyalarını tek adımda içe aktarır.
     */
    suspend fun importAnyFileUri(
        context: Context,
        uri: Uri,
        fileNameHint: String,
        dao: MuallimunDao,
        clearExisting: Boolean = false
    ): ImportReport = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "db_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val cleanName = fileNameHint.ifBlank { "imported_file.db" }
            val copiedFile = File(tempDir, cleanName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(copiedFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext ImportReport(0, 1, listOf("Dosya okunamadı."))

            val lower = cleanName.lowercase()
            when {
                lower.endsWith(".zip") -> {
                    var totalAdded = 0
                    val allDetails = mutableListOf<String>()
                    ZipInputStream(copiedFile.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val entryName = File(entry.name).name
                                if (entryName.endsWith(".db", ignoreCase = true) ||
                                    entryName.endsWith(".sqlite", ignoreCase = true) ||
                                    entryName.endsWith(".json", ignoreCase = true)
                                ) {
                                    val extracted = File(tempDir, entryName)
                                    FileOutputStream(extracted).use { fos -> zis.copyTo(fos) }
                                    val rep = if (entryName.endsWith(".json", ignoreCase = true)) {
                                        importJsonFile(extracted, dao, clearExisting)
                                    } else {
                                        importSqliteDbFile(extracted, dao, clearExisting)
                                    }
                                    totalAdded += rep.addedCount
                                    allDetails.addAll(rep.skippedDetails)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                    ImportReport(totalAdded, 0, allDetails)
                }

                lower.endsWith(".json") -> importJsonFile(copiedFile, dao, clearExisting)

                else -> importSqliteDbFile(copiedFile, dao, clearExisting)
            }
        } catch (ex: Exception) {
            ImportReport(0, 1, listOf("İçe aktarma hatası: ${ex.localizedMessage}"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Eğer app/src/main/assets/ klasörüne kelimeler.db, sozluk.db, diyaloglar.db veya cumleler.db
     * bırakılmışsa ilk açılışta otomatik olarak Room veritabanına aktarır.
     */
    suspend fun importBundledAssetsIfPresent(context: Context, dao: MuallimunDao): Int = withContext(Dispatchers.IO) {
        val assetFiles = try {
            context.assets.list("")?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val dbAssets = assetFiles.filter {
            it.endsWith(".db", ignoreCase = true) || it in listOf("sozluk.json", "diyalog_paketi.json", "kelimeler.json")
        }
        if (dbAssets.isEmpty()) return@withContext 0

        var totalImported = 0
        val tempDir = File(context.cacheDir, "bundled_assets_seed").apply { mkdirs() }
        try {
            for (assetName in dbAssets) {
                val outFile = File(tempDir, assetName)
                context.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                val rep = if (assetName.endsWith(".json", ignoreCase = true)) {
                    importJsonFile(outFile, dao, clearExisting = false)
                } else {
                    importSqliteDbFile(outFile, dao, clearExisting = false)
                }
                totalImported += rep.addedCount
            }
        } catch (_: Exception) {
        } finally {
            tempDir.deleteRecursively()
        }
        totalImported
    }

    private suspend fun importJsonFile(
        jsonFile: File,
        dao: MuallimunDao,
        clearExisting: Boolean
    ): ImportReport {
        val raw = jsonFile.readText(Charsets.UTF_8).trim()
        if (raw.isEmpty()) return ImportReport(0, 1, listOf("JSON dosyası boş"))

        var added = 0
        val details = mutableListOf<String>()

        if (raw.startsWith("[")) {
            val arr = JSONArray(raw)
            if (arr.length() > 0) {
                val first = arr.getJSONObject(0)
                if (first.has("kok") && (first.has("mufret") || first.has("mazi"))) {
                    // sozluk.json formatı
                    if (clearExisting) dao.deleteAllSozluk()
                    val szlList = mutableListOf<SozlukEntity>()
                    val szlWords = mutableListOf<WordEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id", "kelime_${i + 1}")
                        val tur = obj.optString("tur", "isim").lowercase()
                        val kok = ArabicNlpEngine.sanitizeArabicSentence(obj.optString("kok", ""))
                        val mufret = ArabicNlpEngine.sanitizeArabicSentence(obj.optString("mufret", ""))
                        val cemi = ArabicNlpEngine.sanitizeArabicSentence(obj.optString("cemi", ""))
                        val mazi = ArabicNlpEngine.sanitizeArabicSentence(obj.optString("mazi", ""))
                        val muzari = ArabicNlpEngine.sanitizeArabicSentence(obj.optString("muzari", ""))
                        val masdar = ArabicNlpEngine.sanitizeArabicSentence(obj.optString("masdar", ""))
                        val anlamTr = ArabicNlpEngine.sanitizeTurkishSentence(obj.optString("anlam_tr", ""))
                        val primaryAr = if (tur == "isim") mufret else mazi
                        val sev = obj.optString("seviye", ArabicNlpEngine.estimateWordCefr(primaryAr))
                        val wid = "szl_$id"
                        if (primaryAr.isNotBlank() && anlamTr.isNotBlank()) {
                            szlList.add(
                                SozlukEntity(
                                    id = id, tur = tur, kok = kok,
                                    mufret = mufret, cemi = cemi, mazi = mazi, muzari = muzari, masdar = masdar,
                                    anlamTr = anlamTr, seviye = sev, wordId = wid
                                )
                            )
                            szlWords.add(WordEntity(id = wid, paketId = "szl_genel", arapca = primaryAr, turkce = anlamTr, seviye = sev))
                        }
                    }
                    szlList.chunked(500).forEach { dao.insertSozlukList(it) }
                    szlWords.chunked(500).forEach { dao.insertWords(it) }
                    added += szlList.size
                    details.add("sozluk.json: $added sözlük maddesi aktarıldı")
                } else if (first.has("satirlar")) {
                    // diyalog_paketi.json formatı
                    if (clearExisting) {
                        dao.deleteAllDialogueLines()
                        dao.deleteAllDialogues()
                    }
                    val dlgs = mutableListOf<DialogueEntity>()
                    val lines = mutableListOf<DialogueLineEntity>()
                    for (i in 0 until arr.length()) {
                        val dObj = arr.getJSONObject(i)
                        val did = dObj.optString("id", "dlg_${i + 1}")
                        val bAr = ArabicNlpEngine.sanitizeArabicSentence(dObj.optString("baslik_ar", ""))
                        val bTr = ArabicNlpEngine.sanitizeTurkishSentence(dObj.optString("baslik_tr", "Diyalog"))
                        val sev = dObj.optString("seviye", "A1")
                        dlgs.add(DialogueEntity(did, "muhadesat_paketi", bAr, bTr, sev, "Günlük Hayat"))
                        val sArr = dObj.optJSONArray("satirlar") ?: JSONArray()
                        for (j in 0 until sArr.length()) {
                            val lObj = sArr.getJSONObject(j)
                            val ar = ArabicNlpEngine.sanitizeArabicSentence(lObj.optString("arapca", ""))
                            val tr = ArabicNlpEngine.sanitizeTurkishSentence(lObj.optString("turkce", ""))
                            if (ar.isNotBlank()) {
                                lines.add(
                                    DialogueLineEntity(
                                        diyalogId = did,
                                        sira = lObj.optInt("sira", j + 1),
                                        konusmaci = lObj.optString("konusmaci", "Kişi"),
                                        konusmaciAr = lObj.optString("konusmaci_ar", ""),
                                        cinsiyet = lObj.optString("cinsiyet", "E"),
                                        arapca = ar,
                                        turkce = tr
                                    )
                                )
                            }
                        }
                    }
                    dao.insertDialogues(dlgs)
                    lines.chunked(500).forEach { dao.insertDialogueLines(it) }
                    added += lines.size
                    details.add("diyalog.json: ${dlgs.size} diyalog (${lines.size} satır) aktarıldı")
                }
            }
        } else if (raw.startsWith("{")) {
            val root = JSONObject(raw)
            val pid = root.optString("paket_id", "ozel_paket")
            val pad = root.optString("paket_adi", pid)
            val sev = root.optString("seviye", "A1")
            if (root.has("kelimeler")) {
                dao.insertPackage(PackageEntity(pid, pad, "words", sev, "Genel"))
                val arr = root.getJSONArray("kelimeler")
                val wList = mutableListOf<WordEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ar = ArabicNlpEngine.sanitizeArabicSentence(o.optString("arapca", ""))
                    val tr = ArabicNlpEngine.sanitizeTurkishSentence(o.optString("turkce", ""))
                    if (ar.isNotBlank() && tr.isNotBlank()) {
                        wList.add(
                            WordEntity(
                                id = o.optString("id", "${pid}_${i + 1}"),
                                paketId = pid,
                                arapca = ar,
                                turkce = tr,
                                seviye = o.optString("seviye", sev)
                            )
                        )
                    }
                }
                wList.chunked(500).forEach { dao.insertWords(it) }
                added += wList.size
                details.add("$pad: $added kelime aktarıldı")
            }
        }

        return ImportReport(added, 0, details)
    }
}
