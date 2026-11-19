package com.nexus.child.components.notification

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.nexus.child.service.NexusBackgroundService
import java.util.concurrent.ConcurrentHashMap

/**
 * NexusNotificationListenerService is the native Android NotificationListenerService that
 * intercepts system-wide and third-party application notifications.
 *
 * Hey friend! Here is how Android's NotificationListenerService works under the hood:
 * 1. The Android OS framework (NotificationManagerService) binds to this service in the background
 *    once the user grants "Notification Access" in Android System Settings.
 * 2. Whenever ANY app on the phone posts a notification, Android automatically invokes onNotificationPosted(sbn).
 * 3. Whenever a notification is dismissed, swiped, or canceled, Android invokes onNotificationRemoved(sbn).
 * 4. Because Android's system server instantiates this service directly, we maintain a thread-safe
 *    companion bridge so our NotificationController can receive live events and query active notifications.
 */
class NexusNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NexusNotifService"

        @Volatile
        private var activeServiceInstance: NexusNotificationListenerService? = null

        @Volatile
        private var notificationListener: INotificationEventListener? = null

        // In-memory cache for app names to avoid repeated PackageManager queries
        private val appNameCache = ConcurrentHashMap<String, String>()

        /**
         * Returns true if the Android framework has connected and bound to this service.
         */
        fun isServiceConnected(): Boolean {
            return activeServiceInstance != null
        }

        /**
         * Registers an event listener from the NotificationController.
         */
        fun setEventListener(listener: INotificationEventListener?) {
            notificationListener = listener
            Log.d(TAG, "Notification event listener ${if (listener != null) "attached" else "detached"}")
        }

        /**
         * Retrieves an unmodifiable snapshot of currently active notifications from the status bar.
         */
        fun getActiveNotificationRecords(context: Context): List<NotificationRecord> {
            val service = activeServiceInstance ?: run {
                Log.w(TAG, "Cannot get active notifications: service instance is null")
                return emptyList()
            }

            return try {
                val activeSbns = service.activeNotifications ?: return emptyList()
                activeSbns.mapNotNull { sbn ->
                    parseStatusBarNotification(context, sbn)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching active notifications: ${e.message}", e)
                emptyList()
            }
        }

        /**
         * Resolves the human-readable application label from the package manager.
         * Results are cached to maintain high rendering performance.
         */
        fun resolveAppName(context: Context, packageName: String): String {
            return appNameCache.getOrPut(packageName) {
                try {
                    val pm = context.packageManager
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(packageName, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }
            }
        }

        /**
         * Parses a raw Android StatusBarNotification into our clean, serializable NotificationRecord.
         */
        fun parseStatusBarNotification(context: Context, sbn: StatusBarNotification): NotificationRecord? {
            val packageName = sbn.packageName ?: return null
            val notification = sbn.notification ?: return null
            val extras = notification.extras ?: Bundle()

            // Resolve human-readable application label
            val appName = resolveAppName(context, packageName)

            // Primary text and title extraction
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim()
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim()

            // Best title resolution
            val effectiveTitle = when {
                title.isNotBlank() -> title
                !titleBig.isNullOrBlank() -> titleBig
                else -> appName
            }

            // Notification flags and classification
            val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val isClearable = sbn.isClearable
            val category = notification.category ?: "general"
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification.channelId
            } else {
                null
            }

            // Extract action button titles (e.g. "Reply", "Mark as read", "Archive")
            val actionTitles = mutableListOf<String>()
            val rawActions = notification.actions
            if (rawActions != null) {
                for (action in rawActions) {
                    val actionTitle = action.title?.toString()?.trim()
                    if (!actionTitle.isNullOrBlank()) {
                        actionTitles.add(actionTitle)
                    }
                }
            }

            // Extract messaging style thread messages (WhatsApp, Telegram, SMS, etc.)
            val conversationMessages = extractMessagingStyleMessages(extras)

            return NotificationRecord(
                id = sbn.id,
                key = sbn.key ?: "${packageName}_${sbn.id}_${sbn.postTime}",
                packageName = packageName,
                appName = appName,
                title = effectiveTitle,
                text = text,
                bigText = bigText,
                subText = subText,
                summaryText = summaryText,
                infoText = infoText,
                category = category,
                channelId = channelId,
                postTime = sbn.postTime,
                isOngoing = isOngoing,
                isClearable = isClearable,
                groupKey = sbn.groupKey,
                actions = actionTitles,
                messages = conversationMessages
            )
        }

        /**
         * Extracts individual conversation messages from a Notification.MessagingStyle extras bundle.
         */
        private fun extractMessagingStyleMessages(extras: Bundle): List<NotificationMessageRecord> {
            val messages = mutableListOf<NotificationMessageRecord>()
            try {
                @Suppress("DEPRECATION")
                val rawMessages: Array<Parcelable>? = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                if (rawMessages != null) {
                    for (parcel in rawMessages) {
                        if (parcel is Bundle) {
                            val sender = parcel.getCharSequence("sender")?.toString()?.trim() ?: "Unknown"
                            val text = parcel.getCharSequence("text")?.toString()?.trim() ?: ""
                            val timestamp = parcel.getLong("time", System.currentTimeMillis())
                            if (text.isNotBlank()) {
                                messages.add(
                                    NotificationMessageRecord(
                                        sender = sender,
                                        text = text,
                                        timestamp = timestamp
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Non-critical: could not parse messaging style bundle: ${e.message}")
            }
            return messages
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeServiceInstance = this
        Log.d(TAG, "Notification listener connected by Android framework")
        ensureBackgroundServiceRunning()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        Log.d(TAG, "Notification listener disconnected by Android framework")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        Log.d(TAG, "Notification listener service destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Keep-alive check: if background service died, system notification activity revives it
        ensureBackgroundServiceRunning()

        if (sbn == null) return

        try {
            val record = parseStatusBarNotification(applicationContext, sbn) ?: return
            notificationListener?.onNotificationPosted(record)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted: ${e.message}", e)
        }
    }

    /**
     * Checks if NexusBackgroundService is running and revives it if terminated.
     */
    private fun ensureBackgroundServiceRunning() {
        try {
            if (!NexusBackgroundService.isRunning()) {
                Log.d(TAG, "Reviving NexusBackgroundService from NotificationListenerService...")
                NexusBackgroundService.start(applicationContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to revive background service from notification listener: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            val key = sbn.key ?: "${sbn.packageName}_${sbn.id}"
            val removedRecord = NotificationRemovedRecord(
                key = key,
                packageName = sbn.packageName ?: "unknown",
                timestamp = System.currentTimeMillis()
            )
            notificationListener?.onNotificationRemoved(removedRecord)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationRemoved: ${e.message}", e)
        }
    }
}

/**
 * INotificationEventListener defines the internal bridge interface between
 * NexusNotificationListenerService and NotificationController.
 */
interface INotificationEventListener {
    fun onNotificationPosted(notification: NotificationRecord)
    fun onNotificationRemoved(removed: NotificationRemovedRecord)
}
