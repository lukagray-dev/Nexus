package com.nexus.child.components.applock

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests for AppLockCommandHandler WebRTC DataChannel message processing.
 *
 * Hey friend! These tests verify:
 * 1. Stream control commands ("APPLOCK_ON" and "APPLOCK_OFF").
 * 2. Remote toggle JSON commands ("APPLOCK_TOGGLE").
 * 3. Remote PIN change JSON commands ("APPLOCK_SET_PIN").
 * 4. Audit history clear commands ("APPLOCK_CLEAR").
 * 5. Rejection of unhandled command strings.
 */
class AppLockCommandHandlerTest {

    private val sentMessages = mutableListOf<String>()

    private class MockAppLockController : IAppLockController {
        var isMonitoring = false
        var recordsCleared = false

        override fun startMonitoring() { isMonitoring = true }
        override fun stopMonitoring() { isMonitoring = false }
        override fun isMonitoringActive(): Boolean = isMonitoring
        override fun recordAttempt(record: AppLockRecord) {}
        override fun getAllRecords(): List<AppLockRecord> = emptyList()
        override fun clearRecords() { recordsCleared = true }
        override fun getStatus(): AppLockStatus = AppLockStatus(
            enabled = true,
            hasPinSet = true,
            failedAttempts = 0,
            isLockedOut = false,
            remainingLockoutSeconds = 0
        )
        override fun cleanup() { isMonitoring = false }
    }

    private lateinit var mockController: MockAppLockController
    private lateinit var commandHandler: AppLockCommandHandler

    @Before
    fun setUp() {
        AppLockManager.testPreferences = FakeSharedPreferences()
        AppLockManager.clearAll(null)
        sentMessages.clear()
        mockController = MockAppLockController()
        commandHandler = AppLockCommandHandler(
            context = null,
            controller = mockController,
            dataSender = { msg ->
                sentMessages.add(msg)
                true
            }
        )
    }

    @After
    fun tearDown() {
        AppLockManager.clearAll(null)
        AppLockManager.testPreferences = null
    }

    @Test
    fun testHandleStreamControlCommands() {
        // APPLOCK_ON
        val handledOn = commandHandler.handleCommand("APPLOCK_ON")
        assertTrue(handledOn)
        assertTrue(mockController.isMonitoring)

        // APPLOCK_OFF
        val handledOff = commandHandler.handleCommand("APPLOCK_OFF")
        assertTrue(handledOff)
        assertFalse(mockController.isMonitoring)
    }

    @Test
    fun testHandleRemoteToggleCommand() {
        val toggleOnCommand = """{"type":"APPLOCK_TOGGLE","enabled":true}"""
        val handledOn = commandHandler.handleCommand(toggleOnCommand)
        assertTrue(handledOn)
        assertTrue(AppLockManager.isAppLockEnabled(null))
        assertTrue(sentMessages.any { it.contains("APPLOCK_STATUS") })

        val toggleOffCommand = """{"type":"APPLOCK_TOGGLE","enabled":false}"""
        val handledOff = commandHandler.handleCommand(toggleOffCommand)
        assertTrue(handledOff)
        assertFalse(AppLockManager.isAppLockEnabled(null))
    }

    @Test
    fun testHandleRemoteSetPinCommand() {
        // Valid 6-digit PIN
        val setPinValid = """{"type":"APPLOCK_SET_PIN","pin":"654321"}"""
        val handledValid = commandHandler.handleCommand(setPinValid)
        assertTrue(handledValid)
        assertTrue(AppLockManager.hasPinSet(null))
        assertTrue(AppLockManager.verifyPin(null, "654321"))
        assertTrue(sentMessages.any { it.contains("APPLOCK_PIN_CHANGED:SUCCESS") })

        // Invalid 5-digit PIN
        val setPinInvalid = """{"type":"APPLOCK_SET_PIN","pin":"12345"}"""
        val handledInvalid = commandHandler.handleCommand(setPinInvalid)
        assertTrue(handledInvalid)
        assertTrue(sentMessages.any { it.contains("APPLOCK_PIN_CHANGED:ERROR") })
    }

    @Test
    fun testHandleClearCommand() {
        val handled = commandHandler.handleCommand("APPLOCK_CLEAR")
        assertTrue(handled)
        assertTrue(mockController.recordsCleared)
        assertTrue(sentMessages.contains("APPLOCK_CLEARED_ACK"))
    }

    @Test
    fun testIgnoresUnrelatedCommands() {
        assertFalse(commandHandler.handleCommand("LOCATION_START"))
        assertFalse(commandHandler.handleCommand("UNKNOWN_COMMAND"))
    }
}
