package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TealAccent
import com.example.ui.theme.WarmGold
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.AppMode
import com.example.viewmodel.MainAppViewModel

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

    val allWords by viewModel.allWords.collectAsState()
    val allDialogues by viewModel.allDialogues.collectAsState()
    val allSozluk by viewModel.allSozluk.collectAsState()

    val visualWordsCount = allWords.count { !it.gorsel.isNullOrBlank() }.let { if (it > 0) it else 65 }
    val dialoguesCount = allDialogues.size.let { if (it > 0) it else 20 }
    val sozlukCount = allSozluk.size.let { if (it > 0) it else 210 }

    val rawLevelTitle = viewModel.getLevelTitle()
    val compactLevelBadge = when {
        rawLevelTitle.contains("Seviye 5") -> "💎 Seviye 5"
        rawLevelTitle.contains("Seviye 4") -> "🥇 Seviye 4"
        rawLevelTitle.contains("Seviye 3") -> "🥈 Seviye 3"
        rawLevelTitle.contains("Seviye 2") -> "🥉 Seviye 2"
        else -> "🌱 Seviye 1"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // BÖLÜM 1: Kompakt Öğrenci & Devam Kartı
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardColor),
            border = BorderStroke(1.5.dp, palette.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Üst Satır: 👤 Öğrenci ✏️, kısayollar (🖨️ Sınav, 🛠️ Yönetim) ve sağda seviye rozeti (🌱 Seviye 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon),
                            contentDescription = "Uygulama Simgesi",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.panelColor,
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .clickable { viewModel.openModal(ActiveModal.STUDENT_NAME) }
                                .testTag("student_profile_badge")
                        ) {
                            Text(
                                text = "👤 $studentName ✏️",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMain,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (palette.isDark) palette.panelColor else Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .clickable { viewModel.openModal(ActiveModal.WORKSHEET_GENERATOR) }
                                .testTag("home_teacher_exam_btn")
                        ) {
                            Text(
                                text = "🖨️ Sınav",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (palette.isDark) palette.accentHead else RoyalBlue,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.amberBg,
                            border = BorderStroke(1.dp, palette.amberBorder),
                            modifier = Modifier
                                .clickable { viewModel.openModal(ActiveModal.ADMIN_PANEL) }
                                .testTag("home_teacher_admin_btn")
                        ) {
                            Text(
                                text = "🛠️ Yönetim",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.amberText,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (palette.isDark) palette.panelColor else Color(0xFFECFDF5),
                            border = BorderStroke(
                                1.dp,
                                if (palette.isDark) EmeraldGreen.copy(alpha = 0.6f) else palette.okGreen.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                text = compactLevelBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.okGreen,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Orta Satır: 🔥 Seri: 1 Gün • 🎯 Bugün: 0/20 • ⭐ Puan: 0
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.panelColor,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔥 Seri: $streak Gün  •  🎯 Bugün: $dailyCount/$dailyGoal  •  ⭐ Puan: $totalPoints",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textMain,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                // Alt Satır: Yan yana eşit genişlikte (weight(1f)) iki aksiyon butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.resumeLastSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("resume_session_button")
                    ) {
                        Text(
                            text = "▶️ Devam Et",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.selectWordStatusFilter("Günlük 15")
                            viewModel.switchMode(AppMode.WORDS)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("daily_15_review_button")
                    ) {
                        Text(
                            text = "📅 Günün 15 Tekrarı",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // BÖLÜM 2: 2x2 Grid Çalışma Modları (Ekrana tam sığan 2 satır x 2 sütun)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Satır: Sol Üst (Görselli Kelime) & Sağ Üst (Günlük Diyalog)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactGridModeCard(
                    title = "🖼️ Görselli Kelime",
                    subtitle = "$visualWordsCount Kart • Mahreç Koçu",
                    centerIcon = "🖼️",
                    centerDescription = "Görsel hafıza, harf mahreci ve SM-2 aralıklı tekrar",
                    buttonText = "🚀 Başla",
                    accentColor = RoyalBlue,
                    testTag = "mode_card_words",
                    onClick = {
                        val curCat = viewModel.selectedWordCategory.value
                        if (curCat == "sozluk_tumu" || curCat.startsWith("szl_")) {
                            viewModel.selectWordCategory("gorselli_tumu")
                        }
                        viewModel.switchMode(AppMode.WORDS)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                CompactGridModeCard(
                    title = "💬 Günlük Diyalog",
                    subtitle = "$dialoguesCount Diyalog • Rol Yapma",
                    centerIcon = "💬",
                    centerDescription = "Sesli diyalog dinleme ve karşılıklı rol canlandırma",
                    buttonText = "🎭 Diyaloglar",
                    accentColor = TealAccent,
                    testTag = "mode_card_dialogs",
                    onClick = { viewModel.switchMode(AppMode.DIALOGUE) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // 2. Satır: Sol Alt (Cümle & Akıcılık) & Sağ Alt (Sarf & Kök Sözlüğü)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactGridModeCard(
                    title = "📜 Cümle & Akıcılık",
                    subtitle = "Cümle Kur & Telaffuz",
                    centerIcon = "📜",
                    centerDescription = "Kelime kelime renkli doğruluk ve akıcılık analizi",
                    buttonText = "🎙️ Cümleler",
                    accentColor = RoyalPurple,
                    testTag = "mode_card_sentences",
                    onClick = { viewModel.switchMode(AppMode.SENTENCES) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                CompactGridModeCard(
                    title = "📖 Sarf & Kök Sözlüğü",
                    subtitle = "$sozlukCount Madde • Kök Ailesi",
                    centerIcon = "🏛️",
                    centerDescription = "Sülâsî kökler, müfret/cemî ve fiil çekim atölyesi",
                    buttonText = "🔎 Sözlüğü Aç",
                    accentColor = AccentCoral,
                    testTag = "mode_card_sarf_explorer",
                    onClick = { viewModel.openModal(ActiveModal.SARF_EXPLORER) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // BÖLÜM 3: Alt Hızlı Araçlar Satırı (2 kompakt eşit genişlikte buton kartı)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.openModal(ActiveModal.LISTENING_EXAM) }
                    .testTag("quick_tool_listening_exam"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (palette.isDark) palette.cardColor else palette.amberBg
                ),
                border = BorderStroke(1.5.dp, palette.amberBorder)
            ) {
                BoxOrCenteredRow(
                    text = "🎧 Dinle & Bul (İstimâ Sınavı)",
                    textColor = palette.amberText
                )
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.openModal(ActiveModal.ANALYTICS_REPORTS) }
                    .testTag("quick_tool_analytics_report"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = palette.cardColor),
                border = BorderStroke(
                    1.5.dp,
                    if (palette.isDark) EmeraldGreen.copy(alpha = 0.7f) else palette.okGreen.copy(alpha = 0.75f)
                )
            ) {
                BoxOrCenteredRow(
                    text = "📊 Gelişim Karnesi & PDF",
                    textColor = palette.okGreen
                )
            }
        }
    }
}

@Composable
private fun BoxOrCenteredRow(
    text: String,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactGridModeCard(
    title: String,
    subtitle: String,
    centerIcon: String,
    centerDescription: String,
    buttonText: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current

    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardColor),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Üst Başlık ve Alt-bilgi Rozeti
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textMain,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.panelColor,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textDim,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Orta Görsel İkon Rozeti + 2 Satırlık Açıklama (Kartın ortasındaki boşluğu doldurur)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = centerIcon,
                            fontSize = 26.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = centerDescription,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textDim,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Alt Aksiyon Butonu
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
