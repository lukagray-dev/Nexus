# Background Service & Multi-Tier Process Revival Specification

Nexus Child operates as an autonomous, persistent monitoring agent on Android. This document specifies the architecture, lifecycle management, Android 14+ Foreground Service compliance, persistent Firebase background presence, and multi-tier process resurrection defense system.

---

## 1. Core Architectural Separation

In Nexus Child, the UI and the monitoring engine are completely decoupled:

1. **`NexusBackgroundService` (`com.nexus.child.service`)**:
   - Manages the entire WebRTC DataChannel connection, Firebase anonymous authentication, and presence heartbeat.
   - Hosts all 10 hardware monitoring domain controllers (Camera, Microphone, Screen, Location, SMS, CallLog, Storage, Notification, Vibrate/Flash, Wallpaper).
   - Runs as a persistent Android Foreground Service with types `dataSync` and `location`.
   - Acquires an indefinite CPU `PARTIAL_WAKE_LOCK` via `WakeLockManager.acquireIndefinite()` to maintain socket connectivity when the display sleeps.
2. **`MainActivity` (`com.nexus.child`)**:
   - Pure presentation layer.
   - Does not create or destroy WebRTC or hardware controller instances.
   - Starts `NexusBackgroundService` and observes its `isConnected` `StateFlow` to update tactical LED status indicators.
   - Closing or destroying `MainActivity` has **zero effect** on the continuous background service.

---

## 2. Multi-Tier Process Revival Strategy

Android's LowMemoryKiller (LMK) and OEM custom battery optimizations (MIUI, OneUI, ColorOS, EMUI) terminate background apps under pressure. Nexus Child defends against process termination using 5 distinct layers:

```text
+----------------------------------------------------------------------------------------------------+
|                                    Process Revival Defense Tiers                                   |
+----------------------------------------------------------------------------------------------------+
| Tier 1: Boot & Update      | BootReceiver intercepts BOOT_COMPLETED, LOCKED_BOOT_COMPLETED,         |
|                            | QUICKBOOT_POWERON, and MY_PACKAGE_REPLACED. Starts service & watchdog. |
+----------------------------+-----------------------------------------------------------------------+
| Tier 2: User Unlock        | UnlockReceiver listens for ACTION_USER_PRESENT. Every time the child  |
|                            | unlocks their screen, the service state is checked and revived if dead|
+----------------------------+-----------------------------------------------------------------------+
| Tier 3: Task Kill Resurrect| onTaskRemoved() and onDestroy() schedule an immediate alarm with      |
|                            | AlarmManager (RTC_WAKEUP) delivering ACTION_RESTART to                |
|                            | ServiceRestartReceiver.                                               |
+----------------------------+-----------------------------------------------------------------------+
| Tier 4: Periodic Watchdog  | PersistentJobService runs every 15 minutes via Android JobScheduler   |
|                            | with setPersisted(true), surviving reboots and reviving dead services.|
+----------------------------+-----------------------------------------------------------------------+
| Tier 5: System Keep-Alive  | NexusAccessibilityService and NexusNotificationListenerService are    |
|                            | bound by Android system server; on events they verify and revive.     |
+----------------------------------------------------------------------------------------------------+
```

---

## 3. Persistent Firebase Presence Architecture (`/.info/connected`)

In cellular and long-running background environments, maintaining accurate status on Firebase Realtime Database requires proactive socket lifecycle management:

1. **The Mobile Network Drop Problem**:
   - Cellular carriers enforce aggressive idle NAT connection timeouts (typically 60–120 seconds).
   - Network handovers (Wi-Fi to LTE, cell tower switches, radio sleep) drop the TCP socket.
   - The Firebase server detects socket termination and fires the registered `onDisconnect()` hook, setting `status = "offline"`.
   - Without active presence listening, the client's internal socket reconnects silently in the background, but the status remains stuck in `offline`.
2. **The `/.info/connected` Solution in `DeviceStatusManager`**:
   - Subscribes to Firebase's special `/.info/connected` path.
   - Whenever `connected == true` fires:
     - Re-arms server-side `onDisconnect().setValue(STATUS_OFFLINE)` and `onDisconnect().setValue(ServerValue.TIMESTAMP)` hooks for the new socket session.
     - Immediately re-asserts the desired operational status (`online-waiting` or `connected`) and refreshes `lastSeen`.
   - The 30-second recurring heartbeat (`heartbeatRunnable`) continuously refreshes `lastSeen` and validates that `status` matches `currentDesiredStatus`.
3. **Indefinite Foreground CPU Wake Lock**:
   - `WakeLockManager.acquireIndefinite()` is held by `NexusBackgroundService` during active foreground service execution.
   - Prevents the Linux kernel from entering deep CPU sleep (Doze mode) that would otherwise freeze background coroutines and drop socket keep-alives with the screen turned off.

---

## 4. Auto-Start & OEM Battery Exemption

Vendor-specific OEM task killers terminate apps regardless of foreground status unless auto-start is granted.
Nexus Child includes `PermissionType.AUTO_START` in its 15-permission catalog:

