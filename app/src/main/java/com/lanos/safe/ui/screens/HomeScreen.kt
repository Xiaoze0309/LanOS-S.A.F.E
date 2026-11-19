package com.lanos.safe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanos.safe.data.rethrowIfCancelled
import com.lanos.safe.data.DomainItem
import com.lanos.safe.data.LanosApi
import com.lanos.safe.data.TrafficSeries
import com.lanos.safe.data.WafEventItem
import com.lanos.safe.data.WafStats
import com.lanos.safe.data.formatBytes
import com.lanos.safe.data.formatCount
import com.lanos.safe.data.relativeTime
import com.lanos.safe.ui.components.TrafficChart
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosCard
import com.lanos.safe.ui.design.LanosChip
import com.lanos.safe.ui.design.PillTag
import com.lanos.safe.ui.design.SectionHeader
import com.lanos.safe.ui.design.StatTile
import com.lanos.safe.ui.design.appear
import kotlinx.coroutines.launch
import java.util.Calendar

private data class RangeSpec(val key: String, val short: String, val long: String)

private val ranges = listOf(
    RangeSpec("30m", "30m", "近 30 分钟"),
    RangeSpec("1h", "1h", "近 1 小时"),
    RangeSpec("3h", "3h", "近 3 小时"),
    RangeSpec("6h", "6h", "近 6 小时"),
    RangeSpec("12h", "12h", "近 12 小时"),
    RangeSpec("24h", "24h", "近 24 小时"),
    RangeSpec("1m", "1M", "近 1 个月"),
    RangeSpec("3m", "3M", "近 3 个月"),
    RangeSpec("6m", "6M", "近 6 个月"),
    RangeSpec("1y", "1Y", "近 1 年")
)

