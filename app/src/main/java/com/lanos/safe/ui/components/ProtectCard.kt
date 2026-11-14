package com.lanos.safe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanos.safe.ui.theme.MiuiBlueHighlight
import com.lanos.safe.ui.theme.SafeGreen

// 核心防护大卡片（无状态组件）：状态经参数传入
@Composable
fun ProtectCard(
    rootEnabled: Boolean?,
    onStartVpnClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MiuiBlueHighlight),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左上角指示灯 + 右上角 Root 标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(SafeGreen, CircleShape)
                    )
                    Text(
                        text = "防护运行中",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                RootBadge(rootEnabled = rootEnabled)
            }
            // 今日已拦截 + 巨大数字
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "今日已拦截", color = Color.Gray)
                Text(
                    text = "0",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // 底部三个统计列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(label = "扫描流量", value = "0 KB")
                StatColumn(label = "活跃连接", value = "0")
                StatColumn(label = "端口", value = "9090")
            }
            // 启动 VPN 大按钮
            Button(
                onClick = onStartVpnClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "启动本地 VPN")
            }
        }
    }
}

// Root 特权 / 普通模式 药丸标签
@Composable
private fun RootBadge(rootEnabled: Boolean?) {
    val root = rootEnabled == true
    // 紫底白字表示 Root 特权，灰底表示普通模式
    val bg = if (root) Color(0xFF7C4DFF) else Color.LightGray
    val fg = if (root) Color.White else Color.DarkGray
    Text(
        text = if (root) "Root 特权" else "普通模式",
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// 底部统计小列
@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
    }
}
