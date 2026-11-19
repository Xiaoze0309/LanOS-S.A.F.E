package com.lanos.safe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanos.safe.Routes
import com.lanos.safe.ui.design.DesignMode
import com.lanos.safe.ui.design.FrostedSurface
import com.lanos.safe.ui.design.Lanos

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val items = listOf(
    NavItem(Routes.Home, "总览", Icons.Rounded.Home, Icons.Outlined.Home),
    NavItem(Routes.Domains, "域名", Icons.Rounded.Dns, Icons.Outlined.Dns),
    NavItem(Routes.Waf, "防护", Icons.Rounded.Shield, Icons.Outlined.Shield),
    NavItem(Routes.Settings, "设置", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

/**
 * 悬浮胶囊导航：选中项背后有一枚随位置滑动的指示器，图标弹性放大，
 * Miuix 模式用弹簧（有回弹），Material 模式接近临界阻尼（干脆）。
 */
@Composable
fun FloatingNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = Lanos.palette
    val motion = Lanos.motion
    val haptics = LocalHapticFeedback.current
    val index = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val pillWidth = if (Lanos.mode == DesignMode.Miuix) 54.dp else 44.dp
    val barHeight = if (Lanos.mode == DesignMode.Miuix) 66.dp else 58.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
    ) {
        FrostedSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            shape = Lanos.pillShape
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                val slot = maxWidth / items.size
                val targetX = slot * index + (slot - pillWidth) / 2
                val x by animateDpAsState(targetValue = targetX, animationSpec = motion.dpEmphasized)
                Box(
                    Modifier
                        .offset(x = x, y = (barHeight - 44.dp) / 2 - 6.dp)
                        .size(pillWidth, 44.dp)
                        .clip(Lanos.pillShape)
                        .background(pal.accent.copy(alpha = if (Lanos.dark) 0.18f else 0.10f))
                )
                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { i, item ->
                        NavButton(
                            item = item,
                            selected = i == index,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            if (i != index) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigate(item.route)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavButton(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = Lanos.palette
    val motion = Lanos.motion
    val tint by animateColorAsState(if (selected) pal.accent else pal.sub, motion.colorGentle)
    val scale by animateFloatAsState(if (selected) 1.06f else 0.96f, motion.standard)

    Column(
        modifier = modifier
            .clip(Lanos.pillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
    }
}
