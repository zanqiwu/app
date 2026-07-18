package com.opendroid.ai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * OpenDroid color palette — resolved via CompositionLocal so all screens
 * automatically adapt to the active theme (light / dark).
 */
data class OpenDroidColors(
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val borderColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentNeonGreen: Color,
    val accentPurple: Color,
    val accentCyan: Color,
    val accentRed: Color,
    val isDark: Boolean
)

// ── Dark palette (the existing one) ─────────────────────────
val DarkPalette = OpenDroidColors(
    background = Color(0xFF0E1324),
    surface = Color(0xFF131A2D),
    cardBackground = Color(0xFF1A2237),
    borderColor = Color(0xFF2D3754),
    textPrimary = Color(0xFFF4F7FF),
    textSecondary = Color(0xFFAAB4CC),
    accentNeonGreen = Color(0xFF5F7EDB),
    accentPurple = Color(0xFF7A5EDB),
    accentCyan = Color(0xFF6FA9FF),
    accentRed = Color(0xFFFF3B30),
    isDark = true
)

// ── Light palette ───────────────────────────────────────────
val LightPalette = OpenDroidColors(
    background = Color(0xFFF6F7FC),
    surface = Color(0xFFFFFFFF),
    cardBackground = Color(0xFFFFFFFF),
    borderColor = Color(0xFFD7DCEB),
    textPrimary = Color(0xFF1B2337),
    textSecondary = Color(0xFF667089),
    accentNeonGreen = Color(0xFF4E68B8),
    accentPurple = Color(0xFF6F55C8),
    accentCyan = Color(0xFF3678D8),
    accentRed = Color(0xFFCF222E),
    isDark = false
)

val LocalOpenDroidColors = compositionLocalOf { LightPalette }

/** Access the active palette from any @Composable */
object AppTheme {
    val colors: OpenDroidColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOpenDroidColors.current
}

// ── Legacy top-level aliases ────────────────────────────────
// These keep every existing screen compiling without changes.
// They delegate to the composition-local palette at read-time.

// NOTE: These are static vals used outside @Composable scope.
// For full dynamic theming in screens that read these outside Compose,
// they keep the dark defaults. Inside @Composable, use AppTheme.colors.*
val DarkBackground = LightPalette.background
val DarkSurface = LightPalette.surface
val CardBackground = LightPalette.cardBackground
val BorderColor = LightPalette.borderColor
val TextPrimary = LightPalette.textPrimary
val TextSecondary = LightPalette.textSecondary
val AccentNeonGreen = LightPalette.accentNeonGreen
val AccentPurple = LightPalette.accentPurple
val AccentCyan = LightPalette.accentCyan
val AccentRed = DarkPalette.accentRed
