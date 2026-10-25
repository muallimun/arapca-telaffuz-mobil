package com.example.util

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.DialogueEntity
import com.example.data.DialogueLineEntity
import com.example.data.DifficultItemRow
import com.example.data.PackageAnalyticsRow
import com.example.data.SentenceEntity
import com.example.data.SozlukEntity
import com.example.data.WordEntity
import com.example.nlp.ArabicNlpEngine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExamGeneratorConfig(
    val sourceMode: String = "words", // "words", "dialogue", "sentences", "all_mixed"
    val paketId: String = "all",
    val seviye: String = "Tümü",
    val sheetTitle: String = "Arapça Dersi Kelime, Sarf ve Anlama Yazılı Sınavı",
    val arToTrCount: Int = 10,
    val trToArCount: Int = 10,
    val fillBlankCount: Int = 5,
    val multipleChoiceCount: Int = 5,
    val matchingCount: Int = 5,
    val sarfTableCount: Int = 5,
    val includeSummaryTable: Boolean = false,
    val includeAnswerKeyPage: Boolean = true,
    val useHandoutMode: Boolean = false,
    val handoutKeepHaraka: Boolean = true,
    val handoutIncludeTr: Boolean = true,
    val handoutTrLayout: String = "tr_layout_side" // "tr_layout_side", "tr_layout_below", "tr_layout_end"
)

object WorksheetAndExportHelper {

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    fun generateWorksheetOrHandoutHtml(
        cfg: ExamGeneratorConfig,
        allWords: List<WordEntity>,
        allSozluk: List<SozlukEntity>,
        allDialogues: List<DialogueEntity>,
        allDialogueLines: List<DialogueLineEntity>,
        allSentences: List<SentenceEntity>
    ): String {
        val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())
        val sheetTitle = escapeHtml(cfg.sheetTitle.ifBlank { "Arapça Çalışma Yaprağı" })

