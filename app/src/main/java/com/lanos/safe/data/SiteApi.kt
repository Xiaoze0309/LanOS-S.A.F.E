package com.lanos.safe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// 后端地址常量：局域网 IP，便于后续统一修改
const val BASE_URL = "http://192.168.1.100:8000"

// 域名数据传输对象
data class SiteDto(
    val id: Int,
    val domain: String,
    val isActive: Boolean,
    val createdAt: String
)

// 域名网络层：HttpURLConnection + org.json，不引入第三方网络库
object SiteApi {
    private const val SITES_PATH = "/api/v1/sites"

    // 拉取域名列表（GET）
    suspend fun fetchSites(): List<SiteDto> = withContext(Dispatchers.IO) {
        val conn = open(SITES_PATH, "GET")
        try {
            checkOk(conn.responseCode)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            List(arr.length()) { i -> arr.getJSONObject(i).toSiteDto() }
        } finally {
            conn.disconnect()
        }
    }

    // 添加域名（POST，请求体 {"domain": "xxx"}）
    suspend fun addSite(domain: String): SiteDto = withContext(Dispatchers.IO) {
        val conn = open(SITES_PATH, "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("domain", domain).toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            checkOk(conn.responseCode)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).toSiteDto()
        } finally {
            conn.disconnect()
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn
    }

    private fun checkOk(code: Int) {
        if (code !in 200..299) throw IOException("HTTP $code")
    }

    private fun JSONObject.toSiteDto() = SiteDto(
        id = optInt("id"),
        domain = optString("domain"),
        isActive = optBoolean("is_active", true),
        createdAt = optString("created_at")
    )
}
