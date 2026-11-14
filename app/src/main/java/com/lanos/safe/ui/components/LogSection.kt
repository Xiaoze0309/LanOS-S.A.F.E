package com.lanos.safe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanos.safe.ui.theme.SafeGreen
import com.lanos.safe.ui.theme.SafeRed

// 日志行数据模型
private data class LogLine(val text: String, val color: Color)

// 实时日志流（无状态组件）：四条静态日志，每条前带彩色圆点
@Composable
fun LogSection(
    modifier: Modifier = Modifier
) {
    // 静态日志：绿 / 黄 / 红 三种状态色
    val logs = listOf(
        LogLine("已建立 VPN 隧道", SafeGreen),
        LogLine("拦截 1 个跟踪器", Color(0xFFF9A825)),
        LogLine("DNS 查询已转发", SafeGreen),
        LogLine("检测到异常流量", SafeRed)
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(logs) { log ->
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(log.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = log.text, fontSize = 13.sp)
                }
            }
        }
    }
}
