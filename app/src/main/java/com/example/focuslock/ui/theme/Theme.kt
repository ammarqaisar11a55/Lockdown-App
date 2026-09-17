package com.example.focuslock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.focuslock.R
import com.example.focuslock.domain.model.ThemeMode

/**
 * "Industry" design tokens from the Lockdown App design handoff: steel-blue accent on a light
 * technical ground, Barlow Condensed headings over Barlow body text.
 */
@Immutable
data class LockdownColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val lineSoft: Color,
    /** Accent for text and icons (contrast-safe on the ground). */
    val accent: Color,
    /** Accent for fills, indicators and borders. */
    val accentSolid: Color,
    val accentTint: Color,
    val onAccent: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val scrim: Color,
    val toast: Color,
    val onToast: Color,
    val isDark: Boolean,
)

private val Ink = Color(0xFF1D1F20)
private val Paper = Color(0xFFE6E9EB)

val LightLockdownColors = LockdownColors(
    bg = Color(0xFFF2F2F3),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE9E9EA),
    text = Ink,
    muted = Color(0xFF5D5D60),
    faint = Color(0xFF7A7A7D),
    line = Ink.copy(alpha = 0.16f),
    lineSoft = Ink.copy(alpha = 0.08f),
    accent = Color(0xFF416180),
    accentSolid = Color(0xFF5980A6),
    accentTint = Color(0xFFEEF6FF),
    onAccent = Color(0xFFF5F7FA),
    ok = Color(0xFF2F6B5E),
    warn = Color(0xFF8A5A00),
    err = Color(0xFFA23A3A),
    scrim = Color(0xFF14181C).copy(alpha = 0.45f),
    toast = Color(0xFF2B2B2D),
    onToast = Color(0xFFF2F2F3),
    isDark = false,
)

val DarkLockdownColors = LockdownColors(
    bg = Color(0xFF101418),
    surface = Color(0xFF171C21),
    surface2 = Color(0xFF1F262C),
    text = Paper,
    muted = Color(0xFF9AA5AE),
    faint = Color(0xFF7D8891),
    line = Paper.copy(alpha = 0.15f),
    lineSoft = Paper.copy(alpha = 0.08f),
    accent = Color(0xFF94BCE3),
    accentSolid = Color(0xFF94BCE3),
    accentTint = Color(0xFF94BCE3).copy(alpha = 0.13f),
    onAccent = Color(0xFF0F151A),
    ok = Color(0xFF9DD3C4),
    warn = Color(0xFFF2C36B),
    err = Color(0xFFF2B8B5),
    scrim = Color(0xFF06090B).copy(alpha = 0.62f),
    toast = Color(0xFF2B2B2D),
    onToast = Color(0xFFF2F2F3),
    isDark = true,
)

/** Fixed palette of the always-dark lockdown surface. */
object LockdownScreenColors {
    val ground = Color(0xFF0D1114)
    val text = Paper
    val clock = Color(0xFFEEF2F5)
    val dim = Color(0xFF8E9AA4)
    val label = Color(0xFF6F7B85)
    val soft = Color(0xFFA9B4BD)
    val goal = Color(0xFFCFD6DB)
    val fine = Color(0xFF5F6B74)
    val accent = Color(0xFF94BCE3)
    val glow = Color(0xFF5980A6)
    val track = Paper.copy(alpha = 0.16f)
    val outline = Paper.copy(alpha = 0.2f)
}

val LocalLockdownColors = staticCompositionLocalOf { LightLockdownColors }

/** Shorthand for the current token set. */
val Tokens: LockdownColors
    @Composable get() = LocalLockdownColors.current

val HeadingFont = FontFamily(
    Font(R.font.barlow_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

val BodyFont = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
)

/** Text styles taken from the design's recurring type treatments. */
object LockdownType {
    val screenTitle = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold, fontSize = 31.sp, lineHeight = 33.sp, letterSpacing = (-0.012).em)
    val headerTitle = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val sectionLabel = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.18.em)
    val cardTitle = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    val button = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.06.em)
    val bigNumber = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 44.sp, letterSpacing = (-0.015).em)
    val clock = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 46.sp, fontFeatureSettings = "tnum")
    val body = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 13.5.sp, lineHeight = 21.sp)
    val caption = TextStyle(fontFamily = BodyFont, fontSize = 12.5.sp, lineHeight = 18.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 20.sp)
}

private val AppTypography = Typography().let { base ->
    fun TextStyle.heading() = copy(fontFamily = HeadingFont, fontWeight = FontWeight.SemiBold)
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    base.copy(
        displayLarge = base.displayLarge.heading(),
        displayMedium = base.displayMedium.heading(),
        displaySmall = base.displaySmall.heading(),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading(),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.heading(),
        titleMedium = base.titleMedium.heading(),
        titleSmall = base.titleSmall.heading(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.heading(),
        labelMedium = base.labelMedium.heading(),
        labelSmall = base.labelSmall.heading(),
    )
}

/** Tabular digits so counters do not jitter. */
val CountdownTextStyle = TextStyle(
    fontFamily = HeadingFont,
    fontWeight = FontWeight.Normal,
    fontSize = 62.sp,
    lineHeight = 62.sp,
    letterSpacing = 0.02.em,
    fontFeatureSettings = "tnum",
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal screen padding from the design. */
    val screen = 20.dp

    /** Gap between top-level sections. */
    val section = 26.dp

    /** Minimum accessible touch target. */
    val touchTarget = 48.dp
}

object Radii {
    val control = 6.dp
    val card = 8.dp
    val dialog = 12.dp
}

private fun LockdownColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = accentSolid, onPrimary = onAccent,
        primaryContainer = accentTint, onPrimaryContainer = accent,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = accentTint, onSecondaryContainer = accent,
        tertiary = warn, background = bg, onBackground = text,
        surface = bg, onSurface = text, surfaceVariant = surface2, onSurfaceVariant = muted,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
        outline = line, outlineVariant = lineSoft, error = err, scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accentSolid, onPrimary = onAccent,
        primaryContainer = accentTint, onPrimaryContainer = accent,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = accentTint, onSecondaryContainer = accent,
        tertiary = warn, background = bg, onBackground = text,
        surface = bg, onSurface = text, surfaceVariant = surface2, onSurfaceVariant = muted,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
        outline = line, outlineVariant = lineSoft, error = err, scrim = scrim,
    )
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun FocusLockTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkLockdownColors else LightLockdownColors
    CompositionLocalProvider(LocalLockdownColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = AppTypography, content = content)
    }
}

/** The lockdown screen is always dark: calmer and less attention-grabbing. */
@Composable
fun LockdownTheme(content: @Composable () -> Unit) = FocusLockTheme(darkTheme = true, content = content)
