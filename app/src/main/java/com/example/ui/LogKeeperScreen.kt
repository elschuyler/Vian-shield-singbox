package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.logging.LogCategory
import com.example.logging.LogEvent
import com.example.logging.LogSeverity
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusStarting
import com.example.ui.theme.StatusStopped

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogKeeperScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val logEvents by viewModel.filteredLogEvents.collectAsState()
    val rawEvents by viewModel.rawLogEvents.collectAsState()
    val isLoggingEnabled by viewModel.isLoggingEnabled.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTimeFilter by viewModel.selectedTimeFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSeverity by viewModel.selectedSeverity.collectAsState()
    val sortNewestFirst by viewModel.sortNewestFirst.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Log Keeper",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("log_keeper_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main"
                        )
                    }
                },
                actions = {
                    // Master Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (isLoggingEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoggingEnabled) StatusConnected else StatusStopped
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isLoggingEnabled,
                            onCheckedChange = { viewModel.setLoggingEnabled(it) },
                            modifier = Modifier
                                .testTag("log_keeper_master_switch")
                                .size(width = 46.dp, height = 28.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = StatusConnected
                            )
                        )
                    }

                    // Copy / Export
                    IconButton(
                        onClick = {
                            val exported = viewModel.exportLogs()
                            clipboardManager.setText(AnnotatedString(exported))
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("export_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.btn_export_logs),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Clear
                    IconButton(
                        onClick = {
                            viewModel.clearLogs()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.btn_clear_logs),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("log_search_field"),
                placeholder = { Text("Search logs (component, message, category)...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Filter Row 1: Time Window Filters (6h, 12h, 24h, All) & Sort Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedTimeFilter == filter,
                            onClick = { viewModel.setTimeFilter(filter) },
                            label = { Text(filter.label, fontSize = 11.sp) },
                            modifier = Modifier.testTag("time_filter_${filter.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                // Sort Order Toggle Button
                OutlinedButton(
                    onClick = { viewModel.toggleSortOrder() },
                    modifier = Modifier.testTag("sort_order_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort Order",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (sortNewestFirst) "Newest" else "Oldest",
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Horizontal Filter Row 2: Severity Chips (All, Info, Warn, Error)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedSeverity == null,
                    onClick = { viewModel.setSeverityFilter(null) },
                    label = { Text("All Severities", fontSize = 11.sp) }
                )
                LogSeverity.entries.forEach { sev ->
                    FilterChip(
                        selected = selectedSeverity == sev,
                        onClick = { viewModel.setSeverityFilter(if (selectedSeverity == sev) null else sev) },
                        label = { Text(sev.name, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Horizontal Filter Row 3: Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("All Categories", fontSize = 11.sp) }
                )
                LogCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.setCategoryFilter(if (selectedCategory == cat) null else cat) },
                        label = { Text(cat.name, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bounded Retention & Truthful Observability Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Showing ${logEvents.size} of ${rawEvents.size} entries (Buffer cap: 500 FIFO). Redacts secrets. Unobserved in Phase 1: per-app decisions & DNS ad-blocking.",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log List
            if (logEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoggingEnabled) {
                            "No events match current filters."
                        } else {
                            "Diagnostic logging is paused via master switch."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(logEvents, key = { it.id }) { event ->
                        LogKeeperItemCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogKeeperItemCard(event: LogEvent) {
    val severityColor = when (event.severity) {
        LogSeverity.INFO -> MaterialTheme.colorScheme.primary
        LogSeverity.WARN -> StatusStarting
        LogSeverity.ERROR -> StatusError
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Severity Pill, Category Pill, Component, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Severity
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = severityColor.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = event.severity.name,
                            color = severityColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Category
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = event.category.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Component
                    Text(
                        text = event.component,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Timestamp
                Text(
                    text = event.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }

            // Message
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Optional Details
            if (!event.details.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = event.details,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
