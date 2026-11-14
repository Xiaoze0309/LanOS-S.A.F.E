package com.lanos.safe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.components.LogSection
import com.lanos.safe.ui.components.ProtectCard
import com.lanos.safe.ui.theme.MiuiBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 协程函数：检测当前是否具备 Root 特权
private suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
    try {
        // 执行 su 并检查返回码
        val process = Runtime.getRuntime().exec("su -c id")
        val success = process.waitFor() == 0
        process.destroy()
        success
    } catch (e: Exception) {
        false
    }
}

// 首页（无状态组件）：状态经参数传入，事件经 Lambda 传出
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onStartVpnClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Root 检测结果：null 表示检测中
    var rootEnabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        rootEnabled = checkRoot()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeHeader(onOpenSettings = onOpenSettings)
        ProtectCard(rootEnabled = rootEnabled, onStartVpnClick = onStartVpnClick)
        LogSection()
    }
}

// 顶部标题栏：大字标题 + 右侧设置图标
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LanOS S.A.F.E",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = MiuiBlue
            )
        }
    }
}
