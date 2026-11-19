package com.nexus.child.components.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Notification DataChannel protocol, command-and-control state machine,
 * initial active notifications snapshot dispatching, live push events, and JSON serialization.
 *
 * Hey friend! These unit tests verify:
 * 1. Permission security enforcement: NOTIFICATION_ON fails immediately if notification access is absent.
 * 2. Successful NOTIFICATION_ON handshake: activates observer, sends NOTIFICATION_STARTED, and dispatches NOTIFICATION_SNAPSHOT.
 * 3. Successful NOTIFICATION_OFF handshake: halts observer and responds with NOTIFICATION_STOPPED.
 * 4. Backward-compatible alias commands: NOTIFICATION_START and NOTIFICATION_STOP.
 * 5. Real-time live event dispatching: NOTIFICATION_NEW payload contains all rich metadata.
 * 6. Notification removal events: NOTIFICATION_REMOVED payload contains key and timestamp.
 * 7. Disconnection fail-safe: peer disconnection triggers immediate observer shutdown.
 * 8. JSON serialization: ensures app, title, text, bigText, actions, and messages conform to the desktop parent contract.
 */
class NotificationComponentTest {

    private class FakeNotificationController(
        var hasAccess: Boolean = true,
        var isMonitoringActive: Boolean = false,
        var startResult: Boolean = true,
        var mockNotifications: List<NotificationRecord> = emptyList()
    ) : INotificationController {

        var startCalled = false
        var stopCalled = false
        var cleanupCalled = false
        var lastNotificationCallback: ((NotificationRecord) -> Unit)? = null
        var lastRemovalCallback: ((NotificationRemovedRecord) -> Unit)? = null

        override fun hasNotificationAccess(): Boolean = hasAccess

        override fun isMonitoring(): Boolean = isMonitoringActive

        override fun fetchActiveNotifications(): List<NotificationRecord> = mockNotifications

        override fun startMonitoring(
            onNotification: (NotificationRecord) -> Unit,
            onRemoved: (NotificationRemovedRecord) -> Unit
        ): Boolean {
            startCalled = true
            lastNotificationCallback = onNotification
            lastRemovalCallback = onRemoved
            if (startResult) {
                isMonitoringActive = true
            }
            return startResult
        }

        override fun stopMonitoring(): Boolean {
            stopCalled = true
            val wasMonitoring = isMonitoringActive
            isMonitoringActive = false
            lastNotificationCallback = null
            lastRemovalCallback = null
            return wasMonitoring
        }

        override fun cleanup() {
            cleanupCalled = true
            isMonitoringActive = false
            lastNotificationCallback = null
            lastRemovalCallback = null
        }

        fun simulateLiveNotification(record: NotificationRecord) {
            lastNotificationCallback?.invoke(record)
        }

        fun simulateRemovedNotification(record: NotificationRemovedRecord) {
            lastRemovalCallback?.invoke(record)
        }
    }

    private fun createSampleNotificationRecord(
        id: Int = 101,
        key: String = "0|com.whatsapp|101|null|10200",
        packageName: String = "com.whatsapp",
        appName: String = "WhatsApp",
        title: String = "Lucas",
        text: String = "Hey, are you free tonight?",
        bigText: String? = "Hey, are you free tonight? Let's check out the new film.",
        actions: List<String> = listOf("Reply", "Mark as read"),
        messages: List<NotificationMessageRecord> = listOf(
            NotificationMessageRecord(
                sender = "Lucas",
                text = "Hey, are you free tonight?",
                timestamp = 1732008450000L
            )
        )
    ): NotificationRecord {
        return NotificationRecord(
            id = id,
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            bigText = bigText,
            subText = "lucas@chat",
            summaryText = null,
            infoText = null,
            category = "msg",
            channelId = "messages",
            postTime = 1732008450000L,
            isOngoing = false,
            isClearable = true,
            groupKey = "group_whatsapp_101",
            actions = actions,
            messages = messages
        )
    }

