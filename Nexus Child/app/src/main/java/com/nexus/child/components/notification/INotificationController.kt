package com.nexus.child.components.notification

/**
 * INotificationController specifies the interface contract for managing, querying,
 * and streaming system and application push notifications on the Android Child device.
 *
 * Hey friend! This interface decouples notification lifecycle and observation logic
 * from DataChannel transport, enabling comprehensive unit testing without requiring
 * live Android OS service bindings.
 */
interface INotificationController {

    /**
     * Checks whether the user has granted Android Notification Access
     * (BIND_NOTIFICATION_LISTENER_SERVICE / ACTION_NOTIFICATION_LISTENER_SETTINGS).
     *
     * @return true if Nexus Child is enabled in Android's notification listeners registry.
     */
    fun hasNotificationAccess(): Boolean

    /**
     * Returns true if real-time push notification monitoring is currently active.
     */
    fun isMonitoring(): Boolean

    /**
     * Begins real-time interception of push notifications.
     *
     * @param onNotification Callback invoked when a notification arrives or updates on the device.
     * @param onRemoved Callback invoked when an active notification is dismissed or canceled.
     * @return true if monitoring was initiated successfully; false if access is missing.
     */
    fun startMonitoring(
        onNotification: (NotificationRecord) -> Unit,
        onRemoved: (NotificationRemovedRecord) -> Unit
    ): Boolean

    /**
     * Halts real-time notification monitoring and unhooks event callbacks.
     *
     * @return true if an active monitoring session was stopped; false if already idle.
     */
    fun stopMonitoring(): Boolean

    /**
     * Fetches a snapshot of all currently active notifications displayed on the status bar.
     *
     * @return List of parsed NotificationRecord objects.
     */
    fun fetchActiveNotifications(): List<NotificationRecord>

    /**
     * Completely cleans up all active observers, listeners, and references.
     */
    fun cleanup()
}
