package com.vovremya.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vovremya.alarm.data.AccentTheme
import com.vovremya.alarm.data.ThemeMode

val Violet = Color(0xFF6558D3)
val VioletDark = Color(0xFFCEC7FF)
val Mint = Color(0xFF35B69F)
val Ink = Color(0xFF1D1A2F)
val SoftBackground = Color(0xFFF8F7FC)
val NightBackground = Color(0xFF12111A)

private data class AccentPalette(
    val light: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
)

private fun AccentTheme.palette(): AccentPalette = when (this) {
    AccentTheme.VIOLET -> AccentPalette(Violet, Color(0xFFE7E2FF), Color(0xFF211A68), VioletDark, Color(0xFF493C92), Color(0xFFE7E2FF))
    AccentTheme.BLUE -> AccentPalette(Color(0xFF3266C5), Color(0xFFD9E7FF), Color(0xFF0B326A), Color(0xFFAEC6FF), Color(0xFF244881), Color(0xFFD9E7FF))
    AccentTheme.GREEN -> AccentPalette(Color(0xFF287D50), Color(0xFFC9F1D8), Color(0xFF073C22), Color(0xFF8DDBAA), Color(0xFF145D38), Color(0xFFC9F1D8))
    AccentTheme.ORANGE -> AccentPalette(Color(0xFFAA5A12), Color(0xFFFFDCC1), Color(0xFF5C2B00), Color(0xFFFFB77B), Color(0xFF7D4109), Color(0xFFFFDCC1))
    AccentTheme.ROSE -> AccentPalette(Color(0xFFAD3E66), Color(0xFFFFD9E3), Color(0xFF651635), Color(0xFFFFB0C8), Color(0xFF84284C), Color(0xFFFFD9E3))
    AccentTheme.TEAL -> AccentPalette(Color(0xFF087A75), Color(0xFFB7F1EC), Color(0xFF003E3B), Color(0xFF7EDBD5), Color(0xFF005E59), Color(0xFFB7F1EC))
}

private fun lightColors(accent: AccentTheme) = accent.palette().let { palette ->
    lightColorScheme(
        primary = palette.light,
        onPrimary = Color.White,
        primaryContainer = palette.lightContainer,
        onPrimaryContainer = palette.onLightContainer,
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
}

private fun darkColors(accent: AccentTheme) = accent.palette().let { palette ->
    darkColorScheme(
        primary = palette.dark,
        onPrimary = palette.onLightContainer,
        primaryContainer = palette.darkContainer,
        onPrimaryContainer = palette.onDarkContainer,
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
}

@Composable
fun VovremyaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentTheme: AccentTheme = AccentTheme.VIOLET,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors(accentTheme) else lightColors(accentTheme),
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
