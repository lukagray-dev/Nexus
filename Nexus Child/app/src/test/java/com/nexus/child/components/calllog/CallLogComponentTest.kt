package com.nexus.child.components.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying CallLog DataChannel protocol, command-and-control state machine,
 * initial 100-history batch dispatching, live call events, and JSON serialization.
 *
 * Hey friend! These unit tests verify:
 * 1. Permission security enforcement: CALLLOG_ON fails immediately if runtime permission is absent.
 * 2. Successful CALLLOG_ON handshake: activates observer, sends CALLLOG_STARTED, and dispatches CALLLOG_HISTORY.
 * 3. Successful CALLLOG_OFF handshake: halts observer and responds with CALLLOG_STOPPED.
 * 4. Backward-compatible alias commands: CALLLOG_START and CALLLOG_STOP.
 * 5. Real-time live call event dispatching: CALLLOG_NEW payload contains all metadata.
 * 6. Disconnection fail-safe: peer disconnection triggers immediate observer shutdown.
 * 7. JSON serialization: ensures name, number, numberType, callType, duration, timestamp,
 *    location, simId, features, and isRead conform to the desktop parent contract.
 */
class CallLogComponentTest {

    private class FakeCallLogController(
        var hasPermission: Boolean = true,
        var isMonitoringActive: Boolean = false,
        var startResult: Boolean = true,
        var mockCallRecords: List<CallRecord> = emptyList()
    ) : ICallLogController {

        var startCalled = false
        var stopCalled = false
        var cleanupCalled = false
        var lastCallback: ((CallRecord) -> Unit)? = null

        override fun hasCallLogPermission(): Boolean = hasPermission

        override fun isMonitoring(): Boolean = isMonitoringActive

        override fun fetchLatestCallLogs(limit: Int): List<CallRecord> = mockCallRecords.take(limit)

        override fun startCallLogMonitoring(onNewCall: (CallRecord) -> Unit): Boolean {
            startCalled = true
            lastCallback = onNewCall
            if (startResult) {
                isMonitoringActive = true
            }
            return startResult
        }

        override fun stopCallLogMonitoring(): Boolean {
            stopCalled = true
            val wasMonitoring = isMonitoringActive
            isMonitoringActive = false
            lastCallback = null
            return wasMonitoring
        }

        override fun cleanup() {
            cleanupCalled = true
            isMonitoringActive = false
            lastCallback = null
        }

        fun simulateLiveCall(record: CallRecord) {
            lastCallback?.invoke(record)
        }
    }

    private fun createSampleCallRecord(
        id: String = "101",
        number: String = "+15552345678",
        name: String = "Sarah (Mom)",
        numberType: String = "Mobile",
        callType: String = "incoming",
        duration: Long = 180L,
        timestamp: Long = 1731938400000L,
        location: String = "Chicago, IL",
        simId: String = "SIM 1",
        features: List<String> = listOf("HD_VOICE", "VIDEO"),
        isRead: Boolean = true
    ): CallRecord {
        return CallRecord(
            id = id,
            number = number,
            name = name,
            numberType = numberType,
            callType = callType,
            duration = duration,
            timestamp = timestamp,
            location = location,
            simId = simId,
            features = features,
            isRead = isRead
        )
    }

    @Test
    fun testCallLogOnWithoutPermissionSendsError() {
        val fakeController = FakeCallLogController(hasPermission = false)
        val sentMessages = mutableListOf<String>()
        val handler = CallLogCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(CallLogCommandHandler.CMD_CALLLOG_ON)
        assertTrue(handled)
        assertEquals(1, sentMessages.size)
        assertTrue(sentMessages[0].startsWith(CallLogCommandHandler.RESP_CALLLOG_ERROR_PREFIX))
        assertFalse(fakeController.startCalled)
    }

    @Test
    fun testCallLogOnWithPermissionStartsAndDispatchesHistory() {
        val sampleList = listOf(
            createSampleCallRecord(id = "1", number = "+111111", name = "Dad"),
            createSampleCallRecord(id = "2", number = "+222222", name = "Mom")
        )
        val fakeController = FakeCallLogController(hasPermission = true, mockCallRecords = sampleList)
        val sentMessages = mutableListOf<String>()
        val handler = CallLogCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(CallLogCommandHandler.CMD_CALLLOG_ON)
        assertTrue(handled)
        assertTrue(fakeController.startCalled)
        assertTrue(fakeController.isMonitoring())

        // Expect 2 messages: confirmation (CALLLOG_STARTED) and batch (CALLLOG_HISTORY)
        assertEquals(2, sentMessages.size)
        assertEquals(CallLogCommandHandler.RESP_CALLLOG_STARTED, sentMessages[0])

        val historyPayload = sentMessages[1]
        assertTrue(historyPayload.contains("\"type\":\"CALLLOG_HISTORY\""))
        assertTrue(historyPayload.contains("\"count\":2"))
        assertTrue(historyPayload.contains("\"name\":\"Dad\""))
        assertTrue(historyPayload.contains("\"name\":\"Mom\""))
    }

