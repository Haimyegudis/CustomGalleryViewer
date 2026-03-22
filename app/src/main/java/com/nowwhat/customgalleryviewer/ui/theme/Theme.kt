package com.nowwhat.customgalleryviewer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun contrastingColor(bg: Color): Color {
    val luminance = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (luminance > 0.35f) Color.Black else Color.White
}

private fun darkColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = contrastingColor(accent),
    primaryContainer = accent.copy(0.15f),
    onPrimaryContainer = accent,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = AccentPurple.copy(0.15f),
    onSecondaryContainer = AccentPurple,
    tertiary = accent,
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
    surfaceTint = accent
)

private fun lightColorScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(0.12f),
    onPrimaryContainer = accent,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = AccentPurple.copy(0.12f),
    onSecondaryContainer = AccentPurple,
    tertiary = accent,
    background = LightBackground,
    onBackground = Color.Black,
    surface = LightSurface,
    onSurface = Color.Black,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color.Black.copy(0.6f),
    error = ErrorRed,
    onError = Color.White,
    outline = Color.Black.copy(0.15f),
    outlineVariant = Color.Black.copy(0.08f),
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    surfaceTint = accent
)

private fun amoledColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = contrastingColor(accent),
    primaryContainer = accent.copy(0.15f),
    onPrimaryContainer = accent,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = AccentPurple.copy(0.15f),
    onSecondaryContainer = AccentPurple,
    tertiary = accent,
    background = AmoledBlack,
    onBackground = Color.White,
    surface = AmoledBlack,
    onSurface = Color.White,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = Color.White.copy(0.6f),
    error = ErrorRed,
    onError = Color.White,
    outline = Color.White.copy(0.15f),
    outlineVariant = Color.White.copy(0.08f),
    inverseSurface = Color.White,
    inverseOnSurface = AmoledBlack,
    surfaceTint = accent
)

@Composable
fun CustomGalleryViewerTheme(
    themeMode: String = "dark",
    accentColor: String = "cyan",
    content: @Composable () -> Unit
) {
    val accent = getAccentColor(accentColor)
    val isSystemDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        "light" -> lightColorScheme(accent)
        "amoled" -> amoledColorScheme(accent)
        "system" -> if (isSystemDark) darkColorScheme(accent) else lightColorScheme(accent)
        else -> darkColorScheme(accent) // "dark"
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    themeMode == "light" || (themeMode == "system" && !isSystemDark)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
