package com.lanos.safe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.theme.AccentBlue
import com.lanos.safe.ui.theme.Hairline
import kotlin.math.cos
import kotlin.math.sin

// 流量趋势折线图：网格线 + 渐变填充 + 波动曲线（模拟实时流量）
@Composable
fun TrafficChart(
    modifier: Modifier = Modifier,
    seed: Int = 0
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val w = size.width
        val h = size.height
        // 网格线
        val cols = 6
        val rows = 4
        for (i in 0..cols) {
            val x = w * i / cols
            drawLine(
                color = Hairline,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
        }
        for (i in 0..rows) {
            val y = h * i / rows
            drawLine(
                color = Hairline,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }
        // 波动曲线数据点
        val points = List(24) { i ->
            val x = w * i / 23f
            val phase = (i + seed * 3) * 0.7f
            val y = h * 0.55f - h * 0.25f * sin(phase).toFloat() -
                h * 0.15f * cos(phase * 0.6f).toFloat()
            Offset(x, y)
        }
        // 渐变填充路径
        val fill = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )
        // 曲线
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = line,
            color = AccentBlue,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}
