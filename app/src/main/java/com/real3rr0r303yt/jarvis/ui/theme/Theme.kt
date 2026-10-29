package com.real3rr0r303yt.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Jarvis palette — pitch black background, neon-green foreground.
val JarvisBlack = Color(0xFF000000)
val JarvisSurface = Color(0xFF0A0F0A)
val JarvisNeon = Color(0xFF00FF66)
val JarvisNeonDim = Color(0xFF00A344)
val JarvisAmber = Color(0xFFFFB300)
val JarvisRed = Color(0xFFFF3B3B)

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisNeon,
    onPrimary = JarvisBlack,
    secondary = JarvisNeonDim,
    background = JarvisBlack,
    onBackground = JarvisNeon,
    surface = JarvisSurface,
    onSurface = JarvisNeon,
    error = JarvisRed,
)

// Monospace everywhere for the terminal/HUD aesthetic.
private val MonoTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

@Composable
fun JarvisTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Jarvis is always dark — the HUD only makes sense in black/neon.
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = MonoTypography,
        content = content,
    )
}
