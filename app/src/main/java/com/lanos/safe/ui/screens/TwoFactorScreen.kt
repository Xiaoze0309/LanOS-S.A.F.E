package com.lanos.safe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lanos.safe.ui.components.QrCanvas
import com.lanos.safe.ui.components.TotpKeyCard

// 2FA 验证页（纯密钥版）：无第三方库，二维码为手绘示意
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 6 位验证码输入
    var code by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "2FA 验证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 提示文字
            Text(
                text = "请打开 Microsoft Authenticator 或 Google Authenticator，选择手动输入密钥",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 模拟二维码
            QrCanvas(modifier = Modifier.align(Alignment.CenterHorizontally))
            // 密钥卡片 + 复制按钮
            TotpKeyCard()
            // 6 位验证码输入
            OutlinedTextField(
                value = code,
                onValueChange = { input ->
                    // 仅保留数字并限制 6 位
                    code = input.filter { it.isDigit() }.take(6)
                },
                label = { Text(text = "6 位验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
                modifier = Modifier.fillMaxWidth()
            )
            // 确认按钮：模拟验证成功并返回
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "确认")
            }
        }
    }
}
