package com.lanos.safe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateMapOf
import com.lanos.safe.ui.theme.MiuiBlue

// 扩展项数据模型
private data class ExtensionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val key: String
)

// 扩展列表定义
private val extensionItems = listOf(
    ExtensionItem("去广告与追踪拦截", "拦截广告与跟踪请求", Icons.Filled.Shield, "ads"),
    ExtensionItem("DNS 防污染 (DoH)", "加密 DNS 查询防污染", Icons.Filled.Lock, "doh"),
    ExtensionItem("恶意挖矿域名阻断", "阻断加密货币挖矿域名", Icons.Filled.Block, "miner")
)

// 扩展中心页（无状态组件）：返回事件经 Lambda 传出
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 各扩展的开关状态
    val toggles = remember {
        mutableStateMapOf("ads" to false, "doh" to false, "miner" to false)
    }
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "扩展与插件") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(extensionItems) { item ->
                ExtensionCard(
                    item = item,
                    checked = toggles[item.key] ?: false,
                    onToggle = {
                        // 更新开关并弹 Toast 提示
                        toggles[item.key] = it
                        Toast.makeText(context, "${item.title}：${if (it) "已启用" else "已关闭"}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// 单个扩展卡片：柔和阴影 + 图标 + 开关
@Composable
private fun ExtensionCard(
    item: ExtensionItem,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = MiuiBlue
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
