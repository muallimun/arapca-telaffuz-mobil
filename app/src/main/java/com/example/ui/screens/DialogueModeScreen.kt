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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    val handsFree by viewModel.handsFreeAutoStart.collectAsState()

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
    val isRecording by viewModel.audioManager.isRecording.collectAsState()

    val context = LocalContext.current
    var pendingPracticeLineIdx by remember { mutableStateOf<Int?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val idxToRun = pendingPracticeLineIdx
        pendingPracticeLineIdx = null
        if (granted && idxToRun != null) {
            viewModel.practiceSingleDialogueLine(idxToRun)
        } else if (!granted) {
            viewModel.showToast("Sesli telaffuz için mikrofon izni gereklidir.")
        }
    }

    fun requestAndPracticeLine(lineIdx: Int) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.practiceSingleDialogueLine(lineIdx)
        } else {
            pendingPracticeLineIdx = lineIdx
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val currentView = LocalView.current
    val shouldKeepScreenOn = isPlayingAll || roleplayActive || handsFree || isRecording
    DisposableEffect(currentView, shouldKeepScreenOn) {
        currentView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            currentView.keepScreenOn = false
        }
    }

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
                // Üst Seçici Bar: Diyalog (weight 1.2f) | Seviye (weight 1f) -> Hepsi 38.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedButton(
                            onClick = { showDialogueMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
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
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead, modifier = Modifier.size(16.dp))
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
                                        viewModel.selectDialogueLevel(lvl)
                                    }
                                )
                            }
                        }
                    }
                }

                // Alt Mod Seçici: 📖 Öğrenme vs 🎭 Rol Yap
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_submode_learn")
                    ) {
                        Text(
                            text = "📖 Öğrenme",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = { viewModel.switchDialogueSubmode("roleplay") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (submode == "roleplay") WarmGold else palette.btnSec,
                            contentColor = if (submode == "roleplay") Color(0xFF0F172A) else palette.textMain
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_submode_roleplay")
                    ) {
                        Text(
                            text = "🎭 Rol Yap",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }

                // Öğrenme veya Roleplay Kontrol Çubuğu
                if (submode == "learn") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(34.dp)
                                .testTag("btn_play_all_dialogue")
                        ) {
                            Text(
                                text = if (isPlayingAll) "⏹️ Durdur" else "▶️ Tümünü Çal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = { viewModel.setChatShowTashkeel(!showTashkeel) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showTashkeel) RoyalBlue else palette.btnSec,
                                contentColor = if (showTashkeel) Color.White else palette.textMain
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = "✨ Hareke",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = { viewModel.setChatShowTr(!showTr) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showTr) RoyalBlue else palette.btnSec,
                                contentColor = if (showTr) Color.White else palette.textMain
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = "🇹🇷 Anlam",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = { viewModel.changeChatFontSize(-2) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.btnSec,
                                contentColor = palette.textMain
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.changeChatFontSize(2) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.btnSec,
                                contentColor = palette.textMain
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                        // Üst Satır: Sol Kompakt İkon Butonlar (🔊, 🎤, 🗣️) | Sağ Konuşmacı Rozeti
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val vType = if (line.cinsiyet.equals("K", ignoreCase = true)) "female" else "male"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCoral,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable {
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
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = "🔊",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                if (submode == "learn") {
                                    val isRecordingThisLine = isRecording && highlightedLineIdx == idx && !roleplayActive
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isRecordingThisLine) CoralRed else EmeraldGreen,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable { requestAndPracticeLine(idx) }
                                            .testTag("btn_practice_dlg_line_$idx")
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = if (isRecordingThisLine) "⏹️" else "🎤",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRecordingThisLine) Color.White else Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = RoyalPurple,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable {
                                            viewModel.openShadowingForCustomTarget(
                                                "dlg_${line.id}",
                                                line.arapca,
                                                line.turkce
                                            )
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = "🗣️",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            val spkLabel = buildString {
                                append("👤 ${line.konusmaci}")
                                if (line.konusmaciAr.isNotBlank()) append(" (${line.konusmaciAr})")
                                if (isUserRole) append(" [Rolünüz]")
                            }
                            Text(
                                text = spkLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUserRole) palette.okGreen else palette.accentHead,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
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
                                                color = if (isRev) palette.okGreen else palette.textDim,
                                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
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
                        } else if (lineDone && (isUserRole || submode == "learn")) {
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
                                            color = if (isOk) palette.okGreen else CoralRed,
                                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                                            modifier = Modifier.clickable {
                                                viewModel.audioManager.speakArabic(wStr, "male", viewModel.slowTts.value)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = dispAr,
                                    fontFamily = AmiriArabicFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = fontSizeSp.sp,
                                    color = palette.textArabic,
                                    textAlign = TextAlign.Start,
                                    lineHeight = (fontSizeSp + 12).sp,
                                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
        LiveMakhrajAndHarakaCoachCard(
            evalState = evalState,
            onSpeakWord = { wStr ->
                viewModel.audioManager.speakArabic(wStr, "male", viewModel.slowTts.value)
            }
        )
    }
}
