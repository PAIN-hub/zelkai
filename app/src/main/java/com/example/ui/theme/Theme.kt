package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

enum class NexusThemeMode(val id: String, val label: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System");

    companion object {
        fun fromId(raw: String?): NexusThemeMode {
            return entries.find { it.id.equals(raw, ignoreCase = true) } ?: LIGHT
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = NexusAccent,
    onPrimary = Color.White,
    primaryContainer = NexusAccentSoftLight,
    onPrimaryContainer = Color(0xFF3730A3),
    secondary = NexusAccent2,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF581C87),
    tertiary = NexusOk,
    onTertiary = Color.White,
    background = NexusBgLight,
    onBackground = NexusTextLight,
    surface = NexusPanelLight,
    onSurface = NexusTextLight,
    surfaceVariant = NexusSurfaceLight,
    onSurfaceVariant = NexusMutedLight,
    outline = NexusBorderLight,
    outlineVariant = NexusBorderStrongLight,
    error = NexusDanger,
    onError = Color.White,
    errorContainer = NexusDangerSoft,
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = NexusAccentSoftDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF0F172A),
    background = NexusBgDark,
    onBackground = NexusTextDark,
    surface = NexusPanelDark,
    onSurface = NexusTextDark,
    surfaceVariant = NexusSurfaceDark,
    onSurfaceVariant = NexusMutedDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
    outline = NexusBorderDark,
    outlineVariant = NexusBorderStrongDark
)

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 220)
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "onSecondary")
    val background by animateColorAsState(target.background, spec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "onBackground")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant")
    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "outlineVariant")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant
    )
}

object NexusThemeTokens {
    val colors: NexusSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusColors.current

    val spacing: NexusSpacing
        get() = NexusSpacing

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes

    val elevation: NexusElevation
        get() = NexusElevation

    val widthTier: WindowWidthTier
        @Composable
        @ReadOnlyComposable
        get() = LocalWindowWidthTier.current
}

@Composable
fun NexusTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        fontScale = fontScale,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val targetScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkNexusSemanticColors else LightNexusSemanticColors
    val animatedScheme = animateColorScheme(targetScheme)
    val activeTypography = scaledTypography(fontScale)
    val widthTier = rememberWindowWidthTier()

    CompositionLocalProvider(
        LocalNexusColors provides semanticColors,
        LocalWindowWidthTier provides widthTier
    ) {
        MaterialTheme(
            colorScheme = animatedScheme,
            shapes = NexusShapes,
            typography = activeTypography
        ) {
            ProvideSafeTextToolbar {
                content()
            }
        }
    }
}
