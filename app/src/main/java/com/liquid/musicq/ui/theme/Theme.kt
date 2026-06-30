package com.liquid.musicq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF7C5CFF)
val AccentAlt = Color(0xFF2BD9FE)
val GlassTint = Color(0x33FFFFFF)
val GlassStroke = Color(0x4DFFFFFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = AccentAlt,
    background = Color(0xFF0B0B14),
    surface = Color(0xFF14141F),
    onPrimary = Color.White,
    onBackground = Color(0xFFEDEDF5),
    onSurface = Color(0xFFEDEDF5)
)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = AccentAlt
)

@Composable
fun LiquidMusicQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
