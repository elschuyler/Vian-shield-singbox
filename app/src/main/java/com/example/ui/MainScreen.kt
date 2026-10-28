package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.SingBoxAdapter
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
import com.example.ui.theme.StatusStopped
import com.example.vpn.VpnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DiagnosticViewModel,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val vpnState by viewModel.vpnState.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpn()
        } else {
            Toast.makeText(context, "VPN permission is required to create network tunnel", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Vianshield",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = SkyBlueTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SkyBluePrimaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "sing-box v1.11.0",
                                color = SkyBlueOnPrimaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onNavigate(AppScreen.SETTINGS) },
                        modifier = Modifier.testTag("topbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = SkyBlueTextSecondary
                        )
                    }
                    IconButton(
                        onClick = { onNavigate(AppScreen.LOG_KEEPER) },
                        modifier = Modifier.testTag("topbar_log_keeper_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ListAlt,
                            contentDescription = "Log Keeper",
                            tint = SkyBluePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SkyBlueSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Connection Status Card
            ConnectionStatusCard(vpnState = vpnState)

            // 2. Primary Connect/Disconnect Action
            PrimaryActionButton(
                vpnState = vpnState,
                onToggle = {
                    val intent = viewModel.checkPermissionIntent()
                    if (intent != null) {
                        vpnPermissionLauncher.launch(intent)
                    } else {
                        viewModel.toggleVpn()
                    }
                }
            )

            // 3. Error Banner (if in error state)
            AnimatedVisibility(visible = vpnState is VpnState.Error) {
                if (vpnState is VpnState.Error) {
                    val errorState = vpnState as VpnState.Error
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VPN Startup Failed",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B),
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorState.message,
                                fontSize = 12.sp,
                                color = Color(0xFF7F1D1D)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onNavigate(AppScreen.DIAGNOSTICS) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("View Diagnostics", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 4. Compact Selected Profile Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(AppScreen.PROFILES) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active WireGuard Profile",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = SkyBlueTextPrimary
                            )
                        }
                        Text(
                            text = "Manage >",
                            color = SkyBluePrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (activeProfile != null) {
                        val profile = activeProfile!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Profile Name", fontSize = 11.sp, color = SkyBlueTextTertiary)
                                Text(profile.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Endpoint", fontSize = 11.sp, color = SkyBlueTextTertiary)
                                Text("${profile.endpointHost}:${profile.endpointPort}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Tunnel Address", fontSize = 11.sp, color = SkyBlueTextTertiary)
                                Text(profile.address, fontSize = 12.sp, color = SkyBlueTextSecondary, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("MTU", fontSize = 11.sp, color = SkyBlueTextTertiary)
                                Text("${profile.mtu} (Mobile)", fontSize = 12.sp, color = SkyBlueTextSecondary)
                            }
                        }
                    } else {
                        Text(
                            text = "No profile loaded. Tap to import or select a WireGuard profile.",
                            fontSize = 12.sp,
                            color = SkyBlueTextSecondary
                        )
                    }
                }
            }

            // 5. Dedicated Pages Navigation Section
            Text(
                text = "Dedicated Modules",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SkyBlueTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardNavTile(
                    title = "Profiles",
                    subtitle = "Import & Config",
                    icon = Icons.Default.Tune,
                    testTag = "nav_tile_profiles",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.PROFILES) }
                )
                DashboardNavTile(
                    title = "Log Keeper",
                    subtitle = "Audit Trail",
                    icon = Icons.Default.ListAlt,
                    testTag = "open_log_keeper_button",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.LOG_KEEPER) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardNavTile(
                    title = "Diagnostics",
                    subtitle = "Engine & Stack",
                    icon = Icons.Default.BugReport,
                    testTag = "nav_tile_diagnostics",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.DIAGNOSTICS) }
                )
                DashboardNavTile(
                    title = "Settings",
                    subtitle = "Tunnel & Logs",
                    icon = Icons.Default.Settings,
                    testTag = "nav_tile_settings",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.SETTINGS) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectionStatusCard(vpnState: VpnState) {
    val (statusColor, statusText, statusIcon) = when (vpnState) {
        is VpnState.Connected -> Triple(StatusConnected, "Connected", Icons.Default.CheckCircle)
        is VpnState.Starting -> Triple(StatusStarting, "Connecting...", Icons.Default.HourglassEmpty)
        is VpnState.Stopping -> Triple(StatusStarting, "Disconnecting...", Icons.Default.HourglassEmpty)
        is VpnState.Error -> Triple(StatusError, "Error", Icons.Default.Error)
        is VpnState.Stopped -> Triple(StatusStopped, "Disconnected", Icons.Default.Stop)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(2.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SkyBlueTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitle = when (vpnState) {
                is VpnState.Connected -> "Traffic routed securely via WireGuard userspace tunnel"
                is VpnState.Starting -> "Establishing TUN interface and initializing native BoxService"
                is VpnState.Stopping -> "Shutting down native BoxService and closing descriptors"
                is VpnState.Error -> vpnState.message
                is VpnState.Stopped -> "Ready to establish WireGuard tunnel"
            }

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SkyBlueTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Compact native status indicator pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SkyBlueSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (SingBoxAdapter.isNativeAvailable()) StatusConnected else StatusStarting)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (SingBoxAdapter.isNativeAvailable()) "libbox Native Core Present" else "Native Core Bridge Pending",
                        fontSize = 11.sp,
                        color = SkyBlueTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    vpnState: VpnState,
    onToggle: () -> Unit
) {
    val isConnected = vpnState is VpnState.Connected
    val isTransitioning = vpnState is VpnState.Starting || vpnState is VpnState.Stopping

    Button(
        onClick = onToggle,
        enabled = !isTransitioning,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isConnected) StatusError else SkyBluePrimary,
            contentColor = Color.White,
            disabledContainerColor = SkyBlueSurfaceBorder,
            disabledContentColor = SkyBlueTextTertiary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("vpn_toggle_button")
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = when {
                vpnState is VpnState.Starting -> "Connecting..."
                vpnState is VpnState.Stopping -> "Disconnecting..."
                isConnected -> "Disconnect VPN"
                else -> "Connect VPN"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DashboardNavTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SkyBluePrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SkyBlueOnPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = SkyBlueTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SkyBlueTextSecondary
            )
        }
    }
}
