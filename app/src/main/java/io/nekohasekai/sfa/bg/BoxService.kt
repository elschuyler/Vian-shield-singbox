// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.bg

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bitproxy.BitProxyServiceHooks
import io.nekohasekai.sfa.bitproxy.EventLog
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Official SFA BoxService engine coordinator incorporating BitProxy reliability hooks.
 */
class BoxService(
    private val service: Service,
    private val platformInterface: PlatformInterface
) : CommandServerHandler {

    companion object {
        private const val TAG = "BoxService"

        val status = MutableLiveData(Status.Stopped)

        fun start() {
            val intent = Intent(Application.application, Settings.serviceClass())
            ContextCompat.startForegroundService(Application.application, intent)
        }

        fun stop() {
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName)
            )
        }
    }

    var fileDescriptor: ParcelFileDescriptor? = null

    private val notification = ServiceNotification(status, service)
    private lateinit var commandServer: CommandServer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.SERVICE_CLOSE -> {
                    stopService()
                }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        serviceUpdateIdleMode()
                    }
                }
            }
        }
    }

    private fun startCommandServer() {
        Libbox.promoteOOMDraft()
        Libbox.discardPowerReportDraft()
        val server = CommandServer(this, platformInterface)
        server.start()
        this.commandServer = server
    }

    private suspend fun startService() {
        try {
            val content = Settings.getActiveConfigContent()
            if (content.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration, "Active WireGuard configuration is empty")
                return
            }

            withContext(Dispatchers.Main) {
                notification.show("Vianshield", "Starting protection...")
            }

            DefaultNetworkMonitor.start()

            try {
                commandServer.startOrReloadService(
                    content,
                    OverrideOptions().apply {
                        autoRedirect = Settings.autoRedirect
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start sing-box service", e)
                stopAndAlert(Alert.CreateService, e.message)
                return
            }

            withContext(Dispatchers.Main) {
                status.value = Status.Started
                notification.show("Vianshield", "Protected")
            }
            BitProxyServiceHooks.onStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Service startup error", e)
            stopAndAlert(Alert.StartService, e.message)
        }
    }

    override fun serviceStop() {
        stopService()
    }

    override fun serviceReload() {
        serviceScope.launch {
            val content = Settings.getActiveConfigContent()
            if (content.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration, "Config is blank during reload")
                return@launch
            }
            try {
                commandServer.startOrReloadService(
                    content,
                    OverrideOptions().apply {
                        autoRedirect = Settings.autoRedirect
                    }
                )
                EventLog.log("Service reloaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload service", e)
                stopAndAlert(Alert.CreateService, e.message)
            }
        }
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? {
        val proxyStatus = SystemProxyStatus()
        proxyStatus.available = false
        proxyStatus.enabled = false
        return proxyStatus
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {
        serviceReload()
    }

    private fun serviceUpdateIdleMode() {
        if (::commandServer.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Application.powerManager.isDeviceIdleMode) {
                commandServer.pause()
            } else {
                commandServer.wake()
            }
        }
    }

    private fun stopService() {
        if (status.value != Status.Started && status.value != Status.Starting) return
        status.postValue(Status.Stopping)
        BitProxyServiceHooks.onStopped()

        if (receiverRegistered) {
            try {
                service.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
        notification.close()

        serviceScope.launch {
            val pfd = fileDescriptor
            if (pfd != null) {
                try {
                    pfd.close()
                } catch (_: Exception) {}
                fileDescriptor = null
            }
            DefaultNetworkMonitor.stop()
            closeService()
            if (::commandServer.isInitialized) {
                try {
                    commandServer.close()
                } catch (_: Exception) {}
            }
            Settings.startedByUser = false
            withContext(Dispatchers.Main) {
                status.value = Status.Stopped
                BitProxyServiceHooks.onFullyStopped()
                service.stopSelf()
            }
        }
    }

    private fun closeService() {
        if (::commandServer.isInitialized) {
            runCatching {
                commandServer.closeService()
            }.onFailure {
                commandServer.setError("android: close service: ${it.message}")
            }
        }
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        BitProxyServiceHooks.onStartFailed(type, message)
        Settings.startedByUser = false
        val pfd = fileDescriptor
        if (pfd != null) {
            try {
                pfd.close()
            } catch (_: Exception) {}
            fileDescriptor = null
        }
        DefaultNetworkMonitor.stop()
        if (::commandServer.isInitialized) {
            closeService()
            try {
                commandServer.close()
            } catch (_: Exception) {}
        }
        withContext(Dispatchers.Main) {
            if (receiverRegistered) {
                try {
                    service.unregisterReceiver(receiver)
                } catch (_: Exception) {}
                receiverRegistered = false
            }
            notification.close()
            status.value = Status.Stopped
            BitProxyServiceHooks.onFullyStopped()
            service.stopSelf()
        }
    }

    internal fun onStartCommand(): Int {
        if (status.value != Status.Stopped) return Service.START_NOT_STICKY
        status.value = Status.Starting
        notification.show("Vianshield", "Starting...")

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                service,
                receiver,
                IntentFilter().apply {
                    addAction(Action.SERVICE_CLOSE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        try {
            startCommandServer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start command server", e)
            serviceScope.launch {
                stopAndAlert(Alert.StartCommandServer, e.message)
            }
            return Service.START_NOT_STICKY
        }

        serviceScope.launch {
            startService()
        }

        return Service.START_NOT_STICKY
    }

    internal fun onDestroy() {
        stopService()
    }

    internal fun onRevoke() {
        stopService()
    }

    internal fun onBind(): IBinder? = null
}
