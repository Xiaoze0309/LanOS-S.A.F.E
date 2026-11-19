package com.lanos.safe.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.rethrowIfCancelled
import com.lanos.safe.data.ApiException
import com.lanos.safe.data.DomainItem
import com.lanos.safe.data.LanosApi
import com.lanos.safe.ui.design.Hairline
import com.lanos.safe.ui.design.KeyValueRow
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosCard
import com.lanos.safe.ui.design.LanosDialog
import com.lanos.safe.ui.design.LanosTextField
import com.lanos.safe.ui.design.LanosTopBar
import com.lanos.safe.ui.design.PillTag
import com.lanos.safe.ui.design.appear
import kotlinx.coroutines.launch

/* 域名页：站点列表 + DNS 校验引导 + 添加/删除。 */

@Composable
fun DomainScreen(onBack: () -> Unit) {
    val pal = Lanos.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var domains by remember { mutableStateOf(emptyList<DomainItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var verifyingId by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DomainItem?>(null) }
    var cfTarget by remember { mutableStateOf<DomainItem?>(null) }
    val messages = remember { mutableStateMapOf<Int, String>() }

    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    fun reason(e: Throwable, fallback: String) = if (e is ApiException) e.message else (e.message ?: fallback)

    // 统一加载入口：首次进入、刷新、重试都走这里
    suspend fun load() {
        loading = true
        error = null
        runCatching { LanosApi.domains() }.onSuccess { domains = it }.onFailure { it.rethrowIfCancelled(); error = reason(it, "加载失败") }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    fun copyValue(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LANOS TXT", text))
        toast("已复制")
    }

    fun verify(item: DomainItem) {
        if (verifyingId != null) return
        verifyingId = item.id
        scope.launch {
            runCatching { LanosApi.verifyDomain(item.id) }
                .onSuccess { r -> messages[item.id] = r.message; toast(r.message); load() }
                .onFailure { e -> val msg = reason(e, "验证失败"); messages[item.id] = msg; toast(msg) }
            verifyingId = null
        }
    }

    val verifiedCount = domains.count { it.verified }

    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()) {
        LanosTopBar(
            title = "域名",
            subtitle = "共 ${domains.size} 个站点 · $verifiedCount 个已验证",
            onBack = onBack,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Rounded.Refresh, "刷新", tint = pal.text, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, "添加", tint = pal.text, modifier = Modifier.size(22.dp)) }
                }
            }
        )
        error?.let { msg -> ErrorBar(msg) { scope.launch { load() } } }
        LanosButton("添加域名", { showAdd = true }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), tonal = true, leading = Icons.Rounded.Add)

        when {
            loading && domains.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = pal.accent)
            }

            domains.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Language, null, tint = pal.sub, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有添加域名", style = MaterialTheme.typography.titleMedium, color = pal.text)
                    Spacer(Modifier.height(4.dp))
                    Text("添加后即可查看流量与攻击拦截情况", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(domains, key = { _, item -> item.id }) { index, item ->
                    DomainCard(
                        item = item, expanded = expandedId == item.id, verifying = verifyingId == item.id,
                        message = messages[item.id],
                        onToggle = { expandedId = if (expandedId == item.id) null else item.id },
                        onCopy = { copyValue(item.dnsValue) }, onVerify = { verify(item) },
                        onCf = { cfTarget = item },
                        onDelete = { pendingDelete = item }, modifier = Modifier.fillMaxWidth().appear(index)
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddDomainDialog(onDismiss = { showAdd = false }, onAdded = { item ->
            showAdd = false
            scope.launch { load(); expandedId = item.id }
        })
    }

    cfTarget?.let { item ->
        CloudflareDialog(site = item, onDismiss = { cfTarget = null }, onChanged = { scope.launch { load() } })
    }

    val target = pendingDelete
    if (target != null) {
        LanosDialog(title = "删除域名", onDismiss = { pendingDelete = null }) {
            Text("确定删除 ${target.domain} 吗？该站点的流量与防护配置会一并失效。", style = MaterialTheme.typography.bodyMedium, color = pal.sub)
            Spacer(Modifier.height(18.dp))
            Row {
                LanosButton("取消", { pendingDelete = null }, Modifier.weight(1f), tonal = true)
                Spacer(Modifier.width(10.dp))
                LanosButton("删除", {
                    pendingDelete = null
                    scope.launch {
                        runCatching { LanosApi.deleteDomain(target.id) }
                            .onSuccess { toast("已删除"); load() }
                            .onFailure { toast(reason(it, "删除失败")) }
                    }
                }, Modifier.weight(1f))
            }
        }
    }
}

/** 单条域名卡片：摘要 + 可展开的校验详情。 */
@Composable
private fun DomainCard(
    item: DomainItem, expanded: Boolean, verifying: Boolean, message: String?,
    onToggle: () -> Unit, onCopy: () -> Unit, onVerify: () -> Unit, onCf: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = Lanos.palette
    LanosCard(modifier = modifier, onClick = onToggle, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.domain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("创建于 ${fmtTime(item.createdAt)}", style = MaterialTheme.typography.bodySmall, color = pal.sub, maxLines = 1)
            }
            PillTag(if (item.verified) "已验证" else "待验证", if (item.verified) pal.good else pal.warn)
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Delete, "删除", tint = pal.sub, modifier = Modifier.size(17.dp)) }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = pal.sub, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(10.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Text("在域名 DNS 添加下面的 TXT 记录，或把校验文件放到站点根目录，然后点验证", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                Spacer(Modifier.height(4.dp))
                KeyValueRow("TXT 主机", item.dnsName, mono = true)
                KeyValueRow("TXT 值", item.dnsValue, mono = true)
                KeyValueRow("校验地址", item.httpPath)
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = if (item.verified) pal.good else pal.warn)
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    LanosButton("复制 TXT 值", onCopy, Modifier.weight(1f), tonal = true, leading = Icons.Rounded.ContentCopy)
                    Spacer(Modifier.width(10.dp))
                    LanosButton("立即验证", onVerify, Modifier.weight(1f), loading = verifying)
                }
                Spacer(Modifier.height(10.dp))
                LanosButton(
                    "Cloudflare 监控",
                    onCf,
                    Modifier.fillMaxWidth(),
                    tonal = true,
                    leading = Icons.Rounded.Cloud
                )
            }
        }
    }
}

