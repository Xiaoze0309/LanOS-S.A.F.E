package com.lanos.safe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.MiuiBlue
import com.lanos.safe.ui.theme.MiuiBlueHighlight

// 底部导航项数据模型：选中/未选中各一个图标
private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// 四个 Tab 定义
private val navItems = listOf(
    NavItem("home", "主页", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem("rules", "规则", Icons.Filled.Rule, Icons.Outlined.Rule),
    NavItem("terminal", "终端", Icons.Filled.Terminal, Icons.Outlined.Terminal),
    NavItem("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

// 悬浮胶囊底部导航栏（无状态组件）
@Composable
fun FloatingNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 胶囊容器：两侧留边距 + 高圆角 + 柔和阴影
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        shadowElevation = 16.dp
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

// 单个导航项：选中态圆角高亮 + 缩放动画（无状态组件）
@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 图标颜色平滑过渡
    val tint by animateColorAsState(if (selected) MiuiBlue else Color.Gray)
    // 选中背景高亮平滑过渡
    val highlight by animateColorAsState(if (selected) MiuiBlueHighlight else Color.Transparent)
    // 选中时轻微放大
    val scale by animateFloatAsState(if (selected) 1.08f else 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(highlight)
            .scale(scale)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = tint
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
