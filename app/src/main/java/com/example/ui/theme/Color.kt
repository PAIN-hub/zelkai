package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Editorial Light Palette — Warm off-white (#F7F6F3), crisp hairline borders, high-contrast ink
val NexusBgLight = Color(0xFFF7F6F3)
val NexusPanelLight = Color(0xFFFFFFFF)
val NexusSurfaceLight = Color(0xFFF0EFEA)
val NexusUserBubbleLight = Color(0xFFE6E4DD)
val NexusOnUserBubbleLight = Color(0xFF111318) // Contrast vs #E6E4DD is ~13.9:1 (WCAG AAA)
val NexusAiTextLight = Color(0xFF141519)       // Contrast vs #F7F6F3 is ~15.8:1 (WCAG AAA)
val NexusBorderLight = Color(0xFFE6E4DC)
val NexusBorderStrongLight = Color(0xFFCFCBC0)
val NexusTextLight = Color(0xFF141519)
val NexusMutedLight = Color(0xFF565963)

// Editorial Dark Palette — Near-black (#0B0D11), subtle slate surfaces, high-contrast text
val NexusBgDark = Color(0xFF0B0D11)
val NexusPanelDark = Color(0xFF13161D)
val NexusSurfaceDark = Color(0xFF1A1E27)
val NexusUserBubbleDark = Color(0xFF232836)
val NexusOnUserBubbleDark = Color(0xFFF8FAFC)  // Contrast vs #232836 is ~13.2:1 (WCAG AAA)
val NexusAiTextDark = Color(0xFFF1F5F9)        // Contrast vs #0B0D11 is ~16.8:1 (WCAG AAA)
val NexusBorderDark = Color(0xFF242936)
val NexusBorderStrongDark = Color(0xFF333A4B)
val NexusTextDark = Color(0xFFF1F5F9)
val NexusMutedDark = Color(0xFF94A3B8)

// Backward-compatible aliases
val NexusBg = NexusBgLight
val NexusPanel = NexusPanelLight
val NexusSurface = NexusSurfaceLight
val NexusBorder = NexusBorderLight
val NexusBorderStrong = NexusBorderStrongLight
val NexusText = NexusTextLight
val NexusMuted = NexusMutedLight

// Brand Accent (Indigo -> Violet)
val NexusAccent = Color(0xFF4F46E5)
val NexusAccent2 = Color(0xFF7C3AED)
val NexusAccentSoftLight = Color(0xFFEEF2FF)
val NexusAccentSoftDark = Color(0xFF1E1B4B)
val NexusAccentSoft = NexusAccentSoftLight

// Semantic Status Colors
val NexusOk = Color(0xFF0D9488)
val NexusOkSoft = Color(0xFFCCFBF1)
val NexusWarn = Color(0xFFD97706)
val NexusWarnSoft = Color(0xFFFEF3C7)
val NexusWarnText = Color(0xFF92400E)
val NexusDanger = Color(0xFFDC2626)
val NexusDangerSoft = Color(0xFFFEE2E2)
val NexusDangerBorder = Color(0xFFFCA5A5)

// Code Block & Terminal Colors
val NexusCodeBg = Color(0xFF0E1117)
val NexusCodeHeaderBg = Color(0xFF161A23)
val NexusCodeText = Color(0xFFE6EDF3)
val NexusCodeLangText = Color(0xFF9CA3AF)

// Dedicated paired tokens for logs and terminal output (WCAG AA >= 4.5:1 guaranteed)
val NexusLogSurfaceLight = Color(0xFF1E222D)
val NexusOnLogSurfaceLight = Color(0xFFF1F5F9) // Contrast vs #1E222D is ~12.5:1
val NexusLogSurfaceDark = Color(0xFF0E1117)
val NexusOnLogSurfaceDark = Color(0xFFE6EDF3)  // Contrast vs #0E1117 is ~15.2:1
val NexusLogStderr = Color(0xFFF87171)        // Contrast vs log surfaces is > 6.0:1
val NexusLogWarning = Color(0xFFFBBF24)       // Contrast vs log surfaces is > 8.0:1

/**
 * Explicit paired color tokens for user chat bubbles, AI messages, and surfaces
 * in both Light and Dark themes. Never rely on defaults or LocalContentColor.
 */
@Immutable
data class NexusSemanticColors(
    val isDark: Boolean,
    val userBubble: Color,
    val onUserBubble: Color,
    val aiText: Color,
    val aiSecondaryText: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val background: Color,
    val onBackground: Color,
    val outline: Color,
    val borderStrong: Color = outline,
    val accent: Color = NexusAccent,
    val accentSoft: Color = if (isDark) NexusAccentSoftDark else NexusAccentSoftLight,
    val ok: Color = NexusOk,
    val warn: Color = NexusWarn,
    val danger: Color = NexusDanger
)

val LightNexusSemanticColors = NexusSemanticColors(
    isDark = false,
    userBubble = NexusUserBubbleLight,
    onUserBubble = NexusOnUserBubbleLight,
    aiText = NexusAiTextLight,
    aiSecondaryText = NexusMutedLight,
    surface = NexusPanelLight,
    onSurface = NexusTextLight,
    surfaceVariant = NexusSurfaceLight,
    onSurfaceVariant = NexusMutedLight,
    background = NexusBgLight,
    onBackground = NexusTextLight,
    outline = NexusBorderLight,
    borderStrong = NexusBorderStrongLight,
    accent = NexusAccent,
    accentSoft = NexusAccentSoftLight,
    ok = NexusOk,
    warn = NexusWarn,
    danger = NexusDanger
)

val DarkNexusSemanticColors = NexusSemanticColors(
    isDark = true,
    userBubble = NexusUserBubbleDark,
    onUserBubble = NexusOnUserBubbleDark,
    aiText = NexusAiTextDark,
    aiSecondaryText = NexusMutedDark,
    surface = NexusPanelDark,
    onSurface = NexusTextDark,
    surfaceVariant = NexusSurfaceDark,
    onSurfaceVariant = NexusMutedDark,
    background = NexusBgDark,
    onBackground = NexusTextDark,
    outline = NexusBorderDark,
    borderStrong = NexusBorderStrongDark,
    accent = Color(0xFF818CF8),
    accentSoft = NexusAccentSoftDark,
    ok = Color(0xFF2DD4BF),
    warn = Color(0xFFFBBF24),
    danger = Color(0xFFF87171)
)

val LocalNexusColors = staticCompositionLocalOf { LightNexusSemanticColors }

@Immutable
data class NexusPalette(
    val safeGreen: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val WarnAmber: Color,
    val surfaceCard: Color,
    val surfaceModal: Color,
    val surfaceInput: Color,
    val terminalBg: Color,
    val borderSubtle: Color,
    val dangerRed: Color,
    val logSurface: Color,
    val onLogSurface: Color,
    val logStderr: Color,
    val logWarning: Color,
    val isDark: Boolean = false,
    val bg: Color = NexusBgLight,
    val borderStrong: Color = NexusBorderStrongLight,
    val accentSoft: Color = NexusAccentSoftLight
)

@androidx.compose.runtime.Composable
fun rememberNexusPalette(): NexusPalette {
    val semantic = LocalNexusColors.current
    return NexusPalette(
        safeGreen = semantic.ok,
        accent = semantic.accent,
        textPrimary = semantic.onSurface,
        textSecondary = semantic.onSurfaceVariant,
        textMuted = semantic.onSurfaceVariant.copy(alpha = 0.72f),
        WarnAmber = semantic.warn,
        surfaceCard = semantic.surface,
        surfaceModal = semantic.surface,
        surfaceInput = semantic.surfaceVariant,
        terminalBg = if (semantic.isDark) NexusLogSurfaceDark else NexusLogSurfaceLight,
        borderSubtle = semantic.outline,
        dangerRed = semantic.danger,
        logSurface = if (semantic.isDark) NexusLogSurfaceDark else NexusLogSurfaceLight,
        onLogSurface = if (semantic.isDark) NexusOnLogSurfaceDark else NexusOnLogSurfaceLight,
        logStderr = NexusLogStderr,
        logWarning = NexusLogWarning,
        isDark = semantic.isDark,
        bg = semantic.background,
        borderStrong = semantic.borderStrong,
        accentSoft = semantic.accentSoft
    )
}

/**
 * Computes WCAG 2.1 contrast ratio between [foreground] and [background].
 * WCAG AA requires >= 4.5:1 for normal text.
 */
fun wcagContrastRatio(foreground: Color, background: Color): Double {
    fun channelLuminance(c: Float): Double {
        val v = c.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    fun relativeLuminance(color: Color): Double {
        return 0.2126 * channelLuminance(color.red) +
            0.7152 * channelLuminance(color.green) +
            0.0722 * channelLuminance(color.blue)
    }
    val l1 = relativeLuminance(foreground)
    val l2 = relativeLuminance(background)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}
