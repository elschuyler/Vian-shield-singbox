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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val speedPreset by viewModel.speedPreset.collectAsState()
    val dnsProvider by viewModel.dnsProvider.collectAsState()
    val isAdFilterEnabled by viewModel.isAdFilterEnabled.collectAsState()
    val adFilterAllowList by viewModel.adFilterAllowList.collectAsState()

    val context = LocalContext.current
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showAllowListDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpn()
        } else {
            Toast.makeText(context, "VPN permission is required to create network tunnel", Toast.LENGTH_LONG).show()
        }
    }

    // Speed Preset Selection Dialog
    if (showSpeedDialog) {
        val speedOptions = listOf(
            "Unlimited (Full Speed)" to "Default line speed, no rate limiting",
            "Gaming / Low Latency" to "Optimized packet buffer, prioritize latency",
            "Battery Saver (5 Mbps)" to "Capped throughput for cellular power saving",
            "Extreme Data Saver (1 Mbps)" to "Strict bandwidth limit for metered roaming"
        )
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text("Speed-Throttling Preset", fontWeight = FontWeight.Bold, color = SkyBlueTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Native rate limiting is staged. Engine throttling enforcement is scheduled for Phase 2.",
                        fontSize = 11.sp,
                        color = SkyBlueTextTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    speedOptions.forEach { (presetName, presetDesc) ->
                        val isSelected = speedPreset == presetName
                        Surface(
                            onClick = {
                                viewModel.setSpeedPreset(presetName)
                                showSpeedDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) SkyBluePrimaryContainer else SkyBlueSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) SkyBluePrimary else SkyBlueSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setSpeedPreset(presetName)
                                        showSpeedDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = SkyBluePrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = presetName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = SkyBlueTextPrimary
                                    )
                                    Text(
                                        text = presetDesc,
                                        fontSize = 11.sp,
                                        color = SkyBlueTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = SkyBluePrimary)
                }
            },
            containerColor = SkyBlueSurface
        )
    }

    // DNS Selection Dialog
    if (showDnsDialog) {
        val dnsOptions = listOf(
            "Profile Default (from .conf)" to "Use upstream DNS server defined in active WireGuard profile",
            "Cloudflare DNS (1.1.1.1)" to "Fast, privacy-first public DNS resolver",
            "Quad9 Security (9.9.9.9)" to "Blocks malicious domains, botnets, and malware",
            "Google Public DNS (8.8.8.8)" to "High reliability global Anycast resolvers",
            "AdGuard DNS (94.140.14.14)" to "Default ad and tracker blocking at DNS layer"
        )
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = {
                Text("Select DNS Provider", fontWeight = FontWeight.Bold, color = SkyBlueTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Custom DNS overrides upstream server. Tunnel must restart to switch DNS detour.",
                        fontSize = 11.sp,
                        color = SkyBlueTextTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    dnsOptions.forEach { (dnsName, dnsDesc) ->
                        val isSelected = dnsProvider == dnsName
                        Surface(
                            onClick = {
                                viewModel.setDnsProvider(dnsName)
                                showDnsDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) SkyBluePrimaryContainer else SkyBlueSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) SkyBluePrimary else SkyBlueSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setDnsProvider(dnsName)
                                        showDnsDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = SkyBluePrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = dnsName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = SkyBlueTextPrimary
                                    )
                                    Text(
                                        text = dnsDesc,
                                        fontSize = 11.sp,
                                        color = SkyBlueTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Close", color = SkyBluePrimary)
                }
            },
            containerColor = SkyBlueSurface
        )
    }

    // Ad Filter Allow-List Dialog
    if (showAllowListDialog) {
        var newDomainInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAllowListDialog = false },
            title = {
                Text("Ad-Filter Allow-List", fontWeight = FontWeight.Bold, color = SkyBlueTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Bypass sinkhole blocking for specific domains.",
                        fontSize = 12.sp,
                        color = SkyBlueTextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDomainInput,
                            onValueChange = { newDomainInput = it },
                            placeholder = { Text("example.com", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SkyBlueSurface,
                                unfocusedContainerColor = SkyBlueSurface
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newDomainInput.isNotBlank()) {
                                    viewModel.addAllowListDomain(newDomainInput)
                                    newDomainInput = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                        ) {
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (adFilterAllowList.isEmpty()) {
                        Text(
                            text = "No domains in allow-list",
                            fontSize = 12.sp,
                            color = SkyBlueTextTertiary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            adFilterAllowList.forEach { domain ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SkyBlueSurfaceVariant, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = domain,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = SkyBlueTextPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = StatusError,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.removeAllowListDomain(domain) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllowListDialog = false }) {
                    Text("Done", color = SkyBluePrimary)
                }
            },
            containerColor = SkyBlueSurface
        )
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================================================================
            // ORDER MANDATED BY SPECIFICATION:
            // 1. Master protection switch
            // 2. Speed-throttling preset
            // 3. DNS changer
            // 4. Ad filter
            // 5. Ad-filter allow-list
            // 6. Actual protection status and relevant errors
            // ================================================================

            // 1. Master Protection Switch
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

            // 2. Speed-Throttling Preset
            DashboardControlRow(
                icon = Icons.Default.Speed,
                title = "Speed-Throttling Preset",
                value = speedPreset,
                statusTag = "Limiter Inactive",
                onClick = { showSpeedDialog = true },
                testTag = "main_speed_preset_row"
            )

            // 3. DNS Changer
            DashboardControlRow(
                icon = Icons.Default.Dns,
                title = "DNS Changer",
                value = dnsProvider,
                statusTag = "Active",
                onClick = { showDnsDialog = true },
                testTag = "main_dns_changer_row"
            )

            // 4. Ad Filter
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isAdFilterEnabled) SkyBluePrimaryContainer else SkyBlueSurfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isAdFilterEnabled) SkyBluePrimary else SkyBlueTextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Ad & Tracker Filter",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = SkyBlueTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SkyBlueSurfaceVariant
                                ) {
                                    Text(
                                        text = "Rules Staged",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SkyBlueTextTertiary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isAdFilterEnabled) "Local sinkhole enabled (Phase 2)" else "Drop ad & telemetry domains",
                                fontSize = 11.sp,
                                color = if (isAdFilterEnabled) StatusConnected else SkyBlueTextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isAdFilterEnabled,
                        onCheckedChange = { viewModel.setAdFilterEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SkyBluePrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SkyBlueSurfaceBorder
                        ),
                        modifier = Modifier.testTag("main_ad_filter_switch")
                    )
                }
            }

            // 5. Ad-Filter Allow-List
            DashboardControlRow(
                icon = Icons.Default.Security,
                title = "Ad-Filter Allow-List",
                value = "${adFilterAllowList.size} domains whitelisted",
                statusTag = "Manage",
                onClick = { showAllowListDialog = true },
                testTag = "main_ad_allowlist_row"
            )

            // 6. Actual Protection Status and Relevant Errors
            ConnectionStatusCard(
                vpnState = vpnState,
                activeProfile = activeProfile
            )

            // Error Banner (shown if VPN is in Error state)
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DashboardControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    statusTag: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
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

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SkyBlueTextPrimary
                    )
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        color = SkyBlueTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SkyBlueSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = statusTag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = SkyBlueTextTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = SkyBlueTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
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
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isConnected) StatusError else SkyBluePrimary,
            contentColor = Color.White,
            disabledContainerColor = SkyBlueSurfaceBorder,
            disabledContentColor = SkyBlueTextTertiary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("vpn_toggle_button")
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = when {
                vpnState is VpnState.Starting -> "Connecting..."
                vpnState is VpnState.Stopping -> "Disconnecting..."
                isConnected -> "Stop Protection"
                else -> "Start Protection"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    vpnState: VpnState,
    activeProfile: com.example.config.WireGuardProfile?
) {
    val (statusColor, statusText, statusIcon) = when (vpnState) {
        is VpnState.Connected -> Triple(StatusConnected, "Protection Active", Icons.Default.CheckCircle)
        is VpnState.Starting -> Triple(StatusStarting, "Establishing Tunnel...", Icons.Default.HourglassEmpty)
        is VpnState.Stopping -> Triple(StatusStarting, "Stopping Tunnel...", Icons.Default.HourglassEmpty)
        is VpnState.Error -> Triple(StatusError, "Protection Error", Icons.Default.Error)
        is VpnState.Stopped -> Triple(StatusStopped, "Protection Inactive", Icons.Default.Stop)
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
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(2.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = SkyBlueTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitle = when (vpnState) {
                is VpnState.Connected -> "WireGuard tunnel routing active on vianshield-tun"
                is VpnState.Starting -> "Creating TUN interface and initializing native BoxService"
                is VpnState.Stopping -> "Closing TUN descriptors and stopping BoxService"
                is VpnState.Error -> vpnState.message
                is VpnState.Stopped -> "Ready to protect network connections"
            }

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SkyBlueTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (activeProfile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SkyBlueSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile: ${activeProfile.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SkyBlueTextPrimary
                    )
                    Text(
                        text = "${activeProfile.endpointHost}:${activeProfile.endpointPort}",
                        fontSize = 11.sp,
                        color = SkyBlueTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Native Core Status Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SkyBlueSurfaceVariant
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (SingBoxAdapter.isNativeAvailable()) StatusConnected else StatusStarting)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (SingBoxAdapter.isNativeAvailable())
                            "libbox Native Core Linked"
                        else
                            "Native Core Bridge Pending",
                        fontSize = 10.sp,
                        color = SkyBlueTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
