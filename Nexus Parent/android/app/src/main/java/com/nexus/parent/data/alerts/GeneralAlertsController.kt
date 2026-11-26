package com.nexus.parent.data.alerts

import com.nexus.parent.data.mic.CallStatePacket
import com.nexus.parent.data.mic.MicProtocol
import com.nexus.parent.state.ActiveCallAlert
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.SettingsValues

/**
 * SafetyAlertDispatcher - Platform-agnostic boundary for showing urgent parent alerts
 * (in-app toasts + high-priority Android notifications), making [GeneralAlertsController]
 * 100% testable on the JVM without booting Android NotificationManager.
 */
interface SafetyAlertDispatcher {
    fun showToast(message: String)
    fun postNotification(
        notificationId: Int,
        title: String,
        body: String,
        onlyAlertOnce: Boolean = false
    )
    fun cancelNotification(notificationId: Int)
}

/**
 * GeneralAlertsController.kt - Real-time safety alert evaluator matching Desktop `desktop-alerts.ts`.
 *
 * Hey friend! Manages runtime behavior for the 3 safety alert toggles under General & System:
 * 1. `alertChildActiveCall`: Synchronizes `CALL_ALERT_ON`/`CALL_ALERT_OFF` with Child, turns
 *    `CALL_STATE` packets into floating banner + OS notification, and suppresses repeats once Mic
 *    listening begins.
 * 2. `alertLowBattery`: Alerts once when Child battery drops below 15% while discharging, resetting
 *    only after charging starts or battery recovers to >= 20% (hysteresis anti-spam).
 * 3. `alertChildDisconnected`: Alerts via toast + OS notification on unexpected peer drops.
 */
class GeneralAlertsController(
    private val dispatcher: SafetyAlertDispatcher
) {

    companion object {
        const val LOW_BATTERY_THRESHOLD_PERCENT = 15
        const val LOW_BATTERY_RECOVERY_PERCENT = 20

        const val ALERT_ID_LOW_BATTERY = 4201
        const val ALERT_ID_DISCONNECTED = 4202
        const val ALERT_ID_ACTIVE_CALL = 4203
    }

    private var lowBatteryAlertedDeviceId: String? = null
    private var lastSyncedCallAlertEnabled: Boolean? = null

    fun resetSession() {
        lowBatteryAlertedDeviceId = null
        lastSyncedCallAlertEnabled = null
        dispatcher.cancelNotification(ALERT_ID_LOW_BATTERY)
        dispatcher.cancelNotification(ALERT_ID_ACTIVE_CALL)
    }

    fun syncCallAlertSettingToChild(
        enabled: Boolean,
        sendCommand: (String) -> Boolean,
        force: Boolean = false
    ): Boolean {
        if (!enabled) {
            dispatcher.cancelNotification(ALERT_ID_ACTIVE_CALL)
        }
        if (!force && lastSyncedCallAlertEnabled == enabled) {
            return true
        }
        val cmd = if (enabled) MicProtocol.CMD_CALL_ALERT_ON else MicProtocol.CMD_CALL_ALERT_OFF
        val sent = sendCommand(cmd)
        if (sent) {
            lastSyncedCallAlertEnabled = enabled
        }
        return sent
    }

    fun evaluateLowBattery(device: DeviceRecord?, settings: SettingsValues): Boolean {
        if (device == null) {
            lowBatteryAlertedDeviceId = null
            return false
        }

        val percent = device.telemetry.batteryPercent
        val isCharging = device.telemetry.isCharging
        if (isCharging || percent >= LOW_BATTERY_RECOVERY_PERCENT) {
            if (lowBatteryAlertedDeviceId == device.rawId) {
                dispatcher.cancelNotification(ALERT_ID_LOW_BATTERY)
            }
            lowBatteryAlertedDeviceId = null
            return false
        }

        if (percent !in 1 until LOW_BATTERY_THRESHOLD_PERCENT) {
            return false
        }

        if (lowBatteryAlertedDeviceId == device.rawId) {
            return false
        }

        if (!settings.alertLowBattery) {
            return false
        }

        lowBatteryAlertedDeviceId = device.rawId
        val title = "Child Device Low Battery"
        val body = "${device.name} battery is at $percent% — connect charger soon"
        dispatcher.showToast("$title: $body")
        dispatcher.postNotification(
            notificationId = ALERT_ID_LOW_BATTERY,
            title = title,
            body = body,
            onlyAlertOnce = true
        )
        return true
    }

    fun evaluateUnexpectedDisconnect(device: DeviceRecord?, settings: SettingsValues): Boolean {
        lowBatteryAlertedDeviceId = null
        lastSyncedCallAlertEnabled = null
        dispatcher.cancelNotification(ALERT_ID_ACTIVE_CALL)

        if (device == null || !settings.alertChildDisconnected) {
            return false
        }

        val title = "Child Device Disconnected"
        val body = "Lost P2P connection to ${device.name} (${device.formattedId}). Auto-reconnecting..."
        dispatcher.showToast(body)
        dispatcher.postNotification(
            notificationId = ALERT_ID_DISCONNECTED,
            title = title,
            body = body,
            onlyAlertOnce = false
        )
        return true
    }

    fun evaluateCallState(
        packet: CallStatePacket,
        settings: SettingsValues,
        isMicListeningOrStarting: Boolean,
        deviceName: String?
    ): ActiveCallAlert? {
        if (packet.state == "idle" || !settings.alertChildActiveCall || isMicListeningOrStarting) {
            dispatcher.cancelNotification(ALERT_ID_ACTIVE_CALL)
            return null
        }

        val alert = MicProtocol.toActiveCallAlert(packet) ?: return null
        val targetLabel = deviceName?.takeIf { it.isNotBlank() } ?: "Child device"
        dispatcher.postNotification(
            notificationId = ALERT_ID_ACTIVE_CALL,
            title = "${alert.title} ($targetLabel)",
            body = "${alert.subtitle} — Tap to open Parent and listen live",
            onlyAlertOnce = packet.isRepeat
        )
        return alert
    }

    fun onMicListeningActivated() {
        dispatcher.cancelNotification(ALERT_ID_ACTIVE_CALL)
    }
}
