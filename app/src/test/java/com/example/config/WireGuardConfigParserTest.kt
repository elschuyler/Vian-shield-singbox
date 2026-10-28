package com.example.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WireGuardConfigParserTest {

    // Standard 32-byte WireGuard keys: 43 Base64 chars + 1 '=' padding = 44 characters total
    private val validPrivateKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa="
    private val validPublicKey = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb="
    private val validPresharedKey = "ccccccccccccccccccccccccccccccccccccccccccc="

    @Test
    fun parse_validStandardConfig_succeeds() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1420

            [Peer]
            PublicKey = $validPublicKey
            PresharedKey = $validPresharedKey
            Endpoint = 203.0.113.10:51820
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf, "Production Node")
        assertTrue("Expected valid config to parse successfully: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val profile = result.getOrThrow()
        assertEquals("Production Node", profile.name)
        assertEquals(validPrivateKey, profile.privateKey)
        assertEquals("10.0.0.2/24", profile.address)
        assertEquals("1.1.1.1", profile.dns)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), profile.dnsServers)
        assertEquals(1420, profile.mtu)
        assertEquals(validPublicKey, profile.peerPublicKey)
        assertEquals(validPresharedKey, profile.presharedKey)
        assertEquals("203.0.113.10", profile.endpointHost)
        assertEquals(51820, profile.endpointPort)
        assertEquals(listOf("0.0.0.0/0", "::/0"), profile.allowedIps)
    }

    @Test
    fun parse_commentsAndWhitespace_handledCorrectly() {
        val conf = """
            # Global comment line
            ; Semicolon comment line
            
            [Interface]
               PrivateKey = $validPrivateKey  # inline comment with hash
               Address = 172.16.0.5/32 ; inline comment with semicolon
               DNS = 9.9.9.9
               MTU = 1360
            
            # Peer comment
            [Peer]
               PublicKey = $validPublicKey
               Endpoint = [2001:db8::1]:51820 # IPv6 endpoint
               AllowedIPs = 10.0.0.0/8 , 192.168.0.0/16
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf, "Clean Node")
        assertTrue("Expected config with comments & whitespace to parse successfully: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val profile = result.getOrThrow()
        assertEquals(validPrivateKey, profile.privateKey)
        assertEquals("172.16.0.5/32", profile.address)
        assertEquals("9.9.9.9", profile.dns)
        assertEquals(1360, profile.mtu)
        assertEquals("2001:db8::1", profile.endpointHost)
        assertEquals(51820, profile.endpointPort)
        assertEquals(listOf("10.0.0.0/8", "192.168.0.0/16"), profile.allowedIps)
    }

    @Test
    fun parse_addressWithoutCidr_autoAppendsSlash32() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 192.168.1.50
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.2:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue("Expected address normalization to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals("192.168.1.50/32", result.getOrThrow().address)
    }

    @Test
    fun parse_missingPrivateKey_fails() {
        val conf = """
            [Interface]
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.2:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue("Expected failure for missing PrivateKey", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PrivateKey") == true)
    }

    @Test
    fun parse_missingPublicKey_fails() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            [Peer]
            Endpoint = 198.51.100.2:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue("Expected failure for missing PublicKey: ${result.exceptionOrNull()?.message}", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PublicKey") == true)
    }

    @Test
    fun parse_missingEndpoint_fails() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = $validPublicKey
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue("Expected failure for missing Endpoint: ${result.exceptionOrNull()?.message}", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Endpoint") == true)
    }

    @Test
    fun parse_malformedEndpoint_fails() {
        val invalidPorts = listOf(
            "198.51.100.1",        // Missing port
            "198.51.100.1:",       // Empty port
            "198.51.100.1:abc",    // Non-numeric port
            "198.51.100.1:0",      // Out of range (0)
            "198.51.100.1:70000"   // Out of range (>65535)
        )

        for (badEndpoint in invalidPorts) {
            val conf = """
                [Interface]
                PrivateKey = $validPrivateKey
                Address = 10.0.0.2/24
                [Peer]
                PublicKey = $validPublicKey
                Endpoint = $badEndpoint
            """.trimIndent()

            val result = WireGuardConfigParser.parse(conf)
            assertTrue("Expected failure for malformed endpoint: $badEndpoint", result.isFailure)
        }
    }

    @Test
    fun parse_invalidKeyLengths_fail() {
        val shortKeyConf = """
            [Interface]
            PrivateKey = tooShortKey==
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(shortKeyConf)
        assertTrue("Expected failure for short key", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PrivateKey") == true)
    }

    @Test
    fun parseFromInputStream_readsSuccessfully() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val stream = ByteArrayInputStream(conf.toByteArray(Charsets.UTF_8))
        val result = WireGuardConfigParser.parseFromInputStream(stream, "StreamProfile")
        assertTrue(result.isSuccess)
        assertEquals("StreamProfile", result.getOrThrow().name)
    }

    @Test
    fun secretRedaction_toRedactedSummaryAndDisplaySummary_neverLeaksKeys() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            DNS = 1.1.1.1
            MTU = 1420
            [Peer]
            PublicKey = $validPublicKey
            PresharedKey = $validPresharedKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val profile = WireGuardConfigParser.parse(conf).getOrThrow()

        // Test toRedactedSummary()
        val summary = profile.toRedactedSummary()
        assertFalse("Summary must not contain PrivateKey", summary.contains(validPrivateKey))
        assertFalse("Summary must not contain PresharedKey", summary.contains(validPresharedKey))
        assertTrue("Summary must contain REDACTED", summary.contains("[REDACTED]"))

        // Test toDisplaySummary()
        val display = profile.toDisplaySummary()
        assertFalse("Display summary must not contain raw PrivateKey", display.values.any { it.contains(validPrivateKey) })
        assertFalse("Display summary must not contain raw PresharedKey", display.values.any { it.contains(validPresharedKey) })
        assertTrue("Display summary must mark PrivateKey as redacted", display["Private Key"]?.contains("[REDACTED") == true)
        assertTrue("Display summary must mark PresharedKey as redacted", display["Preshared Key"]?.contains("[REDACTED") == true)
        // Public key snippet only
        assertTrue("Display summary should only show snippet of public key", display["Peer Public Key"]?.contains("...") == true)
    }

    @Test
    fun toSingBoxJson_producesVerifiedSchema() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)

        val root = JSONObject(jsonStr)

        // Verify route
        val route = root.getJSONObject("route")
        assertTrue("route.auto_detect_interface must be true", route.getBoolean("auto_detect_interface"))
        assertEquals("wg-out", route.getString("final"))
        val rules = route.getJSONArray("rules")
        assertTrue("rules must contain at least 2 rules", rules.length() >= 2)
        assertEquals("dns", rules.getJSONObject(0).getString("protocol"))
        assertEquals("dns-out", rules.getJSONObject(0).getString("outbound"))

        // Verify inbounds
        val inbounds = root.getJSONArray("inbounds")
        val tun = inbounds.getJSONObject(0)
        assertEquals("gvisor", tun.getString("stack"))
        assertEquals("vianshield-tun", tun.getString("interface_name"))
        assertTrue("inbounds must contain address array", tun.has("address"))
        assertEquals(profile.address, tun.getJSONArray("address").getString(0))

        // Verify outbounds
        val outbounds = root.getJSONArray("outbounds")
        val dnsOut = outbounds.getJSONObject(0)
        assertEquals("dns", dnsOut.getString("type"))
        assertEquals("dns-out", dnsOut.getString("tag"))

        // Verify endpoints (modern sing-box v1.11.0 WireGuard schema with keepalive support)
        assertTrue("root must contain endpoints array", root.has("endpoints"))
        val endpoints = root.getJSONArray("endpoints")
        val wg = endpoints.getJSONObject(0)
        assertEquals("wireguard", wg.getString("type"))
        assertEquals("wg-out", wg.getString("tag"))
        assertEquals(profile.privateKey, wg.getString("private_key"))
        val peer = wg.getJSONArray("peers").getJSONObject(0)
        assertEquals(profile.endpointHost, peer.getString("address"))
        assertEquals(profile.endpointPort, peer.getInt("port"))
        assertEquals(profile.peerPublicKey, peer.getString("public_key"))

        // Verify DNS detour
        val dns = root.getJSONObject("dns")
        val dnsServers = dns.getJSONArray("servers")
        assertTrue(dnsServers.length() > 0)
        assertEquals("wg-out", dnsServers.getJSONObject(0).getString("detour"))

        // Verify experimental cache_file configuration
        assertTrue("root must contain experimental object", root.has("experimental"))
        val experimental = root.getJSONObject("experimental")
        assertTrue("experimental must contain cache_file", experimental.has("cache_file"))
        val cacheFile = experimental.getJSONObject("cache_file")
        assertFalse("Default toSingBoxJson without path must disable cache_file to prevent relative /cache.db error", cacheFile.getBoolean("enabled"))
    }

    @Test
    fun toSingBoxJson_withExplicitCachePath_configuresEnabledCacheFile() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val explicitPath = "/data/user/0/com.aistudio.vianshield.vxnpqk/files/singbox/cache.db"
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile, cacheFilePath = explicitPath, enableCacheFile = true)

        val root = JSONObject(jsonStr)
        assertTrue(root.has("experimental"))
        val cacheFile = root.getJSONObject("experimental").getJSONObject("cache_file")
        assertTrue("cache_file must be enabled when path is provided", cacheFile.getBoolean("enabled"))
        assertEquals(explicitPath, cacheFile.getString("path"))
        assertFalse(cacheFile.getBoolean("store_fakeip"))
    }

    @Test
    fun toSingBoxJson_disabledCacheFile_configuresDisabledCacheFile() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile, cacheFilePath = null, enableCacheFile = false)

        val root = JSONObject(jsonStr)
        val cacheFile = root.getJSONObject("experimental").getJSONObject("cache_file")
        assertFalse("cache_file must be disabled", cacheFile.getBoolean("enabled"))
    }

    @Test
    fun parse_multiAddress_ipv4AndIpv6_succeeds() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24, fd00::2/64
            DNS = 1.1.1.1, 8.8.8.8
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf, "DualStack")
        assertTrue("Expected dual stack parsing to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("10.0.0.2/24", profile.address)
        assertEquals(listOf("10.0.0.2/24", "fd00::2/64"), profile.addresses)
        assertEquals("1.1.1.1", profile.dns)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), profile.dnsServers)

        // Verify JSON includes both addresses
        val json = JSONObject(WireGuardConfigParser.toSingBoxJson(profile))
        val tunAddresses = json.getJSONArray("inbounds").getJSONObject(0).getJSONArray("address")
        assertEquals(2, tunAddresses.length())
        assertEquals("10.0.0.2/24", tunAddresses.getString(0))
        assertEquals("fd00::2/64", tunAddresses.getString(1))
    }

    @Test
    fun parse_persistentKeepalive_succeedsAndIncludedInJson() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
            PersistentKeepalive = 25
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(25, profile.persistentKeepalive)

        val json = JSONObject(WireGuardConfigParser.toSingBoxJson(profile))
        val endpoints = json.getJSONArray("endpoints")
        val wgEndpoint = endpoints.getJSONObject(0)
        val peer = wgEndpoint.getJSONArray("peers").getJSONObject(0)
        assertTrue("WireGuard peer must contain persistent_keepalive_interval", peer.has("persistent_keepalive_interval"))
        assertEquals(25, peer.getInt("persistent_keepalive_interval"))
    }

    @Test
    fun parse_multiplePeers_failsWithClearError() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.2:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue("Expected failure when multiple peers exist", result.isFailure)
        val err = result.exceptionOrNull()?.message ?: ""
        assertTrue("Error must explain multiple peers are not supported: $err", err.contains("Multiple [Peer] sections are not supported"))
    }

    @Test
    fun parse_unsupportedSection_failsWithClearError() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            [BadSection]
            Foo = Bar
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue("Expected failure for unsupported section", result.isFailure)
        val err = result.exceptionOrNull()?.message ?: ""
        assertTrue("Error must mention unsupported section: $err", err.contains("Unsupported section"))
    }

    @Test
    fun parse_omittedMtu_defaultsTo1280() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("Omitted MTU must default to mobile-safe 1280", 1280, profile.mtu)
    }

    @Test
    fun parse_oversizedMtu_clampedTo1420() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            MTU = 1500
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("MTU > 1420 must be clamped to MAX_MTU 1420", 1420, profile.mtu)
    }

    @Test
    fun parse_undersizedMtu_clampedTo1280() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            MTU = 1100
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("MTU < 1280 must be clamped to MIN_MTU 1280", 1280, profile.mtu)
    }

    @Test
    fun parse_validCustomMtu_preserved() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            MTU = 1360
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val result = WireGuardConfigParser.parse(conf)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("Valid custom MTU within [1280, 1420] must be preserved", 1360, profile.mtu)
    }
}
