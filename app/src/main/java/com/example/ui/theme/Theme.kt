package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = OnCyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = CyberBackground,
    onBackground = OnCyberBackground,
    surface = CyberSurface,
    onSurface = OnCyberSurface,
    surfaceVariant = CyberSurfaceVariant,
    outline = CyberBorder
)

private val LightColorScheme = DarkColorScheme // Keep consistent premium dark theme for the studio workspace

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for cinematic dark atmosphere
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce the branded neon comedy club aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme // Enforce the creative signature theme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
