package com.nexus.parent.data.monitor

import com.nexus.parent.state.CallFilter
import com.nexus.parent.state.NotificationFilter
import com.nexus.parent.state.WellbeingCategoryFilter
import com.nexus.parent.state.WellbeingRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MonitorPayloadParserTest.kt - Verifies all DataChannel wire responses and JSON payloads
 * across all 8 monitoring features.
 */
class MonitorPayloadParserTest {

    @Test
    fun `parses plain ack and error tokens accurately`() {
        assertEquals(MonitorDataEvent.SmsStarted, MonitorPayloadParser.parse("SMS_STARTED"))
        assertEquals(MonitorDataEvent.SmsStopped, MonitorPayloadParser.parse("SMS_STOPPED"))
        assertEquals(
            MonitorDataEvent.WellbeingError("Usage Access permission not granted"),
            MonitorPayloadParser.parse("WELLBEING_ERROR: Usage Access permission not granted")
        )
        assertEquals(
            MonitorDataEvent.AppLockPinChanged(true, "PIN updated successfully on device"),
            MonitorPayloadParser.parse("APPLOCK_PIN_CHANGED:SUCCESS")
        )
        assertEquals(
            MonitorDataEvent.AppLockPinChanged(false, "PIN must be 6 digits"),
            MonitorPayloadParser.parse("APPLOCK_PIN_CHANGED:ERROR:PIN must be 6 digits")
        )
        assertEquals(
            MonitorDataEvent.VibrateResult(true, "sos"),
            MonitorPayloadParser.parse("VIBRATE_SUCCESS: sos")
        )
        assertEquals(
            MonitorDataEvent.FlashResult(false, "Camera flash unavailable"),
            MonitorPayloadParser.parse("FLASH_ERROR: Camera flash unavailable")
        )
        assertEquals(
            MonitorDataEvent.WallpaperSetSuccess,
            MonitorPayloadParser.parse("WALLPAPER_SET_SUCCESS")
        )
        assertNull(MonitorPayloadParser.parse("UNKNOWN_SIGNAL"))
    }

    @Test
    fun `parses SMS, CallLog, Notification, and Keyboard JSON payloads`() {
        val now = 1_700_000_100_000L
        val sms = MonitorPayloadParser.parse(
            """{"type":"SMS_HISTORY","chunkIndex":0,"totalChunks":2,"messages":[{"id":"m1","address":"+15551234","senderName":"Mom","body":"Home soon?","smsType":"inbox","timestamp":1700000095000,"read":false,"simId":"0"}]}""",
            nowMs = now
        ) as MonitorDataEvent.SmsHistoryChunk
        assertEquals(1, sms.messages.size)
        assertTrue(sms.messages[0].isIncoming)
        assertTrue(sms.messages[0].isUnread)
        assertEquals("SIM 1", sms.messages[0].simSlot)

        val calls = MonitorPayloadParser.parse(
            """{"type":"CALLLOG_HISTORY","records":[{"id":"c1","number":"+15559876","name":"","numberType":"Mobile","callType":"rejected","duration":75,"timestamp":1700000050000,"location":"Austin, TX","simId":"2","features":["VIDEO","HD_VOICE"]}]}""",
            nowMs = now
        ) as MonitorDataEvent.CallLogHistory
        assertEquals(1, calls.records.size)
        assertEquals(CallFilter.DECLINED, calls.records[0].filterType)
        assertEquals("01:15", calls.records[0].durationLabel)
        assertEquals("SIM 2", calls.records[0].simSlot)
        assertEquals("Austin, TX", calls.records[0].location)
        assertEquals(listOf("VIDEO", "HD_VOICE"), calls.records[0].features)

        val notif = MonitorPayloadParser.parse(
            """{"type":"NOTIFICATION_NEW","notification":{"key":"k1","packageName":"com.whatsapp","appName":"WhatsApp","title":"Group","text":"Hi","bigText":"Expanded group text","subText":"2 messages","category":"msg","postTime":1700000090000,"messages":[{"sender":"Alex","text":"Ready?"}]}}""",
            nowMs = now
        ) as MonitorDataEvent.NotificationNew
        assertEquals(NotificationFilter.MESSAGES, notif.item.category)
        assertEquals("Expanded group text", notif.item.bigText)
        assertEquals(listOf("Alex: Ready?"), notif.item.messages)

        val kb = MonitorPayloadParser.parse(
            """{"type":"KEYBOARD_NEW","record":{"id":"kb1","app":"com.android.chrome","appName":"Chrome","text":"secret123","fieldHint":"Password","inputType":"PASSWORD","isPassword":true,"timestamp":1700000095000}}""",
            nowMs = now
        ) as MonitorDataEvent.KeyboardNew
        assertTrue(kb.record.isPassword)
        assertEquals("Chrome", kb.record.appName)
    }

    @Test
    fun `parses Wellbeing, AppLock, and Wallpaper payloads accurately`() {
        val wb = MonitorPayloadParser.parse(
            """{"type":"WELLBEING_SNAPSHOT","range":"7d","chunkIndex":0,"totalChunks":2,"totalScreenTimeMs":3660000,"unlockCount":18,"totalLaunches":42,"activeAppName":"YouTube","buckets":[{"label":"Mon","durationMs":1800000}],"apps":[{"packageName":"com.google.android.youtube","appName":"YouTube","category":"video","totalTimeMs":2400000,"launchCount":12,"percent":66}]}"""
        ) as MonitorDataEvent.WellbeingSnapshotChunk
        assertEquals(WellbeingRange.SEVEN_DAYS, wb.range)
        assertEquals(2, wb.totalChunks)
        assertEquals(18, wb.unlockCount)
        assertEquals(WellbeingCategoryFilter.VIDEO, wb.apps[0].category)
        assertEquals("40m", wb.apps[0].durationLabel)

        val lockStatus = MonitorPayloadParser.parse(
            """{"type":"APPLOCK_STATUS","enabled":true,"isLockedOut":false}"""
        ) as MonitorDataEvent.AppLockStatus
        assertTrue(lockStatus.enabled)
        assertFalse(lockStatus.isLockedOut)

        val lockSnap = MonitorPayloadParser.parse(
            """{"type":"APPLOCK_SNAPSHOT","chunkIndex":0,"totalChunks":1,"records":[{"id":"a1","timestamp":1700000000000,"pinCorrect":false,"failedAttempts":3,"isLockedOut":true,"selfie":"AQID"}]}"""
        ) as MonitorDataEvent.AppLockSnapshotChunk
        assertEquals(1, lockSnap.records.size)
        assertTrue(lockSnap.records[0].isLockedOut)
        assertTrue(lockSnap.records[0].hasSelfiePreview)
        assertEquals("AQID", lockSnap.records[0].selfieBase64)

        val wp = MonitorPayloadParser.parse(
            """{"type":"WALLPAPER_CHUNK","screen":"lock","chunkIndex":0,"totalChunks":2,"data":"AAAA","isSame":false}"""
        ) as MonitorDataEvent.WallpaperChunk
        assertEquals("lock", wp.screen)
        assertFalse(wp.isSame)
        assertNotNull(wp.data)
    }
}