@Composable
fun HomeScreen(
    onOpenDomains: () -> Unit = {},
    onOpenWaf: () -> Unit = {}
) {
    val pal = Lanos.palette
    val scope = rememberCoroutineScope()

    var domains by remember { mutableStateOf(emptyList<DomainItem>()) }
    var domainId by rememberSaveable { mutableStateOf<Int?>(null) }
    var range by rememberSaveable { mutableStateOf("24h") }
    var series by remember { mutableStateOf<TrafficSeries?>(null) }
    var stats by remember { mutableStateOf<WafStats?>(null) }
    var events by remember { mutableStateOf(emptyList<WafEventItem>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var seeding by remember { mutableStateOf(false) }

    val spec = ranges.firstOrNull { it.key == range } ?: ranges[5]

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        runCatching { LanosApi.domains() }
            .onSuccess { list ->
                domains = list
                if (domainId == null || list.none { it.id == domainId }) {
                    domainId = list.firstOrNull()?.id
                }
            }
            .onFailure { it.rethrowIfCancelled(); error = it.message }
        loading = false
    }

    LaunchedEffect(domainId, range, reloadTick) {
        val id = domainId ?: return@LaunchedEffect
        runCatching {
            val t = LanosApi.traffic(id, range)
            val s = LanosApi.wafStats("24h")
            series = t
            stats = s
        }.onFailure { it.rethrowIfCancelled(); error = it.message }
        runCatching { LanosApi.wafEvents(6) }.onSuccess { events = it }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 120.dp)
    ) {
        // 顶部：品牌 + 问候 + 在线状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LANOS S.A.F.E",
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.sub,
                    letterSpacing = 2.4.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(greeting(), style = MaterialTheme.typography.displaySmall, color = pal.text)
            }
            IconButton(onClick = { reloadTick++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = pal.sub, modifier = Modifier.size(20.dp))
            }
            PillTag(
                text = if (error == null) "在线" else "异常",
                color = if (error == null) pal.good else pal.bad
            )
        }

        Spacer(Modifier.height(18.dp))

        if (domains.isEmpty() && !loading) {
            LanosCard(Modifier.fillMaxWidth().appear(0), padding = 24.dp) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(pal.accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = pal.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("还没有接入域名", style = MaterialTheme.typography.titleLarge, color = pal.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "添加域名并完成校验后，这里会显示实时流量与拦截数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.sub
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = pal.bad)
                }
                Spacer(Modifier.height(16.dp))
                LanosButton("去添加域名", onClick = onOpenDomains, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                LanosButton(
                    text = if (seeding) "正在生成…" else "先生成一份演示数据",
                    onClick = {
                        if (!seeding) {
                            seeding = true
                            scope.launch {
                                runCatching { LanosApi.seedDemo() }
                                    .onSuccess { reloadTick++ }
                                    .onFailure { it.rethrowIfCancelled(); error = it.message }
                                seeding = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tonal = true,
                    loading = seeding
                )
            }
            return@Column
        }

        // 域名切换
        if (domains.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                domains.forEach { d ->
                    LanosChip(d.domain, d.id == domainId) { domainId = d.id }
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            domains.firstOrNull()?.let { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = pal.sub, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(d.domain, style = MaterialTheme.typography.bodyMedium, color = pal.text)
                    Spacer(Modifier.width(8.dp))
                    PillTag(if (d.verified) "已验证" else "待验证", if (d.verified) pal.good else pal.warn)
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (error != null) {
            LanosCard(Modifier.fillMaxWidth(), padding = 14.dp, light = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = pal.bad, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        error ?: "请求失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.bad,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "重试",
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.accent,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { reloadTick++ }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 流量主卡
        val total = series
        LanosCard(Modifier.fillMaxWidth().appear(1), padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("请求量", style = MaterialTheme.typography.labelMedium, color = pal.sub)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatCount(total?.totalRequests ?: 0L), style = MaterialTheme.typography.headlineLarge, color = pal.text)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${formatBytes(total?.totalBytes ?: 0L)} 流量",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.sub,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                PillTag(spec.long, pal.accent)
            }
            Spacer(Modifier.height(6.dp))
            TrafficChart(
                points = total?.points ?: emptyList(),
                shortRange = spec.key !in listOf("1m", "3m", "6m", "1y"),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ranges.forEach { r ->
                    LanosChip(r.short, r.key == range) { range = r.key }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 四个指标
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "请求",
                formatCount(total?.totalRequests ?: 0L),
                "次",
                Icons.Rounded.Bolt,
                pal.accent,
                Modifier.weight(1f).appear(2)
            )
            StatTile(
                "拦截",
                formatCount(stats?.blocked ?: 0L),
                "次",
                Icons.Rounded.Shield,
                pal.bad,
                Modifier.weight(1f).appear(3)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "流量",
                formatBytes(total?.totalBytes ?: 0L).substringBefore(" "),
                formatBytes(total?.totalBytes ?: 0L).substringAfter(" ", "B"),
                Icons.Rounded.Dns,
                pal.good,
                Modifier.weight(1f).appear(4)
            )
            StatTile(
                "扫描",
                formatCount(stats?.scanned ?: 0L),
                "次",
                Icons.Rounded.Warning,
                pal.warn,
                Modifier.weight(1f).appear(5)
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader(
            "最近攻击",
            "WAF 实时日志",
            trailing = {
                Text(
                    "全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = pal.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenWaf
                    )
                )
            }
        )
        Spacer(Modifier.height(10.dp))

        LanosCard(Modifier.fillMaxWidth().appear(6), padding = 8.dp) {
            if (events.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Text("最近 24 小时没有攻击事件", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                }
            } else {
                events.forEachIndexed { i, e ->
                    EventRow(e)
                    if (i != events.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 26.dp, end = 8.dp)
                                .height(1.dp)
                                .background(pal.hairline)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: WafEventItem) {
    val pal = Lanos.palette
    val tint = when (e.action) {
        "block" -> pal.bad
        "challenge" -> pal.warn
        else -> pal.sub
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.path.ifBlank { "/" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = pal.text,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${e.ip} · ${e.ruleName}",
                style = MaterialTheme.typography.labelSmall,
                color = pal.sub,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            PillTag(actionLabel(e.action), tint)
            Spacer(Modifier.height(3.dp))
            Text(relativeTime(e.createdAt), style = MaterialTheme.typography.labelSmall, color = pal.sub)
        }
    }
}

private fun actionLabel(action: String): String = when (action) {
    "block" -> "已拦截"
    "challenge" -> "已质询"
    else -> "已记录"
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        in 19..22 -> "晚上好"
        else -> "夜深了"
    }
}
