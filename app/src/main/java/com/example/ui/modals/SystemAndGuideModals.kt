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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.finishOnboardingTour(dontShowAgain) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.panelColor, contentColor = palette.textDim),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_skip_tour")
                    ) {
                        Text("⏭️ Turu Atla", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    if (curStep > 0) {
                        Button(
                            onClick = { curStep-- },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("◀ Önceki", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_next_tour")
                    ) {
                        Text(
                            text = if (curStep == steps.size - 1) "🚀 Hemen Başla!" else "Sonraki Adım ▶",
                            fontSize = 13.sp,
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
@Composable
fun SettingsModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val scope = rememberCoroutineScope()

    val studentName by viewModel.studentName.collectAsState()
    val showTeacherBar by viewModel.showTeacherBar.collectAsState()
    val showDemoSim by viewModel.showDemoSimulationButtons.collectAsState()
    val slowTts by viewModel.slowTts.collectAsState()
    val autoPlay by viewModel.autoPlayOnCardChange.collectAsState()
    val autoAdvance by viewModel.autoAdvanceOnCorrect.collectAsState()
    val handsFree by viewModel.handsFreeAutoStart.collectAsState()
    val randomOrder by viewModel.randomOrder.collectAsState()
    val testModeHideAr by viewModel.testModeHideArabic.collectAsState()
    val voicePref by viewModel.voicePreference.collectAsState()
    val maxAttempts by viewModel.maxAttemptsSetting.collectAsState()
    val isDark by viewModel.isDarkTheme.collectAsState()

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
                        text = "⚙️ UYGULAMA VE EĞİTİM AYARLARI (v40.0)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
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
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("🏠 Genel", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("🔊 Ses & TTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("🛠️ Bakım", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) })
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    when (selectedTab) {
                        0 -> {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        label = { Text("👤 Öğrenci / Kullanıcı Adı Soyadı") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    SettingSwitchRow("⏩ Doğru Bilince Otomatik İlerle", autoAdvance) { viewModel.setAutoAdvanceOnCorrect(it) }
                                    SettingSwitchRow("🙌 Eller Serbest Mod (Otomatik Kayıt)", handsFree) { viewModel.setHandsFreeAutoStart(it) }
                                    SettingSwitchRow("🎲 Kartları Rastgele Sırala", randomOrder) { viewModel.setRandomOrder(it) }
                                    SettingSwitchRow("🙈 Sınav Modu (Arapçayı Gizle)", testModeHideAr) { viewModel.setTestModeHideArabic(it) }
                                    SettingSwitchRow("👨‍🏫 Ana Ekranda Öğretmen Barını Göster", showTeacherBar) { viewModel.setShowTeacherBar(it) }
                                    SettingSwitchRow("🧪 Demo / Simülasyon Butonlarını Göster", showDemoSim) { viewModel.setShowDemoSimulationButtons(it) }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Tema: ${if (isDark) "🌙 Karanlık Mod" else "☀️ Aydınlık Mod"}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.textMain,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { viewModel.toggleTheme() },
                                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "🔄 Temayı Değiştir",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🎙️ Seslendirme Tercihi:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
                                    listOf("Kadın", "Erkek", "Sırayla").forEach { pref ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { viewModel.setVoicePreference(pref) }
                                        ) {
                                            RadioButton(selected = voicePref == pref, onClick = { viewModel.setVoicePreference(pref) })
                                            Text("$pref Sesi", fontSize = 13.sp, color = palette.textMain)
                                        }
                                    }

                                    SettingSwitchRow("🐢 Yavaş Seslendirme (TTS %25 Yavaş)", slowTts) { viewModel.setSlowTts(it) }
                                    SettingSwitchRow("🔊 Kart Geçişlerinde Otomatik Seslendir", autoPlay) { viewModel.setAutoPlayOnCardChange(it) }

                                    Text("🎯 Kart Başına Deneme Hakkı:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("1 Kez", "2 Kez", "3 Kez", "5 Kez", "Sınırsız").forEach { m ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (maxAttempts == m) RoyalBlue else palette.btnSec,
                                                modifier = Modifier.clickable { viewModel.setMaxAttemptsSetting(m) }
                                            ) {
                                                Text(
                                                    text = m,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (maxAttempts == m) Color.White else palette.textMain,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                                    Text("🧹 Ses Kayıtları ve Veritabanı Bakımı", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
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
                                        Text("🗑️ Ses Kayıtlarını Temizle (${recStats.first} dosya, ${recStats.second} MB)", fontWeight = FontWeight.Bold)
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
                                        Text("🪄 Veritabanı Metin Doktorunu Çalıştır", fontWeight = FontWeight.Bold)
                                    }

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
                                        Text("🔄 Tüm İstatistikleri ve SM-2 Verilerini Sıfırla", fontWeight = FontWeight.Bold)
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
                    Text("✅ AYARLARI UYGULA VE KAPAT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    text = "📖 MOBİL KULLANIM KILAVUZU",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.openModal(ActiveModal.ABOUT_INFO) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("ℹ️ Hakkında", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { viewModel.openModal(ActiveModal.ONBOARDING_TOUR) },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("🔄 7 Adımlı Tur", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                }
            }

            // Kompakt Sekmeler: Menü & Gezinme | Mikrofon & Mahreç | Paket & İçerik
            TabRow(
                selectedTabIndex = selectedGuideTab,
                containerColor = palette.panelColor,
                contentColor = palette.accentHead
            ) {
                Tab(
                    selected = selectedGuideTab == 0,
                    onClick = { selectedGuideTab = 0 },
                    text = { Text("📱 Menü & Modlar", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                Tab(
                    selected = selectedGuideTab == 1,
                    onClick = { selectedGuideTab = 1 },
                    text = { Text("🎤 Ses & Mahreç", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                Tab(
                    selected = selectedGuideTab == 2,
                    onClick = { selectedGuideTab = 2 },
                    text = { Text("📥 Paket Yükleme", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
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
                            title = "🧭 1. Alt ve Üst Menü Kullanımı",
                            body = "• Alt Gezinme Çubuğu: Ekranın altındaki 4 sekme (🏠 Ana Sayfa, 🔤 Kelime, 💬 Diyalog, 📝 Cümle) arasında tek dokunuşla geçiş yapın.\n• Üst Hızlı Araçlar: '📖 Sözlük' ile Sarf Kök Sözlüğünü, '🎧 Sınav' ile Dinle & Bul testini, '📊 Karne' ile gelişim istatistiklerinizi açın.\n• ⚙️ Filtre Butonu: Kelime ve Cümle ekranlarında '🙈 Gizle', '✨ Hareke', '🇹🇷 Anlam', punto (A-/A+) ve '📅 Günlük 15' filtrelerini açıp kapatın."
                        )
                        GuideSectionCard(
                            title = "🌿 2. Sarf Kök Sözlüğü ve 6'lı Petek Görünümü",
                            body = "• Kelime kartlarındaki 3 harfli kök rozetine veya üst menüdeki '📖 Sözlük' butonuna dokunun.\n• Kelimenin sülâsî kökünü, veznini, Tekil/Çoğul veya Mâzî/Muzâri/Masdar çekimlerini ve aynı kökten türeyen kelime ailesini 6'lı petek görünümünde sesli inceleyin."
                        )
                        GuideSectionCard(
                            title = "🎭 3. Diyalog Öğrenme ve Canlandırma (Roleplay)",
                            body = "• '📖 Öğrenme Modu'nda diyaloğu satır satır veya tümüyle dinleyin.\n• '🎭 Canlandırma (Roleplay)' modunda bir konuşmacı rolü seçin; karşı rolü uygulama seslendirsin, sıra size geldiğinde mikrofona konuşarak diyaloğu tamamlayın."
                        )
                    }
                    1 -> {
                        GuideSectionCard(
                            title = "🎤 1. Mikrofona Konuşma İpuçları",
                            body = "• Kartın altındaki '🎤 Mikrofon' butonuna bir kez dokunun; kart çerçevesi yeşil parladığında kelimenizi veya cümlenizi net bir sesle okuyun.\n• Konuşmanız bittiğinde sistem sesi otomatik değerlendirir ve doğruluk puanınızı verir.\n• '🗣️ Gölgele' ile örnek sesi dinledikten hemen sonra peş peşe tekrar yapabilir, '🎧 Sesim' ile kendi kaydınızı dinleyebilirsiniz."
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
                            title = "📥 1. Kolay Paket ve Veritabanı (.db / .json / .csv) Yükleme",
                            body = "• Ana Sayfadaki '🛠️ İçerik Ekle' (veya Öğretmen / İçerik Yönetimi) ekranını açın.\n• Telefonunuzdaki '.db', '.json' veya '.csv' uzantılı kelime, cümle ve diyalog paketlerini tek dokunuşla seçerek uygulamaya aktarın."
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
// 4B. ℹ️ HAKKINDA PENCERESİ (AboutAppModal / AboutInfoModal - resim_7.jpg)
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

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardColor,
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ℹ️ Arapça Telaffuz Asistanı",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accentHead,
                    maxLines = 1
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RoyalBlue.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, RoyalBlue)
                ) {
                    Text(
                        text = "Mobil Sürüm v40.0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Muallimun.Net tarafından geliştirilen Yapay Zekâ Destekli Mahreç ve Telaffuz Koçu, Sarf Kök Sözlüğü, Diyalog Canlandırma (Roleplay) ve Dinle & Bul Mobil Eğitim Platformu.",
                    fontSize = 13.sp,
                    color = palette.textMain,
                    lineHeight = 19.sp
                )

                // Tek ana dış bağlantı butonu
                Button(
                    onClick = { openUrl("https://arapca.muallimun.net/arapca-telaffuz-asistani/") },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "🌐 Web Tanıtım & Kılavuz Sayfası",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                // Yan yana iki kompakt uygulama içi yardımcı buton
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel?.openModal(ActiveModal.USER_GUIDE)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = "📖 Mobil Kullanım Kılavuzu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            viewModel?.openModal(ActiveModal.ONBOARDING_TOUR)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = "🔄 7 Adımlı Tanıtım Turu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "Geliştirici & Eğitim Portalı: www.muallimun.com",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead,
                    modifier = Modifier.clickable { openUrl("https://www.muallimun.com") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Kapat", fontWeight = FontWeight.Bold)
            }
        }
    )
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
