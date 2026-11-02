// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.bg

import android.annotation.SuppressLint
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings as AndroidSettings
import android.system.OsConstants
import android.util.Log
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import io.nekohasekai.sfa.Application
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

interface PlatformInterfaceWrapper : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun openTun(options: TunOptions): Int {
        error("android: tun inbound requires VPN service")
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        try {
            val owner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uid = Application.connectivity.getConnectionOwnerUid(
                    ipProtocol,
                    InetSocketAddress(sourceAddress, sourcePort),
                    InetSocketAddress(destinationAddress, destinationPort),
                )
                if (uid == Process.INVALID_UID) error("android: connection owner not found")
                ConnectionOwner().also { it.userId = uid }
            } else {
                Libbox.findConnectionOwner(ipProtocol, sourceAddress, sourcePort, destinationAddress, destinationPort)
            }
            val packages = Application.packageManager.getPackagesForUid(owner.userId)
            owner.userName = packages?.firstOrNull() ?: ""
            owner.setAndroidPackageNames(StringArray(packages?.toList()?.iterator() ?: emptyList<String>().iterator()))
            return owner
        } catch (e: Exception) {
            Log.e("PlatformInterface", "findConnectionOwner", e)
            throw e
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val networks = Application.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibboxNetworkInterface()
            val linkProperties = Application.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities = Application.connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface = networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer = StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
            boxInterface.gateway = StringArray(
                linkProperties.routes
                    .filter { it.destination.prefixLength == 0 }
                    .mapNotNull { it.gateway }
                    .filterNot { it.isAnyLocalAddress }
                    .mapNotNull { it.hostAddress }
                    .iterator()
            )
            boxInterface.type = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            boxInterface.index = networkInterface.index
            runCatching {
                boxInterface.mtu = networkInterface.mtu
            }.onFailure {
                Log.e("PlatformInterface", "failed to get mtu for interface ${boxInterface.name}", it)
            }
            boxInterface.addresses = StringArray(
                networkInterface.interfaceAddresses.mapTo(mutableListOf()) { it.toPrefix() }.iterator()
            )
            boxInterface.type = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 3
                else -> 0
            }
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? {
        @Suppress("DEPRECATION")
        val wifiInfo = Application.wifiManager.connectionInfo ?: return null
        var ssid = wifiInfo.ssid
        if (ssid == "<unknown ssid>") {
            return WIFIState().apply {
                this.ssid = ""
                this.bssid = ""
            }
        }
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return WIFIState().apply {
            this.ssid = ssid
            this.bssid = wifiInfo.bssid ?: ""
        }
    }

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        error("platform shell not supported in standalone vpn mode")
    }

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int
    ): ShellSession {
        error("platform shell not supported in standalone vpn mode")
    }

    override fun readSystemSSHHostKey(): String = error("not supported")

    override fun lookupSFTPServer(): String = error("not supported")

    override fun tailscaleHostname(): String = AndroidSettings.Global.getString(
        Application.application.contentResolver,
        AndroidSettings.Global.DEVICE_NAME
    )?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession {
        error("bridge not supported in standalone vpn mode")
    }

    override fun lookupUser(username: String?): PlatformUser = PlatformUser()

    override fun registerMyInterface(name: String?) {}

    override fun sendNotification(notification: Notification?) {}

    override fun cancelNotification(identifier: String?, typeID: Int) {}

    class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
        "${address.hostAddress}/$networkPrefixLength"
    }
}
