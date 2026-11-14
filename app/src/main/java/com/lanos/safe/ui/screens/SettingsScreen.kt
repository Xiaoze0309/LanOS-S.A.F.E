package com.lanos.safe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.MiuiBlue

// 设置分组数据模型
private data class SettingGroup(
    val title: String,
    val items: List<SettingEntry>
)

// 单个设置项数据模型
private data class SettingEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val action: Action
)

// 设置项的三种行为
private enum class Action {
    NavigateLogin,
    NavigateTwoFactor,
    NavigateExtensions,
    ToastDomain,
    ToggleTheme
}

// 设置页（无状态组件）：全部回调经 Lambda 传入
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onOpenLogin: () -> Unit,
    onOpenTwoFactor: () -> Unit,
    onOpenExtensions: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 域名管理 Toast 提示所需的 Context
    val context = LocalContext.current

    // 按分组定义设置项
    val groups = listOf(
        SettingGroup(
            title = "账户",
            items = listOf(
                SettingEntry("邮箱登录", "使用邮箱账号登录", Icons.Filled.AccountCircle, Action.NavigateLogin)
            )
        ),
        SettingGroup(
            title = "安全",
            items = listOf(
                SettingEntry("2FA 验证", "双重身份验证", Icons.Filled.Fingerprint, Action.NavigateTwoFactor)
            )
        ),
        SettingGroup(
            title = "网络",
            items = listOf(
                SettingEntry("域名管理", "管理拦截域名列表", Icons.Filled.Dns, Action.ToastDomain)
            )
        ),
        SettingGroup(
            title = "扩展",
            items = listOf(
                SettingEntry("扩展与插件", "管理安全扩展", Icons.Filled.Extension, Action.NavigateExtensions)
            )
        ),
        SettingGroup(
            title = "外观",
            items = listOf(
                SettingEntry("深色主题", "切换浅色 / 深色外观", Icons.Filled.Palette, Action.ToggleTheme)
            )
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(vertical = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        // 遍历分组
        groups.forEach { group ->
            item(key = group.title) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MiuiBlue,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            group.items.forEach { entry ->
                item(key = entry.title) {
                    // 根据行为分发回调
                    val onClick = when (entry.action) {
                        Action.NavigateLogin -> onOpenLogin
                        Action.NavigateTwoFactor -> onOpenTwoFactor
                        Action.NavigateExtensions -> onOpenExtensions
                        Action.ToggleTheme -> onToggleTheme
                        Action.ToastDomain -> {
                            { Toast.makeText(context, "域名管理开发中", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    ListItem(
                        headlineContent = { Text(text = entry.title) },
                        supportingContent = { Text(text = entry.subtitle) },
                        leadingContent = {
                            Icon(imageVector = entry.icon, contentDescription = entry.title, tint = MiuiBlue)
                        },
                        trailingContent = {
                            if (entry.action == Action.ToggleTheme) {
                                Switch(checked = darkTheme, onCheckedChange = { onClick() })
                            } else {
                                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable(onClick = onClick)
                    )
                }
                item(key = "divider_${entry.title}") {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}
