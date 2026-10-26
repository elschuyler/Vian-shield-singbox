package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ShieldPrimaryCyan,
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF80F5FD),
    secondary = ShieldSecondarySky,
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D68),
    tertiary = ShieldTertiaryIndigo,
    background = ShieldDarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = ShieldDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = ShieldDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ShieldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = ShieldPrimaryCyan,
    background = ShieldLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = ShieldLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = ShieldLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    error = StatusError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent cyber-shield palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Prefer high-contrast dark theme by default for firewall/network security console
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
