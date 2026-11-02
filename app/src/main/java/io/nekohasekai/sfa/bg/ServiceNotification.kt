// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.MutableLiveData
import com.example.MainActivity
import com.example.R
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Status

class ServiceNotification(
    private val status: MutableLiveData<Status>,
    private val service: Service
) {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL = "vianshield_vpn_service"
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(service, NOTIFICATION_CHANNEL)
            .setShowWhen(false)
            .setOngoing(true)
            .setContentTitle("Vianshield")
            .setOnlyAlertOnce(true)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(service, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    flags
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "Stop",
                    PendingIntent.getBroadcast(
                        service,
                        0,
                        Intent(Action.SERVICE_CLOSE).setPackage(service.packageName),
                        flags
                    )
                ).build()
            )
    }

    fun show(title: String, statusText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Vianshield VPN Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            Application.notificationManager.createNotificationChannel(channel)
        }
        val notification = notificationBuilder
            .setContentTitle(title.takeIf { it.isNotBlank() } ?: "Vianshield")
            .setContentText(statusText)
            .build()
        service.startForeground(NOTIFICATION_ID, notification)
    }

    fun close() {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}
