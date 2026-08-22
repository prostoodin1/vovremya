package com.vovremya.alarm.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.vovremya.alarm.data.AccentTheme
import com.vovremya.alarm.data.BackgroundStyle
import com.vovremya.alarm.data.ThemeMode

val Violet = Color(0xFF6558D3)
val Mint = Color(0xFF35B69F)
val Ink = Color(0xFF1D1A2F)
val SoftBackground = Color(0xFFF8F7FC)
val NightBackground = Color(0xFF12111A)

private data class AccentPalette(
    val light: Color,
    val onLight: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
)

fun AccentTheme.previewColor(customAccentColor: Int = 0xFF6558D3.toInt()): Color = when (this) {
    AccentTheme.VIOLET -> Violet
    AccentTheme.BLUE -> Color(0xFF3266C5)
    AccentTheme.GREEN -> Color(0xFF287D50)
    AccentTheme.ORANGE -> Color(0xFFAA5A12)
    AccentTheme.ROSE -> Color(0xFFAD3E66)
    AccentTheme.TEAL -> Color(0xFF087A75)
    AccentTheme.RED -> Color(0xFFB3261E)
    AccentTheme.AMBER -> Color(0xFFE28A00)
    AccentTheme.LIME -> Color(0xFF5D7E16)
    AccentTheme.CYAN -> Color(0xFF007C91)
    AccentTheme.INDIGO -> Color(0xFF4338CA)
    AccentTheme.GRAPHITE -> Color(0xFF535866)
    AccentTheme.CUSTOM -> Color(customAccentColor)
}

private fun AccentTheme.palette(customAccentColor: Int): AccentPalette {
    val seed = previewColor(customAccentColor)
    val lightContainer = lerp(seed, Color.White, .78f)
    val dark = lerp(seed, Color.White, .42f)
    val darkContainer = lerp(seed, Color.Black, .28f)
    return AccentPalette(
        light = seed,
        onLight = contentColorFor(seed),
        lightContainer = lightContainer,
        onLightContainer = contentColorFor(lightContainer),
        dark = dark,
        onDark = contentColorFor(dark),
        darkContainer = darkContainer,
        onDarkContainer = contentColorFor(darkContainer),
    )
}

private fun contentColorFor(background: Color): Color =
    if (background.luminance() > .48f) Color(0xFF17151D) else Color.White

private fun lightColors(
    accent: AccentTheme,
    customAccentColor: Int,
    backgroundStyle: BackgroundStyle,
) = accent.palette(customAccentColor).let { palette ->
    val background = when (backgroundStyle) {
        BackgroundStyle.TINTED -> lerp(palette.lightContainer, Color.White, .72f)
        else -> SoftBackground
    }
    val surface = when (backgroundStyle) {
        BackgroundStyle.TINTED -> lerp(palette.lightContainer, Color.White, .9f)
        else -> Color.White
    }
    val surfaceVariant = when (backgroundStyle) {
        BackgroundStyle.TINTED -> lerp(palette.lightContainer, Color.White, .54f)
        else -> Color(0xFFF0EEF7)
    }
    lightColorScheme(
        primary = palette.light,
        onPrimary = palette.onLight,
        primaryContainer = palette.lightContainer,
        onPrimaryContainer = palette.onLightContainer,
        secondary = Mint,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC8F3EA),
        onSecondaryContainer = Color(0xFF073B32),
        background = background,
        onBackground = Ink,
        surface = surface,
        onSurface = Ink,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF666275),
        outline = Color(0xFF8F899C),
    )
}

private fun darkColors(
    accent: AccentTheme,
    customAccentColor: Int,
    backgroundStyle: BackgroundStyle,
) = accent.palette(customAccentColor).let { palette ->
    val background = when (backgroundStyle) {
        BackgroundStyle.TINTED -> lerp(palette.darkContainer, NightBackground, .72f)
        BackgroundStyle.AMOLED -> Color.Black
        BackgroundStyle.STANDARD -> NightBackground
    }
    val surface = when (backgroundStyle) {
        BackgroundStyle.TINTED -> lerp(palette.darkContainer, Color(0xFF1B1923), .82f)
        BackgroundStyle.AMOLED -> Color(0xFF08080B)
        BackgroundStyle.STANDARD -> Color(0xFF1B1923)
    }
    val surfaceVariant = when (backgroundStyle) {
        BackgroundStyle.TINTED -> lerp(palette.darkContainer, Color(0xFF302E3A), .58f)
        BackgroundStyle.AMOLED -> Color(0xFF17171C)
        BackgroundStyle.STANDARD -> Color(0xFF302E3A)
    }
    darkColorScheme(
        primary = palette.dark,
        onPrimary = palette.onDark,
        primaryContainer = palette.darkContainer,
        onPrimaryContainer = palette.onDarkContainer,
        secondary = Color(0xFF78DAC7),
        onSecondary = Color(0xFF00382F),
        secondaryContainer = Color(0xFF005045),
        onSecondaryContainer = Color(0xFF9AF8E2),
        background = background,
        onBackground = Color(0xFFE7E1ED),
        surface = surface,
        onSurface = Color(0xFFE7E1ED),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFFCAC4D0),
    )
}

@Composable
private fun animateScheme(target: ColorScheme): ColorScheme {
    val animation = tween<Color>(durationMillis = 520, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, animation, label = "themePrimary")
    val onPrimary by animateColorAsState(target.onPrimary, animation, label = "themeOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, animation, label = "themePrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, animation, label = "themeOnPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, animation, label = "themeSecondary")
    val onSecondary by animateColorAsState(target.onSecondary, animation, label = "themeOnSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, animation, label = "themeSecondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, animation, label = "themeOnSecondaryContainer")
    val background by animateColorAsState(target.background, animation, label = "themeBackground")
    val onBackground by animateColorAsState(target.onBackground, animation, label = "themeOnBackground")
    val surface by animateColorAsState(target.surface, animation, label = "themeSurface")
    val onSurface by animateColorAsState(target.onSurface, animation, label = "themeOnSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animation, label = "themeSurfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, animation, label = "themeOnSurfaceVariant")
    val outline by animateColorAsState(target.outline, animation, label = "themeOutline")
    val error by animateColorAsState(target.error, animation, label = "themeError")
    val errorContainer by animateColorAsState(target.errorContainer, animation, label = "themeErrorContainer")
    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        errorContainer = errorContainer,
    )
}

@Composable
fun VovremyaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentTheme: AccentTheme = AccentTheme.VIOLET,
    customAccentColor: Int = 0xFF6558D3.toInt(),
    backgroundStyle: BackgroundStyle = BackgroundStyle.STANDARD,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit,
) {
    val target = if (darkTheme) {
        darkColors(accentTheme, customAccentColor, backgroundStyle)
    } else {
        lightColors(accentTheme, customAccentColor, backgroundStyle)
    }
    MaterialTheme(
        colorScheme = animateScheme(target),
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
