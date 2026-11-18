package com.nexus.child.components.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying SMS DataChannel protocol, command-and-control state machine,
 * chunked history dispatching, live SMS events, and JSON serialization.
 *
 * Hey friend! These unit tests verify:
 * 1. Permission security enforcement: SMS_ON fails immediately if runtime permission is absent.
 * 2. Successful SMS_ON handshake: activates observer, sends SMS_STARTED, and dispatches chunked SMS_HISTORY.
 * 3. Chunking logic: ensures large batches are partitioned to prevent DataChannel buffer overflows.
 * 4. Successful SMS_OFF handshake: halts observer and responds with SMS_STOPPED.
 * 5. Backward-compatible alias commands: SMS_START and SMS_STOP.
 * 6. Real-time live SMS event dispatching: SMS_NEW payload contains all metadata.
 * 7. Disconnection fail-safe: peer disconnection triggers immediate observer shutdown.
 * 8. JSON serialization: ensures address, senderName, body, smsType, timestamp, dateSent,
 *    read, and simId conform to the desktop parent contract.
 */
class SmsComponentTest {

    private class FakeSmsController(
        var hasPermission: Boolean = true,
        var isMonitoringActive: Boolean = false,
        var startResult: Boolean = true,
        var mockSmsRecords: List<SmsRecord> = emptyList()
    ) : ISmsController {

        var startCalled = false
        var stopCalled = false
        var cleanupCalled = false
        var lastCallback: ((SmsRecord) -> Unit)? = null

        override fun hasSmsPermission(): Boolean = hasPermission

        override fun isMonitoring(): Boolean = isMonitoringActive

        override fun fetchLatestSms(limit: Int): List<SmsRecord> = mockSmsRecords.take(limit)

        override fun startSmsMonitoring(onNewSms: (SmsRecord) -> Unit): Boolean {
            startCalled = true
            lastCallback = onNewSms
            if (startResult) {
                isMonitoringActive = true
            }
            return startResult
        }

        override fun stopSmsMonitoring(): Boolean {
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

        fun simulateLiveSms(record: SmsRecord) {
            lastCallback?.invoke(record)
        }
    }

    private fun createSampleSmsRecord(
        id: String = "501",
        address: String = "+15553456789",
        senderName: String = "Mom (Sarah)",
        body: String = "Don't forget your lunch box today! Love you.",
        smsType: String = "inbox",
        timestamp: Long = 1731938450000L,
        dateSent: Long = 1731938445000L,
        read: Boolean = true,
        simId: String = "SIM 1"
    ): SmsRecord {
        return SmsRecord(
            id = id,
            address = address,
            senderName = senderName,
            body = body,
            smsType = smsType,
            timestamp = timestamp,
            dateSent = dateSent,
            read = read,
            simId = simId
        )
    }

    @Test
    fun testSmsOnWithoutPermissionSendsError() {
        val fakeController = FakeSmsController(hasPermission = false)
        val sentMessages = mutableListOf<String>()
        val handler = SmsCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(SmsCommandHandler.CMD_SMS_ON)
        assertTrue(handled)
        assertEquals(1, sentMessages.size)
        assertTrue(sentMessages[0].startsWith(SmsCommandHandler.RESP_SMS_ERROR_PREFIX))
        assertFalse(fakeController.startCalled)
    }

    @Test
    fun testSmsOnWithPermissionStartsAndDispatchesHistory() {
        val sampleList = listOf(
            createSampleSmsRecord(id = "1", body = "Message 1"),
            createSampleSmsRecord(id = "2", body = "Message 2")
        )
        val fakeController = FakeSmsController(hasPermission = true, mockSmsRecords = sampleList)
        val sentMessages = mutableListOf<String>()
        val handler = SmsCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(SmsCommandHandler.CMD_SMS_ON)
        assertTrue(handled)
        assertTrue(fakeController.startCalled)
        assertTrue(fakeController.isMonitoring())

        // Expect confirmation (SMS_STARTED) + 1 history chunk
        assertEquals(2, sentMessages.size)
        assertEquals(SmsCommandHandler.RESP_SMS_STARTED, sentMessages[0])

        val historyPayload = sentMessages[1]
        assertTrue(historyPayload.contains("\"type\":\"SMS_HISTORY\""))
        assertTrue(historyPayload.contains("\"totalCount\":2"))
        assertTrue(historyPayload.contains("\"totalChunks\":1"))
        assertTrue(historyPayload.contains("\"chunkIndex\":0"))
        assertTrue(historyPayload.contains("\"body\":\"Message 1\""))
        assertTrue(historyPayload.contains("\"body\":\"Message 2\""))
    }

    @Test
    fun testSmsHistoryChunkingOverFiftyRecords() {
        val largeList = (1..65).map { i ->
            createSampleSmsRecord(id = "$i", body = "Message $i")
        }
        val fakeController = FakeSmsController(hasPermission = true, mockSmsRecords = largeList)
        val sentMessages = mutableListOf<String>()
        val handler = SmsCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        handler.handleCommand(SmsCommandHandler.CMD_SMS_ON)

        // Expect 1 confirmation + 2 chunks (50 + 15)
        assertEquals(3, sentMessages.size)
        assertEquals(SmsCommandHandler.RESP_SMS_STARTED, sentMessages[0])

        val chunk1 = sentMessages[1]
        assertTrue(chunk1.contains("\"chunkIndex\":0"))
        assertTrue(chunk1.contains("\"totalChunks\":2"))
        assertTrue(chunk1.contains("\"count\":50"))

        val chunk2 = sentMessages[2]
        assertTrue(chunk2.contains("\"chunkIndex\":1"))
        assertTrue(chunk2.contains("\"totalChunks\":2"))
        assertTrue(chunk2.contains("\"count\":15"))
    }

    @Test
    fun testSmsOffStopsMonitoringAndSendsConfirmation() {
        val fakeController = FakeSmsController(hasPermission = true, isMonitoringActive = true)
        val sentMessages = mutableListOf<String>()
        val handler = SmsCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        val handled = handler.handleCommand(SmsCommandHandler.CMD_SMS_OFF)
        assertTrue(handled)
        assertTrue(fakeController.stopCalled)
        assertFalse(fakeController.isMonitoring())
        assertEquals(1, sentMessages.size)
        assertEquals(SmsCommandHandler.RESP_SMS_STOPPED, sentMessages[0])
    }

    @Test
    fun testAliasesAreHandledProperly() {
        val fakeController = FakeSmsController(hasPermission = true)
        val sentMessages = mutableListOf<String>()
        val handler = SmsCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        assertTrue(handler.handleCommand(SmsCommandHandler.ALIAS_SMS_START))
        assertTrue(handler.handleCommand(SmsCommandHandler.ALIAS_SMS_STOP))
    }

    @Test
    fun testLiveSmsEventDispatchesNewSmsJson() {
        val fakeController = FakeSmsController(hasPermission = true)
        val sentMessages = mutableListOf<String>()
        val handler = SmsCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        handler.handleCommand(SmsCommandHandler.CMD_SMS_ON)
        sentMessages.clear()

        // Simulate live incoming SMS
        val liveSms = createSampleSmsRecord(
            id = "777",
            address = "+19998887777",
            senderName = "School Alert",
            body = "Early release today at 1:30 PM",
            smsType = "inbox"
        )
        fakeController.simulateLiveSms(liveSms)

        assertEquals(1, sentMessages.size)
        val newSmsJson = sentMessages[0]
        assertTrue(newSmsJson.contains("\"type\":\"SMS_NEW\""))
        assertTrue(newSmsJson.contains("\"id\":\"777\""))
        assertTrue(newSmsJson.contains("\"address\":\"+19998887777\""))
        assertTrue(newSmsJson.contains("\"senderName\":\"School Alert\""))
        assertTrue(newSmsJson.contains("\"body\":\"Early release today at 1:30 PM\""))
        assertTrue(newSmsJson.contains("\"smsType\":\"inbox\""))
    }

    @Test
    fun testPeerDisconnectedHaltsMonitoring() {
        val fakeController = FakeSmsController(hasPermission = true, isMonitoringActive = true)
        val handler = SmsCommandHandler(fakeController) { true }

        handler.onPeerDisconnected()
        assertTrue(fakeController.stopCalled)
        assertFalse(fakeController.isMonitoring())
    }

    @Test
    fun testSerializationConformsToContract() {
        val fakeController = FakeSmsController()
        val handler = SmsCommandHandler(fakeController) { true }

        val record = createSampleSmsRecord(
            id = "88",
            address = "+1234567890",
            senderName = "Bank Verification",
            body = "Your one-time passcode is 839201.",
            smsType = "inbox",
            timestamp = 1731938600000L,
            dateSent = 1731938590000L,
            read = false,
            simId = "SIM 1"
        )

        val jsonStr = handler.serializeSmsRecord(record)
        assertTrue(jsonStr.contains("\"id\":\"88\""))
        assertTrue(jsonStr.contains("\"address\":\"+1234567890\""))
        assertTrue(jsonStr.contains("\"senderName\":\"Bank Verification\""))
        assertTrue(jsonStr.contains("\"body\":\"Your one-time passcode is 839201.\""))
        assertTrue(jsonStr.contains("\"smsType\":\"inbox\""))
        assertTrue(jsonStr.contains("\"timestamp\":1731938600000"))
        assertTrue(jsonStr.contains("\"dateSent\":1731938590000"))
        assertTrue(jsonStr.contains("\"read\":false"))
        assertTrue(jsonStr.contains("\"simId\":\"SIM 1\""))
    }
}
