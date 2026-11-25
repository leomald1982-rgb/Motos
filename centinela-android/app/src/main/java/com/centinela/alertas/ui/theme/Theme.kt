package com.centinela.alertas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.Severity

object Hud {
    val Bg = Color(0xFF020A14)
    val BgDeep = Color(0xFF01060C)
    val Panel = Color(0xFF051726)
    val PanelHi = Color(0xFF0A2236)
    val Cyan = Color(0xFF00E5FF)
    val CyanSoft = Color(0xFF7FF3FF)
    val Blue = Color(0xFF1E90FF)
    val BlueDeep = Color(0xFF0B4F8A)
    val Line = Color(0xFF0E3A57)
    val Text = Color(0xFFD8F6FF)
    val TextDim = Color(0xFF6E9AB3)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF3D5A)
    val Green = Color(0xFF2CFFB0)
    val Violet = Color(0xFF8C7CFF)

    val Mono = FontFamily.Monospace

    fun severity(s: Severity) = when (s) {
        Severity.INFO -> Cyan
        Severity.ADVERTENCIA -> Amber
        Severity.CRITICA -> Red
    }

    fun category(c: Category) = when (c) {
        Category.SISMO -> Cyan
        Category.DESAPARECIDO -> Violet
        Category.FUGA -> Amber
        Category.BUSCADO -> Red
    }
}

private val typography = Typography().let { t ->
    fun TextStyle.m() = copy(fontFamily = Hud.Mono, color = Hud.Text)
    t.copy(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m().copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
        titleMedium = t.titleMedium.m().copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        titleSmall = t.titleSmall.m(), bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(),
        bodySmall = t.bodySmall.m(), labelLarge = t.labelLarge.m().copy(letterSpacing = 2.sp),
        labelMedium = t.labelMedium.m().copy(letterSpacing = 1.5.sp), labelSmall = t.labelSmall.m().copy(letterSpacing = 1.5.sp),
    )
}

@Composable
fun CentinelaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Hud.Cyan, onPrimary = Hud.BgDeep, secondary = Hud.Blue, onSecondary = Hud.Text,
            background = Hud.Bg, onBackground = Hud.Text, surface = Hud.Panel, onSurface = Hud.Text,
            surfaceVariant = Hud.PanelHi, onSurfaceVariant = Hud.TextDim, error = Hud.Red, outline = Hud.Line,
            surfaceContainer = Hud.Panel, surfaceContainerHigh = Hud.PanelHi, surfaceContainerHighest = Hud.PanelHi,
        ),
        typography = typography,
        content = content,
    )
}
