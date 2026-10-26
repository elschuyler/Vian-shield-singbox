package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.config.WireGuardConfigParser
import com.example.engine.SingBoxAdapter
import com.example.engine.SingBoxPlatformCallback
import com.example.logging.LogCategory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        assertTrue(root.has("outbounds"))
        assertTrue(root.has("route"))

        val inbounds = root.getJSONArray("inbounds")
        val tun = inbounds.getJSONObject(0)
        assertEquals("tun", tun.getString("type"))
        assertEquals("vianshield-tun", tun.getString("interface_name"))

        val outbounds = root.getJSONArray("outbounds")
        val wg = outbounds.getJSONObject(0)
        assertEquals("wireguard", wg.getString("type"))
        assertEquals(profile.endpointHost, wg.getString("server"))
        assertEquals(profile.endpointPort, wg.getInt("server_port"))
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
    fun diagnosticViewModel_navigationBetweenMainAndLogKeeper() {
        val viewModel = DiagnosticViewModel(ApplicationProvider.getApplicationContext())
        assertEquals(AppScreen.MAIN, viewModel.currentScreen.value)

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
}
