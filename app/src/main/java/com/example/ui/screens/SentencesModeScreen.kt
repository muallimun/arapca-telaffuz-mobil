package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nlp.ArabicNlpEngine
import com.example.ui.components.LiveMakhrajAndHarakaCoachCard
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
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.MainAppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentencesModeScreen(
    viewModel: MainAppViewModel,
    onRequestMicPermissionAndRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val scope = rememberCoroutineScope()

    val selectedFilter by viewModel.selectedSentenceFilter.collectAsState()
    val selectedLevel by viewModel.selectedSentenceLevel.collectAsState()
    val rawSentences by viewModel.filteredSentences.collectAsState()
    val sentenceIndex by viewModel.sentenceIndex.collectAsState()
    val showTr by viewModel.sentShowTr.collectAsState()
    val showTashkeel by viewModel.sentShowTashkeel.collectAsState()
    val hideArabic by viewModel.sentHideArabic.collectAsState()
    val testModeHideAr by viewModel.testModeHideArabic.collectAsState()
    val fontSizeSp by viewModel.sentFontSizeSp.collectAsState()
    val showDemoSim by viewModel.showDemoSimulationButtons.collectAsState()

    val isRecording by viewModel.audioManager.isRecording.collectAsState()
    val waveBars by viewModel.audioManager.waveBars.collectAsState()
    val evalState by viewModel.evalState.collectAsState()

    var showFilterMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }
    var showCardLevelMenu by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val sentences = remember(rawSentences, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) rawSentences
        else rawSentences.filter {
            it.turkce.contains(q, ignoreCase = true) ||
                it.arapca.contains(q, ignoreCase = true) ||
                ArabicNlpEngine.removeDiacritics(it.arapca).contains(q, ignoreCase = true)
        }
    }

    val currentItem = if (sentences.isNotEmpty()) {
        sentences[sentenceIndex.coerceIn(0, sentences.size - 1)]
    } else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Kompakt & Açılır-Kapanır Üst Filtre ve Seviye Barı
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Üst Seçici Bar: Filtre/Kategori (weight 1.2f) | Seviye (weight 1f) | Filtre Toggle (wrapContentWidth) -> Hepsi 38.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(
                            onClick = { showFilterMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("sentence_filter_selector")
                        ) {
                            Text(
                                text = selectedFilter,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            listOf("Tümü", "📅 Günün 15 Tekrarı", "Favoriler", "Ezberlenmeyen", "Öğrendiklerim", "Zorlandıklarım").forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showFilterMenu = false
                                        viewModel.selectSentenceFilter(f)
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text(
                                text = "Seviye: $selectedLevel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMain, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showLevelMenu,
                            onDismissRequest = { showLevelMenu = false }
                        ) {
                            listOf("Tümü", "A1", "A2", "B1", "B2", "C1").forEach { lvl ->
                                DropdownMenuItem(
                                    text = { Text(lvl, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showLevelMenu = false
                                        viewModel.selectSentenceLevel(lvl)
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterExpanded) palette.accentHead.copy(alpha = 0.2f) else palette.btnSec,
                        border = BorderStroke(1.dp, if (filterExpanded) palette.accentHead else palette.border),
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(38.dp)
                            .clickable { filterExpanded = !filterExpanded }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (filterExpanded) "⚙️ Filtre ▴" else "⚙️ Filtre ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filterExpanded) palette.accentHead else palette.textMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }

                // Açılır-Kapanır Ek Filtreler (wrapContentHeight, Maksimum 2 Kompakt Satır)
                if (filterExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Satır: 🙈 Gizle | ✨ Hareke | 🇹🇷 Anlam (Eşit yükseklik 36.dp, weight(1f), tek satır)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 🙈 Gizle
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (hideArabic) WarmGold else palette.btnSec,
                                border = BorderStroke(1.dp, if (hideArabic) WarmGold else palette.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable { viewModel.setSentHideArabic(!hideArabic) }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = "🙈 Gizle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hideArabic) Color(0xFF0F172A) else palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }

                            // ✨ Hareke
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showTashkeel) RoyalBlue else palette.btnSec,
                                border = BorderStroke(1.dp, if (showTashkeel) RoyalBlue else palette.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable { viewModel.setSentShowTashkeel(!showTashkeel) }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = "✨ Hareke",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showTashkeel) Color.White else palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }

                            // 🇹🇷 Anlam
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showTr) RoyalBlue else palette.btnSec,
                                border = BorderStroke(1.dp, if (showTr) RoyalBlue else palette.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable { viewModel.setSentShowTr(!showTr) }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = "🇹🇷 Anlam",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showTr) Color.White else palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 2. Satır: A- / A+ Punto + Kompakt Arama + Yatay Kaydırılabilir Durum Çipleri
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.btnSec,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable { viewModel.changeSentenceFontSize(-2) }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textMain, maxLines = 1)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.btnSec,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable { viewModel.changeSentenceFontSize(2) }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textMain, maxLines = 1)
                                }
                            }

                            // Kompakt Arama Kutusu
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.cardColor,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "🔍 Ara...",
                                            fontSize = 10.sp,
                                            color = palette.textDim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 11.sp,
                                            color = palette.textMain,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(palette.accentHead),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Tek satırlık yatay kaydırılabilir durum çipleri
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusItems = listOf(
                                    "Tümü" to "Tümü",
                                    "📅 Günün 15 Tekrarı" to "📅 Günlük 15",
                                    "Favoriler" to "⭐ Favori",
                                    "Ezberlenmeyen" to "📌 Ezberlenmeyen",
                                    "Zorlandıklarım" to "⚠️ Zayıf"
                                )
                                statusItems.forEach { (filterKey, label) ->
                                    val isSel = selectedFilter == filterKey
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) TealAccent else palette.btnSec,
                                        border = BorderStroke(1.dp, if (isSel) TealAccent else palette.border),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .clickable { viewModel.selectSentenceFilter(filterKey) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else palette.textMain,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
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

        // 2. ANA CÜMLE KARTI
        val cardBorderColor = if (isRecording) EmeraldGreen else palette.border
        val cardBorderWidth = if (isRecording) 2.5.dp else 1.dp

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(cardBorderWidth, cardBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("main_sentence_card")
        ) {
            if (currentItem == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Seçili filtre veya seviyede cümle bulunamadı.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDim
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Üst Kontroller: ⭐ Favori | ✅ Öğrendim | 🏷️ Seviye (Sabit Genişlik, 32.dp yükseklik)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isFav = currentItem.isFav == 1
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFav) WarmGold else palette.btnSec,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable {
                                        scope.launch { viewModel.repository.toggleSentenceFavorite(currentItem.id) }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = if (isFav) "⭐ Favori" else "☆ Favori",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFav) Color(0xFF0F172A) else palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }

                            val isLearned = currentItem.isLearned == 1
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLearned) EmeraldGreen else palette.btnSec,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable {
                                        scope.launch { viewModel.repository.toggleSentenceLearned(currentItem.id) }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = if (isLearned) "✅ Öğrendim" else "☑️ Öğrendim",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLearned) Color(0xFF0F172A) else palette.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panelColor,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .clickable { showCardLevelMenu = true }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = "🏷️ ${currentItem.seviye} ▾",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentHead,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showCardLevelMenu,
                                onDismissRequest = { showCardLevelMenu = false }
                            ) {
                                listOf("A1", "A2", "B1", "B2", "C1").forEach { lvl ->
                                    DropdownMenuItem(
                                        text = { Text("Seviye: $lvl", fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            showCardLevelMenu = false
                                            scope.launch {
                                                viewModel.repository.updateSentenceLevel(currentItem.id, lvl)
                                                viewModel.showToast("🏷️ Cümle seviyesi $lvl olarak güncellendi!")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Arapça Cümle Alanı (Kelime Bazlı Renkli Değerlendirme veya Gizli Mod)
                    val displayArabic = if (showTashkeel) {
                        currentItem.arapca
                    } else {
                        ArabicNlpEngine.removeDiacritics(currentItem.arapca)
                    }

                    if (evalState.wordStatuses.isNotEmpty() && evalState.expectedArabic == currentItem.arapca) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                evalState.wordStatuses.forEach { (wStr, isOk) ->
                                    val wDisp = if (showTashkeel) wStr else ArabicNlpEngine.removeDiacritics(wStr)
                                    Text(
                                        text = wDisp,
                                        fontFamily = AmiriArabicFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fontSizeSp.sp,
                                        color = if (isOk) palette.okGreen else CoralRed,
                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                                        modifier = Modifier.clickable {
                                            viewModel.audioManager.speakArabic(wStr, "male", viewModel.slowTts.value)
                                        }
                                    )
                                }
                            }
                        }
                    } else if (hideArabic || testModeHideAr) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.panelColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔒 [ Arapça Cümle Gizli — Söyleyin veya '🔊 Dinle'ye Basın ]",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textArabic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = displayArabic,
                                fontFamily = AmiriArabicFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSizeSp.sp,
                                color = palette.textArabic,
                                textAlign = TextAlign.Center,
                                lineHeight = (fontSizeSp + 12).sp,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }

                    // Türkçe Çeviri
                    if (showTr) {
                        Text(
                            text = currentItem.turkce,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textMain,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Gezinme Çubuğu: ⏮ İlk | ◀ Önceki | Sonraki ▶ | Son ⏭
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.btnSec,
                            modifier = Modifier.clickable { viewModel.jumpToSentenceIndex(1) }
                        ) {
                            Text(
                                text = "⏮ İlk",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.btnSec,
                            modifier = Modifier
                                .clickable { viewModel.changeSentenceIndex(-1) }
                                .testTag("btn_prev_sentence")
                        ) {
                            Text(
                                text = "◀ Önceki",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.btnSec,
                            modifier = Modifier
                                .clickable { viewModel.changeSentenceIndex(1) }
                                .testTag("btn_next_sentence")
                        ) {
                            Text(
                                text = "Sonraki ▶",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.btnSec,
                            modifier = Modifier.clickable { viewModel.jumpToSentenceIndex(sentences.size) }
                        ) {
                            Text(
                                text = "Son ⏭",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Ses Dalga Boyu ve Aksiyon Butonları
                    LiveWaveformBars(
                        bars = waveBars,
                        isRecording = isRecording,
                        modifier = Modifier
                            .height(26.dp)
                            .padding(horizontal = 24.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRequestMicPermissionAndRecord,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) CoralRed else RoyalBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .testTag("btn_sentence_record")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRecording) "DURDUR" else "🎤 Telaffuz Et",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = { viewModel.speakCurrentItem(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("btn_sentence_tts")
                        ) {
                            Text("🔊 Dinle", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.openShadowingForCurrentCard() },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text("🗣️ Gölgele", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    // Emülatör & Canlı Koç Test Butonları (Ayarlar -> Demo / Simülasyon Açık İse Gösterilir)
                    if (showDemoSim) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panelColor,
                                border = BorderStroke(1.dp, palette.okGreen.copy(alpha = 0.6f)),
                                modifier = Modifier.clickable { viewModel.simulatePronunciationAttempt("correct") }
                            ) {
                                Text(
                                    text = "🎯 Örnek Doğru Okuma",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.okGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panelColor,
                                border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.6f)),
                                modifier = Modifier.clickable { viewModel.simulatePronunciationAttempt("makhraj") }
                            ) {
                                Text(
                                    text = "🔬 Mahreç Hatası Test Et",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WarmGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    val verdictColor = when (evalState.isCorrect) {
                        true -> palette.okGreen
                        false -> CoralRed
                        null -> palette.textDim
                    }
                    Text(
                        text = evalState.verdictTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                        textAlign = TextAlign.Center
                    )
                    if (evalState.statusDetail.isNotBlank()) {
                        Text(
                            text = evalState.statusDetail,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textMain,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "[${currentItem.seviye}]  ${sentenceIndex + 1} / ${sentences.size}  •  Doğru: ${currentItem.dogru} | Yanlış: ${currentItem.yanlis}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDim
                    )
                }
            }
        }

        // 3. CANLI MAHREÇ, KÖK HARF & HAREKE KOÇU PANELİ
        LiveMakhrajAndHarakaCoachCard(evalState = evalState)
    }
}
