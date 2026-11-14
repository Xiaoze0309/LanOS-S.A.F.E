package com.lanos.safe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.components.StatusCard
import com.lanos.safe.ui.theme.SafeGreen

// 主页（无状态组件）：事件通过 Lambda 传出
@Composable
fun HomeScreen(
    onStartVpnClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 大标题
        Text(
            text = "LanOS S.A.F.E",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 副标题
        Text(
            text = "安全防护引擎",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        // 防护状态卡片：指示灯为绿色
        StatusCard(
            title = "防护已开启",
            subtitle = "本地安全防护正在实时运行",
            indicatorColor = SafeGreen
        )
        Spacer(modifier = Modifier.weight(1f))
        // 启动 VPN 大按钮
        Button(
            onClick = onStartVpnClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "启动本地 VPN")
        }
    }
}
