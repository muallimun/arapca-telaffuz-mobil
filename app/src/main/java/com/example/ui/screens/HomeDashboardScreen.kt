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
import androidx.compose.ui.text.style.TextOverflow
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
        dailyCount == 0 -> "🎯 Öneri: Günlük serinizi başlatmak için '📅 Günün 15 Tekrarı' ile ısınma yapın."
        wrongWordsCount >= 3 -> "🎯 Koç Önerisi: Zorlandığınız $wrongWordsCount kelime var; '🎯 Zayıflar' listesini tekrar edin."
        else -> "🎯 Koç Önerisi: Kelime köklerini keşfedip '🎧 Dinle & Bul Sınavı' ile bilginizi pekiştirin."
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Üst Karşılama & Günlük Hedef Banner'ı (Ultra-Kompakt Mobil Düzen)
        Card(
            shape = RoundedCornerShape(12.dp),
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
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "أَهْلاً وَسَهْلاً بِكُمْ فِي مُخْتَبَرِ النُّطْقِ وَالْمُحَادَثَةِ",
                        fontFamily = AmiriArabicFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = palette.textArabic,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "👋 Hoş geldin $studentName! Bugün hangi modda Arapça konuşma pratiği yapmak istersin?",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textMain
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.cardColor,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openModal(ActiveModal.STUDENT_NAME) }
                            .testTag("student_profile_badge")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "👤 $studentName ✏️",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = viewModel.getLevelTitle(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.okGreen,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "🔥 Seri: $streak Gün  •  🎯 Bugün: $dailyCount/$dailyGoal  •  ⭐ Puan: $totalPoints",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. "📍 Kaldığınız Yerden Devam Edin" + "🎯 Akıllı Öneri" Şeridi
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "📍 Son Çalışılan: $lastModeLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentHead,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = smartTip,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
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
                        .height(38.dp)
                        .testTag("resume_session_button")
                ) {
                    Text(
                        text = "▶️ Kaldığım Yerden Devam Et",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Öğrenci Odaklı 4 Ana Modül Kartı (Tek Satır Kısa ve Net Butonlar)
        DashboardModuleCard(
            icon = "🏠",
            title = "1. Görselli Kelime Öğren & Konuş",
            statText = "🖼️ $visualWordsCount Görselli Kelime Kartı  •  🎯 Canlı Mahreç Koçu",
            description = "Yeni başlayanlar için görsel destekli kelimeler, kadın/erkek telaffuzu, canlı mahreç koçu ve gölgeleme antrenmanı.",
            primaryBtnText = "🚀 Kelimeye Başla",
            primaryBtnColor = RoyalBlue,
            primaryBtnContentColor = Color.White,
            onPrimaryClick = {
                val firstVisualPkg = viewModel.wordPackages.value.firstOrNull {
                    !it.id.startsWith("szl_") && it.id != "sozluk_tumu"
                }?.id ?: "hayvanlar"
                viewModel.selectWordCategory(firstVisualPkg)
                viewModel.switchMode(AppMode.WORDS)
            },
            secondaryBtnText = "📅 Günün 15 Tekrarı",
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
            primaryBtnText = "🌿 Sözlük Kartları",
            primaryBtnColor = TealAccent,
            primaryBtnContentColor = Color.White,
            onPrimaryClick = {
                viewModel.selectWordCategory("sozluk_tumu")
                viewModel.switchMode(AppMode.WORDS)
            },
            secondaryBtnText = "🔎 Kök Atölyesi",
            onSecondaryClick = { viewModel.openModal(ActiveModal.SARF_EXPLORER) },
            testTagPrefix = "module_sarf"
        )

        DashboardModuleCard(
            icon = "💬",
            title = "3. Günlük Diyaloglar & Rol Yapma",
            statText = "🎭 $dialoguesCount Günlük Diyalog  •  💬 $linesCount Konuşma Satırı",
            description = "Günlük hayat diyaloglarını dinleyin; ardından bir rol seçip uygulama ile karşılıklı Arapça konuşma canlandırması yapın.",
            primaryBtnText = "💬 Diyalog Dinle",
            primaryBtnColor = AccentCoral,
            primaryBtnContentColor = Color.White,
            onPrimaryClick = {
                viewModel.switchDialogueSubmode("learn")
                viewModel.switchMode(AppMode.DIALOGUE)
            },
            secondaryBtnText = "🎭 Rol Yap (Roleplay)",
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
            primaryBtnText = "📝 Cümle Çalış",
            primaryBtnColor = EmeraldGreen,
            primaryBtnContentColor = Color(0xFF0F172A),
            onPrimaryClick = { viewModel.switchMode(AppMode.SENTENCES) },
            secondaryBtnText = "🎧 Dinle & Bul Sınavı",
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
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "👨‍🏫 Öğretmen, Sınav & Yönetim Araçları:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.openModal(ActiveModal.WORKSHEET_GENERATOR) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmGold,
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_worksheet_btn")
                        ) {
                            Text(
                                text = "🖨️ Sınav & PDF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { viewModel.openModal(ActiveModal.ANALYTICS_REPORTS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_reports_btn")
                        ) {
                            Text(
                                text = "📊 Karne & Ödev",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { viewModel.openModal(ActiveModal.ADMIN_PANEL) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.btnSec,
                                contentColor = palette.textMain
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_admin_btn")
                        ) {
                            Text(
                                text = "🛠️ Yönetim",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardColor),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.okGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = description,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = palette.textDim
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onPrimaryClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBtnColor,
                        contentColor = primaryBtnContentColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("${testTagPrefix}_primary_btn")
                ) {
                    Text(
                        text = primaryBtnText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
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
                        .weight(1f)
                        .height(40.dp)
                        .testTag("${testTagPrefix}_secondary_btn")
                ) {
                    Text(
                        text = secondaryBtnText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
