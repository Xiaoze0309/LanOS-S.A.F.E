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
import com.lanos.safe.ui.screens.DomainManagerScreen
import com.lanos.safe.ui.screens.HomeScreen
import com.lanos.safe.ui.screens.LoginScreen
import com.lanos.safe.ui.screens.RulesScreen
import com.lanos.safe.ui.screens.SettingsScreen
import com.lanos.safe.ui.screens.TwoFactorScreen
import com.lanos.safe.ui.theme.LanosSafeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LanosSafeApp() }
    }
}

@Composable
private fun LanosSafeApp() {
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = listOf(Routes.Home, Routes.Rules, Routes.Settings)

    LanosSafeTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (route in tabs) {
                    FloatingNavBar(
                        currentRoute = route ?: Routes.Home,
                        onNavigate = { r ->
                            navController.navigate(r) {
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
        ) { pad ->
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier = Modifier.padding(pad)
            ) {
                composable(Routes.Home) {
                    HomeScreen(onConfigureDomain = { navController.navigate(Routes.Domain) })
                }
                composable(Routes.Rules) { RulesScreen() }
                composable(Routes.Settings) {
                    SettingsScreen(
                        darkTheme = darkTheme,
                        onOpenLogin = { navController.navigate(Routes.Login) },
                        onOpenTwoFactor = { navController.navigate(Routes.TwoFactor) },
                        onToggleTheme = { darkTheme = !darkTheme }
                    )
                }
                composable(Routes.Domain) {
                    DomainManagerScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Login) {
                    LoginScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.TwoFactor) {
                    TwoFactorScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
