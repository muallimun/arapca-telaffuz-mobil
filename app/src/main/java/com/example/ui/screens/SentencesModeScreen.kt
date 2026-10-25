package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.ConfettiOverlay
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedFilter by viewModel.selectedSentenceFilter.collectAsState()
    val selectedLevel by viewModel.selectedSentenceLevel.collectAsState()
    val sentences by viewModel.filteredSentences.collectAsState()
    val sentenceIndex by viewModel.sentenceIndex.collectAsState()
    val showTr by viewModel.sentShowTr.collectAsState()
    val showTashkeel by viewModel.sentShowTashkeel.collectAsState()
    val hideArabic by viewModel.sentHideArabic.collectAsState()
    val testModeHideAr by viewModel.testModeHideArabic.collectAsState()
    val fontSizeSp by viewModel.sentFontSizeSp.collectAsState()
    val attemptCount by viewModel.sentAttemptCount.collectAsState()
    val maxAttemptsSetting by viewModel.maxAttemptsSetting.collectAsState()
    val searchQuery by viewModel.sentenceSearchQuery.collectAsState()
    val isRadioModeActive by viewModel.isRadioModeActive.collectAsState()

    val isRecording by viewModel.audioManager.isRecording.collectAsState()
    val waveBars by viewModel.audioManager.waveBars.collectAsState()
    val evalState by viewModel.evalState.collectAsState()

    var showFilterMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }

    val safeSentenceIndex = if (sentences.isNotEmpty()) {
        sentenceIndex.coerceIn(0, sentences.size - 1)
    } else 0

    val currentItem = if (sentences.isNotEmpty()) {
        sentences[safeSentenceIndex]
    } else null

    val currentFilterLabel = when (selectedFilter) {
        "📅 Günün 15 Tekrarı", "📅 Günlük 15", "🔥 Günlük 15" -> "🔥 Günlük 15"
        "Favoriler", "⭐ Favori" -> "⭐ Favori"
        "Ezberlenmeyen", "📌 Ezberlenmeyen" -> "📌 Ezberlenmeyen"
        "Zorlandıklarım", "⚠️ Zayıf" -> "⚠️ Zayıf"
        "Öğrendiklerim" -> "✅ Öğrendiklerim"
        else -> "📦 Tümü"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Üst Seçici ve 3 Satırlı Açılır Filtre Paneli
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
                // Üst Seçici Bar: Filtre/Kategori (weight 1.25f) | Seviye "🎯 Tümü" (weight 0.9f) | Filtre Toggle -> Hepsi 38.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1.25f)) {
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
                                text = currentFilterLabel,
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
                            listOf("Tümü", "🔥 Günlük 15", "Favoriler", "Ezberlenmeyen", "Öğrendiklerim", "Zorlandıklarım").forEach { f ->
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

                    // Seviye Seçici ("Seviye: " ön eki olmadan "🎯 Tümü" veya "🎯 A1")
                    Box(modifier = Modifier.weight(0.9f)) {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text(
                                text = "🎯 $selectedLevel",
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
                                    text = { Text("🎯 $lvl", fontWeight = FontWeight.Bold) },
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
                                text = if (filterExpanded) "⚙️ Filtre ▲" else "⚙️ Filtre ▼",
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

                // Açılır-Kapanır Ek Filtreler: 3 Net Satır (Yatay Kaydırma veya Kesilme Yok)
                if (filterExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Satır: 🙈 Gizle | ✨ Hareke | 🇹🇷 Anlam | A- | A+ (Tek satırda kompakt)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.setSentHideArabic(!hideArabic) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hideArabic) WarmGold else palette.btnSec,
                                    contentColor = if (hideArabic) Color(0xFF0F172A) else palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text("🙈 Gizle", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.setSentShowTashkeel(!showTashkeel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showTashkeel) RoyalBlue else palette.btnSec,
                                    contentColor = if (showTashkeel) Color.White else palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text("✨ Hareke", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.setSentShowTr(!showTr) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showTr) RoyalBlue else palette.btnSec,
                                    contentColor = if (showTr) Color.White else palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text("🇹🇷 Anlam", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.changeSentenceFontSize(-2) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.btnSec,
                                    contentColor = palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Button(
                                onClick = { viewModel.changeSentenceFontSize(2) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.btnSec,
                                    contentColor = palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        // 2. Satır: Tam Genişlikte (fillMaxWidth) Arama Kutusu (MainAppViewModel ile %100 senkronize)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.cardColor,
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "🔍 Kelime veya anlam ara...",
                                            fontSize = 11.sp,
                                            color = palette.textDim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSentenceSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            color = palette.textMain,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(palette.accentHead),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Text(
                                        text = "✖",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textDim,
                                        modifier = Modifier
                                            .clickable { viewModel.setSentenceSearchQuery("") }
                                            .padding(start = 6.dp)
                                    )
                                }
                            }
                        }

                        // 3. Satır: Durum Çipleri (FlowRow içinde kesilmeden temizce dizilir)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val statusItems = listOf(
                                "Tümü" to "Tümü",
                                "🔥 Günlük 15" to "🔥 Günlük 15",
                                "Favoriler" to "⭐ Favori",
                                "Ezberlenmeyen" to "📌 Ezberlenmeyen",
                                "Zorlandıklarım" to "⚠️ Zayıf"
                            )
                            statusItems.forEach { (filterKey, label) ->
                                val isSel = selectedFilter == filterKey ||
                                    (filterKey == "🔥 Günlük 15" && (selectedFilter == "📅 Günün 15 Tekrarı" || selectedFilter == "📅 Günlük 15"))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) TealAccent else palette.btnSec,
                                    border = BorderStroke(1.dp, if (isSel) TealAccent else palette.border),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clickable { viewModel.selectSentenceFilter(filterKey) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else palette.textMain,
                                            maxLines = 1,
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

        // 2. ANA CÜMLE KARTI (Sadeleştirilmiş & Göz Hizasında Mahreç Koçu)
        val cardBorderColor = if (isRecording) EmeraldGreen else palette.border
        val cardBorderWidth = if (isRecording) 2.5.dp else 1.dp

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(cardBorderWidth, cardBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("main_sentence_card")
        ) {
            if (currentItem == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Seçili filtre, seviye veya aramada cümle bulunamadı.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDim,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // 1. ÜST META BARI (Sabit): Sol (⭐, ☑️ ve 📤 Paylaş) | Orta (< Sayaç >) | Sağ (Seviye Rozeti)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isFav = currentItem.isFav == 1
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFav) WarmGold else palette.btnSec,
                                    border = BorderStroke(1.dp, if (isFav) WarmGold else palette.border),
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable {
                                            scope.launch { viewModel.repository.toggleSentenceFavorite(currentItem.id) }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isFav) "⭐" else "☆",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFav) Color(0xFF0F172A) else palette.textMain
                                        )
                                    }
                                }

                                val isLearned = currentItem.isLearned == 1
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLearned) EmeraldGreen else palette.btnSec,
                                    border = BorderStroke(1.dp, if (isLearned) EmeraldGreen else palette.border),
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable {
                                            scope.launch { viewModel.repository.toggleSentenceLearned(currentItem.id) }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isLearned) "✅" else "☑️",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLearned) Color(0xFF0F172A) else palette.textMain
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = palette.btnSec,
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable { viewModel.shareCurrentCard(context) }
                                        .testTag("btn_share_sentence_card")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "📤",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Orta: < Önceki | Sayaç + Doğru/Yanlış | Sonraki >
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.changeSentenceIndex(-1) },
                                    modifier = Modifier
                                        .background(palette.btnSec, RoundedCornerShape(8.dp))
                                        .size(30.dp)
                                        .testTag("btn_prev_sentence")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Önceki Cümle",
                                        tint = palette.textMain,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = "${safeSentenceIndex + 1}/${sentences.size} • ✅${currentItem.dogru}|❌${currentItem.yanlis}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textDim,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

                                IconButton(
                                    onClick = { viewModel.changeSentenceIndex(1) },
                                    modifier = Modifier
                                        .background(palette.btnSec, RoundedCornerShape(8.dp))
                                        .size(30.dp)
                                        .testTag("btn_next_sentence")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Sonraki Cümle",
                                        tint = palette.textMain,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.accentHead.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, palette.accentHead.copy(alpha = 0.5f)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = currentItem.seviye,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = palette.accentHead,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 2. ORTA CÜMLE, KELİME ÇİPLERİ & CANLI MAHREÇ KOÇU ALANI (Kaydırılabilir & AnimatedContent Geçişli)
                        val middleScrollState = rememberScrollState()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(middleScrollState)
                                .padding(vertical = 6.dp)
                                .pointerInput(currentItem.id) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDrag = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDrag += dragAmount
                                        },
                                        onDragEnd = {
                                            if (totalDrag < -60f) {
                                                viewModel.changeSentenceIndex(1)
                                            } else if (totalDrag > 60f) {
                                                viewModel.changeSentenceIndex(-1)
                                            }
                                        }
                                    )
                                }
                        ) {
                            AnimatedContent(
                                targetState = safeSentenceIndex,
                                transitionSpec = {
                                    val dir = if (targetState >= initialState) 1 else -1
                                    (slideInHorizontally(tween(240)) { fullW -> dir * (fullW / 3) } + fadeIn(tween(220)))
                                        .togetherWith(slideOutHorizontally(tween(220)) { fullW -> -dir * (fullW / 3) } + fadeOut(tween(180)))
                                },
                                label = "SentenceCardSlideAnimation",
                                modifier = Modifier.fillMaxWidth()
                            ) { animIdx ->
                                val animItem = sentences.getOrNull(animIdx) ?: currentItem
                                val animDisplayAr = if (showTashkeel) animItem.arapca else ArabicNlpEngine.removeDiacritics(animItem.arapca)
                                val animSentenceWords = remember(animDisplayAr) {
                                    animDisplayAr.split(Regex("\\s+")).filter { it.isNotBlank() }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Tam Genişlikte Odak Cümle Kutusu
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = palette.panelColor,
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            if (evalState.wordStatuses.isNotEmpty() && evalState.expectedArabic == animItem.arapca) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp)
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
                                                Text(
                                                    text = "🔒 [ Arapça Cümle Gizli — Söyleyin veya '🔊 Dinle'ye Basın ]",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.textArabic,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(6.dp)
                                                )
                                            } else {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    Text(
                                                        text = animDisplayAr,
                                                        fontFamily = AmiriArabicFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = fontSizeSp.sp,
                                                        color = palette.textArabic,
                                                        textAlign = TextAlign.Center,
                                                        lineHeight = (fontSizeSp + 10).sp,
                                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }

                                            // Türkçe Çeviri
                                            if (showTr) {
                                                Text(
                                                    text = animItem.turkce,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = palette.textMain,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    // Kelime Kelime Sesli Dinleme Çipleri
                                    if (!hideArabic && !testModeHideAr && animSentenceWords.isNotEmpty()) {
                                        Text(
                                            text = "💡 Tek tek dinlemek istediğiniz kelimeye dokunun:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.textDim,
                                            textAlign = TextAlign.Center
                                        )

                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                animSentenceWords.forEach { wordToken ->
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = palette.panelColor,
                                                        border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.55f)),
                                                        modifier = Modifier.clickable {
                                                            viewModel.audioManager.speakArabic(
                                                                wordToken,
                                                                "male",
                                                                viewModel.slowTts.value
                                                            )
                                                        }
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = "🔊",
                                                                fontSize = 11.sp
                                                            )
                                                            Text(
                                                                text = wordToken,
                                                                fontFamily = AmiriArabicFontFamily,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 17.sp,
                                                                color = palette.textArabic,
                                                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Göz Hizasında Dinamik Mahreç & Telaffuz Koçu Kutusu (Orta kaydırılabilir alanın içinde)
                            LiveMakhrajAndHarakaCoachCard(
                                evalState = evalState,
                                activeArabicText = currentItem.arapca,
                                onSpeakWord = { wStr ->
                                    viewModel.audioManager.speakArabic(wStr, "male", viewModel.slowTts.value)
                                }
                            )
                        }

                        // 3. ALT BÖLÜM (Sabit): Ses & Oto-Dinle Satırı + Mikrofon Butonu
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Üst Ses Satırı: Yan Yana Eşit Genişlikte (weight(1f)) 🔊 Dinle | 🗣️ Gölgele | 🎧 Sesim | 📻 Oto-Dinle
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.speakCurrentItem(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("btn_sentence_tts")
                                ) {
                                    Text("🔊 Dinle", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = { viewModel.openShadowingForCurrentCard() },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Text("🗣️ Gölgele", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = {
                                        val ok = viewModel.audioManager.playSavedWavFile()
                                        if (!ok) viewModel.showToast("Dinlenecek ses kaydı bulunamadı.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Text("🎧 Sesim", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = { viewModel.toggleRadioMode() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRadioModeActive) CoralRed else TealAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(36.dp)
                                        .testTag("btn_radio_mode_sentence")
                                ) {
                                    Text(
                                        text = if (isRadioModeActive) "⏹️ Durdur" else "📻 Oto-Dinle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Sadece kayıt sırasında görünen canlı ses dalga boyu
                            if (isRecording) {
                                LiveWaveformBars(
                                    bars = waveBars,
                                    isRecording = isRecording,
                                    modifier = Modifier
                                        .height(22.dp)
                                        .padding(horizontal = 24.dp)
                                )
                            }

                            Button(
                                onClick = onRequestMicPermissionAndRecord,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) CoralRed else RoyalBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_sentence_record")
                            ) {
                                Text(
                                    text = if (isRecording) "⏹️ KAYDI DURDUR" else "🎤 BAŞLA VE KONUŞ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // %75+ Başarıda 2 Saniyelik Konfeti Parçacık Animasyonu
                    ConfettiOverlay(visible = evalState.showConfetti)
                }
            }
        }
    }
}
