package com.nexus.parent.data.monitor

import com.nexus.parent.state.NotificationFilter
import com.nexus.parent.state.WellbeingCategoryFilter
import com.nexus.parent.state.WellbeingRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MonitorProtocolTest.kt - Verifies wire-protocol formatters and category mappers against Desktop.
 */
class MonitorProtocolTest {

    @Test
    fun `formatSimSlot normalizes raw indices and subIds cleanly`() {
        assertEquals("", MonitorProtocol.formatSimSlot(null))
        assertEquals("", MonitorProtocol.formatSimSlot("   "))
        assertEquals("SIM 1 • Jio", MonitorProtocol.formatSimSlot("SIM 1 • Jio"))
        assertEquals("SIM 1", MonitorProtocol.formatSimSlot("0"))
        assertEquals("SIM 1", MonitorProtocol.formatSimSlot("1"))
        assertEquals("SIM 2", MonitorProtocol.formatSimSlot("2"))
        assertEquals("SIM 1", MonitorProtocol.formatSimSlot("SIM 5"))
        assertEquals("SIM 2", MonitorProtocol.formatSimSlot("4"))
    }

    @Test
    fun `formatCallDuration and formatScreenTimeDuration match Desktop outputs`() {
        assertEquals("00:00", MonitorProtocol.formatCallDuration(0))
        assertEquals("03:05", MonitorProtocol.formatCallDuration(185))

        assertEquals("0m", MonitorProtocol.formatScreenTimeDuration(0L))
        assertEquals("45s", MonitorProtocol.formatScreenTimeDuration(45_000L))
        assertEquals("42m", MonitorProtocol.formatScreenTimeDuration(42 * 60_000L))
        assertEquals("2h", MonitorProtocol.formatScreenTimeDuration(120 * 60_000L))
        assertEquals("2h 14m", MonitorProtocol.formatScreenTimeDuration((134 * 60_000L)))
    }

    @Test
    fun `category and range mappers preserve Desktop filtering semantics`() {
        assertEquals(NotificationFilter.MESSAGES, MonitorProtocol.mapNotificationCategory("", "com.whatsapp"))
        assertEquals(NotificationFilter.SOCIAL, MonitorProtocol.mapNotificationCategory("social", "com.example.app"))
        assertEquals(NotificationFilter.SYSTEM, MonitorProtocol.mapNotificationCategory("service", "com.android.systemui"))
        assertEquals(NotificationFilter.MEDIA, MonitorProtocol.mapNotificationCategory("transport", "com.spotify.music"))
        assertEquals(NotificationFilter.ALL, MonitorProtocol.mapNotificationCategory("", "com.example.utility"))

        assertEquals(WellbeingCategoryFilter.SOCIAL, MonitorProtocol.mapWellbeingCategory("social"))
        assertEquals(WellbeingCategoryFilter.GAME, MonitorProtocol.mapWellbeingCategory("games"))
        assertEquals(WellbeingCategoryFilter.OTHER, MonitorProtocol.mapWellbeingCategory("maps"))

        assertEquals("today", MonitorProtocol.rangeToWire(WellbeingRange.TODAY))
        assertEquals("7d", MonitorProtocol.rangeToWire(WellbeingRange.SEVEN_DAYS))
        assertEquals(WellbeingRange.SEVEN_DAYS, MonitorProtocol.rangeFromWire("7D"))
        assertEquals(WellbeingRange.TODAY, MonitorProtocol.rangeFromWire("today"))
    }
}
