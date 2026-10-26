package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.WireGuardConfigParser
import com.example.logging.LogCategory
import com.example.logging.LogEvent
import com.example.logging.LogSeverity
import com.example.logging.OperationalLogCatcher
import com.example.vpn.VianshieldVpnService
import com.example.vpn.VpnLifecycleController
import com.example.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AppScreen {
    MAIN,
    LOG_KEEPER
}

enum class TimeFilter(val label: String, val durationMs: Long?) {
    ALL("All", null),
    H6("6h", 6 * 3600 * 1000L),
    H12("12h", 12 * 3600 * 1000L),
    H24("24h", 24 * 3600 * 1000L)
}

class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {

    private val lifecycleController = VpnLifecycleController(application)

    val vpnState: StateFlow<VpnState> = VianshieldVpnService.vpnState
    val rawLogEvents = OperationalLogCatcher.events
    val isLoggingEnabled = OperationalLogCatcher.isLoggingEnabled

    private val _currentScreen = MutableStateFlow(AppScreen.MAIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTimeFilter = MutableStateFlow(TimeFilter.ALL)
    val selectedTimeFilter: StateFlow<TimeFilter> = _selectedTimeFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<LogCategory?>(null)
    val selectedCategory: StateFlow<LogCategory?> = _selectedCategory.asStateFlow()

    private val _selectedSeverity = MutableStateFlow<LogSeverity?>(null)
    val selectedSeverity: StateFlow<LogSeverity?> = _selectedSeverity.asStateFlow()

    private val _sortNewestFirst = MutableStateFlow(true)
    val sortNewestFirst: StateFlow<Boolean> = _sortNewestFirst.asStateFlow()

    private val _currentConfig = MutableStateFlow(WireGuardConfigParser.createDefaultTestConfig())
    val currentConfig: StateFlow<String> = _currentConfig.asStateFlow()

    // Bounded and filtered events stream for Log Keeper UI
    val filteredLogEvents: StateFlow<List<LogEvent>> = combine(
        combine(rawLogEvents, _searchQuery, _selectedTimeFilter) { events, query, timeFilter ->
            Triple(events, query, timeFilter)
        },
        combine(_selectedCategory, _selectedSeverity, _sortNewestFirst) { category, severity, newestFirst ->
            Triple(category, severity, newestFirst)
        }
    ) { (events, query, timeFilter), (category, severity, newestFirst) ->
        val now = System.currentTimeMillis()
        val minTimestamp = timeFilter.durationMs?.let { now - it } ?: 0L

        val filtered = events.filter { event ->
            // 1. Time filter
            val matchesTime = event.timestamp >= minTimestamp

            // 2. Category filter
            val matchesCategory = category == null || event.category == category

            // 3. Severity filter
            val matchesSeverity = severity == null || event.severity == severity

            // 4. Search query filter
            val matchesQuery = query.isBlank() ||
                    event.message.contains(query, ignoreCase = true) ||
                    event.component.contains(query, ignoreCase = true) ||
                    (event.details?.contains(query, ignoreCase = true) == true) ||
                    event.category.name.contains(query, ignoreCase = true)

            matchesTime && matchesCategory && matchesSeverity && matchesQuery
        }

        if (newestFirst) filtered.reversed() else filtered
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        OperationalLogCatcher.initialize(application)
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTimeFilter(filter: TimeFilter) {
        _selectedTimeFilter.value = filter
    }

    fun setCategoryFilter(category: LogCategory?) {
        _selectedCategory.value = category
    }

    fun setSeverityFilter(severity: LogSeverity?) {
        _selectedSeverity.value = severity
    }

    fun toggleSortOrder() {
        _sortNewestFirst.value = !_sortNewestFirst.value
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
