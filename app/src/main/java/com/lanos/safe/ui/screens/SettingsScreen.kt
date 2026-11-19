package com.lanos.safe.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.rethrowIfCancelled
import com.lanos.safe.data.ApiException
import com.lanos.safe.data.LanosApi
import com.lanos.safe.data.TotpSetup
import com.lanos.safe.data.UserInfo
import com.lanos.safe.ui.design.DesignMode
import com.lanos.safe.ui.design.Hairline
import com.lanos.safe.ui.design.KeyValueRow
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosCard
import com.lanos.safe.ui.design.LanosDialog
import com.lanos.safe.ui.design.LanosSegmented
import com.lanos.safe.ui.design.LanosSwitch
import com.lanos.safe.ui.design.LanosTextField
import com.lanos.safe.ui.design.LanosTopBar
import com.lanos.safe.ui.design.NavigationRow
import com.lanos.safe.ui.design.PillTag
import com.lanos.safe.ui.design.SectionHeader
import com.lanos.safe.ui.design.appear
import kotlinx.coroutines.launch

/** 设置页：外观 / 服务端 / 账号（含 2FA 弹窗）/ 关于。状态全部由外层持有，本页只回调。 */
@Composable
fun SettingsScreen(
    mode: DesignMode,
    dark: Boolean,
    baseUrl: String,
    onModeChange: (DesignMode) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onLoggedOut: () -> Unit
) {
    val pal = Lanos.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<UserInfo?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var show2fa by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<LegalKind?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    // 拉取账号信息；失败时行内提示而不是一直停在“读取中”
    suspend fun reloadUser() {
        runCatching { LanosApi.me() }
            .onSuccess { user = it; loadFailed = false }
            .onFailure { it.rethrowIfCancelled(); loadFailed = true }
    }
    LaunchedEffect(Unit) { reloadUser() }

    // 测试连接：临时把全局 baseUrl 指向输入框地址，失败则还原
    fun testConnection() {
        if (testing) return
        val target = baseUrl.trim().trimEnd('/')
        if (target.isBlank()) { toast("请先填写后端地址"); return }
        scope.launch {
            testing = true
            val prev = LanosApi.baseUrl
            LanosApi.baseUrl = target
            val ok = runCatching { LanosApi.health() }.getOrDefault(false)
            if (!ok) LanosApi.baseUrl = prev
            testing = false
            toast(if (ok) "连接成功" else "连接失败")
        }
    }

    fun saveBaseUrl() {
        val target = baseUrl.trim().trimEnd('/')
        if (target.isBlank()) { toast("地址不能为空"); return }
        LanosApi.baseUrl = target
        onBaseUrlChange(target)
        toast("已保存")
    }

    fun logout() {
        scope.launch {
            runCatching { LanosApi.logout() }
            onLoggedOut()
        }
    }

    fun openGitHub() {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xiaoze0309/LanOS-S.A.F.E")))
        } catch (e: ActivityNotFoundException) {
            toast("未找到可用的浏览器")
        }
    }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LanosTopBar(
                title = "设置",
                subtitle = "外观 · 账号 · 安全",
                action = { AccountAvatar(user?.email) }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { SectionHeader("外观") }
                item {
                    LanosCard(Modifier.fillMaxWidth().appear(0)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, contentDescription = null,
                                tint = pal.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("设计语言", style = MaterialTheme.typography.bodyLarge,
                                color = pal.text, modifier = Modifier.weight(1f))
                            LanosSegmented(
                                options = listOf("Miuix", "Material"),
                                selectedIndex = if (mode == DesignMode.Miuix) 0 else 1,
                                onSelect = { onModeChange(if (it == 0) DesignMode.Miuix else DesignMode.Material) },
                                modifier = Modifier.width(148.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Miuix 为大圆角与弹性动效，Material 为 Material 3 规范",
                            style = MaterialTheme.typography.bodySmall, color = pal.sub)
                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DarkMode, contentDescription = null,
                                tint = pal.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("深色模式", style = MaterialTheme.typography.bodyLarge, color = pal.text)
                                Text("夜间使用更护眼", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                            }
                            LanosSwitch(checked = dark, onCheckedChange = onDarkChange)
                        }
                        Hairline()
                        Row(Modifier.fillMaxWidth().clickable { toast("请在系统设置中切换") }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("跟随系统", style = MaterialTheme.typography.bodyLarge,
                                color = pal.text, modifier = Modifier.weight(1f))
                            PillTag("未实现", pal.sub)
                        }
                    }
                }

                item { SectionHeader("服务端", "修改后建议重新测试连接") }
                item {
                    LanosCard(Modifier.fillMaxWidth().appear(1)) {
                        LanosTextField(
                            value = baseUrl,
                            onValueChange = onBaseUrlChange,
                            label = "后端地址",
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "http://127.0.0.1:8000",
                            keyboardType = KeyboardType.Uri
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LanosButton("测试连接", { testConnection() }, Modifier.weight(1f),
                                loading = testing, tonal = true, leading = Icons.Rounded.Dns)
                            LanosButton("保存", { saveBaseUrl() }, Modifier.weight(1f))
                        }
                    }
                }

                item { SectionHeader("账号", "当前登录的本地账号") }
                item {
                    LanosCard(Modifier.fillMaxWidth().appear(2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(user?.let { maskEmail(it.email) } ?: "读取中…",
                                    style = MaterialTheme.typography.titleMedium, color = pal.text)
                                Text(
                                    when {
                                        user != null -> "账号 ID：${user!!.id}"
                                        loadFailed -> "账号信息读取失败，请检查后端地址"
                                        else -> "正在获取账号信息"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = pal.sub
                                )
                            }
                            val twoFaOn = user?.totpEnabled == true
                            PillTag(if (twoFaOn) "2FA 已开启" else "2FA 未开启",
                                if (twoFaOn) pal.good else pal.warn)
                        }
                        Spacer(Modifier.height(10.dp))
                        Hairline()
                        NavigationRow(
                            title = "双因素认证",
                            subtitle = "Authenticator + 邮箱验证码",
                            leading = Icons.Rounded.Security,
                            onClick = { show2fa = true }
                        )
                        Hairline()
                        NavigationRow(
                            title = "退出登录",
                            subtitle = null,
                            leading = Icons.Rounded.Logout,
                            leadingTint = pal.bad,
                            onClick = { logout() }
                        )
                    }
                }

                item { SectionHeader("关于") }
                item {
                    LanosCard(Modifier.fillMaxWidth().appear(3)) {
                        KeyValueRow("版本", "2.8")
                        KeyValueRow("架构", "Kotlin + FastAPI + MySQL + Uvicorn")
                        Spacer(Modifier.height(4.dp))
                        Hairline()
                        Row(Modifier.fillMaxWidth().clickable { openGitHub() }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, contentDescription = null,
                                tint = pal.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Xiaoze0309/LanOS-S.A.F.E", style = MaterialTheme.typography.bodyMedium,
                                color = pal.accent, modifier = Modifier.weight(1f))
                            Text("在浏览器打开", style = MaterialTheme.typography.labelSmall, color = pal.sub)
                        }
                        Hairline()
                        NavigationRow(
                            title = "服务协议",
                            subtitle = "使用规则与免责条款",
                            leading = Icons.Rounded.Description,
                            onClick = { legal = LegalKind.Terms }
                        )
                        Hairline()
                        NavigationRow(
                            title = "隐私政策",
                            subtitle = "收集哪些数据、怎么用",
                            leading = Icons.Rounded.PrivacyTip,
                            onClick = { legal = LegalKind.Privacy }
                        )

                    }
                }
            }
        }
    }

    legal?.let { kind ->
        LegalDialog(kind) { legal = null }
    }
    if (show2fa) {
        TwoFactorDialog(
            enabled = user?.totpEnabled == true,
            onDismiss = { show2fa = false },
            onChanged = { scope.launch { reloadUser() } }
        )
    }
}

