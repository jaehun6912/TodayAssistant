package com.example.galaxycalendarprobe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondaryContainer = Color(0xFFDCE2F2),
    surfaceVariant = Color(0xFFE8EAF1),
    onSurfaceVariant = Color(0xFF45464F),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    primaryContainer = Color(0xFF164584),
    secondaryContainer = Color(0xFF414754),
    background = Color(0xFF101116),
    surface = Color(0xFF101116),
    surfaceVariant = Color(0xFF30323A),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outlineVariant = Color(0xFF45464F),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun GalaxyCalendarProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = Typography(),
        content = content,
    )
}
