package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import com.example.config.WireGuardConfigParser
import com.example.logging.LogSeverity
import com.example.logging.OperationalLogCatcher
import com.example.vpn.VianshieldVpnService
import com.example.vpn.VpnLifecycleController
import com.example.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {

    private val lifecycleController = VpnLifecycleController(application)

    val vpnState: StateFlow<VpnState> = VianshieldVpnService.vpnState
    val logEvents = OperationalLogCatcher.events
    val isLoggingEnabled = OperationalLogCatcher.isLoggingEnabled

    private val _selectedSeverity = MutableStateFlow<LogSeverity?>(null)
    val selectedSeverity: StateFlow<LogSeverity?> = _selectedSeverity.asStateFlow()

    private val _currentConfig = MutableStateFlow(WireGuardConfigParser.createDefaultTestConfig())
    val currentConfig: StateFlow<String> = _currentConfig.asStateFlow()

    init {
        OperationalLogCatcher.initialize(application)
    }

    fun setSeverityFilter(severity: LogSeverity?) {
        _selectedSeverity.value = severity
    }

    fun updateConfig(newConfig: String) {
        _currentConfig.value = newConfig
    }

    fun checkPermissionIntent(): Intent? {
        return lifecycleController.checkVpnPermission()
    }

    fun toggleVpn(permissionLauncher: ActivityResultLauncher<Intent>? = null) {
        when (vpnState.value) {
            is VpnState.Connected, is VpnState.Starting -> {
                lifecycleController.requestStopVpn()
            }
            is VpnState.Stopped, is VpnState.Error -> {
                lifecycleController.requestStartVpn(_currentConfig.value, permissionLauncher)
            }
            is VpnState.Stopping -> {
                // Ignore while stopping to prevent conflicting lifecycle states
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        OperationalLogCatcher.setLoggingEnabled(enabled)
    }

    fun clearLogs() {
        OperationalLogCatcher.clear()
    }

    fun exportLogs(): String {
        return OperationalLogCatcher.exportFormattedLogs()
    }
}