/** 右上角账号首字母头像，无点击行为 */
@Composable
private fun AccountAvatar(email: String?) {
    val pal = Lanos.palette
    val initial = email?.trim()?.take(1)?.uppercase().orEmpty().ifEmpty { "L" }
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(pal.accent),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

/** 2FA 绑定 / 关闭弹窗：未开启走 TOTP 绑定，已开启走密码关闭 */
@Composable
private fun TwoFactorDialog(enabled: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val pal = Lanos.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var setup by remember { mutableStateOf<TotpSetup?>(null) }
    var code by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("lanos", text))
        toast("已复制")
    }

    // 统一包装异步动作的 loading / 错误 / 成功提示；dismiss 决定成功后是否关弹窗
    fun runBusy(dismiss: Boolean, okMsg: String? = null, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                block()
                okMsg?.let { toast(it) }
                onChanged()
                if (dismiss) onDismiss()
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "网络异常，请稍后重试"
            } finally {
                busy = false
            }
        }
    }

    fun startSetup() = runBusy(dismiss = false) { setup = LanosApi.setupTotp() }

    fun enable() {
        if (code.length != 6) { error = "请输入 6 位验证码"; return }
        runBusy(dismiss = true, okMsg = "已开启 2FA") { LanosApi.enableTotp(code) }
    }

    fun disable() {
        if (pwd.isBlank()) { error = "请输入密码"; return }
        runBusy(dismiss = true, okMsg = "已关闭 2FA") { LanosApi.disableTotp(pwd) }
    }

    LanosDialog(
        title = if (enabled) "关闭双因素认证" else "开启双因素认证",
        onDismiss = { if (!busy) onDismiss() }
    ) {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            val hint = "关闭后登录仅校验密码，建议保留；忘记密码请联系管理员。"
            if (enabled) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = pal.sub)
                Spacer(Modifier.height(12.dp))
                LanosTextField(value = pwd, onValueChange = { pwd = it }, label = "登录密码",
                    modifier = Modifier.fillMaxWidth(), placeholder = "输入当前密码", isPassword = true)
                Spacer(Modifier.height(12.dp))
                ErrorLine(error)
                LanosButton("关闭 2FA", { disable() }, Modifier.fillMaxWidth(),
                    enabled = !busy, loading = busy, tonal = true)
            } else if (setup == null) {
                Text("在 Authenticator 中添加密钥后输入 6 位动态码，即可完成绑定。",
                    style = MaterialTheme.typography.bodySmall, color = pal.sub)
                Spacer(Modifier.height(12.dp))
                ErrorLine(error)
                LanosButton("开始绑定", { startSetup() }, Modifier.fillMaxWidth(),
                    enabled = !busy, loading = busy, leading = Icons.Rounded.Security)
            } else {
                val s = setup!!
                Text("秘密密钥（可复制）", style = MaterialTheme.typography.labelMedium, color = pal.sub)
                CopyRow(s.secret, mono = true) { copy(s.secret) }
                Text("otpauth URI（可复制）", style = MaterialTheme.typography.labelMedium, color = pal.sub)
                CopyRow(s.otpauthUri, mono = false) { copy(s.otpauthUri) }
                Spacer(Modifier.height(6.dp))
                Text("备用码（请离线保存）", style = MaterialTheme.typography.labelMedium, color = pal.sub)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(pal.cardAlt).padding(10.dp)) {
                    Column {
                        s.backupCodes.forEach {
                            Text(it, color = pal.text,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LanosTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                    label = "6 位验证码",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "000000",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(12.dp))
                ErrorLine(error)
                LanosButton("验证并开启", { enable() }, Modifier.fillMaxWidth(),
                    enabled = !busy, loading = busy)
            }
        }
    }
}

/** 一行可复制的值（等宽可选） */
@Composable
private fun CopyRow(value: String, mono: Boolean, onCopy: () -> Unit) {
    val pal = Lanos.palette
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            color = pal.text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
            ),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制",
                tint = pal.accent, modifier = Modifier.size(16.dp))
        }
    }
}

/** 邮箱脱敏：保留前 2 位与 @ 之后的域名 */
private fun maskEmail(raw: String): String {
    val at = raw.indexOf('@')
    if (at <= 0) return raw
    return raw.take(2) + "***" + raw.substring(at)
}

/** 弹窗 / 卡片内的红色错误行 */
@Composable
private fun ErrorLine(message: String?) {
    if (message.isNullOrBlank()) return
    Text(message, style = MaterialTheme.typography.bodySmall, color = Lanos.palette.bad,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}
