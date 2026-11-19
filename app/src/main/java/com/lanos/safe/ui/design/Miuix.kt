package com.lanos.safe.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------------------------------------------------------------------------------------
 * Miuix 组件层：所有页面只允许用这里的组件，保证 Miuix / Material 两种模式下都能正确渲染。
 * ------------------------------------------------------------------------------------------- */

@Composable
fun LanosCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    light: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val pal = Lanos.palette
    val motion = Lanos.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) motion.cardScale else 1f, motion.standard)
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            ),
        shape = Lanos.cardShape,
        color = if (light) pal.cardAlt else pal.card,
        border = BorderStroke(1.dp, pal.hairline),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun LanosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (Lanos.mode == DesignMode.Material) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    val pal = Lanos.palette
    val motion = Lanos.motion
    val haptics = LocalHapticFeedback.current
    val trackW = 50.dp
    val trackH = 30.dp
    val thumb = 24.dp
    val target = if (checked) trackW - thumb - 3.dp else 3.dp
    val offset by animateDpAsState(targetValue = target, animationSpec = motion.dpStandard)
    val track by animateColorAsState(if (checked) pal.accent else pal.hairline, motion.colorGentle)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .background(track)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(thumb)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun LanosSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = Lanos.palette
    val motion = Lanos.motion
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .height(40.dp)
            .clip(Lanos.pillShape)
            .background(pal.cardAlt)
            .border(1.dp, pal.hairline, Lanos.pillShape)
            .padding(3.dp)
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selectedIndex, motion.dpEmphasized)
        Box(
            Modifier
                .offset(x = x)
                .width(segW)
                .fillMaxHeight()
                .clip(Lanos.pillShape)
                .background(pal.card)
                .border(1.dp, pal.hairline, Lanos.pillShape)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Lanos.pillShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (i != selectedIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == selectedIndex) pal.text else pal.sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LanosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tonal: Boolean = false,
    leading: ImageVector? = null
) {
    val pal = Lanos.palette
    val motion = Lanos.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) motion.cardScale else 1f, motion.standard)
    val bg = when {
        !enabled -> pal.cardAlt
        tonal -> pal.accent.copy(alpha = 0.12f)
        else -> pal.accent
    }
    val fg = when {
        !enabled -> pal.sub
        tonal -> pal.accent
        else -> Color.White
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(50.dp)
            .clip(if (Lanos.mode == DesignMode.Miuix) Lanos.pillShape else RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
                Spacer(Modifier.width(10.dp))
            } else if (leading != null) {
                Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LanosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    error: String? = null
) {
    val pal = Lanos.palette
    var revealed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (Lanos.mode == DesignMode.Miuix) 16.dp else 10.dp)

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = pal.sub)
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = pal.text),
            cursorBrush = SolidColor(pal.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(pal.cardAlt)
                        .border(1.dp, if (error != null) pal.bad else pal.hairline, shape)
                        .padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = pal.sub.copy(alpha = 0.7f)
                            )
                        }
                        inner()
                    }
                    if (isPassword) {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (revealed) "隐藏密码" else "显示密码",
                                tint = pal.sub,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        )
        if (error != null) {
            Text(
                error,
                color = pal.bad,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun LanosChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = Lanos.palette
    val motion = Lanos.motion
    val bg by animateColorAsState(if (selected) pal.accent else pal.card, motion.colorGentle)
    val fg by animateColorAsState(if (selected) Color.White else pal.sub, motion.colorGentle)
    val scale by animateFloatAsState(if (selected) 1f else 0.98f, motion.standard)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Lanos.pillShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else pal.hairline, Lanos.pillShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pal = Lanos.palette
    Row(
        modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = pal.text, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = pal.sub)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun LanosTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null
) {
    val pal = Lanos.palette
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = pal.text, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 0.dp else 0.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = pal.text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = pal.sub)
            }
        }
        action?.invoke()
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val pal = Lanos.palette
    LanosCard(modifier = modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelSmall, color = pal.sub, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = pal.text)
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                color = pal.sub,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Lanos.palette.hairline))
}

@Composable
fun PillTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Lanos.pillShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

/** 对话框：Miuix 风格大圆角卡片，Material 模式下同样可用。 */
@Composable
fun LanosDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val pal = Lanos.palette
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(if (Lanos.mode == DesignMode.Miuix) 28.dp else 18.dp),
            color = pal.card,
            border = BorderStroke(1.dp, pal.hairline)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = pal.text)
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

/** 进场动画：轻微上浮 + 淡入，index 越大延迟越久（列表错落感）。 */
@Composable
fun Modifier.appear(index: Int = 0, offsetY: Dp = 12.dp): Modifier {
    val motion = Lanos.motion
    val alpha = remember { Animatable(0f) }
    val ty = remember { Animatable(offsetY.value) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 8) * 45).toLong())
        launch { alpha.animateTo(1f, motion.fade) }
        ty.animateTo(0f, motion.emphasized)
    }
    return this.graphicsLayer {
        this.alpha = alpha.value
        translationY = ty.value * density
    }
}

/** 毛玻璃质感的悬浮容器：半透明卡面 + 渐变 + 细描边。 */
@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Lanos.pillShape,
    content: @Composable BoxScope.() -> Unit
) {
    val pal = Lanos.palette
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(pal.card.copy(alpha = 0.94f), pal.card.copy(alpha = 0.80f))
                )
            )
            .border(1.dp, pal.hairline, shape),
        content = content
    )
}

/** 详情行的通用样式：左标签右值。 */
@Composable
fun KeyValueRow(key: String, value: String, mono: Boolean = false) {
    val pal = Lanos.palette
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = pal.sub, modifier = Modifier.width(96.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium,
            color = pal.text,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 箭头指示的“可进入”行。 */
@Composable
fun NavigationRow(
    title: String,
    subtitle: String? = null,
    leading: ImageVector? = null,
    leadingTint: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val pal = Lanos.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (leadingTint == Color.Unspecified) pal.accent else leadingTint
        if (leading != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(leading, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = pal.text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = pal.sub)
            }
        }
        if (trailing != null) trailing() else Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = pal.sub.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}
