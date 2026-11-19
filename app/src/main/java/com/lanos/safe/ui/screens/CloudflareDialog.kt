package com.lanos.safe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.ApiException
import com.lanos.safe.data.DomainItem
import com.lanos.safe.data.LanosApi
import com.lanos.safe.ui.design.KeyValueRow
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosDialog
import com.lanos.safe.ui.design.LanosTextField
import com.lanos.safe.ui.design.PillTag
import kotlinx.coroutines.launch

/** 一键接 Cloudflare：粘贴 token + zone id，后端自校验并每 30 分钟自动拉数据。 */
@Composable
fun CloudflareDialog(
    site: DomainItem,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val pal = Lanos.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<LanosApi.CloudflareStatus?>(null) }
    var zoneId by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var synced by remember { mutableStateOf<Int?>(null) }
    var level by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    LaunchedEffect(site.id) {
        runCatching { LanosApi.cfStatus(site.id) }.onSuccess { status = it }
        runCatching { LanosApi.cfSecurity(site.id) }.onSuccess { level = it }
    }

    fun connect() {
        if (busy) return
        if (zoneId.length < 16 || token.length < 20) {
            error = "Zone ID 和 API Token 看起来都不完整"
            return
        }
        scope.launch {
            busy = true
            error = null
            runCatching { LanosApi.cfConnect(site.id, zoneId.trim(), token.trim()) }
                .onSuccess {
                    status = it
                    token = ""
                    toast("已接入 ${it.zoneName}")
                    onChanged()
                }
                .onFailure { error = (it as? ApiException)?.message ?: "接入失败" }
            busy = false
        }
    }

    fun syncNow() {
        scope.launch {
            busy = true
            error = null
            runCatching { LanosApi.cfSyncNow(site.id) }
                .onSuccess {
                    synced = it
                    status = runCatching { LanosApi.cfStatus(site.id) }.getOrNull()
                    toast("同步完成，写入 $it 个时间点")
                }
                .onFailure { error = (it as? ApiException)?.message ?: "同步失败" }
            busy = false
        }
    }

    fun disconnect() {
        scope.launch {
            busy = true
            runCatching { LanosApi.cfDisconnect(site.id) }
                .onSuccess {
                    status = LanosApi.CloudflareStatus(false, "", "", null)
                    toast("已断开")
                    onChanged()
                }
                .onFailure { error = (it as? ApiException)?.message ?: "断开失败" }
            busy = false
        }
    }

    LanosDialog(title = "Cloudflare 监控", onDismiss = { if (!busy) onDismiss() }) {
        Text(
            site.domain,
            style = MaterialTheme.typography.bodySmall,
            color = pal.sub
        )
        Spacer(Modifier.height(10.dp))

        val st = status
        if (st?.connected == true) {
            Row {
                PillTag("已接入", pal.good)
                Spacer(Modifier.width(8.dp))
                Text(st.zoneName, style = MaterialTheme.typography.bodyMedium, color = pal.text)
            }
            Spacer(Modifier.height(8.dp))
            KeyValueRow("上次同步", st.lastSync.ifBlank { "尚未同步" })
            KeyValueRow(
                "安全级别",
                when (level) {
                    "under_attack" -> "Under Attack（最强）"
                    "high" -> "高"
                    "medium" -> "中"
                    "low" -> "低"
                    "essentially_off" -> "基本关闭"
                    "off" -> "关闭"
                    else -> level.ifBlank { "未知" }
                }
            )
            st.lastError?.let {
                Text("最近错误：$it", style = MaterialTheme.typography.bodySmall, color = pal.bad)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                LanosButton("立即同步", { syncNow() }, Modifier.weight(1f), loading = busy, tonal = true)
                Spacer(Modifier.width(10.dp))
                LanosButton(
                    if (level == "under_attack") "关闭 Under Attack" else "Under Attack",
                    {
                        scope.launch {
                            busy = true
                            val target = if (level == "under_attack") "medium" else "under_attack"
                            runCatching { LanosApi.cfSetSecurity(site.id, target) }
                                .onSuccess { level = it; toast("已切换为 $it") }
                                .onFailure { error = (it as? ApiException)?.message ?: "切换失败（token 需要 Zone Settings:Edit）" }
                            busy = false
                        }
                    },
                    Modifier.weight(1f),
                    tonal = true,
                    enabled = !busy
                )
            }
            Spacer(Modifier.height(10.dp))
            Row {
                LanosButton(
                    "下发 WAF 规则",
                    {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching { LanosApi.cfPushRules(site.id) }
                                .onSuccess {
                                    toast("已下发 ${it.pushed} 条，跳过 ${it.skipped} 条（免费版上限 5）")
                                }
                                .onFailure {
                                    error = (it as? ApiException)?.message
                                        ?: "下发失败（token 需要 Zone.WAF:Edit）"
                                }
                            busy = false
                        }
                    },
                    Modifier.weight(1f),
                    enabled = !busy
                )
                Spacer(Modifier.width(10.dp))
                LanosButton("断开", { disconnect() }, Modifier.weight(1f), tonal = true, enabled = !busy)
            }
        } else {
            Text(
                "在 Cloudflare 后台建一个 API Token（权限 Zone.Analytics:Read + Zone:Read），把 Zone ID 和 Token 填进来即可，之后每 30 分钟自动拉数据，不用跑任何脚本。",
                style = MaterialTheme.typography.bodySmall,
                color = pal.sub
            )
            Spacer(Modifier.height(12.dp))
            LanosTextField(
                value = zoneId,
                onValueChange = { zoneId = it.trim() },
                label = "Zone ID",
                modifier = Modifier.fillMaxWidth(),
                placeholder = "32 位十六进制",
                keyboardType = KeyboardType.Ascii
            )
            Spacer(Modifier.height(10.dp))
            LanosTextField(
                value = token,
                onValueChange = { token = it.trim() },
                label = "API Token",
                modifier = Modifier.fillMaxWidth(),
                placeholder = "粘贴 token",
                isPassword = true
            )
            Spacer(Modifier.height(14.dp))
            LanosButton(
                "验证并接入",
                { connect() },
                Modifier.fillMaxWidth(),
                enabled = zoneId.isNotBlank() && token.isNotBlank() && !busy,
                loading = busy
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = pal.bad)
        }
        synced?.let {
            Spacer(Modifier.height(6.dp))
            Text("本次写入 $it 个时间点", style = MaterialTheme.typography.bodySmall, color = pal.good)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "提示：token 存在后端数据库里，只用于读取统计；建议单独建一个只读 token。",
            style = MaterialTheme.typography.labelSmall,
            color = pal.sub
        )
    }
}
