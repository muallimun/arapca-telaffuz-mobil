package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AmiriArabicFontFamily
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TealAccent
import com.example.ui.theme.WarmGold
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.AppMode
import com.example.viewmodel.MainAppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDashboardScreen(
    viewModel: MainAppViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val studentName by viewModel.studentName.collectAsState()
    val streak by viewModel.streakDays.collectAsState()
    val dailyCount by viewModel.dailyCount.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val totalPoints by viewModel.totalPoints.collectAsState()
    val showTeacherBar by viewModel.showTeacherBar.collectAsState()
    val lastMode by viewModel.lastActiveModeName.collectAsState()

    val allWords by viewModel.allWords.collectAsState()
    val allSozluk by viewModel.allSozluk.collectAsState()
    val allDialogues by viewModel.allDialogues.collectAsState()
    val allDialogueLines by viewModel.allDialogueLines.collectAsState()
    val allSentences by viewModel.allSentences.collectAsState()

    val visualWordsCount = allWords.count { !it.paketId.startsWith("szl_") }
    val sozlukCount = allSozluk.size
    val dialoguesCount = allDialogues.size
    val linesCount = allDialogueLines.size
    val sentencesCount = allSentences.size

    val wrongWordsCount = allWords.count { it.yanlis > 0 }
    val smartTip = when {
        dailyCount == 0 -> "🎯 Bugünün Önerisi: Günlük serinizi başlatmak için '📅 Günün 15 Tekrarı' ile ısınma yapın."
        wrongWordsCount >= 3 -> "🎯 Akıllı Koç Önerisi: Zorlandığınız $wrongWordsCount kelime var; '🎯 Zayıflar' listesini tekrar edebilirsiniz."
        else -> "🎯 Akıllı Koç Önerisi: Kelime köklerini keşfedip '🎧 Dinle & Bul' sınavıyla bilginizi pekiştirin."
    }

    val lastModeLabel = when (lastMode) {
        "dialogue" -> "💬 Diyalog Modu"
        "sentences" -> "📜 Cümle Modu"
        else -> "🏠 Kelime Modu"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Üst Karşılama & Günlük Hedef Banner'ı
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.panelColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                palette.panelColor,
                                if (palette.isDark) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFFDBEAFE)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "أَهْلاً وَسَهْلاً بِكُمْ فِي مُخْتَبَرِ النُّطْقِ وَالْمُحَادَثَةِ",
                        fontFamily = AmiriArabicFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = palette.textArabic,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "👋 Hoş geldin $studentName! Bugün hangi modda Arapça konuşma pratiği yapmak istersin?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textMain
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.cardColor,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openModal(ActiveModal.STUDENT_NAME) }
                            .testTag("student_profile_badge")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "👤 $studentName ✏️",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Text(
                                    text = viewModel.getLevelTitle(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.okGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "🔥 Seri: $streak Gün  |  🎯 Bugün: $dailyCount/$dailyGoal  |  ⭐ Puan: $totalPoints",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textDim
                            )
                        }
                    }
                }
            }
        }

        // 2. "📍 Kaldığınız Yerden Devam Edin" + "🎯 Akıllı Öneri" Şeridi
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📍 Son Çalışılan: $lastModeLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead
                )
                Text(
                    text = smartTip,
                    fontSize = 12.sp,
                    color = palette.textMain
                )
                Button(
                    onClick = { viewModel.resumeLastSession() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resume_session_button")
                ) {
                    Text(
                        text = "▶️ Kaldığım Yerden Devam Et",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 3. Öğrenci Odaklı 4 Ana Modül Kartı (2x2 Mobil Uyumlu Izgara)
        DashboardModuleCard(
            icon = "🏠",
            title = "1. Görselli Kelime Öğren & Konuş",
            statText = "🖼️ $visualWordsCount Görselli Kelime Kartı  •  🎯 Canlı Mahreç Koçu",
            description = "Yeni başlayanlar için görsel destekli kelimeler, kadın/erkek telaffuzu, canlı mahreç koçu ve gölgeleme antrenmanı.",
            primaryBtnText = "🚀 Hemen Kelime Öğrenmeye Başla",
            primaryBtnColor = RoyalBlue,
            primaryBtnContentColor = Color.White,
            onPrimaryClick = {
                val firstVisualPkg = viewModel.wordPackages.value.firstOrNull {
                    !it.id.startsWith("szl_") && it.id != "sozluk_tumu"
                }?.id ?: "hayvanlar"
                viewModel.selectWordCategory(firstVisualPkg)
                viewModel.switchMode(AppMode.WORDS)
            },
            secondaryBtnText = "📅 Günün 15 Akıllı Tekrarı (SM-2)",
            onSecondaryClick = {
                viewModel.selectWordCategory("gunluk_tekrar")
                viewModel.switchMode(AppMode.WORDS)
            },
            testTagPrefix = "module_words"
        )

        DashboardModuleCard(
            icon = "📖",
            title = "2. Sarf Sözlüğü & Kelime Kökleri",
            statText = "🌿 $sozlukCount Sözlük Maddesi  •  🏷️ 6 Tematik Kategori",
            description = "Kelimelerin 3 harfli köklerini (Sülâsî), tekil/çoğul (Müfret/Cemî) ve geçmiş/şimdiki zaman (Mâzî/Muzâri) çekimlerini öğrenin.",
            primaryBtnText = "🎤 Sözlük Kartlarıyla Telaffuz Çalış",
            primaryBtnColor = TealAccent,
            primaryBtnContentColor = Color.White,
            onPrimaryClick = {
                viewModel.selectWordCategory("sozluk_tumu")
                viewModel.switchMode(AppMode.WORDS)
            },
            secondaryBtnText = "🔍 Kök Ailesi Keşif Atölyesini Aç",
            onSecondaryClick = { viewModel.openModal(ActiveModal.SARF_EXPLORER) },
            testTagPrefix = "module_sarf"
        )

        DashboardModuleCard(
            icon = "💬",
            title = "3. Günlük Diyaloglar & Rol Yapma",
            statText = "🎭 $dialoguesCount Günlük Diyalog  •  💬 $linesCount Konuşma Satırı",
            description = "Günlük hayat diyaloglarını dinleyin; ardından bir rol seçip uygulama ile karşılıklı Arapça konuşma canlandırması yapın.",
            primaryBtnText = "💬 Diyalogları Dinle ve Çalış",
            primaryBtnColor = AccentCoral,
            primaryBtnContentColor = Color.White,
            onPrimaryClick = {
                viewModel.switchDialogueSubmode("learn")
                viewModel.switchMode(AppMode.DIALOGUE)
            },
            secondaryBtnText = "🎭 Karşılıklı Canlandırma (Roleplay)",
            onSecondaryClick = {
                viewModel.switchDialogueSubmode("roleplay")
                viewModel.switchMode(AppMode.DIALOGUE)
            },
            testTagPrefix = "module_dialogue"
        )

        DashboardModuleCard(
            icon = "📜",
            title = "4. Anahtar Cümleler & Sesli Sınav",
            statText = "📜 $sentencesCount Cümle (A1-C1)  •  🎧 4 Şıklı Dinleme Sınavı",
            description = "Kelime kelime renkli doğruluk analiziyle cümle telaffuzu çalışın veya 10/20/50 soruluk Dinle & Bul sınavına girin.",
            primaryBtnText = "📜 Anahtar Cümle Moduna Başla",
            primaryBtnColor = EmeraldGreen,
            primaryBtnContentColor = Color(0xFF0F172A),
            onPrimaryClick = { viewModel.switchMode(AppMode.SENTENCES) },
            secondaryBtnText = "🎧 Dinle & Bul Sesli Sınav Merkezi",
            onSecondaryClick = { viewModel.openModal(ActiveModal.LISTENING_EXAM) },
            testTagPrefix = "module_sentences"
        )

        // 4. Alt Şerit: 👨‍🏫 Öğretmen, Yazılı Sınav, Ders Notu ve Yönetim Araçları (Ayarlardan Gizlenebilir)
        if (showTeacherBar) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.panelColor),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("teacher_tools_bar")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "👨‍🏫 Öğretmen, Yazılı Sınav, Ders Notu & Yönetim Araçları:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.openModal(ActiveModal.WORKSHEET_GENERATOR) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmGold,
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_worksheet_btn")
                        ) {
                            Text(
                                text = "🖨️ A4 Yazılı Sınav & Ders Notu Üret",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.openModal(ActiveModal.ANALYTICS_REPORTS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_reports_btn")
                        ) {
                            Text(
                                text = "📊 Gelişim Karnesi & Rozetler",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.openModal(ActiveModal.ADMIN_PANEL) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.btnSec,
                                contentColor = palette.textMain
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_admin_btn")
                        ) {
                            Text(
                                text = "🛠️ Yönetim & Excel Paket Paneli",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardModuleCard(
    icon: String,
    title: String,
    statText: String,
    description: String,
    primaryBtnText: String,
    primaryBtnColor: Color,
    primaryBtnContentColor: Color,
    onPrimaryClick: () -> Unit,
    secondaryBtnText: String,
    onSecondaryClick: () -> Unit,
    testTagPrefix: String
) {
    val palette = LocalMuallimunPalette.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardColor),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon,
                    fontSize = 30.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead
                    )
                    Text(
                        text = statText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.okGreen
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                color = palette.textDim
            )

            Button(
                onClick = onPrimaryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryBtnColor,
                    contentColor = primaryBtnContentColor
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_primary_btn")
            ) {
                Text(
                    text = primaryBtnText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = onSecondaryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.btnSec,
                    contentColor = palette.textMain
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_secondary_btn")
            ) {
                Text(
                    text = secondaryBtnText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
