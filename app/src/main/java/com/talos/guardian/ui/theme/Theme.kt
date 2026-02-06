package com.talos.guardian.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TalosGold,
    onPrimary = TalosNavyDark,
    primaryContainer = TalosNavyLight,
    onPrimaryContainer = White,
    secondary = TalosGoldDim,
    onSecondary = TalosNavyDark,
    tertiary = TalosSuccess,
    background = TalosNavyDark,
    onBackground = White,
    surface = TalosNavy,
    onSurface = White,
    error = TalosError
)

private val LightColorScheme = lightColorScheme(
    primary = TalosNavy,
    onPrimary = White,
    primaryContainer = TalosNavyLight,
    onPrimaryContainer = White,
    secondary = TalosGold,
    onSecondary = TalosNavyDark,
    tertiary = TalosSuccess,
    background = OffWhite,
    onBackground = TalosNavyDark,
    surface = White,
    onSurface = TalosNavyDark,
    error = TalosError
)

@Composable
fun TalosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to enforce Brand Identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TalosTypography,
        content = content
    )
}
