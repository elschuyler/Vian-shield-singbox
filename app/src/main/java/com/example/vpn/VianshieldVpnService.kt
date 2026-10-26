package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.config.WireGuardConfigParser
import com.example.config.WireGuardProfile
import com.example.engine.SingBoxAdapter
import com.example.engine.SingBoxPlatformCallback
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VianshieldVpnService : VpnService(), SingBoxPlatformCallback {

    companion object {
        const val ACTION_START_VPN = "com.example.vpn.ACTION_START_VPN"
        const val ACTION_STOP_VPN = "com.example.vpn.ACTION_STOP_VPN"
        const val EXTRA_CONFIG_STRING = "com.example.vpn.EXTRA_CONFIG_STRING"

        private const val NOTIFICATION_CHANNEL_ID = "vianshield_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _vpnState = MutableStateFlow<VpnState>(VpnState.Stopped)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        fun startIntent(context: Context, configString: String): Intent {
            return Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_START_VPN
                putExtra(EXTRA_CONFIG_STRING, configString)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_STOP_VPN
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val singBoxEngine = SingBoxAdapter()
    private var activeProfile: WireGuardProfile? = null
    private var activeTunFd: Int = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VianshieldVpnService created"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_VPN -> {
                val configStr = intent.getStringExtra(EXTRA_CONFIG_STRING)
                    ?: WireGuardConfigParser.createDefaultTestConfig()
                handleStartVpn(configStr)
            }
            ACTION_STOP_VPN -> {
                handleStopVpn()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartVpn(rawConfig: String) {
        if (_vpnState.value is VpnState.Connected || _vpnState.value is VpnState.Starting) {
            OperationalLogCatcher.warn(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "Start requested while VPN is already starting/connected (duplicate start suppressed)"
            )
            return
        }

        _vpnState.value = VpnState.Starting("Validating configuration")
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "Initiating VPN start sequence"
        )

        serviceScope.launch {
            // 1. Parse and validate WireGuard configuration
            val parseResult = WireGuardConfigParser.parse(rawConfig)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()?.message ?: "Configuration parse failed"
                OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "VpnService", err)
                _vpnState.value = VpnState.Error("ERR_CONFIG_INVALID", err)
                stopForegroundSafely()
                return@launch
            }

            val profile = parseResult.getOrThrow()
            activeProfile = profile

            // 2. Transform to sing-box v1.11 JSON
            _vpnState.value = VpnState.Starting("Generating sing-box configuration")
            val singBoxJson = WireGuardConfigParser.toSingBoxJson(profile)

            // 3. Verify native binary availability before claiming connection
            if (!singBoxEngine.isNativeBinaryAvailable()) {
                val missingMsg = "sing-box native engine (libbox.aar) has not been compiled yet. Run the GitHub Actions workflow (.github/workflows/build-vianshield.yml) to generate libbox.aar."
                OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "VpnService", missingMsg)
                _vpnState.value = VpnState.Error("ERR_NATIVE_AAR_PENDING", missingMsg)
                stopForegroundSafely()
                return@launch
            }

            // 4. Start foreground notification
            startForeground(NOTIFICATION_ID, buildNotification("Starting Vianshield Tunnel…"))

            // 5. Initialize engine adapter
            _vpnState.value = VpnState.Starting("Initializing sing-box core")
            val initResult = singBoxEngine.initialize(singBoxJson, this@VianshieldVpnService)
            if (initResult.isFailure) {
                val err = initResult.exceptionOrNull()?.message ?: "Engine initialization failed"
                OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "VpnService", err)
                _vpnState.value = VpnState.Error("ERR_ENGINE_INIT", err)
                stopForegroundSafely()
                return@launch
            }

            // 6. Start sing-box engine (engine calls openTun callback during startup)
            _vpnState.value = VpnState.Starting("Starting sing-box routing core")
            val engineStartResult = singBoxEngine.start()
            if (engineStartResult.isFailure) {
                val err = engineStartResult.exceptionOrNull()?.message ?: "Engine start failed"
                OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "VpnService", err)
                _vpnState.value = VpnState.Error("ERR_ENGINE_START", err)
                releaseTunFd()
                stopForegroundSafely()
                return@launch
            }

            // 7. Transition to Connected ONLY after engine and TUN are verified running
            _vpnState.value = VpnState.Connected(
                endpoint = "${profile.endpointHost}:${profile.endpointPort}",
                localAddress = profile.address,
                mtu = profile.mtu
            )

            updateNotification("Connected to ${profile.endpointHost}:${profile.endpointPort}")
            OperationalLogCatcher.info(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "VPN tunnel and sing-box engine successfully running"
            )
        }
    }

    private fun handleStopVpn() {
        if (_vpnState.value is VpnState.Stopped || _vpnState.value is VpnState.Stopping) {
            return
        }

        _vpnState.value = VpnState.Stopping
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "Stopping VPN service and releasing resources"
        )

        serviceScope.launch {
            singBoxEngine.stop()
            releaseTunFd()
            stopForegroundSafely()
            _vpnState.value = VpnState.Stopped
            OperationalLogCatcher.info(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "VPN service stopped cleanly"
            )
            stopSelf()
        }
    }

    // SingBoxPlatformCallback implementations (Bridge to libbox PlatformInterface)

    override fun openTun(options: Any?): Int {
        val profile = activeProfile
        if (profile == null) {
            OperationalLogCatcher.error(LogCategory.TUN_INTERFACE, "VpnService", "openTun requested without active profile")
            return -1
        }

        return try {
            val builder = Builder()
                .setSession("Vianshield VPN")
                .setMtu(profile.mtu)

            val addressParts = profile.address.split("/")
            val ip = addressParts[0]
            val prefix = if (addressParts.size > 1) addressParts[1].toIntOrNull() ?: 32 else 32
            builder.addAddress(ip, prefix)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer(profile.dns)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val pfd: ParcelFileDescriptor? = builder.establish()
            if (pfd == null) {
                OperationalLogCatcher.error(LogCategory.TUN_INTERFACE, "VpnService", "Builder.establish() returned null")
                return -1
            }

            // Detach descriptor to transfer exclusive ownership to Go / sing-box runtime
            val rawFd = pfd.detachFd()
            activeTunFd = rawFd

            OperationalLogCatcher.info(
                LogCategory.TUN_INTERFACE,
                "VpnService",
                "TUN interface established and detached: fd=$rawFd, address=$ip/$prefix, mtu=${profile.mtu}"
            )
            rawFd
        } catch (e: Exception) {
            OperationalLogCatcher.error(LogCategory.TUN_INTERFACE, "VpnService", "Exception in openTun: ${e.message}")
            -1
        }
    }

    override fun autoDetectInterfaceControl(fd: Int): Boolean {
        return try {
            val success = protect(fd)
            if (success) {
                OperationalLogCatcher.info(
                    LogCategory.NETWORK_STATE,
                    "VpnService",
                    "Outbound socket fd=$fd protected from VPN routing loop"
                )
            } else {
                OperationalLogCatcher.warn(
                    LogCategory.NETWORK_STATE,
                    "VpnService",
                    "protect(fd=$fd) returned false"
                )
            }
            success
        } catch (e: Exception) {
            OperationalLogCatcher.error(
                LogCategory.NETWORK_STATE,
                "VpnService",
                "Exception protecting socket fd=$fd: ${e.message}"
            )
            false
        }
    }

    override fun onLog(level: String, message: String) {
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxCore", "[$level] $message")
    }

    override fun onError(code: String, message: String) {
        OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxCore", "Error ($code): $message")
        _vpnState.value = VpnState.Error(code, message)
    }

    private fun releaseTunFd() {
        if (activeTunFd >= 0) {
            try {
                // When detached, Go closes the file descriptor upon BoxService.close().
                // If Go did not close it, we close the descriptor safely.
                ParcelFileDescriptor.adoptFd(activeTunFd).close()
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "VpnService", "Active TUN descriptor closed: fd=$activeTunFd")
            } catch (_: Exception) {}
            activeTunFd = -1
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_vpn_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, VianshieldVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.btn_stop_vpn), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun stopForegroundSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseTunFd()
        singBoxEngine.stop()
        _vpnState.value = VpnState.Stopped
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VianshieldVpnService destroyed"
        )
        super.onDestroy()
    }

    override fun onRevoke() {
        OperationalLogCatcher.warn(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VPN permission revoked by system or another VPN application started"
        )
        handleStopVpn()
        super.onRevoke()
    }
}
