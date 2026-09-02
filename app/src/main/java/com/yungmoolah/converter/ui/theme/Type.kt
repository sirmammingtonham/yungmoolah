package com.yungmoolah.converter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yungmoolah.converter.R

/**
 * Inter, subset to the characters this app renders and instanced at three static
 * weights. Anything outside that subset — flag emoji, the Arabic and Devanagari
 * currency symbols — falls back to the system font, which is what should happen.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * Tight tracking on the large sizes and roomier tracking on the small ones: Inter
 * is drawn for that, and it is most of what separates a considered screen from a
 * default one.
 */
val MoolahTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Inter,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * The per-row unit rate. Tabular figures keep the decimals in a column as rates
 * change; applying `tnum` to every small label instead would also monospace
 * punctuation and leave visible gaps around hyphens in prose.
 */
val RateLabelStyle = TextStyle(
    fontFamily = Inter,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum",
)

/**
 * The style used for every editable amount.
 *
 * `tnum` gives fixed-width digits; without it every keystroke re-measures the
 * number and the row visibly jitters as it updates.
 */
val AmountTextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = 27.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
)
