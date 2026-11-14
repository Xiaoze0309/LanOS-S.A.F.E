package com.lanos.safe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanos.safe.ui.components.LogSection
import com.lanos.safe.ui.components.TrafficChart
import com.lanos.safe.ui.theme.Ink
import com.lanos.safe.ui.theme.SoftGray

// 首页：安全监控面板
@Composable
fun HomeScreen(
    onConfigureDomain: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hasDomain by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "LanOS S.A.F.E",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        if (!hasDomain) {
            DomainHint(onConfigureDomain = onConfigureDomain)
        }
        TrafficCard()
        MetricsGrid()
        LogSection()
    }
}

// 未配置域名提示条
@Composable
private fun DomainHint(onConfigureDomain: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "未配置域名", fontWeight = FontWeight.SemiBold)
        Button(
            onClick = onConfigureDomain,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = "去配置")
        }
    }
}

// 流量图表卡片：大数字 + 折线图
@Composable
private fun TrafficCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGray),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "扫描流量", color = Color.Gray, fontSize = 12.sp)
            Text(text = "0 KB/s", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Ink)
            TrafficChart(modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// 2x2 数据小方块
@Composable
private fun MetricsGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(label = "系统负载", value = "23%", modifier = Modifier.weight(1f))
            MetricTile(label = "活跃连接", value = "0", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(label = "拦截次数", value = "0", modifier = Modifier.weight(1f))
            MetricTile(label = "拦截率", value = "98.7%", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SoftGray, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
    }
}
