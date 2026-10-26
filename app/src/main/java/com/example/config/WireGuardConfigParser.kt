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
}

object WireGuardConfigParser {

    fun parse(confContent: String, profileName: String = "Default Profile"): Result<WireGuardProfile> {
        val lines = confContent.lines().map { it.trim() }

        var currentSection = ""
        var privateKey: String? = null
        var address: String? = null
        var dns: String = "1.1.1.1"
        var mtu: Int = 1420
        var peerPublicKey: String? = null
        var presharedKey: String? = null
        var endpoint: String? = null
        val allowedIps = mutableListOf<String>()

        for (line in lines) {
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }

            val parts = line.split("=", limit = 2)
            if (parts.size != 2) continue
            val key = parts[0].trim().lowercase()
            val value = parts[1].trim()

            when (currentSection) {
                "interface" -> {
                    when (key) {
                        "privatekey" -> privateKey = value
                        "address" -> address = value.split(",").firstOrNull()?.trim()
                        "dns" -> dns = value.split(",").firstOrNull()?.trim() ?: "1.1.1.1"
                        "mtu" -> mtu = value.toIntOrNull() ?: 1420
                    }
                }
                "peer" -> {
                    when (key) {
                        "publickey" -> peerPublicKey = value
                        "presharedkey" -> presharedKey = value
                        "endpoint" -> endpoint = value
                        "allowedips" -> allowedIps.addAll(value.split(",").map { it.trim() })
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

        if (address.isNullOrBlank()) {
            val err = "Missing or empty Address in [Interface]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (peerPublicKey.isNullOrBlank()) {
            val err = "Missing or empty PublicKey in [Peer]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        if (endpoint.isNullOrBlank()) {
            val err = "Missing or empty Endpoint in [Peer]"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val endpointParts = endpoint.split(":")
        if (endpointParts.size != 2) {
            val err = "Invalid Endpoint format '$endpoint'. Expected host:port"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val host = endpointParts[0].trim()
        val port = endpointParts[1].trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            val err = "Invalid Endpoint port in '$endpoint'"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "ConfigParser", err)
            return Result.failure(IllegalArgumentException(err))
        }

        val finalAllowedIps = if (allowedIps.isEmpty()) listOf("0.0.0.0/0") else allowedIps

        val profile = WireGuardProfile(
            name = profileName,
            privateKey = privateKey,
            address = address,
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

    /**
     * Converts a validated WireGuardProfile to sing-box v1.11 compatible JSON.
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
        root.put("dns", dnsObj)

        // Inbound: TUN interface
        val inboundsArray = JSONArray()
        val tunInbound = JSONObject()
        tunInbound.put("type", "tun")
        tunInbound.put("tag", "tun-in")
        tunInbound.put("interface_name", "vianshield-tun")
        val addresses = JSONArray()
        addresses.put(profile.address)
        tunInbound.put("inet4_address", addresses)
        tunInbound.put("mtu", profile.mtu)
        tunInbound.put("auto_route", true)
        tunInbound.put("strict_route", true)
        tunInbound.put("stack", "system")
        tunInbound.put("sniff", true)
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

        // Route section: default all traffic through wg-out
        val routeObj = JSONObject()
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
     */
    fun createDefaultTestConfig(): String {
        return """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            Address = 172.19.0.2/30
            DNS = 1.1.1.1
            MTU = 1420

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            Endpoint = 198.51.100.1:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
    }
}
