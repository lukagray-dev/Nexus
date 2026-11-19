package com.nexus.child.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying default contract state and configuration of NexusBackgroundService.
 */
class NexusBackgroundServiceTest {

    @Test
    fun testInitialServiceState() {
        assertFalse(
            "Service must report not running before onCreate",
            NexusBackgroundService.isRunning()
        )
    }

    @Test
    fun testInitialConnectionStateFlow() {
        val isConnected = NexusBackgroundService.isConnected.value
        assertFalse("Service must default to disconnected state", isConnected)
    }

    @Test
    fun testNotificationIdConstant() {
        assertEquals(8801, ServiceNotificationManager.NOTIFICATION_ID)
        assertEquals("nexus_core_service_channel", ServiceNotificationManager.CHANNEL_ID)
    }
}