        // =========================================================================
        // MODE 1: DERS NOTU / OKUMA METNİ (HANDOUT) FORMATI
        // =========================================================================
        if (cfg.useHandoutMode && (cfg.sourceMode == "dialogue" || cfg.sourceMode == "sentences")) {
            data class HandoutRow(val speakerOrNum: String, val arText: String, val trText: String)
            val rows = mutableListOf<HandoutRow>()

            if (cfg.sourceMode == "dialogue") {
                val dlgMap = allDialogues.associateBy { it.id }
                val lines = allDialogueLines.filter { line ->
                    val dlg = dlgMap[line.diyalogId]
                    val pkgOk = cfg.paketId == "all" || line.diyalogId == cfg.paketId
                    val lvlOk = cfg.seviye == "Tümü" || (dlg?.seviye ?: "A1") == cfg.seviye
                    pkgOk && lvlOk
                }
                for (l in lines) {
                    val arClean = if (cfg.handoutKeepHaraka) l.arapca else ArabicNlpEngine.removeDiacritics(l.arapca)
                    val spkDisp = if (l.konusmaciAr.isNotBlank()) "${l.konusmaci} (${l.konusmaciAr})" else l.konusmaci
                    rows.add(HandoutRow(spkDisp, arClean, l.turkce))
                }
            } else {
                val sents = allSentences.filter { s ->
                    val pkgOk = cfg.paketId == "all" || s.paketId == cfg.paketId
                    val lvlOk = cfg.seviye == "Tümü" || s.seviye == cfg.seviye
                    pkgOk && lvlOk
                }
                for ((idx, s) in sents.withIndex()) {
                    val arClean = if (cfg.handoutKeepHaraka) s.arapca else ArabicNlpEngine.removeDiacritics(s.arapca)
                    rows.add(HandoutRow("${idx + 1}.", arClean, s.turkce))
                }
            }

            val bodyHtml = if (!cfg.handoutIncludeTr) {
                val linesHtml = rows.joinToString("\n") { r ->
                    val spk = if (r.speakerOrNum.isNotBlank()) "<span class='spk-ar'>${escapeHtml(r.speakerOrNum)}:</span> " else ""
                    "<div class='dialogue-line'><div class='ar-handout'>$spk${escapeHtml(r.arText)}</div></div>"
                }
                "<div class='handout-container'>$linesHtml</div>"
            } else if (cfg.handoutTrLayout == "tr_layout_side") {
                val tRows = rows.joinToString("\n") { r ->
                    val spkAr = if (r.speakerOrNum.isNotBlank()) "<b style='color:#1e3a8a;'>${escapeHtml(r.speakerOrNum)}:</b> " else ""
                    val spkTr = if (cfg.sourceMode == "dialogue" && r.speakerOrNum.isNotBlank()) {
                        "<b>${escapeHtml(r.speakerOrNum.substringBefore("(").trim())}:</b> "
                    } else ""
                    "<tr><td class='ar-side'>$spkAr${escapeHtml(r.arText)}</td><td class='tr-side'>$spkTr${escapeHtml(r.trText)}</td></tr>"
                }
                """
                <table class="side-table">
                  <thead>
                    <tr>
                      <th style="width:50%;text-align:right;direction:rtl;">النَّصُّ الْعَرَبِيُّ (Arapça Metin)</th>
                      <th style="width:50%;text-align:left;direction:ltr;">Türkçe Tercümesi</th>
                    </tr>
                  </thead>
                  <tbody>$tRows</tbody>
                </table>
                """.trimIndent()
            } else if (cfg.handoutTrLayout == "tr_layout_below") {
                val blocks = rows.joinToString("\n") { r ->
                    val spkAr = if (r.speakerOrNum.isNotBlank()) "<span class='spk-ar'>${escapeHtml(r.speakerOrNum)}:</span> " else ""
                    "<div class='dialogue-line'><div class='ar-handout'>$spkAr${escapeHtml(r.arText)}</div><div class='tr-handout'>${escapeHtml(r.trText)}</div></div>"
                }
                "<div class='handout-container'>$blocks</div>"
            } else {
                val arPart = rows.joinToString("\n") { r ->
                    val spkAr = if (r.speakerOrNum.isNotBlank()) "<span class='spk-ar'>${escapeHtml(r.speakerOrNum)}:</span> " else ""
                    "<div class='ar-handout' style='margin-bottom:10px;'>$spkAr${escapeHtml(r.arText)}</div>"
                }
                val trPart = rows.mapIndexed { i, r ->
                    val spkTr = if (r.speakerOrNum.isNotBlank()) "<b>${escapeHtml(r.speakerOrNum)}:</b> " else "<b>${i + 1}.</b> "
                    "<div class='tr-end-line'>$spkTr${escapeHtml(r.trText)}</div>"
                }.joinToString("\n")
                """
                <div class="handout-container">$arPart</div>
                <div class="tr-end-section">
                  <h2>🇹🇷 Metnin Türkçe Çevirisi</h2>
                  $trPart
                </div>
                """.trimIndent()
            }

            return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                <meta charset="utf-8">
                <title>$sheetTitle</title>
                <style>
                  @page { size: A4; margin: 15mm; }
                  body { font-family: sans-serif; color: #0f172a; background: #fff; margin: 0; padding: 14px; }
                  .header-box { border-bottom: 2px solid #1e3a8a; padding-bottom: 10px; margin-bottom: 18px; display: flex; justify-content: space-between; align-items: center; }
                  h1 { font-size: 19px; color: #1e3a8a; margin: 0 0 4px 0; }
                  h2 { font-size: 15px; color: #1e3a8a; background: #f1f5f9; padding: 7px 12px; border-left: 4px solid #1e3a8a; margin-top: 20px; }
                  .side-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                  .side-table th, .side-table td { border: 1px solid #cbd5e1; padding: 9px 12px; vertical-align: top; }
                  .ar-side { width: 50%; direction: rtl; text-align: right; font-size: 21px; font-weight: bold; line-height: 1.75; }
                  .tr-side { width: 50%; direction: ltr; text-align: left; font-size: 13.5px; line-height: 1.75; color: #334155; }
                  .dialogue-line { border-bottom: 1px dashed #cbd5e1; padding: 9px 4px; page-break-inside: avoid; }
                  .ar-handout { direction: rtl; text-align: right; font-size: 21px; font-weight: bold; line-height: 1.75; }
                  .tr-handout { direction: ltr; text-align: right; font-size: 13.5px; color: #475569; margin-top: 4px; }
                  .spk-ar { color: #1e3a8a; font-weight: bold; }
                </style>
                </head>
                <body>
                  <div class="header-box">
                    <div>
                      <h1>🕌 $sheetTitle</h1>
                      <div style="font-size:12px;color:#475569;">Seviye: ${escapeHtml(cfg.seviye)} &nbsp;|&nbsp; Tarih: $todayStr &nbsp;|&nbsp; Muallimun.Net</div>
                    </div>
                  </div>
                  $bodyHtml
                </body>
                </html>
            """.trimIndent()
        }

        // =========================================================================
        // MODE 2: A4 YAZILI SINAV & ÇALIŞMA YAPRAĞI (AYRIK HAVUZLU & KARMA DESTEKLİ)
        // =========================================================================
        data class PoolItem(val id: String, val ar: String, val tr: String, val meta: String)
        val pool = mutableListOf<PoolItem>()

        when (cfg.sourceMode) {
            "words" -> {
                allWords.filter { cfg.paketId == "all" || it.paketId == cfg.paketId }
                    .filter { cfg.seviye == "Tümü" || it.seviye == cfg.seviye }
                    .forEach { pool.add(PoolItem("w_${it.id}", it.arapca, it.turkce, it.seviye)) }
            }
            "dialogue" -> {
                val dlgMap = allDialogues.associateBy { it.id }
                allDialogueLines.filter { cfg.paketId == "all" || it.diyalogId == cfg.paketId }
                    .filter { cfg.seviye == "Tümü" || (dlgMap[it.diyalogId]?.seviye ?: "A1") == cfg.seviye }
                    .forEach {
                        val dlvl = dlgMap[it.diyalogId]?.seviye ?: "A1"
                        pool.add(PoolItem("d_${it.id}", it.arapca, it.turkce, "$dlvl | ${it.konusmaci}"))
                    }
            }
            "sentences" -> {
                allSentences.filter { cfg.paketId == "all" || it.paketId == cfg.paketId }
                    .filter { cfg.seviye == "Tümü" || it.seviye == cfg.seviye }
                    .forEach { pool.add(PoolItem("s_${it.id}", it.arapca, it.turkce, it.seviye)) }
            }
            else -> {
                allWords.filter { cfg.seviye == "Tümü" || it.seviye == cfg.seviye }
                    .forEach { pool.add(PoolItem("w_${it.id}", it.arapca, it.turkce, "Kelime (${it.seviye})")) }
                val dlgMap = allDialogues.associateBy { it.id }
                allDialogueLines.filter { cfg.seviye == "Tümü" || (dlgMap[it.diyalogId]?.seviye ?: "A1") == cfg.seviye }
                    .forEach {
                        val dlvl = dlgMap[it.diyalogId]?.seviye ?: "A1"
                        pool.add(PoolItem("d_${it.id}", it.arapca, it.turkce, "Diyalog ($dlvl)"))
                    }
                allSentences.filter { cfg.seviye == "Tümü" || it.seviye == cfg.seviye }
                    .forEach { pool.add(PoolItem("s_${it.id}", it.arapca, it.turkce, "Cümle (${it.seviye})")) }
            }
        }

        if (pool.isEmpty()) {
            pool.addAll(allWords.map { PoolItem("w_${it.id}", it.arapca, it.turkce, it.seviye) })
        }

        // Disjoint Pools: Section 1 (AR->TR) & Section 2 (TR->AR) never ask the same item
        val arToTrPool = if (cfg.arToTrCount > 0) pool.shuffled().take(cfg.arToTrCount) else emptyList()
        val usedIds1 = arToTrPool.map { it.id }.toSet()
        val remainingAfter1 = pool.filter { it.id !in usedIds1 }

        val trToArPool = if (cfg.trToArCount > 0) {
            if (remainingAfter1.size >= cfg.trToArCount) {
                remainingAfter1.shuffled().take(cfg.trToArCount)
            } else {
                val extra = pool.filter { it.id in usedIds1 }.shuffled().take(cfg.trToArCount - remainingAfter1.size)
                remainingAfter1 + extra
            }
        } else emptyList()

        val usedIds1And2 = usedIds1 + trToArPool.map { it.id }
        val remainingAfter2 = pool.filter { it.id !in usedIds1And2 }.ifEmpty { pool }

        val fbPool = if (cfg.fillBlankCount > 0) remainingAfter2.shuffled().take(cfg.fillBlankCount) else emptyList()
        val mcPool = if (cfg.multipleChoiceCount > 0) pool.shuffled().take(cfg.multipleChoiceCount) else emptyList()
        val matchPool = if (cfg.matchingCount > 0) pool.shuffled().take(cfg.matchingCount.coerceAtMost(20)) else emptyList()

        val sectionsHtml = StringBuilder()
        val answersHtml = StringBuilder()
        var secNo = 0

        if (cfg.includeSummaryTable) {
            secNo++
            val rowsT = pool.take(25).mapIndexed { i, it ->
                "<tr><td style='text-align:center;'>${i + 1}</td><td style='text-align:center;'>${escapeHtml(it.meta)}</td><td>${escapeHtml(it.tr)}</td><td class='ar'>${escapeHtml(it.ar)}</td></tr>"
            }.joinToString("\n")
            sectionsHtml.append(
                """
                <div class="section">
                  <h2>📋 Bölüm $secNo: Konu Özeti Çalışma Tablosu</h2>
                  <table>
                    <thead><tr><th style="width:40px;">No</th><th style="width:100px;">Seviye</th><th>Türkçe Anlamı</th><th>Arapça İfade (RTL)</th></tr></thead>
                    <tbody>$rowsT</tbody>
                  </table>
                </div>
                """.trimIndent()
            )
        }

        if (arToTrPool.isNotEmpty()) {
            secNo++
            val rowsArTr = arToTrPool.mapIndexed { idx, it ->
                "<tr><td style='width:38px;text-align:center;font-weight:bold;'>${idx + 1}.</td>" +
                    "<td class='ar' style='width:45%;padding:8px 12px;'>${escapeHtml(it.ar)}</td>" +
                    "<td style='width:50%;color:#64748b;'>........................................................................</td></tr>"
            }.joinToString("\n")
            val ansArTr = arToTrPool.mapIndexed { idx, it -> "<b>${idx + 1})</b> ${escapeHtml(it.tr)}" }.joinToString(" &nbsp;|&nbsp; ")
            sectionsHtml.append(
                """
                <div class="section">
                  <h2>✍️ Bölüm $secNo: Arapça ➔ Türkçe Anlam Yazma (${arToTrPool.size} Soru)</h2>
                  <table>
                    <thead><tr><th>No</th><th>Arapça Kelime / İfade</th><th>Türkçe Anlamı (Öğrenci Yanıtı)</th></tr></thead>
                    <tbody>$rowsArTr</tbody>
                  </table>
                </div>
                """.trimIndent()
            )
            answersHtml.append("<div class='ans-block'><h3>Bölüm $secNo (Arapça ➔ Türkçe) Cevapları:</h3><p>$ansArTr</p></div>")
        }

        if (trToArPool.isNotEmpty()) {
            secNo++
            val rowsTrAr = trToArPool.mapIndexed { idx, it ->
                "<tr style='height:42px;'><td style='width:38px;text-align:center;font-weight:bold;'>${idx + 1}.</td>" +
                    "<td style='width:46%;padding:8px 10px;'>${escapeHtml(it.tr)}</td>" +
                    "<td style='width:48%;text-align:right;color:#94a3b8;padding-right:14px;'>....................................................................</td></tr>"
            }.joinToString("\n")
            val ansTrAr = trToArPool.mapIndexed { idx, it -> "<b>${idx + 1})</b> <span class='ar-inline'>${escapeHtml(it.ar)}</span>" }.joinToString(" &nbsp;&nbsp;|&nbsp;&nbsp; ")
            sectionsHtml.append(
                """
                <div class="section">
                  <h2>🖋️ Bölüm $secNo: Türkçe ➔ Arapça Yazma (${trToArPool.size} Soru)</h2>
                  <table>
                    <thead><tr><th>No</th><th>Türkçe Anlamı</th><th>Arapça Yazılışı (Sağdan Sola Yazınız)</th></tr></thead>
                    <tbody>$rowsTrAr</tbody>
                  </table>
                </div>
                """.trimIndent()
            )
            answersHtml.append("<div class='ans-block'><h3>Bölüm $secNo (Türkçe ➔ Arapça) Cevapları:</h3><p dir='rtl'>$ansTrAr</p></div>")
        }

        if (fbPool.isNotEmpty()) {
            secNo++
            val ansFb = mutableListOf<String>()
            val rowsFb = fbPool.mapIndexed { idx, it ->
                val wordsAr = it.ar.split(" ").toMutableList()
                val missingW: String
                val promptAr: String
                if (wordsAr.size > 1) {
                    val hideI = wordsAr.indices.random()
                    missingW = wordsAr[hideI]
                    wordsAr[hideI] = "........................"
                    promptAr = wordsAr.joinToString(" ")
                } else {
                    missingW = it.ar
                    promptAr = if (it.ar.length > 2) "${it.ar.first()} ........................" else "........................"
                }
                ansFb.add("<b>${idx + 1})</b> <span class='ar-inline'>${escapeHtml(missingW)}</span>")
                "<div class='qbox'><div><b>${idx + 1}.</b> ${escapeHtml(it.tr)}</div><div class='ar' style='margin-top:4px;'>${escapeHtml(promptAr)}</div></div>"
            }.joinToString("\n")

            sectionsHtml.append(
                """
                <div class="section">
                  <h2>🧩 Bölüm $secNo: Boşluk Doldurma Alıştırması (${fbPool.size} Soru)</h2>
                  $rowsFb
                </div>
                """.trimIndent()
            )
            answersHtml.append("<div class='ans-block'><h3>Bölüm $secNo (Boşluk Doldurma) Cevapları:</h3><p dir='rtl'>${ansFb.joinToString(" &nbsp;&nbsp;|&nbsp;&nbsp; ")}</p></div>")
        }

        if (mcPool.isNotEmpty() && pool.size >= 4) {
            secNo++
            val optLets = listOf("A", "B", "C", "D")
            val ansMc = mutableListOf<String>()
            val mcHtml = mcPool.mapIndexed { idx, it ->
                val distractors = pool.map { p -> p.tr }.distinct().filter { tr -> tr != it.tr }.shuffled().take(3)
                val opts = (distractors + it.tr).shuffled()
                val corrLet = optLets[opts.indexOf(it.tr).coerceAtLeast(0)]
                ansMc.add("<b>${idx + 1}</b>-$corrLet")
                val optsStr = opts.mapIndexed { i, o ->
                    "<div class='opt-cell'><span class='opt-badge'>${optLets.getOrElse(i) { "A" }})</span> <span>${escapeHtml(o)}</span></div>"
                }.joinToString("")
                "<div class='qbox'><div class='q-title'><b>Soru ${idx + 1}:</b> &nbsp; <span class='ar-inline'>${escapeHtml(it.ar)}</span> &nbsp; ifadesinin doğru Türkçe karşılığı hangisidir?</div><div class='opts-grid'>$optsStr</div></div>"
            }.joinToString("\n")

            sectionsHtml.append(
                """
                <div class="section">
                  <h2>📝 Bölüm $secNo: Çoktan Seçmeli Değerlendirme Testi (${mcPool.size} Soru)</h2>
                  $mcHtml
                </div>
                """.trimIndent()
            )
            answersHtml.append("<div class='ans-block'><h3>Bölüm $secNo (Çoktan Seçmeli Test) Cevapları:</h3><p>${ansMc.joinToString(" &nbsp;|&nbsp; ")}</p></div>")
        }

        if (matchPool.isNotEmpty()) {
            secNo++
            val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val shuffledTr = matchPool.mapIndexed { i, p -> (i + 1) to p.tr }.shuffled()
            val ansM = mutableListOf<String>()
            val rowsM = matchPool.mapIndexed { idx, it ->
                val let = letters[idx % letters.length]
                val (origNum, shufTrTxt) = shuffledTr[idx]
                val correctLet = letters[(origNum - 1) % letters.length]
                ansM.add("<b>${idx + 1}</b> ➔ $correctLet")
                "<tr><td class='ar'><b>($let)</b> &nbsp; ${escapeHtml(it.ar)}</td><td style='text-align:center;width:80px;'>[ &nbsp;&nbsp;&nbsp;&nbsp; ]</td><td><b>${idx + 1}.</b> ${escapeHtml(shufTrTxt)}</td></tr>"
            }.joinToString("\n")

            sectionsHtml.append(
                """
                <div class="section">
                  <h2>🔗 Bölüm $secNo: Eşleştirme Alıştırması (${matchPool.size} Soru)</h2>
                  <table>
                    <thead><tr><th>Arapça İfade</th><th>Harf</th><th>Türkçe Karşılığı</th></tr></thead>
                    <tbody>$rowsM</tbody>
                  </table>
                </div>
                """.trimIndent()
            )
            answersHtml.append("<div class='ans-block'><h3>Bölüm $secNo (Eşleştirme) Cevapları:</h3><p>${ansM.joinToString(" &nbsp;|&nbsp; ")}</p></div>")
        }

        if (cfg.sarfTableCount > 0 && allSozluk.isNotEmpty()) {
            secNo++
            val sarfPicked = allSozluk.shuffled().take(cfg.sarfTableCount)
            val sarfAns = mutableListOf<String>()
            val sarfRows = sarfPicked.mapIndexed { idx, s ->
                if (s.tur == "isim") {
                    sarfAns.add("<b>${idx + 1})</b> ج: ${escapeHtml(s.cemi)}")
                    "<tr><td style='text-align:center;'>${idx + 1}</td><td class='ar' style='text-align:center;'>${escapeHtml(s.kok)}</td><td>İsim (Müfret ➔ Cemî)</td><td class='ar'>${escapeHtml(s.mufret)}</td><td class='ar'>........................</td><td>${escapeHtml(s.anlamTr)}</td></tr>"
                } else {
                    sarfAns.add("<b>${idx + 1})</b> ${escapeHtml(s.muzari)} / ${escapeHtml(s.masdar)}")
                    "<tr><td style='text-align:center;'>${idx + 1}</td><td class='ar' style='text-align:center;'>${escapeHtml(s.kok)}</td><td>Fiil (Mâzî ➔ Muzâri / Masdar)</td><td class='ar'>${escapeHtml(s.mazi)}</td><td class='ar'>............ / ............</td><td>${escapeHtml(s.anlamTr)}</td></tr>"
                }
            }.joinToString("\n")

            sectionsHtml.append(
                """
                <div class="section">
                  <h2>🌿 Bölüm $secNo: Sarf (Sülâsî Kök & Türev) Alıştırma Tablosu (${sarfPicked.size} Soru)</h2>
                  <table>
                    <thead><tr><th>No</th><th>Sülâsî Kök</th><th>Tür</th><th>Ana Kelime</th><th>Cemî / Muzâri & Masdar</th><th>Anlamı</th></tr></thead>
                    <tbody>$sarfRows</tbody>
                  </table>
                </div>
                """.trimIndent()
            )
            answersHtml.append("<div class='ans-block'><h3>Bölüm $secNo (Sarf Tablosu) Cevapları:</h3><p dir='rtl'>${sarfAns.joinToString(" &nbsp;&nbsp;|&nbsp;&nbsp; ")}</p></div>")
        }

        if (cfg.includeAnswerKeyPage && answersHtml.isNotEmpty()) {
            sectionsHtml.append(
                """
                <div class="answer-key-page">
                  <h2>🔑 ÖĞRETMEN CEVAP ANAHTARI — $sheetTitle</h2>
                  $answersHtml
                </div>
                """.trimIndent()
            )
        }

        return """
            <!DOCTYPE html>
            <html lang="tr">
            <head>
            <meta charset="utf-8">
            <title>$sheetTitle</title>
            <style>
              @page { size: A4; margin: 14mm; }
              body { font-family: sans-serif; color: #0f172a; background: #fff; margin: 0; padding: 12px; }
              .header-box { border: 2px solid #1e3a8a; border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center; background: #f8fafc; }
              h1 { font-size: 17px; color: #1e3a8a; margin: 0 0 4px 0; }
              h2 { font-size: 14.5px; color: #1e3a8a; background: #f1f5f9; padding: 6px 10px; border-left: 4px solid #1e3a8a; margin-top: 18px; margin-bottom: 8px; }
              h3 { font-size: 13.5px; color: #0f172a; margin: 8px 0 4px 0; }
              .student-info { font-size: 12px; line-height: 1.75; border-left: 2px dashed #94a3b8; padding-left: 12px; min-width: 220px; }
              table { width: 100%; border-collapse: collapse; margin-bottom: 14px; font-size: 13px; }
              th, td { border: 1px solid #94a3b8; padding: 7px 9px; vertical-align: middle; }
              th { background: #f1f5f9; font-weight: bold; }
              .ar { font-size: 20px; font-weight: bold; direction: rtl; text-align: right; line-height: 1.5; }
              .ar-inline { font-size: 19px; font-weight: bold; direction: rtl; }
              .qbox { border: 1px solid #cbd5e1; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; page-break-inside: avoid; }
              .q-title { font-size: 13.5px; margin-bottom: 6px; border-bottom: 1px dashed #e2e8f0; padding-bottom: 4px; }
              .opts-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 14px; }
              .opt-cell { font-size: 12.5px; background: #f8fafc; padding: 5px 8px; border-radius: 4px; border: 1px solid #e2e8f0; }
              .opt-badge { font-weight: bold; color: #1e3a8a; margin-right: 4px; }
              .answer-key-page { page-break-before: always; break-before: page; padding-top: 10px; }
              .ans-block { border-bottom: 1px solid #e2e8f0; padding-bottom: 6px; margin-bottom: 6px; font-size: 13px; }
            </style>
            </head>
            <body>
              <div class="header-box">
                <div>
                  <h1>🕌 $sheetTitle</h1>
                  <div style="font-size:11.5px;color:#475569;">Seviye: ${escapeHtml(cfg.seviye)} &nbsp;|&nbsp; Muallimun.Net Arapça Dil Laboratuvarı</div>
                </div>
                <div class="student-info">
                  <b>Adı Soyadı:</b> ........................................<br>
                  <b>Sınıfı / No:</b> .................. &nbsp; <b>Tarih:</b> $todayStr<br>
                  <b>Aldığı Puan:</b> ............ / 100
                </div>
              </div>
              $sectionsHtml
            </body>
            </html>
        """.trimIndent()
    }

    fun printHtmlAsPdf(context: Context, htmlContent: String, jobName: String = "Muallimun_Arapca_Sinav_Ve_Ders_Notu") {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                val adapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    fun shareHomeworkReportAndCsv(
        context: Context,
        studentName: String,
        levelTitle: String,
        streakDays: Int,
        totalPoints: Int,
        lastExamSummary: String,
        analyticsRows: List<PackageAnalyticsRow>,
        difficultRows: List<DifficultItemRow>,
        recordingsDir: File
    ) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val safeName = studentName.replace(Regex("[^a-zA-Z0-9ğüşöçıİĞÜŞÖÇ]"), "_").ifBlank { "Ogrenci" }
            val zipFile = File(exportDir, "Arapca_Odev_${safeName}_$dateStr.zip")

            val csvBuilder = StringBuilder()
            csvBuilder.append("\uFEFF") // UTF-8 BOM for Excel
            csvBuilder.append("OGRENCI ADI SOYADI;$studentName;SEVIYE UNVANI;$levelTitle;TOPLAM PUAN;$totalPoints\n\n")
            csvBuilder.append("BOLUM;PAKET / DIYALOG ADI;TOPLAM KAYIT;TAMAMLANAN;DOGRU;YANLIS;BASARI (%);TAMAMLANMA (%)\n")
            for (r in analyticsRows) {
                csvBuilder.append("${r.section};${r.name};${r.total};${r.learned};${r.correct};${r.wrong};%${r.accuracyPct};%${r.completionPct}\n")
            }
            csvBuilder.append("\nEN COK HATA YAPILAN KAYITLAR\nTUR;ID;TURKCE ANLAMI;ARAPCA METIN;DOGRU;YANLIS\n")
            for (d in difficultRows) {
                csvBuilder.append("${d.kind};${d.id};${d.turkce};${d.arapca};${d.dogru};${d.yanlis}\n")
            }

            val wavFiles = recordingsDir.listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }?.take(20) ?: emptyList()

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("ogrenci_basari_raporu.csv"))
                zos.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for ((idx, wav) in wavFiles.withIndex()) {
                    zos.putNextEntry(ZipEntry("ses_kayitlari/${String.format("%02d", idx + 1)}_${wav.name}"))
                    wav.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            val summaryShareText = buildString {
                append("📊 MUALLİMUN.NET ARAPÇA TELAFFUZ VE KONUŞMA ÖDEV KARNESİ\n")
                append("👤 Öğrenci: $studentName\n")
                append("🏆 Seviye: $levelTitle | 🔥 Seri: $streakDays Gün | ⭐ Puan: $totalPoints\n")
                append("🎧 Son Dinle & Bul Sınavı: $lastExamSummary\n")
                append("📦 Kaydedilen Ödev Arşivi: ${zipFile.name} (${wavFiles.size} ses kaydı dahil)\n\n")
                if (difficultRows.isNotEmpty()) {
                    append("⚠️ En Çok Tekrar Edilmesi Gereken İfadeler:\n")
                    difficultRows.take(5).forEach { d ->
                        append("• ${d.arapca} (${d.turkce}) — Doğru: ${d.dogru} / Yanlış: ${d.yanlis}\n")
                    }
                }
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Arapça Konuşma ve Telaffuz Ödev Karnesi — $studentName")
                putExtra(Intent.EXTRA_TEXT, summaryShareText)
            }
            context.startActivity(Intent.createChooser(sendIntent, "📦 Ödev Karnesini Öğretmenle Paylaş"))
        } catch (_: Exception) {
        }
    }
}
