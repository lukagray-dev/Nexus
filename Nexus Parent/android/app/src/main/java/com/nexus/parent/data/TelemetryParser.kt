package com.nexus.parent.data

import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import org.json.JSONObject
import java.util.Locale

/**
 * DeviceTelemetryUpdate - Partial or full hardware/posture telemetry update parsed from
 * Child's `DEVICE_TELEMETRY` DataChannel JSON payload (all 27 fields).
 */
data class DeviceTelemetryUpdate(
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val networkState: String? = null,
    val deviceName: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val androidVersion: String? = null,
    val freeStorageBytes: Long? = null,
    val totalStorageBytes: Long? = null,
    val sdkInt: Int? = null,
    val brand: String? = null,
    val appVersion: String? = null,
    val chargingSource: String? = null,
    val batteryTemperatureC: Float? = null,
    val isPowerSaveMode: Boolean? = null,
    val batteryOptimizationIgnored: Boolean? = null,
    val totalRamBytes: Long? = null,
    val availableRamBytes: Long? = null,
    val isScreenOn: Boolean? = null,
    val screenResolution: String? = null,
    val ringerMode: String? = null,
    val simCarrier: String? = null,
    val deviceUptimeMs: Long? = null,
    val isStealthMode: Boolean? = null,
    val isAppLockEnabled: Boolean? = null,
    val grantedPermissionsCount: Int? = null,
    val totalPermissionsCount: Int? = null
)

data class ChildUpdateStatusPacket(
    val updateAvailable: Boolean,
    val currentVersion: String? = null,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val deviceAbi: String? = null,
    val recommendedAssetUrl: String? = null
)

/**
 * TelemetryParser.kt - Parses and formats the 27-field telemetry packet matching Desktop `device-state.ts`.
 */
object TelemetryParser {

    fun parsePacket(rawMessage: String): DeviceTelemetryUpdate? {
        val trimmed = rawMessage.trim()
        if (!trimmed.startsWith("{")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            if (json.optString("type") != "DEVICE_TELEMETRY") return null

            DeviceTelemetryUpdate(
                batteryPercent = json.optNullableInt("batteryPercent"),
                isCharging = json.optNullableBoolean("isCharging"),
                networkState = json.optNullableString("networkState"),
                deviceName = json.optNullableString("deviceName"),
                model = json.optNullableString("model"),
                manufacturer = json.optNullableString("manufacturer"),
                androidVersion = json.optNullableString("androidVersion"),
                freeStorageBytes = json.optNullableLong("freeStorageBytes"),
                totalStorageBytes = json.optNullableLong("totalStorageBytes"),
                sdkInt = json.optNullableInt("sdkInt"),
                brand = json.optNullableString("brand"),
                appVersion = json.optNullableString("appVersion"),
                chargingSource = json.optNullableString("chargingSource"),
                batteryTemperatureC = json.optNullableDouble("batteryTemperatureC")?.toFloat(),
                isPowerSaveMode = json.optNullableBoolean("isPowerSaveMode"),
                batteryOptimizationIgnored = json.optNullableBoolean("batteryOptimizationIgnored"),
                totalRamBytes = json.optNullableLong("totalRamBytes"),
                availableRamBytes = json.optNullableLong("availableRamBytes"),
                isScreenOn = json.optNullableBoolean("isScreenOn"),
                screenResolution = json.optNullableString("screenResolution"),
                ringerMode = json.optNullableString("ringerMode"),
                simCarrier = json.optNullableString("simCarrier"),
                deviceUptimeMs = json.optNullableLong("deviceUptimeMs"),
                isStealthMode = json.optNullableBoolean("isStealthMode"),
                isAppLockEnabled = json.optNullableBoolean("isAppLockEnabled"),
                grantedPermissionsCount = json.optNullableInt("grantedPermissionsCount"),
                totalPermissionsCount = json.optNullableInt("totalPermissionsCount")
            )
        }.getOrNull()
    }

