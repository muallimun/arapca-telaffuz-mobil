package com.example.ui.modals

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
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
import kotlin.math.roundToInt

// =============================================================================
// 1. 👋 7 ADIMLI KAPSAMLI İNTERAKTİF BAŞLANGIÇ TURU (ONBOARDING)
// =============================================================================
private data class OnboardingStep(
    val badge: String,
    val icon: String,
    val title: String,
    val description: String,
    val tip: String
)

@Composable
fun SevenStepOnboardingTourModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    OnboardingTourModal(viewModel = viewModel, onDismiss = onDismiss)
}

@Composable
fun OnboardingTourModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    var curStep by remember { mutableIntStateOf(0) }
    var dontShowAgain by remember { mutableStateOf(true) }

    val steps = remember {
        listOf(
            OnboardingStep(
                badge = "ADIM 1 / 7 — 🎯 HOŞ GELDİNİZ",
                icon = "🎯  🕌  📱",
                title = "🎯 Hoş Geldiniz: Mobil Arapça Telaffuz Asistanı",
                description = "Arapçayı sadece okuyarak değil, sesli konuşarak ve doğru mahreçle öğrenmeniz için tasarlanan mobil telaffuz asistanınıza hoş geldiniz!\n\nEkranın en altındaki gezinme çubuğundan 4 ana çalışma moduna (🏠 Ana Sayfa, 🔤 Kelime, 💬 Diyalog, 📝 Cümle) tek dokunuşla anında geçiş yapabilirsiniz.",
                tip = "💡 Üst bardaki ☀️/🌙 ikonuyla temayı değiştirebilir, ℹ️ butonuyla kılavuz ve tanıtım turuna her zaman ulaşabilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 2 / 7 — 🎤 SESLİ TELAFFUZ & MAHREÇ KOÇU",
                icon = "🎤  🎯  🔬",
                title = "🎤 Sesli Telaffuz & Canlı Mahreç Koçu",
                description = "Çalışma kartlarındaki '🎤 Mikrofon' butonuna basıp Arapça kelimeyi veya cümleyi sesli okuyun.\n\nCanlı ses tanıma motoru okumanızı anında analiz eder; boğaz (ع-ح-خ), peltek (ث-ذ-ظ), kalın/ince (ص-س, ط-ت, ض-د, ق-ك) harf farklarını ve med-vezin (uzatma) hatalarını tespit ederek ağız ve boğazdaki doğru çıkış noktasını size tarif eder.",
                tip = "💡 Cümle içinde hatalı okuduğunuz kırmızı kelimenin üzerine dokunarak yavaşlatılmış doğru telaffuzunu anında dinleyebilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 3 / 7 — 🗣️ GÖLGELEME & KENDİ SESİNİ DİNLEME",
                icon = "🗣️  🔊  🎧",
                title = "🗣️ Gölgeleme (Shadowing) & Kendi Sesini Dinleme",
                description = "• 🔊 Kadın / Erkek butonlarıyla kelimenin veya cümlenin örnek telaffuzunu dinleyin.\n• 🗣️ 'Gölgele' butonuyla önce örnek sesi duyup hemen ardından beklemeden tekrar ederek ritim ve vurgu çalışması yapın.\n• 🎧 'Sesim' butonuyla kendi son ses kaydınızı dinleyip örnek okunuşla karşılaştırın.",
                tip = "💡 Üst üste zorlandığınız kelimelerde Koç otomatik olarak '🐢 Yavaş Seslendirme (%25)' desteğini devreye sokar."
            ),
            OnboardingStep(
                badge = "ADIM 4 / 7 — 🌿 SARF KÖK SÖZLÜĞÜ & KÖK ATÖLYESİ",
                icon = "📖  🌿  🐝",
                title = "🌿 Sarf Kök Sözlüğü & 6'lı Petek Görünümü",
                description = "Üst menüdeki '📖 Sözlük' butonuna dokunarak Arapça kelimelerin 3 harfli sülâsî köklerini, vezinlerini ve aynı kökten türeyen kelime ailesini keşfedin.\n\nİsimlerin Tekil/Çoğul (Müfret/Cemî), fiillerin Mâzî/Muzâri/Masdar çekimlerini ve 6'lı petek (altıgen kök) görünümünü sesli olarak inceleyin.",
                tip = "💡 Kelime kartlarındaki 3 harfli kök rozetine dokunarak o kelimenin tüm türev ailesini anında açabilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 5 / 7 — 🎭 DİYALOG CANLANDIRMA (ROLEPLAY)",
                icon = "🎭  💬  🎙️",
                title = "🎭 Diyalog Canlandırma — Roleplay",
                description = "Alt menüden '💬 Diyalog' moduna geçip '🎭 Canlandırma (Roleplay)' sekmesini seçin.\n\nDiyalogdaki karakterlerden birini seçtiğinizde karşı rolü uygulama sesli olarak okur; sıra sizin karakterinize geldiğinde mikrofon açılır ve gerçek bir konuşma partneriyle pratik yapıyormuş gibi sesli Arapça diyalog kurarsınız.",
                tip = "💡 Canlandırma sırasında takıldığınız yerde '💡 İpucu' butonuyla cümlenin anlamını görebilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 6 / 7 — 🎧 DİNLE & BUL VE GÜNÜN 15 TEKRARI",
                icon = "🎧  📅  ⭐",
                title = "🎧 Dinle & Bul ve Günün 15 Tekrarı",
                description = "• Üst menüdeki '🎧 Sınav' butonuyla Arapça sesi dinleyip doğru anlamı bulduğunuz çoktan seçmeli sesli testler çözün.\n• Kelime ve Cümle ekranlarındaki filtre panelinden '📅 Günlük 15', '📌 Ezberlenmeyen' veya '⚠️ Zayıf' çiplerini seçerek her gün akıllı aralıklı tekrar (SM-2) yapın.",
                tip = "💡 Filtre panelinden '🙈 Gizle', '✨ Hareke' ve '🇹🇷 Anlam' butonlarıyla kart görünümünü tek dokunuşla özelleştirebilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 7 / 7 — 📊 GELİŞİM KARNESİ & KOLAY İÇERİK EKLEME",
                icon = "📊  🏅  📥",
                title = "📊 Gelişim Karnesi & Kolay İçerik Ekleme",
                description = "• Üst menüdeki '📊 Karne' ekranından kazandığınız başarı rozetlerini, doğruluk oranınızı ve en çok zorlandığınız kelimeleri takip edin.\n• İçerik Yönetimi ekranından panoya kopyaladığınız listeleri veya hazır paket dosyalarını (.db / .json / .csv) tek dokunuşla aktararak yeni kelime, cümle ve diyaloglar ekleyin.",
                tip = "💡 Şimdi '🚀 Hemen Başla!' butonuna dokunarak ilk sesli Arapça pratiğinize başlayabilirsiniz!"
            )
        )
    }

    val step = steps[curStep]

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = step.badge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead,
                    modifier = Modifier.weight(1f)
                )
                val dots = steps.indices.joinToString(" ") { if (it == curStep) "●" else "○" }
                Text(
                    text = dots,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.okGreen
                )
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.cardColor),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(text = step.icon, fontSize = 32.sp)
                    Text(
                        text = step.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = step.description,
                        fontSize = 13.sp,
                        color = palette.textMain,
                        lineHeight = 20.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.panelColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = step.tip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentHead,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Text("Bir daha açılışta gösterme", fontSize = 12.sp, color = palette.textDim)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.finishOnboardingTour(dontShowAgain) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.panelColor, contentColor = palette.textDim),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_skip_tour")
                    ) {
                        Text("⏭️ Turu Atla", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (curStep > 0) {
                        Button(
                            onClick = { curStep-- },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("◀", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            if (curStep < steps.size - 1) {
                                curStep++
                            } else {
                                viewModel.finishOnboardingTour(dontShowAgain)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (curStep == steps.size - 1) EmeraldGreen else RoyalBlue,
                            contentColor = if (curStep == steps.size - 1) Color(0xFF0F172A) else Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_next_tour")
                    ) {
                        Text(
                            text = if (curStep == steps.size - 1) "✅ Bitir" else "▶",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. 👤 ÖĞRENCİ / KULLANICI İSMİ KAYIT PENCERESİ
// =============================================================================
@Composable
fun StudentNameModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val currentName by viewModel.studentName.collectAsState()
    var nameInput by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardColor,
        title = {
            Text(
                text = "👋 Öğrenci / Kullanıcı Kaydı",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentHead
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Lütfen adınızı ve soyadınızı girin. (Bu isim ana ekranda, başarı karnenizde ve öğretmen raporlarında yer alacaktır):",
                    fontSize = 13.sp,
                    color = palette.textMain
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Adınız Soyadınız") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveStudentName(nameInput) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                modifier = Modifier.testTag("btn_save_student_name")
            ) {
                Text("✅ Kaydet ve Başla", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// =============================================================================
// 3. ⚙️ SEKMELİ UYGULAMA VE EĞİTİM AYARLARI PENCERESİ
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val scope = rememberCoroutineScope()

    val studentName by viewModel.studentName.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val wordFontSizeSp by viewModel.wordFontSizeSp.collectAsState()
    val sentFontSizeSp by viewModel.sentFontSizeSp.collectAsState()
    val chatFontSizeSp by viewModel.chatFontSizeSp.collectAsState()
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
    val hapticFeedback by viewModel.hapticFeedbackEnabled.collectAsState()
    val autoPlay by viewModel.autoPlayOnCardChange.collectAsState()
    val showTashkeel by viewModel.wordShowTashkeel.collectAsState()
    val autoAdvance by viewModel.autoAdvanceOnCorrect.collectAsState()
    val handsFree by viewModel.handsFreeAutoStart.collectAsState()
    val randomOrder by viewModel.randomOrder.collectAsState()
    val testModeHideAr by viewModel.testModeHideArabic.collectAsState()
    val voicePref by viewModel.voicePreference.collectAsState()
    val maxAttempts by viewModel.maxAttemptsSetting.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editedName by remember(studentName) { mutableStateOf(studentName) }
    var recStats by remember { mutableStateOf(viewModel.audioManager.getRecordingsStats()) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚙️ Ayarlar",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
                    text = {
                        Text(
                            text = "🎨 Görünüm",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "🔊 Ses",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "🎯 Çalışma",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    // 1. BÖLÜM: 🎨 GÖRÜNÜM & TEMA
                    0 -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    label = { Text("👤 Öğrenci / Kullanıcı Adı Soyadı") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "🎨 Tema Seçimi:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                val themeOptions = listOf(
                                    "gece" to "🌙 Gece",
                                    "gunduz" to "☀️ Gündüz",
                                    "okul" to "🏫 Okul",
                                    "kurumsal" to "🏛️ Kurumsal"
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    themeOptions.chunked(2).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowItems.forEach { (modeKey, modeLabel) ->
                                                val isSel = appThemeMode == modeKey
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSel) RoyalBlue else palette.btnSec,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSel) RoyalBlue else palette.border
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(38.dp)
                                                        .clickable { viewModel.setAppThemeMode(modeKey) }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text(
                                                            text = modeLabel,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSel) Color.White else palette.textMain,
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🔠 Arapça Yazı Tipi Boyutu (Punto):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Text(
                                    text = "Kelime: ${wordFontSizeSp}sp • Cümle: ${sentFontSizeSp}sp • Diyalog: ${chatFontSizeSp}sp",
                                    fontSize = 11.sp,
                                    color = palette.textDim
                                )

                                val fontPresets = listOf(
                                    32 to "Küçük",
                                    40 to "Normal",
                                    46 to "Büyük",
                                    52 to "Çok Büyük"
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    fontPresets.chunked(2).forEach { rowPresets ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowPresets.forEach { (spVal, label) ->
                                                val isSel = kotlin.math.abs(wordFontSizeSp - spVal) <= 2
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSel) EmeraldGreen else palette.btnSec,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSel) EmeraldGreen else palette.border
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(38.dp)
                                                        .clickable { viewModel.setGlobalFontSizePreset(spVal) }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSel) Color(0xFF0F172A) else palette.textMain,
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Slider(
                                    value = wordFontSizeSp.toFloat(),
                                    onValueChange = { viewModel.setGlobalFontSizePreset(it.roundToInt()) },
                                    valueRange = 28f..54f,
                                    steps = 12,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accentHead,
                                        activeTrackColor = RoyalBlue
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.cardColor,
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 12.dp)
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                            Text(
                                                text = if (showTashkeel) "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ" else "بسم الله الرحمن الرحيم",
                                                fontFamily = AmiriArabicFontFamily,
                                                fontSize = (wordFontSizeSp * 0.65f).coerceIn(18f, 34f).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.textMain,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. BÖLÜM: 🔊 SES & OKUMA
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val formattedRate = String.format(java.util.Locale.US, "%.2fx", ttsSpeechRate)
                                Text(
                                    text = "🎚️ Seslendirme Hızı: $formattedRate",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val speedPresets = listOf(
                                        0.75f to "🐢 0.75x Yavaş",
                                        1.0f to "▶️ 1.0x Normal",
                                        1.25f to "⚡ 1.25x Hızlı"
                                    )
                                    speedPresets.forEach { (rateVal, rateLabel) ->
                                        val isSel = kotlin.math.abs(ttsSpeechRate - rateVal) < 0.04f
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) RoyalBlue else palette.btnSec,
                                            border = BorderStroke(1.dp, if (isSel) RoyalBlue else palette.border),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.setTtsSpeechRate(rateVal) }
                                        ) {
                                            Text(
                                                text = rateLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else palette.textMain,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }
                                Slider(
                                    value = ttsSpeechRate,
                                    onValueChange = { viewModel.setTtsSpeechRate(it) },
                                    valueRange = 0.75f..1.25f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accentHead,
                                        activeTrackColor = RoyalBlue
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "🎙️ Seslendirme Tercihi:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Kadın" to "👩 Kadın", "Erkek" to "👨 Erkek", "Sırayla" to "🔄 Sırayla").forEach { (prefKey, prefLabel) ->
                                        val isSel = voicePref == prefKey
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) RoyalBlue else palette.btnSec,
                                            border = BorderStroke(1.dp, if (isSel) RoyalBlue else palette.border),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.setVoicePreference(prefKey) }
                                        ) {
                                            Text(
                                                text = prefLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else palette.textMain,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }

                                SettingSwitchRow("🔊 Kart Geçişlerinde Otomatik Seslendir", autoPlay) {
                                    viewModel.setAutoPlayOnCardChange(it)
                                }
                                SettingSwitchRow("✨ Arapça Metinleri Harekeli Göster", showTashkeel) {
                                    viewModel.setGlobalShowTashkeel(it)
                                }
                                SettingSwitchRow("🙈 Sınav Modu (Arapça Metni Gizle)", testModeHideAr) {
                                    viewModel.setTestModeHideArabic(it)
                                }
                                SettingSwitchRow("📳 Titreşim Geri Bildirimi", hapticFeedback) {
                                    viewModel.setHapticFeedbackEnabled(it)
                                }
                            }
                        }
                    }

                    // 3. BÖLÜM: 🎯 ÇALIŞMA & İLERLEME
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "🎯 Günlük Çalışma Hedefi ($dailyGoal Kart):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(10, 15, 20, 30, 50).forEach { goalVal ->
                                        val isSel = dailyGoal == goalVal
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) EmeraldGreen else palette.btnSec,
                                            border = BorderStroke(1.dp, if (isSel) EmeraldGreen else palette.border),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.setDailyGoal(goalVal) }
                                        ) {
                                            Text(
                                                text = "$goalVal Kart",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color(0xFF0F172A) else palette.textMain,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "🎙️ Kart Başına Deneme Hakkı:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("1 Kez", "2 Kez", "3 Kez", "5 Kez", "Sınırsız").forEach { m ->
                                        val isSel = maxAttempts == m
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) RoyalBlue else palette.btnSec,
                                            border = BorderStroke(1.dp, if (isSel) RoyalBlue else palette.border),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.setMaxAttemptsSetting(m) }
                                        ) {
                                            Text(
                                                text = m,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else palette.textMain,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }

                                SettingSwitchRow("⏩ Doğru Bilince Otomatik İlerle", autoAdvance) {
                                    viewModel.setAutoAdvanceOnCorrect(it)
                                }
                                SettingSwitchRow("🙌 Eller Serbest Mod (Otomatik Kayıt)", handsFree) {
                                    viewModel.setHandsFreeAutoStart(it)
                                }
                                SettingSwitchRow("🎲 Kartları Rastgele Sırala", randomOrder) {
                                    viewModel.setRandomOrder(it)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "🧹 İlerleme Sıfırlama ve Bakım Araçları",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )

                                Button(
                                    onClick = {
                                        scope.launch {
                                            viewModel.repository.resetStatsForMode("words")
                                            viewModel.repository.resetStatsForMode("sentences")
                                            viewModel.repository.resetStatsForMode("dialogue")
                                            viewModel.showToast("🔄 Tüm çalışma istatistikleri ve SM-2 verileri sıfırlandı.")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🔄 Tüm İstatistikleri ve Ezber Durumunu Sıfırla", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        val deleted = viewModel.audioManager.cleanupRecordings(0)
                                        recStats = viewModel.audioManager.getRecordingsStats()
                                        viewModel.showToast("🧹 $deleted ses kaydı temizlendi.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🗑️ Ses Kayıtlarını Temizle (${recStats.first} dosya, ${recStats.second} MB)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val fixed = viewModel.repository.repairAllDatabases()
                                            viewModel.showToast("🪄 Metin Doktoru: $fixed kayıt onarıldı.")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🪄 Veritabanı Metin Doktorunu Çalıştır", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveStudentName(editedName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("✅ Kaydet ve Kapat", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalMuallimunPalette.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = palette.textMain,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// =============================================================================
// 4. 📖 MOBİL KULLANIM KILAVUZU (InteractiveGuideModal / UserGuideModal)
// =============================================================================
@Composable
fun InteractiveGuideModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    UserGuideModal(viewModel = viewModel, onDismiss = onDismiss)
}

@Composable
fun UserGuideModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current
    var selectedGuideTab by remember { mutableIntStateOf(0) }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📖 Kullanım Kılavuzu",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.openModal(ActiveModal.ABOUT_INFO) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("ℹ️ Hakkında", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { viewModel.openModal(ActiveModal.ONBOARDING_TOUR) },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("🔄 7 Adımlı Tur", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                }
            }

            // Kompakt Sekmeler: Modlar | Ses & Mahreç | Paketler
            TabRow(
                selectedTabIndex = selectedGuideTab,
                containerColor = palette.panelColor,
                contentColor = palette.accentHead
            ) {
                Tab(
                    selected = selectedGuideTab == 0,
                    onClick = { selectedGuideTab = 0 },
                    text = { Text("📱 Modlar", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                Tab(
                    selected = selectedGuideTab == 1,
                    onClick = { selectedGuideTab = 1 },
                    text = { Text("🎤 Ses & Mahreç", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                Tab(
                    selected = selectedGuideTab == 2,
                    onClick = { selectedGuideTab = 2 },
                    text = { Text("📥 Paketler", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedGuideTab) {
                    0 -> {
                        GuideSectionCard(
                            title = "🧭 1. Ekran Geçişleri ve Araç Çubukları",
                            body = "• Alt Menü: Ekranın altındaki 4 sekme (🏠 Ana Sayfa, 📖 Kelime, 💬 Diyalog, 📝 Cümle) arasında tek dokunuşla geçiş yapın.\n" +
                                "• Üst Araçlar (🔧): Çalışma ekranlarında sağ üstteki '🔧' butonuna dokunarak Kök Sözlüğü, Dinle & Bul ve Karne ekranlarını istediğiniz an açın.\n" +
                                "• Görünüm & Filtre (⚙️ Filtre ▼): Kelime ve Cümle ekranlarındaki '⚙️ Filtre ▼' butonundan Arapça gizleme, hareke, anlam, yazı boyutu (A-/A+) ve durum filtrelerini yönetin."
                        )
                        GuideSectionCard(
                            title = "🌿 2. Sarf Kök Sözlüğü ve 6'lı Petek Görünümü",
                            body = "• Kelime kartlarındaki 3 harfli kök rozetine veya '📖 Sarf Kök Sözlüğü' kartına dokunun.\n• Kelimenin sülâsî kökünü, veznini, Tekil/Çoğul veya Mâzî/Muzâri/Masdar çekimlerini ve aynı kökten türeyen kelime ailesini 6'lı petek görünümünde sesli inceleyin."
                        )
                        GuideSectionCard(
                            title = "🎭 3. Diyalog Öğrenme ve Canlandırma (Roleplay)",
                            body = "• '📖 Öğrenme Modu'nda diyaloğu satır satır veya tümüyle dinleyin.\n• '🎭 Canlandırma (Roleplay)' modunda bir konuşmacı rolü seçin; karşı rolü uygulama seslendirsin, sıra size geldiğinde mikrofona konuşarak diyaloğu tamamlayın."
                        )
                    }
                    1 -> {
                        GuideSectionCard(
                            title = "🎤 1. Mikrofona Konuşma ve 📻 Oto-Dinle Modu",
                            body = "• Kartın altındaki '🎤 Mikrofon' butonuna bir kez dokunun; kart çerçevesi yeşil parladığında kelimenizi veya cümlenizi net bir sesle okuyun.\n• '📻 Oto-Dinle' moduyla yolda veya kulaklıkla çalışırken kartların Arapçasını ve Türkçe anlamını sırayla otomatik dinleyebilirsiniz.\n• '🗣️ Gölgele' ile örnek sesi dinledikten hemen sonra peş peşe tekrar yapabilir, '🎧 Sesim' ile kendi kaydınızı dinleyebilirsiniz."
                        )
                        GuideSectionCard(
                            title = "🐢 2. Hatalı Kelimeye Dokunarak Yavaş Telaffuz Dinleme",
                            body = "• Okuma sonrasında doğru kelimeler yeşil, hatalı veya eksik kelimeler kırmızı renkle işaretlenir.\n• Cümle veya diyalog içinde kırmızı (hatalı) görünen herhangi bir Arapça kelimenin üzerine doğrudan dokunarak o kelimenin yavaşlatılmış doğru telaffuzunu anında dinleyebilirsiniz."
                        )
                        GuideSectionCard(
                            title = "🔬 3. Canlı Mahreç ve Vezin Uyarıları",
                            body = "• Boğaz harfleri (ع-ح-خ), peltek harfler (ث-ذ-ظ), kalın-ince harf ayrımları (ص-س, ط-ت, ض-د, ق-ك) ve med (uzatma) hataları kartın altında detaylı açıklamayla gösterilir."
                        )
                    }
                    2 -> {
                        GuideSectionCard(
                            title = "📥 1. Kolay Paket ve Veritabanı (.db / .zip / .json / .csv) Yükleme",
                            body = "• Ana Sayfadaki '🛠️ Yönetim' butonuna dokunarak İçerik ve Paket Yönetimi ekranını açın.\n• Telefonunuzdaki '.db', '.zip', '.json' veya '.csv' uzantılı kelime, cümle ve diyalog paketlerini tek dokunuşla seçerek uygulamaya aktarın."
                        )
                        GuideSectionCard(
                            title = "📋 2. Panodan Hızlı Kelime / Cümle Ekleme ve Medya Eşleştirme",
                            body = "• Yeni bir kategori adı belirleyip kelime, cümle veya diyaloglarınızı doğrudan form üzerinden ya da panodan toplu yapıştırarak ekleyebilirsiniz.\n• Dilerseniz eklediğiniz kelime ve cümlelere telefonunuzdan görsel (.png/.jpg) ve ses (.mp3/.wav) dosyası bağlayabilirsiniz."
                        )
                    }
                }

                Button(
                    onClick = { openUrl("https://arapca.muallimun.net/arapca-telaffuz-asistani/") },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "🌐 Web Tanıtım & Kılavuz Sayfası",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("✅ Anladım, Kapat", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GuideSectionCard(title: String, body: String) {
    val palette = LocalMuallimunPalette.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, fontSize = 12.sp, color = palette.textMain, lineHeight = 18.sp)
        }
    }
}

// =============================================================================
// 4B. ℹ️ HAKKINDA PENCERESİ (AboutAppModal / AboutInfoModal)
// =============================================================================
@Composable
fun AboutAppModal(
    viewModel: MainAppViewModel? = null,
    onDismiss: () -> Unit
) {
    AboutInfoModal(viewModel = viewModel, onDismiss = onDismiss)
}

@Composable
fun AboutInfoModal(
    viewModel: MainAppViewModel? = null,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var updateResultMessage by remember { mutableStateOf<String?>(null) }
    var updateStoreUrl by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
            }
        }
    }

    fun checkForUpdates() {
        isCheckingUpdate = true
        if (viewModel != null) {
            viewModel.checkForUpdates(currentVersionName = "1.0.0") { hasUpdate, _, message, storeUrl ->
                isCheckingUpdate = false
                updateResultMessage = message
                updateStoreUrl = if (hasUpdate) storeUrl else null
            }
        } else {
            isCheckingUpdate = false
            updateResultMessage = "✅ Uygulamanız güncel! En son sürümü (Mobil Sürüm v1.0.0) kullanıyorsunuz."
            updateStoreUrl = null
        }
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            containerColor = palette.cardColor,
            title = {
                Text(
                    text = "🔒 Gizlilik Politikası & Veri Güvenliği",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead
                )
            },
            text = {
                Text(
                    text = "Ses verileriniz yalnızca anlık telaffuz analizi için cihaz içinde işlenir, hiçbir sunucuya kaydedilmez veya üçüncü taraflarla paylaşılmaz.",
                    fontSize = 13.sp,
                    color = palette.textMain,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyPolicyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Anladım", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    updateResultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateResultMessage = null },
            containerColor = palette.cardColor,
            title = {
                Text(
                    text = "🔄 Sürüm & Güncelleme Kontrolü",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    color = palette.textMain,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    updateStoreUrl?.let { url ->
                        Button(
                            onClick = {
                                updateResultMessage = null
                                openUrl(url)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🌐 Güncelle", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { updateResultMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Tamam", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .testTag("modal_about_info")
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    // 1. Üst Başlık (Hero Header) Alanı
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Arapça Telaffuz Asistanı Simgesi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )

                    Text(
                        text = "Arapça Telaffuz Asistanı",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accentHead,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = RoyalBlue.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "✨ Mobil Sürüm v1.0.0 • Yapay Zekâ Destekli",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoyalBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    // 2. İçerik Özeti ve 2x2 Özellik Rozetleri (Capabilities Grid)
                    Text(
                        text = "Yapay zekâ destekli ses analizi ve SM-2 aralıklı tekrar algoritmasıyla geliştirilmiş interaktif Arapça eğitim ve mahreç koçu.",
                        fontSize = 12.sp,
                        color = palette.textMain,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AboutCapabilityBadge(
                                label = "🎙️ Canlı Mahreç Koçu",
                                modifier = Modifier.weight(1f)
                            )
                            AboutCapabilityBadge(
                                label = "🏛️ Sarf & Kök Atölyesi",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AboutCapabilityBadge(
                                label = "🎭 Diyalog & Rol Yapma",
                                modifier = Modifier.weight(1f)
                            )
                            AboutCapabilityBadge(
                                label = "🎧 Dinle & Bul (İstimâ)",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Uyumlu ve Kurumsal Aksiyon Butonları
                    // Ana Buton (Tam Genişlik)
                    Button(
                        onClick = { openUrl("https://arapca.muallimun.net/arapca-telaffuz-asistani/") },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("btn_about_web_guide")
                    ) {
                        Text(
                            text = "🌐 Web Tanıtım & Kılavuz Sayfası",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // İkili Yardımcı Satır 1 (Yan Yana 2 Eşit Buton - spacedBy(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel?.openModal(ActiveModal.USER_GUIDE)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.panelColor,
                                contentColor = TealAccent
                            ),
                            border = BorderStroke(1.dp, TealAccent),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_about_user_guide")
                        ) {
                            Text(
                                text = "📖 Kılavuz",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealAccent,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                viewModel?.openModal(ActiveModal.ONBOARDING_TOUR)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.panelColor,
                                contentColor = RoyalPurple
                            ),
                            border = BorderStroke(1.dp, RoyalPurple),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_about_onboarding_tour")
                        ) {
                            Text(
                                text = "🚀 Tanıtım Turu",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalPurple,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // İkili Sistem Satırı 2 (Yan Yana 2 Eşit Kompakt Buton - spacedBy(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showPrivacyPolicyDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.panelColor,
                                contentColor = palette.textMain
                            ),
                            border = BorderStroke(1.dp, palette.border),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("btn_privacy_policy")
                        ) {
                            Text(
                                text = "🔒 Gizlilik & Veri",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { checkForUpdates() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.panelColor,
                                contentColor = palette.textMain
                            ),
                            border = BorderStroke(1.dp, palette.border),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("btn_check_updates")
                        ) {
                            Text(
                                text = if (isCheckingUpdate) "⏳ Kontrol..." else "🔄 Güncelleme",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 4. Alt Kurumsal Marka Kartı (muallimun_logo.png) ve www.muallimun.net Bağlantısı
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.panelColor,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openUrl("https://www.muallimun.net") }
                            .testTag("card_muallimun_brand")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.muallimun_logo),
                                    contentDescription = "Muallimun.net Kurumsal Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = "Geliştirici & Dijital Eğitim Portalı",
                                fontSize = 11.sp,
                                color = palette.textDim,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "🌐 www.muallimun.net",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalBlue,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Sağ üst köşede zarif, yuvarlak kapat (✕) ikon butonu (En üst katmanda - Column'un üstünde)
                Surface(
                    shape = CircleShape,
                    color = palette.panelColor,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                        .testTag("btn_close_about_modal")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = palette.textMain,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCapabilityBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.bgColor.copy(alpha = 0.6f),
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textMain,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =============================================================================
// 5. 📊 DİYALOG CANLANDIRMA (ROLEPLAY) KONUŞMA KARNESİ MODALI
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleplayReportCardModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val roleName by viewModel.selectedRoleName.collectAsState()
    val lines by viewModel.currentDialogueLines.collectAsState()
    val finalResults by viewModel.roleplayFinalResults.collectAsState()

    val totalWords = finalResults.values.sumOf { it.size }
    val okWords = finalResults.values.sumOf { list -> list.count { it.second } }
    val scorePct = if (totalWords > 0) ((okWords.toDouble() / totalWords) * 100).roundToInt() else 100

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📊 KONUŞMA KARNESİ — Rol: $roleName",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead
                    )
                    Text(
                        text = "Başarı: %$scorePct",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.okGreen
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    finalResults.forEach { (lineIdx, wordList) ->
                        val line = lines.getOrNull(lineIdx) ?: return@forEach
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
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentCoral,
                                        modifier = Modifier.clickable {
                                            viewModel.audioManager.speakArabic(line.arapca, "male", false)
                                        }
                                    ) {
                                        Text(
                                            text = "🔊 Doğrusunu Dinle",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "${line.sira}. Cümle (${line.turkce})",
                                        fontSize = 11.sp,
                                        color = palette.textDim
                                    )
                                }

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        wordList.forEach { (wStr, isOk) ->
                                            Text(
                                                text = wStr,
                                                fontFamily = AmiriArabicFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = if (isOk) palette.okGreen else CoralRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val otherRoles = lines.map { it.konusmaci }.distinct().filter { it != roleName }
                    if (otherRoles.isNotEmpty()) {
                        val nextRole = otherRoles.first()
                        Button(
                            onClick = {
                                onDismiss()
                                viewModel.selectRoleName(nextRole)
                                viewModel.startRoleplaySession()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔄 '$nextRole' Rolüyle Canlandır", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✅ Kapat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
        }
    }
}
