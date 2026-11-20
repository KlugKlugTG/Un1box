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
 * The app wears iOS 6: the last Apple interface drawn by hand, with its blue lacquered bars,
 * pinstriped grouped tables, hairline separators, gloss on everything you can press and a
 * Helvetica-like sans for the words.
 *
 * Two palettes exist, because the app has a theme switch. Light is the iOS 6 everybody
 * remembers: a pinstriped blue-grey page, white cells, one blue tint. Dark is the same phone
 * at night: black chrome, dark linen, and the same gloss.
 *
 * Everything a screen draws comes from these tokens, so a screen that only names
 * `MaterialTheme.colorScheme.surface` becomes a white iOS cell without being edited.
 */
internal object LtColors {
    /** The tint: iOS blue, the colour of every selected thing. */
    val Accent = Color(0xFF4A90D9)
    val AccentDeep = Color(0xFF1263B8)
    /** A second accent for gradients, kept close to the blue so nothing competes with it. */
    val Violet = Color(0xFF8E8CE0)
    val VioletDeep = Color(0xFF4B4AA8)
    val Pink = Color(0xFFE58FB4)
    val Danger = Color(0xFFD93B30)
    val DangerDeep = Color(0xFFB0261D)
    val Warning = Color(0xFFE8A317)
    val Success = Color(0xFF3FA936)
    // Overlay colours follow MangoHud's convention: one hue per subsystem.
    val OverlayFps = Color(0xFFF0A030)
    val OverlayCpu = Color(0xFF3C9BE8)
    val OverlayGpu = Color(0xFF46B95C)
    val OverlayMem = Color(0xFFA46BD8)
    val OverlayLabel = Color(0xFFB8BCC4)
}

/**
 * The iOS 6 palette, named as the parts of the phone rather than as themes: the tint, the
 * chrome, the paper a table is printed on, the gloss on a button.
 */
internal object Ios6 {
    // The one tint.
    val Blue = Color(0xFF3478D4)
    val BlueDeep = Color(0xFF0D4C96)
    val BlueLight = Color(0xFF7FB2EA)
    val BlueSelection = Color(0xFF1D62B5)

    // Chrome: navigation bars, tab bars, toolbars.
    val NavTop = Color(0xFF93B4D6)
    val NavMid = Color(0xFF4C7FBD)
    val NavBottom = Color(0xFF2A5A97)
    val NavTopDark = Color(0xFF6E6E72)
    val NavMidDark = Color(0xFF3A3A3D)
    val NavBottomDark = Color(0xFF151517)
    val BarButtonTop = Color(0xFF7FA8DB)
    val BarButtonBottom = Color(0xFF2A5E9F)

    // Tab bars.
    val TabTop = Color(0xFF5A5A5C)
    val TabBottom = Color(0xFF121214)
    val TabSelectedTop = Color(0xFF7C7C80)
    val TabSelectedBottom = Color(0xFF2C2C30)

    // Buttons.
    val ButtonTop = Color(0xFF8FBCEF)
    val ButtonMid = Color(0xFF3C7CD2)
    val ButtonBottom = Color(0xFF1C57A5)
    val ButtonEdge = Color(0xFF103F7C)
    val GrayButtonTop = Color(0xFFFBFBFB)
    val GrayButtonMid = Color(0xFFDEDEE2)
    val GrayButtonBottom = Color(0xFFB9B9BF)
    val GrayButtonEdge = Color(0xFF8E8E93)
    val DestructiveTop = Color(0xFFEC9089)
    val DestructiveMid = Color(0xFFD04237)
    val DestructiveBottom = Color(0xFFA72A21)

    // Paper.
    val PageTop = Color(0xFFD7DCE1)
    val PageBottom = Color(0xFFC2C9D1)
    val PageDarkTop = Color(0xFF232326)
    val PageDarkBottom = Color(0xFF141416)
    val Cell = Color(0xFFFFFFFF)
    val CellDark = Color(0xFF1F1F22)
    val Separator = Color(0xFFC7C7CC)
    val SeparatorDark = Color(0xFF34343A)
    val HeaderTop = Color(0xFFA9B5C4)
    val HeaderBottom = Color(0xFF8795A8)
    val HeaderTopDark = Color(0xFF4B4B4F)
    val HeaderBottomDark = Color(0xFF303034)

    // Text.
    val Ink = Color(0xFF1A1A1A)
    val InkSecondary = Color(0xFF5B5B60)
    val InkDark = Color(0xFFF2F2F5)
    val InkSecondaryDark = Color(0xFF9A9AA1)
    val Link = Color(0xFF1F5FAF)

    // Alert sheets (UIAlertView) are a blue-grey lacquer in both schemes.
    val AlertTop = Color(0xFF6C809B)
    val AlertBottom = Color(0xFF26344A)
    val AlertButtonTop = Color(0xFF7C9CC9)
    val AlertButtonBottom = Color(0xFF2F4E7B)

    // Controls.
    val SwitchTrackOn = Color(0xFF2A6FC4)
    val SwitchTrackOff = Color(0xFFD5D5DA)
    val SwitchKnobTop = Color(0xFFFFFFFF)
    val SwitchKnobBottom = Color(0xFFD4D4D9)

