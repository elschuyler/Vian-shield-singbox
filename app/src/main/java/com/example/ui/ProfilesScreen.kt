package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.WireGuardConfigParser
import com.example.config.WireGuardProfile
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
fun ProfilesScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val activeProfile by viewModel.activeProfile.collectAsState()
    val currentConfig by viewModel.currentConfig.collectAsState()
    val savedProfiles by viewModel.savedProfiles.collectAsState()
    val vpnState by viewModel.vpnState.collectAsState()
    val importedProfilePreview by viewModel.importedProfilePreview.collectAsState()
    val importError by viewModel.importError.collectAsState()

    val context = LocalContext.current
    var showRawEditor by remember { mutableStateOf(false) }
    var rawEditorText by remember { mutableStateOf(currentConfig) }
    var expandedProfileId by remember { mutableStateOf<String?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadConfigFile(context, uri)
        }
    }

    if (importedProfilePreview != null) {
        ImportProfileConfirmationDialog(
            profile = importedProfilePreview!!,
            onConfirm = {
                viewModel.confirmImportedProfile()
                Toast.makeText(context, "WireGuard profile applied and saved", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.dismissImportDialog() }
        )
    }

    if (importError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = StatusError,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Failed", fontWeight = FontWeight.Bold, color = SkyBlueTextPrimary)
                }
            },
            text = {
                Text(
                    text = importError ?: "Unknown parsing error",
                    color = SkyBlueTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportDialog() }) {
                    Text("OK", color = SkyBluePrimary)
                }
            },
            containerColor = SkyBlueSurface
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VPN",
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SkyBluePrimaryContainer
                        ) {
                            Text(
                                text = "WireGuard Profiles",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SkyBlueOnPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            rawEditorText = currentConfig
                            showRawEditor = true
                        },
                        modifier = Modifier.testTag("profiles_raw_editor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Config",
                            tint = SkyBlueTextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.resetToDefaultConfig()
                            Toast.makeText(context, "Reset to default profile", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("profiles_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Default",
                            tint = SkyBlueTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    importFileLauncher.launch(arrayOf("*/*", "application/octet-stream", "text/plain"))
                },
                containerColor = SkyBluePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .testTag("vpn_fab_add_profile")
                    .testTag("import_conf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import WireGuard .conf Profile",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Connection / Profile Health Status Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                VpnHealthStatusCard(vpnState = vpnState, activeProfile = activeProfile)
            }

            // 2. Saved WireGuard Profiles Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Profiles (${savedProfiles.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SkyBlueTextPrimary
                    )
                    OutlinedButton(
                        onClick = {
                            importFileLauncher.launch(arrayOf("*/*", "application/octet-stream", "text/plain"))
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .conf", fontSize = 11.sp)
                    }
                }
            }

            // 3. Saved Profiles List
            items(
                items = savedProfiles,
                key = { it.id }
            ) { item ->
                val isSelected = currentConfig.trim() == item.rawConfig.trim()
                val isExpanded = expandedProfileId == item.id

                SavedProfileCard(
                    item = item,
                    isSelected = isSelected,
                    isExpanded = isExpanded,
                    onSelect = { viewModel.selectSavedProfile(item) },
                    onToggleExpand = {
                        expandedProfileId = if (isExpanded) null else item.id
                    }
                )
            }

            // 4. Raw Editor Dialog / Card if open
            if (showRawEditor) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBluePrimary)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Manual Configuration Editor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SkyBlueTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Directly edit or inspect the active WireGuard .conf",
                                fontSize = 11.sp,
                                color = SkyBlueTextTertiary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = rawEditorText,
                                onValueChange = { rawEditorText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .testTag("raw_config_text_field"),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showRawEditor = false }) {
                                    Text("Cancel", color = SkyBlueTextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val parseCheck = WireGuardConfigParser.parse(rawEditorText)
                                        if (parseCheck.isSuccess) {
                                            viewModel.updateConfig(rawEditorText)
                                            showRawEditor = false
                                            Toast.makeText(context, "Config applied", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Invalid syntax: ${parseCheck.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                                ) {
                                    Text("Validate & Save")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun VpnHealthStatusCard(
    vpnState: VpnState,
    activeProfile: WireGuardProfile?
) {
    val (statusColor, statusText, statusIcon) = when (vpnState) {
        is VpnState.Connected -> Triple(StatusConnected, "Tunnel Connected", Icons.Default.CheckCircle)
        is VpnState.Starting -> Triple(StatusStarting, "Connecting...", Icons.Default.HourglassEmpty)
        is VpnState.Stopping -> Triple(StatusStarting, "Stopping...", Icons.Default.HourglassEmpty)
        is VpnState.Error -> Triple(StatusError, "Tunnel Error", Icons.Default.Error)
        is VpnState.Stopped -> Triple(StatusStopped, "Disconnected", Icons.Default.Stop)
    }

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
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SkyBlueTextPrimary
                        )
                        Text(
                            text = when (vpnState) {
                                is VpnState.Connected -> "Encrypted UDP userspace tunnel active"
                                is VpnState.Error -> vpnState.message
                                else -> "sing-box v1.11.0 ready"
                            },
                            fontSize = 11.sp,
                            color = SkyBlueTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SkyBlueSurfaceVariant
                ) {
                    Text(
                        text = "WireGuard",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SkyBlueTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (activeProfile != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active Endpoint", fontSize = 11.sp, color = SkyBlueTextTertiary)
                        Text(
                            "${activeProfile.endpointHost}:${activeProfile.endpointPort}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = SkyBlueTextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Tunnel Address", fontSize = 11.sp, color = SkyBlueTextTertiary)
                        Text(
                            activeProfile.address,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = SkyBlueTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedProfileCard(
    item: DiagnosticViewModel.SavedProfileItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_profile_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SkyBluePrimaryContainer.copy(alpha = 0.35f) else SkyBlueSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) SkyBluePrimary else SkyBlueSurfaceBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelect),
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
                            .background(if (isSelected) SkyBluePrimary else SkyBlueSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else SkyBlueTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SkyBlueTextPrimary
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SkyBluePrimary
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.endpoint,
                            fontSize = 11.sp,
                            color = SkyBlueTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Profile Details",
                        tint = SkyBlueTextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                val parsed = remember(item.rawConfig) {
                    WireGuardConfigParser.parse(item.rawConfig).getOrNull()
                }

                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (parsed != null) {
                        ProfileDetailRow("Tunnel Addresses", parsed.addresses.joinToString(", "))
                        ProfileDetailRow("DNS Resolvers", parsed.dnsServers.joinToString(", "))
                        ProfileDetailRow("MTU Buffer", "${parsed.mtu} bytes (RFC 8200 Standard)")
                        parsed.persistentKeepalive?.let {
                            ProfileDetailRow("Persistent Keepalive", "$it seconds")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!isSelected) {
                        Button(
                            onClick = onSelect,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Text("Switch to this Profile", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = SkyBlueTextTertiary)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SkyBlueTextPrimary)
    }
}

@Composable
private fun ImportProfileConfirmationDialog(
    profile: WireGuardProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm Profile Import",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SkyBlueTextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Profile: ${profile.name}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = SkyBlueTextPrimary
                )
                Text(
                    text = "Endpoint: ${profile.endpointHost}:${profile.endpointPort}",
                    fontSize = 12.sp,
                    color = SkyBlueTextSecondary
                )
                Text(
                    text = "Address: ${profile.addresses.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = SkyBlueTextSecondary
                )
                Text(
                    text = "DNS: ${profile.dnsServers.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = SkyBlueTextSecondary
                )
                Text(
                    text = "MTU: ${profile.mtu} bytes",
                    fontSize = 12.sp,
                    color = SkyBlueTextSecondary
                )
                profile.persistentKeepalive?.let {
                    Text(
                        text = "Keepalive: $it seconds",
                        fontSize = 12.sp,
                        color = SkyBlueTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Private keys are validated and safely stored in memory. They are never exported or logged.",
                    fontSize = 11.sp,
                    color = StatusConnected,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                modifier = Modifier.testTag("confirm_import_button")
            ) {
                Text("Apply Profile")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_import_button")
            ) {
                Text("Cancel", color = SkyBlueTextSecondary)
            }
        },
        containerColor = SkyBlueSurface
    )
}
