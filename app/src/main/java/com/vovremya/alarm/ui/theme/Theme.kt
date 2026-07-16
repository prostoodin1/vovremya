package com.vovremya.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Violet = Color(0xFF6558D3)
val VioletDark = Color(0xFFCEC7FF)
val Mint = Color(0xFF35B69F)
val Ink = Color(0xFF1D1A2F)
val SoftBackground = Color(0xFFF8F7FC)
val NightBackground = Color(0xFF12111A)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E2FF),
    onPrimaryContainer = Color(0xFF211A68),
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F3EA),
    onSecondaryContainer = Color(0xFF073B32),
    background = SoftBackground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EEF7),
    onSurfaceVariant = Color(0xFF666275),
    outline = Color(0xFF8F899C),
)

private val DarkColors = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color(0xFF342779),
    primaryContainer = Color(0xFF493C92),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Color(0xFF78DAC7),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005045),
    onSecondaryContainer = Color(0xFF9AF8E2),
    background = NightBackground,
    onBackground = Color(0xFFE7E1ED),
    surface = Color(0xFF1B1923),
    onSurface = Color(0xFFE7E1ED),
    surfaceVariant = Color(0xFF302E3A),
    onSurfaceVariant = Color(0xFFCAC4D0),
)

@Composable
fun VovremyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
