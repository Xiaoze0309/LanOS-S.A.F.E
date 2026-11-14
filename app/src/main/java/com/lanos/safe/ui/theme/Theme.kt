package com.lanos.safe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 浅色配色：主色为科技蓝
private val LightColorScheme = lightColorScheme(
    primary = MiuiBlue,
    error = SafeRed,
    background = Color.White
)

// 深色配色：主色为科技蓝，背景为深色
private val DarkColorScheme = darkColorScheme(
    primary = MiuiBlue,
    error = SafeRed,
    background = DarkBackground,
    surface = DarkBackground
)

// 应用主题入口：依据系统深浅色模式自动切换配色
@Composable
fun LanosSafeTheme(
    // 主题包裹的内容
    content: @Composable () -> Unit
) {
    // 根据系统设置选择配色方案
    val colorScheme = if (isSystemInDarkTheme()) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    // 应用 Material 3 主题
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeTypography,
        content = content
    )
}
