// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.bg

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.sfa.database.Settings

/**
 * Official sing-box-for-Android (SFA) VpnService implementation.
 */
class VPNService : VpnService(), PlatformInterfaceWrapper {

    companion object {
        private const val TAG = "VPNService"
    }

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return service.onStartCommand()
    }

    override fun onBind(intent: Intent): IBinder? {
        val binder = super.onBind(intent)
        if (binder != null) {
            return binder
        }
        return service.onBind()
    }

    override fun onDestroy() {
        super.onDestroy()
        service.onDestroy()
    }

    override fun onRevoke() {
        service.onRevoke()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder = Builder()
            .setSession("Vianshield")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        if (Settings.allowBypass) {
            builder.allowBypass()
        }

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dnsServerAddress = options.dnsServerAddress
                while (dnsServerAddress.hasNext()) {
                    val dns = dnsServerAddress.next()
                    if (dns.isNotBlank()) {
                        val cleanIp = dns.split("/")[0].split(":")[0]
                        runCatching { builder.addDnsServer(cleanIp) }
                    }
                }
            }

            val inet4RouteAddress = options.inet4RouteAddress
            var hasV4Route = false
            while (inet4RouteAddress.hasNext()) {
                val route = inet4RouteAddress.next()
                builder.addRoute(route.address(), route.prefix())
                hasV4Route = true
            }
            if (!hasV4Route) {
                builder.addRoute("0.0.0.0", 0)
            }

            val inet6RouteAddress = options.inet6RouteAddress
            var hasV6Route = false
            while (inet6RouteAddress.hasNext()) {
                val route = inet6RouteAddress.next()
                builder.addRoute(route.address(), route.prefix())
                hasV6Route = true
            }
            if (!hasV6Route && options.inet6Address.hasNext()) {
                builder.addRoute("::", 0)
            }
        }

        val pfd: ParcelFileDescriptor = builder.establish() ?: error("android: establish VPN returned null")
        service.fileDescriptor = pfd
        Log.i(TAG, "TUN interface established successfully with fd=${pfd.fd}")
        return pfd.detachFd()
    }
}
