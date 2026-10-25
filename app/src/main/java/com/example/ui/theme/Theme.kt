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
    val okGreen: Color
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
        okGreen = EmeraldGreen
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
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val palette = if (darkTheme) {
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
            okGreen = EmeraldGreen
        )
    } else {
        MuallimunPalette(
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
            okGreen = EmeraldGreenLightMode
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
