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
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003731),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFA7F3D0),

    secondary = IndigoSecondaryDark,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = Color(0xFFC7D2FE),

    tertiary = EmeraldSuccessDark,
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),

    background = Slate900,
    onBackground = Slate100,
    surface = Slate800,
    onSurface = Slate100,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate200,

    error = RoseErrorDark,
    onError = Color(0xFF4C0519),
    errorContainer = RoseContainerDark,
    onErrorContainer = Color(0xFFFECDD3)
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF115E59),

    secondary = IndigoSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = Color(0xFF3730A3),

    tertiary = EmeraldSuccessLight,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF065F46),

    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,

    error = RoseErrorLight,
    onError = Color.White,
    errorContainer = RoseContainerLight,
    onErrorContainer = Color(0xFF9F1239)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our tailored theme for consistent high polish
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
