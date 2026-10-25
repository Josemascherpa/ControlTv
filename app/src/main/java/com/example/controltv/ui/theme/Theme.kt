package com.example.controltv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Blue40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Blue40,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Slate90,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = PaperCard,
    surfaceContainer = PaperDim,
    surfaceContainerHigh = PaperDim,
    surfaceContainerHighest = Slate90,
    outline = Outline40,
    outlineVariant = Outline40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Blue80,
    onSecondary = Blue20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90Dark,
    tertiary = Blue80,
    background = Night,
    onBackground = InkDark,
    surface = Night,
    onSurface = InkDark,
    surfaceVariant = Slate30,
    onSurfaceVariant = InkSoftDark,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightDim,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHigh,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
)

// Esquinas redondeadas parejas en toda la app.
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun ControlTvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
