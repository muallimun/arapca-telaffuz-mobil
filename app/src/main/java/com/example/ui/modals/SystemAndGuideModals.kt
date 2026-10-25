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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                badge = "ADIM 1 / 7 — UYGULAMANIN AMACI NEDİR?",
                icon = "🎙️  🕌  🤖",
                title = "Arapçayı Sadece Okuyarak Değil, Sesli Konuşarak Öğrenin!",
                description = "Bu uygulamanın temel amacı; Arapça öğrenenlerin en çok zorlandığı 'doğru telaffuz etme (mahreç)', 'kelime köklerini tanıma (sarf)' ve 'akıcı konuşma (muhâdese)' becerilerini yapay zekâ destekli fonetik koçla geliştirmektir.",
                tip = "💡 Alt gezinme çubuğundan ve üst menüden istediğiniz çalışma ekranına her an geçiş yapabilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 2 / 7 — 🏛️ ANA SAYFA EKRANINDA NE YAPABİLİRSİNİZ?",
                icon = "🏛️  📍  🔥",
                title = "Başlangıç Merkezini Kullanma ve Kaldığınız Yerden Devam Etme",
                description = "Uygulama açıldığında karşınıza gelen '🏛️ Ana Sayfa' sizin çalışma merkezinizdir. Adınızı, günlük çalışma serinizi (🔥 Streak) ve puanınızı takip edebilir; '▶️ Kaldığım Yerden Devam Et' butonuyla son çalıştığınız ekrana tek dokunuşla dönebilirsiniz.",
                tip = "💡 Sağ üstteki ☀️/🌙 butonuyla Aydınlık veya Karanlık temayı seçebilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 3 / 7 — 🏠 KELİME MODU EKRANINDA NE YAPABİLİRSİNİZ?",
                icon = "🏠  🔊  🎤",
                title = "Kelimeleri Dinleyin, Mikrofona Okuyun ve Gölgeleme Yapın",
                description = "1) '🔊 Kadın / Erkek' butonlarıyla kelimenin doğru telaffuzunu dinleyin.\n2) '🎤 BAŞLA VE KONUŞ' butonuna basıp yeşil çerçeve parladığında mikrofona okuyun.\n3) '🗣️ Gölgele' ile sesi duyduktan hemen sonra aynı ritimle tekrar etme antrenmanı yapın.\n4) '📅 Günün 15 Tekrarı' ile SM-2 akıllı tekrar yapın.",
                tip = "💡 Emülatörde veya sessiz ortamda test için '🎯 Örnek Doğru Okuma' ve '🔬 Mahreç Hatası Test Et' butonlarını da kullanabilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 4 / 7 — 📖 SARF SÖZLÜĞÜ EKRANINDA NE YAPABİLİRSİNİZ?",
                icon = "📖  🌿  🔍",
                title = "Kelimelerin 3 Harfli Köklerini ve Türev Ailelerini Keşfedin",
                description = "Arapça kelimelerin hangi 3 asli harften (Sülâsî Kök) türediğini öğrenin. İsimlerin Tekil/Çoğul (Müfret/Cemî), fiillerin ise Geçmiş/Şimdiki Zaman (Mâzî/Muzâri/Masdar) çekimlerini dinleyip aynı kökten türeyen akraba kelimeleri inceleyin.",
                tip = "💡 Kelime Modunda sözlük paketlerini seçtiğinizde kart üzerinde Altıgen Kök Rozeti açılır."
            ),
            OnboardingStep(
                badge = "ADIM 5 / 7 — 💬 DİYALOG VE 📜 CÜMLE EKRANLARINDA NE YAPABİLİRSİNİZ?",
                icon = "💬  🎭  📜",
                title = "Karşılıklı Rol Yaparak Konuşun ve Cümle Telaffuzu Çalışın",
                description = "• 💬 Diyalog Modu: '📖 Öğrenme Modu'nda günlük hayat diyaloglarını dinleyin. '🎭 Canlandırma (Roleplay)' modunda ise bir karakteri seçin; karşı rolü uygulama seslendirsin, sıra size geldiğinde mikrofona konuşarak diyaloğu tamamlayın.\n• 📜 Cümle Modu: Cümle içindeki doğru (yeşil) ve hatalı (kırmızı) kelimelerinizi anında görün.",
                tip = "💡 Canlandırma modunda Türkçe çeviri gizlenir; ihtiyaç duyarsanız '💡 Türkçe İpucu Göster' butonuna dokunabilirsiniz."
            ),
            OnboardingStep(
                badge = "ADIM 6 / 7 — 🎯 CANLI MAHREÇ KOÇU VE 🎧 DİNLE & BUL SINAVI",
                icon = "🎯  🎧  🏆",
                title = "Harf Hatalarınızı Düzeltin ve 4 Şıklı Dinleme Sınavı Çözün",
                description = "• 🎯 Canlı Mahreç & Hareke Koçu: Birbirine karıştırdığınız harfleri (ص-س, ذ-ز, ع-ء, ن-ر vb.), kök harf değişimlerini (نَحْلَة vs رحلة) veya vezin/uzatma hatalarını kırmızıyla gösterip ağız/boğazdaki doğru çıkış noktasını tarif eder.\n• 🎧 Dinle & Bul: 10, 20, 50 soruluk veya Sınırsız 4 şıklı sesli anlama sınavı çözün.",
                tip = "💡 Üst üste 3 hatalı denemede Koç otomatik olarak '🐢 Yavaş Seslendirme (%25)' modunu açar."
            ),
            OnboardingStep(
                badge = "ADIM 7 / 7 — 📊 RAPORLAR, 🖨️ SINAV/DERS NOTU VE 🛠️ YÖNETİM PANELİ",
                icon = "📊  🖨️  🛠️",
                title = "Gelişim Karnenizi İnceleyin, A4 Sınav/Ders Notu Üretin ve İçerik Ekleyin",
                description = "• 📊 Raporlar: Rozetlerinizi, ilerleme grafiklerinizi ve zorlandığınız kelimelerdeki ses kayıtlarınızı (.wav) dinleyin; öğretmeninize '📦 Ödev & Karne (.ZIP/CSV)' paylaşın.\n• 🖨️ Sınav & Ders Notu / 🛠️ Yönetim Paneli: A4 yazılı sınav veya okuma metni (PDF) çıktısı alın; Excel'den kopyala-yapıştır ile kendi ders içeriğinizi ekleyin.",
                tip = "💡 Şimdi '🚀 Hemen Başla!' butonuna dokunarak adınızı kaydedip ilk Arapça pratiğinize başlayabilirsiniz!"
            )
        )
    }

    val step = steps[curStep]

    Dialog(
        onDismissRequest = { viewModel.finishOnboardingTour(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.bgColor.copy(alpha = 0.96f),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .safeDrawingPadding()
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp)
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
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(text = step.icon, fontSize = 34.sp)
                        Text(
                            text = step.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentHead,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = step.description,
                            fontSize = 14.sp,
                            color = palette.textMain,
                            lineHeight = 21.sp
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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            modifier = Modifier.testTag("btn_skip_tour")
                        ) {
                            Text("⏭️ Turu Atla", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (curStep > 0) {
                            Button(
                                onClick = { curStep-- },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.btnSec, contentColor = palette.textMain),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("◀ Önceki", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                .testTag("btn_next_tour")
                        ) {
                            Text(
                                text = if (curStep == steps.size - 1) "🚀 Hemen Başla!" else "Sonraki Adım ▶",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.bgColor,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
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
// 4. 📖 KULLANIM KILAVUZU VE ℹ️ HAKKINDA / KILAVUZ MERKEZİ MODALLARI
// =============================================================================
@Composable
fun UserGuideModal(
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalMuallimunPalette.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.bgColor,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📖 KAPSAMLI KULLANIM KILAVUZU",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.openModal(ActiveModal.ONBOARDING_TOUR) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("👋 7 Adımlı Tur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = palette.textMain)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GuideSectionCard(
                        title = "🎯 1. Uygulamanın Amacı ve Eğitim Felsefesi",
                        body = "Muallimun.Net Arapça Telaffuz ve Konuşma Asistanı; İmam Hatip Liseleri, İlahiyat Fakülteleri, TÖMER ve bireysel Arapça öğrenenler için geliştirilmiş yapay zekâ destekli bir konuşma (المحادثة), sarf (kök/türev) ve doğru telaffuz (النطق الصحيح) laboratuvarıdır."
                    )
                    GuideSectionCard(
                        title = "🏠 2. Kelime Modu, Gölgeleme (Shadowing) ve SM-2 Tekrar",
                        body = "• '🎤 BAŞLA VE KONUŞ' butonuna bastığınızda kartın dış çerçevesi parlak yeşil renkte parlar.\n• '🗣️ Gölgele' ile referans ses dalga boyunu (mavi) ve kendi ses dalga boyunuzu (yeşil) karşılaştırarak ritim uyumu çalışabilirsiniz.\n• '📅 Günün 15 Tekrarı' bilimsel SM-2 aralıklı tekrar algoritmasıyla zorlandığınız kelimeleri seçer."
                    )
                    GuideSectionCard(
                        title = "🌿 3. Sarf Sözlüğü, Altıgen Kök Rozeti ve Türev Ağacı",
                        body = "Görselsiz sözlük kartlarında kelimenin 3 asli kök harfi (ك ت ب vb.) altıgen rozet içinde gösterilir. İsimlerde 'Müfret & Cemî', fiillerde 'Mâzî • Muzâri • Masdar' kutularına dokunarak her çekimi ayrı ayrı dinleyip telaffuz edebilirsiniz."
                    )
                    GuideSectionCard(
                        title = "🎯 4. Canlı Mahreç, Kök Harf ve Hareke Koçu",
                        body = "Birbirine yakın mahreçli harfleri (ص↔س, ط↔ت, ض↔د, ع↔ء, ح↔ه, ق↔ك, ذ↔ز, ث↔س, ن↔ر vb.), kök harf değişimlerini (نَحْلَة vs رحلة) ve vezin/med farklarını (كِتَاب vs كتب) yakalar ve hatalı kelimeyi kırmızıyla vurgular."
                    )
                    GuideSectionCard(
                        title = "🖨️ 5. A4 Yazılı Sınav, Karma Quiz ve Ders Notu (Handout) PDF",
                        body = "Ayrık havuzlu (Arapça➔Türkçe ve Türkçe➔Arapça bölümlerinde aynı sorunun tekrarlanmadığı) yazılı sınavlar veya sınavsız temiz 'Ders Notu / Okuma Metni' A4 PDF çıktıları üretebilirsiniz."
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Anladım, Kapat", fontWeight = FontWeight.Bold)
                }
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
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.accentHead)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, fontSize = 12.sp, color = palette.textMain, lineHeight = 19.sp)
        }
    }
}

@Composable
fun AboutInfoModal(
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
            Text(
                text = "ℹ️ Arapça Telaffuz Asistanı v40.0",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentHead
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Muallimun.Net tarafından geliştirilen Yapay Zekâ Destekli Mahreç, Sarf Sözlüğü, Diyalog Canlandırma ve Sınav Üretim Platformu (Android Mobil Sürümü).",
                    fontSize = 13.sp,
                    color = palette.textMain
                )

                Button(
                    onClick = { openUrl("https://www.youtube.com/watch?v=vqttBAoov00") },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶️ YouTube Tanıtım Videosunu İzle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { openUrl("https://arapca.muallimun.net/arapca-telaffuz-asistani/") },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🌐 Web Tanıtım & Kılavuz Sayfası", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { openUrl("https://arapca.muallimun.net/arapca-telaffuz-asistani/Arapca_Telaffuz_Asistani_Kullanim_Kilavuzu.pdf") },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📕 Resimli PDF Kullanım Kılavuzu (A4)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF0F172A))
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.bgColor,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
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
}
