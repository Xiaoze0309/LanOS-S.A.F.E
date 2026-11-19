package com.lanos.safe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosButton
import com.lanos.safe.ui.design.LanosDialog

/** 服务协议 / 隐私政策：正文 + 弹窗两种形态 */
enum class LegalKind { Terms, Privacy }

private const val TERMS = """LanOS S.A.F.E 服务协议
更新日期：2026-11-20

一、服务内容
本应用是一个自托管的站点流量与安全控制台，提供账号认证、域名归属校验、
流量统计与 WAF 规则管理。软件按“现状”提供，不承诺任何可用性指标。

二、账号
1. 你需要使用真实可用的邮箱注册，并对账号下的一切操作负责。
2. 请妥善保管密码，建议开启双因素认证。
3. 一个邮箱仅限一个账号，不得出借、出售账号。

三、使用规范
1. 只能添加你本人拥有或已获授权的域名。
2. 不得利用本服务对他人站点进行扫描、压测、DDoS、撞库或发送垃圾邮件。
3. 不得尝试绕过限流、破坏服务或干扰其他用户。
4. 违反上述约定，我们有权暂停或终止你的账号。

四、数据与内容
你添加的域名、规则与统计数据归你所有；我们仅在你自建的服务器内处理这些数据。

五、免责
1. 因配置错误、服务器故障、第三方服务（Cloudflare、邮件服务商）异常造成的损失，
   我们不承担责任。
2. 安全规则可能误判或漏判，请勿将其作为唯一防护手段。

六、协议变更
协议更新后会在应用内展示，继续使用即视为接受新版本。

七、联系
如有疑问，请通过部署方提供的联系方式反馈。"""

private const val PRIVACY = """LanOS S.A.F.E 隐私政策
更新日期：2026-11-20

一、我们收集什么
1. 账号信息：邮箱、密码的不可逆哈希（PBKDF2）、注册与登录时间。
2. 验证码记录：邮箱、用途、哈希后的验证码、过期时间（不保存明文）。
3. 域名信息：你添加的域名、校验串、验证状态。
4. 流量与安全数据：按小时的请求数、流量字节数、被拦截次数；
   WAF 事件中的来源 IP、User-Agent、请求路径与时间。
5. 可选集成凭据：你主动填写的 Cloudflare API Token 与 Zone ID，仅用于读取统计。

二、用途
仅用于账号认证、二次验证、域名归属校验、流量与安全数据展示、故障排查。

三、存储与安全
1. 数据保存在你自己部署的服务器与数据库中，默认不向任何第三方上传。
2. 密码只保存哈希；访问令牌短期有效并支持吊销；可开启双因素认证。
3. 若通过公网访问，建议启用 HTTPS 反向代理。

四、共享
除法律法规要求或你明确授权外，我们不会向第三方提供你的数据。
Cloudflare 统计由你提供的 Token 直接向 Cloudflare 读取，不经过其他中间方。

五、保留期限
1. 邮箱验证码 10 分钟后失效。
2. 流量与安全事件默认长期保留，删除域名时一并删除。
3. 注销账号后 30 天内清除关联数据。

六、你的权利
你可以随时查看、导出或删除自己的域名与数据；如需注销账号，请联系部署方。

七、未成年人
未满 18 周岁的用户应在监护人同意与指导下使用本服务。

八、联系我们
隐私相关问题请通过部署方提供的联系方式反馈。"""

@Composable
fun LegalDialog(kind: LegalKind, onDismiss: () -> Unit) {
    val pal = Lanos.palette
    LanosDialog(
        title = if (kind == LegalKind.Terms) "服务协议" else "隐私政策",
        onDismiss = onDismiss
    ) {
        Column(
            Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (kind == LegalKind.Terms) TERMS else PRIVACY,
                style = MaterialTheme.typography.bodySmall,
                color = pal.text
            )
        }
        Spacer(Modifier.height(14.dp))
        LanosButton("我知道了", onDismiss, Modifier.fillMaxWidth(), tonal = true)
    }
}

/** 打赏弹窗：收款码图片未内置时给出占位提示 */
@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val pal = Lanos.palette
    LanosDialog(title = "支持作者", onDismiss = onDismiss) {
        Text(
            "如果 LanOS S.A.F.E 帮到了你，可以请作者喝杯奶茶。\n感谢每一份支持，它会直接用在服务器和域名上。",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.text
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pal.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("收款码待配置", style = MaterialTheme.typography.titleMedium, color = pal.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "把微信/支付宝收款码保存为 res/drawable/donate_qr.png 后重新编译即可显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.sub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LanosButton("关闭", onDismiss, Modifier.fillMaxWidth(), tonal = true)
    }
}
