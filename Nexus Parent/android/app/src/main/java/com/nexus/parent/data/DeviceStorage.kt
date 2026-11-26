package com.nexus.parent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.state.FeatureRegistry
import com.nexus.parent.state.LoudspeakerVolume
import com.nexus.parent.state.ScreenQuality
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.VoicePresetId
import org.json.JSONArray
import org.json.JSONObject

/**
 * StorageStore - Key-value persistence abstraction so `DeviceStorage` can run both on
 * Android `SharedPreferences` and in JVM unit tests with an in-memory map.
 */
interface StorageStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPrefsStorageStore(context: Context) : StorageStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("nexus_parent_prefs_v1", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }
}

class InMemoryStorageStore : StorageStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}

/**
 * DeviceStorage.kt - Persistent storage for paired child devices, cached 27-field telemetry,
 * and SettingsValues (mirroring `localStorage` persistence in Desktop `device-state.ts` & `settings`).
 */
class DeviceStorage(private val store: StorageStore) {

    companion object {
        private const val KEY_LAST_DEVICE = "nexus_last_connected_child_device"
        private const val KEY_DEVICE_HISTORY = "nexus_paired_devices_history_v1"
        private const val KEY_SETTINGS = "nexus_parent_settings_v1"
    }

    fun loadLastConnectedDevice(): DeviceRecord? {
        val raw = store.getString(KEY_LAST_DEVICE) ?: return null
        return runCatching { decodeDeviceRecord(JSONObject(raw)) }.getOrNull()
    }

