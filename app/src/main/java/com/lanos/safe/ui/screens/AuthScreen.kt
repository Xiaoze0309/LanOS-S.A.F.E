package com.lanos.safe.ui.screens

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.ApiException
import com.lanos.safe.data.LanosApi
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosCard
import com.lanos.safe.ui.design.LanosDialog
import com.lanos.safe.ui.design.LanosSegmented
import com.lanos.safe.ui.design.LanosTextField
import com.lanos.safe.ui.design.PillTag
import com.lanos.safe.ui.design.appear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 验证码渠道：邮箱验证码 / Authenticator 动态码 */
private enum class CodeChannel { Email, Totp }

/** 全屏认证页：登录 / 注册 / 验证码三步共用一张卡片；成功通过 onAuthenticated 回调交权。 */
@Composable
fun AuthScreen(onAuthenticated: (accessToken: String) -> Unit) {
    val pal = Lanos.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var segment by remember { mutableStateOf(0) }            // 0 登录 / 1 注册
    var codeStep by remember { mutableStateOf(false) }       // 是否在验证码步骤
    var registerFlow by remember { mutableStateOf(false) }   // 验证码来自注册还是登录
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mfaToken by remember { mutableStateOf<String?>(null) }
    var methods by remember { mutableStateOf<List<String>>(emptyList()) }
    var channel by remember { mutableStateOf(CodeChannel.Email) }
    var devCode by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReset by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<LegalKind?>(null) }
    var showMs by remember { mutableStateOf(false) }
    var msAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { msAvailable = LanosApi.msLoginEnabled() }

    // 60 秒重发倒计时
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun fail(e: Throwable) {
        error = (e as? ApiException)?.message ?: "网络异常，请稍后重试"
    }
    fun mailError(): String? = if (email.isNotBlank() && !email.contains("@")) "邮箱格式不正确" else null
    fun pwdError(): String? = if (password.isNotBlank() && password.length < 8) "密码至少 8 位" else null

    // 统一处理 loading 与 ApiException
    fun launchBusy(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: ApiException) {
                fail(e)
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading = false
            }
        }
    }

    // 发送登录邮箱验证码；dev_code 非空时展示，便于本地自测
    suspend fun sendLoginCode(token: String) {
        loading = true
        try {
            devCode = LanosApi.sendLoginEmailCode(token)
            countdown = 60
            toast("验证码已发送到邮箱")
        } catch (e: Exception) {
            fail(e)
        } finally {
            loading = false
        }
    }

    fun doLogin() {
        error = null
        if (!email.contains("@")) { error = "请输入正确的邮箱"; return }
        if (password.length < 8) { error = "密码至少 8 位"; return }
        launchBusy {
            val r = LanosApi.login(email.trim(), password)
            when {
                !r.mfaRequired && r.accessToken != null -> onAuthenticated(r.accessToken!!)
                r.mfaRequired && r.mfaToken != null -> {
                    mfaToken = r.mfaToken
                    methods = r.methods
                    registerFlow = false
                    code = ""
                    codeStep = true
                    if (r.methods.contains("email")) {
                        channel = CodeChannel.Email
                        sendLoginCode(r.mfaToken!!)
                    } else {
                        channel = CodeChannel.Totp
                        countdown = 0
                    }
                }
                else -> error = "登录失败：服务端未返回凭证"
            }
        }
    }

    fun doRegister() {
        error = null
        if (!email.contains("@")) { error = "请输入正确的邮箱"; return }
        if (password.length < 8) { error = "密码至少 8 位"; return }
        if (confirm != password) { error = "两次输入的密码不一致"; return }
        launchBusy {
            val r = LanosApi.register(email.trim(), password)
            devCode = r.devCode
            mfaToken = null
            methods = r.methods
            registerFlow = true
            channel = CodeChannel.Email
            code = ""
            codeStep = true
            countdown = 60
            toast("验证码已发送到邮箱")
        }
    }

    fun doVerify() {
        error = null
        if (code.length != 6) { error = "请输入 6 位验证码"; return }
        launchBusy {
            val r = if (registerFlow) {
                LanosApi.registerVerify(email.trim(), code)
            } else {
                val token = mfaToken ?: throw ApiException(400, "会话已过期，请重新登录")
                if (channel == CodeChannel.Totp) LanosApi.loginTotp(token, code)
                else LanosApi.loginEmailCode(token, code)
            }
            val access = r.accessToken
            if (access == null) {
                error = "验证失败，请检查验证码"
            } else {
                onAuthenticated(access)
            }
        }
    }

    fun resend() {
        if (countdown > 0 || loading) return
        error = null
        if (registerFlow) {
            launchBusy {
                devCode = LanosApi.register(email.trim(), password).devCode
                countdown = 60
                toast("验证码已重新发送")
            }
        } else {
            val token = mfaToken
            if (token == null) {
                error = "会话已过期，请重新登录"
            } else {
                scope.launch { sendLoginCode(token) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            Text("LANOS S.A.F.E", style = MaterialTheme.typography.displaySmall, color = pal.text,
                modifier = Modifier.appear(0))
            Spacer(Modifier.height(6.dp))
            Text("多站点流量与安全网关", style = MaterialTheme.typography.bodyMedium, color = pal.sub)
            Spacer(Modifier.height(26.dp))

            if (!codeStep) {
                LanosSegmented(
                    options = listOf("登录", "注册"),
                    selectedIndex = segment,
                    onSelect = { i -> if (i != segment) { segment = i; error = null; code = "" } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
            }

            LanosCard(modifier = Modifier.fillMaxWidth().appear(1), padding = 20.dp) {
                if (codeStep) {
                    CodeStep(
                        email = email,
                        devCode = devCode,
                        code = code,
                        onCodeChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                        channel = channel,
                        // 两种方式都可用时才提供切换入口
                        canSwitchToTotp = methods.contains("totp") && methods.contains("email"),
                        onSwitchChannel = { wanted -> channel = wanted; code = ""; error = null },
                        countdown = countdown,
                        loading = loading,
                        error = error,
                        onVerify = { doVerify() },
                        onResend = { resend() },
                        onBack = { codeStep = false; code = ""; error = null }
                    )
                } else {
                    AuthForm(
                        register = segment == 1,
                        email = email,
                        onEmailChange = { email = it },
                        emailError = mailError(),
                        password = password,
                        onPasswordChange = { password = it },
                        passwordError = pwdError(),
                        confirm = confirm,
                        onConfirmChange = { confirm = it },
                        confirmError = if (confirm.isNotBlank() && confirm != password) "两次输入的密码不一致" else null,
                        loading = loading,
                        error = error,
                        onForgot = { showReset = true },
                        onSubmit = { if (segment == 0) doLogin() else doRegister() }
                    )
                }
            }

            if (!codeStep && msAvailable) {
                Spacer(Modifier.height(10.dp))
                LanosButton(
                    text = "使用 Microsoft 账号登录",
                    onClick = { showMs = true },
                    modifier = Modifier.fillMaxWidth(),
                    tonal = true
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("继续即表示同意", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                Text(
                    "《服务协议》", style = MaterialTheme.typography.bodySmall, color = pal.accent,
                    modifier = Modifier.clickable { legal = LegalKind.Terms }
                )
                Text("与", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                Text(
                    "《隐私政策》", style = MaterialTheme.typography.bodySmall, color = pal.accent,
                    modifier = Modifier.clickable { legal = LegalKind.Privacy }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMs) {
        MicrosoftLoginDialog(
            onDismiss = { showMs = false },
            onAuthenticated = { token -> showMs = false; onAuthenticated(token) }
        )
    }

    legal?.let { kind ->
        LegalDialog(kind) { legal = null }
    }

    if (showReset) {
        ResetPasswordDialog(
            initialEmail = email,
            onDismiss = { showReset = false },
            onDone = { showReset = false; toast("密码已重置，请用新密码登录") }
        )
    }
}

/** 忘记密码：邮箱验证码 + 新密码 */
@Composable
private fun ResetPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val pal = Lanos.palette
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var devCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    fun sendCode() {
        if (countdown > 0 || busy) return
        if (!email.contains("@")) {
            error = "请输入正确的邮箱"
            return
        }
        scope.launch {
            busy = true
            error = null
            runCatching { LanosApi.requestPasswordReset(email.trim()) }
                .onSuccess { devCode = it; countdown = 60 }
                .onFailure { error = (it as? ApiException)?.message ?: "发送失败，请稍后重试" }
            busy = false
        }
    }

    fun submit() {
        if (busy) return
        if (code.length != 6) {
            error = "请输入 6 位验证码"
            return
        }
        if (pwd.length < 8) {
            error = "新密码至少 8 位"
            return
        }
        scope.launch {
            busy = true
            error = null
            runCatching { LanosApi.resetPassword(email.trim(), code, pwd) }
                .onSuccess { onDone() }
                .onFailure { error = (it as? ApiException)?.message ?: "重置失败" }
            busy = false
        }
    }

    LanosDialog(title = "重置密码", onDismiss = { if (!busy) onDismiss() }) {
        Text(
            "验证码会发到你注册的邮箱。部署方没配 SMTP 时，后端（DEV_MODE）会把调试码一并返回。",
            style = MaterialTheme.typography.bodySmall,
            color = pal.sub
        )
        Spacer(Modifier.height(12.dp))
        LanosTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "邮箱",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(10.dp))
        LanosButton(
            text = if (countdown > 0) "重新发送（${countdown}s）" else "发送验证码",
            onClick = { sendCode() },
            modifier = Modifier.fillMaxWidth(),
            enabled = countdown == 0 && !busy,
            loading = busy && countdown == 0,
            tonal = true
        )
        devCode?.let {
            Spacer(Modifier.height(8.dp))
            PillTag(text = "本地调试码：$it", color = pal.warn)
        }
        Spacer(Modifier.height(12.dp))
        LanosTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(6); error = null },
            label = "验证码",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "6 位数字",
            keyboardType = KeyboardType.Number
        )
        Spacer(Modifier.height(10.dp))
        LanosTextField(
            value = pwd,
            onValueChange = { pwd = it; error = null },
            label = "新密码",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "至少 8 位",
            isPassword = true,
            keyboardType = KeyboardType.Password
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = pal.bad)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            LanosButton("取消", { if (!busy) onDismiss() }, Modifier.weight(1f), tonal = true, enabled = !busy)
            Spacer(Modifier.width(10.dp))
            LanosButton(
                "重置",
                { submit() },
                Modifier.weight(1f),
                enabled = code.length == 6 && pwd.length >= 8 && !busy,
                loading = busy
            )
        }
    }
}

/** 登录 / 注册共用的表单主体 */
@Composable
private fun AuthForm(
    register: Boolean,
    email: String,
    onEmailChange: (String) -> Unit,
    emailError: String?,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
    confirm: String,
    onConfirmChange: (String) -> Unit,
    confirmError: String?,
    loading: Boolean,
    error: String?,
    onForgot: () -> Unit,
    onSubmit: () -> Unit
) {
    val pal = Lanos.palette
    LanosTextField(value = email, onValueChange = onEmailChange, label = "邮箱",
        modifier = Modifier.fillMaxWidth(), placeholder = "you@example.com",
        keyboardType = KeyboardType.Email, error = emailError)
    Spacer(Modifier.height(12.dp))
    LanosTextField(value = password, onValueChange = onPasswordChange, label = "密码",
        modifier = Modifier.fillMaxWidth(), placeholder = "至少 8 位", isPassword = true,
        keyboardType = KeyboardType.Password, error = passwordError)
    if (register) {
        Spacer(Modifier.height(12.dp))
        LanosTextField(value = confirm, onValueChange = onConfirmChange, label = "确认密码",
            modifier = Modifier.fillMaxWidth(), placeholder = "再次输入密码", isPassword = true,
            keyboardType = KeyboardType.Password, error = confirmError)
        Spacer(Modifier.height(14.dp))
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("忘记密码？", style = MaterialTheme.typography.labelMedium, color = pal.accent,
                modifier = Modifier.clickable(onClick = onForgot).padding(vertical = 8.dp))
        }
    }
    ErrorLine(error)
    LanosButton(
        text = if (register) "注册并发送验证码" else "登录",
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
        loading = loading
    )
}

/** 验证码步骤：登录 MFA 与注册验证共用；支持切换 Authenticator 与 60 秒重发 */
@Composable
private fun CodeStep(
    email: String,
    devCode: String?,
    code: String,
    onCodeChange: (String) -> Unit,
    channel: CodeChannel,
    canSwitchToTotp: Boolean,
    onSwitchChannel: (CodeChannel) -> Unit,
    countdown: Int,
    loading: Boolean,
    error: String?,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    val pal = Lanos.palette
    Text("验证码已发送到邮箱", style = MaterialTheme.typography.titleMedium, color = pal.text)
    Spacer(Modifier.height(4.dp))
    Text(email, style = MaterialTheme.typography.bodySmall, color = pal.sub)
    if (devCode != null) {
        Spacer(Modifier.height(10.dp))
        PillTag(text = "本地调试码：$devCode", color = pal.warn)
    }
    Spacer(Modifier.height(14.dp))
    LanosTextField(
        value = code,
        onValueChange = onCodeChange,
        label = if (channel == CodeChannel.Totp) "Authenticator 验证码" else "邮箱验证码",
        modifier = Modifier.fillMaxWidth(),
        placeholder = "6 位数字",
        keyboardType = KeyboardType.Number,
        error = if (code.isNotEmpty() && code.length < 6) "请输入 6 位数字" else null
    )
    Spacer(Modifier.height(12.dp))
    ErrorLine(error)
    LanosButton(text = "验证并进入", onClick = onVerify, modifier = Modifier.fillMaxWidth(),
        enabled = !loading, loading = loading)
    if (canSwitchToTotp) {
        Spacer(Modifier.height(8.dp))
        val toTotp = channel == CodeChannel.Email
        LanosButton(
            text = if (toTotp) "改用 Authenticator 验证码" else "改用邮箱验证码",
            onClick = { onSwitchChannel(if (toTotp) CodeChannel.Totp else CodeChannel.Email) },
            modifier = Modifier.fillMaxWidth(), enabled = !loading, tonal = true
        )
    }
    if (channel == CodeChannel.Email) {
        Spacer(Modifier.height(8.dp))
        LanosButton(
            text = if (countdown > 0) "重新发送（${countdown}s）" else "重新发送",
            onClick = onResend,
            modifier = Modifier.fillMaxWidth(), enabled = countdown == 0 && !loading, tonal = true
        )
    }
    Spacer(Modifier.height(10.dp))
    Text("返回上一步", style = MaterialTheme.typography.labelMedium, color = pal.sub,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 6.dp))
}

/** 按钮上方的红色错误行 */
@Composable
private fun ErrorLine(message: String?) {
    if (message.isNullOrBlank()) return
    Text(message, style = MaterialTheme.typography.bodySmall, color = Lanos.palette.bad,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

/** Microsoft 设备码登录：展示 6 位码 + 打开浏览器 + 后台轮询 */
@Composable
private fun MicrosoftLoginDialog(
    onDismiss: () -> Unit,
    onAuthenticated: (String) -> Unit
) {
    val pal = Lanos.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<LanosApi.MsDeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expired by remember { mutableStateOf(false) }

    fun start() {
        scope.launch {
            error = null
            expired = false
            info = null
            runCatching { LanosApi.msStart() }
                .onSuccess { info = it }
                .onFailure { error = (it as? ApiException)?.message ?: "无法启动微软登录" }
        }
    }
    LaunchedEffect(Unit) { start() }

    LaunchedEffect(info?.deviceCode) {
        val d = info ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + d.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(d.interval.coerceAtLeast(5).toLong() * 1000)
            val result = runCatching { LanosApi.msPoll(d.deviceCode) }.getOrNull()
            if (result?.accessToken != null) {
                onAuthenticated(result.accessToken)
                return@LaunchedEffect
            }
        }
        expired = true
    }

    LanosDialog(title = "使用 Microsoft 账号", onDismiss = { onDismiss() }) {
        when {
            error != null -> {
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = pal.bad)
                Spacer(Modifier.height(12.dp))
                LanosButton("重试", { start() }, Modifier.fillMaxWidth(), tonal = true)
            }
            info == null -> {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = pal.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("正在向微软申请验证码…", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                }
            }
            else -> {
                val d = info!!
                Text("用浏览器打开下面的地址，输入这串代码：", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                Spacer(Modifier.height(6.dp))
                Text(d.verificationUri, style = MaterialTheme.typography.bodySmall, color = pal.accent)
                Spacer(Modifier.height(10.dp))
                Text(d.userCode, style = MaterialTheme.typography.displaySmall, color = pal.text)
                Spacer(Modifier.height(12.dp))
                Row {
                    LanosButton("复制代码", {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("microsoft code", d.userCode))
                    }, Modifier.weight(1f), tonal = true)
                    Spacer(Modifier.width(10.dp))
                    LanosButton("打开浏览器", {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.verificationUri)))
                        }
                    }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (expired) "已超时，请关闭重试" else "登录完成后会自动进入，无需手动返回",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expired) pal.warn else pal.sub
                )
            }
        }
    }
}
