package com.lanos.safe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.TrafficPoint
import com.lanos.safe.data.formatAxisTime
import com.lanos.safe.data.formatCount
import com.lanos.safe.ui.design.Lanos
import kotlin.math.max

/**
 * 流量面积图：三次贝塞尔平滑曲线 + 渐变填充 + 入场自左向右生长，
 * 手指横向拖动可拾取某个数据点（竖线 + 高亮圆点 + 上方读数）。
 */
@Composable
fun TrafficChart(
    points: List<TrafficPoint>,
    shortRange: Boolean,
    modifier: Modifier = Modifier
) {
    val pal = Lanos.palette
    val anim = remember { Animatable(0f) }
    var selected by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(points) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis = 720, easing = FastOutSlowInEasing))
    }

    val peak = remember(points) { max(1L, points.maxOfOrNull { it.requests } ?: 1L) }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(168.dp), contentAlignment = Alignment.Center) {
                    Text("暂无流量数据", style = MaterialTheme.typography.bodySmall, color = pal.sub)
                }
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .pointerInput(points) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> selected = indexAt(offset.x, size.width, points.size) },
                                onHorizontalDrag = { change, _ ->
                                    selected = indexAt(change.position.x, size.width, points.size)
                                },
                                onDragEnd = { },
                                onDragCancel = { }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val bottom = h - 6f
                    val top = 8f
                    val usable = bottom - top

                    // 网格（三条虚线）
                    val grid = PathEffect.dashPathEffect(floatArrayOf(5f, 9f))
                    for (i in 1..3) {
                        val y = top + usable * i / 4f
                        drawLine(
                            color = pal.hairline.copy(alpha = 0.8f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = grid
                        )
                    }

                    fun xAt(i: Int): Float = if (points.size == 1) w / 2f else w * i / (points.size - 1).toFloat()
                    fun yAt(v: Long): Float = bottom - (v.toFloat() / peak.toFloat()) * usable

                    val line = Path()
                    val fill = Path()
                    points.forEachIndexed { i, p ->
                        val x = xAt(i)
                        val y = yAt(p.requests)
                        if (i == 0) {
                            line.moveTo(x, y)
                            fill.moveTo(x, bottom)
                            fill.lineTo(x, y)
                        } else {
                            val px = xAt(i - 1)
                            val py = yAt(points[i - 1].requests)
                            val cx = (px + x) / 2f
                            line.cubicTo(cx, py, cx, y, x, y)
                            fill.cubicTo(cx, py, cx, y, x, y)
                        }
                    }
                    fill.lineTo(xAt(points.lastIndex), bottom)
                    fill.close()

                    clipRect(right = w * anim.value) {
                        drawPath(
                            path = fill,
                            brush = Brush.verticalGradient(
                                listOf(pal.chartTop.copy(alpha = 0.38f), pal.chartTop.copy(alpha = 0.02f)),
                                startY = top,
                                endY = bottom
                            )
                        )
                        drawPath(
                            path = line,
                            color = pal.chartTop,
                            style = Stroke(width = 2.6f, cap = StrokeCap.Round)
                        )

                        // 被拦截的部分：用低饱和的红线压在下方
                        if (points.any { it.blocked > 0 }) {
                            val blockedPath = Path()
                            points.forEachIndexed { i, p ->
                                val x = xAt(i)
                                val y = bottom - (p.blocked.toFloat() / peak.toFloat()) * usable
                                if (i == 0) blockedPath.moveTo(x, y) else blockedPath.lineTo(x, y)
                            }
                            drawPath(
                                path = blockedPath,
                                color = pal.bad.copy(alpha = 0.65f),
                                style = Stroke(width = 1.6f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    val idx = selected
                    if (idx != null && idx in points.indices) {
                        val x = xAt(idx)
                        val y = yAt(points[idx].requests)
                        drawLine(
                            color = pal.sub.copy(alpha = 0.45f),
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = 1f
                        )
                        drawCircle(color = pal.card, radius = 7f, center = Offset(x, y))
                        drawCircle(color = pal.chartTop, radius = 4.5f, center = Offset(x, y))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            val idx = selected
            if (idx != null && idx in points.indices) {
                Text(
                    "${formatAxisTime(points[idx].t, shortRange)}  ·  ${formatCount(points[idx].requests)} 请求",
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.text,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (points.isNotEmpty()) {
                Text(
                    formatAxisTime(points.first().t, shortRange),
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.sub
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "峰值 ${formatCount(peak)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.sub
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatAxisTime(points.last().t, shortRange),
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.sub
                )
            }
        }
    }
}

private fun indexAt(x: Float, width: Int, count: Int): Int? {
    if (count <= 0 || width <= 0) return null
    val ratio = (x / width).coerceIn(0f, 1f)
    return if (count == 1) 0 else Math.round(ratio * (count - 1))
}
