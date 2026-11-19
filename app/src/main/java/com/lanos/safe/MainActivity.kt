package com.lanos.safe

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lanos.safe.data.LanosApi
import com.lanos.safe.ui.components.FloatingNavBar
import com.lanos.safe.ui.design.DesignMode
import com.lanos.safe.ui.design.Lanos
import com.lanos.safe.ui.design.LanosTheme
import com.lanos.safe.ui.screens.AuthScreen
import com.lanos.safe.ui.screens.DomainScreen
import com.lanos.safe.ui.screens.HomeScreen
import com.lanos.safe.ui.screens.SettingsScreen
import com.lanos.safe.ui.screens.WafScreen

private const val prefsName = "lanos_prefs"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        LanosApi.baseUrl = prefs.getString("base_url", "http://127.0.0.1:8000") ?: "http://127.0.0.1:8000"
        LanosApi.accessToken = prefs.getString("token", null)
        LanosApi.refreshToken = prefs.getString("refresh", null)

        setContent {
            var token by remember { mutableStateOf(LanosApi.accessToken) }
            var mode by remember {
                mutableStateOf(
                    runCatching { DesignMode.valueOf(prefs.getString("mode", "Miuix")!!) }
                        .getOrDefault(DesignMode.Miuix)
                )
            }
            // isSystemInDarkTheme() 是 @Composable，必须在 remember 之外先取值
            val systemDark = isSystemInDarkTheme()
            var dark by remember {
                mutableStateOf(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else systemDark)
            }
            var baseUrl by remember { mutableStateOf(LanosApi.baseUrl) }

            LanosTheme(mode = mode, darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = Lanos.palette.bg) {
                    if (token == null) {
                        AuthScreen(
                            onAuthenticated = { newToken ->
                                LanosApi.accessToken = newToken
                                prefs.edit()
                                    .putString("token", newToken)
                                    .putString("refresh", LanosApi.refreshToken)
                                    .apply()
                                token = newToken
                            }
                        )
                    } else {
                        AppShell(
                            token = token!!,
                            mode = mode,
                            dark = dark,
                            baseUrl = baseUrl,
                            onModeChange = {
                                mode = it
                                prefs.edit().putString("mode", it.name).apply()
                            },
                            onDarkChange = {
                                dark = it
                                prefs.edit().putBoolean("dark", it).apply()
                            },
                            onBaseUrlChange = {
                                baseUrl = it
                                LanosApi.baseUrl = it
                                prefs.edit().putString("base_url", it).apply()
                            },
                            onLoggedOut = {
                                prefs.edit().remove("token").remove("refresh").apply()
                                token = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    token: String,
    mode: DesignMode,
    dark: Boolean,
    baseUrl: String,
    onModeChange: (DesignMode) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onLoggedOut: () -> Unit
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.Home
    val tabs = listOf(Routes.Home, Routes.Domains, Routes.Waf, Routes.Settings)

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 24 }
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = {
                fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 24 }
            }
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    onOpenDomains = { navController.switchTab(Routes.Domains) },
                    onOpenWaf = { navController.switchTab(Routes.Waf) }
                )
            }
            composable(Routes.Domains) {
                DomainScreen(onBack = { navController.switchTab(Routes.Home) })
            }
            composable(Routes.Waf) { WafScreen() }
            composable(Routes.Settings) {
                SettingsScreen(
                    mode = mode,
                    dark = dark,
                    baseUrl = baseUrl,
                    onModeChange = onModeChange,
                    onDarkChange = onDarkChange,
                    onBaseUrlChange = onBaseUrlChange,
                    onLoggedOut = onLoggedOut
                )
            }
        }

        // 底部导航：只在主 Tab 显示，悬浮于内容之上
        if (route in tabs) {
            FloatingNavBar(
                currentRoute = route,
                onNavigate = { navController.switchTab(it) },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
    }
}

private fun androidx.navigation.NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
