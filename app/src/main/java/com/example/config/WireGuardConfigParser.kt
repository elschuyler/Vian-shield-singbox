package com.example.config

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import org.json.JSONArray
import org.json.JSONObject

data class WireGuardProfile(
    val name: String,
    val privateKey: String,
    val address: String,
    val addresses: List<String> = listOf(address),
    val dns: String = "1.1.1.1",
    val dnsServers: List<String> = listOf(dns),
    val mtu: Int = WireGuardConfigParser.DEFAULT_MTU,
    val peerPublicKey: String,
    val presharedKey: String? = null,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: List<String> = listOf("0.0.0.0/0"),
    val persistentKeepalive: Int? = null
) {
    fun toRedactedSummary(): String {
        return "Profile '$name': Endpoint=$endpointHost:$endpointPort, Addresses=${addresses.joinToString(",")}, MTU=$mtu, DNS=${dnsServers.joinToString(",")}, Keepalive=${persistentKeepalive ?: "none"}, AllowedIPs=${allowedIps.joinToString(",")}, Keys=[REDACTED]"
    }

    /**
     * Produces safe, non-secret diagnostic map for UI preview before import confirmation.
     */
    fun toDisplaySummary(): Map<String, String> {
        val pubKeySnippet = if (peerPublicKey.length >= 10) {
            "${peerPublicKey.take(6)}...${peerPublicKey.takeLast(6)}"
        } else {
            "[REDACTED]"
        }
        return linkedMapOf(
            "Profile Name" to name,
            "Server Endpoint" to "$endpointHost:$endpointPort",
            "Tunnel IP (Address)" to addresses.joinToString(", "),
            "DNS Servers" to dnsServers.joinToString(", "),
            "Tunnel MTU" to mtu.toString(),
            "Persistent Keepalive" to (persistentKeepalive?.let { "${it}s" } ?: "Disabled"),
            "Allowed IPs" to allowedIps.joinToString(", "),
            "Peer Public Key" to pubKeySnippet,
            "Private Key" to "[REDACTED - 44 chars validated]",
            "Preshared Key" to if (presharedKey != null) "[REDACTED - 44 chars validated]" else "None"
        )
    }
}

object WireGuardConfigParser {

    const val DEFAULT_MTU = 1280
    const val MIN_MTU = 1280
    const val MAX_MTU = 1420

