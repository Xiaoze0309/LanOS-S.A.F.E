package com.lanos.safe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.AccentBlue

// 设置页：用户信息卡片 + 分类设置列表
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onOpenLogin: () -> Unit,
    onOpenTwoFactor: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(modifier = modifier.fillMaxSize().padding(vertical = 16.dp)) {
        item { UserCard(onClick = onOpenLogin) }
        item { SectionHeader("账户") }
        item { SettingRow("邮箱绑定", "绑定 / 更换登录邮箱", Icons.Filled.MailOutline, onClick = {}) }
        item { SettingDivider() }
        item { SettingRow("信任设备管理", "查看已信任的设备", Icons.Filled.Devices, onClick = {}) }
        item { SectionHeader("安全") }
        item { SettingRow("2FA 设置", "双重身份验证", Icons.Filled.Fingerprint, onClick = onOpenTwoFactor) }
        item { SectionHeader("网络") }
        item {
            SettingRow(
                "清除缓存",
                "清理本地缓存数据",
                Icons.Filled.DeleteSweep,
                onClick = { Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show() }
            )
        }
        item { SectionHeader("外观") }
        item {
            SettingRow(
                title = "深色主题",
                subtitle = "切换浅色 / 深色外观",
                icon = Icons.Filled.Palette,
                trailing = { Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() }) },
                onClick = onToggleTheme
            )
        }
        item { SectionHeader("关于") }
        item {
            SettingRow(
                "关于我们",
                "LanOS S.A.F.E v1.0",
                Icons.Filled.Info,
                onClick = { Toast.makeText(context, "LanOS S.A.F.E", Toast.LENGTH_SHORT).show() }
            )
        }
    }
}

// 顶部用户信息卡片：头像 + 用户名 + UID + 会员等级
@Composable
private fun UserCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "头像",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "点击登录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UID：未登录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "普通用户",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(AccentBlue, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        leadingContent = { Icon(imageVector = icon, contentDescription = title) },
        trailingContent = trailing ?: {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
