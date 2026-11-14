package com.lanos.safe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 浅色配色：黑白灰为主
private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftGray,
    onSurfaceVariant = GrayMuted,
    outline = Hairline
)

// 深色配色：近黑背景，白色主色
private val DarkColorScheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    background = Color(0xFF121212),
    onBackground = Paper,
    surface = Color(0xFF16161A),
    onSurface = Paper,
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C33)
)

// 应用主题入口
@Composable
fun LanosSafeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = scheme, typography = SafeTypography, content = content)
}
