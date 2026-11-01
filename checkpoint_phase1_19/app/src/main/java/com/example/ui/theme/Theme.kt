package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LightSkyColorScheme = lightColorScheme(
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

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightSkyColorScheme,
        typography = Typography,
        content = content
    )
}

