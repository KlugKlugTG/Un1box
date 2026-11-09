package org.un1box.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The palette the whole app draws from. One accent (sea green) carries the emulated device and
 * everything that acts on it; a violet second accent is only ever a gradient partner, so a
 * screen never has two competing primary colours. Status colours stay flat and saturated: they
 * are read at a glance, over glass, in sunlight.
 */
internal object LtColors {
    val Accent = Color(0xFF5FE0CB)
    val AccentDeep = Color(0xFF0E8271)
    val Violet = Color(0xFF9A8CFF)
    val VioletDeep = Color(0xFF5B4BE0)
    val Pink = Color(0xFFFF7BB0)
    val Danger = Color(0xFFFF6B6B)
    val DangerDeep = Color(0xFFC62828)
    val Warning = Color(0xFFFFC46B)
    val Success = Color(0xFF6EE7A5)
    // Overlay colours follow MangoHud's convention: one hue per subsystem.
    val OverlayFps = Color(0xFFFFB86B)
    val OverlayCpu = Color(0xFF6BB8FF)
    val OverlayGpu = Color(0xFF7CE38B)
    val OverlayMem = Color(0xFFD59BFF)
    val OverlayLabel = Color(0xFFB8BCC4)
}

/** The two-stop gradients the chrome is built from: aurora behind, accent on the things you press. */
internal object LtGradients {
    val accent = listOf(LtColors.Accent, Color(0xFF4FB6E8))
    val violet = listOf(LtColors.Violet, LtColors.Pink)
    val mixed = listOf(LtColors.Accent, LtColors.Violet)
}

private val Dark = darkColorScheme(
    primary = LtColors.Accent, onPrimary = Color(0xFF00312B),
    primaryContainer = Color(0xFF113F3A), onPrimaryContainer = Color(0xFFBFF6EC),
    secondary = LtColors.Violet, onSecondary = Color(0xFF221A4D),
    secondaryContainer = Color(0xFF2C2557), onSecondaryContainer = Color(0xFFE2DDFF),
    tertiary = LtColors.Pink, onTertiary = Color(0xFF451027),
    background = Color(0xFF090B11), onBackground = Color(0xFFE9EAF2),
    surface = Color(0xFF0D1017), onSurface = Color(0xFFE9EAF2),
    surfaceVariant = Color(0xFF1B2029), onSurfaceVariant = Color(0xFF9AA3B8),
    surfaceContainerLowest = Color(0xFF06070B), surfaceContainerLow = Color(0xFF11141C),
    surfaceContainer = Color(0xFF161A23), surfaceContainerHigh = Color(0xFF1D2230),
    surfaceContainerHighest = Color(0xFF252B3B),
    outline = Color(0xFF39415A), outlineVariant = Color(0xFF232A38),
    error = LtColors.Danger, onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1616), onErrorContainer = Color(0xFFFFDAD6),
)

private val Light = lightColorScheme(
    primary = LtColors.AccentDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F0E6), onPrimaryContainer = Color(0xFF00201B),
    secondary = LtColors.VioletDeep, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E0FF), onSecondaryContainer = Color(0xFF17094F),
    tertiary = Color(0xFFB3346E), onTertiary = Color.White,
    background = Color(0xFFF2F4FA), onBackground = Color(0xFF12151E),
    surface = Color(0xFFF2F4FA), onSurface = Color(0xFF12151E),
    surfaceVariant = Color(0xFFE3E6EF), onSurfaceVariant = Color(0xFF5A6275),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEFF1F8),
    surfaceContainerHighest = Color(0xFFE6E9F3),
    outline = Color(0xFFC2C8D6), outlineVariant = Color(0xFFDCE0EA),
    error = LtColors.DangerDeep, onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val Type = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

/** The one set of corner radii: cards are soft, controls are rounded, chips are pills. */
private val LtShapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp))

internal val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)

/** Full screen is always dark: its menus sit on black around the guest. */
@Composable
internal fun DarkTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, typography = Type,
    shapes = LtShapes) { OnBackground(content) }

/** Screens drawn straight on the background, outside a Surface, still get readable text and icons. */
@Composable
private fun OnBackground(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)

@Composable
internal fun Un1boxTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = LtShapes) { OnBackground(content) }
}
