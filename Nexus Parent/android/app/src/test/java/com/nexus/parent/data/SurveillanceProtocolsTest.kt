package com.nexus.parent.data

import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.ScreenQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SurveillanceProtocolsTest.kt - Verifies Camera, Screen, and Location DataChannel command
 * builders, ACK parsers, LOCATION_UPDATE JSON decoding, and telemetry formatters.
 */
class SurveillanceProtocolsTest {

    @Test
    fun `builds camera and screen commands matching Desktop and Child contracts`() {
        assertEquals("CAMERA_ON:REAR", SurveillanceProtocols.buildCameraOnCommand(CameraLens.REAR))
        assertEquals("CAMERA_ON:FRONT", SurveillanceProtocols.buildCameraOnCommand(CameraLens.FRONT))
        assertEquals(CameraLens.FRONT, SurveillanceProtocols.parseCameraSwitchedLens("CAMERA_SWITCHED: FRONT", CameraLens.REAR))
        assertEquals(CameraLens.REAR, SurveillanceProtocols.parseCameraSwitchedLens("CAMERA_SWITCHED: REAR", CameraLens.FRONT))
        assertEquals(CameraLens.FRONT, SurveillanceProtocols.parseCameraSwitchedLens("CAMERA_SWITCHED", CameraLens.REAR))

        assertEquals("SCREEN_ON:1080p", SurveillanceProtocols.buildScreenOnCommand(ScreenQuality.P1080))
        assertEquals("SCREEN_ON:720p", SurveillanceProtocols.buildScreenOnCommand(ScreenQuality.P720))
        assertEquals("SCREEN_ON:540p", SurveillanceProtocols.buildScreenOnCommand(ScreenQuality.P540))
        assertEquals("SCREEN_QUALITY:540p", SurveillanceProtocols.buildScreenQualityCommand(ScreenQuality.P540))
    }

    @Test
    fun `parses Child LOCATION_UPDATE json packets with both explicit fields and coords array`() {
        val childJson = """{"type":"LOCATION_UPDATE","coords":[22.572645,88.363892],"latitude":22.572645,"longitude":88.363892,"accuracy":8.4,"altitude":12.0,"speed":1.5,"bearing":180.0,"timestamp":1764160000000}"""
        val packet = SurveillanceProtocols.parseLocationPacket(childJson)
        assertNotNull(packet)
        assertEquals(22.572645, packet!!.latitude, 0.000001)
        assertEquals(88.363892, packet.longitude, 0.000001)
        assertEquals(8.4f, packet.accuracyMeters, 0.01f)
        assertEquals(1.5f, packet.speedMps, 0.01f)

        val coordsOnlyJson = """{"type":"LOCATION_UPDATE","coords":[-33.8688,151.2093],"accuracy":15.0}"""
        val packet2 = SurveillanceProtocols.parseLocationPacket(coordsOnlyJson)
        assertNotNull(packet2)
        assertEquals(-33.8688, packet2!!.latitude, 0.0001)
        assertEquals(151.2093, packet2.longitude, 0.0001)

        assertNull(SurveillanceProtocols.parseLocationPacket("""{"type":"DEVICE_TELEMETRY"}"""))
        assertNull(SurveillanceProtocols.parseLocationPacket("LOCATION_STARTED"))
    }

    @Test
    fun `formats coordinates, speed, accuracy, relative timestamps, and elapsed durations accurately`() {
        assertEquals("22.572645° N, 88.363892° E", SurveillanceProtocols.formatCoordinates(22.572645, 88.363892))
        assertEquals("33.868800° S, 118.243683° W", SurveillanceProtocols.formatCoordinates(-33.8688, -118.243683))

        assertEquals("0.0 km/h (Stationary)", SurveillanceProtocols.formatSpeed(0.15f))
        assertEquals("36.0 km/h", SurveillanceProtocols.formatSpeed(10.0f))

        assertEquals("±0 m (High Precision)", SurveillanceProtocols.formatAccuracy(0f))
        assertEquals("±6.5 m", SurveillanceProtocols.formatAccuracy(6.5f))

        val now = 1_000_000L
        assertEquals("Just now", SurveillanceProtocols.formatRelativeTime(999_000L, now))
        assertEquals("14 seconds ago", SurveillanceProtocols.formatRelativeTime(986_000L, now))
        assertEquals("3m ago", SurveillanceProtocols.formatRelativeTime(820_000L, now))

        assertEquals("00:45", SurveillanceProtocols.formatElapsedSeconds(45))
        assertEquals("01:02:03", SurveillanceProtocols.formatElapsedSeconds(3723))
    }
}