/** 添加域名弹窗：输入校验 + 异步提交。 */
@Composable
private fun AddDomainDialog(onDismiss: () -> Unit, onAdded: (DomainItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LanosDialog(title = "添加域名", onDismiss = { if (!busy) onDismiss() }) {
        LanosTextField(value, { value = it; err = null }, "域名", placeholder = "example.com", keyboardType = KeyboardType.Uri, error = err, enabled = !busy)
        Spacer(Modifier.height(16.dp))
        Row {
            LanosButton("取消", onDismiss, Modifier.weight(1f), tonal = true, enabled = !busy)
            Spacer(Modifier.width(10.dp))
            LanosButton("添加", {
                val input = value.trim().lowercase()
                if (input.isBlank() || !input.contains(".")) {
                    err = if (input.isBlank()) "请输入域名" else "域名格式不正确"
                } else {
                    busy = true
                    scope.launch {
                        runCatching { LanosApi.addDomain(input) }
                            .onSuccess { onAdded(it) }
                            .onFailure { err = if (it is ApiException) it.message else (it.message ?: "添加失败") }
                        busy = false
                    }
                }
            }, Modifier.weight(1f), enabled = value.isNotBlank() && !busy, loading = busy)
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

/** ISO 时间直接截到分钟，不引第三方日期库。 */
private fun fmtTime(raw: String): String = raw.replace("T", " ").take(16)
