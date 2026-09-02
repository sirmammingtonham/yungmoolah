package com.yungmoolah.converter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Jade40,
    onPrimary = Color.White,
    primaryContainer = Jade90,
    onPrimaryContainer = Jade10,
    secondary = Jade30,
    onSecondary = Color.White,
    secondaryContainer = Jade95,
    onSecondaryContainer = Jade10,
    tertiary = Brass40,
    onTertiary = Color.White,
    tertiaryContainer = Brass90,
    onTertiaryContainer = Brass30,
    background = SurfaceLight,
    onBackground = Slate10,
    surface = SurfaceLight,
    onSurface = Slate10,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = Color(0xFF404944),
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = Color(0xFFE4ECE6),
    outline = OutlineLight,
    outlineVariant = Color(0xFFC6D0CA),
    error = ErrorRed40,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Jade80,
    onPrimary = Jade10,
    primaryContainer = Jade30,
    onPrimaryContainer = Jade90,
    secondary = Jade80,
    onSecondary = Jade10,
    secondaryContainer = Jade20,
    onSecondaryContainer = Jade90,
    tertiary = Brass80,
    onTertiary = Brass30,
    tertiaryContainer = Brass30,
    onTertiaryContainer = Brass90,
    background = SurfaceDark,
    onBackground = Slate90,
    surface = SurfaceDark,
    onSurface = Slate90,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF242D28),
    outline = OutlineDark,
    outlineVariant = Color(0xFF3F4844),
    error = ErrorRed80,
    onError = Color(0xFF690005),
)

@Composable
fun MoolahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MoolahTypography,
        content = content,
    )
}
