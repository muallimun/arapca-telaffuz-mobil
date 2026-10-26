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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.draw.alpha
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val packages by viewModel.wordPackages.collectAsState()
    val allSozluk by viewModel.allSozluk.collectAsState()
    val selectedCategory by viewModel.selectedWordCategory.collectAsState()
    val selectedStatusFilter by viewModel.selectedWordStatusFilter.collectAsState()
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
    val searchQuery by viewModel.wordSearchQuery.collectAsState()
    val isRadioModeActive by viewModel.isRadioModeActive.collectAsState()

    val isRecording by viewModel.audioManager.isRecording.collectAsState()
    val waveBars by viewModel.audioManager.waveBars.collectAsState()
    val evalState by viewModel.evalState.collectAsState()

    var showPackageMenu by remember { mutableStateOf(false) }
    var showLevelMenu by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var pendingMicAction by remember { mutableStateOf<String?>(null) }

    val wordMicPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingMicAction
        pendingMicAction = null
        if (granted) {
            if (action == "shadowing") {
                viewModel.openShadowingForCurrentCard()
            } else {
                viewModel.startPronunciationRecording()
            }
        } else {
            viewModel.onMicrophonePermissionDenied()
        }
    }

    fun checkMicAndRun(actionType: String) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            if (actionType == "shadowing") {
                viewModel.openShadowingForCurrentCard()
            } else {
                onRequestMicPermissionAndRecord()
            }
        } else {
            pendingMicAction = actionType
            wordMicPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val safeWordIndex = if (filteredWords.isNotEmpty()) {
        wordIndex.coerceIn(0, filteredWords.size - 1)
    } else 0

    val currentItem = if (filteredWords.isNotEmpty()) {
        filteredWords[safeWordIndex]
    } else null

    val morphInfo = remember(currentItem, filteredWords, allSozluk) {
        viewModel.currentWordMorphology()
    }

    val currentCategoryTitle = when (selectedCategory) {
        "tumu" -> "📦 Tümü"
        "gorselli_tumu" -> "🖼️ Tüm Görselli Kelimeler"
        "sozluk_tumu" -> "📖 Tüm Sözlük Kelimeleri"
        else -> packages.firstOrNull { it.id == selectedCategory }?.ad ?: "📦 Paket Seçin"
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
                // Üst Seçici Bar: Paket (weight 1.1f) | Seviye "🎯 Tümü" (weight 0.95f) | Filtre Toggle -> Hepsi 38.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Paket Seçici
                    Box(modifier = Modifier.weight(1.1f)) {
                        OutlinedButton(
                            onClick = { showPackageMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("word_package_selector")
                        ) {
                            Text(
                                text = currentCategoryTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentHead,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.accentHead, modifier = Modifier.size(15.dp))
                        }
                        DropdownMenu(
                            expanded = showPackageMenu,
                            onDismissRequest = { showPackageMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📦 Tüm Kelimeler", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showPackageMenu = false
                                    viewModel.selectWordCategory("tumu")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🖼️ Tüm Görselli Kelimeler", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showPackageMenu = false
                                    viewModel.selectWordCategory("gorselli_tumu")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📖 Tüm Sözlük Kelimeleri", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showPackageMenu = false
                                    viewModel.selectWordCategory("sozluk_tumu")
                                }
                            )
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

                    // Seviye Seçici ("Seviye: " ön eki olmadan "🎯 Tümü" veya "🎯 A1")
                    Box(modifier = Modifier.weight(0.95f)) {
                        OutlinedButton(
                            onClick = { showLevelMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("word_level_selector")
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
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.textMain, modifier = Modifier.size(15.dp))
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
                                        viewModel.selectWordLevel(lvl)
                                    }
                                )
                            }
                        }
                    }

                    // Açılır-Kapanır Görünüm & Filtre Butonu
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterExpanded) palette.accentHead.copy(alpha = 0.2f) else palette.btnSec,
                        border = BorderStroke(1.dp, if (filterExpanded) palette.accentHead else palette.border),
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(38.dp)
                            .clickable { filterExpanded = !filterExpanded }
                            .testTag("btn_toggle_word_filters")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
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

                // Açılır-Kapanır Ek Filtreler: 1) Arama 2) Tek Satır Durum Çipleri 3) Kart Görünüm Ayarları
                if (filterExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 2. Satır (Arama): Tam Genişlikte (fillMaxWidth) Arama Kutusu (Kategori seçicilerin hemen altında)
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
                                        onValueChange = { viewModel.setWordSearchQuery(it) },
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
                                            .clickable { viewModel.setWordSearchQuery("") }
                                            .padding(start = 6.dp)
                                    )
                                }
                            }
                        }

                        // 3. Satır (Veri Filtre Çipleri): Kesinlikle tek satırda yan yana 5 çip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusItems = listOf(
                                "Tümü" to "Tümü",
                                "Günlük 15" to "🔥 Günlük 15",
                                "Favoriler" to "⭐ Favori",
                                "Ezberlenmeyen" to "📌 Ezberlenmeyen",
                                "Zayıflar" to "⚠️ Zayıf"
                            )
                            statusItems.forEach { (statusKey, label) ->
                                val isSel = selectedStatusFilter == statusKey
                                val testTagId = when (statusKey) {
                                    "Günlük 15" -> "chip_daily_15"
                                    "Favoriler" -> "chip_favorites"
                                    "Zayıflar" -> "chip_weak_words"
                                    "Ezberlenmeyen" -> "chip_ezberlenmeyen"
                                    else -> "chip_tumu"
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) TealAccent else palette.btnSec,
                                    border = BorderStroke(1.dp, if (isSel) TealAccent else palette.border),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clickable { viewModel.selectWordStatusFilter(statusKey) }
                                        .testTag(testTagId)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
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

                        // 4. Satır (Kart Görünüm Ayarları): Kelime kartının hemen üstünde, optimize genişlik oranlarıyla
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.setTestModeHideArabic(!testModeHideAr) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (testModeHideAr) WarmGold else palette.btnSec,
                                    contentColor = if (testModeHideAr) Color(0xFF0F172A) else palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(34.dp)
                            ) {
                                Text("🙈 Gizle", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.setWordShowTashkeel(!showTashkeel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showTashkeel) RoyalBlue else palette.btnSec,
                                    contentColor = if (showTashkeel) Color.White else palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(34.dp)
                            ) {
                                Text("✨ Hareke", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.setWordShowTr(!showTr) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showTr) RoyalBlue else palette.btnSec,
                                    contentColor = if (showTr) Color.White else palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(34.dp)
                            ) {
                                Text("🇹🇷 Anlam", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.changeWordFontSize(-2) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.btnSec,
                                    contentColor = palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(0.68f)
                                    .height(34.dp)
                            ) {
                                Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { viewModel.changeWordFontSize(2) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.btnSec,
                                    contentColor = palette.textMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(0.68f)
                                    .height(34.dp)
                            ) {
                                Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }

        // 2. ANA KELİME VE SARF KARTI (Sadeleştirilmiş & Kaydırılabilir Orta Alan + Sabit Alt Butonlar)
        val cardBorderColor = if (isRecording) EmeraldGreen else palette.border
        val cardBorderWidth = if (isRecording) 3.dp else 1.dp

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(cardBorderWidth, cardBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("main_word_card")
        ) {
            if (currentItem == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bu filtre veya aramada kelime bulunamadı (Filtreyi veya seviyeyi 'Tümü' yapabilirsiniz).",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textDim,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val rawArabicToDisplay = activeMorphAr ?: currentItem.arapca
                val displayArabic = if (showTashkeel) {
                    rawArabicToDisplay
                } else {
                    ArabicNlpEngine.removeDiacritics(rawArabicToDisplay)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // 1. ÜST META BARI (Sabit): Sol (☆/⭐, ☑️/✅ ve 📤 Paylaş) | Orta (Sayaç + Doğru/Yanlış + Hak: X/3) | Sağ (Seviye Rozeti)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isFav = currentItem.isFav == 1
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFav) WarmGold else palette.btnSec,
                                    border = BorderStroke(1.dp, if (isFav) WarmGold else palette.border),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable {
                                            scope.launch { viewModel.repository.toggleWordFavorite(currentItem.id) }
                                        }
                                        .testTag("btn_word_fav")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isFav) "⭐" else "☆",
                                            fontSize = 14.sp,
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
                                        .size(32.dp)
                                        .clickable {
                                            scope.launch { viewModel.repository.toggleWordLearned(currentItem.id) }
                                        }
                                        .testTag("btn_word_learned")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isLearned) "✅" else "☑️",
                                            fontSize = 13.sp,
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
                                        .size(32.dp)
                                        .clickable { viewModel.shareCurrentCard(context) }
                                        .testTag("btn_share_word_card")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "📤",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            val shortMaxAttempts = if (maxAttemptsSetting.contains("Sınırsız")) {
                                "∞"
                            } else {
                                maxAttemptsSetting.substringBefore(" ")
                            }
                            Text(
                                text = "${safeWordIndex + 1}/${filteredWords.size} • ✅${currentItem.dogru} ❌${currentItem.yanlis} • 🎯$attemptCount/$shortMaxAttempts",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textDim,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )

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
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 2. ORTA BÖLÜM (Kaydırılabilir & AnimatedContent Geçişli): Görsel/Sarf Barı + < Arapça Kelime & Türkçe Anlam > + Canlı Mahreç Koçu
                        val middleScrollState = rememberScrollState()
                        LaunchedEffect(currentItem.id) {
                            middleScrollState.scrollTo(0)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(middleScrollState)
                                .padding(top = 2.dp, bottom = 2.dp)
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
                                                viewModel.changeWordIndex(1)
                                            } else if (totalDrag > 60f) {
                                                viewModel.changeWordIndex(-1)
                                            }
                                        }
                                    )
                                }
                        ) {
                            AnimatedContent(
                                targetState = safeWordIndex,
                                transitionSpec = {
                                    val dir = if (targetState >= initialState) 1 else -1
                                    (slideInHorizontally(tween(240)) { fullW -> dir * (fullW / 3) } + fadeIn(tween(220)))
                                        .togetherWith(slideOutHorizontally(tween(220)) { fullW -> -dir * (fullW / 3) } + fadeOut(tween(180)))
                                },
                                label = "WordCardSlideAnimation",
                                modifier = Modifier.fillMaxWidth()
                            ) { animIdx ->
                                val animItem = filteredWords.getOrNull(animIdx) ?: currentItem
                                val animRawAr = activeMorphAr ?: animItem.arapca
                                val animDisplayAr = if (showTashkeel) animRawAr else ArabicNlpEngine.removeDiacritics(animRawAr)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Görsel İllüstrasyon Kutusu (Kesilmeyi Önleyen Kompakt Esnek Boyut 60.dp..94.dp) VEYA Sarf Sözlüğü Barı
                                    if (!animItem.gorsel.isNullOrBlank()) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .sizeIn(minWidth = 60.dp, maxWidth = 94.dp, minHeight = 60.dp, maxHeight = 94.dp)
                                        ) {
                                            com.example.util.HybridWordVisualBox(
                                                wordId = animItem.id,
                                                paketId = animItem.paketId,
                                                gorsel = animItem.gorsel,
                                                sizeDp = 82.dp
                                            )
                                        }
                                    } else if (morphInfo != null) {
                                        WordMorphologyCardBar(
                                            morphInfo = morphInfo,
                                            activeMorphArabic = activeMorphAr,
                                            defaultWordArabic = animItem.arapca,
                                            onSelectForm = { ar, lbl -> viewModel.selectMorphFormOnCard(ar, lbl) },
                                            onOpenRootExplorer = { root ->
                                                viewModel.openModal(ActiveModal.SARF_EXPLORER, root)
                                            }
                                        )
                                    }

                                    // Önceki (<) / Büyük Arapça Kelime & Türkçe Anlam / Sonraki (>) Satırı
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.changeWordIndex(-1) },
                                            modifier = Modifier
                                                .background(palette.btnSec, RoundedCornerShape(10.dp))
                                                .size(40.dp)
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
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            if (evalState.wordStatuses.size > 1 && evalState.expectedArabic == animRawAr) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.fillMaxWidth()
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
                                            } else {
                                                Text(
                                                    text = if (testModeHideAr && evalState.isCorrect != true) "❓ ❓ ❓" else animDisplayAr,
                                                    fontFamily = AmiriArabicFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = fontSizeSp.sp,
                                                    color = if (testModeHideAr && evalState.isCorrect != true) palette.textDim else palette.textArabic,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = (fontSizeSp + 8).sp,
                                                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                                )
                                            }

                                            if (showTr || evalState.isCorrect == true) {
                                                val trPrefix = if (activeMorphLbl.isNotBlank()) "[$activeMorphLbl] " else ""
                                                Text(
                                                    text = "$trPrefix${animItem.turkce}",
                                                    fontSize = 19.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (evalState.isCorrect == true) palette.okGreen else palette.textMain,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.changeWordIndex(1) },
                                            modifier = Modifier
                                                .background(palette.btnSec, RoundedCornerShape(10.dp))
                                                .size(40.dp)
                                                .testTag("btn_next_word")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Sonraki Kelime",
                                                tint = palette.textMain
                                            )
                                        }
                                    }
                                }
                            }

                            // Göz Hizasında Dinamik Mahreç & Telaffuz Koçu Kutusu (Orta kaydırılabilir alanın içinde)
                            LiveMakhrajAndHarakaCoachCard(
                                evalState = evalState,
                                activeArabicText = rawArabicToDisplay,
                                onSpeakWord = { wStr ->
                                    viewModel.audioManager.speakArabic(wStr, "male", viewModel.slowTts.value)
                                }
                            )
                        }

                        // 3. ALT BÖLÜM (Sabit): Referans Ses & Oto-Dinle Butonları + Mikrofon Butonu
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Referans Ses, Gölgeleme, Sesim ve Radyo Butonları (Kompakt Tek Satır, Eşit Genişlik, Kesilmesiz 4 Buton)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.speakCurrentItem(null) },
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCoral,
                                        contentColor = Color.White,
                                        disabledContainerColor = AccentCoral,
                                        disabledContentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .alpha(if (isRecording) 0.5f else 1f)
                                        .testTag("btn_speak_female")
                                ) {
                                    Text(
                                        text = "🔊 Dinle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Button(
                                    onClick = { checkMicAndRun("shadowing") },
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoyalPurple,
                                        contentColor = Color.White,
                                        disabledContainerColor = RoyalPurple,
                                        disabledContentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .alpha(if (isRecording) 0.5f else 1f)
                                        .testTag("btn_open_shadowing")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Gölge",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val ok = viewModel.audioManager.playSavedWavFile()
                                        if (!ok) viewModel.showToast("Dinlenecek ses kaydı bulunamadı.")
                                    },
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.btnSec,
                                        contentColor = palette.textMain,
                                        disabledContainerColor = palette.btnSec,
                                        disabledContentColor = palette.textMain
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .alpha(if (isRecording) 0.5f else 1f)
                                ) {
                                    Text(
                                        text = "🎧 Sesim",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                val radioBg = if (isRadioModeActive) CoralRed else TealAccent
                                Button(
                                    onClick = { viewModel.toggleRadioMode() },
                                    enabled = !isRecording,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = radioBg,
                                        contentColor = Color.White,
                                        disabledContainerColor = radioBg,
                                        disabledContentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .alpha(if (isRecording) 0.5f else 1f)
                                        .testTag("btn_radio_mode_word")
                                ) {
                                    Text(
                                        text = if (isRadioModeActive) "⏹️ Dur" else "📻 Radyo",
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
                                onClick = { checkMicAndRun("record") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) CoralRed else RoyalBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_word_record")
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
