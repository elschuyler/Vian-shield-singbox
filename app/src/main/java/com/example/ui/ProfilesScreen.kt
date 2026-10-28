package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val activeProfile by viewModel.activeProfile.collectAsState()
    val currentConfig by viewModel.currentConfig.collectAsState()
    val importedProfilePreview by viewModel.importedProfilePreview.collectAsState()
    val importError by viewModel.importError.collectAsState()

    val context = LocalContext.current
    var showRawEditor by remember { mutableStateOf(false) }
    var rawEditorText by remember { mutableStateOf(currentConfig) }

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
                Toast.makeText(context, "WireGuard profile applied", Toast.LENGTH_SHORT).show()
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
                    Text(
                        text = "Profiles",
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("profiles_back_button")
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
            // Import Action Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Import WireGuard Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SkyBlueTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import a standard WireGuard .conf file via Android Storage Access Framework. Cryptographic keys are validated and never exposed in logs.",
                        fontSize = 12.sp,
                        color = SkyBlueTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            importFileLauncher.launch(arrayOf("*/*", "application/octet-stream", "text/plain"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("import_conf_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select .conf File", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Active Profile Card
            Text(
                text = "Active Configuration",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = SkyBlueTextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (activeProfile != null) {
                        val profile = activeProfile!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = profile.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SkyBlueTextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SkyBluePrimaryContainer
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SkyBlueOnPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ProfileDetailRow("Endpoint", "${profile.endpointHost}:${profile.endpointPort}")
                        ProfileDetailRow("Tunnel Address", profile.addresses.joinToString(", "))
                        ProfileDetailRow("DNS Servers", profile.dnsServers.joinToString(", "))
                        ProfileDetailRow("Allowed IPs", profile.allowedIps.joinToString(", "))
                        ProfileDetailRow("Tunnel MTU", "${profile.mtu} bytes (mobile safe)")
                        ProfileDetailRow(
                            "Persistent Keepalive",
                            profile.persistentKeepalive?.let { "$it seconds" } ?: "Disabled (0)"
                        )
                    } else {
                        Text(
                            text = "Invalid or unparseable profile loaded.",
                            color = StatusError,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                rawEditorText = currentConfig
                                showRawEditor = !showRawEditor
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showRawEditor) "Hide Editor" else "Edit Raw", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.resetToDefaultConfig()
                                Toast.makeText(context, "Reset to default test profile", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Default", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Raw Configuration Editor Card (Expandable)
            if (showRawEditor) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Manual Configuration Editor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SkyBlueTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = rawEditorText,
                            onValueChange = { rawEditorText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .testTag("raw_config_editor_field"),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            placeholder = { Text("[Interface]\nPrivateKey = ...") }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    val result = WireGuardConfigParser.parse(rawEditorText, "Custom Configuration")
                                    if (result.isSuccess) {
                                        viewModel.updateConfig(rawEditorText)
                                        showRawEditor = false
                                        Toast.makeText(context, "Configuration updated", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Changes")
                            }
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
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = SkyBlueTextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SkyBlueTextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ImportProfileConfirmationDialog(
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
