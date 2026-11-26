package com.nexus.parent.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexus.parent.MainActivity
import com.nexus.parent.R

/**
 * ParentNotificationManager.kt - Notification channels and builders for `ParentConnectionService`
 * and urgent `GeneralAlertsController` safety notifications.
 *
 * Hey friend! We intentionally separate notifications into two distinct channels:
 * 1. `CHANNEL_ID` (`IMPORTANCE_LOW`): silent ongoing status notification required for the
 *    background WebRTC Foreground Service so it never beeps or buzzes.
 * 2. `ALERT_CHANNEL_ID` (`IMPORTANCE_HIGH`): audible heads-up alerts for urgent child events
 *    (Active Call, Low Battery < 15%, Unexpected Disconnection) even when minimized/screen off.
 */
object ParentNotificationManager {

    const val CHANNEL_ID = "nexus_parent_connection_channel"
    const val ALERT_CHANNEL_ID = "nexus_parent_alerts_channel"
    const val NOTIFICATION_ID = 4101

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.service_notification_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(serviceChannel)
        }

        if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.alert_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.alert_notification_channel_desc)
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    fun buildNotification(
        context: Context,
        title: String,
        body: String
    ): Notification {
        ensureChannel(context)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createMainActivityPendingIntent(context))
            .build()
    }

    fun postSafetyAlert(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        onlyAlertOnce: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createMainActivityPendingIntent(context))
            .build()

        manager.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(notificationId)
    }

    private fun createMainActivityPendingIntent(context: Context): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
