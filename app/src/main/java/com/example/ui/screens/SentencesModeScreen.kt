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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val sentences by viewModel.filteredSentences.collectAsState()
    val sentenceIndex by viewModel.sentenceIndex.collectAsState()
    val showTr by viewModel.sentShowTr.collectAsState()
    val showTashkeel by viewModel.sentShowTashkeel.collectAsState()
    val hideArabic by viewModel.sentHideArabic.collectAsState()
    val testModeHideAr by viewModel.testModeHideArabic.collectAsState()
    val fontSizeSp by viewModel.sentFontSizeSp.collectAsState()

    val isRecording by viewModel.audioManager.isRecording.collectAsState()
    val waveBars by viewModel.audioManager.waveBars.collectAsState()
    val evalState by viewModel.evalState.collectAsState()

    var showFilterMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }
    var showCardLevelMenu by remember { mutableStateOf(false) }

    val currentItem = if (sentences.isNotEmpty()) {
        sentences[sentenceIndex.coerceIn(0, sentences.size - 1)]
    } else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Üst Filtre, Seviye ve Görünüm Barı
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showFilterMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sentence_filter_selector")
                        ) {
                            Text(
                                text = "Filtre: $selectedFilter",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead)
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            listOf("Tümü", "📅 Günün 15 Tekrarı", "Favoriler", "Öğrendiklerim", "Zorlandıklarım").forEach { f ->
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

                    Box {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Seviye: $selectedLevel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain
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
                                        viewModel.selectSentenceLevel(lvl)
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCoral,
                        modifier = Modifier.clickable { viewModel.openModal(ActiveModal.LISTENING_EXAM) }
                    ) {
                        Text(
                            text = "🎧 Dinle & Bul",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                // Görünüm ve Punto Satırı
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = hideArabic,
                            onClick = { viewModel.setSentHideArabic(!hideArabic) },
                            label = { Text("🙈 Arapçayı Gizle", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmGold,
                                selectedLabelColor = Color(0xFF0F172A)
                            )
                        )
                        FilterChip(
                            selected = showTashkeel,
                            onClick = { viewModel.setSentShowTashkeel(!showTashkeel) },
                            label = { Text("Hareke", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = showTr,
                            onClick = { viewModel.setSentShowTr(!showTr) },
                            label = { Text("🇹🇷 Türkçe", fontSize = 10.sp) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.btnSec,
                            modifier = Modifier.clickable { viewModel.changeSentenceFontSize(-2) }
                        ) {
                            Text(
                                text = "A-",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                        Text(" Punto ", fontSize = 10.sp, color = palette.textDim, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.btnSec,
                            modifier = Modifier.clickable { viewModel.changeSentenceFontSize(2) }
                        ) {
                            Text(
                                text = "A+",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. ANA CÜMLE KARTI
        val cardBorderColor = if (isRecording) EmeraldGreen else palette.border
        val cardBorderWidth = if (isRecording) 3.dp else 1.dp

        Card(
            shape = RoundedCornerShape(18.dp),
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
                        .padding(32.dp)
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Üst Kontroller: ⭐ Favori | ✅ Öğrendim | 🏷️ Seviye
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isFav = currentItem.isFav == 1
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFav) WarmGold else palette.btnSec,
                            modifier = Modifier.clickable {
                                scope.launch { viewModel.repository.toggleSentenceFavorite(currentItem.id) }
                            }
                        ) {
                            Text(
                                text = if (isFav) "⭐ Favori" else "☆ Favori",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFav) Color(0xFF0F172A) else palette.textMain,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        val isLearned = currentItem.isLearned == 1
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLearned) EmeraldGreen else palette.btnSec,
                            modifier = Modifier.clickable {
                                scope.launch { viewModel.repository.toggleSentenceLearned(currentItem.id) }
                            }
                        ) {
                            Text(
                                text = if (isLearned) "✅ Öğrendim" else "☑️ Öğrendim",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLearned) Color(0xFF0F172A) else palette.textMain,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = palette.panelColor,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.clickable { showCardLevelMenu = true }
                            ) {
                                Text(
                                    text = "🏷️ ${currentItem.seviye} ▾",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
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
                                    .padding(vertical = 8.dp)
                            ) {
                                evalState.wordStatuses.forEach { (wStr, isOk) ->
                                    val wDisp = if (showTashkeel) wStr else ArabicNlpEngine.removeDiacritics(wStr)
                                    Text(
                                        text = wDisp,
                                        fontFamily = AmiriArabicFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fontSizeSp.sp,
                                        color = if (isOk) palette.okGreen else CoralRed
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
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "🔒 [ Arapça Cümle Gizli — Söyleyin veya '🔊 Dinle'ye Basın ]",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textArabic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Text(
                            text = displayArabic,
                            fontFamily = AmiriArabicFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSizeSp.sp,
                            color = palette.textArabic,
                            textAlign = TextAlign.Center,
                            lineHeight = (fontSizeSp + 14).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                    }

                    // Türkçe Çeviri
                    if (showTr) {
                        Text(
                            text = currentItem.turkce,
                            fontSize = 16.sp,
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Ses Dalga Boyu ve Aksiyon Butonları
                    LiveWaveformBars(
                        bars = waveBars,
                        isRecording = isRecording,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRequestMicPermissionAndRecord,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) CoralRed else RoyalBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_sentence_record")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRecording) "⏹️ DURDUR" else "🎤 Telaffuz Et",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.speakCurrentItem(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_sentence_tts")
                        ) {
                            Text("🔊 Dinle (TTS)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.openShadowingForCurrentCard() },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🗣️ Gölgele", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Emülatör & Canlı Koç Test Butonları
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
