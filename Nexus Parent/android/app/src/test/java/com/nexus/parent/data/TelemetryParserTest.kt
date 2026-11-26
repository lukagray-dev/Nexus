package com.nexus.parent.data

import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TelemetryParserTest.kt - Verifies parsing and merging of Child's 27-field `DEVICE_TELEMETRY` packets.
 */
class TelemetryParserTest {

    @Test
    fun `parses full 27-field DEVICE_TELEMETRY json packet and applies onto record`() {
        val payload = """
            {
              "type": "DEVICE_TELEMETRY",
              "batteryPercent": 76,
              "isCharging": true,
              "networkState": "5G",
              "deviceName": "Pixel 8 Kid",
              "model": "Pixel 8 Pro",
              "manufacturer": "Google",
              "androidVersion": "15",
              "freeStorageBytes": 64424509440,
              "totalStorageBytes": 274877906944,
              "sdkInt": 35,
              "brand": "google",
              "appVersion": "0.2.0",
              "chargingSource": "USB-C PD",
              "batteryTemperatureC": 33.5,
              "isPowerSaveMode": false,
              "batteryOptimizationIgnored": true,
              "totalRamBytes": 12884901888,
              "availableRamBytes": 6442450944,
              "isScreenOn": true,
              "screenResolution": "1344x2992",
              "ringerMode": "Vibrate",
              "simCarrier": "T-Mobile",
              "deviceUptimeMs": 93780000,
              "isStealthMode": true,
              "isAppLockEnabled": true,
              "grantedPermissionsCount": 14,
              "totalPermissionsCount": 15
            }
        """.trimIndent()

        val update = TelemetryParser.parsePacket(payload)
        assertNotNull(update)

        val base = DeviceRecord(
            rawId = "482194021104",
            formattedId = "4821 9402 1104",
            name = "Child Android Device",
            model = "Android Device",
            lastSyncedLabel = "Connecting"
        )
        val merged = TelemetryParser.applyUpdate(base, update!!)

        assertEquals("Pixel 8 Kid", merged.name)
        assertEquals("Google Pixel 8 Pro", merged.model)
        assertEquals("Just now", merged.lastSyncedLabel)
        assertEquals(76, merged.telemetry.batteryPercent)
        assertTrue(merged.telemetry.isCharging)
        assertEquals("5G", merged.telemetry.networkState)
        assertEquals("15", merged.telemetry.androidVersion)
        assertEquals(35, merged.telemetry.sdkInt)
        assertEquals("google", merged.telemetry.brand)
        assertEquals("0.2.0", merged.telemetry.appVersion)
        assertEquals("USB-C PD", merged.telemetry.chargingSource)
        assertEquals(33.5f, merged.telemetry.batteryTemperatureC, 0.01f)
        assertFalse(merged.telemetry.isPowerSaveMode)
        assertTrue(merged.telemetry.batteryOptimizationIgnored)
        assertEquals("1344x2992", merged.telemetry.screenResolution)
        assertEquals("Vibrate", merged.telemetry.ringerMode)
        assertEquals("T-Mobile", merged.telemetry.simCarrier)
        assertTrue(merged.telemetry.isStealthMode)
        assertTrue(merged.telemetry.isAppLockEnabled)
        assertEquals("14 / 15 Granted", merged.telemetry.survivalPermissionsText)
        assertEquals("App Lock On • Stealth Hidden", merged.telemetry.securityGuardsText)
        assertTrue(merged.telemetry.storageUsageText.contains("GB free of"))
        assertTrue(merged.telemetry.ramUsageText.contains("GB free /"))
        assertEquals("1d 2h 3m", merged.telemetry.deviceUptimeLabel)
    }

    @Test
    fun `partial updates preserve previously synced telemetry fields and ignore non-telemetry packets`() {
        assertNull(TelemetryParser.parsePacket("PING"))
        assertNull(TelemetryParser.parsePacket("""{"type":"CALL_ACTIVE"}"""))
        assertNull(TelemetryParser.parsePacket("not valid json"))

        val existing = DeviceRecord(
            rawId = "482194021104",
            formattedId = "4821 9402 1104",
            name = "Existing Kid",
            model = "Galaxy A54",
            lastSyncedLabel = "Online",
            telemetry = DeviceTelemetry(
                batteryPercent = 90,
                networkState = "WiFi",
                simCarrier = "AT&T",
                isStealthMode = false
            )
        )

        val partial = TelemetryParser.parsePacket("""{"type":"DEVICE_TELEMETRY","batteryPercent":85}""")!!
        val updated = TelemetryParser.applyUpdate(existing, partial)

        assertEquals(85, updated.telemetry.batteryPercent)
        assertEquals("WiFi", updated.telemetry.networkState)
        assertEquals("AT&T", updated.telemetry.simCarrier)
        assertEquals("Existing Kid", updated.name)
    }
}
