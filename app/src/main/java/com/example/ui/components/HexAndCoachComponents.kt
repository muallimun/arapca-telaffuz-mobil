package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WordMorphologyInfo
import com.example.nlp.ArabicNlpEngine
import com.example.ui.theme.AmiriArabicFontFamily
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.WarmGold
import com.example.viewmodel.EvaluationSummaryState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HexagonRootBadge(
    rootText: String,
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val hexFill = if (palette.isDark) DeepNavy else Color(0xFFDBEAFE)
    val hexStroke = if (palette.isDark) Color(0xFF38BDF8) else RoyalBlue

    Box(
        modifier = modifier
            .size(width = 106.dp, height = 92.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("hexagon_root_badge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 4.dp.toPx()
            val radius = minOf(size.width, size.height) * 0.40f
            val path = Path()
            for (i in 0 until 6) {
                val angleRad = Math.toRadians((60.0 * i) - 30.0)
                val x = cx + (radius * cos(angleRad)).toFloat()
                val y = cy + (radius * sin(angleRad)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path = path, color = hexFill)
            drawPath(path = path, color = hexStroke, style = Stroke(width = 2.dp.toPx()))
        }

        Text(
            text = emoji,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 2.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = rootText,
                fontFamily = AmiriArabicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = if (palette.isDark) WarmGold else DeepNavy,
                textAlign = TextAlign.Center
            )
            Text(
                text = "SÜLÂSÎ KÖK",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (palette.isDark) Color(0xFF93C5FD) else DeepNavy
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Kök Ailesi",
                tint = palette.textDim,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "Kök Ailesi",
                fontSize = 9.sp,
                color = palette.textDim,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordMorphologyCardBar(
    morphInfo: WordMorphologyInfo,
    activeMorphArabic: String?,
    defaultWordArabic: String,
    onSelectForm: (arabic: String, label: String) -> Unit,
    onOpenRootExplorer: (root: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val currentSelected = activeMorphArabic ?: defaultWordArabic
    var tooltipMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HexagonRootBadge(
                    rootText = morphInfo.entry.kok,
                    emoji = morphInfo.entry.emoji,
                    onClick = { onOpenRootExplorer(morphInfo.entry.kok) }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val turBadge = if (morphInfo.entry.tur == "isim") {
                        "🏷️ İSİM (Tekil & Çoğul)"
                    } else {
                        "⚡ FİİL (Mâzî • Muzâri • Masdar)"
                    }
                    Text(
                        text = "${morphInfo.entry.kategori}  |  $turBadge",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead
                    )
                    Text(
                        text = "Telaffuz edeceğiniz forma dokunun (Terim bilgisi için ℹ️):",
                        fontSize = 10.sp,
                        color = palette.textDim
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        morphInfo.forms.forEach { form ->
                            val isSelected = form.arabic == currentSelected
                            val bgCol = if (isSelected) EmeraldGreen else palette.cardColor
                            val fgCol = if (isSelected) Color(0xFF0F172A) else palette.textArabic
                            val borderCol = if (isSelected) EmeraldGreen else Color(form.colorHex)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bgCol,
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderCol),
                                modifier = Modifier
                                    .clickable {
                                        onSelectForm(form.arabic, form.label)
                                        tooltipMessage = ArabicNlpEngine.TERM_TOOLTIPS[form.label]
                                    }
                                    .testTag("morph_form_${form.role}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = form.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF0F172A) else palette.textDim
                                    )
                                    Text(
                                        text = form.arabic,
                                        fontFamily = AmiriArabicFontFamily,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fgCol
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (morphInfo.family.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val famStr = morphInfo.family.take(3).joinToString("  •  ") { f ->
                    val head = if (f.tur == "isim") f.mufret else f.mazi
                    "$head (${f.anlamTr.substringBefore(",").take(16)})"
                }
                Text(
                    text = "🌿 Kök Ailesi: $famStr",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRootExplorer(morphInfo.entry.kok) }
                        .padding(top = 2.dp)
                )
            }

            AnimatedVisibility(visible = !tooltipMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, WarmGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { tooltipMessage = null }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Terim Açıklaması",
                            tint = WarmGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tooltipMessage ?: "",
                            fontSize = 11.sp,
                            color = WarmGold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveWaveformBars(
    bars: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barColorOverride: Color? = null
) {
    val palette = LocalMuallimunPalette.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val count = bars.size.coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - (count - 1) * gap) / count).coerceIn(4.dp.toPx(), 10.dp.toPx())
        val totalWidth = count * barWidth + (count - 1) * gap
        val startX = (size.width - totalWidth) / 2f

        for ((i, rms) in bars.withIndex()) {
            val h = if (isRecording || barColorOverride != null) {
                (rms * size.height).coerceIn(5.dp.toPx(), size.height)
            } else {
                5.dp.toPx()
            }
            val x = startX + i * (barWidth + gap)
            val y = (size.height - h) / 2f
            val col = barColorOverride ?: when {
                !isRecording -> palette.border
                rms < 0.35f -> EmeraldGreen
                rms < 0.70f -> WarmGold
                else -> CoralRed
            }
            drawRoundRect(
                color = col,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveMakhrajAndHarakaCoachCard(
    evalState: EvaluationSummaryState,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_makhraj_coach_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.panelColor),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🎯 CANLI MAHREÇ, KÖK HARF & HAREKE KOÇU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Aç / Kapat",
                    tint = palette.accentHead
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (evalState.wordStatuses.isNotEmpty()) {
                        Text(
                            text = "Kelime Doğruluğu (Yeşil: Doğru / Kırmızı: Hatalı):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textDim
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                evalState.wordStatuses.forEach { (wStr, isOk) ->
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

                    if (evalState.makhrajTips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        evalState.makhrajTips.forEach { tip ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.cardColor,
                                border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = tip.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (palette.isDark) WarmGold else Color(0xFFB45309)
                                    )
                                    if (tip.wordExp.isNotBlank() && tip.wordHeard.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Hedef: ",
                                                fontSize = 11.sp,
                                                color = palette.textDim
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(CoralRed, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = tip.wordExp,
                                                    fontFamily = AmiriArabicFontFamily,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Text(
                                                text = "  ➔  Duyulan: ${tip.wordHeard}",
                                                fontFamily = AmiriArabicFontFamily,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.textMain
                                            )
                                        }
                                    }
                                    Text(
                                        text = tip.tip,
                                        fontSize = 12.sp,
                                        color = palette.textMain
                                    )
                                }
                            }
                        }
                    } else if (evalState.isCorrect == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✅ Mahreç, kök harf ve med (uzatma) uyumu gayet güzel!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.okGreen
                        )
                    }

                    if (evalState.harakaGuide.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.cardColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "🔤 HAREKE & VOKAL REHBERİ:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentHead
                                )
                                Text(
                                    text = evalState.harakaGuide,
                                    fontSize = 11.sp,
                                    color = palette.textMain
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
