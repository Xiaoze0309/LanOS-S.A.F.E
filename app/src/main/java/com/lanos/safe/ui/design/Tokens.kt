package com.lanos.safe.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 两套视觉体系：Miuix（HyperOS 风）与 Material3。设置页可实时切换。 */
enum class DesignMode { Miuix, Material }

@Immutable
data class LanosPalette(
    val accent: Color,
    val accentPressed: Color,
    val bg: Color,
    val bgElevated: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val sub: Color,
    val hairline: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val chartTop: Color,
    val chartBottom: Color
)

private val LightPalette = LanosPalette(
    accent = Color(0xFF2F6BFF),
    accentPressed = Color(0xFF1E54D8),
    bg = Color(0xFFF3F4F6),
    bgElevated = Color(0xFFFAFAFC),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF7F8FA),
    text = Color(0xFF101114),
    sub = Color(0xFF6E7280),
    hairline = Color(0xFFE6E8EC),
    good = Color(0xFF16A34A),
    warn = Color(0xFFF59E0B),
    bad = Color(0xFFEF4444),
    chartTop = Color(0xFF2F6BFF),
    chartBottom = Color(0xFF8FB3FF)
)

private val DarkPalette = LanosPalette(
    accent = Color(0xFF5B8CFF),
    accentPressed = Color(0xFF3E6FE6),
    bg = Color(0xFF08080A),
    bgElevated = Color(0xFF121215),
    card = Color(0xFF17171B),
    cardAlt = Color(0xFF1F1F24),
    text = Color(0xFFF4F4F6),
    sub = Color(0xFF9A9AA4),
    hairline = Color(0xFF2A2A31),
    good = Color(0xFF34D399),
    warn = Color(0xFFFBBF24),
    bad = Color(0xFFF87171),
    chartTop = Color(0xFF5B8CFF),
    chartBottom = Color(0xFF232A44)
)

/**
 * 动效规格：
 * - emphasized：卡片/页面进场、尺寸变化（高阻尼弹簧，HyperOS 的“肉感”）
 * - standard：开关、指示器等高频交互
 * - gentle：颜色、透明度
 */
@Immutable
data class LanosMotion(
    val emphasized: SpringSpec<Float>,
    val standard: SpringSpec<Float>,
    val gentle: SpringSpec<Float>,
    val fade: TweenSpec<Float>,
    val cardScale: Float,
    // animateColorAsState / animateDpAsState 要求 AnimationSpec<Color> / <Dp>，
    // 不能复用一个 SpringSpec<Float>，所以按类型各存一份（参数一致，观感相同）
    val colorGentle: SpringSpec<Color>,
    val dpStandard: SpringSpec<Dp>,
    val dpEmphasized: SpringSpec<Dp>
)

private val MiuixMotion = LanosMotion(
    emphasized = spring(dampingRatio = 0.68f, stiffness = 360f),
    standard = spring(dampingRatio = 0.76f, stiffness = 520f),
    gentle = spring(dampingRatio = 0.9f, stiffness = 220f),
    fade = tween(durationMillis = 240, easing = FastOutSlowInEasing),
    cardScale = 0.965f,
    colorGentle = spring(dampingRatio = 0.9f, stiffness = 220f),
    dpStandard = spring(dampingRatio = 0.76f, stiffness = 520f),
    dpEmphasized = spring(dampingRatio = 0.68f, stiffness = 360f)
)

private val MaterialMotion = LanosMotion(
    emphasized = spring(dampingRatio = 1f, stiffness = 900f),
    standard = spring(dampingRatio = 1f, stiffness = 1100f),
    gentle = spring(dampingRatio = 1f, stiffness = 700f),
    fade = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    cardScale = 0.985f,
    colorGentle = spring(dampingRatio = 1f, stiffness = 700f),
    dpStandard = spring(dampingRatio = 1f, stiffness = 1100f),
    dpEmphasized = spring(dampingRatio = 1f, stiffness = 900f)
)


/** Material3 基线配色：切到 Material 模式时整套换掉，而不是只改圆角 */
private val MaterialLightPalette = LanosPalette(
    accent = Color(0xFF6750A4),
    accentPressed = Color(0xFF4F378B),
    bg = Color(0xFFFFFBFE),
    bgElevated = Color(0xFFF7F2FA),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF3EDF7),
    text = Color(0xFF1C1B1F),
    sub = Color(0xFF49454F),
    hairline = Color(0xFFCAC4D0),
    good = Color(0xFF386A20),
    warn = Color(0xFF7D5260),
    bad = Color(0xFFB3261E),
    chartTop = Color(0xFF6750A4),
    chartBottom = Color(0xFFEADDFF)
)

private val MaterialDarkPalette = LanosPalette(
    accent = Color(0xFFD0BCFF),
    accentPressed = Color(0xFFB69DF8),
    bg = Color(0xFF1C1B1F),
    bgElevated = Color(0xFF211F26),
    card = Color(0xFF211F26),
    cardAlt = Color(0xFF2B2930),
    text = Color(0xFFE6E1E5),
    sub = Color(0xFFCAC4D0),
    hairline = Color(0xFF49454F),
    good = Color(0xFFB1D18A),
    warn = Color(0xFFE8B4C8),
    bad = Color(0xFFF2B8B5),
    chartTop = Color(0xFFD0BCFF),
    chartBottom = Color(0xFF3F2F6B)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalDesignMode = staticCompositionLocalOf { DesignMode.Miuix }
val LocalMotion = staticCompositionLocalOf { MiuixMotion }
val LocalDark = staticCompositionLocalOf { false }

/** 全局读取入口：Lanos.palette / Lanos.motion / Lanos.mode */
object Lanos {
    val palette: LanosPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val motion: LanosMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val mode: DesignMode
        @Composable @ReadOnlyComposable get() = LocalDesignMode.current

    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDark.current

    /** 卡片圆角：Miuix 更大更圆 */
    val cardRadius
        @Composable @ReadOnlyComposable get() = if (LocalDesignMode.current == DesignMode.Miuix) 26.dp else 16.dp

    val cardShape
        @Composable @ReadOnlyComposable get() = RoundedCornerShape(cardRadius)

    val pillShape
        @Composable @ReadOnlyComposable get() = RoundedCornerShape(percent = 50)
}

private val LanosTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun LanosTheme(
    mode: DesignMode = DesignMode.Miuix,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = when {
        mode == DesignMode.Material && darkTheme -> MaterialDarkPalette
        mode == DesignMode.Material -> MaterialLightPalette
        darkTheme -> DarkPalette
        else -> LightPalette
    }
    val motion = if (mode == DesignMode.Miuix) MiuixMotion else MaterialMotion
    val r = if (mode == DesignMode.Miuix) 28.dp else 16.dp

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.sub,
            outline = palette.hairline,
            error = palette.bad
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.sub,
            outline = palette.hairline,
            error = palette.bad
        )
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(if (mode == DesignMode.Miuix) 10.dp else 6.dp),
        small = RoundedCornerShape(if (mode == DesignMode.Miuix) 14.dp else 8.dp),
        medium = RoundedCornerShape(if (mode == DesignMode.Miuix) 20.dp else 12.dp),
        large = RoundedCornerShape(r),
        extraLarge = RoundedCornerShape(if (mode == DesignMode.Miuix) 34.dp else 24.dp)
    )

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalDesignMode provides mode,
        LocalMotion provides motion,
        LocalDark provides darkTheme
    ) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = LanosTypography, content = content)
    }
}