    private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/=]{44}$")

    fun parse(confContent: String, profileName: String = "Default Profile"): Result<WireGuardProfile> {
        val lines = confContent.lines()

        var currentSection = ""
        var peerCount = 0
        var privateKey: String? = null
        val rawAddresses = mutableListOf<String>()
        val rawDnsServers = mutableListOf<String>()
        var mtu: Int = DEFAULT_MTU
        var peerPublicKey: String? = null
        var presharedKey: String? = null
        var endpoint: String? = null
        var persistentKeepalive: Int? = null
        val allowedIps = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue

            if (line.startsWith("[") && line.contains("]")) {
                val endIdx = line.indexOf("]")
                currentSection = line.substring(1, endIdx).trim().lowercase()
                if (currentSection == "peer") {
                    peerCount++
                    if (peerCount > 1) {
                        val err = "Multiple [Peer] sections are not supported in single-tunnel mode (found $peerCount peers)"
                        OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
                        return Result.failure(IllegalArgumentException(err))
                    }
                } else if (currentSection != "interface") {
                    val err = "Unsupported section '[$currentSection]' in WireGuard configuration"
                    OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
                    return Result.failure(IllegalArgumentException(err))
                }
                continue
            }

            val parts = line.split("=", limit = 2)
            if (parts.size != 2) continue
            val key = parts[0].trim().lowercase()
            // Strip inline comments starting with '#' or ';'
            val rawValue = parts[1].split(Regex("[#;]"), limit = 2)[0].trim()
            if (rawValue.isEmpty()) continue

            when (currentSection) {
                "interface" -> {
                    when (key) {
                        "privatekey" -> privateKey = rawValue
                        "address" -> {
                            val splitAddresses = rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            rawAddresses.addAll(splitAddresses)
                        }
                        "dns" -> {
                            val splitDns = rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            rawDnsServers.addAll(splitDns)
                        }
                        "mtu" -> {
                            val parsed = rawValue.toIntOrNull()
                            mtu = if (parsed != null) parsed.coerceIn(MIN_MTU, MAX_MTU) else DEFAULT_MTU
                        }
                    }
                }
                "peer" -> {
                    when (key) {
                        "publickey" -> peerPublicKey = rawValue
                        "presharedkey" -> presharedKey = rawValue
                        "endpoint" -> endpoint = rawValue
                        "persistentkeepalive" -> persistentKeepalive = rawValue.toIntOrNull()
                        "allowedips" -> allowedIps.addAll(rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    }
                }
            }
        }

        // Validation boundary
        if (privateKey.isNullOrBlank()) {
            val err = "Missing or empty PrivateKey in [Interface]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (!isValidBase64Key(privateKey)) {
            val err = "Invalid PrivateKey format. Must be standard 44-character Base64 key."
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (rawAddresses.isEmpty()) {
            val err = "Missing or empty Address in [Interface]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        // Normalize address CIDR notation (e.g. 10.0.0.2 -> 10.0.0.2/32, fd00::2 -> fd00::2/128)
        val normalizedAddresses = rawAddresses.map { addr ->
            if (!addr.contains("/")) {
                if (addr.contains(":")) "$addr/128" else "$addr/32"
            } else {
                addr
            }
        }
        val primaryAddress = normalizedAddresses.first()

        val finalDnsServers = if (rawDnsServers.isEmpty()) listOf("1.1.1.1") else rawDnsServers
        val primaryDns = finalDnsServers.first()

        if (peerPublicKey.isNullOrBlank()) {
            val err = "Missing or empty PublicKey in [Peer]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (!isValidBase64Key(peerPublicKey)) {
            val err = "Invalid PublicKey format in [Peer]. Must be standard 44-character Base64 key."
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (!presharedKey.isNullOrBlank() && !isValidBase64Key(presharedKey)) {
            val err = "Invalid PresharedKey format in [Peer]. Must be standard 44-character Base64 key."
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (endpoint.isNullOrBlank()) {
            val err = "Missing or empty Endpoint in [Peer]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val lastColon = endpoint.lastIndexOf(':')
        if (lastColon <= 0 || lastColon == endpoint.length - 1) {
            val err = "Invalid Endpoint format '$endpoint'. Expected host:port"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val host = endpoint.substring(0, lastColon).trim().removePrefix("[").removeSuffix("]")
        val portStr = endpoint.substring(lastColon + 1).trim()
        val port = portStr.toIntOrNull()
        if (port == null || port !in 1..65535) {
            val err = "Invalid Endpoint port '$portStr' in '$endpoint'"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val finalAllowedIps = if (allowedIps.isEmpty()) listOf("0.0.0.0/0") else allowedIps

        val profile = WireGuardProfile(
            name = profileName,
            privateKey = privateKey,
            address = primaryAddress,
            addresses = normalizedAddresses,
            dns = primaryDns,
            dnsServers = finalDnsServers,
            mtu = mtu,
            peerPublicKey = peerPublicKey,
            presharedKey = presharedKey,
            endpointHost = host,
            endpointPort = port,
            allowedIps = finalAllowedIps,
            persistentKeepalive = persistentKeepalive
        )

        OperationalLogCatcher.info(
            LogCategory.CONFIG_VALIDATION,
            "ConfigParser",
            "WireGuard profile successfully validated: ${profile.toRedactedSummary()}"
        )

        return Result.success(profile)
    }

    fun parseFromInputStream(inputStream: java.io.InputStream, profileName: String = "Imported Profile"): Result<WireGuardProfile> {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            parse(content, profileName)
        } catch (e: Exception) {
            val err = "Failed to read configuration stream: ${e.message}"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            Result.failure(e)
        }
    }

    private fun isValidBase64Key(key: String): Boolean {
        return key.length == 44 && BASE64_PATTERN.matches(key)
    }

    /**
     * Converts a validated WireGuardProfile to sing-box v1.14.4 compatible JSON.
     * Uses "gvisor" userspace stack on Android so that raw IP packets from openTun
     * are translated into TCP/UDP connections by sing-box without requiring root system netlink.
     *
     * In sing-box v1.14.4, directory paths are passed to the engine via Libbox.setup(),
     * completely eliminating the need for the experimental.cache_file workaround.
     */
    fun toSingBoxJson(
        profile: WireGuardProfile,
        @Suppress("UNUSED_PARAMETER") cacheFilePath: String? = null,
        @Suppress("UNUSED_PARAMETER") enableCacheFile: Boolean = false,
        dnsProviderOverride: String? = null
    ): String {
        val root = JSONObject()

        // Log configuration
        val logObj = JSONObject()
        logObj.put("disabled", false)
        logObj.put("level", "info")
        logObj.put("timestamp", true)
        root.put("log", logObj)

        // DNS configuration with detour to WireGuard outbound (sing-box v1.14.4 modern schema)
        val effectiveDns = resolveDnsServers(dnsProviderOverride, profile.dnsServers)
        val dnsObj = JSONObject()
        val serversArray = JSONArray()
        val existingTags = mutableSetOf<String>()

        for ((idx, dns) in effectiveDns.withIndex()) {
            val dnsServer = parseDnsServerObject(dns, detour = "wg-out", tagIndex = idx)
            var tag = dnsServer.getString("tag")
            var suffix = 1
            while (existingTags.contains(tag)) {
                tag = "${tag}_$suffix"
                suffix++
            }
            dnsServer.put("tag", tag)
            existingTags.add(tag)
            serversArray.put(dnsServer)
        }

        if (serversArray.length() == 0) {
            val fallback = JSONObject().apply {
                put("tag", "dns-remote-fallback-0")
                put("type", "udp")
                put("server", "1.1.1.1")
                put("server_port", 53)
                put("detour", "wg-out")
            }
            serversArray.put(fallback)
        }

        dnsObj.put("servers", serversArray)
        dnsObj.put("strategy", "prefer_ipv4")
        val primaryTag = serversArray.getJSONObject(0).getString("tag")
        dnsObj.put("final", primaryTag)
        root.put("dns", dnsObj)

        // Inbound: TUN interface with "gvisor" stack (official sing-box Android userspace stack)
        val inboundsArray = JSONArray()
        val tunInbound = JSONObject()
        tunInbound.put("type", "tun")
        tunInbound.put("tag", "tun-in")
        tunInbound.put("interface_name", "vianshield-tun")
        val addresses = JSONArray()
        for (addr in profile.addresses) {
            addresses.put(addr)
        }
        tunInbound.put("address", addresses)
        tunInbound.put("mtu", profile.mtu)
        tunInbound.put("auto_route", true)
        tunInbound.put("strict_route", true)
        tunInbound.put("stack", "gvisor")
        tunInbound.put("sniff", true)
        tunInbound.put("sniff_override_destination", false)
        inboundsArray.put(tunInbound)
        root.put("inbounds", inboundsArray)

        // Endpoints: WireGuard endpoint (sing-box v1.14.4 schema)
        // WireGuard is configured under root "endpoints" with a "peers" array,
        // natively supporting "persistent_keepalive_interval" per peer.
        val endpointsArray = JSONArray()
        val wgEndpoint = JSONObject()
        wgEndpoint.put("type", "wireguard")
        wgEndpoint.put("tag", "wg-out")
        val localAddresses = JSONArray()
        for (addr in profile.addresses) {
            localAddresses.put(addr)
        }
        wgEndpoint.put("address", localAddresses)
        wgEndpoint.put("private_key", profile.privateKey)
        wgEndpoint.put("mtu", profile.mtu)

        val peersArray = JSONArray()
        val peerObj = JSONObject()
        peerObj.put("address", profile.endpointHost)
        peerObj.put("port", profile.endpointPort)
        peerObj.put("public_key", profile.peerPublicKey)
        if (!profile.presharedKey.isNullOrBlank()) {
            peerObj.put("pre_shared_key", profile.presharedKey)
        }
        val allowedIpsArray = JSONArray()
        for (ip in profile.allowedIps) {
            allowedIpsArray.put(ip)
        }
        peerObj.put("allowed_ips", allowedIpsArray)
        if (profile.persistentKeepalive != null && profile.persistentKeepalive > 0) {
            peerObj.put("persistent_keepalive_interval", profile.persistentKeepalive)
        }
        peersArray.put(peerObj)
        wgEndpoint.put("peers", peersArray)
        endpointsArray.put(wgEndpoint)
        root.put("endpoints", endpointsArray)

        // Outbound: DNS + Direct (route.final resolves "wg-out" from endpoint manager)
        val outboundsArray = JSONArray()

        // DNS outbound: hijacks port 53 / DNS queries from tun and passes to dns router
        val dnsOutbound = JSONObject()
        dnsOutbound.put("type", "dns")
        dnsOutbound.put("tag", "dns-out")
        outboundsArray.put(dnsOutbound)

        // Direct outbound fallback
        val directOutbound = JSONObject()
        directOutbound.put("type", "direct")
        directOutbound.put("tag", "direct")
        outboundsArray.put(directOutbound)

        root.put("outbounds", outboundsArray)

        // Route section: auto-detect interface to protect outbound sockets & route rules
        val routeObj = JSONObject()
        routeObj.put("auto_detect_interface", true)
        routeObj.put("final", "wg-out")
        val rulesArray = JSONArray()

        // Rule 1: hijack DNS queries and send to dns-out
        val dnsRule = JSONObject()
        dnsRule.put("protocol", "dns")
        dnsRule.put("outbound", "dns-out")
        rulesArray.put(dnsRule)

        // Rule 2: route all TUN packets to wg-out
        val tunRule = JSONObject()
        val tunInbounds = JSONArray()
        tunInbounds.put("tun-in")
        tunRule.put("inbound", tunInbounds)
        tunRule.put("outbound", "wg-out")
        rulesArray.put(tunRule)

        routeObj.put("rules", rulesArray)
        root.put("route", routeObj)

        return root.toString(2)
    }

    /**
     * Default verified minimal testing profile definition (documentation only, not connected blindly).
     * Uses valid 44-character Base64 keys.
     */
    fun createDefaultTestConfig(): String {
        return """
            [Interface]
            # Client WireGuard Private Key (32 bytes Base64)
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 172.19.0.2/30
            DNS = 1.1.1.1
            MTU = 1280

            [Peer]
            # Server Public Key (32 bytes Base64)
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
    }

    /**
     * Resolves DNS provider name (e.g. from UI selector) to a list of server addresses or URLs.
     */
    fun resolveDnsServers(providerName: String?, profileDns: List<String>): List<String> {
        val trimmed = providerName?.trim()
        if (trimmed.isNullOrEmpty() || trimmed.contains("Profile Default", ignoreCase = true)) {
            return profileDns.ifEmpty { listOf("1.1.1.1") }
        }
        return when {
            trimmed.contains("Cloudflare", ignoreCase = true) -> listOf("1.1.1.1", "1.0.0.1")
            trimmed.contains("Google", ignoreCase = true) -> listOf("8.8.8.8", "8.8.4.4")
            trimmed.contains("Quad9", ignoreCase = true) -> listOf("9.9.9.9", "149.112.112.112")
            trimmed.contains("AdGuard", ignoreCase = true) -> listOf("94.140.14.14", "94.140.15.15")
            else -> listOf(trimmed)
        }
    }

    /**
     * Extracts a plain IP address (v4 or v6) from a DNS address string,
     * stripping schemes and ports. Returns null if not a parseable IP.
     */
    fun extractPlainIp(dnsString: String): String? {
        val trimmed = dnsString.trim()
            .removePrefix("udp://")
            .removePrefix("tcp://")
            .removePrefix("tls://")
            .removePrefix("https://")

        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf("]")
            if (endBracket > 1) {
                return trimmed.substring(1, endBracket)
            }
        }
        if (trimmed.count { it == '.' } == 3 && trimmed.contains(":")) {
            return trimmed.substringBefore(":")
        }
        val cleanHost = trimmed.substringBefore("/").substringBefore(":")
        return if (cleanHost.isNotEmpty() && !cleanHost.matches(Regex(".*[a-zA-Z].*"))) cleanHost else null
    }

    /**
     * Parses a DNS string (plain IPv4/IPv6, UDP, TCP, DoT, DoH) into a modern
     * typed sing-box 1.14 DNS server JSON object.
     *
     * In sing-box 1.14:
     * - Legacy "address" field is completely removed.
     * - Every server MUST declare "type", "tag", "server", "server_port", and "detour".
     */
    fun parseDnsServerObject(rawDns: String, detour: String = "wg-out", tagIndex: Int = 0): JSONObject {
        val trimmed = rawDns.trim()
        val dnsServer = JSONObject()

        when {
            trimmed.startsWith("https://", ignoreCase = true) -> {
                dnsServer.put("type", "https")
                val withoutScheme = trimmed.substring(8)
                val pathIdx = withoutScheme.indexOf("/")
                val hostPort = if (pathIdx != -1) withoutScheme.substring(0, pathIdx) else withoutScheme
                val rawPath = if (pathIdx != -1) withoutScheme.substring(pathIdx) else "/dns-query"
                val path = if (rawPath.isBlank()) "/dns-query" else rawPath

                val (host, port) = parseHostAndPort(hostPort, 443)
                val sanitizedHost = sanitizeTag(host)
                dnsServer.put("tag", "dns-https-$sanitizedHost-$tagIndex")
                dnsServer.put("server", host)
                dnsServer.put("server_port", port)
                dnsServer.put("path", path)
                if (host.matches(Regex(".*[a-zA-Z].*"))) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", host)
                    }
                    dnsServer.put("tls", tlsObj)
                }
            }
            trimmed.startsWith("tls://", ignoreCase = true) -> {
                dnsServer.put("type", "tls")
                val withoutScheme = trimmed.substring(6)
                val hostPort = withoutScheme.substringBefore("/")
                val (host, port) = parseHostAndPort(hostPort, 853)
                val sanitizedHost = sanitizeTag(host)
                dnsServer.put("tag", "dns-tls-$sanitizedHost-$tagIndex")
                dnsServer.put("server", host)
                dnsServer.put("server_port", port)
                if (host.matches(Regex(".*[a-zA-Z].*"))) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", host)
                    }
                    dnsServer.put("tls", tlsObj)
                }
            }
            trimmed.startsWith("tcp://", ignoreCase = true) -> {
                dnsServer.put("type", "tcp")
                val withoutScheme = trimmed.substring(6)
                val (host, port) = parseHostAndPort(withoutScheme, 53)
                val sanitizedHost = sanitizeTag(host)
                dnsServer.put("tag", "dns-tcp-$sanitizedHost-$tagIndex")
                dnsServer.put("server", host)
                dnsServer.put("server_port", port)
            }
            trimmed.startsWith("udp://", ignoreCase = true) -> {
                dnsServer.put("type", "udp")
                val withoutScheme = trimmed.substring(6)
                val (host, port) = parseHostAndPort(withoutScheme, 53)
                val sanitizedHost = sanitizeTag(host)
                dnsServer.put("tag", "dns-udp-$sanitizedHost-$tagIndex")
                dnsServer.put("server", host)
                dnsServer.put("server_port", port)
            }
            trimmed.equals("local", ignoreCase = true) -> {
                dnsServer.put("type", "local")
                dnsServer.put("tag", "dns-local-$tagIndex")
                return dnsServer
            }
            else -> {
                // Default plain UDP resolver (e.g. 1.1.1.1, [2606:4700::1111]:53)
                dnsServer.put("type", "udp")
                val (host, port) = parseHostAndPort(trimmed, 53)
                val sanitizedHost = sanitizeTag(host)
                dnsServer.put("tag", "dns-remote-$sanitizedHost-$tagIndex")
                dnsServer.put("server", host)
                dnsServer.put("server_port", port)
            }
        }

        dnsServer.put("detour", detour)
        return dnsServer
    }

    private fun parseHostAndPort(input: String, defaultPort: Int): Pair<String, Int> {
        val str = input.trim()
        if (str.startsWith("[")) {
            val endBracket = str.indexOf("]")
            if (endBracket != -1) {
                val ip = str.substring(1, endBracket)
                val remaining = str.substring(endBracket + 1)
                val port = if (remaining.startsWith(":")) {
                    remaining.substring(1).toIntOrNull() ?: defaultPort
                } else {
                    defaultPort
                }
                return Pair(ip, port)
            }
        }
        if (str.count { it == ':' } == 1) {
            val parts = str.split(":")
            val port = parts[1].toIntOrNull() ?: defaultPort
            return Pair(parts[0], port)
        }
        return Pair(str, defaultPort)
    }

    private fun sanitizeTag(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifEmpty { "server" }
    }
}
