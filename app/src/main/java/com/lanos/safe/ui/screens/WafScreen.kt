package com.lanos.safe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.rethrowIfCancelled
import com.lanos.safe.data.ApiException
import com.lanos.safe.data.LanosApi
import com.lanos.safe.data.WafEventItem
import com.lanos.safe.data.WafRuleItem
import com.lanos.safe.data.WafStats
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosCard
import com.lanos.safe.ui.design.LanosDialog
import com.lanos.safe.ui.design.LanosSegmented
import com.lanos.safe.ui.design.LanosSwitch
import com.lanos.safe.ui.design.LanosTextField
import com.lanos.safe.ui.design.LanosTopBar
import com.lanos.safe.ui.design.PillTag
import com.lanos.safe.ui.design.StatTile
import kotlinx.coroutines.launch

/* WAF 页：24 小时统计 + 规则列表 + 拦截事件。 */

private val TARGET_KEYS = listOf("path", "ua", "query", "body")
private val TARGET_LABELS = listOf("路径", "UA", "查询", "正文")
private val ACTION_KEYS = listOf("block", "challenge", "log")
private val ACTION_LABELS = listOf("拦截", "质询", "仅记录")
private val SEVERITY_KEYS = listOf("low", "medium", "high")
private val SEVERITY_LABELS = listOf("低", "中", "高")

@Composable
fun WafScreen() {
    val pal = Lanos.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rules by remember { mutableStateOf(emptyList<WafRuleItem>()) }
    var events by remember { mutableStateOf(emptyList<WafEventItem>()) }
    var stats by remember { mutableStateOf<WafStats?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WafRuleItem?>(null) }

    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    fun reason(e: Throwable, fallback: String) = if (e is ApiException) e.message else (e.message ?: fallback)

    // 三个接口各自容错，任一失败都汇总到顶部错误条
    suspend fun loadAll() {
        loading = true
        error = null
        var failed: String? = null
        runCatching { LanosApi.wafStats("24h") }.onSuccess { stats = it }.onFailure { it.rethrowIfCancelled(); failed = reason(it, "统计加载失败") }
        runCatching { LanosApi.wafRules() }.onSuccess { rules = it }.onFailure { it.rethrowIfCancelled(); if (failed == null) failed = reason(it, "规则加载失败") }
        runCatching { LanosApi.wafEvents(30) }.onSuccess { events = it }.onFailure { it.rethrowIfCancelled(); if (failed == null) failed = reason(it, "事件加载失败") }
        error = failed
        loading = false
    }

    suspend fun reloadRules() {
        runCatching { LanosApi.wafRules() }.onSuccess { rules = it }.onFailure { toast(reason(it, "刷新失败")) }
    }
    LaunchedEffect(Unit) { loadAll() }

    // 开关先乐观更新，失败再回滚
    fun toggle(rule: WafRuleItem, on: Boolean) {
        rules = rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }
        scope.launch {
            runCatching { LanosApi.setWafRuleEnabled(rule.id, on) }
                .onFailure { e ->
                    rules = rules.map { r -> if (r.id == rule.id) r.copy(enabled = !on) else r }
                    toast(reason(e, "开关失败"))
                }
        }
    }

    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()) {
        LanosTopBar(
            title = "WAF 防护",
            subtitle = "规则引擎 · 实时拦截",
            action = {
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, "新增规则", tint = pal.text, modifier = Modifier.size(22.dp)) }
            }
        )
        error?.let { msg -> ErrorBar(msg) { scope.launch { loadAll() } } }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("今日扫描", stats?.scanned?.toString() ?: "—", "次", Icons.Rounded.Search, pal.accent, Modifier.weight(1f))
            StatTile("已拦截", stats?.blocked?.toString() ?: "—", "次", Icons.Rounded.Block, pal.bad, Modifier.weight(1f))
            StatTile("拦截率", rateText(stats), "%", Icons.Rounded.Security, pal.good, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        LanosSegmented(listOf("规则", "事件"), tab, { tab = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Spacer(Modifier.height(6.dp))

        when {
            loading && rules.isEmpty() && events.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = pal.accent)
            }

            tab == 0 && rules.isEmpty() -> EmptyHint(Icons.Rounded.Security, "暂无自定义规则", "点右上角加号新增一条规则", Modifier.weight(1f).fillMaxWidth())
            tab == 1 && events.isEmpty() -> EmptyHint(Icons.Rounded.Security, "最近没有攻击事件", "有请求命中规则时会在这里出现", Modifier.weight(1f).fillMaxWidth())

            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab == 0) {
                    items(rules, key = { it.id }) { rule -> RuleCard(rule, onToggle = { on -> toggle(rule, on) }, onDelete = { pendingDelete = rule }) }
                } else {
                    items(events, key = { it.id }) { event -> EventCard(event) }
                }
            }
        }
    }

    if (showAdd) {
        AddRuleDialog(onDismiss = { showAdd = false }, onCreated = {
            showAdd = false
            scope.launch { reloadRules(); toast("规则已创建") }
        })
    }

    val target = pendingDelete
    if (target != null) {
        LanosDialog(title = "删除规则", onDismiss = { pendingDelete = null }) {
            Text("确定删除规则「${target.name}」吗？删除后不再参与匹配。", style = MaterialTheme.typography.bodyMedium, color = pal.sub)
            Spacer(Modifier.height(18.dp))
            Row {
                LanosButton("取消", { pendingDelete = null }, Modifier.weight(1f), tonal = true)
                Spacer(Modifier.width(10.dp))
                LanosButton("删除", {
                    pendingDelete = null
                    scope.launch {
                        runCatching { LanosApi.deleteWafRule(target.id) }
                            .onSuccess { toast("已删除"); reloadRules() }
                            .onFailure { toast(reason(it, "删除失败")) }
                    }
                }, Modifier.weight(1f))
            }
        }
    }
}

