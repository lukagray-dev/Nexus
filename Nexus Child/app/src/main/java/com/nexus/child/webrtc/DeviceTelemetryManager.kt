package com.nexus.child.webrtc

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.nexus.child.BuildConfig
import com.nexus.child.components.applock.AppLockManager
import com.nexus.child.components.stealth.StealthController
import com.nexus.child.permission.PermissionManager
import com.nexus.child.service.BatteryOptimizationManager
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data class representing a point-in-time hardware and posture snapshot of the child device.
 *
 * Hey friend! This packet carries real hardware, OS, power, memory, connectivity, and
 * security-posture telemetry across the WebRTC DataChannel to the parent desktop dashboard:
 * - Battery level, charging state/source, battery temperature, and Power Save / Doze status.
 * - Active network route (WiFi vs Mobile Data) and active SIM carrier summary.
 * - Device name, brand, hardware model, manufacturer, Android OS release + API level, and app version.
 * - RAM (available/total), internal storage (free/total), display resolution, and screen awake state.
 * - Ringer mode, device uptime, Stealth Mode, App Lock state, and granted permissions count.
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
    val totalStorageBytes: Long = 0L,
    val sdkInt: Int = 0,
    val brand: String = "",
    val appVersion: String = "",
    val chargingSource: String = "Battery",
    val batteryTemperatureC: Double = 0.0,
    val isPowerSaveMode: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val isScreenOn: Boolean = true,
    val screenResolution: String = "",
    val ringerMode: String = "Normal",
    val simCarrier: String = "",
    val deviceUptimeMs: Long = 0L,
    val isStealthMode: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val grantedPermissionsCount: Int = 0,
    val totalPermissionsCount: Int = 0
) {
    /**
     * Serializes this snapshot into a JSON string for transmission over the DataChannel.
     * We avoid Android JSONObject dependencies here so this data class can also run in plain JVM tests.
     */
    fun toJsonString(): String {
        val tempFormatted = String.format(Locale.US, "%.1f", batteryTemperatureC)
        return "{\"type\":\"DEVICE_TELEMETRY\"," +
            "\"batteryPercent\":$batteryPercent," +
            "\"isCharging\":$isCharging," +
            "\"networkState\":\"${escapeJson(networkState)}\"," +
            "\"deviceName\":\"${escapeJson(deviceName)}\"," +
            "\"model\":\"${escapeJson(model)}\"," +
            "\"manufacturer\":\"${escapeJson(manufacturer)}\"," +
            "\"androidVersion\":\"${escapeJson(androidVersion)}\"," +
            "\"freeStorageBytes\":$freeStorageBytes," +
            "\"totalStorageBytes\":$totalStorageBytes," +
            "\"sdkInt\":$sdkInt," +
            "\"brand\":\"${escapeJson(brand)}\"," +
            "\"appVersion\":\"${escapeJson(appVersion)}\"," +
            "\"chargingSource\":\"${escapeJson(chargingSource)}\"," +
            "\"batteryTemperatureC\":$tempFormatted," +
            "\"isPowerSaveMode\":$isPowerSaveMode," +
            "\"batteryOptimizationIgnored\":$batteryOptimizationIgnored," +
            "\"totalRamBytes\":$totalRamBytes," +
            "\"availableRamBytes\":$availableRamBytes," +
            "\"isScreenOn\":$isScreenOn," +
            "\"screenResolution\":\"${escapeJson(screenResolution)}\"," +
            "\"ringerMode\":\"${escapeJson(ringerMode)}\"," +
            "\"simCarrier\":\"${escapeJson(simCarrier)}\"," +
            "\"deviceUptimeMs\":$deviceUptimeMs," +
            "\"isStealthMode\":$isStealthMode," +
            "\"isAppLockEnabled\":$isAppLockEnabled," +
            "\"grantedPermissionsCount\":$grantedPermissionsCount," +
            "\"totalPermissionsCount\":$totalPermissionsCount}"
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

/**
 * DeviceTelemetryManager monitors hardware status changes on the child device
 * and transmits telemetry over the WebRTC DataChannel.
 *
 * Key engineering principles implemented here:
 * 1. Lazy lifecycle: Hardware listeners are NOT active until the parent connects (DataChannel OPEN).
 * 2. Event-driven updates: No wasteful heartbeat polling loops. Updates dispatch only when a
 *    monitored hardware/posture field changes.
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

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

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

    // BroadcastReceiver for battery, screen power, battery saver, and ringer mode transitions
    private val hardwareStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_BATTERY_CHANGED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == Intent.ACTION_POWER_DISCONNECTED ||
                action == Intent.ACTION_SCREEN_ON ||
                action == Intent.ACTION_SCREEN_OFF ||
                action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ||
                action == AudioManager.RINGER_MODE_CHANGED_ACTION
            ) {
                evaluateAndDispatchIfChanged()
            }
        }
    }

    /**
     * Starts monitoring hardware changes and registers OS listeners.
     * Invoked ONLY when the parent has successfully opened the DataChannel.
     */
    fun start() {
        if (isRunning.getAndSet(true)) return

        Log.d(TAG, "Starting hardware event monitoring for telemetry changes (parent connected)...")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(hardwareStateReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register hardware state receiver: ${e.message}")
        }

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
            Log.d(TAG, "Initial telemetry packet transmitted.")
        }
    }

    /**
     * Evaluates current hardware state and sends an update ONLY if a monitored field changed.
     */
    fun evaluateAndDispatchIfChanged() {
        if (!isRunning.get()) return

        val current = captureCurrentSnapshot()
        val previous = lastSentSnapshot

        val hasChanged = previous == null ||
            previous.batteryPercent != current.batteryPercent ||
            previous.isCharging != current.isCharging ||
            previous.chargingSource != current.chargingSource ||
            previous.networkState != current.networkState ||
            previous.isPowerSaveMode != current.isPowerSaveMode ||
            previous.isScreenOn != current.isScreenOn ||
            previous.ringerMode != current.ringerMode ||
            previous.isStealthMode != current.isStealthMode ||
            previous.isAppLockEnabled != current.isAppLockEnabled

        if (hasChanged) {
            Log.d(
                TAG,
                "Hardware state changed: battery=${current.batteryPercent}%, charging=${current.isCharging}, net=${current.networkState}, screen=${current.isScreenOn}"
            )
            if (sendDataChannelPacket(current.toJsonString())) {
                lastSentSnapshot = current
            }
        }
    }

    /**
     * Reads current hardware, OS, battery, memory, network, and posture metrics from Android.
     */
    fun captureCurrentSnapshot(): TelemetrySnapshot {
        // 1. Battery level, charging source, and temperature
        val batteryStatus: Intent? = try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(null, ifilter)
        } catch (_: Exception) {
            null
        }

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Plugged" else "Battery"
        }

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemperatureC = if (tempTenths > 0) Math.round(tempTenths.toDouble()) / 10.0 else 0.0

        // 2. Network transport
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
        } catch (_: Exception) {
            NETWORK_WIFI
        }

        // 3. Hardware, OS, and app identification
        val model = try { Build.MODEL ?: "Android Device" } catch (_: Exception) { "Android Device" }
        val manufacturer = try { Build.MANUFACTURER ?: "Generic" } catch (_: Exception) { "Generic" }
        val brand = try { Build.BRAND ?: manufacturer } catch (_: Exception) { manufacturer }
        val androidVersion = try { Build.VERSION.RELEASE ?: "14" } catch (_: Exception) { "14" }
        val sdkInt = try { Build.VERSION.SDK_INT } catch (_: Exception) { 0 }
        val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Exception) { "" }
        val deviceName = resolveDeviceName(context, manufacturer, model)

        // 4. Internal storage & RAM
        val (freeBytes, totalBytes) = readStorageMetrics()
        val (availRam, totalRam) = readRamMetrics()

        // 5. Power, screen, ringer, uptime
        val isPowerSaveMode = try { powerManager?.isPowerSaveMode ?: false } catch (_: Exception) { false }
        val isScreenOn = try { powerManager?.isInteractive ?: true } catch (_: Exception) { true }
        val batteryOptIgnored = try {
            BatteryOptimizationManager.isIgnoringBatteryOptimizations(context)
        } catch (_: Exception) {
            false
        }
        val screenResolution = readScreenResolution()
        val ringerMode = readRingerMode()
        val simCarrier = readSimCarrierSummary()
        val deviceUptimeMs = try { SystemClock.elapsedRealtime() } catch (_: Exception) { 0L }

        // 6. Security & survival posture
        val isStealthMode = try { StealthController(context).isStealthMode() } catch (_: Exception) { false }
        val isAppLockEnabled = try { AppLockManager.isAppLockEnabled(context) } catch (_: Exception) { false }
        val grantedPermissionsCount = try { PermissionManager.getGrantedCount(context) } catch (_: Exception) { 0 }
        val totalPermissionsCount = try { PermissionManager.getTotalCount() } catch (_: Exception) { 0 }

        return TelemetrySnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkState = networkState,
            deviceName = deviceName,
            model = model,
            manufacturer = manufacturer,
            androidVersion = androidVersion,
            freeStorageBytes = freeBytes,
            totalStorageBytes = totalBytes,
            sdkInt = sdkInt,
            brand = brand,
            appVersion = appVersion,
            chargingSource = chargingSource,
            batteryTemperatureC = batteryTemperatureC,
            isPowerSaveMode = isPowerSaveMode,
            batteryOptimizationIgnored = batteryOptIgnored,
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            isScreenOn = isScreenOn,
            screenResolution = screenResolution,
            ringerMode = ringerMode,
            simCarrier = simCarrier,
            deviceUptimeMs = deviceUptimeMs,
            isStealthMode = isStealthMode,
            isAppLockEnabled = isAppLockEnabled,
            grantedPermissionsCount = grantedPermissionsCount,
            totalPermissionsCount = totalPermissionsCount
        )
    }

    private fun resolveDeviceName(ctx: Context, manufacturer: String, model: String): String {
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

        try {
            val btName = Settings.System.getString(ctx.contentResolver, "bluetooth_name")
            if (!btName.isNullOrBlank()) {
                return btName.trim()
            }
        } catch (_: Exception) {}

        val cleanMfg = manufacturer.replaceFirstChar { it.uppercase() }
        return if (model.startsWith(cleanMfg, ignoreCase = true)) {
            model
        } else {
            "$cleanMfg $model".trim()
        }
    }

    private fun readStorageMetrics(): Pair<Long, Long> {
        return try {
            val dataDir = Environment.getDataDirectory()
            val stat = StatFs(dataDir.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            Pair(freeBytes, totalBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query StatFs for storage metrics: ${e.message}")
            Pair(0L, 0L)
        }
    }

    private fun readRamMetrics(): Pair<Long, Long> {
        return try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            Pair(memInfo.availMem, memInfo.totalMem)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query ActivityManager memory info: ${e.message}")
            Pair(0L, 0L)
        }
    }

    @Suppress("DEPRECATION")
    private fun readScreenResolution(): String {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                "${bounds.width()}x${bounds.height()}"
            } else if (wm != null) {
                val dm = DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(dm)
                "${dm.widthPixels}x${dm.heightPixels}"
            } else {
                val dm = context.resources.displayMetrics
                "${dm.widthPixels}x${dm.heightPixels}"
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun readRingerMode(): String {
        return try {
            when (audioManager?.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                else -> "Normal"
            }
        } catch (_: Exception) {
            "Normal"
        }
    }

    @SuppressLint("MissingPermission")
    private fun readSimCarrierSummary(): String {
        return try {
            val hasPhoneState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPhoneState) return ""

            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    ?: return ""
            val subs = subManager.activeSubscriptionInfoList ?: return ""
            if (subs.isEmpty()) return "No SIM"

            subs.sortedBy { it.simSlotIndex }.joinToString(", ") { info ->
                val slot = info.simSlotIndex + 1
                val carrier = info.carrierName?.toString()?.trim().orEmpty()
                if (carrier.isNotEmpty()) "SIM $slot • $carrier" else "SIM $slot"
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Stops monitoring and unregisters system event receivers.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "Stopping hardware telemetry monitoring and unregistering receivers...")
        try {
            context.unregisterReceiver(hardwareStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Hardware state receiver unregister error: ${e.message}")
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Network callback unregister error: ${e.message}")
        }

        lastSentSnapshot = null
    }
}
