package com.nexus.child.components.location

import android.location.Location
import android.util.Log

/**
 * LocationCommandHandler orchestrates DataChannel commands for geolocation surveillance:
 * 1. LOCATION_ON (or LOCATE_CHILD): Initiates location tracking and confirms with LOCATION_STARTED.
 * 2. LOCATION_OFF (or LOCATE_CHILD_STOP): Halts location tracking and confirms with LOCATION_STOPPED.
 * 3. Formats and sends LOCATION_UPDATE JSON packets whenever the device physically moves.
 * 4. Resets tracking when the WebRTC peer connection drops to prevent battery exhaustion.
 *
 * Hey friend! Here is the step-by-step handshake flow:
 *
 * --- STARTING LOCATION TRACKING ---
 * 1. Desktop parent sends "LOCATION_ON" over the 'nexus-control' DataChannel.
 * 2. We check if the Android device has granted ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
 *    - If denied: we send "LOCATION_ERROR: Location permission not granted" and stop.
 * 3. We call locationController.startLocationTracking { location -> sendLocationUpdate(location) }.
 * 4. We send "LOCATION_STARTED" over the DataChannel.
 * 5. Parent desktop receives "LOCATION_STARTED" and flips its loading spinner to the active "Stop" button.
 *
 * --- REAL-TIME UPDATES (DISPLACEMENT ONLY) ---
 * 1. Whenever the phone physically moves 5 meters or more, LocationController invokes our callback.
 * 2. We package the coordinates, accuracy, speed, altitude, and timestamp into a JSON packet.
 * 3. We transmit the JSON packet across the DataChannel to the parent console.
 *
 * --- STOPPING LOCATION TRACKING ---
 * 1. Desktop parent sends "LOCATION_OFF" over the DataChannel.
 * 2. We call locationController.stopLocationTracking() to shut down GPS hardware.
 * 3. We send "LOCATION_STOPPED" over the DataChannel.
 * 4. Parent flips its button back to "Start Live Tracking".
 */
class LocationCommandHandler(
    private val locationController: ILocationController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "LocationCommandHandler"

        // Primary Control Commands
        const val CMD_LOCATION_ON = "LOCATION_ON"
        const val CMD_LOCATION_OFF = "LOCATION_OFF"

        // Backward compatibility aliases
        const val ALIAS_LOCATE_CHILD = "LOCATE_CHILD"
        const val ALIAS_LOCATE_CHILD_STOP = "LOCATE_CHILD_STOP"

        // Response and Confirmation Messages
        const val RESP_LOCATION_STARTED = "LOCATION_STARTED"
        const val RESP_LOCATION_STOPPED = "LOCATION_STOPPED"
        const val RESP_LOCATION_ERROR_PREFIX = "LOCATION_ERROR: "

        // Telemetry Packet Type
        const val TYPE_LOCATION_UPDATE = "LOCATION_UPDATE"
    }

    /**
     * Inspects an incoming message from the parent and executes the corresponding action
     * if it matches a location command.
     *
     * @param command Raw string received over DataChannel
     * @return true if command was handled by this module; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_LOCATION_ON, ALIAS_LOCATE_CHILD -> {
                handleLocationOn()
                true
            }
            CMD_LOCATION_OFF, ALIAS_LOCATE_CHILD_STOP -> {
                handleLocationOff()
                true
            }
            else -> false
        }
    }

    /**
     * Activates continuous location tracking and dispatches confirmation.
     */
    private fun handleLocationOn() {
        Log.d(TAG, "Processing LOCATION_ON command...")

        // Step 1: Verify runtime permission
        if (!locationController.hasLocationPermission()) {
            val errorMsg = "${RESP_LOCATION_ERROR_PREFIX}Location permission not granted on child device"
            Log.w(TAG, errorMsg)
            dataSender(errorMsg)
            return
        }

        // Step 2: Start tracking with update callback
        val started = locationController.startLocationTracking { location ->
            sendLocationUpdate(location)
        }

        if (started) {
            Log.d(TAG, "Location tracking started. Sending confirmation: $RESP_LOCATION_STARTED")
            dataSender(RESP_LOCATION_STARTED)
        } else {
            val errorMsg = "${RESP_LOCATION_ERROR_PREFIX}Failed to start location hardware provider"
            Log.e(TAG, errorMsg)
            dataSender(errorMsg)
        }
    }

    /**
     * Halts continuous location tracking and dispatches confirmation.
     */
    private fun handleLocationOff() {
        Log.d(TAG, "Processing LOCATION_OFF command...")
        locationController.stopLocationTracking()
        Log.d(TAG, "Location tracking stopped. Sending confirmation: $RESP_LOCATION_STOPPED")
        dataSender(RESP_LOCATION_STOPPED)
    }

    /**
     * Formats a Location instance into a standardized JSON payload and transmits
     * it across the WebRTC DataChannel.
     */
    private fun sendLocationUpdate(location: Location) {
        try {
            val json = buildLocationJson(location)
            val dispatched = dataSender(json)
            if (dispatched) {
                Log.d(
                    TAG,
                    "Dispatched location packet: (${location.latitude}, ${location.longitude}) " +
                            "acc=${if (location.hasAccuracy()) "${location.accuracy}m" else "N/A"}"
                )
            } else {
                Log.w(TAG, "Failed to send location update: DataChannel send returned false")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize and transmit location update: ${e.message}", e)
        }
    }

    /**
     * Serializes coordinates and telemetry fields to a clean, deterministic JSON string.
     */
    fun buildLocationJson(
        latitude: Double,
        longitude: Double,
        accuracy: Double = 0.0,
        altitude: Double = 0.0,
        speed: Double = 0.0,
        bearing: Double = 0.0,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        return "{\"type\":\"$TYPE_LOCATION_UPDATE\"," +
                "\"coords\":[$latitude,$longitude]," +
                "\"latitude\":$latitude," +
                "\"longitude\":$longitude," +
                "\"accuracy\":$accuracy," +
                "\"altitude\":$altitude," +
                "\"speed\":$speed," +
                "\"bearing\":$bearing," +
                "\"timestamp\":$timestamp}"
    }

    /**
     * Serializes Android Location object to a clean, deterministic JSON string matching the parent contract.
     * Uses direct string formatting to avoid stubbed JSONObject issues in unit tests and reduce runtime allocations.
     */
    fun buildLocationJson(location: Location): String {
        val lat = location.latitude
        val lng = location.longitude
        val acc = if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0
        val alt = if (location.hasAltitude()) location.altitude else 0.0
        val speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val bearing = if (location.hasBearing()) location.bearing.toDouble() else 0.0
        val time = location.time

        return buildLocationJson(lat, lng, acc, alt, speed, bearing, time)
    }

    /**
     * Invoked when WebRTC peer connection is lost or closed.
     * Guarantees GPS radio is immediately shut down to prevent battery drain.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Halting location tracking to save power...")
        locationController.stopLocationTracking()
    }

    /**
     * Clean up all references on activity destruction.
     */
    fun cleanup() {
        locationController.cleanup()
    }
}
