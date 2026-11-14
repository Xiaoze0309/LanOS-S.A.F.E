package com.lanos.safe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lanos.safe.ui.components.FloatingNavBar
import com.lanos.safe.ui.screens.ExtensionsScreen
import com.lanos.safe.ui.screens.HomeScreen
import com.lanos.safe.ui.screens.LoginScreen
import com.lanos.safe.ui.screens.RulesScreen
import com.lanos.safe.ui.screens.SettingsScreen
import com.lanos.safe.ui.screens.TerminalScreen
import com.lanos.safe.ui.screens.TwoFactorScreen
import com.lanos.safe.ui.theme.LanosSafeTheme

// 路由常量：集中管理全部目的地
object Routes {
    const val Home = "home"
    const val Rules = "rules"
    const val Terminal = "terminal"
    const val Settings = "settings"
    const val Login = "login"
    const val TwoFactor = "twofactor"
    const val Extensions = "extensions"
}

// 应用唯一入口
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启边到边显示
        enableEdgeToEdge()
        setContent {
            LanosSafeApp()
        }
    }
}

// 根组件：装配主题 + 导航宿主 + 底部导航
@Composable
private fun LanosSafeApp() {
    // 深浅色主题开关状态（设置页可切换）
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    // 导航控制器与当前路由
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // 底部导航只在四个主 Tab 显示
    val showBottomBar = currentRoute in listOf(
        Routes.Home, Routes.Rules, Routes.Terminal, Routes.Settings
    )

    LanosSafeTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    FloatingNavBar(
                        currentRoute = currentRoute ?: Routes.Home,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.Home) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(Routes.Settings) },
                        onStartVpnClick = { }
                    )
                }
                composable(Routes.Rules) { RulesScreen() }
                composable(Routes.Terminal) { TerminalScreen() }
                composable(Routes.Settings) {
                    SettingsScreen(
                        darkTheme = darkTheme,
                        onOpenLogin = { navController.navigate(Routes.Login) },
                        onOpenTwoFactor = { navController.navigate(Routes.TwoFactor) },
                        onOpenExtensions = { navController.navigate(Routes.Extensions) },
                        onToggleTheme = { darkTheme = !darkTheme }
                    )
                }
                composable(Routes.Login) {
                    LoginScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.TwoFactor) {
                    TwoFactorScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Extensions) {
                    ExtensionsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