- **Xiaomi / Redmi / Poco (MIUI & HyperOS)**: Dispatches `miui.intent.action.OP_AUTO_START` targeting `com.miui.permcenter.autostart.AutoStartManagementActivity`.
- **Huawei / Honor (EMUI)**: Dispatches `huawei.intent.action.STARTUP_MANAGER` targeting `com.huawei.systemmanager.optimize.process.ProtectActivity`.
- **Oppo / Realme (ColorOS)**: Dispatches `oppo.intent.action.OP_AUTO_START` targeting `com.coloros.safecenter.sysclear.ui.mainpage.MainActivity`.
- **Vivo / iQOO (Funtouch & OriginOS)**: Dispatches `vivo.intent.action.VIVO_AUTO_START` targeting `com.vivo.permissionmanager.activity.BgStartUpManagerActivity`.
- **Samsung (One UI)**: Dispatches `samsung.intent.action.AUTO_START` targeting `com.samsung.android.sm.ui.battery.BatteryActivity`.
- **Standard AOSP / Pixel**: Falls back to `ACTION_APPLICATION_DETAILS_SETTINGS` and standard `isIgnoringBatteryOptimizations()`.

---

## 5. Foreground Notification & Tactical Design System

In compliance with the project's **No-Emoji Policy** and tactical monochrome aesthetic:
- **Channel ID**: `nexus_core_service_channel`
- **Channel Name**: `Nexus Core Service`
- **Importance**: `IMPORTANCE_LOW` (silent, no vibration, public lockscreen visibility)
- **Small Icon**: `R.drawable.ic_shield_check` (Lucide vector drawable)
- **Ongoing**: `true` (pinned notification)
- **Status States**:
  - Waiting: *"Awaiting parent console connection"*
  - Connected: *"Live session active with parent console"*

---

## 6. File & Component Map

| File | Package | Responsibility |
| :--- | :--- | :--- |
| `NexusBackgroundService.kt` | `com.nexus.child.service` | Core Foreground Service hosting WebRTC and 10 monitoring domains |
| `ServiceNotificationManager.kt` | `com.nexus.child.service` | Creates notification channel and builds tactical ongoing notification |
| `WakeLockManager.kt` | `com.nexus.child.service` | Safe acquisition (indefinite or timeout) and release of CPU `PARTIAL_WAKE_LOCK` |
| `DeviceStatusManager.kt` | `com.nexus.child.webrtc` | Presence management via `/.info/connected`, 30s heartbeat, and `onDisconnect()` hooks |
| `BatteryOptimizationManager.kt` | `com.nexus.child.service` | Evaluates battery exemptions and resolves OEM auto-start intents |
| `BootReceiver.kt` | `com.nexus.child.service.receiver` | Intercepts boot, quickboot, and package updates to start service |
| `UnlockReceiver.kt` | `com.nexus.child.service.receiver` | Intercepts `ACTION_USER_PRESENT` to revive service upon screen unlock |
| `ServiceRestartReceiver.kt` | `com.nexus.child.service.receiver` | Resurrects service via `AlarmManager` after task removal or kill |
| `PersistentJobService.kt` | `com.nexus.child.service.scheduler` | 15-minute persistent `JobService` watchdog surviving reboots |
| `PermissionModel.kt` | `com.nexus.child.permission` | Includes `AUTO_START` capability in 15-permission catalog |
| `MainActivity.kt` | `com.nexus.child` | Decoupled UI observer displaying ID and connection LED |

---

## 7. Nexus Parent Android: Persistent Screen-Off Connection Service

Unlike **Nexus Parent Desktop** (which runs on PCs with `keepawake` window management and rarely faces OS-level socket suspension), **Nexus Parent Android** (`Nexus Parent/android`) must prevent Android Doze and background socket freezing whenever the parent locks their phone screen while monitoring or auto-reconnecting:

- **Application-Scoped WebRTC Ownership (`NexusParentApplication.kt`)**: Holds `DeviceStorage`, `WebRtcManager`, `DeviceStateCoordinator`, and `ParentStateHolder` at `Application` scope so configuration changes or backgrounding never drop peer connections or countdown loops.
- **`ParentConnectionService.kt` (`com.nexus.parent.service`)**:
  - Declared with `foregroundServiceType="dataSync"` and `POST_NOTIFICATIONS` / `WAKE_LOCK` permissions in `AndroidManifest.xml`.
  - Automatically started whenever a session is connecting, connected, or executing the 9-attempt backoff `ReconnectManager` loop; torn down cleanly on manual disconnect or reconnect exhaustion.
  - Holds a non-ref-counted `PowerManager.PARTIAL_WAKE_LOCK` (12-hour safety window) released immediately on stop/destroy.
- **`ParentNotificationManager.kt`**: Creates channel `nexus_parent_connection_channel` (`IMPORTANCE_LOW`, silent, ongoing, `R.drawable.ic_shield_check`) and updates status text dynamically (`"Nexus Parent Connecting"`, `"Connected to <Child>"`, or `"Retry X/9 in Ys"`).
