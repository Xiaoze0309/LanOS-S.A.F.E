package com.lanos.safe.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import com.lanos.safe.ui.theme.Ink
import com.lanos.safe.ui.theme.SoftGray

// 首页：没有域名就没有防护
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
        StatusCard(hasDomain = hasDomain, onConfigureDomain = onConfigureDomain)
        LogSection()
    }
}

// 核心大卡片：空状态 / 防护状态切换
@Composable
private fun StatusCard(hasDomain: Boolean, onConfigureDomain: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGray),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        AnimatedContent(
            targetState = hasDomain,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "statusCard"
        ) { has ->
            if (has) {
                ProtectedContent(hasDomain = has)
            } else {
                EmptyContent(onConfigureDomain = onConfigureDomain)
            }
        }
    }
}

// 空状态：尚未添加域名 + 去配置按钮
@Composable
private fun EmptyContent(onConfigureDomain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "尚未添加域名",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
        Button(
            onClick = onConfigureDomain,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = "去配置")
        }
    }
}

// 防护状态：今日已拦截 + 64sp 数字 + 三列数据
@Composable
private fun ProtectedContent(hasDomain: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "今日已拦截", color = Color.Gray)
        AnimatedContent(
            targetState = hasDomain,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "number"
        ) {
            Text(
                text = "0",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Ink
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Stat(label = "扫描流量", value = "0 KB")
            Stat(label = "活跃连接", value = "0")
            Stat(label = "端口", value = "9090")
        }
    }
}

// 三列灰色数据小列
@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
    }
}
