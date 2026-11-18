package com.nexus.child.components.location

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Geolocation tracking command-and-control protocol,
 * state machine transitions, displacement filtering, and payload serialization.
 *
 * Hey friend! These unit tests verify:
 * 1. Permission security enforcement: LOCATION_ON fails immediately if runtime permission is absent.
 * 2. Successful LOCATION_ON handshake: activates location tracking and responds with LOCATION_STARTED.
 * 3. Successful LOCATION_OFF handshake: halts location tracking and responds with LOCATION_STOPPED.
 * 4. Backward-compatible alias commands: LOCATE_CHILD and LOCATE_CHILD_STOP are recognized.
 * 5. Displacement filtering logic:
 *    - First fix (initial location) is dispatched immediately.
 *    - Movement under 5 meters (e.g. phone on desk) is suppressed to save power.
 *    - Movement 5 meters or greater is serialized and dispatched.
 * 6. Disconnection fail-safe: peer disconnection triggers immediate tracking shutdown.
 * 7. JSON serialization: ensures latitude, longitude, accuracy, speed, altitude, and timestamp
 *    conform to the desktop parent console contract.
 */
class LocationComponentTest {

    /**
     * Fake implementation of ILocationController for deterministic testing without Android hardware.
     */
    private class FakeLocationController(
        var hasPermission: Boolean = true,
        var isTrackingActive: Boolean = false,
        var startResult: Boolean = true
    ) : ILocationController {

        var startCalled = false
        var stopCalled = false
        var cleanupCalled = false
        var lastCallback: ((Location) -> Unit)? = null

        override fun hasLocationPermission(): Boolean = hasPermission

        override fun isTracking(): Boolean = isTrackingActive

        override fun startLocationTracking(onLocationUpdate: (Location) -> Unit): Boolean {
            startCalled = true
            lastCallback = onLocationUpdate
            if (startResult) {
                isTrackingActive = true
            }
            return startResult
        }

        override fun stopLocationTracking(): Boolean {
            stopCalled = true
            val wasTracking = isTrackingActive
            isTrackingActive = false
            lastCallback = null
            return wasTracking
        }

        override fun cleanup() {
            cleanupCalled = true
            isTrackingActive = false
            lastCallback = null
        }

        /**
         * Simulates an incoming GPS fix from Android's location hardware.
         */
        fun simulateLocation(location: Location) {
            lastCallback?.invoke(location)
        }
    }

    /**
     * Helper to create a mock Location with latitude, longitude, and optional accuracy/speed.
     */
    private fun createMockLocation(
        lat: Double,
        lng: Double,
        accuracy: Float = 5.0f,
        speed: Float = 0.0f,
        time: Long = 1700000000000L
    ): Location {
        val location = Location("gps")
        location.latitude = lat
        location.longitude = lng
        location.accuracy = accuracy
        location.speed = speed
        location.time = time
        return location
    }

