package com.lanos.safe.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.MiuiBlue

// 单个设置项的数据封装
private data class SettingItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

// 设置列表内容
private val settingItems = listOf(
    SettingItem("设备指纹", "管理设备唯一标识", Icons.Filled.Fingerprint),
    SettingItem("规则更新", "检查安全规则最新版本", Icons.Filled.Refresh),
    SettingItem("关于", "LanOS S.A.F.E 版本信息", Icons.Filled.Info)
)

// 设置页面（无状态组件）
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 32.dp)
    ) {
        // 页面大标题
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        // 遍历渲染设置项
        settingItems.forEach { item ->
            ListItem(
                headlineContent = {
                    Text(text = item.title)
                },
                supportingContent = {
                    Text(text = item.subtitle)
                },
                leadingContent = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = MiuiBlue
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null
                    )
                }
            )
        }
    }
}
