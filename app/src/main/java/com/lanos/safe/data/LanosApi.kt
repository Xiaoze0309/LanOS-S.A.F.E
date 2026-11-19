package com.lanos.safe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/* ---------------------------------------------------------------------------------------------
 * 数据层：只依赖 JDK HttpURLConnection + org.json，不引入 Retrofit/Ktor，保证构建轻量。
 * 所有接口失败都会抛 ApiException(code, 中文提示)，页面统一 catch 后弹提示。
 * ------------------------------------------------------------------------------------------- */

class ApiException(val code: Int, override val message: String) : IOException(message)

data class TrafficPoint(val t: Long, val requests: Long, val bytes: Long, val blocked: Long)

data class TrafficSeries(
    val points: List<TrafficPoint>,
    val totalRequests: Long,
    val totalBytes: Long,
    val totalBlocked: Long,
    val bucket: String
)

data class DomainItem(
    val id: Int,
    val domain: String,
    val status: String,
    val verifyToken: String,
    val dnsName: String,
    val dnsValue: String,
    val httpPath: String,
    val createdAt: String
) {
    val verified: Boolean get() = status == "verified"
}

data class WafRuleItem(
    val id: Int,
    val name: String,
    val pattern: String,
    val target: String,
    val action: String,
    val severity: String,
    val enabled: Boolean,
    val builtin: Boolean
)

data class WafEventItem(
    val id: Int,
    val ip: String,
    val method: String,
    val path: String,
    val action: String,
    val ruleName: String,
    val score: Int,
    val createdAt: String
)

data class CountItem(val label: String, val value: Long)

data class WafStats(
    val scanned: Long,
    val blocked: Long,
    val topAttacks: List<CountItem>,
    val topIps: List<CountItem>
)

data class AuthResult(
    val accessToken: String?,
    val refreshToken: String?,
    val mfaRequired: Boolean,
    val mfaToken: String?,
    val methods: List<String>,
    val devCode: String?
)

data class UserInfo(val id: Int, val email: String, val totpEnabled: Boolean)

data class TotpSetup(val secret: String, val otpauthUri: String, val backupCodes: List<String>)

data class VerifyResult(val verified: Boolean, val dnsOk: Boolean, val httpOk: Boolean, val message: String)

object LanosApi {

    /** 默认指向本机；在设置页可改成局域网地址，例如 http://192.168.1.5:8000 */
    @Volatile
    var baseUrl: String = "http://127.0.0.1:8000"

    @Volatile
    var accessToken: String? = null

    @Volatile
    var refreshToken: String? = null

    // ---------------------------------------------------------------- 认证

