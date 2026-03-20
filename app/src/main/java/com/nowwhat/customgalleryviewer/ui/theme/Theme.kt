package com.nowwhat.customgalleryviewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color.Black,
    primaryContainer = AccentCyan.copy(0.15f),
    onPrimaryContainer = AccentCyan,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = AccentPurple.copy(0.15f),
    onSecondaryContainer = AccentPurple,
    tertiary = AccentCyan,
    background = DarkSurface,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color.White.copy(0.6f),
    error = ErrorRed,
    onError = Color.White,
    outline = Color.White.copy(0.15f),
    outlineVariant = Color.White.copy(0.08f),
    inverseSurface = Color.White,
    inverseOnSurface = DarkSurface,
    surfaceTint = AccentCyan
)

@Composable
fun CustomGalleryViewerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
