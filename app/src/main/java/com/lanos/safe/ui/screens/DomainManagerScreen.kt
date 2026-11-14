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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.lanos.safe.ui.theme.SafetyGreen
import com.lanos.safe.ui.theme.SoftGray
// 域名数据模型（本地模拟）
private data class Domain(val name: String, val active: Boolean)
// 域名管理页：极简 TopAppBar + 加号 + 域名列表
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var domains by remember { mutableStateOf(listOf<Domain>()) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "域名管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDialog = true }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "添加域名")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(domains) { domain ->
                DomainCard(
                    domain = domain,
                    onToggle = { on ->
                        domains = domains.map { if (it == domain) it.copy(active = on) else it }
                    }
                )
            }
        }
    }
    if (showDialog) {
        AddDomainDialog(
            onDismiss = { showDialog = false },
            onConfirm = { name ->
                domains = domains + Domain(name = name, active = true)
                showDialog = false
            }
        )
    }
}
// 单个域名卡片：左侧粗体域名 + 绿色状态，右侧开关
@Composable
private fun DomainCard(domain: Domain, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGray),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = domain.name, fontWeight = FontWeight.Bold)
                Text(
                    text = if (domain.active) "防护中" else "已停用",
                    color = if (domain.active) SafetyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = domain.active, onCheckedChange = onToggle)
        }
    }
}
// 添加域名弹窗
@Composable
private fun AddDomainDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加域名") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(text = "域名") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (input.isNotBlank()) onConfirm(input.trim()) }) {
                Text(text = "确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        }
    )
}
