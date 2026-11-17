package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Bundled Poppins font family (Regular, Medium, SemiBold) stored in res/font.
 * Works 100% offline with zero network font loading.
 */
val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold)
)

// Backward-compatible aliases mapped to Poppins so all UI & chat surfaces use Poppins
val DmSansFontFamily = PoppinsFontFamily
val InterFontFamily = PoppinsFontFamily
val EditorialSerifFontFamily = PoppinsFontFamily

// Monospace font strictly reserved for Terminal CLI and fenced code blocks
val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

fun scaledTypography(scaleFactor: Float): Typography {
    val s = scaleFactor.coerceIn(0.85f, 1.25f)
    if (s == 1.0f) return Typography
    fun TextStyle.scale(): TextStyle = copy(
        fontSize = (fontSize.value * s).sp,
        lineHeight = (lineHeight.value * s).sp
    )
    return Typography(
        displaySmall = Typography.displaySmall.scale(),
        headlineSmall = Typography.headlineSmall.scale(),
        titleLarge = Typography.titleLarge.scale(),
        titleMedium = Typography.titleMedium.scale(),
        titleSmall = Typography.titleSmall.scale(),
        bodyLarge = Typography.bodyLarge.scale(),
        bodyMedium = Typography.bodyMedium.scale(),
        bodySmall = Typography.bodySmall.scale(),
        labelLarge = Typography.labelLarge.scale(),
        labelMedium = Typography.labelMedium.scale(),
        labelSmall = Typography.labelSmall.scale()
    )
}
