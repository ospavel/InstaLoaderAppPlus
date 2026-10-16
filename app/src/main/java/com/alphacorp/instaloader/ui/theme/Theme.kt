package com.alphacorp.instaloader.ui.theme

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

private val LightColors = lightColorScheme(
    primary = BrandMagenta,
    onPrimary = Color.White,
    primaryContainer = BrandMagentaLight,
    onPrimaryContainer = BrandMagentaDark,
    secondary = InstaPurple,
    onSecondary = Color.White,
    tertiary = InstaOrange,
    onTertiary = Color(0xFF24120A),
)

private val DarkColors = darkColorScheme(
    primary = BrandMagenta,
    onPrimary = Color.White,
    primaryContainer = BrandMagentaDark,
    onPrimaryContainer = BrandMagentaLight,
    secondary = InstaOrange,
    onSecondary = Color(0xFF24120A),
    tertiary = InstaPurple,
    onTertiary = Color.White,
)

@Composable
fun InstaLoaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
