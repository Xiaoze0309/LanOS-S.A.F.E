package com.lanos.safe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanos.safe.data.SiteApi
import com.lanos.safe.data.SiteDto
import com.lanos.safe.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

// 域名管理页：从 FastAPI 后端加载 / 添加域名
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sites by remember { mutableStateOf<List<SiteDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }

    // 加载时发起 GET，失败 Toast 提示
    LaunchedEffect(Unit) {
        loading = true
        sites = try {
            SiteApi.fetchSites()
        } catch (e: Exception) {
            Toast.makeText(context, "网络连接失败", Toast.LENGTH_SHORT).show()
            emptyList()
        }
        loading = false
    }

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
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            sites.isEmpty() -> EmptyState(
                onAdd = { showDialog = true },
                modifier = Modifier.fillMaxSize().padding(pad)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sites, key = { it.id }) { SiteCard(it) }
            }
        }
    }

    if (showDialog) {
        AddSiteDialog(
            onDismiss = { showDialog = false },
            onConfirm = { domain ->
                showDialog = false
                scope.launch {
                    try {
                        SiteApi.addSite(domain)
                        sites = SiteApi.fetchSites()
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络连接失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

// 空状态
@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "暂无域名",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        TextButton(onClick = onAdd) { Text(text = "添加域名") }
    }
}

// 单个域名卡片：URL + 防护状态 + 详细设置按钮
@Composable
private fun SiteCard(site: SiteDto) {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = site.domain, fontWeight = FontWeight.Bold)
                Text(
                    text = if (site.isActive) "防护中" else "已停用",
                    color = if (site.isActive) SafetyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { }) { Text(text = "详细设置") }
        }
    }
}

// 添加域名弹窗
@Composable
private fun AddSiteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
