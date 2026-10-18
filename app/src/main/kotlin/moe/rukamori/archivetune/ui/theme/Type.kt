/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R

/**
 * iOS Dynamic Type scale (Large Title 34 / Title1 28 / Title2 22 / Title3 20 /
 * Headline 17 semibold / Body 17 / Callout 16 / Subhead 15 / Footnote 13 / Caption 12-11)
 * mapped onto Material slots. Bold titles + slightly negative tracking like SF Pro.
 */
private fun ios(
    fontFamily: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double,
) = TextStyle(
    fontFamily = fontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun buildTypography(fontFamily: FontFamily) =
    Typography(
        displayLarge = ios(fontFamily, 54, 60, FontWeight.Bold, -0.8),
        displayMedium = ios(fontFamily, 44, 50, FontWeight.Bold, -0.6),
        displaySmall = ios(fontFamily, 34, 41, FontWeight.Bold, -0.4), // Large Title
        headlineLarge = ios(fontFamily, 32, 39, FontWeight.Bold, -0.4),
        headlineMedium = ios(fontFamily, 28, 34, FontWeight.Bold, -0.3), // Title 1
        headlineSmall = ios(fontFamily, 22, 28, FontWeight.Bold, -0.2), // Title 2
        titleLarge = ios(fontFamily, 20, 25, FontWeight.SemiBold, -0.2), // Title 3
        titleMedium = ios(fontFamily, 17, 22, FontWeight.SemiBold, -0.3), // Headline
        titleSmall = ios(fontFamily, 15, 20, FontWeight.SemiBold, -0.2), // Subheadline (bold)
        bodyLarge = ios(fontFamily, 17, 22, FontWeight.Normal, -0.3), // Body
        bodyMedium = ios(fontFamily, 15, 20, FontWeight.Normal, -0.2), // Subheadline
        bodySmall = ios(fontFamily, 13, 18, FontWeight.Normal, -0.1), // Footnote
        labelLarge = ios(fontFamily, 15, 20, FontWeight.SemiBold, -0.2),
        labelMedium = ios(fontFamily, 12, 16, FontWeight.Medium, 0.0), // Caption 1
        labelSmall = ios(fontFamily, 11, 13, FontWeight.Medium, 0.05), // Caption 2
    )

// Default font: system sans (closest to SF Pro on Android) instead of Poppins.
val AppFontFamily: FontFamily = FontFamily.SansSerif
val LyricsFontFamily = FontFamily(Font(R.font.sfprodisplaybold))
val AppTypography = buildTypography(AppFontFamily)
val SystemTypography = buildTypography(FontFamily.Default)

fun typographyFor(fontFamily: FontFamily) = buildTypography(fontFamily)

