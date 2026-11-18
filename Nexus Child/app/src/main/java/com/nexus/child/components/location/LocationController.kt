package com.nexus.child.components.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * LocationController manages hardware-fused geolocation updates on the Android child device.
 *
 * Hey friend! Here is how this component works in plain English:
 * 1. It uses Google Play Services' FusedLocationProviderClient.
 *    Why Fused Location? Because it intelligently blends signals from GPS satellites,
 *    Wi-Fi networks, cell towers, and device sensors to give the highest accuracy
 *    while conserving as much battery power as possible!
 *
 * 2. Smart Displacement Filtering (No Periodic Spam):
 *    In a monitoring app, sending GPS coordinates every 1 or 2 seconds while the phone
 *    is sitting stationary on a desk is a massive waste of battery and radio power.
 *    Instead, we enforce a strict 5-meter displacement rule:
 *    - When tracking starts, we immediately dispatch the device's last known location
 *      so the parent's map instantly places a target pin.
 *    - Afterward, we only trigger a location update when the device physically moves
 *      5 meters or more from its previous reported location!
 *    - If the child stays in one spot, zero extra location packets are sent.
 *
 * 3. Lifecycle-Safe Cleanup:
 *    When the parent stops tracking or the WebRTC connection drops, we unregister the
 *    location callback immediately so the phone's GPS radio turns off completely.
 */
class LocationController(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) : ILocationController {

    companion object {
        private const val TAG = "LocationController"

        /**
         * Minimum physical movement required before sending an updated location packet.
         * 5 meters (~16.4 feet) prevents GPS micro-jitter while stationary and preserves battery.
         */
        const val MIN_DISPLACEMENT_METERS = 5.0f

        /**
         * Desired interval for active location requests in milliseconds (10 seconds).
         * Note: Google Play Services uses this as a guidance rate, but our distance filter
         * ensures updates are only delivered when physical movement actually happens.
         */
        private const val UPDATE_INTERVAL_MS = 10_000L

        /**
         * Fastest interval at which the app can process updates (3 seconds).
         */
        private const val FASTEST_INTERVAL_MS = 3_000L
    }

    @Volatile
    private var trackingActive: Boolean = false

    @Volatile
    private var activeCallback: LocationCallback? = null

    @Volatile
    private var lastSentLocation: Location? = null

    /**
     * Checks if runtime location permission is currently granted.
     */
    override fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    /**
     * Returns true if continuous location updates are currently running.
     */
    override fun isTracking(): Boolean = trackingActive

    /**
     * Starts continuous high-accuracy location tracking with displacement filtering.
     *
     * @param onLocationUpdate Callback invoked whenever an initial fix or displacement >= 5m occurs.
     * @return true if successfully initiated, false if permission missing or failed.
     */
    @SuppressLint("MissingPermission")
    override fun startLocationTracking(onLocationUpdate: (Location) -> Unit): Boolean {
        if (trackingActive) {
            Log.d(TAG, "Location tracking is already running. Ignoring duplicate start request.")
            return true
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start location tracking: Location permissions are not granted.")
            return false
        }

        Log.d(TAG, "Starting location tracking with min displacement: ${MIN_DISPLACEMENT_METERS}m...")

        // Step 1: Send the device's last known location immediately if available
        // This gives the parent an instant map pin instead of waiting for GPS satellite acquisition.
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (lastLoc != null && trackingActive) {
                        Log.d(TAG, "Dispatching cached last known location: (${lastLoc.latitude}, ${lastLoc.longitude})")
                        lastSentLocation = lastLoc
                        onLocationUpdate(lastLoc)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to fetch last known location: ${error.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Exception querying last known location: ${e.message}")
        }

        // Step 2: Configure high-accuracy location request with displacement threshold
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .setWaitForAccurateLocation(false)
            .build()

        // Step 3: Define callback with manual distance filter verification
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!trackingActive) return

                val newLoc = result.lastLocation ?: return
                val prevLoc = lastSentLocation

                // Calculate distance from previous sent location
                val distanceMoved = prevLoc?.distanceTo(newLoc) ?: Float.MAX_VALUE

                if (prevLoc == null || distanceMoved >= MIN_DISPLACEMENT_METERS) {
                    Log.d(
                        TAG,
                        "Location updated (${newLoc.latitude}, ${newLoc.longitude}) - " +
                                "Displacement: ${if (prevLoc == null) "Initial Fix" else "${distanceMoved.toInt()}m"}"
                    )
                    lastSentLocation = newLoc
                    onLocationUpdate(newLoc)
                } else {
                    Log.d(TAG, "Stationary: movement was only ${distanceMoved.toInt()}m (< ${MIN_DISPLACEMENT_METERS}m). Skipping packet.")
                }
            }
        }

        // Step 4: Register listener with Google Play Services on main looper
        return try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            activeCallback = callback
            trackingActive = true
            Log.d(TAG, "Location tracking successfully started and registered.")
            true
        } catch (securityEx: SecurityException) {
            Log.e(TAG, "SecurityException registering location updates: ${securityEx.message}", securityEx)
            trackingActive = false
            activeCallback = null
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register location updates: ${e.message}", e)
            trackingActive = false
            activeCallback = null
            false
        }
    }

    /**
     * Stops continuous location updates and powers down GPS callbacks.
     */
    override fun stopLocationTracking(): Boolean {
        if (!trackingActive) {
            Log.d(TAG, "Location tracking is already inactive.")
            return false
        }

        Log.d(TAG, "Stopping location tracking and clearing listeners...")
        val callback = activeCallback
        if (callback != null) {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing location updates from FusedClient: ${e.message}")
            }
        }

        activeCallback = null
        trackingActive = false
        lastSentLocation = null
        Log.d(TAG, "Location tracking stopped cleanly.")
        return true
    }

    /**
     * Ensures all callbacks are cleanly removed and state is reset.
     */
    override fun cleanup() {
        stopLocationTracking()
    }
}
