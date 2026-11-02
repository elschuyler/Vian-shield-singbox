package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.DiagnosticsScreen
import com.example.ui.DiagnosticViewModel
import com.example.ui.FirewallScreen
import com.example.ui.LogKeeperScreen
import com.example.ui.MainScreen
import com.example.ui.ProfilesScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SkyBluePrimaryContainer
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.SkyBlueTextTertiary

data class NavTabItem(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val diagnosticViewModel: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val currentScreen by diagnosticViewModel.currentScreen.collectAsState()

                val tabs = listOf(
                    NavTabItem(AppScreen.MAIN, "Main", Icons.Default.Security, "bottom_nav_main"),
                    NavTabItem(AppScreen.FIREWALL, "Firewall", Icons.Default.Tune, "bottom_nav_firewall"),
                    NavTabItem(AppScreen.VPN, "VPN", Icons.Default.Lock, "bottom_nav_vpn"),
                    NavTabItem(AppScreen.SETTINGS, "Settings", Icons.Default.Settings, "bottom_nav_settings")
                )

                val isPrimaryTab = currentScreen in listOf(
                    AppScreen.MAIN,
                    AppScreen.FIREWALL,
                    AppScreen.VPN,
                    AppScreen.PROFILES,
                    AppScreen.SETTINGS
                )

                if (isPrimaryTab) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar(
                                containerColor = SkyBlueSurface,
                                modifier = Modifier.testTag("persistent_bottom_navigation")
                            ) {
                                tabs.forEach { tab ->
                                    val isSelected = when (tab.screen) {
                                        AppScreen.VPN -> currentScreen == AppScreen.VPN || currentScreen == AppScreen.PROFILES
                                        else -> currentScreen == tab.screen
                                    }

                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { diagnosticViewModel.navigateTo(tab.screen) },
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = SkyBluePrimary,
                                            selectedTextColor = SkyBluePrimary,
                                            indicatorColor = SkyBluePrimaryContainer,
                                            unselectedIconColor = SkyBlueTextTertiary,
                                            unselectedTextColor = SkyBlueTextTertiary
                                        ),
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                AppScreen.MAIN -> {
                                    MainScreen(
                                        viewModel = diagnosticViewModel,
                                        onNavigate = { diagnosticViewModel.navigateTo(it) }
                                    )
                                }
                                AppScreen.FIREWALL -> {
                                    FirewallScreen(
                                        viewModel = diagnosticViewModel,
                                        onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) }
                                    )
                                }
                                AppScreen.VPN, AppScreen.PROFILES -> {
                                    ProfilesScreen(
                                        viewModel = diagnosticViewModel,
                                        onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) }
                                    )
                                }
                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        viewModel = diagnosticViewModel,
                                        onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) },
                                        onOpenLogKeeper = { diagnosticViewModel.navigateTo(AppScreen.LOG_KEEPER) },
                                        onOpenDiagnostics = { diagnosticViewModel.navigateTo(AppScreen.DIAGNOSTICS) }
                                    )
                                }
                                else -> {
                                    // Handled in sub-screens
                                }
                            }
                        }
                    }
                } else {
                    // Full-screen dedicated tools / sub-screens
                    when (currentScreen) {
                        AppScreen.LOG_KEEPER -> {
                            LogKeeperScreen(
                                viewModel = diagnosticViewModel,
                                onBack = { diagnosticViewModel.navigateTo(AppScreen.SETTINGS) }
                            )
                        }
                        AppScreen.DIAGNOSTICS -> {
                            DiagnosticsScreen(
                                viewModel = diagnosticViewModel,
                                onBack = { diagnosticViewModel.navigateTo(AppScreen.SETTINGS) },
                                onOpenLogKeeper = { diagnosticViewModel.navigateTo(AppScreen.LOG_KEEPER) }
                            )
                        }
                        else -> {
                            MainScreen(
                                viewModel = diagnosticViewModel,
                                onNavigate = { diagnosticViewModel.navigateTo(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
