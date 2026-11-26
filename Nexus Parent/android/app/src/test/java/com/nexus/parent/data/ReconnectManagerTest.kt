package com.nexus.parent.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReconnectManagerTest.kt - Verifies backoff schedule ticks, retryNow(), cancel(), and exhaustion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectManagerTest {

    private val sampleTarget = ReconnectTargetDevice(
        rawId = "482194021104",
        formattedId = "4821 9402 1104",
        name = "Test Kid",
        model = "Pixel 8"
    )

    @Test
    fun `counts down, invokes attempts across schedule, and fires onExhausted after final failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = TestScope(dispatcher)
        val manager = ReconnectManager(
            scope = managerScope,
            delaysSeconds = listOf(2, 3),
            maxAttempts = 2,
            tickIntervalMs = 1000L
        )

        var attemptsCount = 0
        var exhaustedTarget: ReconnectTargetDevice? = null
        manager.setConnectHandler {
            attemptsCount += 1
            false
        }
        manager.setCallbacks(
            onCancelled = {},
            onExhausted = { exhaustedTarget = it },
            onSucceeded = {}
        )

        manager.start(sampleTarget)
        managerScope.runCurrent()

        assertTrue(manager.isReconnecting())
        assertEquals(ReconnectStatus.WAITING, manager.state.value.status)
        assertEquals(1, manager.state.value.currentAttempt)
        assertEquals(2, manager.state.value.secondsRemaining)

        // Advance through attempt 1 (2s) and attempt 2 (3s)
        managerScope.advanceTimeBy(6000L)
        managerScope.runCurrent()

        assertEquals(2, attemptsCount)
        assertFalse(manager.isReconnecting())
        assertEquals(sampleTarget.rawId, exhaustedTarget?.rawId)
    }

    @Test
    fun `retryNow immediately runs attempt and emits onSucceeded on true`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = TestScope(dispatcher)
        val manager = ReconnectManager(
            scope = managerScope,
            delaysSeconds = listOf(10, 30),
            maxAttempts = 2,
            tickIntervalMs = 1000L
        )

        var succeededTarget: ReconnectTargetDevice? = null
        manager.setConnectHandler { true }
        manager.setCallbacks(
            onCancelled = {},
            onExhausted = {},
            onSucceeded = { succeededTarget = it }
        )

        manager.start(sampleTarget)
        managerScope.runCurrent()
        assertEquals(10, manager.state.value.secondsRemaining)

        manager.retryNow()
        managerScope.runCurrent()

        assertFalse(manager.isReconnecting())
        assertEquals(sampleTarget.rawId, succeededTarget?.rawId)
    }

    @Test
    fun `cancel aborts waiting loop and triggers onCancelled callback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = TestScope(dispatcher)
        val manager = ReconnectManager(
            scope = managerScope,
            delaysSeconds = listOf(10, 30),
            maxAttempts = 2,
            tickIntervalMs = 1000L
        )

        var cancelled = false
        var attempts = 0
        manager.setConnectHandler {
            attempts += 1
            true
        }
        manager.setCallbacks(
            onCancelled = { cancelled = true },
            onExhausted = {},
            onSucceeded = {}
        )

        manager.start(sampleTarget)
        managerScope.runCurrent()
        manager.cancel(silent = false)
        managerScope.advanceTimeBy(15_000L)

        assertTrue(cancelled)
        assertFalse(manager.isReconnecting())
        assertEquals(0, attempts)
    }
}