    @Test
    fun testNotificationOnFailsWhenAccessNotGranted() {
        val fakeController = FakeNotificationController(hasAccess = false)
        val sentMessages = mutableListOf<String>()

        val handler = NotificationCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(NotificationCommandHandler.CMD_NOTIFICATION_ON)
        assertTrue("NOTIFICATION_ON should be recognized by command handler", handled)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Should return NOTIFICATION_ERROR when access is absent",
            sentMessages[0].startsWith(NotificationCommandHandler.RESP_NOTIFICATION_ERROR_PREFIX)
        )
        assertFalse("Monitoring should remain inactive", fakeController.isMonitoring())
    }

    @Test
    fun testNotificationOnStartsMonitoringAndSendsSnapshot() {
        val sampleRecord = createSampleNotificationRecord()
        val fakeController = FakeNotificationController(
            hasAccess = true,
            mockNotifications = listOf(sampleRecord)
        )
        val sentMessages = mutableListOf<String>()

        val handler = NotificationCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(NotificationCommandHandler.CMD_NOTIFICATION_ON)
        assertTrue(handled)
        assertTrue("Controller startMonitoring should be invoked", fakeController.startCalled)
        assertTrue("Controller should report isMonitoring active", fakeController.isMonitoring())

        // Expect two messages: STARTED confirmation + active snapshot JSON
        assertEquals(2, sentMessages.size)
        assertEquals(NotificationCommandHandler.RESP_NOTIFICATION_STARTED, sentMessages[0])

        val snapshotJson = sentMessages[1]
        assertTrue(snapshotJson.contains("\"type\":\"NOTIFICATION_SNAPSHOT\""))
        assertTrue(snapshotJson.contains("\"count\":1"))
        assertTrue(snapshotJson.contains("\"appName\":\"WhatsApp\""))
        assertTrue(snapshotJson.contains("\"packageName\":\"com.whatsapp\""))
        assertTrue(snapshotJson.contains("\"title\":\"Lucas\""))
    }

    @Test
    fun testNotificationOffStopsMonitoring() {
        val fakeController = FakeNotificationController(
            hasAccess = true,
            isMonitoringActive = true
        )
        val sentMessages = mutableListOf<String>()

        val handler = NotificationCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(NotificationCommandHandler.CMD_NOTIFICATION_OFF)
        assertTrue(handled)
        assertTrue("Controller stopMonitoring should be called", fakeController.stopCalled)
        assertFalse("Controller monitoring flag should be false", fakeController.isMonitoring())
        assertEquals(1, sentMessages.size)
        assertEquals(NotificationCommandHandler.RESP_NOTIFICATION_STOPPED, sentMessages[0])
    }

    @Test
    fun testAliasCommandsSupported() {
        val fakeController = FakeNotificationController(hasAccess = true)
        val sentMessages = mutableListOf<String>()

        val handler = NotificationCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        assertTrue(handler.handleCommand(NotificationCommandHandler.ALIAS_NOTIFICATION_START))
        assertTrue(handler.handleCommand(NotificationCommandHandler.ALIAS_NOTIFICATION_STOP))
    }

    @Test
    fun testLiveNotificationDispatchesNotificationNew() {
        val fakeController = FakeNotificationController(hasAccess = true)
        val sentMessages = mutableListOf<String>()

        val handler = NotificationCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        handler.handleCommand(NotificationCommandHandler.CMD_NOTIFICATION_ON)
        sentMessages.clear() // Clear start handshake messages

        // Simulate a new notification arriving on the child device
        val liveRecord = createSampleNotificationRecord(
            id = 202,
            key = "0|com.instagram.android|202|null|10300",
            packageName = "com.instagram.android",
            appName = "Instagram",
            title = "emma_w",
            text = "Sent you a photo",
            actions = listOf("Reply")
        )
        fakeController.simulateLiveNotification(liveRecord)

        assertEquals(1, sentMessages.size)
        val packet = sentMessages[0]
        assertTrue(packet.contains("\"type\":\"NOTIFICATION_NEW\""))
        assertTrue(packet.contains("\"id\":202"))
        assertTrue(packet.contains("\"appName\":\"Instagram\""))
        assertTrue(packet.contains("\"packageName\":\"com.instagram.android\""))
        assertTrue(packet.contains("\"title\":\"emma_w\""))
        assertTrue(packet.contains("\"text\":\"Sent you a photo\""))
        assertTrue(packet.contains("\"actions\":[\"Reply\"]"))
    }

    @Test
    fun testNotificationRemovedDispatchesNotificationRemoved() {
        val fakeController = FakeNotificationController(hasAccess = true)
        val sentMessages = mutableListOf<String>()

        val handler = NotificationCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        handler.handleCommand(NotificationCommandHandler.CMD_NOTIFICATION_ON)
        sentMessages.clear()

        val removedRecord = NotificationRemovedRecord(
            key = "0|com.whatsapp|101|null|10200",
            packageName = "com.whatsapp",
            timestamp = 1732008500000L,
            reason = 1
        )
        fakeController.simulateRemovedNotification(removedRecord)

        assertEquals(1, sentMessages.size)
        val packet = sentMessages[0]
        assertTrue(packet.contains("\"type\":\"NOTIFICATION_REMOVED\""))
        assertTrue(packet.contains("\"key\":\"0|com.whatsapp|101|null|10200\""))
        assertTrue(packet.contains("\"packageName\":\"com.whatsapp\""))
        assertTrue(packet.contains("\"reason\":1"))
    }

    @Test
    fun testPeerDisconnectedShutsDownMonitoring() {
        val fakeController = FakeNotificationController(
            hasAccess = true,
            isMonitoringActive = true
        )
        val handler = NotificationCommandHandler(fakeController) { true }

        handler.onPeerDisconnected()
        assertTrue("stopMonitoring should be invoked on peer disconnection", fakeController.stopCalled)
        assertFalse("Monitoring should be inactive after peer disconnection", fakeController.isMonitoring())
    }

    @Test
    fun testNotificationRecordJsonSerialization() {
        val record = createSampleNotificationRecord()
        val jsonStr = record.toJsonString()

        assertTrue(jsonStr.contains("\"id\":101"))
        assertTrue(jsonStr.contains("\"key\":\"0|com.whatsapp|101|null|10200\""))
        assertTrue(jsonStr.contains("\"packageName\":\"com.whatsapp\""))
        assertTrue(jsonStr.contains("\"appName\":\"WhatsApp\""))
        assertTrue(jsonStr.contains("\"title\":\"Lucas\""))
        assertTrue(jsonStr.contains("\"text\":\"Hey, are you free tonight?\""))
        assertTrue(jsonStr.contains("\"bigText\":\"Hey, are you free tonight? Let's check out the new film.\""))
        assertTrue(jsonStr.contains("\"category\":\"msg\""))
        assertTrue(jsonStr.contains("\"actions\":[\"Reply\",\"Mark as read\"]"))
        assertTrue(jsonStr.contains("\"sender\":\"Lucas\""))
        assertTrue(jsonStr.contains("\"timestamp\":1732008450000"))
    }
}
