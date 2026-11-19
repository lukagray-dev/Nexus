package com.nexus.child.components.notification

import android.util.Log

/**
 * NotificationCommandHandler coordinates DataChannel commands and telemetry payloads
 * for real-time notification monitoring between Child and Parent.
 *
 * Hey friend! Here is the command handshake protocol:
 * 1. Parent clicks Play in Notifications panel -> sends "NOTIFICATION_ON" over the DataChannel.
 * 2. We verify that Notification Access is granted on the child device.
 *    - If denied: we send "NOTIFICATION_ERROR: Notification access permission not granted on child device".
 * 3. We activate monitoring and send "NOTIFICATION_STARTED" confirmation.
 * 4. We fetch the active status bar notifications snapshot and dispatch "NOTIFICATION_SNAPSHOT"
 *    so the parent console instantly shows what's already on the screen.
 * 5. When a new notification arrives or updates, we dispatch a real-time "NOTIFICATION_NEW" packet.
 * 6. When a notification is dismissed by the child, we dispatch a "NOTIFICATION_REMOVED" packet.
 * 7. Parent clicks Pause -> sends "NOTIFICATION_OFF". We halt monitoring and send "NOTIFICATION_STOPPED".
 * 8. If peer disconnects -> onPeerDisconnected() shuts down listeners immediately to prevent battery drain.
 */
class NotificationCommandHandler(
    private val notificationController: INotificationController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "NotificationCmdHandler"

        // Commands
        const val CMD_NOTIFICATION_ON = "NOTIFICATION_ON"
        const val CMD_NOTIFICATION_OFF = "NOTIFICATION_OFF"
        const val ALIAS_NOTIFICATION_START = "NOTIFICATION_START"
        const val ALIAS_START_NOTIFICATION = "START_NOTIFICATION"
        const val ALIAS_NOTIFICATION_STOP = "NOTIFICATION_STOP"
        const val ALIAS_STOP_NOTIFICATION = "STOP_NOTIFICATION"

        // Responses
        const val RESP_NOTIFICATION_STARTED = "NOTIFICATION_STARTED"
        const val RESP_NOTIFICATION_STOPPED = "NOTIFICATION_STOPPED"
        const val RESP_NOTIFICATION_ERROR_PREFIX = "NOTIFICATION_ERROR: "

        // Telemetry Types
        const val TYPE_NOTIFICATION_SNAPSHOT = "NOTIFICATION_SNAPSHOT"
        const val TYPE_NOTIFICATION_NEW = "NOTIFICATION_NEW"
        const val TYPE_NOTIFICATION_REMOVED = "NOTIFICATION_REMOVED"
    }

    /**
     * Inspects incoming DataChannel strings and executes the matching notification command.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_NOTIFICATION_ON, ALIAS_NOTIFICATION_START, ALIAS_START_NOTIFICATION -> {
                handleNotificationOn()
                true
            }
            CMD_NOTIFICATION_OFF, ALIAS_NOTIFICATION_STOP, ALIAS_STOP_NOTIFICATION -> {
                handleNotificationOff()
                true
            }
            else -> false
        }
    }

    /**
     * Activates notification monitoring, sends confirmation, and dispatches the active snapshot.
     */
    private fun handleNotificationOn() {
        Log.d(TAG, "Processing NOTIFICATION_ON command...")

        // Step 1: Permission check
        if (!notificationController.hasNotificationAccess()) {
            val errorMsg = "${RESP_NOTIFICATION_ERROR_PREFIX}Notification access permission not granted on child device"
            Log.w(TAG, errorMsg)
            dataSender(errorMsg)
            return
        }

        // Step 2: Register real-time observation callbacks
        val started = notificationController.startMonitoring(
            onNotification = { newNotif ->
                sendNewNotificationRecord(newNotif)
            },
            onRemoved = { removedNotif ->
                sendRemovedNotificationRecord(removedNotif)
            }
        )

        if (started) {
            Log.d(TAG, "Notification monitoring started. Sending confirmation: $RESP_NOTIFICATION_STARTED")
            dataSender(RESP_NOTIFICATION_STARTED)

            // Step 3: Fetch active notifications snapshot and dispatch to parent
            try {
                val activeList = notificationController.fetchActiveNotifications()
                val snapshotJson = buildNotificationSnapshotJson(activeList)
                dataSender(snapshotJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching active notification snapshot: ${e.message}", e)
            }
        } else {
            val errorMsg = "${RESP_NOTIFICATION_ERROR_PREFIX}Failed to start notification listener service"
            Log.e(TAG, errorMsg)
            dataSender(errorMsg)
        }
    }

    /**
     * Halts notification monitoring and sends confirmation.
     */
    private fun handleNotificationOff() {
        Log.d(TAG, "Processing NOTIFICATION_OFF command...")
        notificationController.stopMonitoring()
        dataSender(RESP_NOTIFICATION_STOPPED)
        Log.d(TAG, "Notification monitoring stopped. Sent confirmation: $RESP_NOTIFICATION_STOPPED")
    }

    /**
     * Dispatches a real-time event packet for a newly arrived or updated notification.
     */
    private fun sendNewNotificationRecord(record: NotificationRecord) {
        try {
            val packet = buildNewNotificationJson(record)
            dataSender(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize NOTIFICATION_NEW packet: ${e.message}", e)
        }
    }

    /**
     * Dispatches an event packet when a notification is removed or dismissed on the child device.
     */
    private fun sendRemovedNotificationRecord(removed: NotificationRemovedRecord) {
        try {
            val packet = buildRemovedNotificationJson(removed)
            dataSender(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize NOTIFICATION_REMOVED packet: ${e.message}", e)
        }
    }

    /**
     * Builds the JSON snapshot packet containing all currently active notifications.
     */
    fun buildNotificationSnapshotJson(notifications: List<NotificationRecord>): String {
        val notifsJson = notifications.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toJsonString() }
        return "{\"type\":\"$TYPE_NOTIFICATION_SNAPSHOT\"," +
                "\"count\":${notifications.size}," +
                "\"notifications\":$notifsJson}"
    }

    /**
     * Builds the JSON packet for a new real-time notification event.
     */
    fun buildNewNotificationJson(record: NotificationRecord): String {
        return "{\"type\":\"$TYPE_NOTIFICATION_NEW\",\"notification\":${record.toJsonString()}}"
    }

    /**
     * Builds the JSON packet for a notification removal event.
     */
    fun buildRemovedNotificationJson(removed: NotificationRemovedRecord): String {
        return "{\"type\":\"$TYPE_NOTIFICATION_REMOVED\"," +
                "\"key\":\"${NotificationRecord.escapeJson(removed.key)}\"," +
                "\"packageName\":\"${NotificationRecord.escapeJson(removed.packageName)}\"," +
                "\"timestamp\":${removed.timestamp}," +
                "\"reason\":${removed.reason}}"
    }

    /**
     * Invoked when the WebRTC peer connection is dropped or disconnected.
     * Guarantees all listeners are stopped to prevent battery leakage.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Halting notification monitoring fail-safe...")
        notificationController.stopMonitoring()
    }

    /**
     * Cleans up all controller references.
     */
    fun cleanup() {
        notificationController.cleanup()
    }
}
