package com.nexus.child.components.notification

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * NotificationController coordinates notification observation, filtering,
 * and retrieval on the Android Child device.
 *
 * Hey friend! Here is how this controller functions:
 * 1. Checks whether the user has granted Notification Access in Android Settings.
 * 2. Bridges events with NexusNotificationListenerService when monitoring is active.
 * 3. Filters out internal notifications from the Nexus Child app itself so we never echo our own status alerts.
 * 4. Filters out empty system stubs and provides snapshot capabilities of active status bar notifications.
 */
class NotificationController(
    private val context: Context
) : INotificationController {

    companion object {
        private const val TAG = "NotificationController"

        // Packages to ignore by default (e.g. system UI noise or self)
        private val DEFAULT_IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "android"
        )
    }

    @Volatile
    private var isMonitoringActive: Boolean = false

    private var notificationCallback: ((NotificationRecord) -> Unit)? = null
    private var removalCallback: ((NotificationRemovedRecord) -> Unit)? = null

    /**
     * Checks whether Notification Access is granted to Nexus Child in Android system settings.
     */
    override fun hasNotificationAccess(): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            enabledListeners.contains(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification access permission: ${e.message}", e)
            false
        }
    }

    /**
     * Returns true if real-time push notification monitoring is currently underway.
     */
    override fun isMonitoring(): Boolean {
        return isMonitoringActive
    }

    /**
     * Initiates real-time interception of push notifications.
     */
    override fun startMonitoring(
        onNotification: (NotificationRecord) -> Unit,
        onRemoved: (NotificationRemovedRecord) -> Unit
    ): Boolean {
        if (!hasNotificationAccess()) {
            Log.w(TAG, "Cannot start notification monitoring: Notification Access is not granted")
            return false
        }

        if (isMonitoringActive) {
            Log.d(TAG, "Notification monitoring is already active")
            return true
        }

        Log.d(TAG, "Starting notification monitoring and attaching event listener...")
        this.notificationCallback = onNotification
        this.removalCallback = onRemoved

        NexusNotificationListenerService.setEventListener(object : INotificationEventListener {
            override fun onNotificationPosted(notification: NotificationRecord) {
                if (!isMonitoringActive) return

                // Filter out self-notifications and empty noise
                if (shouldFilterNotification(notification)) {
                    return
                }

                notificationCallback?.invoke(notification)
            }

            override fun onNotificationRemoved(removed: NotificationRemovedRecord) {
                if (!isMonitoringActive) return

                if (removed.packageName == context.packageName) {
                    return
                }

                removalCallback?.invoke(removed)
            }
        })

        isMonitoringActive = true
        Log.d(TAG, "Notification monitoring successfully started")
        return true
    }

    /**
     * Halts notification observation and disconnects event callbacks.
     */
    override fun stopMonitoring(): Boolean {
        if (!isMonitoringActive) {
            Log.d(TAG, "Notification monitoring is already stopped")
            return false
        }

        Log.d(TAG, "Stopping notification monitoring...")
        NexusNotificationListenerService.setEventListener(null)
        notificationCallback = null
        removalCallback = null
        isMonitoringActive = false
        Log.d(TAG, "Notification monitoring successfully stopped")
        return true
    }

    /**
     * Fetches a snapshot of all currently active notifications on the status bar.
     */
    override fun fetchActiveNotifications(): List<NotificationRecord> {
        if (!hasNotificationAccess()) {
            Log.w(TAG, "Cannot fetch active notifications: access not granted")
            return emptyList()
        }

        val activeList = NexusNotificationListenerService.getActiveNotificationRecords(context)
        return activeList.filterNot { shouldFilterNotification(it) }
    }

    /**
     * Determines whether a notification should be filtered out to avoid spamming the parent console.
     */
    private fun shouldFilterNotification(record: NotificationRecord): Boolean {
        // Filter out our own app's notifications (e.g. background service status)
        if (record.packageName == context.packageName) {
            return true
        }

        // Filter out empty notifications where neither title nor text is present
        if (record.title.isBlank() && record.text.isBlank() && record.bigText.isNullOrBlank()) {
            return true
        }

        return false
    }

    /**
     * Completely cleans up all active listeners.
     */
    override fun cleanup() {
        stopMonitoring()
    }
}
