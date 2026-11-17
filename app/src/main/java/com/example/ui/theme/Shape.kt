package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized Material 3 shape scale for Zelkai surfaces, cards, pills, and sheets.
 */
val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Standardized spacing, minimum accessible touch target, and responsive reading widths.
 */
@Immutable
object NexusSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Minimum accessible interactive target size per Android / WCAG guidelines. */
    val minTouchTarget: Dp = 48.dp

    /** Responsive maximum reading/content column widths per viewport tier. */
    val readingMeasureCompact: Dp = 600.dp
    val readingMeasureMedium: Dp = 680.dp
    val readingMeasureExpanded: Dp = 780.dp
}

/**
 * Standardized elevation scale matching the editorial hairline-border aesthetic.
 */
@Immutable
object NexusElevation {
    val none: Dp = 0.dp
    val subtle: Dp = 1.dp
    val card: Dp = 2.dp
    val floating: Dp = 4.dp
    val modal: Dp = 8.dp
}

/**
 * 3-tier responsive width breakpoints:
 * - Compact: < 600.dp (phones in portrait)
 * - Medium: 600.dp .. 839.dp (foldables / small tablets)
 * - Expanded: >= 840.dp (tablets / large landscape viewports)
 */
enum class WindowWidthTier {
    Compact,
    Medium,
    Expanded
}

fun windowWidthTierFor(maxWidth: Dp): WindowWidthTier = when {
    maxWidth < 600.dp -> WindowWidthTier.Compact
    maxWidth < 840.dp -> WindowWidthTier.Medium
    else -> WindowWidthTier.Expanded
}

val LocalWindowWidthTier = staticCompositionLocalOf { WindowWidthTier.Compact }

@Composable
@ReadOnlyComposable
fun rememberWindowWidthTier(): WindowWidthTier {
    val widthDp = LocalConfiguration.current.screenWidthDp.dp
    return windowWidthTierFor(widthDp)
}
