package com.daisychain.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.daisychain.app.data.ThemeMode

val Teal = Color(0xFF0B6B5D)
val Indigo = Color(0xFF4A4DB5)
val Amber = Color(0xFFA8560C)
val Slate = Color(0xFF3E4A52)
val Blue = Color(0xFF1F5FA8)
val Plum = Color(0xFF8B3A82)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBE5),
    onPrimaryContainer = Color(0xFF00201B),
    secondaryContainer = Color(0xFFD3EBE5),
    onSecondaryContainer = Color(0xFF00201B),
    background = Color(0xFFF2F4F2),
    onBackground = Color(0xFF151A19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A19),
    surfaceVariant = Color(0xFFE4E9E6),
    onSurfaceVariant = Color(0xFF4A5551),
    surfaceContainer = Color(0xFFE8EDEA),
    outline = Color(0xFFD5DBD8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CC9B4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0B6B5D),
    onPrimaryContainer = Color(0xFFD3EBE5),
    secondaryContainer = Color(0xFF1E3A35),
    onSecondaryContainer = Color(0xFFD3EBE5),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE3E9E6),
    surface = Color(0xFF1A1F1E),
    onSurface = Color(0xFFE3E9E6),
    surfaceVariant = Color(0xFF2A302E),
    onSurfaceVariant = Color(0xFFB5BFBB),
    surfaceContainer = Color(0xFF181D1C),
    outline = Color(0xFF3A423F),
)

@Composable
fun DaisychainTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
