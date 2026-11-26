package com.nexus.child.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.SessionDescription

/**
 * Unit tests verifying WebRTC configuration constants, ID formatting utilities,
 * and SessionDescription serialization logic.
 *
 * Hey friend! We test these components to guarantee that:
 * 1. Device ID formatting behaves deterministically for UI display.
 * 2. WebRTC SessionDescriptions serialize and deserialize without loss of SDP data.
 * 3. Configuration endpoints conform to expected URI schemes.
 */
class WebRtcProtocolTest {

    @Test
    fun testDeviceIdFormatWithValid12Digits() {
        val rawId = "482194021104"
        val formatted = DeviceIdManager.format(rawId)
        assertEquals("4821 9402 1104", formatted)
    }

    @Test
    fun testDeviceIdFormatWithDashesOrSpaces() {
        val messyId = "4821-9402-1104"
        val formatted = DeviceIdManager.format(messyId)
        assertEquals("4821 9402 1104", formatted)
    }

    @Test
    fun testDeviceIdFormatWithShortStringReturnsOriginal() {
        val shortId = "12345"
        val formatted = DeviceIdManager.format(shortId)
        assertEquals("12345", formatted)
    }

    @Test
    fun testSessionDescriptionSerializationAndDeserialization() {
        val sampleSdp = "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=sendrecv\r\n"
        val originalOffer = SessionDescription(SessionDescription.Type.OFFER, sampleSdp)

        // Serialize to Map
        val serializedMap = originalOffer.serialize()
        assertEquals("offer", serializedMap["type"])
        assertEquals(sampleSdp, serializedMap["sdp"])

        // Deserialize back to SessionDescription
        val reconstructed = serializedMap.toSessionDescription()
        assertEquals(SessionDescription.Type.OFFER, reconstructed.type)
        assertEquals(sampleSdp, reconstructed.description)
    }

    @Test
    fun testSessionDescriptionAnswerSerialization() {
        val sampleSdp = "v=0\r\no=- 654321 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=recvonly\r\n"
        val originalAnswer = SessionDescription(SessionDescription.Type.ANSWER, sampleSdp)

        val serializedMap = originalAnswer.serialize()
        assertEquals("answer", serializedMap["type"])
        assertEquals(sampleSdp, serializedMap["sdp"])

        val reconstructed = serializedMap.toSessionDescription()
        assertEquals(SessionDescription.Type.ANSWER, reconstructed.type)
        assertEquals(sampleSdp, reconstructed.description)
    }

    @Test
    fun testAppConfigContainsValidStunServers() {
        val stunServers = AppConfig.WebRtc.STUN_SERVERS
        assertTrue("STUN server list must not be empty", stunServers.isNotEmpty())

        for (server in stunServers) {
            assertTrue(
                "STUN server must start with 'stun:' scheme: $server",
                server.startsWith("stun:")
            )
        }
    }

    @Test
    fun testAppConfigContainsValidFirebaseEndpoints() {
        val databaseUrl = AppConfig.Firebase.DATABASE_URL
        assertNotNull(databaseUrl)
        assertTrue(
            "Firebase database URL must start with https://",
            databaseUrl.startsWith("https://")
        )

        val apiKey = AppConfig.Firebase.API_KEY
        assertTrue("Firebase API key must not be blank", apiKey.isNotBlank())
    }