    /** The page a grouped table is printed on; dark at night. */
    fun page(dark: Boolean) = if (dark) PageDarkTop else PageTop
    fun cell(dark: Boolean) = if (dark) CellDark else Cell
    fun separator(dark: Boolean) = if (dark) SeparatorDark else Separator
    fun ink(dark: Boolean) = if (dark) InkDark else Ink
    fun inkSecondary(dark: Boolean) = if (dark) InkSecondaryDark else InkSecondary

    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
}

/** The two-stop gradients the chrome is built from: accent on the things you press. */
internal object LtGradients {
    val accent = listOf(LtColors.Accent, Ios6.BlueLight)
    val violet = listOf(LtColors.Violet, LtColors.Pink)
    val mixed = listOf(Ios6.ButtonTop, Ios6.ButtonMid, Ios6.ButtonBottom)
}

private val Dark = darkColorScheme(
    primary = Color(0xFF6FA8E8), onPrimary = Color.White,
    primaryContainer = Color(0xFF23405F), onPrimaryContainer = Color(0xFFD8E8FA),
    secondary = Color(0xFFA6A6E8), onSecondary = Color(0xFF15152B),
    secondaryContainer = Color(0xFF2C2C46), onSecondaryContainer = Color(0xFFE4E4FF),
    tertiary = Color(0xFFE0A0BC), onTertiary = Color(0xFF33121F),
    background = Ios6.PageDarkBottom, onBackground = Ios6.InkDark,
    surface = Ios6.CellDark, onSurface = Ios6.InkDark,
    surfaceVariant = Color(0xFF2A2A2E), onSurfaceVariant = Ios6.InkSecondaryDark,
    surfaceContainerLowest = Color(0xFF0E0E10), surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Color(0xFF1F1F23), surfaceContainerHigh = Color(0xFF26262B),
    surfaceContainerHighest = Color(0xFF2E2E34),
    outline = Color(0xFF4A4A50), outlineVariant = Ios6.SeparatorDark,
    error = Color(0xFFFF6B62), onError = Color(0xFF2B0906),
    errorContainer = Color(0xFF4A1713), onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE8E8EC), inverseOnSurface = Color(0xFF16161A),
)

private val Light = lightColorScheme(
    primary = Ios6.Blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7), onPrimaryContainer = Color(0xFF0A2B4F),
    secondary = Color(0xFF5D5D9E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E3F5), onSecondaryContainer = Color(0xFF1A1A3A),
    tertiary = Color(0xFFA63D62), onTertiary = Color.White,
    background = Ios6.PageBottom, onBackground = Ios6.Ink,
    surface = Ios6.Cell, onSurface = Ios6.Ink,
    surfaceVariant = Color(0xFFEDEFF2), onSurfaceVariant = Ios6.InkSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF2F3F6),
    surfaceContainerHighest = Color(0xFFE6E9EE),
    outline = Color(0xFF9A9AA0), outlineVariant = Ios6.Separator,
    error = Ios6.DestructiveMid, onError = Color.White,
    errorContainer = Color(0xFFF6D7D4), onErrorContainer = Color(0xFF4A0F0A),
    inverseSurface = Color(0xFF2A2A2E), inverseOnSurface = Color(0xFFF4F4F7),
)

/**
 * Helvetica Neue's proportions in the sans Android ships: tighter than Material's default
 * rhythm, slightly larger body text, and bold where iOS is bold.
 */
private val IosFont = FontFamily.SansSerif

private fun iosStyle(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 4) =
    TextStyle(fontFamily = IosFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)

private val Type = Typography(
    displayLarge = iosStyle(34, FontWeight.Bold, 40),
    displayMedium = iosStyle(30, FontWeight.Bold, 36),
    displaySmall = iosStyle(26, FontWeight.Bold, 32),
    headlineLarge = iosStyle(26, FontWeight.Bold, 32),
    headlineMedium = iosStyle(23, FontWeight.Bold, 29),
    headlineSmall = iosStyle(20, FontWeight.Bold, 25),
    titleLarge = iosStyle(20, FontWeight.Bold, 25),
    titleMedium = iosStyle(17, FontWeight.SemiBold, 22),
    titleSmall = iosStyle(16, FontWeight.SemiBold, 20),
    bodyLarge = iosStyle(17, FontWeight.Normal, 22),
    bodyMedium = iosStyle(15, FontWeight.Normal, 20),
    bodySmall = iosStyle(13, FontWeight.Normal, 17),
    labelLarge = iosStyle(15, FontWeight.Bold, 19),
    labelMedium = iosStyle(13, FontWeight.SemiBold, 17),
    labelSmall = iosStyle(11, FontWeight.Medium, 14),
)

/** iOS 6 corner radii: cells and alerts are 10 dp, controls are pills. */
private val IosShapes = Shapes(small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(10.dp))

internal val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)

/** Full screen is always dark: its menus sit on black around the guest. */
@Composable
internal fun DarkTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, typography = Type,
    shapes = IosShapes) { OnBackground(content) }

/** Screens drawn straight on the background, outside a Surface, still get readable text and icons. */
@Composable
private fun OnBackground(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)

@Composable
internal fun Un1boxTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = IosShapes) { OnBackground(content) }
}
