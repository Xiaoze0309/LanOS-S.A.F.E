package com.lanos.safe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.AccentBlue
import com.lanos.safe.ui.theme.GrayMuted

// 底部导航项：选中 / 未选中各一个图标
private data class NavItem(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// 三个 Tab
private val navItems = listOf(
    NavItem("home", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem("rules", Icons.Filled.Security, Icons.Outlined.Security),
    NavItem("settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

// 悬浮毛玻璃胶囊底部导航栏（无状态组件）
@Composable
fun FloatingNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, bottom = 24.dp),
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.8f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            navItems.forEach { item ->
                NavBarItem(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// 单个导航项：选中圆角灰色高亮 + 缩放动画（无状态组件）
@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(if (selected) AccentBlue else GrayMuted)
    val highlight by animateColorAsState(if (selected) AccentBlue.copy(alpha = 0.12f) else Color.Transparent)
    val scale by animateFloatAsState(if (selected) 1.1f else 1f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(highlight)
            .scale(scale)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.route,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
    }
}
