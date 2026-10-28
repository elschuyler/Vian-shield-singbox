package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.AppScreen
import com.example.ui.DiagnosticsScreen
import com.example.ui.DiagnosticViewModel
import com.example.ui.LogKeeperScreen
import com.example.ui.MainScreen
import com.example.ui.ProfilesScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val diagnosticViewModel: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val currentScreen by diagnosticViewModel.currentScreen.collectAsState()

                when (currentScreen) {
                    AppScreen.MAIN -> {
                        MainScreen(
                            viewModel = diagnosticViewModel,
                            onNavigate = { diagnosticViewModel.navigateTo(it) }
                        )
                    }
                    AppScreen.PROFILES -> {
                        ProfilesScreen(
                            viewModel = diagnosticViewModel,
                            onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) }
                        )
                    }
                    AppScreen.DIAGNOSTICS -> {
                        DiagnosticsScreen(
                            viewModel = diagnosticViewModel,
                            onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) },
                            onOpenLogKeeper = { diagnosticViewModel.navigateTo(AppScreen.LOG_KEEPER) }
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            viewModel = diagnosticViewModel,
                            onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) },
                            onOpenLogKeeper = { diagnosticViewModel.navigateTo(AppScreen.LOG_KEEPER) }
                        )
                    }
                    AppScreen.LOG_KEEPER -> {
                        LogKeeperScreen(
                            viewModel = diagnosticViewModel,
                            onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) }
                        )
                    }
                }
            }
        }
    }
}
