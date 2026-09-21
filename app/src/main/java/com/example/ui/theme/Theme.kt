package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = EmisBluePrimary,
    onPrimary = Color.White,
    primaryContainer = EmisBlueContainer,
    onPrimaryContainer = EmisOnBlueContainer,
    secondary = EmisSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmisSecondaryContainer,
    onSecondaryContainer = Color(0xFF0C3547),
    tertiary = EmisBlueDark,
    onTertiary = Color.White,
    background = EmisBackground,
    onBackground = EmisTextPrimary,
    surface = EmisSurface,
    onSurface = EmisTextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = EmisTextSecondary,
    outline = EmisCardBorder,
    error = EmisError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = EmisBlueLight,
    onPrimary = Color(0xFF012A4A),
    primaryContainer = Color(0xFF014F86),
    onPrimaryContainer = Color(0xFFE2F0F7),
    secondary = Color(0xFF89C2D9),
    onSecondary = Color(0xFF012A4A),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
