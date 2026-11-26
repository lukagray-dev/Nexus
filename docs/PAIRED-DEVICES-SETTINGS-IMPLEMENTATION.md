# Paired Devices Settings Implementation Specification

## Overview

This document specifies the **Paired Devices** master-detail settings page inside **Nexus Parent** (`Nexus Parent/desktop`) and the expanded `DEVICE_TELEMETRY` protocol emitted by **Nexus Child** (`Nexus Child/app`).

The **Paired Devices** page lists every child device the parent has ever connected to—with one-click **Connect** buttons for quick reconnection—and opens a strictly read-only detailed telemetry view when any device is selected (full-contrast live telemetry when online, grayed-out cached telemetry when offline).

---

## 1. Telemetry Expansion (`Nexus Child` -> `Nexus Parent`)

Defined in `Nexus Child/app/src/main/java/com/nexus/child/webrtc/DeviceTelemetryManager.kt` (`TelemetrySnapshot`):

| Field | Type | Source on Android | Purpose |
| :--- | :--- | :--- | :--- |
| `batteryPercent` | `Int` | `BatteryManager.EXTRA_LEVEL` / `EXTRA_SCALE` | Current charge percentage (`0..100`). |
| `isCharging` | `Boolean` | `BatteryManager.EXTRA_STATUS` | Whether device is charging or full. |
| `chargingSource` | `String` | `BatteryManager.EXTRA_PLUGGED` | `"AC"`, `"USB"`, `"Wireless"`, `"Plugged"`, or `"Battery"`. |
| `batteryTemperatureC` | `Double` | `BatteryManager.EXTRA_TEMPERATURE` | Battery temperature in °C. |
| `isPowerSaveMode` | `Boolean` | `PowerManager.isPowerSaveMode` | Android Battery Saver state. |
| `batteryOptimizationIgnored` | `Boolean` | `BatteryOptimizationManager` | Whether Nexus Child is whitelisted from Doze. |
| `networkState` | `String` | `ConnectivityManager` | `"WiFi"`, `"Mobile Data"`, `"Ethernet"`, `"Connected"`, or `"Offline"`. |
| `simCarrier` | `String` | `SubscriptionManager` (`READ_PHONE_STATE`) | Active SIM slot(s) and carrier name(s). |
| `deviceName`, `model`, `manufacturer`, `brand` | `String` | `Settings.Global.DEVICE_NAME` / `Build` | Device identity and hardware model. |
| `androidVersion`, `sdkInt`, `appVersion` | `String` / `Int` | `Build.VERSION.RELEASE`, `SDK_INT`, `BuildConfig.VERSION_NAME` | OS release, API level, and Child APK version. |
| `freeStorageBytes`, `totalStorageBytes` | `Long` | `StatFs` (`/data`) | Internal storage capacity. |
| `availableRamBytes`, `totalRamBytes` | `Long` | `ActivityManager.MemoryInfo` | System RAM availability. |
| `isScreenOn`, `screenResolution` | `Boolean` / `String` | `PowerManager.isInteractive`, `WindowManager` | Awake/asleep screen state and pixel dimensions. |
| `ringerMode` | `String` | `AudioManager.ringerMode` | `"Normal"`, `"Vibrate"`, or `"Silent"`. |
| `deviceUptimeMs` | `Long` | `SystemClock.elapsedRealtime()` | Time elapsed since last device reboot. |
| `isStealthMode`, `isAppLockEnabled` | `Boolean` | `StealthController`, `AppLockManager` | Launcher visibility and App Lock PIN status. |
| `grantedPermissionsCount`, `totalPermissionsCount` | `Int` | `PermissionManager` | Runtime + special permissions granted out of 14. |

### Event-Driven Updates
`DeviceTelemetryManager.start()` listens to `ACTION_BATTERY_CHANGED`, `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED`, `ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_POWER_SAVE_MODE_CHANGED`, `RINGER_MODE_CHANGED_ACTION`, and `ConnectivityManager.registerDefaultNetworkCallback` only while the WebRTC DataChannel is open.

---

## 2. Desktop State & Multi-Device History (`device-state.ts`)

- `DeviceStateManager` (`Nexus Parent/desktop/src/ts/shared/device-state.ts`) persists:
  - `nexus_last_connected_child_device`: Most recent device shortcut (`LastConnectedDevice`).
  - `nexus_paired_devices_history_v1`: Full array of all previously connected child devices (`LastConnectedDevice[]`), each retaining its own `lastTelemetry` (`LastDeviceTelemetrySnapshot`) and `lastConnectedAt` timestamp.
- Automatic migration seeds `nexus_paired_devices_history_v1` from `lastDevice` when upgrading from single-device storage.
- `getPairedDevicesHistory()` returns all paired devices sorted with any active online device first, followed by most recently connected devices descending.
- `getPairedDeviceRecord(rawId)` resolves a specific device record by its 12-digit ID.

---

## 3. Master-Detail Paired Devices UI & Live Sidebar Badge

- **Removed Dummy Elements**:
  - Deleted `device-enrollment.ts` (`Enroll New Child Phone`, `Clear Device Local Cache`).
  - Removed editable `deviceAlias` input and `Unpair` button from `device-card.ts`.
  - Removed `deviceAlias` from `SettingsValues` (`types.ts`) and stripped legacy `deviceAlias` on load (`state.ts`).
- **List View** (`src/ts/settings/main-content/paired-devices/paired-devices.ts`):
  - Displays all previously connected devices from `getPairedDevicesHistory()`.
  - Each row shows device name, model, 12-digit ID, status badge (`ONLINE`, `RECONNECTING`, `OFFLINE`), OS/battery/network summary, and an action button: a red **Disconnect** button (`.settings-btn-danger`) when the device is currently online, or a **Connect** button when offline (disconnecting any other active session cleanly before connecting to the chosen device).
  - Shows a grayed-out empty state card if no device has ever been paired.
- **Detail Telemetry View** (`paired-devices.ts` + `device-card.ts`):
  - Clicking any device row navigates to its read-only telemetry detail page with a **All Paired Devices** back button (and a **Connect** button if offline).
  - **Connected**: Full-contrast summary card (`ONLINE`) and three read-only groups (`DEVICE & OS`, `POWER & MEMORY`, `NETWORK & SYSTEM POSTURE`).
  - **Disconnected**: Grayed-out `.is-offline` card and telemetry groups (`OFFLINE • CACHED`) displaying that device's saved `lastTelemetry` snapshot.
- **Live Sidebar Badge** (`src/ts/settings/sidebar/sidebar.ts`): Reactive `Online` / `Reconnecting` / `Offline` badge synced with `deviceState`.

---

## 4. Verification

### Desktop Tests (`Nexus Parent/desktop`)
```powershell
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test
```
- **`test/paired-devices.test.js`**: Verifies multi-device history persistence, list rendering with `Connect` buttons, switching active sessions via `Connect`, row click navigation to the detail telemetry page, live -> grayed-out offline transitions, and sidebar badge updates.

### Android Unit Tests (`Nexus Child`)
```powershell
Set-Location "d:\Nexus\Nexus Child"
.\gradlew.bat testDebugUnitTest
```
- **`WebRtcProtocolTest.kt`**: Verifies `TelemetrySnapshot.toJsonString()` serialization across all 27 telemetry fields.
