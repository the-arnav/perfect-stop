package com.example.perfectstop.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PerfectStopTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val bg = if (darkTheme) Color.Black else Color.White
    val fg = if (darkTheme) Color.White else Color.Black
    val muted = if (darkTheme) Color(0xFFADADAD) else Color(0xFF595959)
    val surface = if (darkTheme) Color(0xFF161616) else Color(0xFFF2F2F2)
    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(primary = fg, onPrimary = bg,
        secondary = fg, onSecondary = bg, tertiary = fg, onTertiary = bg,
        background = bg, onBackground = fg, surface = bg, onSurface = fg,
        surfaceVariant = surface, onSurfaceVariant = muted, surfaceTint = Color.Transparent,
        primaryContainer = surface, onPrimaryContainer = fg,
        secondaryContainer = surface, onSecondaryContainer = fg,
        tertiaryContainer = surface, onTertiaryContainer = fg,
        inverseSurface = fg, inverseOnSurface = bg, inversePrimary = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        outline = muted, outlineVariant = if (darkTheme) Color(0xFF363636) else Color(0xFFD5D5D5),
        error = if (darkTheme) Color(0xFFFF8078) else Color(0xFFB3261E)),
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp)),
        typography = Typography, content = content)
}
