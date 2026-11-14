package com.lanos.safe.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.Ink

// 设置页：只保留邮箱、2FA、主题切换
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onOpenLogin: () -> Unit,
    onOpenTwoFactor: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(vertical = 16.dp)) {
        item { SectionHeader("账户") }
        item {
            SettingRow("邮箱登录", "使用邮箱账号登录", Icons.Filled.AccountCircle, onClick = onOpenLogin)
        }
        item { SettingDivider() }
        item { SectionHeader("安全") }
        item {
            SettingRow("2FA 验证", "双重身份验证", Icons.Filled.Fingerprint, onClick = onOpenTwoFactor)
        }
        item { SettingDivider() }
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
        leadingContent = { Icon(imageVector = icon, contentDescription = title, tint = Ink) },
        trailingContent = trailing ?: { Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
