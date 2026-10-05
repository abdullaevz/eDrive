package com.edrive.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Web versiyası ilə eyni palitra: qrafit fon + nanə-yaşıl aksent. */
object EColors {
    val Bg = Color(0xFF0A0D12)
    val Bg2 = Color(0xFF0E1218)
    val Surface = Color(0xFF121720)
    val Surface2 = Color(0xFF171D28)
    val Surface3 = Color(0xFF1D2532)
    val Line = Color(0xFF232C3A)
    val Line2 = Color(0xFF2D384A)
    val Text = Color(0xFFE7ECF3)
    val Muted = Color(0xFF8693A7)
    val Faint = Color(0xFF5A667A)
    val Accent = Color(0xFF3DDC97)
    val AccentDim = Color(0xFF2BB47A)
    val AccentInk = Color(0xFF04170F)
    val Amber = Color(0xFFF2B64A)
    val Danger = Color(0xFFFF6B6B)
}

private val scheme = darkColorScheme(
    primary = EColors.Accent,
    onPrimary = EColors.AccentInk,
    primaryContainer = Color(0xFF0F3527),
    onPrimaryContainer = EColors.Accent,
    secondary = EColors.Muted,
    onSecondary = EColors.Bg,
    tertiary = EColors.Amber,
    background = EColors.Bg,
    onBackground = EColors.Text,
    surface = EColors.Bg,
    onSurface = EColors.Text,
    surfaceVariant = EColors.Surface2,
    onSurfaceVariant = EColors.Muted,
    surfaceContainerLowest = EColors.Bg,
    surfaceContainerLow = EColors.Surface,
    surfaceContainer = EColors.Surface,
    surfaceContainerHigh = EColors.Surface2,
    surfaceContainerHighest = EColors.Surface3,
    outline = EColors.Line2,
    outlineVariant = EColors.Line,
    error = EColors.Danger,
    onError = Color(0xFF2A0606),
    scrim = Color(0xCC04060A),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = EColors.Muted)

private val typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        // Surface xaricində də mətn rəngi açıq olsun (qara fonda qara mətn olmasın)
        CompositionLocalProvider(LocalContentColor provides EColors.Text, content = content)
    }
}
