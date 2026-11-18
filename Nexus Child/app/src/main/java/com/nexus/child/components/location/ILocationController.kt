package com.nexus.child.components.location

import android.location.Location

/**
 * ILocationController defines the contract for hardware location tracking on the Android child device.
 *
 * Hey friend! Why do we use an interface here?
 * 1. Separation of Concerns: It decouples our WebRTC command handling from Android's hardware GPS services.
 * 2. Deterministic Unit Testing: In tests, we can plug in a fake location controller without needing
 *    a real phone, Google Play Services, or mock location permissions.
 * 3. Safe Lifecycle: Enforces clear start, stop, and cleanup boundaries to ensure GPS hardware
 *    never runs in the background unintentionally.
 */
interface ILocationController {

    /**
     * Checks whether runtime location permissions (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION)
     * are currently granted by the operating system.
     *
     * @return true if granted, false otherwise.
     */
    fun hasLocationPermission(): Boolean

    /**
     * Reports whether continuous location updates are currently actively running.
     *
     * @return true if GPS/network location listener is registered and streaming.
     */
    fun isTracking(): Boolean

    /**
     * Starts continuous high-accuracy location tracking.
     *
     * Important Behavior:
     * - Dispatches the initial position fix (or last known location) immediately.
     * - Subsequent updates are filtered: only locations with physical movement
     *   greater than or equal to the minimum displacement threshold (5 meters)
     *   trigger the [onLocationUpdate] callback.
     * - Stationary devices do NOT fire repeated updates, preventing battery drain.
     *
     * @param onLocationUpdate Callback invoked on the main looper whenever a valid location update occurs.
     * @return true if location updates were successfully registered; false if permissions missing or error.
     */
    fun startLocationTracking(onLocationUpdate: (Location) -> Unit): Boolean

    /**
     * Halts continuous location tracking and unregisters all location listeners.
     * Clears cached displacement state so the next tracking session starts fresh.
     *
     * @return true if stopped cleanly; false if not currently tracking.
     */
    fun stopLocationTracking(): Boolean

    /**
     * Cleans up all resources, clears callbacks, and ensures GPS hardware is powered off.
     */
    fun cleanup()
}
