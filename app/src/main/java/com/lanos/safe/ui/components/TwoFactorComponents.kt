package com.lanos.safe.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 模拟的 16 位密钥（去掉空格后用于复制）
private const val TOTP_SECRET = "JBSWY3DPEHPK3PXP"

// 手绘二维码：黑白方块矩阵，无需真实二维码库
@Composable
fun QrCanvas(
    modifier: Modifier = Modifier
) {
    // 3x3 矩阵图案：1 表示黑块
    val cells = listOf(
        listOf(1, 1, 1, 0, 1, 1, 1),
        listOf(1, 0, 1, 0, 1, 0, 1),
        listOf(1, 1, 1, 0, 1, 1, 1),
        listOf(0, 0, 0, 1, 0, 0, 0),
        listOf(1, 0, 1, 0, 1, 1, 1),
        listOf(1, 0, 1, 1, 0, 1, 0),
        listOf(1, 1, 1, 0, 1, 1, 1)
    )
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        cells.forEach { row ->
            Row {
                row.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(if (cell == 1) Color.Black else Color.White)
                    )
                }
            }
        }
    }
}

// 密钥展示卡片：等宽字体、背景色、复制按钮
@Composable
fun TotpKeyCard(
    modifier: Modifier = Modifier
) {
    // 剪贴板与 Toast 所需对象
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "JBSW Y3DP EHPK 3PXP",
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = {
                // 复制去掉空格的密钥到剪贴板
                clipboard.setText(AnnotatedString(TOTP_SECRET))
                Toast.makeText(context, "密钥已复制", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制密钥",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
