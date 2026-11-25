package com.enerix.sentryscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SentryTeal = Color(0xFF0F6B5C)
val SentryBlue = Color(0xFF17588C)
val SentryAccent = Color(0xFFB3542A)
val SentryDark = Color(0xFF101A17)

private val LightColors = lightColorScheme(
    primary = SentryTeal,
    secondary = SentryBlue,
    tertiary = SentryAccent
)

private val DarkColors = darkColorScheme(
    primary = SentryTeal,
    secondary = SentryBlue,
    tertiary = SentryAccent
)

@Composable
fun SentryScanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
