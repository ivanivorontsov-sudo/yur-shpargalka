package org.ivanivorontsov.yur.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CriminalRed = Color(0xFFE85D5D)
val LargeOrange = Color(0xFFF0A05A)
val MidAmber = Color(0xFFE8C547)
val SmallGreen = Color(0xFF6BCB77)
val CardDark = Color(0xFF141C2E)
val BgDark = Color(0xFF0B1220)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8C547),
    onPrimary = Color(0xFF1A1400),
    secondary = Color(0xFF7EB6FF),
    background = BgDark,
    surface = CardDark,
    onBackground = Color(0xFFE8EEF8),
    onSurface = Color(0xFFE8EEF8),
    surfaceVariant = Color(0xFF1C2740),
    onSurfaceVariant = Color(0xFFB8C4D9),
    error = CriminalRed
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A00),
    onPrimary = Color.White,
    secondary = Color(0xFF1B4F8A),
    background = Color(0xFFF4F6FA),
    surface = Color.White,
    onBackground = Color(0xFF121826),
    onSurface = Color(0xFF121826),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF3A4558),
    error = Color(0xFFB3261E)
)

@Composable
fun YurTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val useDark = dark || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}

fun severityColor(severity: String): Color = when (severity) {
    "criminal", "red" -> CriminalRed
    "fine_large", "orange" -> LargeOrange
    "fine_mid", "yellow" -> MidAmber
    "fine_small" -> SmallGreen
    else -> MidAmber
}

fun severityLabel(severity: String, labels: Map<String, String>): String =
    labels[severity] ?: when (severity) {
        "criminal" -> "УГОЛОВНОЕ"
        "fine_large" -> "Крупный штраф"
        "fine_mid" -> "Среднее"
        "fine_small" -> "Мелочь"
        "red" -> "Красный"
        "orange" -> "Оранжевый"
        "yellow" -> "Жёлтый"
        else -> severity
    }
