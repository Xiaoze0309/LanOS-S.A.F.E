package com.lanos.safe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 终端浅绿色文字
private val TermGreen = Color(0xFF76FF03)
// 暗黑背景色
private val TermBackground = Color(0xFF0D1117)

// 生成命令输出：白名单匹配则返回模拟规则，否则拒绝
private fun outputFor(command: String): List<String> {
    return if (command == "su -c iptables -L") {
        listOf(
            "# 模拟 iptables 规则",
            "Chain INPUT (policy ACCEPT)",
            "target  prot  opt  source  destination",
            "DROP    all   --  0.0.0.0/0  0.0.0.0/0",
            "Chain OUTPUT (policy ACCEPT)",
            "DROP    tcp   --  0.0.0.0/0  0.0.0.0/0"
        )
    } else {
        listOf("[SAFE] 权限拒绝：不在白名单")
    }
}

// 安全终端页（无状态组件）：极简暗黑风格，无需 TopAppBar
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier
) {
    // 日志列表与输入状态
    var logs by remember { mutableStateOf(listOf("欢迎使用 LanOS 安全终端")) }
    var input by remember { mutableStateOf("") }
    // 列表滚动状态：用于自动滚动到底部
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 发送命令：更新日志、清空输入、滚动到底部
    fun send() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        val newLogs = logs + outputFor(cmd)
        logs = newLogs
        input = ""
        scope.launch {
            listState.animateScrollToItem(newLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TermBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部输入框：左侧 # 前缀，右侧发送图标
        TextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(text = "输入命令，如 su -c iptables -L", color = Color.Gray) },
            singleLine = true,
            leadingIcon = {
                Text(text = "#", color = TermGreen, fontFamily = FontFamily.Monospace)
            },
            trailingIcon = {
                IconButton(onClick = { send() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = TermGreen
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161B22),
                unfocusedContainerColor = Color(0xFF161B22),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TermGreen,
                fontFamily = FontFamily.Monospace
            ),
            modifier = Modifier.fillMaxWidth()
        )
        // 日志列表：等宽字体 + 浅绿色
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs) { log ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> ",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = log,
                        color = TermGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
