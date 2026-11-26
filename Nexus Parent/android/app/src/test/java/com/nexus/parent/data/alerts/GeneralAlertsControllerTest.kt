package com.nexus.parent.data.alerts

import com.nexus.parent.data.mic.CallStatePacket
import com.nexus.parent.data.mic.MicProtocol
import com.nexus.parent.state.AppLockAttemptItem
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import com.nexus.parent.state.SettingsValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GeneralAlertsControllerTest.kt - Verifies low-battery hysteresis, disconnect alerts,
 * CALL_ALERT_ON/OFF child sync, and CALL_STATE suppression during live Mic monitoring.
 */
class GeneralAlertsControllerTest {

    private class FakeDispatcher : SafetyAlertDispatcher {
        val toasts = mutableListOf<String>()
        val notifications = mutableListOf<Triple<Int, String, Boolean>>()
        val cancelledIds = mutableListOf<Int>()

        override fun showToast(message: String) {
            toasts += message
        }

        override fun postNotification(
            notificationId: Int,
            title: String,
            body: String,
            onlyAlertOnce: Boolean
        ) {
            notifications += Triple(notificationId, title, onlyAlertOnce)
        }

        override fun cancelNotification(notificationId: Int) {
            cancelledIds += notificationId
        }
    }

    private fun sampleDevice(percent: Int, isCharging: Boolean = false): DeviceRecord {
        return DeviceRecord(
            rawId = "482194021104",
            formattedId = "4821 9402 1104",
            name = "Child Pixel",
            model = "Pixel 8",
            lastSyncedLabel = "Just now",
            telemetry = DeviceTelemetry(batteryPercent = percent, isCharging = isCharging)
        )
    }

    @Test
    fun `low battery alert fires once below 15 percent and resets cleanly at 20 percent or charging`() {
        val dispatcher = FakeDispatcher()
        val controller = GeneralAlertsController(dispatcher)
        val enabled = SettingsValues(alertLowBattery = true)

        assertFalse(controller.evaluateLowBattery(sampleDevice(16), enabled))
        assertTrue(controller.evaluateLowBattery(sampleDevice(14), enabled))
        assertEquals(1, dispatcher.notifications.size)
        assertEquals(GeneralAlertsController.ALERT_ID_LOW_BATTERY, dispatcher.notifications.first().first)

        // Subsequent drops stay deduplicated until recovery >= 20%
        assertFalse(controller.evaluateLowBattery(sampleDevice(13), enabled))
        assertEquals(1, dispatcher.notifications.size)

        // Charging resets the latch and clears notification
        assertFalse(controller.evaluateLowBattery(sampleDevice(13, isCharging = true), enabled))
        assertTrue(GeneralAlertsController.ALERT_ID_LOW_BATTERY in dispatcher.cancelledIds)

        // Dropping again while unplugged triggers cleanly; disabled toggle suppresses alert
        assertFalse(controller.evaluateLowBattery(sampleDevice(12), SettingsValues(alertLowBattery = false)))
        assertTrue(controller.evaluateLowBattery(sampleDevice(12), enabled))
        assertEquals(2, dispatcher.notifications.size)
    }

    @Test
    fun `active call alert syncs command with child, suppresses while mic active, and clears on idle`() {
        val dispatcher = FakeDispatcher()
        val controller = GeneralAlertsController(dispatcher)
        val sentCommands = mutableListOf<String>()
        val send: (String) -> Boolean = { cmd -> sentCommands += cmd; true }

        assertTrue(controller.syncCallAlertSettingToChild(true, send, force = true))
        assertEquals(listOf(MicProtocol.CMD_CALL_ALERT_ON), sentCommands)

        // Duplicate enabled sync is skipped cleanly unless forced
        assertTrue(controller.syncCallAlertSettingToChild(true, send, force = false))
        assertEquals(1, sentCommands.size)

        val packet = CallStatePacket(
            state = "ringing",
            direction = "incoming",
            number = "+15550100",
            contactName = "Mom",
            timestampMs = 1000L,
            isRepeat = false
        )

        val alert = controller.evaluateCallState(
            packet = packet,
            settings = SettingsValues(alertChildActiveCall = true),
            isMicListeningOrStarting = false,
            deviceName = "Child Pixel"
        )
        assertNotNull(alert)
        assertEquals(1, dispatcher.notifications.size)
        assertFalse(dispatcher.notifications.first().third)

        // Suppressed when Mic monitoring is already live or starting
        val suppressed = controller.evaluateCallState(
            packet = packet.copy(isRepeat = true),
            settings = SettingsValues(alertChildActiveCall = true),
            isMicListeningOrStarting = true,
            deviceName = "Child Pixel"
        )
        assertNull(suppressed)
        assertTrue(GeneralAlertsController.ALERT_ID_ACTIVE_CALL in dispatcher.cancelledIds)

        // Toggling off sends CALL_ALERT_OFF and clears active notification
        assertTrue(controller.syncCallAlertSettingToChild(false, send, force = false))
        assertEquals(listOf(MicProtocol.CMD_CALL_ALERT_ON, MicProtocol.CMD_CALL_ALERT_OFF), sentCommands)
    }

    @Test
    fun `unexpected disconnect alert fires toast and notification only when enabled and resets battery latch`() {
        val dispatcher = FakeDispatcher()
        val controller = GeneralAlertsController(dispatcher)
        val device = sampleDevice(10)

        assertTrue(controller.evaluateLowBattery(device, SettingsValues(alertLowBattery = true)))
        assertFalse(controller.evaluateUnexpectedDisconnect(device, SettingsValues(alertChildDisconnected = false)))
        assertTrue(controller.evaluateUnexpectedDisconnect(device, SettingsValues(alertChildDisconnected = true)))
        assertEquals(2, dispatcher.notifications.size)
        assertEquals(GeneralAlertsController.ALERT_ID_DISCONNECTED, dispatcher.notifications.last().first)

        // Latch was reset on disconnect, so reconnecting at 10% can alert again
        assertTrue(controller.evaluateLowBattery(device, SettingsValues(alertLowBattery = true)))
    }

    @Test
    fun `intruder selfie alert fires only on wrong PIN attempts when enabled`() {
        val dispatcher = FakeDispatcher()
        val controller = GeneralAlertsController(dispatcher)

        val validAttempt = AppLockAttemptItem(
            id = "a1",
            timestampLabel = "10:15 AM",
            pinCorrect = true,
            isLockedOut = false,
            failedAttempts = 0,
            hasSelfiePreview = false
        )
        assertFalse(controller.evaluateIntruderSelfie(validAttempt, SettingsValues(alertIntruderSelfie = true), "Child Pixel"))

        val wrongPinAttempt = validAttempt.copy(
            id = "a2",
            pinCorrect = false,
            failedAttempts = 1,
            hasSelfiePreview = true
        )
        assertFalse(controller.evaluateIntruderSelfie(wrongPinAttempt, SettingsValues(alertIntruderSelfie = false), "Child Pixel"))
        assertTrue(controller.evaluateIntruderSelfie(wrongPinAttempt, SettingsValues(alertIntruderSelfie = true), "Child Pixel"))
        assertEquals(1, dispatcher.notifications.size)
        assertEquals(GeneralAlertsController.ALERT_ID_INTRUDER_SELFIE, dispatcher.notifications.first().first)
    }
}
