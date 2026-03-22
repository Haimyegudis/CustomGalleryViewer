package com.nowwhat.customgalleryviewer.ui.theme

import androidx.compose.ui.graphics.Color

val AccentCyan = Color(0xFF00E5FF)
val AccentPurple = Color(0xFF6C63FF)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E2E)
val DarkCard = Color(0xFF252536)
val ErrorRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF4CAF50)

// Accent color palettes
val AccentGreen = Color(0xFF00E676)
val AccentOrange = Color(0xFFFF9100)
val AccentPink = Color(0xFFFF4081)
val AccentBlue = Color(0xFF448AFF)
val AccentYellow = Color(0xFFFFD740)
val AccentRed = Color(0xFFFF5252)

// Light theme colors
val LightSurface = Color(0xFFF5F5F5)
val LightSurfaceVariant = Color(0xFFE0E0E0)
val LightBackground = Color(0xFFFFFFFF)

// AMOLED
val AmoledBlack = Color(0xFF000000)
val AmoledSurfaceVariant = Color(0xFF0A0A0A)

fun getAccentColor(name: String): Color {
    return when (name) {
        "cyan" -> AccentCyan
        "purple" -> AccentPurple
        "green" -> AccentGreen
        "orange" -> AccentOrange
        "pink" -> AccentPink
        "blue" -> AccentBlue
        "yellow" -> AccentYellow
        "red" -> AccentRed
        else -> AccentCyan
    }
}

val accentColorOptions = listOf(
    "cyan" to AccentCyan,
    "purple" to AccentPurple,
    "green" to AccentGreen,
    "orange" to AccentOrange,
    "pink" to AccentPink,
    "blue" to AccentBlue,
    "yellow" to AccentYellow,
    "red" to AccentRed
)
