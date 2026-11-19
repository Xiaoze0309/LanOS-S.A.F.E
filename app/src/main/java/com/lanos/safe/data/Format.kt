package com.lanos.safe.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 数值/时间格式化：全站统一口径，避免各页面自造。 */
fun formatCount(n: Long): String = when {
    n >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", n / 1_000_000_000.0)
    n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 10_000L -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
private val mmdd = SimpleDateFormat("MM-dd", Locale.getDefault())
private val mmddhhmm = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

/** 图表 X 轴：短周期显示时分，长周期显示月日。 */
fun formatAxisTime(epochSeconds: Long, shortRange: Boolean): String {
    val d = Date(epochSeconds * 1000)
    return if (shortRange) hhmm.format(d) else mmdd.format(d)
}

fun formatClock(epochSeconds: Long): String = mmddhhmm.format(Date(epochSeconds * 1000))

/** 后端返回的 ISO 时间（2026-11-18T05:12:00）→ 05:12 / 11-18 05:12 */
fun formatIsoShort(iso: String): String {
    if (iso.isBlank()) return "—"
    return runCatching {
        val normalized = iso.replace("T", " ").removeSuffix("Z").substringBefore(".")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(normalized)
            ?: return@runCatching normalized.take(16)
        val now = System.currentTimeMillis()
        val diff = abs(now - date.time)
        if (diff < 24 * 3600_000L) hhmm.format(date) else mmddhhmm.format(date)
    }.getOrDefault(iso.take(16))
}

/** 相对时间：刚刚 / 3 分钟前 / 2 小时前 */
fun relativeTime(iso: String): String {
    if (iso.isBlank()) return "—"
    return runCatching {
        val normalized = iso.replace("T", " ").removeSuffix("Z").substringBefore(".")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(normalized)
            ?: return@runCatching iso.take(16)
        val diff = (System.currentTimeMillis() - date.time) / 1000
        when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60} 分钟前"
            diff < 86400 -> "${diff / 3600} 小时前"
            else -> "${diff / 86400} 天前"
        }
    }.getOrDefault(iso.take(16))
}
