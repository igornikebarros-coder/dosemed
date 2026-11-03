package com.igorbarros.dosemed

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF12A594)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B7A6E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF2EC), onPrimaryContainer = Color(0xFF00382F),
    secondary = Color(0xFF4A635F), tertiary = Color(0xFF3B6EA5),
    background = Color(0xFFF3F8F7), surface = Color(0xFFF3F8F7),
    surfaceVariant = Color(0xFFE2ECEA), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FD8C4), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0A4F46), onPrimaryContainer = Color(0xFFCDF2EC),
    secondary = Color(0xFFB0CCC6), tertiary = Color(0xFF9CC4F0),
    background = Color(0xFF101817), surface = Color(0xFF101817),
    surfaceVariant = Color(0xFF2A3735), surfaceContainer = Color(0xFF18221F),
    surfaceContainerHigh = Color(0xFF1E2926), surfaceContainerLow = Color(0xFF18221F),
)
private val AmoledScheme = DarkScheme.copy(
    background = Color.Black, surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A), surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF141414), surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainerHighest = Color(0xFF1A1A1A), surfaceContainerLowest = Color.Black,
    primaryContainer = Color(0xFF063D36),
)

@Composable
fun DosemedTheme(mode: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when (mode) {
        "light" -> LightScheme
        "dark" -> DarkScheme
        "amoled" -> AmoledScheme
        else -> if (dark) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
