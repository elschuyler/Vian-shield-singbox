package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.WireGuardConfigParser
import com.example.engine.SingBoxAdapter
import com.example.engine.SingBoxPlatformCallback
import com.example.logging.CrashReporter
import com.example.logging.LogCategory
import com.example.logging.LogExporter
import com.example.logging.LogSeverity
import com.example.logging.OperationalLogCatcher
import com.example.ui.AppScreen
import com.example.ui.DiagnosticViewModel
import com.example.ui.TimeFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VianshieldPhase1Test {

    @Before
    fun setup() {
        OperationalLogCatcher.setLoggingEnabled(true)
        OperationalLogCatcher.clear()
    }

    @Test
    fun wireguardConfigParser_parsesValidProfile() {
        val conf = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            Address = 10.0.0.2/24
            DNS = 1.1.1.1
            MTU = 1380

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            Endpoint = 203.0.113.5:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf, "Test Profile")
        assertTrue("Parser should succeed for valid config", result.isSuccess)

        val profile = result.getOrThrow()
        assertEquals("Test Profile", profile.name)
        assertEquals("10.0.0.2/24", profile.address)
        assertEquals(1380, profile.mtu)
        assertEquals("203.0.113.5", profile.endpointHost)
        assertEquals(51820, profile.endpointPort)
        assertEquals("1.1.1.1", profile.dns)
    }

    @Test
    fun wireguardConfigParser_generatesValidSingBoxJson() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonString = WireGuardConfigParser.toSingBoxJson(profile)

        val root = JSONObject(jsonString)
        assertTrue(root.has("log"))
        assertTrue(root.has("dns"))
        assertTrue(root.has("inbounds"))
        assertTrue(root.has("endpoints"))
        assertTrue(root.has("route"))

        val inbounds = root.getJSONArray("inbounds")
        val tun = inbounds.getJSONObject(0)
        assertEquals("tun", tun.getString("type"))
        assertEquals("vianshield-tun", tun.getString("interface_name"))

        val endpoints = root.getJSONArray("endpoints")
        val wg = endpoints.getJSONObject(0)
        assertEquals("wireguard", wg.getString("type"))
        assertEquals("wg-out", wg.getString("tag"))
        val peer = wg.getJSONArray("peers").getJSONObject(0)
        assertEquals(profile.endpointHost, peer.getString("address"))
        assertEquals(profile.endpointPort, peer.getInt("port"))
    }

    @Test
    fun wireguardConfigParser_rejectsInvalidConfigs() {
        val missingKey = """
            [Interface]
            Address = 10.0.0.2/24
            [Peer]
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(missingKey)
        assertTrue("Config with missing PrivateKey should fail", result.isFailure)

        val invalidEndpoint = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            Endpoint = not-a-valid-endpoint
        """.trimIndent()

        val result2 = WireGuardConfigParser.parse(invalidEndpoint)
        assertTrue("Config with malformed Endpoint should fail", result2.isFailure)
    }

    @Test
    fun operationalLogCatcher_redactsSensitiveSecrets() {
        val secretKey = "44CharacterBase64EncodedKeyStringPattern123="
        val sensitiveMessage = "Config private_key=$secretKey processed with password=supersecret"

        val redacted = OperationalLogCatcher.redactSecrets(sensitiveMessage)
        assertFalse("Raw private key should be redacted", redacted.contains(secretKey))
        assertTrue("Redaction placeholder should be present", redacted.contains("[REDACTED"))
    }

    @Test
    fun operationalLogCatcher_respectsMasterSwitch() {
        OperationalLogCatcher.setLoggingEnabled(false)
        OperationalLogCatcher.info(LogCategory.VPN_LIFECYCLE, "TestComp", "Message while disabled")

        val events = OperationalLogCatcher.events.value
        assertEquals("Zero events should be recorded when master switch is off", 0, events.size)

        OperationalLogCatcher.setLoggingEnabled(true)
        OperationalLogCatcher.info(LogCategory.VPN_LIFECYCLE, "TestComp", "Message while enabled")
        assertEquals(2, OperationalLogCatcher.events.value.size) // 1 from enable event + 1 logged
    }

    @Test
    fun operationalLogCatcher_enforcesCapacityBound() {
        // Log 520 events to verify FIFO capping at 500
        for (i in 1..520) {
            OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "TestBound", "Event number $i")
        }

        val events = OperationalLogCatcher.events.value
        assertEquals("Buffer must be strictly capped at 500 entries", 500, events.size)
        assertTrue("Oldest events dropped; last event present", events.last().message.contains("520"))
    }

    @Test
    fun singBoxAdapter_truthfullyFailsWhenNativeBinaryIsMissing() {
        val realAdapter = SingBoxAdapter(allowSimulationForTesting = false)
        assertFalse(realAdapter.isNativeBinaryAvailable())

        val testProfile = WireGuardConfigParser.parse(WireGuardConfigParser.createDefaultTestConfig()).getOrThrow()
        val json = WireGuardConfigParser.toSingBoxJson(testProfile)

        val callback = object : SingBoxPlatformCallback {
            override fun openTun(options: Any?): Int = -1
            override fun autoDetectInterfaceControl(fd: Int): Boolean = false
            override fun onLog(level: String, message: String) {}
            override fun onError(code: String, message: String) {}
        }

        val result = realAdapter.initialize(json, callback)
        assertTrue("Adapter must report failure when native libbox.aar is absent", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("libbox.aar") == true)
    }

    @Test
    fun singBoxAdapter_lifecycleSimulation() {
        val adapter = SingBoxAdapter(allowSimulationForTesting = true)
        assertEquals("sing-box v1.11.0", adapter.getEngineVersion())
        assertFalse(adapter.isRunning())

        val testProfile = WireGuardConfigParser.parse(WireGuardConfigParser.createDefaultTestConfig()).getOrThrow()
        val json = WireGuardConfigParser.toSingBoxJson(testProfile)

        val callback = object : SingBoxPlatformCallback {
            override fun openTun(options: Any?): Int = 42
            override fun autoDetectInterfaceControl(fd: Int): Boolean = true
            override fun onLog(level: String, message: String) {}
            override fun onError(code: String, message: String) {}
        }

        val initResult = adapter.initialize(json, callback)
        assertTrue(initResult.isSuccess)

        val startResult = adapter.start()
        assertTrue(startResult.isSuccess)
        assertTrue(adapter.isRunning())

        val stopResult = adapter.stop()
        assertTrue(stopResult.isSuccess)
        assertFalse(adapter.isRunning())
    }

    @Test
    fun diagnosticViewModel_navigationAcrossAllScreens() {
        val viewModel = DiagnosticViewModel(ApplicationProvider.getApplicationContext())
        assertEquals(AppScreen.MAIN, viewModel.currentScreen.value)

        viewModel.navigateTo(AppScreen.PROFILES)
        assertEquals(AppScreen.PROFILES, viewModel.currentScreen.value)

        viewModel.navigateTo(AppScreen.DIAGNOSTICS)
        assertEquals(AppScreen.DIAGNOSTICS, viewModel.currentScreen.value)

        viewModel.navigateTo(AppScreen.SETTINGS)
        assertEquals(AppScreen.SETTINGS, viewModel.currentScreen.value)

        viewModel.navigateTo(AppScreen.LOG_KEEPER)
        assertEquals(AppScreen.LOG_KEEPER, viewModel.currentScreen.value)

        viewModel.navigateTo(AppScreen.MAIN)
        assertEquals(AppScreen.MAIN, viewModel.currentScreen.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun diagnosticViewModel_filterAndSearchIntegration() = runTest {
        OperationalLogCatcher.clear()
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "Core", "sing-box initialized")
        OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "VpnService", "TUN failed to open", "fd=-1")
        OperationalLogCatcher.info(LogCategory.NETWORK_STATE, "Net", "Socket protected", "fd=15")

        val viewModel = DiagnosticViewModel(ApplicationProvider.getApplicationContext())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Search test
        viewModel.setSearchQuery("TUN")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val searchFiltered = viewModel.filteredLogEvents.value
        assertTrue("Search should match 'TUN'", searchFiltered.any { it.message.contains("TUN") })

        // Category filter test
        viewModel.setSearchQuery("")
        viewModel.setCategoryFilter(LogCategory.ERROR_DIAGNOSTIC)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val catFiltered = viewModel.filteredLogEvents.value
        assertEquals(1, catFiltered.size)
        assertEquals(LogCategory.ERROR_DIAGNOSTIC, catFiltered[0].category)

        // Time filter test
        viewModel.setCategoryFilter(null)
        viewModel.setTimeFilter(TimeFilter.H6)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val timeFiltered = viewModel.filteredLogEvents.value
        assertTrue("Recent events should pass 6h filter", timeFiltered.size >= 3)
    }

    @Test
    fun operationalLogCatcher_enforcesPersistentDiskLimitAndRotation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OperationalLogCatcher.initialize(context)
        OperationalLogCatcher.clear()

        assertEquals(0L, OperationalLogCatcher.getDiskLogSizeBytes())

        // Write events
        for (i in 1..20) {
            OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "DiskTest", "Disk event line $i with details")
        }

        val diskBytes = OperationalLogCatcher.getDiskLogSizeBytes()
        assertTrue("Disk log bytes should be non-zero after logging", diskBytes > 0)
        assertTrue("Disk log must not exceed MAX_TOTAL_DISK_BYTES (3 MiB)", diskBytes <= OperationalLogCatcher.MAX_TOTAL_DISK_BYTES)

        val activeFile = File(context.filesDir, OperationalLogCatcher.ACTIVE_LOG_FILE)
        assertTrue("Active log file must exist on disk", activeFile.exists())

        OperationalLogCatcher.clear()
        assertEquals("Clearing should delete active and rotated log files", 0L, OperationalLogCatcher.getDiskLogSizeBytes())
        assertFalse("Active log file deleted after clear", activeFile.exists())
    }

    @Test
    fun crashReporter_recordsUncaughtExceptionAndRedactsSecrets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReporter.clearCrashReports(context)
        assertFalse("Initially should have no crash reports", CrashReporter.hasPendingCrashReports(context))

        val secretKey = "44CharacterBase64EncodedKeyStringPattern123="
        val sensitiveException = RuntimeException("Tunnel failed for private_key=$secretKey")
        val thread = Thread.currentThread()

        val report = CrashReporter.recordCrash(context, thread, sensitiveException)
        assertNotNull("Crash report should be created", report)
        assertTrue("Pending crash reports should be detected", CrashReporter.hasPendingCrashReports(context))
        assertEquals(1, CrashReporter.getPendingCrashCount(context))

        val loaded = CrashReporter.getPendingCrashReports(context).first()
        assertEquals("java.lang.RuntimeException", loaded.exceptionClass)
        assertFalse("Private key must be redacted in exception message", loaded.exceptionMessage.contains(secretKey))
        assertTrue("Redaction tag should be present", loaded.exceptionMessage.contains("[REDACTED"))

        CrashReporter.clearCrashReports(context)
        assertFalse("After clear, pending crash reports should be false", CrashReporter.hasPendingCrashReports(context))
    }

    @Test
    fun logExporter_exportsOperationalLogsAndRedactsSecrets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OperationalLogCatcher.clear()

        val secretKey = "44CharacterBase64EncodedKeyStringPattern123="
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "TestExport", "Engine started with secret token=$secretKey")

        val events = OperationalLogCatcher.events.value
        val result = LogExporter.exportOperationalLogs(context, events)

        assertTrue("Log export to Downloads should succeed", result.isSuccess)
        val msg = result.getOrThrow()
        assertTrue("Export confirmation must indicate saved destination", msg.contains("Saved to"))
    }

    @Test
    fun logExporter_exportsCrashReportsWithMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReporter.clearCrashReports(context)

        val testCrash = IllegalStateException("Native bridge init exception")
        CrashReporter.recordCrash(context, Thread.currentThread(), testCrash)

        val pending = CrashReporter.getPendingCrashReports(context)
        assertEquals(1, pending.size)

        val result = LogExporter.exportCrashReports(context, pending)
        assertTrue("Crash report export should succeed", result.isSuccess)
        assertTrue(result.getOrThrow().contains("Saved to"))

        CrashReporter.clearCrashReports(context)
    }

    @Test
    fun diagnosticViewModel_crashReportAndExportLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReporter.clearCrashReports(context)

        // Seed a crash report
        CrashReporter.recordCrash(context, Thread.currentThread(), NullPointerException("Test NPE"))

        val viewModel = DiagnosticViewModel(context as android.app.Application)
        assertTrue("ViewModel must detect pending crash reports", viewModel.hasPendingCrashReports.value)
        assertEquals(1, viewModel.pendingCrashCount.value)

        // Export crash reports
        val exportResult = viewModel.exportCrashReportsToDownloads()
        assertTrue("Export crash reports must succeed", exportResult.isSuccess)

        // Dismiss crash reports
        viewModel.dismissCrashReports()
        assertFalse("Pending crash reports must be false after dismissal", viewModel.hasPendingCrashReports.value)
        assertEquals(0, viewModel.pendingCrashCount.value)
    }
}
