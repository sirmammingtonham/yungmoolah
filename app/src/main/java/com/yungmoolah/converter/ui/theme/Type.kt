package com.yungmoolah.converter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Monospaced-width digits. Without this, every keystroke re-measures the amount
 * and the numbers visibly jitter as they update.
 */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

private val default = Typography()

val MoolahTypography = Typography(
    displaySmall = default.displaySmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = default.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = default.labelSmall.copy(
        fontFamily = FontFamily.Default,
        letterSpacing = 0.2.sp,
    ),
)

/** The style used for every editable amount. */
val AmountTextStyle = TextStyle(
    fontSize = 26.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
)
