// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.bitproxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.constant.Alert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reliability improvements and recovery hooks inspired by BitProxy patches 0008 and 0034.
 */
object BitProxyServiceHooks {
    private const val CHANNEL = "sfa_service_errors"
    private const val FAILED_ID = 404
    private const val STOP_TIMEOUT_MS = 15_000L

    @Volatile
    var running = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stopWatchdog: Job? = null

    fun onStarted() {
        EventLog.log("VPN service started successfully")
        running = true
    }

    fun onStopped() {
        EventLog.log("VPN service stopping...")
        stopWatchdog?.cancel()
        stopWatchdog = scope.launch {
            delay(STOP_TIMEOUT_MS)
            EventLog.log("Stop did not finish in ${STOP_TIMEOUT_MS / 1000}s (sing-box hung); ending process for safety")
            notifyHung()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        running = false
    }

    fun onFullyStopped() {
        stopWatchdog?.cancel()
        stopWatchdog = null
        EventLog.log("VPN service fully stopped")
    }

    fun onStartFailed(type: Alert, message: String?) {
        val detail = message?.takeIf { it.isNotBlank() } ?: type.name
        EventLog.log("VPN start failed: $detail")
        notifyFailure(detail)
        com.example.vpn.VpnLifecycleController.updateState(
            com.example.vpn.VpnState.Failed("ERR_ENGINE_START", detail)
        )
    }

    private fun notifyFailure(detail: String) = runCatching {
        val ctx = Application.application
        val nm = Application.notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "VPN Errors", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        nm.notify(
            FAILED_ID,
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("VPN start failed")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notifyHung() = runCatching {
        val ctx = Application.application
        val nm = Application.notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "VPN Errors", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        nm.notify(
            FAILED_ID,
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("VPN stopped unexpectedly")
                .setContentText("The VPN engine hung during shutdown and was restarted.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