    @Test
    fun testCallLogOffStopsMonitoringAndSendsConfirmation() {
        val fakeController = FakeCallLogController(hasPermission = true, isMonitoringActive = true)
        val sentMessages = mutableListOf<String>()
        val handler = CallLogCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(CallLogCommandHandler.CMD_CALLLOG_OFF)
        assertTrue(handled)
        assertTrue(fakeController.stopCalled)
        assertFalse(fakeController.isMonitoring())
        assertEquals(1, sentMessages.size)
        assertEquals(CallLogCommandHandler.RESP_CALLLOG_STOPPED, sentMessages[0])
    }

    @Test
    fun testAliasesAreHandledProperly() {
        val fakeController = FakeCallLogController(hasPermission = true)
        val sentMessages = mutableListOf<String>()
        val handler = CallLogCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        assertTrue(handler.handleCommand(CallLogCommandHandler.ALIAS_CALLLOG_START))
        assertTrue(handler.handleCommand(CallLogCommandHandler.ALIAS_CALLLOG_STOP))
    }

    @Test
    fun testLiveCallEventDispatchesNewCallJson() {
        val fakeController = FakeCallLogController(hasPermission = true)
        val sentMessages = mutableListOf<String>()
        val handler = CallLogCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        handler.handleCommand(CallLogCommandHandler.CMD_CALLLOG_ON)
        sentMessages.clear()

        // Simulate live incoming call
        val liveCall = createSampleCallRecord(
            id = "999",
            number = "+18005550199",
            name = "Emergency Services",
            callType = "missed",
            duration = 0L
        )
        fakeController.simulateLiveCall(liveCall)

        assertEquals(1, sentMessages.size)
        val newCallJson = sentMessages[0]
        assertTrue(newCallJson.contains("\"type\":\"CALLLOG_NEW\""))
        assertTrue(newCallJson.contains("\"id\":\"999\""))
        assertTrue(newCallJson.contains("\"number\":\"+18005550199\""))
        assertTrue(newCallJson.contains("\"name\":\"Emergency Services\""))
        assertTrue(newCallJson.contains("\"callType\":\"missed\""))
        assertTrue(newCallJson.contains("\"duration\":0"))
    }

    @Test
    fun testPeerDisconnectedHaltsMonitoring() {
        val fakeController = FakeCallLogController(hasPermission = true, isMonitoringActive = true)
        val handler = CallLogCommandHandler(fakeController) { true }

        handler.onPeerDisconnected()
        assertTrue(fakeController.stopCalled)
        assertFalse(fakeController.isMonitoring())
    }

    @Test
    fun testSerializationConformsToContract() {
        val fakeController = FakeCallLogController()
        val handler = CallLogCommandHandler(fakeController) { true }

        val record = createSampleCallRecord(
            id = "42",
            number = "+1 (555) 000-1111",
            name = "Alex Johnson",
            numberType = "Work",
            callType = "outgoing",
            duration = 125L,
            timestamp = 1731938500000L,
            location = "New York, NY",
            simId = "SIM 2",
            features = listOf("HD_VOICE"),
            isRead = true
        )

        val jsonStr = handler.serializeCallRecord(record)
        assertTrue(jsonStr.contains("\"id\":\"42\""))
        assertTrue(jsonStr.contains("\"number\":\"+1 (555) 000-1111\""))
        assertTrue(jsonStr.contains("\"name\":\"Alex Johnson\""))
        assertTrue(jsonStr.contains("\"numberType\":\"Work\""))
        assertTrue(jsonStr.contains("\"callType\":\"outgoing\""))
        assertTrue(jsonStr.contains("\"duration\":125"))
        assertTrue(jsonStr.contains("\"timestamp\":1731938500000"))
        assertTrue(jsonStr.contains("\"location\":\"New York, NY\""))
        assertTrue(jsonStr.contains("\"simId\":\"SIM 2\""))
        assertTrue(jsonStr.contains("\"features\":[\"HD_VOICE\"]"))
        assertTrue(jsonStr.contains("\"isRead\":true"))
    }
}