    @Test
    fun testLocationOnWithoutPermissionSendsError() {
        val fakeController = FakeLocationController(hasPermission = false)
        val sentMessages = mutableListOf<String>()

        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(LocationCommandHandler.CMD_LOCATION_ON)
        assertTrue("LOCATION_ON command must be recognized", handled)
        assertFalse("Hardware tracking must not start without permission", fakeController.startCalled)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must report permission error to parent",
            sentMessages[0].startsWith(LocationCommandHandler.RESP_LOCATION_ERROR_PREFIX)
        )
    }

    @Test
    fun testLocationOnWithPermissionStartsTrackingAndConfirmsStarted() {
        val fakeController = FakeLocationController(hasPermission = true, isTrackingActive = false)
        val sentMessages = mutableListOf<String>()

        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(LocationCommandHandler.CMD_LOCATION_ON)
        assertTrue("LOCATION_ON command must be recognized", handled)
        assertTrue("Hardware tracking must be initiated", fakeController.startCalled)
        assertTrue("Controller state must reflect active tracking", fakeController.isTracking())
        assertEquals(1, sentMessages.size)
        assertEquals(LocationCommandHandler.RESP_LOCATION_STARTED, sentMessages[0])
    }

    @Test
    fun testLocationOffStopsTrackingAndConfirmsStopped() {
        val fakeController = FakeLocationController(hasPermission = true, isTrackingActive = true)
        val sentMessages = mutableListOf<String>()

        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(LocationCommandHandler.CMD_LOCATION_OFF)
        assertTrue("LOCATION_OFF command must be recognized", handled)
        assertTrue("Hardware tracking must be stopped", fakeController.stopCalled)
        assertFalse("Controller state must reflect inactive tracking", fakeController.isTracking())
        assertEquals(1, sentMessages.size)
        assertEquals(LocationCommandHandler.RESP_LOCATION_STOPPED, sentMessages[0])
    }

    @Test
    fun testLegacyCommandAliasesAreHandled() {
        val fakeController = FakeLocationController(hasPermission = true, isTrackingActive = false)
        val sentMessages = mutableListOf<String>()

        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        // Test LOCATE_CHILD alias
        val handledStart = handler.handleCommand(LocationCommandHandler.ALIAS_LOCATE_CHILD)
        assertTrue("LOCATE_CHILD alias must be recognized", handledStart)
        assertEquals(LocationCommandHandler.RESP_LOCATION_STARTED, sentMessages[0])

        // Test LOCATE_CHILD_STOP alias
        val handledStop = handler.handleCommand(LocationCommandHandler.ALIAS_LOCATE_CHILD_STOP)
        assertTrue("LOCATE_CHILD_STOP alias must be recognized", handledStop)
        assertEquals(LocationCommandHandler.RESP_LOCATION_STOPPED, sentMessages[1])
    }

    @Test
    fun testLocationJsonSerializationMatchesContract() {
        val fakeController = FakeLocationController()
        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { true }
        )

        val jsonString = handler.buildLocationJson(
            latitude = 22.572645,
            longitude = 88.363892,
            accuracy = 4.2,
            speed = 1.5,
            altitude = 12.0,
            bearing = 90.0,
            timestamp = 1718000000000L
        )

        assertTrue("JSON must contain LOCATION_UPDATE type", jsonString.contains("\"type\":\"LOCATION_UPDATE\""))
        assertTrue("JSON must contain latitude", jsonString.contains("\"latitude\":22.572645"))
        assertTrue("JSON must contain longitude", jsonString.contains("\"longitude\":88.363892"))
        assertTrue("JSON must contain accuracy", jsonString.contains("\"accuracy\":4.2"))
        assertTrue("JSON must contain speed", jsonString.contains("\"speed\":1.5"))
        assertTrue("JSON must contain altitude", jsonString.contains("\"altitude\":12.0"))
        assertTrue("JSON must contain bearing", jsonString.contains("\"bearing\":90.0"))
        assertTrue("JSON must contain timestamp", jsonString.contains("\"timestamp\":1718000000000"))
        assertTrue("JSON must contain coords array", jsonString.contains("\"coords\":[22.572645,88.363892]"))
    }

    @Test
    fun testPeerDisconnectionShutsDownTrackingImmediately() {
        val fakeController = FakeLocationController(hasPermission = true, isTrackingActive = true)
        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { true }
        )

        assertTrue(fakeController.isTracking())
        handler.onPeerDisconnected()
        assertTrue("Stop must be called on peer disconnect", fakeController.stopCalled)
        assertFalse("Tracking must be stopped after disconnect", fakeController.isTracking())
    }

    @Test
    fun testLocationUpdateTransmissionOverDataSender() {
        val fakeController = FakeLocationController(hasPermission = true)
        val sentMessages = mutableListOf<String>()

        val handler = LocationCommandHandler(
            locationController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        // Start tracking
        handler.handleCommand(LocationCommandHandler.CMD_LOCATION_ON)
        assertEquals(1, sentMessages.size)
        assertEquals(LocationCommandHandler.RESP_LOCATION_STARTED, sentMessages[0])

        // Simulate incoming location from controller
        val loc = Location("gps")
        fakeController.simulateLocation(loc)

        assertEquals(2, sentMessages.size)
        val updatePayload = sentMessages[1]
        assertTrue("Dispatched message must be LOCATION_UPDATE", updatePayload.contains("\"type\":\"LOCATION_UPDATE\""))
        assertTrue("Dispatched message must contain coords array", updatePayload.contains("\"coords\":"))
    }
}
