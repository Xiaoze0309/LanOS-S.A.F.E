package com.lanos.safe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lanos.safe.ui.components.SafeBottomNav
import com.lanos.safe.ui.screens.HomeScreen
import com.lanos.safe.ui.screens.SettingsScreen
import com.lanos.safe.ui.theme.LanosSafeTheme

// 应用唯一入口：只负责装配主题与根组件
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启边到边显示
        enableEdgeToEdge()
        setContent {
            LanosSafeTheme {
                LanosSafeApp()
            }
        }
    }
}

// 根组件：维护路由状态并按路由切换页面
@Composable
private fun LanosSafeApp() {
    // 当前路由状态：home / settings
    var currentRoute by remember { mutableStateOf("home") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            SafeBottomNav(
                currentRoute = currentRoute,
                onNavigate = { route -> currentRoute = route }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            modifier = Modifier.padding(innerPadding),
            label = "routeContent"
        ) { route ->
            when (route) {
                // 主页
                "home" -> HomeScreen(
                    onStartVpnClick = {
                        // 预留：此处只做 UI 层 Lambda 传递，暂不写具体逻辑
                    }
                )
                // 设置页
                else -> SettingsScreen()
            }
        }
    }
}
