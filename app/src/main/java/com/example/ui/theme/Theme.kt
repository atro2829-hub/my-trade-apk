package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmcDarkColorScheme = darkColorScheme(
    primary = SmcGoldAccent,
    onPrimary = SmcNavyDark,
    secondary = SmcInstitutionalBlue,
    onSecondary = SmcTextPrimary,
    tertiary = SmcBullishEmerald,
    background = SmcNavyDark,
    onBackground = SmcTextPrimary,
    surface = SmcSlateSurface,
    onSurface = SmcTextPrimary,
    surfaceVariant = SmcSlateCard,
    onSurfaceVariant = SmcTextSecondary,
    error = SmcBearishCrimson
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SmcDarkColorScheme,
        typography = Typography,
        content = content
    )
}
