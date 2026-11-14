package com.lanos.safe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.MiuiBlue
import com.lanos.safe.ui.theme.MiuiBlueHighlight

// 单个导航项的数据封装（路由 + 选中/未选中图标）
private data class NavItemData(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// 底部两个导航项：主页 与 设置
private val navItems = listOf(
    NavItemData("home", Icons.Filled.Home, Icons.Outlined.Home),
    NavItemData("settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

// 悬浮胶囊形底部导航栏（无状态组件，状态由外层传入）
@Composable
fun SafeBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // 胶囊容器：两边带边距，圆角胶囊外形
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                SafeNavItem(
                    item = item,
                    selected = item.route == currentRoute,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

// 单个导航项（无状态组件）
@Composable
private fun SafeNavItem(
    item: NavItemData,
    selected: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 背景圆形高亮的颜色动画
    val highlightColor by animateColorAsState(
        targetValue = if (selected) MiuiBlueHighlight else Color.Transparent,
        label = "navHighlight"
    )
    // 图标颜色的平滑动画
    val iconColor by animateColorAsState(
        targetValue = if (selected) MiuiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navIconColor"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(CircleShape)
            .background(highlightColor)
            .clickable { onNavigate(item.route) }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.route,
            tint = iconColor
        )
    }
}
