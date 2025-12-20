// FILE: app/src/main/java/com/toastedcoffee/bottlevault/ui/theme/Theme.kt
package com.toastedcoffee.bottlevault.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Custom color scheme for BottleVault
// Using wine/amber inspired colors for alcohol inventory theme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4AF37), // Gold
    onPrimary = Color(0xFF1C1B1F),
    primaryContainer = Color(0xFF8B4513), // Saddle Brown
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFF722F37), // Wine Red
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4A1E23),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFB8860B), // Dark Golden Rod
    onTertiary = Color(0xFF1C1B1F),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B4513), // Saddle Brown
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4AF37), // Gold
    onPrimaryContainer = Color(0xFF1C1B1F),
    secondary = Color(0xFF722F37), // Wine Red
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6E6),
    onSecondaryContainer = Color(0xFF4A1E23),
    tertiary = Color(0xFFB8860B), // Dark Golden Rod
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun BottleVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}