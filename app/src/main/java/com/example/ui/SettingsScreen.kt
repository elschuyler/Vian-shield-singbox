package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import com.example.ui.theme.StatusStarting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
    onOpenLogKeeper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val isLoggingEnabled by viewModel.isLoggingEnabled.collectAsState()
    val diskSizeBytes = viewModel.getDiskLogSizeBytes()
    val context = LocalContext.current

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
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SkyBlueTextPrimary
                        )
                    }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Operational Logging Master Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Operational Logging",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SkyBlueTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isLoggingEnabled) "Logging active (Rolling 3 MiB on-disk buffer)" else "Logging disabled (Zero I/O overhead)",
                                fontSize = 12.sp,
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

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingInfoRow("Max Persistent Storage", "3 MiB Total (2x 1.5 MiB Rolling Files)")
                    SettingInfoRow("In-Memory Event Buffer", "500 events (FIFO)")
                    SettingInfoRow("Current Disk Usage", "${diskSizeBytes / 1024} KB")

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenLogKeeper,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_open_log_keeper_button")
                        ) {
                            Icon(imageVector = Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Log Keeper", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.clearLogs()
                                Toast.makeText(context, "Operational logs cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_logs_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Logs", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. Tunnel & Routing Policies
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Networking & Tunnel Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SkyBlueTextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingInfoRow("Userspace Stack", "sing-box v1.11.0 (gVisor)")
                    SettingInfoRow("Mobile MTU Standard", "1280 (RFC 8200 IPv6 Minimum Standard)")
                    SettingInfoRow("DNS Detour Strategy", "prefer_ipv4 with detour: wg-out")
                    SettingInfoRow("Tunnel Interface", "vianshield-tun (strict_route = true)")
                    SettingInfoRow("Loop Prevention", "VpnService.protect(fd) on socket creation")
                }
            }

            // 3. Application Identity & Architecture
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About Vianshield",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SkyBlueTextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingInfoRow("Version", "1.0 (Release Build)")
                    SettingInfoRow("Target Architecture", "Dual-ARM (armeabi-v7a + arm64-v8a)")
                    SettingInfoRow("Core Networking Engine", "SagerNet/sing-box v1.11.0 Go Core")
                    SettingInfoRow("Privacy & Analytics", "Zero Analytics | No Tracking | Local Only")
                }
            }
        }
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
