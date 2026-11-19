package com.nexus.child.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexus.child.MainActivity
import com.nexus.child.R

/**
 * ServiceNotificationManager creates and manages persistent foreground service notifications.
 *
 * Hey friend! Here is why this notification is essential:
 * Starting from Android 8 (Oreo) and reinforced in Android 14+, any service executing ongoing
 * background tasks MUST run as a "Foreground Service" paired with an active status bar notification.
 *
 * Why this matters:
 * 1. Linux Kernel Out-Of-Memory (OOM) Protection: Android's LowMemoryKiller (LMK) assigns foreground
 *    services a high priority (low oom_adj score), preventing the OS from reclaiming its memory.
 * 2. Visual Transparency: Informs the system that continuous telemetry is actively executing.
 * 3. Tactical Aesthetic & No-Emoji Compliance: Follows Nexus UI rules with clean Lucide icons
 *    and professional monochrome copy.
 */
class ServiceNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "ServiceNotifMgr"
        const val NOTIFICATION_ID = 8801
        const val CHANNEL_ID = "nexus_core_service_channel"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Creates the Android Notification Channel required on Android 8.0 (API 26) and above.
     * Uses IMPORTANCE_LOW to prevent intrusive sound or heads-up vibration popups.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false) // Don't show numeric notification count badges on app icon
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Visible on lockscreen
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Foreground notification channel created: $CHANNEL_ID")
        }
    }

    /**
     * Builds the persistent ongoing Notification for NexusBackgroundService.
     *
     * @param isConnected True when WebRTC peer connection with parent console is active.
     */
    fun buildForegroundNotification(isConnected: Boolean = false): Notification {
        // Tapping the notification brings the existing MainActivity to the front
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (isConnected) {
            context.getString(R.string.notification_status_connected)
        } else {
            context.getString(R.string.notification_status_waiting)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(contentText)
            .setOngoing(true) // User cannot swipe it away; keeps service protected in foreground
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Dynamically updates the notification text when the connection state toggles.
     */
    fun updateNotification(isConnected: Boolean) {
        try {
            val notification = buildForegroundNotification(isConnected)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification: ${e.message}", e)
        }
    }
}
