package com.example.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Observer
import com.example.config.WireGuardConfigParser
import com.example.config.WireGuardProfile
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import com.example.logging.StartupContextTracker
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VpnLifecycleController(private val context: Context) {

    companion object {
        private val _vpnState = MutableStateFlow<VpnState>(VpnState.Stopped)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        @Volatile
        private var lastConfiguredProfile: WireGuardProfile? = null

        private val statusObserver = Observer<Status> { status ->
            when (status) {
                Status.Stopped -> {
                    if (_vpnState.value !is VpnState.Failed) {
                        _vpnState.value = VpnState.Stopped
                    }
                }
                Status.Starting -> {
                    _vpnState.value = VpnState.Starting("Starting VPN engine...")
                }
                Status.Started -> {
                    val profile = lastConfiguredProfile
                    _vpnState.value = VpnState.Connected(
                        endpoint = profile?.let { "${it.endpointHost}:${it.endpointPort}" } ?: "sing-box core",
                        localAddress = profile?.address ?: "172.19.0.2/30",
                        mtu = profile?.mtu ?: WireGuardConfigParser.DEFAULT_MTU
                    )
                    StartupContextTracker.checkpointSuccess(OperationalLogCatcher.EVENT_VPN_CONNECTED)
                }
                Status.Stopping -> {
                    _vpnState.value = VpnState.Stopping
                }
            }
        }

        init {
            val mainHandler = Handler(Looper.getMainLooper())
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runCatching { BoxService.status.observeForever(statusObserver) }
            } else {
                mainHandler.post {
                    runCatching { BoxService.status.observeForever(statusObserver) }
                }
            }
        }

        fun updateState(state: VpnState) {
            _vpnState.value = state
        }
    }

    fun checkVpnPermission(): Intent? {
        return VpnService.prepare(context)
    }

    fun requestStartVpn(
        configContent: String,
        permissionLauncher: ActivityResultLauncher<Intent>? = null,
        activity: Activity? = null,
        dnsProvider: String? = null
    ) {
        val prepareIntent = checkVpnPermission()
        if (prepareIntent != null) {
            OperationalLogCatcher.info(
                LogCategory.VPN_LIFECYCLE,
                "LifecycleController",
                "[${OperationalLogCatcher.EVENT_VPN_PERMISSION_REQUIRED}] Android VPN permission required. Prompting consent dialog."
            )
            _vpnState.value = VpnState.PermissionRequired
            if (permissionLauncher != null) {
                permissionLauncher.launch(prepareIntent)
            } else {
                activity?.startActivityForResult(prepareIntent, 101)
            }
            return
        }

        // Permission already granted, start service
        startVpnService(configContent, dnsProvider)
    }

    fun startVpnService(configContent: String, dnsProvider: String? = null) {
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "LifecycleController",
            "[${OperationalLogCatcher.EVENT_START_REQUESTED}] STAGE 1: Serializing WireGuard profile to sing-box configuration"
        )
        StartupContextTracker.checkpointAttempt(
            OperationalLogCatcher.EVENT_START_REQUESTED,
            vpnPermissionGranted = true,
            vpnServiceCreated = true
        )

        // 1. Parse and validate WireGuard configuration
        val parseResult = WireGuardConfigParser.parse(configContent)
        if (parseResult.isFailure) {
            val err = parseResult.exceptionOrNull()?.message ?: "Configuration parse failed"
            OperationalLogCatcher.error(
                LogCategory.CONFIG_VALIDATION,
                "LifecycleController",
                "[${OperationalLogCatcher.EVENT_CONFIG_VALIDATION_FAILED}] STAGE 1 FAILED: $err"
            )
            _vpnState.value = VpnState.Failed("ERR_CONFIG_INVALID", err)
            return
        }

        val profile = parseResult.getOrThrow()
        lastConfiguredProfile = profile

        // 2. Generate modern sing-box v1.14+ JSON configuration
        val singBoxJson = WireGuardConfigParser.toSingBoxJson(profile, dnsProviderOverride = dnsProvider)

        // 3. Pre-validate schema with Libbox.checkConfig before starting service (BitProxy patch 0007)
        try {
            Libbox.checkConfig(singBoxJson)
            OperationalLogCatcher.info(
                LogCategory.CONFIG_VALIDATION,
                "LifecycleController",
                "[${OperationalLogCatcher.EVENT_CONFIG_VALIDATION_SUCCESS}] sing-box configuration syntax validated via Libbox.checkConfig"
            )
        } catch (e: Throwable) {
            val err = e.message ?: "Configuration validation failed"
            OperationalLogCatcher.error(
                LogCategory.CONFIG_VALIDATION,
                "LifecycleController",
                "[${OperationalLogCatcher.EVENT_CONFIG_VALIDATION_FAILED}] Pre-start validation error: $err"
            )
            _vpnState.value = VpnState.Failed("ERR_CONFIG_INVALID", err)
            return
        }

        // 4. Save to active config file path in sandbox for BoxService to read
        try {
            val configFile = File(context.filesDir, "active_config.json")
            configFile.writeText(singBoxJson)
            Settings.selectedConfigPath = configFile.absolutePath
            Settings.startedByUser = true
        } catch (e: Throwable) {
            val err = "Failed to write active configuration file: ${e.message}"
            OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "LifecycleController", err)
            _vpnState.value = VpnState.Failed("ERR_STORAGE_WRITE", err)
            return
        }

        // 5. Start official SFA BoxService
        _vpnState.value = VpnState.Starting("Starting official SFA VPN engine")
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "LifecycleController",
            "[${OperationalLogCatcher.EVENT_VPN_SERVICE_START_REQUESTED}] Starting BoxService..."
        )
        try {
            BoxService.start()
        } catch (e: Throwable) {
            val err = "Failed to start BoxService: ${e.message}"
            OperationalLogCatcher.error(LogCategory.VPN_LIFECYCLE, "LifecycleController", err)
            _vpnState.value = VpnState.Failed("ERR_SERVICE_START", err)
        }
    }

    fun requestStopVpn() {
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "LifecycleController",
            "[${OperationalLogCatcher.EVENT_VPN_STOP_REQUESTED}] Requesting VPN stop..."
        )
        _vpnState.value = VpnState.Stopping
        try {
            BoxService.stop()
        } catch (e: Throwable) {
            OperationalLogCatcher.error(
                LogCategory.VPN_LIFECYCLE,
                "LifecycleController",
                "Failed to stop VPN service: ${e.message}"
            )
        }
    }
}
