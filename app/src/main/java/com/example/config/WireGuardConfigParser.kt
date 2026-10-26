package com.example.config

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import org.json.JSONArray
import org.json.JSONObject

data class WireGuardProfile(
    val name: String,
    val privateKey: String,
    val address: String,
    val dns: String = "1.1.1.1",
    val mtu: Int = 1420,
    val peerPublicKey: String,
    val presharedKey: String? = null,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: List<String> = listOf("0.0.0.0/0")
) {
    fun toRedactedSummary(): String {
        return "Profile '$name': Endpoint=$endpointHost:$endpointPort, Address=$address, MTU=$mtu, DNS=$dns, AllowedIPs=${allowedIps.joinToString(",")}, Keys=[REDACTED]"
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
            "Tunnel IP (Address)" to address,
            "DNS Server" to dns,
            "Tunnel MTU" to mtu.toString(),
            "Allowed IPs" to allowedIps.joinToString(", "),
            "Peer Public Key" to pubKeySnippet,
            "Private Key" to "[REDACTED - 44 chars validated]",
            "Preshared Key" to if (presharedKey != null) "[REDACTED - 44 chars validated]" else "None"
        )
    }
}

object WireGuardConfigParser {

    private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/=]{44}$")

    fun parse(confContent: String, profileName: String = "Default Profile"): Result<WireGuardProfile> {
        val lines = confContent.lines()

        var currentSection = ""
        var privateKey: String? = null
        var address: String? = null
        var dns: String = "1.1.1.1"
        var mtu: Int = 1420
        var peerPublicKey: String? = null
        var presharedKey: String? = null
        var endpoint: String? = null
        val allowedIps = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue

            if (line.startsWith("[") && line.contains("]")) {
                val endIdx = line.indexOf("]")
                currentSection = line.substring(1, endIdx).trim().lowercase()
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
                        "address" -> address = rawValue.split(",").firstOrNull()?.trim()
                        "dns" -> dns = rawValue.split(",").firstOrNull()?.trim() ?: "1.1.1.1"
                        "mtu" -> mtu = rawValue.toIntOrNull() ?: 1420
                    }
                }
                "peer" -> {
                    when (key) {
                        "publickey" -> peerPublicKey = rawValue
                        "presharedkey" -> presharedKey = rawValue
                        "endpoint" -> endpoint = rawValue
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

        if (address.isNullOrBlank()) {
            val err = "Missing or empty Address in [Interface]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        // Normalize address CIDR notation (e.g. 10.0.0.2 -> 10.0.0.2/32)
        val normalizedAddress = if (!address.contains("/")) {
            if (address.contains(":")) "$address/128" else "$address/32"
        } else {
            address
        }

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
            address = normalizedAddress,
            dns = dns,
            mtu = mtu,
            peerPublicKey = peerPublicKey,
            presharedKey = presharedKey,
            endpointHost = host,
            endpointPort = port,
            allowedIps = finalAllowedIps
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
     * Converts a validated WireGuardProfile to sing-box v1.11 compatible JSON.
     * Uses "mixed" userspace stack on Android so that raw packets from openTun
     * are processed by sing-box without requiring root system netlink.
     */
    fun toSingBoxJson(profile: WireGuardProfile): String {
        val root = JSONObject()

        // Log configuration
        val logObj = JSONObject()
        logObj.put("disabled", false)
        logObj.put("level", "info")
        logObj.put("timestamp", true)
        root.put("log", logObj)

        // DNS configuration
        val dnsObj = JSONObject()
        val serversArray = JSONArray()
        val dnsServer = JSONObject()
        dnsServer.put("tag", "dns-remote")
        dnsServer.put("address", profile.dns)
        serversArray.put(dnsServer)
        dnsObj.put("servers", serversArray)
        dnsObj.put("strategy", "prefer_ipv4")
        root.put("dns", dnsObj)

        // Inbound: TUN interface with "mixed" stack (official sing-box Android userspace stack)
        val inboundsArray = JSONArray()
        val tunInbound = JSONObject()
        tunInbound.put("type", "tun")
        tunInbound.put("tag", "tun-in")
        tunInbound.put("interface_name", "vianshield-tun")
        val addresses = JSONArray()
        addresses.put(profile.address)
        // v1.11 unified address schema
        tunInbound.put("address", addresses)
        // v1.10 backward compatibility
        tunInbound.put("inet4_address", addresses)
        tunInbound.put("mtu", profile.mtu)
        tunInbound.put("auto_route", true)
        tunInbound.put("strict_route", true)
        tunInbound.put("stack", "mixed")
        tunInbound.put("sniff", true)
        tunInbound.put("sniff_override_destination", false)
        inboundsArray.put(tunInbound)
        root.put("inbounds", inboundsArray)

        // Outbound: WireGuard
        val outboundsArray = JSONArray()
        val wgOutbound = JSONObject()
        wgOutbound.put("type", "wireguard")
        wgOutbound.put("tag", "wg-out")
        wgOutbound.put("server", profile.endpointHost)
        wgOutbound.put("server_port", profile.endpointPort)
        val localAddresses = JSONArray()
        localAddresses.put(profile.address)
        wgOutbound.put("local_address", localAddresses)
        wgOutbound.put("private_key", profile.privateKey)
        wgOutbound.put("peer_public_key", profile.peerPublicKey)
        if (!profile.presharedKey.isNullOrBlank()) {
            wgOutbound.put("pre_shared_key", profile.presharedKey)
        }
        wgOutbound.put("mtu", profile.mtu)
        outboundsArray.put(wgOutbound)

        // Direct outbound fallback
        val directOutbound = JSONObject()
        directOutbound.put("type", "direct")
        directOutbound.put("tag", "direct")
        outboundsArray.put(directOutbound)

        root.put("outbounds", outboundsArray)

        // Route section: auto-detect interface to protect outbound sockets & route default traffic
        val routeObj = JSONObject()
        routeObj.put("auto_detect_interface", true)
        routeObj.put("final", "wg-out")
        val rulesArray = JSONArray()
        val defaultRule = JSONObject()
        defaultRule.put("outbound", "wg-out")
        rulesArray.put(defaultRule)
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
            MTU = 1420

            [Peer]
            # Server Public Key (32 bytes Base64)
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
    }
}
