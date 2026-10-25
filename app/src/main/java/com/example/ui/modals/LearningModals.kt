package com.example.ui.modals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.core.content.ContextCompat
import com.example.data.ListeningQuestion
import com.example.data.SozlukEntity
import com.example.nlp.ArabicNlpEngine
import com.example.ui.components.HexagonRootBadge
import com.example.ui.components.LiveWaveformBars
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AmiriArabicFontFamily
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TealAccent
import com.example.ui.theme.WarmGold
import com.example.viewmodel.AppMode
import com.example.viewmodel.MainAppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================
// 1. 📖 SARF SÖZLÜĞÜ VE SÜLÂSÎ KÖK AİLESİ KEŞİF ATÖLYESİ
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SarfExplorerModal(
    viewModel: MainAppViewModel,
    initialQuery: String,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val allSozluk by viewModel.allSozluk.collectAsState()

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedTur by remember { mutableStateOf("Tümü") } // "Tümü", "isim", "fiil"
    var sortCol by remember { mutableStateOf("kok") } // "kok", "ar", "tr"
    var sortDesc by remember { mutableStateOf(false) }

    val filteredEntries = remember(allSozluk, searchQuery, selectedTur, sortCol, sortDesc) {
        val q = searchQuery.trim().lowercase()
        val qNormAr = ArabicNlpEngine.stripAl(ArabicNlpEngine.normalizeArabic(q))
        val base = allSozluk.filter { item ->
            val turOk = selectedTur == "Tümü" || item.tur == selectedTur
            if (!turOk) return@filter false
            if (q.isEmpty()) return@filter true

            val hayTr = "${item.id} ${item.anlamTr}".lowercase()
            val hayAr = "${item.kok} ${item.mufret} ${item.cemi} ${item.mazi} ${item.muzari} ${item.masdar}"
            val hayNorm = ArabicNlpEngine.normalizeArabic(hayAr)
            hayTr.contains(q) || hayAr.contains(q) || (qNormAr.isNotEmpty() && hayNorm.contains(qNormAr))
        }

        val sorted = when (sortCol) {
            "ar" -> base.sortedBy { ArabicNlpEngine.removeDiacritics(if (it.tur == "isim") it.mufret else it.mazi) }
            "tr" -> base.sortedBy { it.anlamTr.lowercase() }
            else -> base.sortedBy { it.kok }
        }
        if (sortDesc) sorted.reversed() else sorted
    }

    var selectedEntryId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Kompakt Başlık ve Tek Satır Bilgi
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📖 Sarf Sözlüğü & Kök Atölyesi",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Bulunan: ${filteredEntries.size} madde • Kök veya anlamla arayın",
                        fontSize = 11.sp,
                        color = palette.okGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Arama Kutusu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "🔍 Kök (ك ت ب), Arapça veya Türkçe anlam ara...",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sarf_search_input")
                )
                if (searchQuery.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.btnSec,
                        modifier = Modifier.clickable { searchQuery = "" }
                    ) {
                        Text(
                            text = "🧹",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 1. Satır (Tür Filtresi): Tümü, 🏷️ İsim, ⚡ Fiil eşit genişlikte
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Tümü" to "Tümü", "isim" to "🏷️ İsim", "fiil" to "⚡ Fiil").forEach { (key, label) ->
                    val isSelected = selectedTur == key
                    Button(
                        onClick = { selectedTur = key },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) EmeraldGreen else palette.btnSec,
                            contentColor = if (isSelected) Color(0xFF0F172A) else palette.textMain
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Satır (Sıralama): Kök ⇅, Arapça ⇅, Türkçe ⇅ eşit genişlikte ve tek satır
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("kok" to "Kök", "ar" to "Arapça", "tr" to "Türkçe").forEach { (colKey, colLbl) ->
                    val isActive = sortCol == colKey
                    val arrow = if (isActive) (if (sortDesc) " ▼" else " ▲") else " ⇅"
                    Button(
                        onClick = {
                            if (sortCol == colKey) sortDesc = !sortDesc
                            else {
                                sortCol = colKey
                                sortDesc = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) RoyalBlue else palette.btnSec,
                            contentColor = if (isActive) Color.White else palette.textMain
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            text = "$colLbl$arrow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sözlük Maddeleri Listesi
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredEntries, key = { it.id }) { item ->
                    val isSelected = selectedEntryId == item.id
                    val primaryAr = if (item.tur == "isim") item.mufret else item.mazi
                    val turBadgeText = if (item.tur == "isim") "🏷️ İsim" else "⚡ Fiil"
                    val turBadgeColor = if (item.tur == "isim") TealAccent else WarmGold

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) palette.panelColor else palette.cardColor
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) WarmGold else palette.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedEntryId = if (isSelected) null else item.id
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Sol Kök Kutusu: Sade çerçeveli kutu içinde kök harfleri ve 🔍 Kök Ailesi rozeti
                                HexagonRootBadge(
                                    rootText = item.kok,
                                    onClick = {
                                        searchQuery = if (searchQuery == item.kok) "" else item.kok
                                    }
                                )

                                // Orta İçerik: Harekeli Arapça kelime (22.sp), Türkçe anlamı ve sadece doğru tür/seviye rozeti
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = primaryAr,
                                        fontFamily = AmiriArabicFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        color = palette.textArabic,
                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                    )
                                    Text(
                                        text = item.anlamTr,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.textMain
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = turBadgeColor.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, turBadgeColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = turBadgeText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (palette.isDark) turBadgeColor else palette.textMain,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (item.seviye.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = palette.btnSec
                                            ) {
                                                Text(
                                                    text = item.seviye,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.accentHead,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Sağ Etkileşim Butonu: Doğrudan seslendirme yapan belirgin 🔊 (Dinle) butonu
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = RoyalBlue,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.audioManager.speakArabic(primaryAr, "male", viewModel.slowTts.value)
                                        }
                                        .testTag("sarf_speak_${item.id}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "🔊",
                                            fontSize = 18.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Karta tıklandığında açılan Çekim / Türev ve Telaffuz Pratiği alanı
                            if (isSelected) {
                                val morphInfo = remember(item, allSozluk) {
                                    viewModel.repository.resolveWordMorphology(item.id, allSozluk)
                                }

                                morphInfo?.forms?.takeIf { it.isNotEmpty() }?.let { forms ->
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        forms.forEach { f ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = palette.bgColor,
                                                border = BorderStroke(1.dp, Color(f.colorHex)),
                                                modifier = Modifier.clickable {
                                                    viewModel.audioManager.speakArabic(f.arabic, "male", viewModel.slowTts.value)
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "${f.label}: ",
                                                        fontSize = 11.sp,
                                                        color = palette.textDim,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "🔊 ${f.arabic}",
                                                        fontFamily = AmiriArabicFontFamily,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = palette.textArabic,
                                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                morphInfo?.family?.takeIf { it.isNotEmpty() }?.let { famList ->
                                    Text(
                                        text = "🌿 Aynı Sülâsî Kökten (${item.kok}) Türeyen Akraba Kelimeler:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentHead
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        famList.forEach { fam ->
                                            val headAr = if (fam.tur == "isim") fam.mufret else fam.mazi
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = palette.bgColor,
                                                border = BorderStroke(1.dp, palette.border),
                                                modifier = Modifier.clickable {
                                                    viewModel.audioManager.speakArabic(headAr, "male", viewModel.slowTts.value)
                                                }
                                            ) {
                                                Text(
                                                    text = "🔊 $headAr (${fam.anlamTr.substringBefore(",")})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = palette.textMain,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        onDismiss()
                                        viewModel.openWordFromSarf(item)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreen,
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .testTag("btn_practice_in_card")
                                ) {
                                    Text(
                                        text = "🎯 Bu Kelimeyi Kartta Telaffuz Çalış",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. 🎧 DİNLE & BUL (İSTİMÂ) 4 ŞIKLI SESLİ SINAV MERKEZİ
// =============================================================================
@Composable
fun ListeningExamModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val scope = rememberCoroutineScope()

    val allWords by viewModel.allWords.collectAsState()
    val allSentences by viewModel.allSentences.collectAsState()
    val allDialogues by viewModel.allDialogues.collectAsState()
    val allDialogueLines by viewModel.allDialogueLines.collectAsState()
    val wordPackages by viewModel.wordPackages.collectAsState()
    val sentencePackages by viewModel.sentencePackages.collectAsState()

    val sourceOptions = listOf(
        "words" to "🏠 Görselli Kelimeler",
        "dictionary" to "📖 Sarf Sözlüğü",
        "sentences" to "📜 Anahtar Cümleler",
        "dialogue" to "💬 Diyalog Cümleleri"
    )
    var selectedSourceKey by remember { mutableStateOf("words") }
    var selectedPaketId by remember { mutableStateOf("all") }
    var selectedLevel by remember { mutableStateOf("Tümü") }
    var selectedTargetCount by remember { mutableStateOf("10 Soru") } // "10 Soru", "20 Soru", "50 Soru", "♾️ Sınırsız"
    var showArabicHint by remember { mutableStateOf(false) }

    var showSourceMenu by remember { mutableStateOf(false) }
    var showPaketMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }
    var showCountMenu by remember { mutableStateOf(false) }

    val availablePackages = remember(selectedSourceKey, wordPackages, sentencePackages, allDialogues) {
        when (selectedSourceKey) {
            "words" -> listOf("all" to "📦 Tüm Görselli Paketler") +
                wordPackages.filter { !it.id.startsWith("szl_") }.map { it.id to "📦 ${it.ad}" }
            "dictionary" -> listOf("all" to "📖 Tüm Sarf Sözlüğü") +
                wordPackages.filter { it.id.startsWith("szl_") }.map { it.id to "📖 ${it.ad}" }
            "dialogue" -> listOf("all" to "💬 Tüm Diyaloglar") +
                allDialogues.map { it.id to "💬 [${it.seviye}] ${it.baslikTr}" }
            else -> listOf("all" to "📜 Tüm Cümle Paketleri") +
                sentencePackages.map { it.id to "📜 ${it.ad}" }
        }
    }

    var questions by remember { mutableStateOf<List<ListeningQuestion>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }
    var examStarted by remember { mutableStateOf(false) }
    var roundFinished by remember { mutableStateOf(false) }
    var chosenOption by remember { mutableStateOf<String?>(null) }

    fun startNewExam() {
        val countNum = if (selectedTargetCount.contains("Sınırsız")) 25 else {
            selectedTargetCount.substringBefore(" ").toIntOrNull() ?: 10
        }
        val qs = viewModel.repository.generateListeningExamQuestions(
            sourceMode = selectedSourceKey,
            paketId = selectedPaketId,
            seviye = selectedLevel,
            count = countNum,
            allWords = allWords,
            allSentences = allSentences,
            allDialogues = allDialogues,
            allDialogueLines = allDialogueLines
        )
        questions = qs
        currentIndex = 0
        correctCount = 0
        wrongCount = 0
        chosenOption = null
        roundFinished = false
        examStarted = qs.isNotEmpty()
        if (qs.isNotEmpty()) {
            viewModel.audioManager.speakArabic(qs[0].arapca, "male", viewModel.slowTts.value)
        } else {
            viewModel.showToast("Bu filtrede 4 şıklı sınav için yeterli kayıt bulunamadı.")
        }
    }

    val currentQuestion = if (examStarted && currentIndex in questions.indices) questions[currentIndex] else null

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎧 DİNLE & BUL — SESLİ SINAV MERKEZİ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentHead
                        )
                        val totAns = correctCount + wrongCount
                        val accPct = if (totAns > 0) ((correctCount.toDouble() / totAns) * 100).roundToInt() else 0
                        Text(
                            text = if (examStarted) {
                                "Soru: ${currentIndex + 1}/${questions.size}  |  Doğru: $correctCount  |  Yanlış: $wrongCount  |  Başarı: %$accPct"
                            } else {
                                "Hazır • Başlamak için '▶️ SINAVI BAŞLAT' butonuna basın"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.okGreen
                        )
                    }
                    IconButton(onClick = {
                        viewModel.audioManager.stopAllAudio()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                    }
                }

                // Kaynak, Paket/Diyalog, Seviye ve Soru Sayısı Seçici + Sınav Başlatma ve Ses/İpucu Butonları
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Satır: Kaynak ve Paket / Diyalog Seçimi
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showSourceMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val lbl = sourceOptions.firstOrNull { it.first == selectedSourceKey }?.second ?: ""
                                    Text(
                                        text = lbl,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentHead,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead)
                                }
                                DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                                    sourceOptions.forEach { (k, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                showSourceMenu = false
                                                selectedSourceKey = k
                                                selectedPaketId = "all"
                                                examStarted = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showPaketMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("listening_paket_selector")
                                ) {
                                    val pkgLbl = availablePackages.firstOrNull { it.first == selectedPaketId }?.second
                                        ?: "📦 Tümü"
                                    Text(
                                        text = pkgLbl,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMain)
                                }
                                DropdownMenu(expanded = showPaketMenu, onDismissRequest = { showPaketMenu = false }) {
                                    availablePackages.forEach { (pkgKey, pkgLabel) ->
                                        DropdownMenuItem(
                                            text = { Text(pkgLabel, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                showPaketMenu = false
                                                selectedPaketId = pkgKey
                                                examStarted = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Satır: Seviye Seçimi ve Soru Sayısı (Yan Yana Eşit Genişlik - weight(1f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showLevelMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Seviye: $selectedLevel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMain)
                                }
                                DropdownMenu(expanded = showLevelMenu, onDismissRequest = { showLevelMenu = false }) {
                                    listOf("Tümü", "A1", "A2", "B1", "B2", "C1").forEach { lvl ->
                                        DropdownMenuItem(
                                            text = { Text(lvl, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                showLevelMenu = false
                                                selectedLevel = lvl
                                                examStarted = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showCountMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Hedef: $selectedTargetCount",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentHead,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead)
                                }
                                DropdownMenu(expanded = showCountMenu, onDismissRequest = { showCountMenu = false }) {
                                    listOf("10 Soru", "20 Soru", "50 Soru", "♾️ Sınırsız").forEach { cnt ->
                                        DropdownMenuItem(
                                            text = { Text(cnt, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                showCountMenu = false
                                                selectedTargetCount = cnt
                                                examStarted = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Satır: ▶️ SINAVI BAŞLAT (Tam Genişlikte Tek Başına - height(48.dp))
                        Button(
                            onClick = { startNewExam() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (examStarted) RoyalBlue else EmeraldGreen,
                                contentColor = if (examStarted) Color.White else Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_start_listening_exam")
                        ) {
                            Text(
                                text = if (examStarted) "🔄 Yeniden Başlat" else "▶️ SINAVI BAŞLAT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }

                        // 4. Satır: 🔊 Sesi Dinle ve 👁️ İpucu Yan Yana (Eşit Genişlik - weight(1f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    currentQuestion?.let { q ->
                                        viewModel.audioManager.speakArabic(q.arapca, "male", viewModel.slowTts.value)
                                    }
                                },
                                enabled = examStarted && currentQuestion != null,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🔊 Sesi Dinle", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Button(
                                onClick = { showArabicHint = !showArabicHint },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showArabicHint) WarmGold else palette.btnSec,
                                    contentColor = if (showArabicHint) Color(0xFF0F172A) else palette.textMain
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = if (showArabicHint) "👁️ İpucu Açık" else "👁️ İpucu",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Soru Metni / İpucu Alanı
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.cardColor),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        if (!examStarted || currentQuestion == null) {
                            Text(
                                text = "🎯 Yukarıdan kaynak, seviye ve soru hedefini seçip '▶️ SINAVI BAŞLAT' butonuna basın.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead,
                                textAlign = TextAlign.Center
                            )
                        } else if (showArabicHint || chosenOption != null) {
                            Text(
                                text = currentQuestion.arapca,
                                fontFamily = AmiriArabicFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = palette.textArabic,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "🔒 [ Seslendirilen Arapça İfadeyi Dinleyip Doğru Türkçe Karşılığını Seçin ]",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textArabic,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 4 Şıklı Seçenekler (A, B, C, D)
                val letters = listOf("A", "B", "C", "D")
                for (i in 0 until 4) {
                    val optText = currentQuestion?.options?.getOrNull(i)
                        ?: "[ Sınav başladığında seçenekler burada görünecektir ]"
                    val isEnabled = examStarted && currentQuestion != null && chosenOption == null

                    val bgCol = when {
                        chosenOption == null -> palette.panelColor
                        optText == currentQuestion?.correctTr -> EmeraldGreen
                        optText == chosenOption -> CoralRed
                        else -> palette.panelColor
                    }
                    val fgCol = when {
                        chosenOption != null && optText == currentQuestion?.correctTr -> Color(0xFF0F172A)
                        chosenOption != null && optText == chosenOption -> Color.White
                        examStarted -> palette.textMain
                        else -> palette.textDim
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = bgCol),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) {
                                val q = currentQuestion ?: return@clickable
                                chosenOption = optText
                                val isOk = optText == q.correctTr
                                if (isOk) {
                                    correctCount++
                                    viewModel.audioManager.playPraiseTone()
                                } else {
                                    wrongCount++
                                }
                                scope.launch {
                                    viewModel.repository.recordPracticeOutcome(q.id, q.mode, isOk)
                                    delay(1150)
                                    if (currentIndex + 1 < questions.size) {
                                        currentIndex++
                                        chosenOption = null
                                        viewModel.audioManager.speakArabic(
                                            questions[currentIndex].arapca,
                                            "male",
                                            viewModel.slowTts.value
                                        )
                                    } else {
                                        roundFinished = true
                                        val tot = correctCount + wrongCount
                                        val pct = if (tot > 0) ((correctCount.toDouble() / tot) * 100).roundToInt() else 0
                                        viewModel.saveLastListeningExamSummary(
                                            "Çözülen: $tot | Doğru: $correctCount | Yanlış: $wrongCount | Başarı: %$pct"
                                        )
                                    }
                                }
                            }
                            .testTag("listening_option_$i")
                    ) {
                        Text(
                            text = "${letters[i]})   $optText",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = fgCol,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }

                if (roundFinished) {
                    val tot = correctCount + wrongCount
                    val pct = if (tot > 0) ((correctCount.toDouble() / tot) * 100).roundToInt() else 0
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                        border = BorderStroke(2.dp, EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🏆 Tur Tamamlandı! Çözülen: $tot  |  Doğru: $correctCount  |  Yanlış: $wrongCount  |  Başarı: %$pct",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { startNewExam() },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔄 Yeni Sınav Başlat", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain)
                                ) {
                                    Text("Kapat", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
        }
    }
}

// =============================================================================
// 3. 🗣️ GÖLGELEME (SHADOWING) AKICILIK VE RİTİM ANTRENMANI
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowingTrainerModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current
    val target by viewModel.shadowingTarget.collectAsState()
    val liveWaveBars by viewModel.audioManager.waveBars.collectAsState()

    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Hazır olduğunuzda '🎬 Gölgelemeyi Başlat' butonuna basın.") }
    var refDurationSec by remember { mutableStateOf(2.2f) }
    var userDurationSec by remember { mutableStateOf(0.0f) }
    var pronScorePct by remember { mutableIntStateOf(0) }
    var rhythmScorePct by remember { mutableIntStateOf(0) }
    var totalFluencyPct by remember { mutableIntStateOf(0) }
    var coloredWords by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }

    val refBars = remember(target.arabic) {
        val len = target.arabic.length.coerceAtLeast(5)
        List(48) { idx ->
            val v = abs(sin((idx + len) * 0.38)).toFloat()
            (0.18f + v * 0.78f).coerceIn(0.1f, 0.98f)
        }
    }
    var userBars by remember { mutableStateOf(List(48) { 0.08f }) }

    LaunchedEffect(liveWaveBars, isRunning) {
        if (isRunning && liveWaveBars.isNotEmpty()) {
            userBars = List(48) { i -> liveWaveBars[i % liveWaveBars.size] }
        }
    }

    LaunchedEffect(target.arabic) {
        val wordCount = target.arabic.split(Regex("\\s+")).size
        refDurationSec = max(1.6f, wordCount * 0.75f)
        coloredWords = emptyList()
        pronScorePct = 0
        rhythmScorePct = 0
        totalFluencyPct = 0
    }

    fun evaluateShadowingResult(heardText: String, durSec: Float) {
        val cmp = ArabicNlpEngine.compareArabic(target.arabic, heardText)
        val eval = ArabicNlpEngine.evaluateSentenceWords(target.arabic, heardText)
        coloredWords = eval.wordResults

        val computedPronPct = (((cmp.score * 0.55) + (eval.ratio * 0.45)) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        val rawDur = if (durSec > 0.1f) durSec else viewModel.audioManager.getLastSpeechDurationSec()
        val roundedDur = ((rawDur * 10f).roundToInt() / 10f).coerceAtLeast(0.4f)
        userDurationSec = roundedDur
        userBars = viewModel.audioManager.getLastSpeechEnvelope48()

        val durDiff = abs(refDurationSec - roundedDur)
        val tempoScore = if (computedPronPct == 0) {
            0
        } else {
            (100f * max(0f, 1f - (durDiff / max(1.5f, refDurationSec))))
                .roundToInt()
                .coerceIn(20, 100)
        }

        pronScorePct = computedPronPct
        rhythmScorePct = tempoScore
        totalFluencyPct = ((pronScorePct * 0.65) + (rhythmScorePct * 0.35)).roundToInt().coerceIn(0, 100)

        statusText = when {
            pronScorePct >= 80 && totalFluencyPct >= 75 ->
                "✅ Harika Akıcılık ve Telaffuz! (Algılanan: \"$heardText\") — Genel Puan: %$totalFluencyPct"
            pronScorePct >= 50 ->
                "🟡 İyi Deneme! Kırmızı kelimelere dikkat edin (Algılanan: \"$heardText\") — Genel Puan: %$totalFluencyPct"
            else ->
                "⚠️ Telaffuz Eşleşmedi (Algılanan: \"$heardText\") — Genel Puan: %$totalFluencyPct"
        }
        isRunning = false
    }

    fun startShadowingCycle() {
        if (isRunning) return
        isRunning = true
        statusText = "🔊 1. Adım: Referans ses dinleniyor, dikkatle takip edin..."

        viewModel.audioManager.speakArabic(target.arabic, "male", viewModel.slowTts.value) {
            statusText = "🎙️ 2. Adım: ŞİMDİ AYNI HIZ VE TONLAMAYLA TEKRAR EDİN!"
            viewModel.audioManager.startSpeechRecognition(
                itemIdPrefix = "shadow_${target.itemId}"
            ) { heardText, durSec, errMsg ->
                if (!errMsg.isNullOrBlank() && heardText.isBlank()) {
                    coloredWords = ArabicNlpEngine.evaluateSentenceWords(target.arabic, "").wordResults
                    pronScorePct = 0
                    rhythmScorePct = 0
                    totalFluencyPct = 0
                    statusText = "⚠️ $errMsg — Tekrar deneyin."
                    isRunning = false
                } else {
                    evaluateShadowingResult(heardText, durSec)
                }
            }
        }
    }

    val shadowPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startShadowingCycle()
        } else {
            viewModel.showToast("Mikrofon izni verilmedi.")
        }
    }

    fun requestMicAndStartShadowing() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            startShadowingCycle()
        } else {
            shadowPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🗣️ GÖLGELEME (SHADOWING) AKICILIK ANTRENMANI",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentHead
                        )
                        Text(
                            text = "Dinle ➔ Beklemeden Aynı Hız ve Tonlamayla Tekrar Et",
                            fontSize = 11.sp,
                            color = palette.textDim
                        )
                    }
                    IconButton(onClick = {
                        viewModel.audioManager.stopAllAudio()
                        viewModel.audioManager.stopSpeechRecognition()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                    }
                }

                // Metin Kartı
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.cardColor),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (coloredWords.isNotEmpty()) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    coloredWords.forEach { (wStr, isOk) ->
                                        Text(
                                            text = wStr,
                                            fontFamily = AmiriArabicFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 28.sp,
                                            color = if (isOk) palette.okGreen else CoralRed,
                                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = target.arabic,
                                fontFamily = AmiriArabicFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                color = palette.textArabic,
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                            )
                        }

                        if (target.turkish.isNotBlank()) {
                            Text(
                                text = target.turkish,
                                fontSize = 14.sp,
                                color = palette.textDim,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Çift Dalga Boyu Karşılaştırma Kutusu (Referans vs Öğrenci)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🔊 Referans Ses Dalga Boyu ve Ritmi (Süre: $refDurationSec sn)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoyalBlue
                        )
                        LiveWaveformBars(
                            bars = refBars,
                            isRecording = false,
                            barColorOverride = RoyalBlue,
                            modifier = Modifier.height(54.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "🎙️ Sizin Ses Dalga Boyunuz ve Ritminiz (Süre: $userDurationSec sn)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.okGreen
                        )
                        LiveWaveformBars(
                            bars = userBars,
                            isRecording = isRunning,
                            barColorOverride = palette.okGreen,
                            modifier = Modifier.height(54.dp)
                        )
                    }
                }

                // Durum ve Skorlar
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (totalFluencyPct >= 70) palette.okGreen else palette.accentHead,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (totalFluencyPct > 0) {
                    Text(
                        text = "🎯 Telaffuz Doğruluğu: %$pronScorePct   |   🎵 Ritim & Süre Uyumu: %$rhythmScorePct (Referans: ${refDurationSec}sn / Siz: ${userDurationSec}sn)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textMain,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Kontrol Butonları
                Button(
                    onClick = { requestMicAndStartShadowing() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_start_shadowing_cycle")
                ) {
                    Text(
                        text = if (isRunning) "⏳ Gölgeleme Çalışıyor..." else "🎬 Gölgelemeyi Başlat (Dinle & Anında Tekrar Et)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.audioManager.speakArabic(target.arabic, "male", viewModel.slowTts.value)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔊 Referansı Dinle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val ok = viewModel.audioManager.playSavedWavFile()
                            if (!ok) viewModel.showToast("Henüz kayıtlı sesiniz bulunmuyor.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🎧 Sesimi Dinle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
        }
    }
}
