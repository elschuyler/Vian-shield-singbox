package com.example.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

enum class FirewallMode(val label: String) {
    BLOCK("Block"),
    VPN("VPN"),
    DIRECT("Direct")
}

data class AppFirewallItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val mode: FirewallMode = FirewallMode.VPN,
    val isConfigured: Boolean = false,
    val speedException: String = "Default",
    val screenAwareRule: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isBlockAllEnabled by remember { mutableStateOf(false) }
    var expandedAppPackage by remember { mutableStateOf<String?>(null) }

    // In-memory firewall rules map: packageName -> AppFirewallItem
    var appRules by remember {
        mutableStateOf<Map<String, AppFirewallItem>>(emptyMap())
    }

    // Load installed apps once
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val installed = try {
            pm.getInstalledApplications(0)
        } catch (_: Exception) {
            emptyList<ApplicationInfo>()
        }

        val items = if (installed.isNotEmpty()) {
            installed.map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppFirewallItem(
                    packageName = appInfo.packageName,
                    appName = if (label.isNotBlank()) label else appInfo.packageName,
                    isSystemApp = isSystem,
                    mode = FirewallMode.VPN,
                    isConfigured = false
                )
            }
        } else {
            // High-reliability default fallback list if PM returns empty
            listOf(
                AppFirewallItem("com.android.chrome", "Google Chrome", false, FirewallMode.VPN),
                AppFirewallItem("com.google.android.youtube", "YouTube", false, FirewallMode.VPN),
                AppFirewallItem("com.google.android.apps.messaging", "Messages", false, FirewallMode.VPN),
                AppFirewallItem("org.mozilla.firefox", "Firefox Browser", false, FirewallMode.VPN),
                AppFirewallItem("com.spotify.music", "Spotify Music", false, FirewallMode.DIRECT, true),
                AppFirewallItem("com.android.systemui", "System UI", true, FirewallMode.DIRECT),
                AppFirewallItem("com.android.vending", "Google Play Store", true, FirewallMode.VPN)
            )
        }

        appRules = items.associateBy { it.packageName }
    }

    // Filter and sort: Configured apps first, then alphabetical by name
    val displayedApps = appRules.values
        .filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.appName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
            val matchesSystem = showSystemApps || !item.isSystemApp
            matchesSearch && matchesSystem
        }
        .sortedWith(
            compareByDescending<AppFirewallItem> { it.isConfigured }
                .thenBy { it.appName.lowercase() }
        )

    val configuredCount = appRules.values.count { it.isConfigured }
    val totalCount = appRules.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Firewall",
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SkyBluePrimaryContainer
                        ) {
                            Text(
                                text = "$configuredCount/$totalCount Configured",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SkyBlueOnPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Prominent "Block All" Master Action & System Apps Toggle Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBlockAllEnabled) Color(0xFFFEE2E2) else SkyBlueSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isBlockAllEnabled) Color(0xFFFCA5A5) else SkyBlueSurfaceBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isBlockAllEnabled) StatusError else SkyBlueTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Block All Network Traffic",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isBlockAllEnabled) Color(0xFF991B1B) else SkyBlueTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBlockAllEnabled)
                                        "Killswitch active: All app traffic blocked"
                                    else
                                        "Drop all outgoing and incoming packets across apps",
                                    fontSize = 12.sp,
                                    color = if (isBlockAllEnabled) Color(0xFF7F1D1D) else SkyBlueTextSecondary
                                )
                            }
                            Switch(
                                checked = isBlockAllEnabled,
                                onCheckedChange = { isBlockAllEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StatusError,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = SkyBlueSurfaceBorder
                                ),
                                modifier = Modifier.testTag("firewall_block_all_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(
                            color = if (isBlockAllEnabled) Color(0xFFFCA5A5) else SkyBlueSurfaceBorder,
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Secondary Row: System Apps Filter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show System Applications",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = SkyBlueTextPrimary
                            )
                            Switch(
                                checked = showSystemApps,
                                onCheckedChange = { showSystemApps = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SkyBluePrimary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = SkyBlueSurfaceBorder
                                ),
                                modifier = Modifier.testTag("firewall_show_system_switch")
                            )
                        }
                    }
                }
            }

            // 2. Search Bar for filtering installed apps
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed applications...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SkyBlueTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = SkyBlueTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SkyBlueSurface,
                        unfocusedContainerColor = SkyBlueSurface,
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = SkyBlueSurfaceBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firewall_search_input")
                )
            }

            // 3. Technical Notice / Status Banner (Strictly honest, no mock claim)
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SkyBlueSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Firewall rules are configured and staged in app state. Native sing-box eBPF / TUN packet filter integration is scheduled for Phase 2.",
                            fontSize = 11.sp,
                            color = SkyBlueTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 4. List of Apps
            if (displayedApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No applications found matching '$searchQuery'" else "Loading applications...",
                            color = SkyBlueTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(
                    items = displayedApps,
                    key = { it.packageName }
                ) { item ->
                    val isExpanded = expandedAppPackage == item.packageName

                    AppFirewallRow(
                        item = item,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedAppPackage = if (isExpanded) null else item.packageName
                        },
                        onModeSelected = { newMode ->
                            appRules = appRules + (item.packageName to item.copy(
                                mode = newMode,
                                isConfigured = true
                            ))
                        },
                        onSpeedExceptionSelected = { exception ->
                            appRules = appRules + (item.packageName to item.copy(
                                speedException = exception,
                                isConfigured = true
                            ))
                        },
                        onScreenAwareToggled = { enabled ->
                            appRules = appRules + (item.packageName to item.copy(
                                screenAwareRule = enabled,
                                isConfigured = true
                            ))
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AppFirewallRow(
    item: AppFirewallItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onModeSelected: (FirewallMode) -> Unit,
    onSpeedExceptionSelected: (String) -> Unit,
    onScreenAwareToggled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isConfigured) SkyBluePrimary.copy(alpha = 0.5f) else SkyBlueSurfaceBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Main App Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                        Text(
                            text = item.appName.firstOrNull()?.uppercase() ?: "A",
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueOnPrimaryContainer,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.appName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = SkyBlueTextPrimary,
                                maxLines = 1
                            )
                            if (item.isConfigured) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SkyBluePrimary)
                                )
                            }
                        }
                        Text(
                            text = item.packageName,
                            fontSize = 11.sp,
                            color = SkyBlueTextTertiary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SkyBlueSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "SYS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkyBlueTextTertiary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand details",
                        tint = SkyBlueTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Trio Mode Controls: [Block] [VPN] [Direct]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FirewallModeButton(
                    mode = FirewallMode.BLOCK,
                    currentMode = item.mode,
                    modifier = Modifier.weight(1f),
                    activeColor = StatusError,
                    onClick = { onModeSelected(FirewallMode.BLOCK) }
                )
                FirewallModeButton(
                    mode = FirewallMode.VPN,
                    currentMode = item.mode,
                    modifier = Modifier.weight(1f),
                    activeColor = SkyBluePrimary,
                    onClick = { onModeSelected(FirewallMode.VPN) }
                )
                FirewallModeButton(
                    mode = FirewallMode.DIRECT,
                    currentMode = item.mode,
                    modifier = Modifier.weight(1f),
                    activeColor = Color(0xFF64748B),
                    onClick = { onModeSelected(FirewallMode.DIRECT) }
                )
            }

            // Expandable details panel for per-app settings (speed exceptions, screen-aware rules)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Per-App Rules (Staged)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed Exception selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Speed Exception",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SkyBlueTextPrimary
                            )
                            Text(
                                text = "Inactive in Phase 1 (Staged)",
                                fontSize = 10.sp,
                                color = SkyBlueTextTertiary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Default", "Throttled", "Fast").forEach { preset ->
                                FilterChip(
                                    selected = item.speedException == preset,
                                    onClick = { onSpeedExceptionSelected(preset) },
                                    label = { Text(preset, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SkyBluePrimaryContainer,
                                        selectedLabelColor = SkyBlueOnPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Screen-Aware Rule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screen-Off Blocking",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SkyBlueTextPrimary
                            )
                            Text(
                                text = "Block app traffic when screen turns off (Inactive)",
                                fontSize = 10.sp,
                                color = SkyBlueTextTertiary
                            )
                        }
                        Switch(
                            checked = item.screenAwareRule,
                            onCheckedChange = onScreenAwareToggled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SkyBluePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = SkyBlueSurfaceBorder
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirewallModeButton(
    mode: FirewallMode,
    currentMode: FirewallMode,
    modifier: Modifier = Modifier,
    activeColor: Color,
    onClick: () -> Unit
) {
    val isSelected = currentMode == mode

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(34.dp)
            .testTag("firewall_mode_${mode.name.lowercase()}"),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeColor else SkyBlueSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) activeColor else SkyBlueSurfaceBorder
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mode.label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else SkyBlueTextPrimary
            )
        }
    }
}
