package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class MuallimunPalette(
    val isDark: Boolean,
    val bgColor: Color,
    val cardColor: Color,
    val panelColor: Color,
    val chatBg: Color,
    val textMain: Color,
    val textDim: Color,
    val textArabic: Color,
    val accentHead: Color,
    val border: Color,
    val btnSec: Color,
    val okGreen: Color,
    val amberText: Color = WarmGold,
    val amberBorder: Color = WarmGold.copy(alpha = 0.65f),
    val amberBg: Color = DarkPanelColor
)

val LocalMuallimunPalette = staticCompositionLocalOf {
    MuallimunPalette(
        isDark = true,
        bgColor = DarkBgColor,
        cardColor = DarkCardColor,
        panelColor = DarkPanelColor,
        chatBg = DarkChatBg,
        textMain = DarkTextMain,
        textDim = DarkTextDim,
        textArabic = DarkTextArabic,
        accentHead = WarmGold,
        border = DarkBorder,
        btnSec = DarkBtnSec,
        okGreen = EmeraldGreen,
        amberText = WarmGold,
        amberBorder = WarmGold.copy(alpha = 0.65f),
        amberBg = DarkPanelColor
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    secondary = WarmGold,
    onSecondary = DarkBgColor,
    tertiary = EmeraldGreen,
    background = DarkBgColor,
    onBackground = DarkTextMain,
    surface = DarkCardColor,
    onSurface = DarkTextMain,
    surfaceVariant = DarkPanelColor,
    onSurfaceVariant = DarkTextDim,
    error = CoralRed
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    secondary = DeepNavy,
    onSecondary = Color.White,
    tertiary = EmeraldGreenLightMode,
    background = LightBgColor,
    onBackground = LightTextMain,
    surface = LightCardColor,
    onSurface = LightTextMain,
    surfaceVariant = LightPanelColor,
    onSurfaceVariant = LightTextDim,
    error = CoralRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    themeMode: String = if (darkTheme) "gece" else "gunduz",
    content: @Composable () -> Unit
) {
    val normalizedMode = when (themeMode.lowercase()) {
        "dark", "gece" -> "gece"
        "light", "gunduz" -> "gunduz"
        "okul" -> "okul"
        "kurumsal" -> "kurumsal"
        else -> if (darkTheme) "gece" else "gunduz"
    }
    val isDarkResolved = normalizedMode == "gece" || normalizedMode == "kurumsal"
    val colorScheme = if (isDarkResolved) DarkColorScheme else LightColorScheme

    val palette = when (normalizedMode) {
        "kurumsal" -> MuallimunPalette(
            isDark = true,
            bgColor = Color(0xFF0A192F),
            cardColor = Color(0xFF112240),
            panelColor = Color(0xFF172A45),
            chatBg = Color(0xFF071120),
            textMain = Color(0xFFE6F1FF),
            textDim = Color(0xFF8892B0),
            textArabic = WarmGold,
            accentHead = Color(0xFF64FFDA),
            border = Color(0xFF233554),
            btnSec = Color(0xFF1D3557),
            okGreen = EmeraldGreen,
            amberText = WarmGold,
            amberBorder = WarmGold.copy(alpha = 0.65f),
            amberBg = Color(0xFF172A45)
        )
        "okul" -> MuallimunPalette(
            isDark = false,
            bgColor = Color(0xFFFDFBF7),
            cardColor = Color(0xFFFFFFFF),
            panelColor = Color(0xFFF3EFE6),
            chatBg = Color(0xFFFAF6EE),
            textMain = Color(0xFF1C1917),
            textDim = Color(0xFF57534E),
            textArabic = Color(0xFF0F4C81),
            accentHead = Color(0xFF0F4C81),
            border = Color(0xFFD6D3D1),
            btnSec = Color(0xFFE7E2D8),
            okGreen = EmeraldGreenLightMode,
            amberText = AmberTextLightMode,
            amberBorder = AmberBorderLightMode,
            amberBg = AmberBgLightMode
        )
        "gunduz" -> MuallimunPalette(
            isDark = false,
            bgColor = LightBgColor,
            cardColor = LightCardColor,
            panelColor = LightPanelColor,
            chatBg = LightChatBg,
            textMain = LightTextMain,
            textDim = LightTextDim,
            textArabic = LightTextArabic,
            accentHead = DeepNavy,
            border = LightBorder,
            btnSec = LightBtnSec,
            okGreen = EmeraldGreenLightMode,
            amberText = AmberTextLightMode,
            amberBorder = AmberBorderLightMode,
            amberBg = AmberBgLightMode
        )
        else -> MuallimunPalette(
            isDark = true,
            bgColor = DarkBgColor,
            cardColor = DarkCardColor,
            panelColor = DarkPanelColor,
            chatBg = DarkChatBg,
            textMain = DarkTextMain,
            textDim = DarkTextDim,
            textArabic = DarkTextArabic,
            accentHead = WarmGold,
            border = DarkBorder,
            btnSec = DarkBtnSec,
            okGreen = EmeraldGreen,
            amberText = WarmGold,
            amberBorder = WarmGold.copy(alpha = 0.65f),
            amberBg = DarkPanelColor
        )
    }

    CompositionLocalProvider(LocalMuallimunPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
