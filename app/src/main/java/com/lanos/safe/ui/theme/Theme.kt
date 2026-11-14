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

// 应用主题入口：支持由调用方指定深浅色（可被设置页外观开关控制）
@Composable
fun LanosSafeTheme(
    // 是否使用深色模式：缺省跟随系统
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 主题包裹的内容
    content: @Composable () -> Unit
) {
    // 依据参数选择配色方案
    val colorScheme = if (darkTheme) {
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
