package com.lanos.safe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanos.safe.ui.components.LogSection
import com.lanos.safe.ui.components.TrafficChart
import com.lanos.safe.ui.theme.AccentBlue
import com.lanos.safe.ui.theme.GrayMuted
import com.lanos.safe.ui.theme.SafetyGreen

// 网关域名与时间范围、安全功能的数据模型
private data class GatewayDomain(val domain: String, val online: Boolean)
private data class RangeSpec(val label: String, val avg: String, val seed: Int)

// 顶部域名示例数据（多域名横向卡片）
private val sampleDomains = listOf(
    GatewayDomain("api.lanos.safe", true),
    GatewayDomain("cdn.lanos.safe", true),
    GatewayDomain("mail.lanos.safe", false)
)

// 时间范围与平均流量（假数据，seed 用于让折线图变化）
private val ranges = listOf(
    RangeSpec("10m", "1.2 MB/s", 0),
    RangeSpec("30m", "3.4 MB/s", 1),
    RangeSpec("1h", "5.6 MB/s", 2),
    RangeSpec("1d", "42.1 MB/s", 3),
    RangeSpec("1m", "1.8 GB/s", 4),
    RangeSpec("1y", "96.4 TB/s", 5)
)

// 首页：企业级安全网关监控面板
@Composable
fun HomeScreen(
    onConfigureDomain: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(ranges[2]) }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "LanOS S.A.F.E",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        DomainRow()
        TrafficCard(selected = selected, onSelect = { selected = it })
        SecurityGrid()
        LogSection()
    }
}

// 顶部多域名横向卡片
@Composable
private fun DomainRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sampleDomains) { d ->
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (d.online) SafetyGreen else GrayMuted,
                            RoundedCornerShape(50)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = d.domain, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// 流量卡片：平均流量 + 时间范围选择器 + 折线图
@Composable
private fun TrafficCard(selected: RangeSpec, onSelect: (RangeSpec) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "扫描流量",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                text = selected.avg,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ranges.forEach { r ->
                    val active = r == selected
                    Text(
                        text = r.label,
                        color = if (active) AccentBlue else GrayMuted,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (active) AccentBlue.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(r) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            TrafficChart(modifier = Modifier.padding(top = 12.dp), seed = selected.seed)
        }
    }
}

// 2x2 安全功能网格
@Composable
private fun SecurityGrid() {
    var waf by remember { mutableStateOf(true) }
    var ddos by remember { mutableStateOf(true) }
    var crawler by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecurityCard("WAF 防火墙", "拦截 1,024", Icons.Filled.Security, waf, { waf = it }, Modifier.weight(1f))
            SecurityCard("DDoS 防护", "清洗 86.4 GB", Icons.Filled.Shield, ddos, { ddos = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecurityCard("爬虫拦截", "识别 328", Icons.Filled.BugReport, crawler, { crawler = it }, Modifier.weight(1f))
            SecurityCard("速率限制", "1,200 QPS", Icons.Filled.Speed, rate, { rate = it }, Modifier.weight(1f))
        }
    }
}

// 单个安全卡片：开启蓝色图标 / 关闭灰色图标 + 开关
@Composable
private fun SecurityCard(
    title: String,
    value: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = if (checked) AccentBlue else GrayMuted
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, tint = iconColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = iconColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
