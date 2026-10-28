package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward compatibility wrapper for DiagnosticScreen.
 * Delegates to the new minimalist MainScreen dashboard.
 */
@Composable
fun DiagnosticScreen(
    viewModel: DiagnosticViewModel,
    onOpenLogKeeper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MainScreen(
        viewModel = viewModel,
        onNavigate = { screen ->
            if (screen == AppScreen.LOG_KEEPER) {
                onOpenLogKeeper()
            } else {
                viewModel.navigateTo(screen)
            }
        },
        modifier = modifier
    )
}
