package com.example.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.StatusError

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
    var isSearchVisible by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
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
            // Seed a representative list for testing or emulator environments
            listOf(
                AppFirewallItem("com.android.chrome", "Google Chrome", isSystemApp = false, mode = FirewallMode.VPN),
                AppFirewallItem("org.mozilla.firefox", "Firefox Browser", isSystemApp = false, mode = FirewallMode.VPN),
                AppFirewallItem("com.google.android.youtube", "YouTube", isSystemApp = false, mode = FirewallMode.VPN),
                AppFirewallItem("com.spotify.music", "Spotify", isSystemApp = false, mode = FirewallMode.VPN),
                AppFirewallItem("com.whatsapp", "WhatsApp", isSystemApp = false, mode = FirewallMode.VPN),
                AppFirewallItem("com.twitter.android", "X (Twitter)", isSystemApp = false, mode = FirewallMode.VPN),
                AppFirewallItem("com.google.android.gm", "Gmail", isSystemApp = true, mode = FirewallMode.VPN),
                AppFirewallItem("com.google.android.apps.maps", "Google Maps", isSystemApp = true, mode = FirewallMode.VPN),
                AppFirewallItem("com.android.settings", "System Settings", isSystemApp = true, mode = FirewallMode.DIRECT),
                AppFirewallItem("com.android.vending", "Google Play Store", isSystemApp = true, mode = FirewallMode.VPN)
            )
        }

        appRules = items.associateBy { it.packageName }
    }

    // Filter and Sort:
    // 1. Filter by system app toggle
    // 2. Filter by search query
    // 3. Sort: Configured items first, then alphabetical by App Name
    val displayedApps = appRules.values
        .filter { item ->
            if (!showSystemApps && item.isSystemApp) false
            else {
                if (searchQuery.isBlank()) true
                else item.appName.contains(searchQuery, ignoreCase = true) ||
                        item.packageName.contains(searchQuery, ignoreCase = true)
            }
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
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchVisible || searchQuery.isNotEmpty()) SkyBluePrimary else SkyBlueTextPrimary
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = SkyBlueTextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = SkyBlueSurface
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (showSystemApps) "Hide System Applications" else "Show System Applications",
                                    color = SkyBlueTextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                showSystemApps = !showSystemApps
                                showMenu = false
                            }
                        )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Bar (shown when toggled or non-empty)
            if (isSearchVisible || searchQuery.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter applications by name or package...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = SkyBlueTextSecondary,
                                modifier = Modifier.size(16.dp)
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
                        shape = RoundedCornerShape(10.dp),
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
            }

            // 2. List Header with "Set All At Once" controls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "APPLICATIONS (${displayedApps.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextTertiary,
                        letterSpacing = 0.5.sp
                    )

                    // Option B: Master selection to apply mode to all apps at once
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Set All:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SkyBlueTextTertiary
                        )
                        MasterModeChip(
                            label = "Block",
                            color = StatusError,
                            onClick = {
                                val updated = appRules.toMutableMap()
                                displayedApps.forEach { app ->
                                    updated[app.packageName] = app.copy(mode = FirewallMode.BLOCK, isConfigured = true)
                                }
                                appRules = updated
                            }
                        )
                        MasterModeChip(
                            label = "VPN",
                            color = SkyBluePrimary,
                            onClick = {
                                val updated = appRules.toMutableMap()
                                displayedApps.forEach { app ->
                                    updated[app.packageName] = app.copy(mode = FirewallMode.VPN, isConfigured = true)
                                }
                                appRules = updated
                            }
                        )
                        MasterModeChip(
                            label = "Direct",
                            color = Color(0xFF64748B),
                            onClick = {
                                val updated = appRules.toMutableMap()
                                displayedApps.forEach { app ->
                                    updated[app.packageName] = app.copy(mode = FirewallMode.DIRECT, isConfigured = true)
                                }
                                appRules = updated
                            }
                        )
                    }
                }
            }

            // 3. List of Apps (Compact One-Line Rows)
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
                Spacer(modifier = Modifier.height(16.dp))
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
            if (item.isConfigured) SkyBluePrimary.copy(alpha = 0.4f) else SkyBlueSurfaceBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon + Name (Clickable to expand)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggleExpand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (item.mode) {
                                    FirewallMode.BLOCK -> Color(0xFFFEE2E2)
                                    FirewallMode.VPN -> SkyBluePrimaryContainer
                                    FirewallMode.DIRECT -> SkyBlueSurfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.appName.firstOrNull()?.uppercase() ?: "A",
                            fontWeight = FontWeight.Bold,
                            color = when (item.mode) {
                                FirewallMode.BLOCK -> StatusError
                                FirewallMode.VPN -> SkyBlueOnPrimaryContainer
                                FirewallMode.DIRECT -> SkyBlueTextSecondary
                            },
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.appName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = SkyBlueTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (item.isSystemApp) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SkyBlueSurfaceVariant
                                ) {
                                    Text(
                                        text = "SYS",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SkyBlueTextTertiary,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.packageName,
                            fontSize = 10.sp,
                            color = SkyBlueTextTertiary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Compact Option B Trio Mode Checkbox / Segment
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactModeButton(
                        text = "Block",
                        isSelected = item.mode == FirewallMode.BLOCK,
                        activeColor = StatusError,
                        activeBg = Color(0xFFFEE2E2),
                        testTag = "firewall_mode_block",
                        onClick = { onModeSelected(FirewallMode.BLOCK) }
                    )
                    CompactModeButton(
                        text = "VPN",
                        isSelected = item.mode == FirewallMode.VPN,
                        activeColor = SkyBluePrimary,
                        activeBg = SkyBluePrimaryContainer,
                        testTag = "firewall_mode_vpn",
                        onClick = { onModeSelected(FirewallMode.VPN) }
                    )
                    CompactModeButton(
                        text = "Direct",
                        isSelected = item.mode == FirewallMode.DIRECT,
                        activeColor = Color(0xFF64748B),
                        activeBg = Color(0xFFE2E8F0),
                        testTag = "firewall_mode_direct",
                        onClick = { onModeSelected(FirewallMode.DIRECT) }
                    )
                }
            }

            // Expandable secondary details (speed exception & screen-aware rule)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(color = SkyBlueSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed Exception",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SkyBlueTextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Default", "Fast", "Throttled").forEach { opt ->
                                val sel = item.speedException == opt
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (sel) SkyBluePrimaryContainer else SkyBlueSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (sel) SkyBluePrimary else SkyBlueSurfaceBorder
                                    ),
                                    modifier = Modifier.clickable { onSpeedExceptionSelected(opt) }
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (sel) SkyBluePrimary else SkyBlueTextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Screen-Aware Policy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SkyBlueTextPrimary
                            )
                            Text(
                                text = "Block traffic when screen turns off",
                                fontSize = 9.sp,
                                color = SkyBlueTextSecondary
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
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactModeButton(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    activeBg: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) activeBg else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) activeColor else SkyBlueSurfaceBorder
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else SkyBlueTextTertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MasterModeChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
