package com.example.config

import org.json.JSONArray
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
@Config(sdk = [35])
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

        // Verify experimental cache_file configuration is eliminated in v1.14.4
        assertFalse("In sing-box v1.14.4, experimental.cache_file is eliminated", root.has("experimental"))
    }

    @Test
    fun toSingBoxJson_eliminatesCacheFileWorkaround() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)

        val root = JSONObject(jsonStr)
        assertFalse("v1.14.4 JSON schema must not contain experimental object", root.has("experimental"))
        assertTrue("Must contain valid log object", root.has("log"))
        assertTrue("Must contain inbounds", root.has("inbounds"))
        assertTrue("Must contain endpoints", root.has("endpoints"))
        assertTrue("Must contain outbounds", root.has("outbounds"))
        assertTrue("Must contain route", root.has("route"))
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

    // =========================================================================
    // Phase 1.19: sing-box 1.14 DNS Schema Regression Tests
    // =========================================================================

    @Test
    fun dns_importedProfileDns_generatesModernDnsServerObject() {
        val conf = """
            [Interface]
            PrivateKey = $validPrivateKey
            Address = 10.0.0.2/24
            DNS = 1.1.1.1, 8.8.8.8
            [Peer]
            PublicKey = $validPublicKey
            Endpoint = 198.51.100.1:51820
        """.trimIndent()

        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)
        val root = JSONObject(jsonStr)

        val dns = root.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        assertEquals(2, servers.length())

        val s1 = servers.getJSONObject(0)
        assertEquals("udp", s1.getString("type"))
        assertEquals("1.1.1.1", s1.getString("server"))
        assertEquals(53, s1.getInt("server_port"))
        assertEquals("wg-out", s1.getString("detour"))
        assertFalse("Legacy 'address' field must not be present", s1.has("address"))

        val s2 = servers.getJSONObject(1)
        assertEquals("udp", s2.getString("type"))
        assertEquals("8.8.8.8", s2.getString("server"))
        assertEquals(53, s2.getInt("server_port"))
        assertEquals("wg-out", s2.getString("detour"))
        assertFalse("Legacy 'address' field must not be present", s2.has("address"))
    }

    @Test
    fun dns_googleDnsSelection_generatesModernDnsServerObject() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile, dnsProviderOverride = "Google Public DNS (8.8.8.8)")
        val root = JSONObject(jsonStr)

        val dns = root.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        assertTrue("Google DNS must generate at least 2 servers", servers.length() >= 2)

        val s1 = servers.getJSONObject(0)
        assertEquals("udp", s1.getString("type"))
        assertEquals("8.8.8.8", s1.getString("server"))
        assertEquals(53, s1.getInt("server_port"))
        assertEquals("wg-out", s1.getString("detour"))
        assertFalse("Legacy 'address' field must not be present", s1.has("address"))

        val s2 = servers.getJSONObject(1)
        assertEquals("udp", s2.getString("type"))
        assertEquals("8.8.4.4", s2.getString("server"))
        assertEquals(53, s2.getInt("server_port"))
        assertEquals("wg-out", s2.getString("detour"))
        assertFalse("Legacy 'address' field must not be present", s2.has("address"))
    }

    @Test
    fun dns_cloudflareDnsSelection_generatesModernDnsServerObject() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile, dnsProviderOverride = "Cloudflare DNS (1.1.1.1)")
        val root = JSONObject(jsonStr)

        val dns = root.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        assertTrue("Cloudflare DNS must generate at least 2 servers", servers.length() >= 2)

        val s1 = servers.getJSONObject(0)
        assertEquals("udp", s1.getString("type"))
        assertEquals("1.1.1.1", s1.getString("server"))
        assertEquals(53, s1.getInt("server_port"))
        assertEquals("wg-out", s1.getString("detour"))
        assertFalse("Legacy 'address' field must not be present", s1.has("address"))

        val s2 = servers.getJSONObject(1)
        assertEquals("udp", s2.getString("type"))
        assertEquals("1.0.0.1", s2.getString("server"))
        assertEquals(53, s2.getInt("server_port"))
        assertEquals("wg-out", s2.getString("detour"))
        assertFalse("Legacy 'address' field must not be present", s2.has("address"))
    }

    @Test
    fun dns_everyGeneratedServer_hasRequiredFieldsForTransport() {
        // 1. Plain UDP
        val udp = WireGuardConfigParser.parseDnsServerObject("9.9.9.9")
        assertEquals("udp", udp.getString("type"))
        assertEquals("9.9.9.9", udp.getString("server"))
        assertEquals(53, udp.getInt("server_port"))
        assertEquals("wg-out", udp.getString("detour"))
        assertTrue(udp.has("tag"))
        assertFalse(udp.has("address"))

        // 2. TCP
        val tcp = WireGuardConfigParser.parseDnsServerObject("tcp://8.8.8.8:5353")
        assertEquals("tcp", tcp.getString("type"))
        assertEquals("8.8.8.8", tcp.getString("server"))
        assertEquals(5353, tcp.getInt("server_port"))
        assertEquals("wg-out", tcp.getString("detour"))
        assertTrue(tcp.has("tag"))
        assertFalse(tcp.has("address"))

        // 3. DNS-over-TLS (DoT)
        val tls = WireGuardConfigParser.parseDnsServerObject("tls://dns.google:853")
        assertEquals("tls", tls.getString("type"))
        assertEquals("dns.google", tls.getString("server"))
        assertEquals(853, tls.getInt("server_port"))
        assertEquals("wg-out", tls.getString("detour"))
        assertTrue(tls.has("tls"))
        assertEquals("dns.google", tls.getJSONObject("tls").getString("server_name"))
        assertTrue(tls.has("tag"))
        assertFalse(tls.has("address"))

        // 4. DNS-over-HTTPS (DoH)
        val https = WireGuardConfigParser.parseDnsServerObject("https://cloudflare-dns.com/dns-query")
        assertEquals("https", https.getString("type"))
        assertEquals("cloudflare-dns.com", https.getString("server"))
        assertEquals(443, https.getInt("server_port"))
        assertEquals("/dns-query", https.getString("path"))
        assertEquals("wg-out", https.getString("detour"))
        assertTrue(https.has("tls"))
        assertEquals("cloudflare-dns.com", https.getJSONObject("tls").getString("server_name"))
        assertTrue(https.has("tag"))
        assertFalse(https.has("address"))

        // 5. Local
        val local = WireGuardConfigParser.parseDnsServerObject("local")
        assertEquals("local", local.getString("type"))
        assertTrue(local.has("tag"))
        assertFalse(local.has("address"))
    }

    @Test
    fun dns_rulesReferenceExistingServerTags() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)
        val root = JSONObject(jsonStr)

        val dns = root.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        val serverTags = (0 until servers.length()).map { servers.getJSONObject(it).getString("tag") }.toSet()

        if (dns.has("rules")) {
            val rules = dns.getJSONArray("rules")
            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                if (rule.has("server")) {
                    val serverTag = rule.getString("server")
                    assertTrue(
                        "DNS rule[$i] references server '$serverTag' which must exist in dns.servers tags",
                        serverTags.contains(serverTag)
                    )
                }
            }
        }
    }

    @Test
    fun dns_finalSelection_referencesExistingServer() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)
        val root = JSONObject(jsonStr)

        val dns = root.getJSONObject("dns")
        assertTrue("dns must specify a final default server", dns.has("final"))
        val finalTag = dns.getString("final")

        val servers = dns.getJSONArray("servers")
        val serverTags = (0 until servers.length()).map { servers.getJSONObject(it).getString("tag") }.toSet()
        assertTrue(
            "dns.final tag '$finalTag' must exist in dns.servers: $serverTags",
            serverTags.contains(finalTag)
        )
    }

    @Test
    fun dns_noLegacyDnsFieldsOrFormatsRemain() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)

        val root = JSONObject(jsonStr)
        val dns = root.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")

        for (i in 0 until servers.length()) {
            val s = servers.getJSONObject(i)
            assertFalse("Legacy 'address' must not exist in servers[$i]", s.has("address"))
            assertFalse("Legacy 'address_resolver' must not exist in servers[$i]", s.has("address_resolver"))
            assertFalse("Legacy 'independent_cache' must not exist in servers[$i]", s.has("independent_cache"))
            assertTrue("Modern 'type' must exist in servers[$i]", s.has("type"))
            assertTrue("Modern 'tag' must exist in servers[$i]", s.has("tag"))
            assertTrue("Modern 'server' must exist in servers[$i]", s.has("server"))
        }

        // Validate via SingBoxAdapter.checkConfig
        val adapter = com.example.engine.SingBoxAdapter(allowSimulationForTesting = true)
        val validResult = adapter.checkConfig(jsonStr)
        assertTrue("Modern configuration must pass checkConfig: ${validResult.exceptionOrNull()?.message}", validResult.isSuccess)

        // Construct a legacy config to verify checkConfig strictly rejects it
        val legacyJson = JSONObject(jsonStr)
        val legacyDns = legacyJson.getJSONObject("dns")
        val legacyServers = JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "legacy-dns")
                put("address", "1.1.1.1")
                put("detour", "wg-out")
            })
        }
        legacyDns.put("servers", legacyServers)

        val legacyResult = adapter.checkConfig(legacyJson.toString())
        assertTrue("Legacy configuration with 'address' must be rejected", legacyResult.isFailure)
        val errMsg = legacyResult.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "Error message must mention deprecated legacy DNS server formats: $errMsg",
            errMsg.contains("legacy DNS server formats are deprecated in sing-box 1.12.0 and removed in sing-box 1.14.0")
        )
    }

    @Test
    fun dns_nativeLibboxCheckConfig_integrationTest() {
        val conf = WireGuardConfigParser.createDefaultTestConfig()
        val profile = WireGuardConfigParser.parse(conf).getOrThrow()
        val jsonStr = WireGuardConfigParser.toSingBoxJson(profile)

        val isNative = com.example.engine.SingBoxAdapter.isNativeAvailable()
        if (isNative) {
            val nativeAdapter = com.example.engine.SingBoxAdapter(allowSimulationForTesting = false)
            val result = nativeAdapter.checkConfig(jsonStr)
            assertTrue("Native Libbox.checkConfig must validate the modern JSON without error", result.isSuccess)
        } else {
            // JVM-only test runner: native library is not on host classpath
            println("[TEST INFO] Host JVM test runner has no ARM64 native libbox.so. Native Libbox.checkConfig skipped on JVM as expected.")
            val simulationAdapter = com.example.engine.SingBoxAdapter(allowSimulationForTesting = true)
            val result = simulationAdapter.checkConfig(jsonStr)
            assertTrue("Simulation schema check must pass on JVM", result.isSuccess)
        }
    }
}