    @Test
    fun testTelemetrySnapshotSerialization() {
        val snapshot = TelemetrySnapshot(
            batteryPercent = 88,
            isCharging = true,
            networkState = "WiFi",
            deviceName = "Lucas Galaxy S24",
            model = "SM-S928B",
            manufacturer = "Samsung",
            androidVersion = "14",
            freeStorageBytes = 64424509440L,
            totalStorageBytes = 256000000000L,
            sdkInt = 34,
            brand = "samsung",
            appVersion = "0.1.0-alpha",
            chargingSource = "AC",
            batteryTemperatureC = 32.4,
            isPowerSaveMode = false,
            batteryOptimizationIgnored = true,
            totalRamBytes = 8589934592L,
            availableRamBytes = 4294967296L,
            isScreenOn = true,
            screenResolution = "1080x2340",
            ringerMode = "Vibrate",
            simCarrier = "SIM 1 • Jio",
            deviceUptimeMs = 5400000L,
            isStealthMode = false,
            isAppLockEnabled = true,
            grantedPermissionsCount = 13,
            totalPermissionsCount = 14
        )
        val jsonStr = snapshot.toJsonString()
        assertTrue("JSON must contain DEVICE_TELEMETRY type", jsonStr.contains("\"type\":\"DEVICE_TELEMETRY\""))
        assertTrue("JSON must contain batteryPercent", jsonStr.contains("\"batteryPercent\":88"))
        assertTrue("JSON must contain isCharging", jsonStr.contains("\"isCharging\":true"))
        assertTrue("JSON must contain networkState", jsonStr.contains("\"networkState\":\"WiFi\""))
        assertTrue("JSON must contain deviceName", jsonStr.contains("\"deviceName\":\"Lucas Galaxy S24\""))
        assertTrue("JSON must contain model", jsonStr.contains("\"model\":\"SM-S928B\""))
        assertTrue("JSON must contain manufacturer", jsonStr.contains("\"manufacturer\":\"Samsung\""))
        assertTrue("JSON must contain androidVersion", jsonStr.contains("\"androidVersion\":\"14\""))
        assertTrue("JSON must contain freeStorageBytes", jsonStr.contains("\"freeStorageBytes\":64424509440"))
        assertTrue("JSON must contain totalStorageBytes", jsonStr.contains("\"totalStorageBytes\":256000000000"))
        assertTrue("JSON must contain sdkInt", jsonStr.contains("\"sdkInt\":34"))
        assertTrue("JSON must contain brand", jsonStr.contains("\"brand\":\"samsung\""))
        assertTrue("JSON must contain appVersion", jsonStr.contains("\"appVersion\":\"0.1.0-alpha\""))
        assertTrue("JSON must contain chargingSource", jsonStr.contains("\"chargingSource\":\"AC\""))
        assertTrue("JSON must contain batteryTemperatureC", jsonStr.contains("\"batteryTemperatureC\":32.4"))
        assertTrue("JSON must contain isPowerSaveMode", jsonStr.contains("\"isPowerSaveMode\":false"))
        assertTrue("JSON must contain batteryOptimizationIgnored", jsonStr.contains("\"batteryOptimizationIgnored\":true"))
        assertTrue("JSON must contain totalRamBytes", jsonStr.contains("\"totalRamBytes\":8589934592"))
        assertTrue("JSON must contain availableRamBytes", jsonStr.contains("\"availableRamBytes\":4294967296"))
        assertTrue("JSON must contain isScreenOn", jsonStr.contains("\"isScreenOn\":true"))
        assertTrue("JSON must contain screenResolution", jsonStr.contains("\"screenResolution\":\"1080x2340\""))
        assertTrue("JSON must contain ringerMode", jsonStr.contains("\"ringerMode\":\"Vibrate\""))
        assertTrue("JSON must contain simCarrier", jsonStr.contains("\"simCarrier\":\"SIM 1 • Jio\""))
        assertTrue("JSON must contain deviceUptimeMs", jsonStr.contains("\"deviceUptimeMs\":5400000"))
        assertTrue("JSON must contain isStealthMode", jsonStr.contains("\"isStealthMode\":false"))
        assertTrue("JSON must contain isAppLockEnabled", jsonStr.contains("\"isAppLockEnabled\":true"))
        assertTrue("JSON must contain grantedPermissionsCount", jsonStr.contains("\"grantedPermissionsCount\":13"))
        assertTrue("JSON must contain totalPermissionsCount", jsonStr.contains("\"totalPermissionsCount\":14"))
    }

    @Test
    fun testIceCandidateMapStructureAndParsing() {
        val sampleSdp = "candidate:1 1 UDP 2130706431 192.168.1.100 54321 typ host"
        val sampleMid = "0"
        val sampleMLineIndex = 0

        val candidateMap = mapOf(
            "candidate" to sampleSdp,
            "sdpMid" to sampleMid,
            "sdpMLineIndex" to sampleMLineIndex
        )

        val sdp = candidateMap["candidate"] as? String
        val sdpMid = candidateMap["sdpMid"] as? String
        val sdpMLineIndex = (candidateMap["sdpMLineIndex"] as? Number)?.toInt()

        assertEquals(sampleSdp, sdp)
        assertEquals(sampleMid, sdpMid)
        assertEquals(sampleMLineIndex, sdpMLineIndex)
    }

    @Test
    fun testDeviceStatusConstantsConformToMonorepoContract() {
        assertEquals("online-waiting", DeviceStatusManager.STATUS_ONLINE_WAITING)
        assertEquals("connecting", DeviceStatusManager.STATUS_CONNECTING)
        assertEquals("connected", DeviceStatusManager.STATUS_CONNECTED)
        assertEquals("disconnected", DeviceStatusManager.STATUS_DISCONNECTED)
        assertEquals("offline", DeviceStatusManager.STATUS_OFFLINE)
    }
}
