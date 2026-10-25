package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nlp.ArabicNlpEngine
import com.example.ui.components.LiveMakhrajAndHarakaCoachCard
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AmiriArabicFontFamily
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.WarmGold
import com.example.viewmodel.MainAppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogueModeScreen(
    viewModel: MainAppViewModel,
    onRequestMicForRoleplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val dialogues by viewModel.filteredDialogues.collectAsState()
    val selectedDialogueId by viewModel.selectedDialogueId.collectAsState()
    val selectedLevel by viewModel.selectedDialogueLevel.collectAsState()
    val lines by viewModel.currentDialogueLines.collectAsState()
    val submode by viewModel.dialogueSubmode.collectAsState()
    val selectedRole by viewModel.selectedRoleName.collectAsState()
    val showTr by viewModel.chatShowTr.collectAsState()
    val showTashkeel by viewModel.chatShowTashkeel.collectAsState()
    val fontSizeSp by viewModel.chatFontSizeSp.collectAsState()
    val showDemoSim by viewModel.showDemoSimulationButtons.collectAsState()

    val isPlayingAll by viewModel.isPlayingAllDialogue.collectAsState()
    val highlightedLineIdx by viewModel.highlightedDialogueLineIndex.collectAsState()
    val roleplayActive by viewModel.roleplayActive.collectAsState()
    val roleplayTurnIdx by viewModel.roleplayTurnIndex.collectAsState()
    val roleplayWaitingUser by viewModel.roleplayWaitingUser.collectAsState()
    val revealedWordsMap by viewModel.roleplayRevealedWords.collectAsState()
    val finalResultsMap by viewModel.roleplayFinalResults.collectAsState()
    val hintShownSet by viewModel.roleplayHintShownLines.collectAsState()
    val roleplayStatus by viewModel.roleplayStatusText.collectAsState()
    val evalState by viewModel.evalState.collectAsState()

    var showLevelMenu by remember { mutableStateOf(false) }
    var showDialogueMenu by remember { mutableStateOf(false) }
    var showRoleMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val currentDialogue = dialogues.firstOrNull { it.id == selectedDialogueId } ?: dialogues.firstOrNull()
    val distinctSpeakers = remember(lines) { lines.map { it.konusmaci }.distinct() }

    LaunchedEffect(highlightedLineIdx) {
        if (highlightedLineIdx != null && highlightedLineIdx!! in lines.indices) {
            listState.animateScrollToItem(highlightedLineIdx!!)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Kompakt Üst Diyalog Seçici ve Alt-Mod (Öğrenme vs Roleplay) Barı
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
                    Box {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "Seviye: $selectedLevel",
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
                                        viewModel.selectDialogueLevel(lvl)
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showDialogueMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .testTag("dialogue_selector")
                        ) {
                            val titleDisp = if (currentDialogue != null) {
                                "[${currentDialogue.seviye}] ${currentDialogue.baslikTr} — ${currentDialogue.baslikAr}"
                            } else "Diyalog Seçin"
                            Text(
                                text = titleDisp,
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
                            expanded = showDialogueMenu,
                            onDismissRequest = { showDialogueMenu = false }
                        ) {
                            dialogues.forEach { dlg ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "[${dlg.seviye}] ${dlg.baslikTr} — ${dlg.baslikAr}",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        showDialogueMenu = false
                                        viewModel.selectDialogue(dlg.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // Alt Mod Seçici: 📖 Öğrenme Modu vs 🎭 Ölçme / Canlandırma (Roleplay)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.switchDialogueSubmode("learn") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (submode == "learn") EmeraldGreen else palette.btnSec,
                            contentColor = if (submode == "learn") Color(0xFF0F172A) else palette.textMain
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("btn_submode_learn")
                    ) {
                        Text("📖 Öğrenme Modu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = { viewModel.switchDialogueSubmode("roleplay") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (submode == "roleplay") WarmGold else palette.btnSec,
                            contentColor = if (submode == "roleplay") Color(0xFF0F172A) else palette.textMain
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("btn_submode_roleplay")
                    ) {
                        Text("🎭 Canlandırma (Roleplay)", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }

                // Öğrenme veya Roleplay Kontrol Çubuğu
                if (submode == "learn") {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.togglePlayAllDialogue() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAll) CoralRed else AccentCoral,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_play_all_dialogue")
                        ) {
                            Text(
                                text = if (isPlayingAll) "⏹️ Durdur" else "▶️ Tümünü Oynat",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = showTashkeel,
                                onClick = { viewModel.setChatShowTashkeel(!showTashkeel) },
                                label = { Text("Hareke", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = showTr,
                                onClick = { viewModel.setChatShowTr(!showTr) },
                                label = { Text("🇹🇷 Çeviri", fontSize = 10.sp) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { showRoleMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "👤 Rol: $selectedRole",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentHead
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead)
                                }
                                DropdownMenu(
                                    expanded = showRoleMenu,
                                    onDismissRequest = { showRoleMenu = false }
                                ) {
                                    distinctSpeakers.forEach { spk ->
                                        DropdownMenuItem(
                                            text = { Text(spk, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                showRoleMenu = false
                                                viewModel.selectRoleName(spk)
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.toggleRoleplaySession() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (roleplayActive) CoralRed else EmeraldGreen,
                                    contentColor = if (roleplayActive) Color.White else Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_toggle_roleplay")
                            ) {
                                Text(
                                    text = if (roleplayActive) "⏹️ Bitir" else "🎬 Canlandırmayı Başlat",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (roleplayActive && roleplayWaitingUser) {
                                Button(
                                    onClick = onRequestMicForRoleplay,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoyalBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_roleplay_mic")
                                ) {
                                    Text("🎤 Konuş", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (showDemoSim) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = palette.cardColor,
                                        border = BorderStroke(1.dp, EmeraldGreen),
                                        modifier = Modifier.clickable { viewModel.simulatePronunciationAttempt("correct") }
                                    ) {
                                        Text(
                                            text = "🎯 Simüle Et",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.okGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (roleplayStatus.isNotBlank()) {
                            Text(
                                text = roleplayStatus,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead
                            )
                        }
                    }
                }
            }
        }

        // 2. Diyalog Satırları Sohbet Listesi
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(lines, key = { _, item -> item.id }) { idx, line ->
                val isUserRole = submode == "roleplay" && line.konusmaci == selectedRole
                val isActiveTurn = (roleplayActive && roleplayTurnIdx == idx) || (highlightedLineIdx == idx)
                val lineDone = finalResultsMap.containsKey(idx)

                val cardBg = when {
                    isUserRole -> if (palette.isDark) Color(0xFF132A2E) else Color(0xFFECFDF5)
                    idx % 2 == 0 -> palette.cardColor
                    else -> palette.panelColor
                }
                val borderCol = when {
                    isActiveTurn && isUserRole -> EmeraldGreen
                    isActiveTurn -> WarmGold
                    isUserRole -> Color(0xFF0D9488)
                    else -> palette.border
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(if (isActiveTurn) 2.5.dp else 1.dp, borderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Üst Satır: Sol Dinle / Telaffuz Et / Gölgele | Sağ Konuşmacı Rozeti
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val vType = if (line.cinsiyet.equals("K", ignoreCase = true)) "female" else "male"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCoral,
                                    modifier = Modifier.clickable {
                                        viewModel.audioManager.speakArabic(
                                            text = line.arapca,
                                            voiceType = vType,
                                            slow = viewModel.slowTts.value,
                                            audioPath = line.ses,
                                            itemId = "dlgline_${line.id}",
                                            paketId = line.diyalogId
                                        )
                                    }
                                ) {
                                    Text(
                                        text = "🔊 Dinle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = RoyalPurple,
                                    modifier = Modifier.clickable {
                                        viewModel.openShadowingForCustomTarget(
                                            "dlg_${line.id}",
                                            line.arapca,
                                            line.turkce
                                        )
                                    }
                                ) {
                                    Text(
                                        text = "🗣️ Gölgele",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            val spkLabel = buildString {
                                append("👤 ${line.konusmaci}")
                                if (line.konusmaciAr.isNotBlank()) append(" (${line.konusmaciAr})")
                                if (isUserRole) append(" [Sizin Rolünüz]")
                            }
                            Text(
                                text = spkLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUserRole) palette.okGreen else palette.accentHead
                            )
                        }

                        // Orta Satır: Arapça Metin veya Roleplay Maskesi
                        val dispAr = if (showTashkeel) line.arapca else ArabicNlpEngine.removeDiacritics(line.arapca)

                        if (isUserRole && roleplayActive && !lineDone) {
                            val mask = revealedWordsMap[idx] ?: emptyList()
                            val wordsRaw = dispAr.split(" ")
                            if (mask.any { it }) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        wordsRaw.forEachIndexed { wIdx, wStr ->
                                            val isRev = mask.getOrElse(wIdx) { false }
                                            Text(
                                                text = if (isRev) wStr else "❓",
                                                fontFamily = AmiriArabicFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = fontSizeSp.sp,
                                                color = if (isRev) palette.okGreen else palette.textDim
                                            )
                                        }
                                    }
                                }
                            } else {
                                val promptMsg = if (isActiveTurn) {
                                    "🎙️ [ Sıra Sizde — Türkçe İpucundan Yararlanarak Arapçasını Söyleyin ]"
                                } else {
                                    "🔒 [ Sizin Rolünüz — Sıra Geldiğinde Arapça Söyleyeceksiniz ]"
                                }
                                Text(
                                    text = promptMsg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActiveTurn) palette.okGreen else palette.textDim,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (isUserRole && lineDone) {
                            val wordResList = finalResultsMap[idx] ?: emptyList()
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    wordResList.forEach { (wStr, isOk) ->
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
                        } else {
                            Text(
                                text = dispAr,
                                fontFamily = AmiriArabicFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSizeSp.sp,
                                color = palette.textArabic,
                                textAlign = TextAlign.End,
                                lineHeight = (fontSizeSp + 12).sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Alt Satır: Türkçe Çeviri (Roleplay Modunda Akıllı Gizleme + 💡 Türkçe İpucu Göster Butonu)
                        if (line.turkce.isNotBlank() && showTr) {
                            if (isUserRole && !lineDone) {
                                if (idx in hintShownSet) {
                                    Text(
                                        text = "💡 İpucu (Türkçe): ${line.turkce}",
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = palette.textDim,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.CenterEnd,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = palette.btnSec,
                                            modifier = Modifier
                                                .clickable { viewModel.revealRoleplayHintForLine(idx) }
                                                .testTag("btn_reveal_tr_hint_$idx")
                                        ) {
                                            Text(
                                                text = "💡 Türkçe İpucu Göster",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.textMain,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = line.turkce,
                                    fontSize = 13.sp,
                                    color = palette.textDim,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Canlı Mahreç Koçu Özeti
        LiveMakhrajAndHarakaCoachCard(evalState = evalState)
    }
}
