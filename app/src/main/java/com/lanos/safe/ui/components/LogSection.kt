package com.lanos.safe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanos.safe.ui.theme.GrayMuted
import com.lanos.safe.ui.theme.Hairline
import com.lanos.safe.ui.theme.Paper
import com.lanos.safe.ui.theme.SafetyGreen
import com.lanos.safe.ui.theme.SoftAmber
import com.lanos.safe.ui.theme.SoftRed

// 日志行数据模型
private data class LogLine(val text: String, val color: Color)

// 极简日志区：白色 + 细灰边，三条静态日志带彩色圆点
@Composable
fun LogSection(
    modifier: Modifier = Modifier
) {
    val logs = listOf(
        LogLine("已建立安全隧道", SafetyGreen),
        LogLine("拦截 1 个跟踪请求", SoftAmber),
        LogLine("检测到异常访问", SoftRed)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Paper, RoundedCornerShape(16.dp))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        logs.forEach { log ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(log.color, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = log.text, fontSize = 12.sp, color = GrayMuted)
            }
        }
    }
}
