package com.example.focuslock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calm, low-saturation palette. Contrast pairs meet WCAG AA for body text.
private val Slate950 = Color(0xFF0E1417)
private val Slate900 = Color(0xFF151D21)
private val Slate800 = Color(0xFF1F2A30)
private val Slate700 = Color(0xFF2E3C44)
private val Slate300 = Color(0xFFB7C4CB)
private val Slate100 = Color(0xFFE4EBEE)
private val Mist = Color(0xFFF5F7F7)
private val Sage700 = Color(0xFF2F6B5E)
private val Sage200 = Color(0xFF9DD3C4)
private val Sage100 = Color(0xFFCFEBE2)
private val Sage900 = Color(0xFF0B3A30)
private val Amber700 = Color(0xFF8A5A00)
private val Amber200 = Color(0xFFF2C36B)
private val Rose700 = Color(0xFFA23A3A)
private val Rose200 = Color(0xFFF2B8B5)

private val LightColors = lightColorScheme(
    primary = Sage700,
    onPrimary = Color.White,
    primaryContainer = Sage100,
    onPrimaryContainer = Sage900,
    secondary = Slate700,
    onSecondary = Color.White,
    tertiary = Amber700,
    background = Mist,
    onBackground = Slate950,
    surface = Mist,
    onSurface = Slate950,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Slate100,
    error = Rose700,
)

private val DarkColors = darkColorScheme(
    primary = Sage200,
    onPrimary = Sage900,
    primaryContainer = Sage700,
    onPrimaryContainer = Sage100,
    secondary = Slate300,
    onSecondary = Slate900,
    tertiary = Amber200,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate950,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainer = Slate900,
    surfaceContainerHigh = Slate800,
    error = Rose200,
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = 0.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** Tabular digits so the countdown does not jitter. */
val CountdownTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    letterSpacing = 1.sp,
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Minimum accessible touch target. */
    val touchTarget = 48.dp
}

@Composable
fun FocusLockTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** The lockdown screen is always dark: calmer and less attention-grabbing. */
@Composable
fun LockdownTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}
