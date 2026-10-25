package com.example.ui.modals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.SentenceEntity
import com.example.data.WordEntity
import com.example.nlp.ArabicNlpEngine
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AmiriArabicFontFamily
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TealAccent
import com.example.ui.theme.WarmGold
import com.example.util.ExamGeneratorConfig
import com.example.util.WorksheetAndExportHelper
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.MainAppViewModel
import kotlinx.coroutines.launch

// =============================================================================
// 1. 📊 ÖĞRENCİ & ÖĞRETMEN ANALİZ, ROZET, SES DİNLEME VE ÖDEV PAKETİ EKRANI
// =============================================================================
@Composable
fun AnalyticsAndReportsModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current

    val studentName by viewModel.studentName.collectAsState()
    val streak by viewModel.streakDays.collectAsState()
    val dailyCount by viewModel.dailyCount.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val totalPoints by viewModel.totalPoints.collectAsState()
    val lastExam by viewModel.lastListeningExamSummary.collectAsState()

    val allWords by viewModel.allWords.collectAsState()
    val allSentences by viewModel.allSentences.collectAsState()
    val allDialogueLines by viewModel.allDialogueLines.collectAsState()

    val badges = remember(allWords, allSentences, allDialogueLines, streak, totalPoints) {
        viewModel.getBadgesList()
    }
    val packageRows = remember(allWords, allSentences, allDialogueLines) {
        viewModel.getPackageAnalyticsRows()
    }
    val difficultRows = remember(allWords, allSentences, allDialogueLines) {
        viewModel.getMostDifficultRows()
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "👤 $studentName  |  ${viewModel.getLevelTitle()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentHead
                        )
                        Text(
                            text = "🔥 Seri: $streak Gün  |  🎯 Bugün: $dailyCount/$dailyGoal  |  ⭐ Puan: $totalPoints",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.okGreen
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = palette.panelColor,
                    contentColor = palette.accentHead
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("🏅 Rozetler", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📈 Paket İlerleme", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("⚠️ Zayıflar & Ses", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🎧 Son Dinle & Bul Sınav Sonucu:\n$lastExam",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.accentHead,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                items(badges) { b ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (b.unlocked) palette.panelColor else palette.cardColor
                                        ),
                                        border = BorderStroke(
                                            if (b.unlocked) 2.dp else 1.dp,
                                            if (b.unlocked) EmeraldGreen else palette.border
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "${b.title}   [${if (b.unlocked) "✅ KAZANILDI" else "🔒 KİLİTLİ"}]",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (b.unlocked) palette.okGreen else palette.textDim
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = b.description,
                                                fontSize = 12.sp,
                                                color = if (b.unlocked) palette.textMain else palette.textDim
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(packageRows) { row ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "${row.name} (${row.learned}/${row.total})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.textMain,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "Tamamlama: %${row.completionPct} | Başarı: %${row.accuracyPct}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.accentHead
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { (row.completionPct / 100f).coerceIn(0f, 1f) },
                                                color = if (row.completionPct >= 70) EmeraldGreen else RoyalBlue,
                                                trackColor = palette.bgColor,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (difficultRows.isEmpty()) {
                                    item {
                                        Text(
                                            text = "🎉 Henüz hatalı okunan kayıt bulunmuyor!",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.okGreen,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                } else {
                                    items(difficultRows) { d ->
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                                            border = BorderStroke(1.dp, palette.border),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "[${d.kind}] ${d.turkce}",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = palette.textMain
                                                        )
                                                        Text(
                                                            text = "Doğru: ${d.dogru}  |  Yanlış: ${d.yanlis}",
                                                            fontSize = 11.sp,
                                                            color = CoralRed,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(
                                                        text = d.arapca,
                                                        fontFamily = AmiriArabicFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 22.sp,
                                                        color = palette.textArabic
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            viewModel.audioManager.speakArabic(d.arapca, "male", viewModel.slowTts.value)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("🔊 Doğrusunu Dinle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            val wav = viewModel.audioManager.findLatestStudentWavForId(d.id)
                                                            if (wav != null) {
                                                                viewModel.audioManager.playSavedWavFile(wav.absolutePath)
                                                            } else {
                                                                viewModel.showToast("Bu kayıt için saklanmış öğrenci ses dosyası (.wav) bulunamadı.")
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("🎧 Öğrenci Sesini Dinle (.wav)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Alt Aksiyon Çubuğu: 📦 Ödev & Ses Paketi (.ZIP / CSV) Paylaş | 🖨️ Yazılı Sınav Üret
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Button(
                        onClick = {
                            WorksheetAndExportHelper.shareHomeworkReportAndCsv(
                                context = context,
                                studentName = studentName,
                                levelTitle = viewModel.getLevelTitle(),
                                streakDays = streak,
                                totalPoints = totalPoints,
                                lastExamSummary = lastExam,
                                analyticsRows = packageRows,
                                difficultRows = difficultRows,
                                recordingsDir = viewModel.audioManager.recordingsDir
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .testTag("btn_share_homework_zip")
                    ) {
                        Text(
                            text = "📦 Ödev & Karne Paylaş",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { viewModel.openModal(ActiveModal.WORKSHEET_GENERATOR) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = "🖨️ Sınav Üret",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
        }
    }
}

// =============================================================================
// 2. 🖨️ A4 YAZILI SINAV, KARMA QUİZ VE 📖 'DERS NOTU / OKUMA METNİ' PDF ÜRETİCİ
// =============================================================================
@Composable
fun WorksheetAndHandoutGeneratorModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current

    val allWords by viewModel.allWords.collectAsState()
    val allSozluk by viewModel.allSozluk.collectAsState()
    val allDialogues by viewModel.allDialogues.collectAsState()
    val allDialogueLines by viewModel.allDialogueLines.collectAsState()
    val allSentences by viewModel.allSentences.collectAsState()

    val sourceModes = listOf(
        "words" to "🏠 Kelimeler & Sarf Sözlüğü",
        "dialogue" to "💬 Günlük Diyaloglar",
        "sentences" to "📜 Anahtar Cümleler",
        "all_mixed" to "🌟 Karma Sınav (Kelime + Diyalog + Cümle Birlikte)"
    )

    var selectedSourceMode by remember { mutableStateOf("words") }
    var selectedLevel by remember { mutableStateOf("Tümü") }
    var sheetTitle by remember { mutableStateOf("Arapça Dersi Kelime, Sarf ve Anlama Yazılı Sınavı") }

    var arToTrCount by remember { mutableIntStateOf(8) }
    var trToArCount by remember { mutableIntStateOf(8) }
    var fillBlankCount by remember { mutableIntStateOf(5) }
    var mcQuizCount by remember { mutableIntStateOf(5) }
    var matchingCount by remember { mutableIntStateOf(5) }
    var sarfCount by remember { mutableIntStateOf(5) }

    var includeSummaryTable by remember { mutableStateOf(false) }
    var includeAnswerKeyPage by remember { mutableStateOf(true) }

    var useHandoutMode by remember { mutableStateOf(false) }
    var handoutKeepHaraka by remember { mutableStateOf(true) }
    var handoutIncludeTr by remember { mutableStateOf(true) }
    var handoutTrLayout by remember { mutableStateOf("tr_layout_side") }

    var showSourceMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🖨️ A4 YAZILI SINAV, DERS NOTU & PDF ÜRETİCİ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                    }
                }

                // Materyal Kaynağı ve Seviye
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showSourceMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val lbl = sourceModes.firstOrNull { it.first == selectedSourceMode }?.second ?: ""
                            Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.accentHead, maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead)
                        }
                        DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                            sourceModes.forEach { (k, lbl) ->
                                DropdownMenuItem(
                                    text = { Text(lbl, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showSourceMenu = false
                                        selectedSourceMode = k
                                        if (k !in listOf("dialogue", "sentences")) {
                                            useHandoutMode = false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Seviye: $selectedLevel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textMain)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMain)
                        }
                        DropdownMenu(expanded = showLevelMenu, onDismissRequest = { showLevelMenu = false }) {
                            listOf("Tümü", "A1", "A2", "B1", "B2", "C1").forEach { lvl ->
                                DropdownMenuItem(
                                    text = { Text(lvl, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showLevelMenu = false
                                        selectedLevel = lvl
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = sheetTitle,
                    onValueChange = { sheetTitle = it },
                    label = { Text("Sınav / Ders Notu Başlığı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bölüm A: Yazılı Sınav & Quiz Soru Sayıları (Ayrık Havuzlu)
                if (!useHandoutMode) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🔢 Yazılı Sınav & Quiz Soru Sayıları (İstemediğiniz Bölümü 0 Yapabilirsiniz)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead
                            )

                            QuestionCountStepper("1. Bölüm — Arapça ➔ Türkçe Yazma:", arToTrCount, 30) { arToTrCount = it }
                            QuestionCountStepper("2. Bölüm — Türkçe ➔ Arapça Yazma:", trToArCount, 30) { trToArCount = it }
                            QuestionCountStepper("3. Bölüm — Boşluk Doldurma:", fillBlankCount, 30) { fillBlankCount = it }
                            QuestionCountStepper("4. Bölüm — Çoktan Seçmeli (4 Şıklı) Test:", mcQuizCount, 30) { mcQuizCount = it }
                            QuestionCountStepper("5. Bölüm — Eşleştirme Alıştırması:", matchingCount, 20) { matchingCount = it }
                            QuestionCountStepper("6. Bölüm — Sarf (Kök & Türev) Tablosu:", sarfCount, 20) { sarfCount = it }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeSummaryTable, onCheckedChange = { includeSummaryTable = it })
                                Text("📋 Sayfa Başına Konu Özeti Tablosu Ekle", fontSize = 12.sp, color = palette.textMain)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeAnswerKeyPage, onCheckedChange = { includeAnswerKeyPage = it })
                                Text("🔑 Cevap Anahtarını Ayrı Sayfada Bas (page-break)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
                            }
                        }
                    }
                }

                // Bölüm B: Diyalog ve Cümleler İçin "Ders Notu / Okuma Metni (Handout)" Formatı
                if (selectedSourceMode in listOf("dialogue", "sentences")) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                        border = BorderStroke(1.dp, palette.okGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = useHandoutMode,
                                    onCheckedChange = {
                                        useHandoutMode = it
                                        sheetTitle = if (it) {
                                            "Arapça Diyalog ve Okuma Metni Ders Notu"
                                        } else {
                                            "Arapça Dersi Kelime, Sarf ve Anlama Yazılı Sınavı"
                                        }
                                    }
                                )
                                Text(
                                    text = "📖 Sınav Bölümlerini Yoksay ve 'Temiz Ders Notu / Okuma Metni (Handout)' Olarak Üret",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.okGreen
                                )
                            }

                            if (useHandoutMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = handoutKeepHaraka, onCheckedChange = { handoutKeepHaraka = it })
                                        Text("🔤 Harekeli Göster", fontSize = 12.sp, color = palette.textMain)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = handoutIncludeTr, onCheckedChange = { handoutIncludeTr = it })
                                        Text("🇹🇷 Türkçe Tercümeyi Dahil Et", fontSize = 12.sp, color = palette.textMain)
                                    }
                                }

                                if (handoutIncludeTr) {
                                    Text("Tercüme Yerleşimi:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textDim)
                                    listOf(
                                        "tr_layout_side" to "Yan Yana (İki Sütun: Sol Arapça %50 RTL, Sağ Türkçe %50 LTR)",
                                        "tr_layout_below" to "Alt Alta (Her Arapça cümlenin hemen altında Türkçesi)",
                                        "tr_layout_end" to "Sonda Toplu (Tüm Arapça metinden sonra sayfa sonunda Türkçesi)"
                                    ).forEach { (valKey, label) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { handoutTrLayout = valKey }
                                        ) {
                                            RadioButton(selected = handoutTrLayout == valKey, onClick = { handoutTrLayout = valKey })
                                            Text(label, fontSize = 11.sp, color = palette.textMain)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val cfg = ExamGeneratorConfig(
                            sourceMode = selectedSourceMode,
                            seviye = selectedLevel,
                            sheetTitle = sheetTitle,
                            arToTrCount = arToTrCount,
                            trToArCount = trToArCount,
                            fillBlankCount = fillBlankCount,
                            multipleChoiceCount = mcQuizCount,
                            matchingCount = matchingCount,
                            sarfTableCount = sarfCount,
                            includeSummaryTable = includeSummaryTable,
                            includeAnswerKeyPage = includeAnswerKeyPage,
                            useHandoutMode = useHandoutMode,
                            handoutKeepHaraka = handoutKeepHaraka,
                            handoutIncludeTr = handoutIncludeTr,
                            handoutTrLayout = handoutTrLayout
                        )
                        val html = WorksheetAndExportHelper.generateWorksheetOrHandoutHtml(
                            cfg = cfg,
                            allWords = allWords,
                            allSozluk = allSozluk,
                            allDialogues = allDialogues,
                            allDialogueLines = allDialogueLines,
                            allSentences = allSentences
                        )
                        WorksheetAndExportHelper.printHtmlAsPdf(context, html)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_generate_pdf_exam")
                ) {
                    Text(
                        text = "🖨️ A4 MATERYALİ / SINAVI OLUŞTUR VE PDF KAYDET",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
        }
    }
}

@Composable
private fun QuestionCountStepper(
    label: String,
    value: Int,
    maxVal: Int,
    onValueChange: (Int) -> Unit
) {
    val palette = LocalMuallimunPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = palette.textMain,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.btnSec,
                modifier = Modifier.clickable { onValueChange((value - 1).coerceAtLeast(0)) }
            ) {
                Text(" - ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textMain, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(
                text = " $value ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentHead,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.btnSec,
                modifier = Modifier.clickable { onValueChange((value + 1).coerceAtMost(maxVal)) }
            ) {
                Text(" + ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textMain, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

// =============================================================================
// 3. 🛠️ ÖĞRETMEN YÖNETİM & EXCEL PANODAN YAPIŞTIRMA PANELİ (ADMIN PANEL)
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedAdminPanelModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val allPackages by viewModel.wordPackages.collectAsState()
    val allSentencePackages by viewModel.sentencePackages.collectAsState()
    val allDialogues by viewModel.allDialogues.collectAsState()
    val allWords by viewModel.allWords.collectAsState()
    val allSentences by viewModel.allSentences.collectAsState()
    val allDialogueLines by viewModel.allDialogueLines.collectAsState()

    var activeTab by remember { mutableStateOf("words") } // "words", "dialogue", "sentences"
    var searchQuery by remember { mutableStateOf("") }
    var showExcelPasteSection by remember { mutableStateOf(false) }
    var showAuditReport by remember { mutableStateOf(false) }

    var pasteText by remember { mutableStateOf("") }
    var targetPkgId by remember { mutableStateOf("egitim_ilim") }
    var targetPkgTitle by remember { mutableStateOf("Eğitim & İlim") }

    var editingItemId by remember { mutableStateOf<String?>(null) }
    var editingDialogueLineId by remember { mutableStateOf<Int?>(null) }
    var newArabicText by remember { mutableStateOf("") }
    var newTurkishText by remember { mutableStateOf("") }
    var newCefrLevel by remember { mutableStateOf("A1") }
    var newVisualOrEmoji by remember { mutableStateOf("📚") }
    var newAudioRelPath by remember { mutableStateOf("") }
    var newSpeakerName by remember { mutableStateOf("Ömer") }
    var newSpeakerGender by remember { mutableStateOf("E") }

    var statusBanner by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var clearBeforeDbImport by remember { mutableStateOf(false) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val baseId = editingItemId ?: "${activeTab.first()}_${System.currentTimeMillis()}"
        if (editingItemId == null) editingItemId = baseId
        val savedRel = com.example.util.MediaHelper.copyPickedMediaToCustomStorage(
            context = context,
            uri = uri,
            subfolder = "images",
            itemPrefix = baseId,
            defaultExtension = "png"
        )
        if (savedRel != null) {
            newVisualOrEmoji = savedRel
            statusBanner = "🖼️ Görsel kopyalandı: $savedRel"
        } else {
            statusBanner = "⚠️ Görsel dosyası kopyalanamadı."
        }
    }

    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val baseId = editingItemId ?: (editingDialogueLineId?.let { "dlgline_$it" } ?: "${activeTab.first()}_${System.currentTimeMillis()}")
        if (editingItemId == null && activeTab != "dialogue") editingItemId = baseId
        val savedRel = com.example.util.MediaHelper.copyPickedMediaToCustomStorage(
            context = context,
            uri = uri,
            subfolder = "audio",
            itemPrefix = baseId,
            defaultExtension = "mp3"
        )
        if (savedRel != null) {
            newAudioRelPath = savedRel
            statusBanner = "🎵 Ses dosyası kopyalandı: $savedRel"
        } else {
            statusBanner = "⚠️ Ses dosyası kopyalanamadı."
        }
    }

    val dbFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var totalAdded = 0
            val messages = mutableListOf<String>()
            for ((idx, uri) in uris.withIndex()) {
                val fName = uri.lastPathSegment?.substringAfterLast("/") ?: "veritabani_${idx + 1}.db"
                val rep = viewModel.repository.importDesktopDatabaseUri(
                    context = context,
                    uri = uri,
                    fileNameHint = fName,
                    clearExisting = clearBeforeDbImport && idx == 0
                )
                totalAdded += rep.addedCount
                messages.addAll(rep.skippedDetails)
            }
            val detailStr = if (messages.isNotEmpty()) " (${messages.joinToString(" • ")})" else ""
            statusBanner = "✅ Toplam $totalAdded kayıt başarıyla aktarıldı!$detailStr"
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🛠️ YÖNETİM, YENİ KATEGORİ/MEDYA & .DB/.ZIP PANELİ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                    }
                }

                // Hızlı Bakım, Masaüstü .DB/.ZIP Yükleme ve Kalite Araçları
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldGreen,
                        modifier = Modifier
                            .clickable {
                                clearBeforeDbImport = false
                                dbFilePickerLauncher.launch(arrayOf("*/*"))
                            }
                            .testTag("btn_import_desktop_db")
                    ) {
                        Text(
                            text = "📥 Masaüstü .DB / Medyalı .ZIP / .JSON Yükle",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TealAccent,
                        modifier = Modifier
                            .clickable { showExcelPasteSection = !showExcelPasteSection }
                            .testTag("btn_toggle_excel_paste")
                    ) {
                        Text(
                            text = if (showExcelPasteSection) "📋 Excel Panelini Kapat" else "📋 Excel / Panodan Yapıştır",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RoyalPurple,
                        modifier = Modifier.clickable {
                            scope.launch {
                                val fixed = viewModel.repository.repairAllDatabases()
                                statusBanner = "🪄 Metin Doktoru tamamlandı: $fixed kayıt onarıldı!"
                            }
                        }
                    ) {
                        Text(
                            text = "🪄 Metin Doktoru",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RoyalBlue,
                        modifier = Modifier.clickable {
                            scope.launch {
                                val cnt = viewModel.repository.autoAssignCefrLevels(activeTab)
                                statusBanner = "🎯 Sarf ve cümle yapısına göre $cnt kayda otomatik CEFR (A1-C1) seviyesi atandı!"
                            }
                        }
                    ) {
                        Text(
                            text = "🎯 Otomatik Seviye Ata",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WarmGold,
                        modifier = Modifier.clickable { showAuditReport = !showAuditReport }
                    ) {
                        Text(
                            text = "🔍 Kalite Denetimi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                statusBanner?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.panelColor,
                        border = BorderStroke(1.dp, EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.okGreen,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // 📋 Excel / Panodan Kopyala-Yapıştır Bölümü
                if (showExcelPasteSection) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                        border = BorderStroke(1.dp, TealAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📋 Excel veya Tablodan Doğrudan Yapıştır (Arapça [Tab veya ;] Türkçe)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead
                            )
                            OutlinedTextField(
                                value = pasteText,
                                onValueChange = { pasteText = it },
                                placeholder = { Text("Örn:\nمُحَاضَرَةٌ ; Konferans ; B1\nمُسْتَشْفًى ; Hastane ; A2") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("excel_paste_textfield")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text ?: ""
                                        if (clip.isNotBlank()) {
                                            pasteText = if (pasteText.isBlank()) clip else "$pasteText\n$clip"
                                        } else {
                                            statusBanner = "Panoda metin bulunamadı."
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("📋 Panodan Çek", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val rep = viewModel.repository.importFromClipboardOrCsv(
                                                mode = activeTab,
                                                rawText = pasteText,
                                                paketId = targetPkgId.ifBlank { "ozel_paket" },
                                                paketAdi = targetPkgTitle.ifBlank { "Özel Paket" },
                                                defaultSeviye = newCefrLevel
                                            )
                                            statusBanner = "✅ ${rep.addedCount} kayıt eklendi, ${rep.skippedCount} satır atlandı."
                                            if (rep.addedCount > 0) {
                                                pasteText = ""
                                                showExcelPasteSection = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_confirm_excel_import")
                                ) {
                                    Text("📥 Veritabanına Aktar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 🔍 Kalite Denetimi Raporu Özeti
                if (showAuditReport) {
                    val issues = remember(allWords, allSentences, activeTab) {
                        when (activeTab) {
                            "words" -> allWords.mapNotNull { w ->
                                val probs = ArabicNlpEngine.checkItemQuality(w.arapca, w.turkce)
                                if (probs.isEmpty()) null else "${w.id} (${w.turkce}): ${probs.joinToString(", ")}"
                            }
                            "sentences" -> allSentences.mapNotNull { s ->
                                val probs = ArabicNlpEngine.checkItemQuality(s.arapca, s.turkce)
                                if (probs.isEmpty()) null else "${s.id}: ${probs.joinToString(", ")}"
                            }
                            else -> allDialogueLines.mapNotNull { l ->
                                val probs = ArabicNlpEngine.checkItemQuality(l.arapca, l.turkce)
                                if (probs.isEmpty()) null else "Satır #${l.id}: ${probs.joinToString(", ")}"
                            }
                        }
                    }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                        border = BorderStroke(1.dp, WarmGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = if (issues.isEmpty()) "✅ Kalite Denetimi: Tüm kayıtlar harekeli ve eksiksiz!" else "⚠️ Tespit Edilen Kalite Kusurları (${issues.size}):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (issues.isEmpty()) palette.okGreen else WarmGold
                            )
                            issues.take(4).forEach { iss ->
                                Text("• $iss", fontSize = 10.sp, color = palette.textMain)
                            }
                        }
                    }
                }

                // Sekmeler: 🏠 Kelimeler | 💬 Diyaloglar | 📜 Cümleler
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        "words" to "🏠 Kelimeler (${allWords.size})",
                        "dialogue" to "💬 Diyaloglar (${allDialogueLines.size})",
                        "sentences" to "📜 Cümleler (${allSentences.size})"
                    ).forEach { (key, title) ->
                        Button(
                            onClick = {
                                activeTab = key
                                editingItemId = null
                                editingDialogueLineId = null
                                when (key) {
                                    "words" -> {
                                        val firstPkg = allPackages.firstOrNull()
                                        targetPkgId = firstPkg?.id ?: "egitim_ilim"
                                        targetPkgTitle = firstPkg?.ad ?: "Eğitim & İlim"
                                    }
                                    "sentences" -> {
                                        val firstSPkg = allSentencePackages.firstOrNull()
                                        targetPkgId = firstSPkg?.id ?: "gunluk_kaliplar"
                                        targetPkgTitle = firstSPkg?.ad ?: "Günlük Kalıplar"
                                    }
                                    "dialogue" -> {
                                        val firstDlg = allDialogues.firstOrNull()
                                        targetPkgId = firstDlg?.id ?: "dlg_01"
                                        targetPkgTitle = firstDlg?.baslikTr ?: "Yeni Diyalog"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == key) RoyalBlue else palette.btnSec,
                                contentColor = if (activeTab == key) Color.White else palette.textMain
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                // Yayın Sonrası Yeni Kategori/Paket/Diyalog + Kelime/Cümle + Görsel/Ses Ekleme & Düzenleme Formu
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Kategori / Paket / Diyalog ID ve Başlığı
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = targetPkgId,
                                onValueChange = { targetPkgId = it },
                                label = { Text(if (activeTab == "dialogue") "Diyalog ID" else "Kategori/Paket ID", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetPkgTitle,
                                onValueChange = { targetPkgTitle = it },
                                label = { Text(if (activeTab == "dialogue") "Diyalog Başlığı" else "Kategori Başlığı", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                        }

                        // Arapça ve Türkçe Alanları
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = newArabicText,
                                onValueChange = { newArabicText = it },
                                placeholder = { Text("Arapça (RTL)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_new_arabic_input")
                            )
                            OutlinedTextField(
                                value = newTurkishText,
                                onValueChange = { newTurkishText = it },
                                placeholder = { Text("Türkçe Anlamı", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_new_turkish_input")
                            )
                        }

                        // Medya Seçiciler: 🖼️ Telefondan Görsel Seç (.png/.jpg) & 🎵 Telefondan Ses Seç (.mp3/.wav)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (activeTab == "words") {
                                OutlinedTextField(
                                    value = newVisualOrEmoji,
                                    onValueChange = { newVisualOrEmoji = it },
                                    label = { Text("Emoji / Görsel Yolu", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = RoyalPurple,
                                    modifier = Modifier
                                        .clickable { imagePickerLauncher.launch("image/*") }
                                        .testTag("btn_pick_custom_image")
                                ) {
                                    Text(
                                        text = "🖼️ Telefondan Görsel Seç (.png/.jpg)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                            } else if (activeTab == "dialogue") {
                                OutlinedTextField(
                                    value = newSpeakerName,
                                    onValueChange = { newSpeakerName = it },
                                    label = { Text("Konuşmacı", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TealAccent,
                                modifier = Modifier
                                    .clickable { audioPickerLauncher.launch("audio/*") }
                                    .testTag("btn_pick_custom_audio")
                            ) {
                                Text(
                                    text = if (newAudioRelPath.isBlank()) "🎵 Telefondan Ses Seç (.mp3/.wav)" else "🎵 Ses Hazır ✓",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("A1", "A2", "B1", "B2", "C1").forEach { lvl ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (newCefrLevel == lvl) WarmGold else palette.btnSec,
                                        modifier = Modifier.clickable { newCefrLevel = lvl }
                                    ) {
                                        Text(
                                            text = lvl,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (newCefrLevel == lvl) Color(0xFF0F172A) else palette.textMain,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (editingItemId != null || editingDialogueLineId != null) {
                                    OutlinedButton(
                                        onClick = {
                                            editingItemId = null
                                            editingDialogueLineId = null
                                            newArabicText = ""
                                            newTurkishText = ""
                                            newAudioRelPath = ""
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("İptal", fontSize = 11.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val ar = ArabicNlpEngine.sanitizeArabicSentence(newArabicText)
                                        val tr = ArabicNlpEngine.sanitizeTurkishSentence(newTurkishText)
                                        if (ar.isBlank() || tr.isBlank()) {
                                            statusBanner = "Lütfen Arapça ve Türkçe alanlarını doldurun."
                                            return@Button
                                        }
                                        val cleanPkgId = targetPkgId.trim().lowercase()
                                            .replace(" ", "_")
                                            .ifBlank { "ozel_kategori" }
                                        val cleanPkgTitle = targetPkgTitle.trim().ifBlank { cleanPkgId }

                                        scope.launch {
                                            val itemId = editingItemId ?: "${activeTab.first()}_${System.currentTimeMillis()}"
                                            when (activeTab) {
                                                "words" -> {
                                                    viewModel.repository.saveOrUpdatePackage(
                                                        com.example.data.PackageEntity(
                                                            id = cleanPkgId,
                                                            ad = cleanPkgTitle,
                                                            mode = "words",
                                                            seviye = newCefrLevel
                                                        )
                                                    )
                                                    val existing = allWords.firstOrNull { it.id == itemId }
                                                    viewModel.repository.saveOrUpdateWord(
                                                        (existing ?: WordEntity(
                                                            id = itemId,
                                                            paketId = cleanPkgId,
                                                            arapca = ar,
                                                            turkce = tr
                                                        )).copy(
                                                            paketId = cleanPkgId,
                                                            arapca = ar,
                                                            turkce = tr,
                                                            seviye = newCefrLevel,
                                                            gorsel = newVisualOrEmoji.ifBlank { "📚" },
                                                            ses = newAudioRelPath.ifBlank { existing?.ses }
                                                        )
                                                    )
                                                }
                                                "sentences" -> {
                                                    viewModel.repository.saveOrUpdatePackage(
                                                        com.example.data.PackageEntity(
                                                            id = cleanPkgId,
                                                            ad = cleanPkgTitle,
                                                            mode = "sentences",
                                                            seviye = newCefrLevel
                                                        )
                                                    )
                                                    val existing = allSentences.firstOrNull { it.id == itemId }
                                                    viewModel.repository.saveOrUpdateSentence(
                                                        (existing ?: SentenceEntity(
                                                            id = itemId,
                                                            paketId = cleanPkgId,
                                                            arapca = ar,
                                                            turkce = tr
                                                        )).copy(
                                                            paketId = cleanPkgId,
                                                            arapca = ar,
                                                            turkce = tr,
                                                            seviye = newCefrLevel,
                                                            ses = newAudioRelPath.ifBlank { existing?.ses }
                                                        )
                                                    )
                                                }
                                                "dialogue" -> {
                                                    val existingDlg = allDialogues.firstOrNull { it.id == cleanPkgId }
                                                    viewModel.repository.saveOrUpdateDialogue(
                                                        com.example.data.DialogueEntity(
                                                            id = cleanPkgId,
                                                            paketId = existingDlg?.paketId ?: "muhadesat_paketi",
                                                            baslikAr = existingDlg?.baslikAr ?: "",
                                                            baslikTr = cleanPkgTitle,
                                                            seviye = newCefrLevel
                                                        )
                                                    )
                                                    val lineId = editingDialogueLineId ?: 0
                                                    val existingLine = allDialogueLines.firstOrNull { it.id == lineId }
                                                    viewModel.repository.saveOrUpdateDialogueLine(
                                                        com.example.data.DialogueLineEntity(
                                                            id = lineId,
                                                            diyalogId = cleanPkgId,
                                                            sira = existingLine?.sira ?: (allDialogueLines.count { it.diyalogId == cleanPkgId } + 1),
                                                            konusmaci = newSpeakerName.ifBlank { "Ömer" },
                                                            cinsiyet = newSpeakerGender,
                                                            arapca = ar,
                                                            turkce = tr,
                                                            ses = newAudioRelPath.ifBlank { existingLine?.ses }
                                                        )
                                                    )
                                                }
                                            }
                                            val wasEdit = editingItemId != null || editingDialogueLineId != null
                                            editingItemId = null
                                            editingDialogueLineId = null
                                            newArabicText = ""
                                            newTurkishText = ""
                                            newAudioRelPath = ""
                                            statusBanner = if (wasEdit) "✅ Kayıt güncellendi!" else "➕ Yeni kayıt ve kategori ($cleanPkgTitle) kaydedildi!"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_admin_add_record")
                                ) {
                                    Text(
                                        text = if (editingItemId != null || editingDialogueLineId != null) "💾 Güncelle" else "➕ Kaydet / Ekle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Arama Kutusu ve Kayıt Listesi
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("🔍 Kayıtlarda hızlı ara (Düzenlemek için ✏️ butonuna dokunun)...", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val q = searchQuery.trim().lowercase()
                    when (activeTab) {
                        "words" -> {
                            val list = allWords.filter {
                                q.isEmpty() || it.turkce.lowercase().contains(q) || it.arapca.contains(q) || it.paketId.lowercase().contains(q)
                            }
                            items(list, key = { it.id }) { w ->
                                AdminRecordRow(
                                    badge = "[${w.seviye}] ${w.paketId} ${if (!w.ses.isNullOrBlank()) "🎵" else ""}",
                                    turkce = w.turkce,
                                    arapca = w.arapca,
                                    onSpeak = {
                                        viewModel.audioManager.speakArabic(
                                            text = w.arapca,
                                            voiceType = "male",
                                            slow = false,
                                            audioPath = w.ses,
                                            itemId = w.id,
                                            paketId = w.paketId
                                        )
                                    },
                                    onEdit = {
                                        editingItemId = w.id
                                        targetPkgId = w.paketId
                                        targetPkgTitle = allPackages.firstOrNull { it.id == w.paketId }?.ad ?: w.paketId
                                        newArabicText = w.arapca
                                        newTurkishText = w.turkce
                                        newCefrLevel = w.seviye
                                        newVisualOrEmoji = w.gorsel ?: "📚"
                                        newAudioRelPath = w.ses ?: ""
                                    },
                                    onDelete = { scope.launch { viewModel.repository.deleteWord(w.id) } }
                                )
                            }
                        }
                        "sentences" -> {
                            val list = allSentences.filter {
                                q.isEmpty() || it.turkce.lowercase().contains(q) || it.arapca.contains(q)
                            }
                            items(list, key = { it.id }) { s ->
                                AdminRecordRow(
                                    badge = "[${s.seviye}] ${s.paketId} ${if (!s.ses.isNullOrBlank()) "🎵" else ""}",
                                    turkce = s.turkce,
                                    arapca = s.arapca,
                                    onSpeak = {
                                        viewModel.audioManager.speakArabic(
                                            text = s.arapca,
                                            voiceType = "male",
                                            slow = false,
                                            audioPath = s.ses,
                                            itemId = s.id,
                                            paketId = s.paketId
                                        )
                                    },
                                    onEdit = {
                                        editingItemId = s.id
                                        targetPkgId = s.paketId
                                        targetPkgTitle = allSentencePackages.firstOrNull { it.id == s.paketId }?.ad ?: s.paketId
                                        newArabicText = s.arapca
                                        newTurkishText = s.turkce
                                        newCefrLevel = s.seviye
                                        newAudioRelPath = s.ses ?: ""
                                    },
                                    onDelete = { scope.launch { viewModel.repository.deleteSentence(s.id) } }
                                )
                            }
                        }
                        else -> {
                            val list = allDialogueLines.filter {
                                q.isEmpty() || it.turkce.lowercase().contains(q) || it.arapca.contains(q) || it.konusmaci.lowercase().contains(q)
                            }
                            items(list, key = { it.id }) { l ->
                                AdminRecordRow(
                                    badge = "[${l.diyalogId}] ${l.konusmaci} ${if (!l.ses.isNullOrBlank()) "🎵" else ""}",
                                    turkce = l.turkce,
                                    arapca = l.arapca,
                                    onSpeak = {
                                        viewModel.audioManager.speakArabic(
                                            text = l.arapca,
                                            voiceType = "male",
                                            slow = false,
                                            audioPath = l.ses,
                                            itemId = "dlg_${l.id}",
                                            paketId = l.diyalogId
                                        )
                                    },
                                    onEdit = {
                                        editingDialogueLineId = l.id
                                        targetPkgId = l.diyalogId
                                        targetPkgTitle = allDialogues.firstOrNull { it.id == l.diyalogId }?.baslikTr ?: l.diyalogId
                                        newSpeakerName = l.konusmaci
                                        newSpeakerGender = l.cinsiyet
                                        newArabicText = l.arapca
                                        newTurkishText = l.turkce
                                        newAudioRelPath = l.ses ?: ""
                                    },
                                    onDelete = { scope.launch { viewModel.repository.deleteDialogueLine(l.id) } }
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun AdminRecordRow(
    badge: String,
    turkce: String,
    arapca: String,
    onSpeak: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardColor),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
                Text(text = turkce, fontSize = 12.sp, color = palette.textMain)
            }
            Text(
                text = arapca,
                fontFamily = AmiriArabicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = palette.textArabic,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCoral,
                    modifier = Modifier.clickable { onSpeak() }
                ) {
                    Text("🔊", fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = RoyalBlue,
                    modifier = Modifier.clickable { onEdit() }
                ) {
                    Text("✏️", fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CoralRed,
                    modifier = Modifier.clickable { onDelete() }
                ) {
                    Text("🗑️", fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                }
            }
        }
    }
}