    fun applyUpdate(current: DeviceRecord, update: DeviceTelemetryUpdate): DeviceRecord {
        val t = current.telemetry

        val nextName = update.deviceName?.takeIf { it.isNotBlank() }
            ?: update.model?.takeIf { it.isNotBlank() }
            ?: current.name

        val nextModel = if (!update.model.isNullOrBlank()) {
            val mfg = update.manufacturer?.takeIf { it.isNotBlank() }?.let { "$it " } ?: ""
            "$mfg${update.model}".trim()
        } else {
            current.model
        }

        val freeStorage = update.freeStorageBytes ?: t.freeStorageBytes
        val totalStorage = update.totalStorageBytes ?: t.totalStorageBytes
        val storageText = if (freeStorage > 0L || totalStorage > 0L) {
            formatStorage(freeStorage, totalStorage)
        } else {
            t.storageUsageText
        }
        val storageUsedPct = if (totalStorage > 0L) {
            ((totalStorage - freeStorage).coerceAtLeast(0L).toFloat() / totalStorage.toFloat()).coerceIn(0f, 1f)
        } else {
            t.storageUsedPercent
        }

        val availRam = update.availableRamBytes ?: t.availableRamBytes
        val totalRam = update.totalRamBytes ?: t.totalRamBytes
        val ramText = if (availRam > 0L || totalRam > 0L) {
            formatRam(availRam, totalRam)
        } else {
            t.ramUsageText
        }

        val uptimeMs = update.deviceUptimeMs ?: t.deviceUptimeMs
        val uptimeLabel = if (uptimeMs > 0L) formatUptime(uptimeMs) else t.deviceUptimeLabel

        val nextAppLock = update.isAppLockEnabled ?: t.isAppLockEnabled
        val nextStealth = update.isStealthMode ?: t.isStealthMode
        val securityText = formatSecurityGuards(nextAppLock, nextStealth)

        val grantedPerms = update.grantedPermissionsCount ?: t.grantedPermissionsCount
        val totalPerms = update.totalPermissionsCount ?: t.totalPermissionsCount
        val permsText = if (totalPerms > 0) {
            "$grantedPerms / $totalPerms Granted"
        } else {
            t.survivalPermissionsText
        }

        val nextTelemetry = t.copy(
            androidVersion = update.androidVersion ?: t.androidVersion,
            sdkInt = update.sdkInt ?: t.sdkInt,
            brand = update.brand ?: t.brand,
            appVersion = update.appVersion ?: t.appVersion,
            batteryPercent = update.batteryPercent ?: t.batteryPercent,
            isCharging = update.isCharging ?: t.isCharging,
            chargingSource = update.chargingSource ?: t.chargingSource,
            batteryTemperatureC = update.batteryTemperatureC ?: t.batteryTemperatureC,
            isPowerSaveMode = update.isPowerSaveMode ?: t.isPowerSaveMode,
            batteryOptimizationIgnored = update.batteryOptimizationIgnored ?: t.batteryOptimizationIgnored,
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            ramUsageText = ramText,
            freeStorageBytes = freeStorage,
            totalStorageBytes = totalStorage,
            storageUsageText = storageText,
            storageUsedPercent = storageUsedPct,
            networkState = update.networkState ?: t.networkState,
            simCarrier = update.simCarrier ?: t.simCarrier,
            isScreenOn = update.isScreenOn ?: t.isScreenOn,
            screenResolution = update.screenResolution ?: t.screenResolution,
            ringerMode = update.ringerMode ?: t.ringerMode,
            deviceUptimeMs = uptimeMs,
            deviceUptimeLabel = uptimeLabel,
            isStealthMode = nextStealth,
            isAppLockEnabled = nextAppLock,
            securityGuardsText = securityText,
            grantedPermissionsCount = grantedPerms,
            totalPermissionsCount = totalPerms,
            survivalPermissionsText = permsText
        )

        return current.copy(
            name = nextName,
            model = nextModel,
            lastSyncedLabel = "Just now",
            telemetry = nextTelemetry
        )
    }

    fun parseChildUpdatePacket(rawMessage: String): ChildUpdateStatusPacket? {
        val trimmed = rawMessage.trim()
        if (!trimmed.startsWith("{")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            val type = json.optString("type")
            if (type != "CHILD_UPDATE_STATUS" && type != "CHILD_UPDATE_AVAILABLE") return null

            val available = if (type == "CHILD_UPDATE_AVAILABLE") {
                json.optNullableBoolean("updateAvailable") ?: true
            } else {
                json.optBoolean("updateAvailable", false)
            }
            ChildUpdateStatusPacket(
                updateAvailable = available,
                currentVersion = json.optNullableString("currentVersion")?.takeIf { it.isNotBlank() },
                latestVersion = json.optNullableString("latestVersion")?.takeIf { it.isNotBlank() },
                releaseUrl = json.optNullableString("releaseUrl")?.takeIf { it.isNotBlank() },
                deviceAbi = json.optNullableString("deviceAbi")?.takeIf { it.isNotBlank() },
                recommendedAssetUrl = json.optNullableString("recommendedAssetUrl")?.takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    fun applyChildUpdateStatus(current: DeviceRecord, packet: ChildUpdateStatusPacket): DeviceRecord {
        val nextAppVer = packet.currentVersion ?: current.telemetry.appVersion
        return current.copy(
            telemetry = current.telemetry.copy(
                appVersion = nextAppVer,
                updateAvailable = packet.updateAvailable,
                latestAvailableVersion = if (packet.updateAvailable) packet.latestVersion else null,
                updateReleaseUrl = if (packet.updateAvailable) packet.releaseUrl else null,
                deviceAbi = packet.deviceAbi ?: current.telemetry.deviceAbi,
                recommendedUpdateAssetUrl = if (packet.updateAvailable) packet.recommendedAssetUrl else null
            )
        )
    }

    fun formatStorage(freeBytes: Long, totalBytes: Long): String {
        if (totalBytes <= 0L) return "Unavailable"
        val freeGb = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB free of %.1f GB", freeGb, totalGb)
    }

    fun formatRam(availableBytes: Long, totalBytes: Long): String {
        if (totalBytes <= 0L) return "Unavailable"
        val availGb = availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB free / %.1f GB", availGb, totalGb)
    }

    fun formatUptime(uptimeMs: Long): String {
        if (uptimeMs <= 0L) return "Unavailable"
        val totalMinutes = uptimeMs / 60_000L
        if (totalMinutes < 1L) return "< 1m"
        val days = totalMinutes / (60L * 24L)
        val hours = (totalMinutes % (60L * 24L)) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L -> "${days}d ${hours}h ${minutes}m"
            hours > 0L -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun formatSecurityGuards(isAppLockEnabled: Boolean, isStealthMode: Boolean): String {
        val lockStr = if (isAppLockEnabled) "App Lock On" else "App Lock Off"
        val stealthStr = if (isStealthMode) "Stealth Hidden" else "Visible"
        return "$lockStr • $stealthStr"
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getLong(key) }.getOrNull()
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getBoolean(key) }.getOrNull()
    }
}
