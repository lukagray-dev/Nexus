package com.nexus.parent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nexus.parent.MainActivity
import com.nexus.parent.R

/**
 * ParentNotificationManager.kt - Silent low-priority notification builder for `ParentConnectionService`.
 *
 * Hey friend! Android requires any Foreground Service that keeps WebRTC sockets alive while the
 * screen is locked to show an ongoing status bar notification. Using `IMPORTANCE_LOW` guarantees
 * it never beeps, buzzes, or pops up as a noisy heads-up banner while still keeping our encrypted
 * P2P link alive reliably.
 */
object ParentNotificationManager {

    const val CHANNEL_ID = "nexus_parent_connection_channel"
    const val NOTIFICATION_ID = 4101

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.service_notification_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(
        context: Context,
        title: String,
        body: String
    ): Notification {
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .build()
    }
}
