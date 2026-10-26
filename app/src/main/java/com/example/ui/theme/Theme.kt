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

private val LightSkyColorScheme = lightColorScheme(
    primary = SkyBluePrimary,
    onPrimary = Color.White,
    primaryContainer = SkyBluePrimaryContainer,
    onPrimaryContainer = SkyBlueOnPrimaryContainer,
    secondary = SkyBluePrimaryHover,
    onSecondary = Color.White,
    secondaryContainer = SkyBluePrimaryContainer,
    onSecondaryContainer = SkyBlueOnPrimaryContainer,
    tertiary = SkyBluePrimaryLight,
    onTertiary = SkyBlueTextPrimary,
    background = SkyBlueBackground,
    onBackground = SkyBlueTextPrimary,
    surface = SkyBlueSurface,
    onSurface = SkyBlueTextPrimary,
    surfaceVariant = SkyBlueSurfaceVariant,
    onSurfaceVariant = SkyBlueTextSecondary,
    outline = SkyBlueSurfaceBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusOnErrorContainer
)

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
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent Sky Blue palette across Android versions
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightSkyColorScheme // Clean Light Sky Blue Theme by default
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

