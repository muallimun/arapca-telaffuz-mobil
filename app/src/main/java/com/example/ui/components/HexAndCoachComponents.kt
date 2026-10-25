package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
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

@Composable
fun HexagonRootBadge(
    rootText: String,
    emoji: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val boxFill = if (palette.isDark) DeepNavy else Color(0xFFDBEAFE)
    val boxStroke = if (palette.isDark) Color(0xFF38BDF8) else RoyalBlue

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = boxFill,
        border = BorderStroke(1.5.dp, boxStroke),
        modifier = modifier
            .clickable { onClick() }
            .testTag("hexagon_root_badge")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = rootText.ifBlank { "—" },
                fontFamily = AmiriArabicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (palette.isDark) WarmGold else DeepNavy,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (palette.isDark) Color(0xFF1E293B) else Color.White,
                border = BorderStroke(1.dp, boxStroke.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "🔍 Kök Ailesi",
                    fontSize = 10.sp,
                    color = if (palette.isDark) Color(0xFF93C5FD) else RoyalBlue,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
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
                        text = turBadge,
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

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                                            color = fgCol,
                                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                        )
                                    }
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
                rms < 0.60f -> EmeraldGreen
                else -> WarmGold
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
    activeArabicText: String = evalState.expectedArabic,
    onSpeakWord: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val targetAr = activeArabicText.ifBlank { evalState.expectedArabic }
    val hasResult = evalState.isCorrect != null &&
        (evalState.expectedArabic == targetAr || activeArabicText.isBlank())
    val isListeningNow = !hasResult && evalState.verdictTitle.startsWith("🎙️")

    val borderColor = when {
        isListeningNow -> EmeraldGreen
        !hasResult -> palette.border
        evalState.isCorrect == true -> EmeraldGreen
        else -> CoralRed
    }
    val borderWidth = if (hasResult || isListeningNow) 2.dp else 1.dp
    val containerBg = when {
        isListeningNow -> EmeraldGreen.copy(alpha = if (palette.isDark) 0.12f else 0.08f)
        !hasResult -> palette.panelColor
        evalState.isCorrect == true -> EmeraldGreen.copy(alpha = if (palette.isDark) 0.12f else 0.08f)
        else -> CoralRed.copy(alpha = if (palette.isDark) 0.12f else 0.08f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_makhraj_coach_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Başlık & Doğruluk Yüzdesi / Dinleniyor Rozeti Satırı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎯 Mahreç & Telaffuz Koçu",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isListeningNow -> palette.okGreen
                        hasResult -> if (evalState.isCorrect == true) palette.okGreen else CoralRed
                        else -> palette.accentHead
                    },
                    maxLines = 1
                )

                if (isListeningNow) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldGreen
                    ) {
                        Text(
                            text = "🎙️ Dinleniyor...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else if (hasResult) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (evalState.isCorrect == true) EmeraldGreen else CoralRed
                    ) {
                        Text(
                            text = if (evalState.isCorrect == true) {
                                "✅ Doğruluk: %${evalState.accuracyPct}"
                            } else {
                                "⚠️ Doğruluk: %${evalState.accuracyPct}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (evalState.isCorrect == true) Color(0xFF0F172A) else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!hasResult) {
                // Konuşmadan Önce veya Dinleme Sırasında: Aktif kelime/cümlenin kısa hareke ve mahreç ipucu
                if (isListeningNow) {
                    Text(
                        text = "${evalState.verdictTitle} — ${evalState.statusDetail}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.okGreen
                    )
                }
                val proactiveGuide = remember(targetAr) {
                    ArabicNlpEngine.buildProactiveMakhrajGuide(targetAr)
                }
                if (proactiveGuide.isNotBlank()) {
                    Text(
                        text = proactiveGuide,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = palette.textMain
                    )
                } else {
                    Text(
                        text = "🎙️ Telaffuz ve mahreç analizi için '🎤 BAŞLA VE KONUŞ' butonuna dokunun.",
                        fontSize = 11.sp,
                        color = palette.textDim
                    )
                }
            } else {
                // Konuştuktan Sonra: Telaffuz Sonuç Kartı + Tıklanabilir Kelimeler + Mahreç Koçu Tavsiyesi
                Text(
                    text = evalState.verdictTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (evalState.isCorrect == true) palette.okGreen else CoralRed
                )

                if (evalState.wordStatuses.isNotEmpty()) {
                    Text(
                        text = "Kelime Geri Bildirimi (Dinlemek için kelimeye dokunun 🔊):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textDim
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            evalState.wordStatuses.forEach { (wStr, isOk) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isOk) EmeraldGreen.copy(alpha = 0.18f) else CoralRed.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, if (isOk) EmeraldGreen else CoralRed),
                                    modifier = Modifier.clickable(enabled = onSpeakWord != null) {
                                        onSpeakWord?.invoke(wStr)
                                    }
                                ) {
                                    Text(
                                        text = wStr,
                                        fontFamily = AmiriArabicFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (isOk) palette.okGreen else CoralRed,
                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (evalState.makhrajTips.isNotEmpty()) {
                    evalState.makhrajTips.forEach { tip ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.cardColor,
                            border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = tip.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (palette.isDark) WarmGold else Color(0xFFB45309)
                                )
                                if (tip.wordExp.isNotBlank() && tip.wordHeard.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Hedef: ${tip.wordExp}  ➔  Algılanan: ${tip.wordHeard}",
                                            fontFamily = AmiriArabicFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.textMain,
                                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
                                        )
                                    }
                                }
                                Text(
                                    text = tip.tip,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = palette.textMain
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (evalState.isCorrect == true) {
                            "✅ Mahreç, kök harf ve hareke uyumu gayet güzel! (${evalState.statusDetail})"
                        } else {
                            evalState.statusDetail
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (evalState.isCorrect == true) palette.okGreen else palette.textMain
                    )
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val startXFraction: Float,
    val startYFraction: Float,
    val driftX: Float,
    val fallSpeed: Float,
    val wobbleFreq: Float,
    val rotationSpeed: Float,
    val widthDp: Float,
    val heightDp: Float,
    val color: Color,
    val isCircle: Boolean
)

/**
 * %75+ başarıda (evalState.showConfetti == true) kartın üzerinde oynatılan
 * 2 saniyelik zarif Canvas konfeti parçacık animasyonu.
 */
@Composable
fun ConfettiOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )
    }

    val particles = remember {
        val colors = listOf(
            EmeraldGreen,
            WarmGold,
            RoyalBlue,
            CoralRed,
            Color(0xFFA855F7),
            Color(0xFF14B8A6)
        )
        val rng = Random(42)
        List(44) { idx ->
            ConfettiParticle(
                startXFraction = 0.05f + rng.nextFloat() * 0.90f,
                startYFraction = -0.05f - (rng.nextFloat() * 0.25f),
                driftX = (rng.nextFloat() - 0.5f) * 0.32f,
                fallSpeed = 0.85f + rng.nextFloat() * 0.55f,
                wobbleFreq = 4f + rng.nextFloat() * 6f,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 540f,
                widthDp = 6f + rng.nextFloat() * 5f,
                heightDp = 4f + rng.nextFloat() * 4f,
                color = colors[idx % colors.size],
                isCircle = idx % 4 == 0
            )
        }
    }

    val t = progress.value
    if (t >= 1f) return

    val alpha = if (t > 0.72f) ((1f - t) / 0.28f).coerceIn(0f, 1f) else 1f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("confetti_canvas_overlay")
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        particles.forEach { p ->
            val wobble = sin(t * p.wobbleFreq) * 14.dp.toPx()
            val x = (p.startXFraction + p.driftX * t) * w + wobble
            val y = (p.startYFraction + p.fallSpeed * t) * h
            if (y in -24f..(h + 24f)) {
                val pColor = p.color.copy(alpha = alpha)
                if (p.isCircle) {
                    drawCircle(
                        color = pColor,
                        radius = (p.widthDp * 0.5f).dp.toPx(),
                        center = Offset(x, y)
                    )
                } else {
                    rotate(degrees = t * p.rotationSpeed, pivot = Offset(x, y)) {
                        drawRoundRect(
                            color = pColor,
                            topLeft = Offset(x - (p.widthDp * 0.5f).dp.toPx(), y - (p.heightDp * 0.5f).dp.toPx()),
                            size = Size(p.widthDp.dp.toPx(), p.heightDp.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

