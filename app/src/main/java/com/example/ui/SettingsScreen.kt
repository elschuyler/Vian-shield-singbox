package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SkyBlueBackground
import com.example.ui.theme.SkyBlueOnPrimaryContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SkyBluePrimaryContainer
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.SkyBlueSurfaceBorder
import com.example.ui.theme.SkyBlueSurfaceVariant
import com.example.ui.theme.SkyBlueTextPrimary
import com.example.ui.theme.SkyBlueTextSecondary
import com.example.ui.theme.SkyBlueTextTertiary
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusStarting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit = {},
    onOpenLogKeeper: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val isLoggingEnabled by viewModel.isLoggingEnabled.collectAsState()
    val pendingCrashCount by viewModel.pendingCrashCount.collectAsState()
    val diskSizeBytes = viewModel.getDiskLogSizeBytes()
    val context = LocalContext.current

    // Local General Preferences toggles
    var startOnBoot by remember { mutableStateOf(false) }
    var persistentNotification by remember { mutableStateOf(true) }
    var dnsLeakProtection by remember { mutableStateOf(true) }
    var killSwitchPreview by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ================================================================
            // 1. Log Keeper Navigation Row & Operational Logging
            // ================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLogKeeper)
                    .testTag("settings_open_log_keeper_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(SkyBluePrimaryContainer, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ListAlt,
                                    contentDescription = null,
                                    tint = SkyBlueOnPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Log Keeper",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SkyBlueTextPrimary
                                )
                                Text(
                                    text = "Permanent audit logs, search & downloads export",
                                    fontSize = 12.sp,
                                    color = SkyBlueTextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Log Keeper",
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Logging master toggle & disk status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Operational Logging Switch",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SkyBlueTextPrimary
                            )
                            Text(
                                text = if (isLoggingEnabled)
                                    "Active (${diskSizeBytes / 1024} KB of 3 MiB rolling disk buffer)"
                                else
                                    "Logging disabled (Zero I/O overhead)",
                                fontSize = 11.sp,
                                color = if (isLoggingEnabled) StatusConnected else SkyBlueTextTertiary
                            )
                        }
                        Switch(
                            checked = isLoggingEnabled,
                            onCheckedChange = { viewModel.setLoggingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SkyBluePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = SkyBlueSurfaceBorder
                            ),
                            modifier = Modifier.testTag("master_log_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenLogKeeper,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("View Full Log Viewer", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearLogs()
                                Toast.makeText(context, "Operational logs cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_logs_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Logs", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ================================================================
            // 2. Diagnostics & Troubleshooting Navigation Row
            // ================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDiagnostics)
                    .testTag("settings_open_diagnostics_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(SkyBluePrimaryContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = SkyBlueOnPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Diagnostics & Troubleshooting",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SkyBlueTextPrimary
                                )
                                if (pendingCrashCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = StatusError
                                    ) {
                                        Text(
                                            text = "$pendingCrashCount Crashes",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Native BoxService status, stack & crash inspector",
                                fontSize = 12.sp,
                                color = SkyBlueTextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Diagnostics",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ================================================================
            // 3. Backup & Restoration (Staged / Clean Placeholder)
            // ================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(SkyBlueSurfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = SkyBlueTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Backup & Restoration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SkyBlueTextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SkyBlueSurfaceVariant
                        ) {
                            Text(
                                text = "Staged (Phase 2)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = SkyBlueTextTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Export and restore WireGuard profiles, firewall rules, and application preferences.",
                        fontSize = 12.sp,
                        color = SkyBlueTextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Backup staged — encrypted export scheduled for Phase 2",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Backup", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Restore staged — encrypted import scheduled for Phase 2",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Backup", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ================================================================
            // 4. General Preferences
            // ================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "General Preferences",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SkyBlueTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PreferenceToggleRow(
                        title = "Start on Device Boot",
                        subtitle = "Automatically establish VPN when Android system finishes boot",
                        checked = startOnBoot,
                        onCheckedChange = { startOnBoot = it }
                    )

                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    PreferenceToggleRow(
                        title = "Persistent Notification",
                        subtitle = "Foreground service notification with fast disconnect action",
                        checked = persistentNotification,
                        onCheckedChange = { persistentNotification = it }
                    )

                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    PreferenceToggleRow(
                        title = "DNS Leak Protection",
                        subtitle = "Strict routing detour forcing all DNS requests through WireGuard tunnel",
                        checked = dnsLeakProtection,
                        onCheckedChange = { dnsLeakProtection = it }
                    )

                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    PreferenceToggleRow(
                        title = "Kill Switch (Block without VPN)",
                        subtitle = "Drop all packets when tunnel disconnects (System preview)",
                        checked = killSwitchPreview,
                        onCheckedChange = { killSwitchPreview = it }
                    )
                }
            }

            // ================================================================
            // 5. App Information
            // ================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Information",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SkyBlueTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingInfoRow("App Name", "Vianshield")
                    SettingInfoRow("Version", "1.0 (Build 2026.10)")
                    SettingInfoRow("sing-box Core Engine", "SagerNet/sing-box v1.14.4")
                    SettingInfoRow("Target Architecture", "Dual-ARM (armeabi-v7a + arm64-v8a)")
                    SettingInfoRow("TUN Interface", "vianshield-tun (gVisor Userspace Stack)")
                    SettingInfoRow("Privacy & Tracking", "Zero Telemetry | 100% On-Device")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SkyBlueTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SkyBlueTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SkyBluePrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SkyBlueSurfaceBorder
            )
        )
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = SkyBlueTextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SkyBlueTextPrimary
        )
    }
}