    suspend fun register(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email).put("password", password)
        return obj(call("POST", "/api/v1/auth/register", body, auth = false)).toAuth()
    }

    suspend fun registerVerify(email: String, code: String): AuthResult {
        val body = JSONObject().put("email", email).put("code", code)
        return obj(call("POST", "/api/v1/auth/register/verify", body, auth = false)).toAuth()
    }

    suspend fun login(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email).put("password", password)
        return obj(call("POST", "/api/v1/auth/login", body, auth = false)).toAuth()
    }

    suspend fun loginTotp(mfaToken: String, code: String): AuthResult {
        val body = JSONObject().put("mfa_token", mfaToken).put("code", code)
        return obj(call("POST", "/api/v1/auth/login/totp", body, auth = false)).toAuth()
    }

    /** 触发一次邮箱验证码，返回 dev_code（仅本地开发模式有值，正式环境为 null） */
    suspend fun sendLoginEmailCode(mfaToken: String): String? {
        val body = JSONObject().put("mfa_token", mfaToken)
        return obj(call("POST", "/api/v1/auth/login/email-code", body, auth = false)).optString("dev_code", "").ifBlank { null }
    }

    suspend fun loginEmailCode(mfaToken: String, code: String): AuthResult {
        val body = JSONObject().put("mfa_token", mfaToken).put("code", code)
        return obj(call("POST", "/api/v1/auth/login/email-code/verify", body, auth = false)).toAuth()
    }

    /** 忘记密码：请求验证码（DEV_MODE 下返回 dev_code） */
    suspend fun requestPasswordReset(email: String): String? =
        obj(
            call(
                "POST",
                "/api/v1/auth/password/reset-code",
                JSONObject().put("email", email),
                auth = false
            )
        ).optString("dev_code", "").ifBlank { null }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        call(
            "POST",
            "/api/v1/auth/password/reset",
            JSONObject()
                .put("email", email)
                .put("code", code)
                .put("new_password", newPassword),
            auth = false
        )
    }

    // ---------------------------------------------------------------- Microsoft 登录

    data class MsDeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Int,
        val expiresIn: Int
    )

    suspend fun msLoginEnabled(): Boolean = runCatching {
        obj(call("GET", "/api/v1/auth/oauth/microsoft/config", auth = false)).optBoolean("enabled")
    }.getOrDefault(false)

    suspend fun msStart(): MsDeviceCode {
        val o = obj(call("POST", "/api/v1/auth/oauth/microsoft/device/start", auth = false))
        return MsDeviceCode(
            deviceCode = o.optString("device_code"),
            userCode = o.optString("user_code"),
            verificationUri = o.optString("verification_uri", "https://microsoft.com/devicelogin"),
            interval = o.optInt("interval", 5),
            expiresIn = o.optInt("expires_in", 900)
        )
    }

    /** 轮询；返回 null 表示还在等用户在浏览器里确认 */
    suspend fun msPoll(deviceCode: String): AuthResult? {
        val o = obj(
            call(
                "POST",
                "/api/v1/auth/oauth/microsoft/device/poll",
                JSONObject().put("device_code", deviceCode),
                auth = false
            )
        )
        return if (o.optString("status") == "ok") o.toAuth() else null
    }

    suspend fun me(): UserInfo {
        val o = obj(call("GET", "/api/v1/auth/me"))
        return UserInfo(o.optInt("id"), o.optString("email"), o.optBoolean("totp_enabled"))
    }

    suspend fun setupTotp(): TotpSetup {
        val o = obj(call("POST", "/api/v1/auth/2fa/setup"))
        val codes = o.optJSONArray("backup_codes") ?: JSONArray()
        return TotpSetup(
            secret = o.optString("secret"),
            otpauthUri = o.optString("otpauth_uri"),
            backupCodes = List(codes.length()) { codes.getString(it) }
        )
    }

    suspend fun enableTotp(code: String) {
        call("POST", "/api/v1/auth/2fa/enable", JSONObject().put("code", code))
    }

    suspend fun disableTotp(password: String) {
        call("POST", "/api/v1/auth/2fa/disable", JSONObject().put("password", password))
    }

    suspend fun logout() {
        runCatching { call("POST", "/api/v1/auth/logout") }
        accessToken = null
        refreshToken = null
    }

    // ---------------------------------------------------------------- 域名

    suspend fun domains(): List<DomainItem> = withContext(Dispatchers.IO) {
        val arr = JSONArray(call("GET", "/api/v1/domains"))
        List(arr.length()) { arr.getJSONObject(it).toDomain() }
    }

    suspend fun addDomain(domain: String): DomainItem =
        obj(call("POST", "/api/v1/domains", JSONObject().put("domain", domain))).toDomain()

    suspend fun verifyDomain(id: Int): VerifyResult {
        val o = obj(call("POST", "/api/v1/domains/$id/verify"))
        return VerifyResult(
            verified = o.optBoolean("verified"),
            dnsOk = o.optBoolean("dns_ok"),
            httpOk = o.optBoolean("http_ok"),
            message = o.optString("message")
        )
    }

    suspend fun deleteDomain(id: Int) {
        call("DELETE", "/api/v1/domains/$id")
    }

    suspend fun traffic(domainId: Int, range: String): TrafficSeries {
        val o = obj(call("GET", "/api/v1/domains/$domainId/traffic?range=$range"))
        val arr = o.optJSONArray("points") ?: JSONArray()
        return TrafficSeries(
            points = List(arr.length()) { i ->
                val p = arr.getJSONObject(i)
                TrafficPoint(
                    t = p.optLong("t"),
                    requests = p.optLong("requests"),
                    bytes = p.optLong("bytes"),
                    blocked = p.optLong("blocked")
                )
            },
            totalRequests = o.optLong("total_requests"),
            totalBytes = o.optLong("total_bytes"),
            totalBlocked = o.optLong("total_blocked"),
            bucket = o.optString("bucket", "minute")
        )
    }

    // ---------------------------------------------------------------- Cloudflare

    data class CloudflareStatus(
        val connected: Boolean,
        val zoneName: String,
        val lastSync: String,
        val lastError: String?
    )

    suspend fun cfStatus(siteId: Int): CloudflareStatus {
        val o = obj(call("GET", "/api/v1/integrations/cloudflare/$siteId"))
        return CloudflareStatus(
            connected = o.optBoolean("connected"),
            zoneName = o.optString("zone_name"),
            lastSync = o.optString("last_sync_at"),
            lastError = o.optString("last_error", "").ifBlank { null }
        )
    }

    suspend fun cfConnect(siteId: Int, zoneId: String, token: String): CloudflareStatus {
        val body = JSONObject().put("site_id", siteId).put("zone_id", zoneId).put("token", token)
        val o = obj(call("POST", "/api/v1/integrations/cloudflare", body))
        return CloudflareStatus(
            connected = o.optBoolean("connected"),
            zoneName = o.optString("zone_name"),
            lastSync = o.optString("last_sync_at"),
            lastError = o.optString("last_error", "").ifBlank { null }
        )
    }

    suspend fun cfDisconnect(siteId: Int) {
        call("DELETE", "/api/v1/integrations/cloudflare/$siteId")
    }

    data class CfPushResult(val pushed: Int, val skipped: Int, val cfRules: Int)

    suspend fun cfPushRules(siteId: Int): CfPushResult {
        val o = obj(call("POST", "/api/v1/integrations/cloudflare/$siteId/rules/push"))
        return CfPushResult(
            pushed = o.optInt("pushed"),
            skipped = o.optInt("skipped"),
            cfRules = o.optInt("rules")
        )
    }

    suspend fun cfSecurity(siteId: Int): String =
        obj(call("GET", "/api/v1/integrations/cloudflare/$siteId/security")).optString("level")

    suspend fun cfSetSecurity(siteId: Int, level: String): String =
        obj(
            call(
                "POST",
                "/api/v1/integrations/cloudflare/$siteId/security",
                JSONObject().put("level", level)
            )
        ).optString("level")

    suspend fun cfSyncNow(siteId: Int): Int =
        obj(call("POST", "/api/v1/integrations/cloudflare/$siteId/sync")).optInt("points")

    // ---------------------------------------------------------------- WAF

    suspend fun wafRules(): List<WafRuleItem> = withContext(Dispatchers.IO) {
        val arr = JSONArray(call("GET", "/api/v1/waf/rules"))
        List(arr.length()) { arr.getJSONObject(it).toRule() }
    }

    suspend fun addWafRule(
        name: String,
        pattern: String,
        target: String,
        action: String,
        severity: String
    ): WafRuleItem {
        val body = JSONObject()
            .put("name", name)
            .put("pattern", pattern)
            .put("target", target)
            .put("action", action)
            .put("severity", severity)
        return obj(call("POST", "/api/v1/waf/rules", body)).toRule()
    }

    suspend fun setWafRuleEnabled(id: Int, enabled: Boolean) {
        call("PATCH", "/api/v1/waf/rules/$id", JSONObject().put("enabled", enabled))
    }

    suspend fun deleteWafRule(id: Int) {
        call("DELETE", "/api/v1/waf/rules/$id")
    }

    suspend fun wafEvents(limit: Int = 30): List<WafEventItem> = withContext(Dispatchers.IO) {
        val arr = JSONArray(call("GET", "/api/v1/waf/events?limit=$limit"))
        List(arr.length()) { arr.getJSONObject(it).toEvent() }
    }

    suspend fun wafStats(range: String = "24h"): WafStats {
        val o = obj(call("GET", "/api/v1/waf/stats?range=$range"))
        return WafStats(
            scanned = o.optLong("scanned"),
            blocked = o.optLong("blocked"),
            topAttacks = readCounts(o.optJSONArray("top_attacks")),
            topIps = readCounts(o.optJSONArray("top_ips"))
        )
    }

    /** 演示数据（仅后端 DEV_MODE）：生成一年份流量与攻击事件，返回写入的流量点数。 */
    suspend fun seedDemo(): Int =
        obj(call("POST", "/api/v1/system/demo/seed")).optInt("traffic_points")

    suspend fun health(): Boolean = runCatching {
        obj(call("GET", "/api/v1/system/health", auth = false)).optBoolean("ok")
    }.getOrDefault(false)

    // ---------------------------------------------------------------- 内部

    private fun readCounts(arr: JSONArray?): List<CountItem> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CountItem(o.optString("label"), o.optLong("value"))
        }
    }

    private data class Raw(val code: Int, val text: String)

    private suspend fun rawCall(
        method: String,
        path: String,
        body: JSONObject?,
        auth: Boolean
    ): Raw = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (auth) accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            Raw(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    /** 访问令牌过期（401）时自动用刷新令牌换一次，再重试原请求。 */
    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true
    ): String {
        var response = rawCall(method, path, body, auth)
        if (response.code == 401 && auth && refreshToken != null) {
            if (refreshAccess()) {
                response = rawCall(method, path, body, auth)
            }
        }
        if (response.code !in 200..299) throw ApiException(response.code, detailOf(response.text, response.code))
        return response.text
    }

    private suspend fun refreshAccess(): Boolean {
        val token = refreshToken ?: return false
        val response = rawCall(
            "POST",
            "/api/v1/auth/refresh",
            JSONObject().put("refresh_token", token),
            auth = false
        )
        if (response.code !in 200..299) {
            accessToken = null
            refreshToken = null
            return false
        }
        val body = runCatching { JSONObject(response.text) }.getOrNull() ?: return false
        val fresh = body.optString("access_token", "").ifBlank { null } ?: return false
        accessToken = fresh
        val rotated = body.optString("refresh_token", "")
        if (rotated.isNotBlank()) refreshToken = rotated
        return true
    }

    private fun detailOf(raw: String, code: Int): String {
        if (raw.isBlank()) return "请求失败（HTTP $code）"
        return runCatching {
            val d = JSONObject(raw).opt("detail")
            when (d) {
                is String -> d
                is JSONArray -> (0 until d.length()).joinToString("；") { i ->
                    val item = d.opt(i)
                    if (item is JSONObject) item.optString("msg", item.toString()) else item.toString()
                }
                null -> "请求失败（HTTP $code）"
                else -> d.toString()
            }
        }.getOrElse { "请求失败（HTTP $code）" }
    }

    private fun obj(text: String): JSONObject = if (text.isBlank()) JSONObject() else JSONObject(text)

    private fun JSONObject.toAuth(): AuthResult {
        val methodsArr = optJSONArray("methods") ?: JSONArray()
        val result = AuthResult(
            accessToken = optString("access_token", "").ifBlank { null },
            refreshToken = optString("refresh_token", "").ifBlank { null },
            mfaRequired = optBoolean("mfa_required"),
            mfaToken = optString("mfa_token", "").ifBlank { null },
            methods = List(methodsArr.length()) { methodsArr.getString(it) },
            devCode = optString("dev_code", "").ifBlank { null }
        )
        // 登录成功后立刻把令牌放回内存，后续请求与自动刷新都依赖它
        if (result.accessToken != null) {
            accessToken = result.accessToken
            refreshToken = result.refreshToken ?: refreshToken
        }
        return result
    }

    private fun JSONObject.toDomain() = DomainItem(
        id = optInt("id"),
        domain = optString("domain"),
        status = optString("status", "pending"),
        verifyToken = optString("verify_token"),
        dnsName = optString("dns_name"),
        dnsValue = optString("dns_value"),
        httpPath = optString("http_path", "/.well-known/lanos-verify.txt"),
        createdAt = optString("created_at")
    )

    private fun JSONObject.toRule() = WafRuleItem(
        id = optInt("id"),
        name = optString("name"),
        pattern = optString("pattern"),
        target = optString("target", "path"),
        action = optString("action", "block"),
        severity = optString("severity", "medium"),
        enabled = optBoolean("enabled", true),
        builtin = optBoolean("builtin")
    )

    private fun JSONObject.toEvent() = WafEventItem(
        id = optInt("id"),
        ip = optString("ip"),
        method = optString("method"),
        path = optString("path"),
        action = optString("action"),
        ruleName = optString("rule_name", "—"),
        score = optInt("score"),
        createdAt = optString("created_at")
    )
}