/** 规则卡片：名称 + 严重度 + 开关 + 匹配明细（内置规则不可删）。 */
@Composable
private fun RuleCard(rule: WafRuleItem, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val pal = Lanos.palette
    LanosCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    PillTag(severityLabel(rule.severity), severityColor(rule.severity))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${targetLabel(rule.target)} · ${ruleActionLabel(rule.action)}" + if (rule.builtin) " · 内置" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = pal.sub, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            LanosSwitch(checked = rule.enabled, onCheckedChange = onToggle)
            if (!rule.builtin) {
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Delete, "删除规则", tint = pal.sub, modifier = Modifier.size(17.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(rule.pattern, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = pal.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 事件卡片：动作色条 + 请求路径 + 来源 IP + 命中规则。 */
@Composable
private fun EventCard(event: WafEventItem) {
    val pal = Lanos.palette
    val tint = when (event.action) {
        "block" -> pal.bad
        "challenge" -> pal.warn
        else -> pal.sub
    }
    LanosCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(38.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${event.method} ${event.path}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text("${event.ip} · ${fmtTime(event.createdAt)}", style = MaterialTheme.typography.bodySmall, color = pal.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            PillTag(eventActionLabel(event.action), tint)
        }
        Spacer(Modifier.height(6.dp))
        Text("${event.ruleName} · 风险分 ${event.score}", style = MaterialTheme.typography.bodySmall, color = pal.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 新增规则弹窗：名称、匹配内容与三组选择器。 */
@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val pal = Lanos.palette
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(0) }
    var action by remember { mutableStateOf(0) }
    var severity by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val labelStyle = MaterialTheme.typography.labelMedium

    LanosDialog(title = "新增规则", onDismiss = { if (!busy) onDismiss() }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LanosTextField(name, { name = it }, "规则名称", placeholder = "例如：拦截扫描器", enabled = !busy)
            Spacer(Modifier.height(12.dp))
            LanosTextField(pattern, { pattern = it; err = null }, "匹配内容", placeholder = "例如：sqlmap", error = err, enabled = !busy)
            Spacer(Modifier.height(12.dp))
            Text("匹配位置", style = labelStyle, color = pal.sub, modifier = Modifier.padding(bottom = 6.dp))
            LanosSegmented(TARGET_LABELS, target, { target = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("命中动作", style = labelStyle, color = pal.sub, modifier = Modifier.padding(bottom = 6.dp))
            LanosSegmented(ACTION_LABELS, action, { action = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("严重度", style = labelStyle, color = pal.sub, modifier = Modifier.padding(bottom = 6.dp))
            LanosSegmented(SEVERITY_LABELS, severity, { severity = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row {
                LanosButton("取消", { if (!busy) onDismiss() }, Modifier.weight(1f), tonal = true, enabled = !busy)
                Spacer(Modifier.width(10.dp))
                LanosButton("创建", {
                    busy = true
                    scope.launch {
                        runCatching {
                            LanosApi.addWafRule(
                                name = name.trim().ifBlank { "自定义规则" },
                                pattern = pattern.trim(),
                                target = TARGET_KEYS[target],
                                action = ACTION_KEYS[action],
                                severity = SEVERITY_KEYS[severity]
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure { err = if (it is ApiException) it.message else (it.message ?: "创建失败") }
                        busy = false
                    }
                }, Modifier.weight(1f), enabled = pattern.isNotBlank() && !busy, loading = busy)
            }
        }
    }
}

/** 空态：图标 + 两行说明。 */
@Composable
private fun EmptyHint(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val pal = Lanos.palette
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = pal.sub, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = pal.text)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = pal.sub)
        }
    }
}

/** 顶部错误条：红字提示 + 重试。 */
@Composable
private fun ErrorBar(message: String, onRetry: () -> Unit) {
    val pal = Lanos.palette
    Row(
        Modifier.fillMaxWidth().background(pal.bad.copy(alpha = 0.10f)).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = pal.bad, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = pal.bad, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("重试", style = MaterialTheme.typography.labelMedium, color = pal.bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onRetry() }.padding(start = 12.dp))
    }
}

/** 拦截率：整数 + 一位小数，scanned = 0 时返回 0。 */
private fun rateText(stats: WafStats?): String {
    if (stats == null) return "—"
    if (stats.scanned <= 0L) return "0"
    val tenths = (stats.blocked * 1000L) / stats.scanned
    return "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun severityColor(severity: String): Color {
    val pal = Lanos.palette
    return when (severity) {
        "high" -> pal.bad
        "medium" -> pal.warn
        else -> pal.sub
    }
}

private fun severityLabel(severity: String) = when (severity) { "high" -> "高危"; "medium" -> "中危"; else -> "低危" }

private fun targetLabel(target: String) = TARGET_LABELS.getOrElse(TARGET_KEYS.indexOf(target)) { target }

private fun ruleActionLabel(action: String) = when (action) { "block" -> "拦截"; "challenge" -> "质询"; else -> "仅记录" }

private fun eventActionLabel(action: String) = when (action) { "block" -> "已拦截"; "challenge" -> "验证"; else -> "记录" }

/** ISO 时间直接截到分钟，不引第三方日期库。 */
private fun fmtTime(raw: String): String = raw.replace("T", " ").take(16)
