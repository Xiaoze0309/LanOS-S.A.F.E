package com.lanos.safe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// 防火墙规则数据模型
private data class FirewallRule(val type: String, val value: String, val enabled: Boolean)

// 规则页：真实规则列表 + FAB 添加规则
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier
) {
    var rules by remember {
        mutableStateOf(
            listOf(
                FirewallRule("IP 封禁", "203.0.113.45", true),
                FirewallRule("域名拦截", "malware.example.com", true),
                FirewallRule("IP 封禁", "198.51.100.7", false)
            )
        )
    }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "规则引擎", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "添加规则")
            }
        }
    ) { pad ->
        if (rules.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(pad),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "暂无规则", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "点击右下角按钮添加规则",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rules, key = { it.type + it.value }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { on ->
                            rules = rules.map { if (it == rule) it.copy(enabled = on) else it }
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddRuleDialog(
            onDismiss = { showDialog = false },
            onConfirm = { type, value ->
                rules = rules + FirewallRule(type, value, true)
                showDialog = false
            }
        )
    }
}

// 单条规则卡片：类型 + 值 + 开关
@Composable
private fun RuleCard(rule: FirewallRule, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = rule.type,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = rule.value, fontWeight = FontWeight.SemiBold)
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

// 添加规则弹窗：输入类型和值
@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var type by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(text = "规则类型") },
                    placeholder = { Text(text = "如 IP 封禁") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(text = "具体值") },
                    placeholder = { Text(text = "如 203.0.113.1") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (type.isNotBlank() && value.isNotBlank()) {
                        onConfirm(type.trim(), value.trim())
                    }
                }
            ) { Text(text = "确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        }
    )
}