    fun loadPairedDevicesHistory(): List<DeviceRecord> {
        val raw = store.getString(KEY_DEVICE_HISTORY)
        if (!raw.isNullOrBlank()) {
            val list = runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        decodeDeviceRecord(obj)?.let { add(it) }
                    }
                }
            }.getOrNull()
            if (list != null) return list
        }
        return loadLastConnectedDevice()?.let { listOf(it) } ?: emptyList()
    }

    fun savePairedDevice(record: DeviceRecord): List<DeviceRecord> {
        val currentHistory = loadPairedDevicesHistory().filterNot { it.rawId == record.rawId }
        val updatedHistory = listOf(record) + currentHistory

        store.putString(KEY_LAST_DEVICE, encodeDeviceRecord(record).toString())
        val arr = JSONArray()
        updatedHistory.forEach { arr.put(encodeDeviceRecord(it)) }
        store.putString(KEY_DEVICE_HISTORY, arr.toString())
        return updatedHistory
    }

    fun clearPairedDevicesHistory() {
        store.remove(KEY_LAST_DEVICE)
        store.remove(KEY_DEVICE_HISTORY)
    }

    fun loadSettings(): SettingsValues {
        val raw = store.getString(KEY_SETTINGS) ?: return SettingsValues()
        return runCatching {
            val j = JSONObject(raw)
            val defaults = SettingsValues()
            SettingsValues(
                defaultVoicePreset = enumOrDefault(j.optString("defaultVoicePreset"), defaults.defaultVoicePreset),
                noiseSuppression = j.optBoolean("noiseSuppression", defaults.noiseSuppression),
                loudspeakerVolume = enumOrDefault(j.optString("loudspeakerVolume"), defaults.loudspeakerVolume),
                screenQuality = enumOrDefault(j.optString("screenQuality"), defaults.screenQuality),
                preferredCameraLens = enumOrDefault(j.optString("preferredCameraLens"), defaults.preferredCameraLens),
                dvrBufferDuration = enumOrDefault(j.optString("dvrBufferDuration"), defaults.dvrBufferDuration),
                dvrClipFormat = enumOrDefault(j.optString("dvrClipFormat"), defaults.dvrClipFormat),
                hardwareAcceleration = j.optBoolean("hardwareAcceleration", defaults.hardwareAcceleration),
                downloadsDirectoryLabel = j.optString("downloadsDirectoryLabel", defaults.downloadsDirectoryLabel),
                downloadsTreeUri = j.optString("downloadsTreeUri", "").takeIf { it.isNotBlank() },
                askBeforeDownload = j.optBoolean("askBeforeDownload", defaults.askBeforeDownload),
                autoConnectLastDevice = j.optBoolean("autoConnectLastDevice", defaults.autoConnectLastDevice),
                keepScreenAwake = j.optBoolean("keepScreenAwake", defaults.keepScreenAwake),
                alertLowBattery = j.optBoolean("alertLowBattery", defaults.alertLowBattery),
                alertChildDisconnected = j.optBoolean("alertChildDisconnected", defaults.alertChildDisconnected),
                alertChildActiveCall = j.optBoolean("alertChildActiveCall", defaults.alertChildActiveCall)
            )
        }.getOrElse { SettingsValues() }
    }

    fun saveSettings(settings: SettingsValues) {
        val j = JSONObject().apply {
            put("defaultVoicePreset", settings.defaultVoicePreset.name)
            put("noiseSuppression", settings.noiseSuppression)
            put("loudspeakerVolume", settings.loudspeakerVolume.name)
            put("screenQuality", settings.screenQuality.name)
            put("preferredCameraLens", settings.preferredCameraLens.name)
            put("dvrBufferDuration", settings.dvrBufferDuration.name)
            put("dvrClipFormat", settings.dvrClipFormat.name)
            put("hardwareAcceleration", settings.hardwareAcceleration)
            put("downloadsDirectoryLabel", settings.downloadsDirectoryLabel)
            put("downloadsTreeUri", settings.downloadsTreeUri ?: "")
            put("askBeforeDownload", settings.askBeforeDownload)
            put("autoConnectLastDevice", settings.autoConnectLastDevice)
            put("keepScreenAwake", settings.keepScreenAwake)
            put("alertLowBattery", settings.alertLowBattery)
            put("alertChildDisconnected", settings.alertChildDisconnected)
            put("alertChildActiveCall", settings.alertChildActiveCall)
        }
        store.putString(KEY_SETTINGS, j.toString())
    }

    private fun encodeDeviceRecord(record: DeviceRecord): JSONObject {
        val t = record.telemetry
        val telJson = JSONObject().apply {
            put("androidVersion", t.androidVersion)
            put("sdkInt", t.sdkInt)
            put("brand", t.brand)
            put("appVersion", t.appVersion)
            put("batteryPercent", t.batteryPercent)
            put("isCharging", t.isCharging)
            put("chargingSource", t.chargingSource)
            put("batteryTemperatureC", t.batteryTemperatureC.toDouble())
            put("isPowerSaveMode", t.isPowerSaveMode)
            put("batteryOptimizationIgnored", t.batteryOptimizationIgnored)
            put("totalRamBytes", t.totalRamBytes)
            put("availableRamBytes", t.availableRamBytes)
            put("ramUsageText", t.ramUsageText)
            put("freeStorageBytes", t.freeStorageBytes)
            put("totalStorageBytes", t.totalStorageBytes)
            put("storageUsageText", t.storageUsageText)
            put("storageUsedPercent", t.storageUsedPercent.toDouble())
            put("networkState", t.networkState)
            put("simCarrier", t.simCarrier)
            put("isScreenOn", t.isScreenOn)
            put("screenResolution", t.screenResolution)
            put("ringerMode", t.ringerMode)
            put("deviceUptimeMs", t.deviceUptimeMs)
            put("deviceUptimeLabel", t.deviceUptimeLabel)
            put("isStealthMode", t.isStealthMode)
            put("isAppLockEnabled", t.isAppLockEnabled)
            put("securityGuardsText", t.securityGuardsText)
            put("grantedPermissionsCount", t.grantedPermissionsCount)
            put("totalPermissionsCount", t.totalPermissionsCount)
            put("survivalPermissionsText", t.survivalPermissionsText)
        }
        return JSONObject().apply {
            put("rawId", record.rawId)
            put("formattedId", record.formattedId)
            put("name", record.name)
            put("model", record.model)
            put("lastSyncedLabel", record.lastSyncedLabel)
            put("lastConnectedAt", record.lastConnectedAt)
            put("telemetry", telJson)
        }
    }

    private fun decodeDeviceRecord(json: JSONObject): DeviceRecord? {
        val rawId = FeatureRegistry.sanitizeChildIdDigits(json.optString("rawId"))
        if (rawId.length != 12) return null
        val tJson = json.optJSONObject("telemetry")
        val defaults = DeviceTelemetry()
        val telemetry = if (tJson != null) {
            DeviceTelemetry(
                androidVersion = tJson.optString("androidVersion", defaults.androidVersion),
                sdkInt = tJson.optInt("sdkInt", defaults.sdkInt),
                brand = tJson.optString("brand", defaults.brand),
                appVersion = tJson.optString("appVersion", defaults.appVersion),
                batteryPercent = tJson.optInt("batteryPercent", defaults.batteryPercent),
                isCharging = tJson.optBoolean("isCharging", defaults.isCharging),
                chargingSource = tJson.optString("chargingSource", defaults.chargingSource),
                batteryTemperatureC = tJson.optDouble("batteryTemperatureC", defaults.batteryTemperatureC.toDouble()).toFloat(),
                isPowerSaveMode = tJson.optBoolean("isPowerSaveMode", defaults.isPowerSaveMode),
                batteryOptimizationIgnored = tJson.optBoolean("batteryOptimizationIgnored", defaults.batteryOptimizationIgnored),
                totalRamBytes = tJson.optLong("totalRamBytes", defaults.totalRamBytes),
                availableRamBytes = tJson.optLong("availableRamBytes", defaults.availableRamBytes),
                ramUsageText = tJson.optString("ramUsageText", defaults.ramUsageText),
                freeStorageBytes = tJson.optLong("freeStorageBytes", defaults.freeStorageBytes),
                totalStorageBytes = tJson.optLong("totalStorageBytes", defaults.totalStorageBytes),
                storageUsageText = tJson.optString("storageUsageText", defaults.storageUsageText),
                storageUsedPercent = tJson.optDouble("storageUsedPercent", defaults.storageUsedPercent.toDouble()).toFloat(),
                networkState = tJson.optString("networkState", defaults.networkState),
                simCarrier = tJson.optString("simCarrier", defaults.simCarrier),
                isScreenOn = tJson.optBoolean("isScreenOn", defaults.isScreenOn),
                screenResolution = tJson.optString("screenResolution", defaults.screenResolution),
                ringerMode = tJson.optString("ringerMode", defaults.ringerMode),
                deviceUptimeMs = tJson.optLong("deviceUptimeMs", defaults.deviceUptimeMs),
                deviceUptimeLabel = tJson.optString("deviceUptimeLabel", defaults.deviceUptimeLabel),
                isStealthMode = tJson.optBoolean("isStealthMode", defaults.isStealthMode),
                isAppLockEnabled = tJson.optBoolean("isAppLockEnabled", defaults.isAppLockEnabled),
                securityGuardsText = tJson.optString("securityGuardsText", defaults.securityGuardsText),
                grantedPermissionsCount = tJson.optInt("grantedPermissionsCount", defaults.grantedPermissionsCount),
                totalPermissionsCount = tJson.optInt("totalPermissionsCount", defaults.totalPermissionsCount),
                survivalPermissionsText = tJson.optString("survivalPermissionsText", defaults.survivalPermissionsText)
            )
        } else {
            defaults
        }

        return DeviceRecord(
            rawId = rawId,
            formattedId = json.optString("formattedId", FeatureRegistry.formatChildId(rawId)),
            name = json.optString("name", "Android Device"),
            model = json.optString("model", "Android Device"),
            lastSyncedLabel = json.optString("lastSyncedLabel", "Cached snapshot"),
            lastConnectedAt = json.optLong("lastConnectedAt", 0L),
            telemetry = telemetry
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrElse { fallback }
    }
}
