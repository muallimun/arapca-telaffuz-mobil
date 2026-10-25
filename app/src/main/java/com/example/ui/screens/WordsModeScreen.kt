package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nlp.ArabicNlpEngine
import com.example.ui.components.LiveMakhrajAndHarakaCoachCard
import com.example.ui.components.LiveWaveformBars
import com.example.ui.components.WordMorphologyCardBar
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
fun WordsModeScreen(
    viewModel: MainAppViewModel,
    onRequestMicPermissionAndRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val scope = rememberCoroutineScope()

    val packages by viewModel.wordPackages.collectAsState()
    val selectedCategory by viewModel.selectedWordCategory.collectAsState()
    val selectedLevel by viewModel.selectedWordLevel.collectAsState()
    val filteredWords by viewModel.filteredWords.collectAsState()
    val wordIndex by viewModel.wordIndex.collectAsState()
    val showTr by viewModel.wordShowTr.collectAsState()
    val showTashkeel by viewModel.wordShowTashkeel.collectAsState()
    val testModeHideAr by viewModel.testModeHideArabic.collectAsState()
    val fontSizeSp by viewModel.wordFontSizeSp.collectAsState()
    val activeMorphAr by viewModel.activeMorphArabic.collectAsState()
    val activeMorphLbl by viewModel.activeMorphLabel.collectAsState()
    val attemptCount by viewModel.wordAttemptCount.collectAsState()
    val maxAttemptsSetting by viewModel.maxAttemptsSetting.collectAsState()
    val showDemoSim by viewModel.showDemoSimulationButtons.collectAsState()

    val isRecording by viewModel.audioManager.isRecording.collectAsState()
    val waveBars by viewModel.audioManager.waveBars.collectAsState()
    val evalState by viewModel.evalState.collectAsState()

    var showPackageMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }
    var showCardLevelMenu by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }

    val currentItem = if (filteredWords.isNotEmpty()) {
        filteredWords[wordIndex.coerceIn(0, filteredWords.size - 1)]
    } else null

    val morphInfo = remember(currentItem, filteredWords) {
        viewModel.currentWordMorphology()
    }

    val currentCategoryTitle = when (selectedCategory) {
        "gunluk_tekrar" -> "📅 Günün 15 Tekrarı (SM-2)"
        "tekrar" -> "⭐ Tekrar Listem"
        "zayif" -> "🎯 Zayıf Kelimelerim"
        else -> packages.firstOrNull { it.id == selectedCategory }?.ad ?: "📦 Paket Seçin"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Kompakt Tek Satırlık Açılır-Kapanır (Collapsible) Üst Filtre, Paket ve Seviye Şeridi
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Paket Seçici
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showPackageMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .testTag("word_package_selector")
                        ) {
                            Text(
                                text = currentCategoryTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead)
                        }
                        DropdownMenu(
                            expanded = showPackageMenu,
                            onDismissRequest = { showPackageMenu = false }
                        ) {
                            packages.forEach { pkg ->
                                DropdownMenuItem(
                                    text = { Text(pkg.ad, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showPackageMenu = false
                                        viewModel.selectWordCategory(pkg.id)
                                    }
                                )
                            }
                        }
                    }

                    // Seviye Seçici
                    Box {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("word_level_selector")
                        ) {
                            Text(
                                text = selectedLevel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMain)
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
                                        viewModel.selectWordLevel(lvl)
                                    }
                                )
                            }
                        }
                    }

                    // Açılır-Kapanır Görünüm & Filtre Butonu
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterExpanded) RoyalBlue else palette.btnSec,
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { filterExpanded = !filterExpanded }
                            .testTag("btn_toggle_word_filters")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (filterExpanded) "⚙️ ▴" else "⚙️ Filtre ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filterExpanded) Color.White else palette.textMain,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Açılır-Kapanır Ek Filtreler (Günün 15 Tekrarı, Hareke, Türkçe, Punto)
                if (filterExpanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCategory == "gunluk_tekrar") TealAccent else palette.btnSec,
                            modifier = Modifier
                                .clickable { viewModel.selectWordCategory("gunluk_tekrar") }
                                .testTag("chip_daily_15")
                        ) {
                            Text(
                                text = "📅 Günün 15 Tekrarı",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCoral,
                            modifier = Modifier
                                .clickable { viewModel.openModal(ActiveModal.LISTENING_EXAM) }
                                .testTag("chip_listening_exam")
                        ) {
                            Text(
                                text = "🎧 Dinle & Bul",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCategory == "tekrar") RoyalBlue else palette.btnSec,
                            modifier = Modifier
                                .clickable { viewModel.selectWordCategory("tekrar") }
                                .testTag("chip_favorites")
                        ) {
                            Text(
                                text = "⭐ Tekrarım",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCategory == "zayif") CoralRed else palette.btnSec,
                            modifier = Modifier
                                .clickable { viewModel.selectWordCategory("zayif") }
                                .testTag("chip_weak_words")
                        ) {
                            Text(
                                text = "🎯 Zayıflar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = showTashkeel,
                                onClick = { viewModel.setWordShowTashkeel(!showTashkeel) },
                                label = { Text("Hareke", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = showTr,
                                onClick = { viewModel.setWordShowTr(!showTr) },
                                label = { Text("🇹🇷 Türkçe", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.btnSec,
                                modifier = Modifier.clickable { viewModel.changeWordFontSize(-2) }
                            ) {
                                Text(
                                    text = "A-",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textMain,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = " Punto ",
                                fontSize = 11.sp,
                                color = palette.textDim,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.btnSec,
                                modifier = Modifier.clickable { viewModel.changeWordFontSize(2) }
                            ) {
                                Text(
                                    text = "A+",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textMain,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. ANA KELİME VE SARF KARTI (Kompakt Mobil Sığdırma + Yeşil Mikrofon Çerçeve Parlaması)
        val cardBorderColor = if (isRecording) EmeraldGreen else palette.border
        val cardBorderWidth = if (isRecording) 3.dp else 1.dp

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(cardBorderWidth, cardBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("main_word_card")
        ) {
            if (currentItem == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("🎉", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Bu pakette seçili seviyede kelime bulunamadı (Üstten seviyeyi 'Tümü' yapabilirsiniz).",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDim,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Kart Üst Meta Barı: Sol (Tekrar + Öğrendim) | Sağ (Sabit Genişlikli A1 Seviye Rozeti)
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
                                    .clickable {
                                        scope.launch { viewModel.repository.toggleWordFavorite(currentItem.id) }
                                    }
                                    .testTag("btn_word_fav")
                            ) {
                                Text(
                                    text = if (isFav) "⭐ Tekrarım" else "☆ Tekrar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFav) Color(0xFF0F172A) else palette.textMain,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            val isLearned = currentItem.isLearned == 1
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLearned) EmeraldGreen else palette.btnSec,
                                modifier = Modifier
                                    .clickable {
                                        scope.launch { viewModel.repository.toggleWordLearned(currentItem.id) }
                                    }
                                    .testTag("btn_word_learned")
                            ) {
                                Text(
                                    text = if (isLearned) "✅ Öğrendim" else "☑️ Öğrendim",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLearned) Color(0xFF0F172A) else palette.textMain,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panelColor,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .width(64.dp)
                                    .clickable { showCardLevelMenu = true }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "🏷️ ${currentItem.seviye} ▾",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentHead,
                                        maxLines = 1,
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
                                                viewModel.repository.updateWordLevel(currentItem.id, lvl)
                                                viewModel.showToast("🏷️ '${currentItem.turkce}' seviyesi $lvl olarak güncellendi!")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Görsel İllüstrasyon Rozeti (80.dp Hibrit Resim + Emoji Fallback) VEYA Sarf Sözlüğü Barı
                    if (!currentItem.gorsel.isNullOrBlank()) {
                        com.example.util.HybridWordVisualBox(
                            wordId = currentItem.id,
                            paketId = currentItem.paketId,
                            gorsel = currentItem.gorsel,
                            sizeDp = 80.dp
                        )
                    } else if (morphInfo != null) {
                        WordMorphologyCardBar(
                            morphInfo = morphInfo,
                            activeMorphArabic = activeMorphAr,
                            defaultWordArabic = currentItem.arapca,
                            onSelectForm = { ar, lbl -> viewModel.selectMorphFormOnCard(ar, lbl) },
                            onOpenRootExplorer = { root ->
                                viewModel.openModal(ActiveModal.SARF_EXPLORER, root)
                            }
                        )
                    }

                    // Önceki / Arapça Kelime / Sonraki Satırı
                    val rawArabicToDisplay = activeMorphAr ?: currentItem.arapca
                    val displayArabic = if (showTashkeel) {
                        rawArabicToDisplay
                    } else {
                        ArabicNlpEngine.removeDiacritics(rawArabicToDisplay)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { viewModel.changeWordIndex(-1) },
                            modifier = Modifier
                                .background(palette.btnSec, RoundedCornerShape(10.dp))
                                .size(38.dp)
                                .testTag("btn_prev_word")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Önceki Kelime",
                                tint = palette.textMain
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = if (testModeHideAr && evalState.isCorrect != true) "❓ ❓ ❓" else displayArabic,
                                fontFamily = AmiriArabicFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSizeSp.sp,
                                color = if (testModeHideAr && evalState.isCorrect != true) palette.textDim else palette.textArabic,
                                textAlign = TextAlign.Center,
                                lineHeight = (fontSizeSp + 8).sp
                            )

                            if (showTr || evalState.isCorrect == true) {
                                val trPrefix = if (activeMorphLbl.isNotBlank()) "[$activeMorphLbl] " else ""
                                Text(
                                    text = "$trPrefix${currentItem.turkce}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (evalState.isCorrect == true) palette.okGreen else palette.textMain,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.changeWordIndex(1) },
                            modifier = Modifier
                                .background(palette.btnSec, RoundedCornerShape(10.dp))
                                .size(38.dp)
                                .testTag("btn_next_word")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Sonraki Kelime",
                                tint = palette.textMain
                            )
                        }
                    }

                    // Referans Ses ve Gölgeleme Butonları (Kompakt Tek Satır / FlowRow)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.speakCurrentItem("female") },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_speak_female")
                        ) {
                            Text("🔊 Kadın", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.speakCurrentItem("male") },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_speak_male")
                        ) {
                            Text("🔊 Erkek", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.openShadowingForCurrentCard() },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_open_shadowing")
                        ) {
                            Text("🗣️ Gölgele", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                val ok = viewModel.audioManager.playSavedWavFile()
                                if (!ok) viewModel.showToast("Dinlenecek ses kaydı bulunamadı.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("🎧 Sesim", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    // Canlı Ses Dalga Boyu ve Mikrofon Kayıt Butonu
                    LiveWaveformBars(
                        bars = waveBars,
                        isRecording = isRecording,
                        modifier = Modifier
                            .height(26.dp)
                            .padding(horizontal = 24.dp)
                    )

                    Button(
                        onClick = onRequestMicPermissionAndRecord,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) CoralRed else RoyalBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(44.dp)
                            .testTag("btn_word_record")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecording) "⏹️ KAYDI DURDUR" else "🎤 BAŞLA VE KONUŞ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Hızlı Telaffuz & Mahreç Test Butonları (Ayarlar -> Demo / Simülasyon Açık İse Gösterilir)
                    if (showDemoSim) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panelColor,
                                border = BorderStroke(1.dp, palette.okGreen.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clickable { viewModel.simulatePronunciationAttempt("correct") }
                                    .testTag("btn_sim_correct")
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
                                modifier = Modifier
                                    .clickable { viewModel.simulatePronunciationAttempt("makhraj") }
                                    .testTag("btn_sim_makhraj")
                            ) {
                                Text(
                                    text = "🔬 Mahreç/Kök Hatası Test Et",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WarmGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    if (attemptCount > 0) {
                        Text(
                            text = "Deneme: $attemptCount / $maxAttemptsSetting",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentHead
                        )
                    }

                    // Sonuç ve Teşhis Satırı
                    val verdictColor = when (evalState.isCorrect) {
                        true -> palette.okGreen
                        false -> CoralRed
                        null -> palette.textDim
                    }
                    Text(
                        text = evalState.verdictTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                        textAlign = TextAlign.Center
                    )
                    if (evalState.statusDetail.isNotBlank()) {
                        Text(
                            text = evalState.statusDetail,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textMain,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "[${currentItem.seviye}]  ${wordIndex + 1} / ${filteredWords.size}  •  Doğru: ${currentItem.dogru} | Yanlış: ${currentItem.yanlis}",
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
