package com.example.librusweekly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LibrusBlue = Color(0xFF2456D6)
private val LibrusBlueDark = Color(0xFF123692)

private val LightColors = lightColorScheme(
    primary = LibrusBlue,
    secondary = LibrusBlueDark,
    background = Color(0xFFF7F8FA),
    surface = Color.White
)

/// Light theme only for now, matching the iOS app.
@Composable
fun LibrusWeeklyTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
