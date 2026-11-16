package com.nexus.child.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data class representing a point-in-time hardware snapshot of the child device.
 *
 * Hey friend! This packet carries actual hardware and OS telemetry across the WebRTC
 * DataChannel to the parent desktop dashboard:
 * - Real battery level percentage and charging state.
 * - Current active network route (WiFi vs Mobile Data).
 * - Real device name, hardware model, manufacturer, and Android OS release version.
 * - Total and free internal storage capacities in bytes.
 */
data class TelemetrySnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkState: String,
    val deviceName: String = "Android Device",
    val model: String = "Android Device",
    val manufacturer: String = "Generic",
    val androidVersion: String = "14",
    val freeStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L
) {
    /**
     * Serializes this snapshot into a JSON string for transmission over the DataChannel.
     * We avoid Android JSONObject dependencies here so this data class can also run in plain JVM tests.
     */
    fun toJsonString(): String {
        return "{\"type\":\"DEVICE_TELEMETRY\"," +
            "\"batteryPercent\":$batteryPercent," +
            "\"isCharging\":$isCharging," +
            "\"networkState\":\"$networkState\"," +
            "\"deviceName\":\"${escapeJson(deviceName)}\"," +
            "\"model\":\"${escapeJson(model)}\"," +
            "\"manufacturer\":\"${escapeJson(manufacturer)}\"," +
            "\"androidVersion\":\"${escapeJson(androidVersion)}\"," +
            "\"freeStorageBytes\":$freeStorageBytes," +
            "\"totalStorageBytes\":$totalStorageBytes}"
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

/**
 * DeviceTelemetryManager monitors hardware status changes on the child device
 * and transmits telemetry over the WebRTC DataChannel.
 *
 * Hey friend! In Nexus, the desktop parent needs to know the child's battery percentage,
 * whether the phone is plugged in to a charger, network state, actual device model/name,
 * and available internal storage.
 *
 * Key engineering principles implemented here:
 * 1. Lazy lifecycle: Hardware listeners are NOT always active! They only activate when the
 *    parent is actually connected (DataChannel state becomes OPEN).
 * 2. Event-driven updates: No wasteful heartbeat polling loops. Updates are only dispatched
 *    when a physical hardware state changes (e.g. charger plugged, battery percent changes).
 * 3. Zero bloat: Cleanly unregisters all OS receivers as soon as the session disconnects.
 */
class DeviceTelemetryManager(
    private val context: Context,
    private val sendDataChannelPacket: (json: String) -> Boolean
) {

    companion object {
        private const val TAG = "DeviceTelemetryManager"

        const val NETWORK_WIFI = "WiFi"
        const val NETWORK_CELLULAR = "Mobile Data"
        const val NETWORK_DISCONNECTED = "Offline"
    }

    private val isRunning = AtomicBoolean(false)
    private var lastSentSnapshot: TelemetrySnapshot? = null

    // ConnectivityManager for detecting network route changes
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // NetworkCallback to track WiFi vs Cellular switches
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateAndDispatchIfChanged()
        }

        override fun onLost(network: Network) {
            evaluateAndDispatchIfChanged()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            evaluateAndDispatchIfChanged()
        }
    }

    // BroadcastReceiver for tracking battery percentage and charging state changes
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED ||
                intent?.action == Intent.ACTION_POWER_CONNECTED ||
                intent?.action == Intent.ACTION_POWER_DISCONNECTED
            ) {
                evaluateAndDispatchIfChanged()
            }
        }
    }

    /**
     * Starts monitoring hardware changes and registers OS listeners.
     * This is invoked ONLY when the parent has successfully opened the DataChannel.
     */
    fun start() {
        if (isRunning.getAndSet(true)) return

        Log.d(TAG, "Starting hardware event monitoring for telemetry changes (parent connected)...")

        // Register battery status receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        try {
            context.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register battery receiver: ${e.message}")
        }

        // Register default network callback
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register default network callback: ${e.message}")
        }
    }

    /**
     * Called when the WebRTC DataChannel transitions to OPEN.
     * Transmits the current hardware state immediately so the parent has initial telemetry.
     */
    fun onDataChannelOpened() {
        Log.d(TAG, "DataChannel is OPEN. Transmitting initial telemetry snapshot...")
        val snapshot = captureCurrentSnapshot()
        if (sendDataChannelPacket(snapshot.toJsonString())) {
            lastSentSnapshot = snapshot
            Log.d(TAG, "Initial telemetry packet transmitted: $snapshot")
        }
    }

    /**
     * Evaluates current hardware state and sends an update ONLY if a field has actually changed.
     */
    fun evaluateAndDispatchIfChanged() {
        if (!isRunning.get()) return

        val current = captureCurrentSnapshot()
        val previous = lastSentSnapshot

        // Only send if this is the first transmission or if an actual value changed
        val hasChanged = previous == null ||
            previous.batteryPercent != current.batteryPercent ||
            previous.isCharging != current.isCharging ||
            previous.networkState != current.networkState

        if (hasChanged) {
            Log.d(
                TAG,
                "Hardware state changed: battery=${current.batteryPercent}%, charging=${current.isCharging}, net=${current.networkState}"
            )
            if (sendDataChannelPacket(current.toJsonString())) {
                lastSentSnapshot = current
            }
        }
    }

    /**
     * Reads current hardware, OS, battery, network, and storage metrics from Android system services.
     */
    fun captureCurrentSnapshot(): TelemetrySnapshot {
        // 1. Capture battery metrics via sticky battery intent
        val batteryStatus: Intent? = try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(null, ifilter)
        } catch (e: Exception) {
            null
        }

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100
        }

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // 2. Capture network connection type
        val networkState = try {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            when {
                capabilities == null -> NETWORK_DISCONNECTED
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NETWORK_CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        } catch (e: Exception) {
            NETWORK_WIFI
        }

        // 3. Capture real hardware and OS identification
        val model = try { Build.MODEL ?: "Android Device" } catch (e: Exception) { "Android Device" }
        val manufacturer = try { Build.MANUFACTURER ?: "Generic" } catch (e: Exception) { "Generic" }
        val androidVersion = try { Build.VERSION.RELEASE ?: "14" } catch (e: Exception) { "14" }

        // 4. Capture actual device name (e.g. "Lucas's Galaxy S24" or Bluetooth name)
        val deviceName = resolveDeviceName(context, manufacturer, model)

        // 5. Capture internal storage capacity (free and total bytes)
        val (freeBytes, totalBytes) = readStorageMetrics()

        return TelemetrySnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkState = networkState,
            deviceName = deviceName,
            model = model,
            manufacturer = manufacturer,
            androidVersion = androidVersion,
            freeStorageBytes = freeBytes,
            totalStorageBytes = totalBytes
        )
    }

    /**
     * Resolves the user-configured friendly device name (e.g. from Settings or Bluetooth).
     */
    private fun resolveDeviceName(ctx: Context, manufacturer: String, model: String): String {
        // Check Settings.Global.DEVICE_NAME on Android 7.1.1+ (API 25+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val globalName = Settings.Global.getString(
                    ctx.contentResolver,
                    Settings.Global.DEVICE_NAME
                )
                if (!globalName.isNullOrBlank()) {
                    return globalName.trim()
                }
            } catch (_: Exception) {}
        }

        // Check Bluetooth device name from Settings.System
        try {
            val btName = Settings.System.getString(
                ctx.contentResolver,
                "bluetooth_name"
            )
            if (!btName.isNullOrBlank()) {
                return btName.trim()
            }
        } catch (_: Exception) {}

        // Fallback: Clean manufacturer + model (e.g. "Google Pixel 8 Pro" or "Samsung Galaxy S24")
        val cleanMfg = manufacturer.replaceFirstChar { it.uppercase() }
        return if (model.startsWith(cleanMfg, ignoreCase = true)) {
            model
        } else {
            "$cleanMfg $model".trim()
        }
    }

    /**
     * Reads internal data directory free and total storage bytes using StatFs.
     */
    private fun readStorageMetrics(): Pair<Long, Long> {
        return try {
            val dataDir = Environment.getDataDirectory()
            val stat = StatFs(dataDir.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            Pair(freeBytes, totalBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query StatFs for storage metrics: ${e.message}")
            Pair(0L, 0L)
        }
    }

    /**
     * Stops monitoring and unregisters system event receivers.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "Stopping hardware telemetry monitoring and unregistering receivers...")
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Battery receiver unregister error: ${e.message}")
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Network callback unregister error: ${e.message}")
        }

        lastSentSnapshot = null
    }
}
