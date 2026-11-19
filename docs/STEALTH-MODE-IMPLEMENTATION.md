# Stealth Mode & 1x1 Widget Implementation Specification

Nexus Child operates as an unobtrusive background monitoring agent on Android. Stealth Mode allows hiding the application icon from the device launcher and app drawer while keeping all background monitoring services, WebRTC telemetry, and hardware controllers active.

This document specifies the technical architecture, Android `PackageManager` mechanics, 1x1 widget recovery, DataChannel communication protocol, and remote desktop synchronization.

---

## 1. Architectural Overview

```text
+-----------------------------------------------------------------------------------------+
|                                    NEXUS STEALTH ARCHITECTURE                           |
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|   [ Child Device UI ]                     [ 1x1 Home Screen Widget ]                    |
|    - MainActivity (Tactical Card)          - StealthWidgetProvider                      |
|    - Confirmation Dialog                   - Persistent emergency toggle                |
|               |                                         |                               |
|               v                                         v                               |
|   +---------------------------------------------------------------------------------+   |
|   |                      StealthController (com.nexus.child.components.stealth)     |   |
|   |  - pm.setComponentEnabledSetting(MainActivity, DISABLED/ENABLED, DONT_KILL_APP)  |   |
|   |  - SharedPreferences persistence ("nexus_stealth_prefs")                       |   |
|   |  - Global state listener & AppWidgetManager broadcast dispatch                  |   |
|   +---------------------------------------------------------------------------------+   |
|                                       ^                                                 |
|                                       | Local / Remote                                  |
|                                       v                                                 |
|   +---------------------------------------------------------------------------------+   |
|   |                      StealthCommandHandler (WebRTC DataChannel)                |   |
|   |  - STEALTH_ON  -> STEALTH_ON_ACK  + STEALTH_STATUS:ON                           |   |
|   |  - STEALTH_OFF -> STEALTH_OFF_ACK + STEALTH_STATUS:OFF                          |   |
|   |  - GET_STEALTH_STATUS -> Immediate status report                                |   |
|   |  - Automatic sendStatusUpdate() on initial WebRTC connection                    |   |
|   +---------------------------------------------------------------------------------+   |
|                                       ^                                                 |
|                                       | WebRTC DataChannel (E2EE)                       |
|                                       v                                                 |
|   [ Nexus Parent Desktop Console ]                                                      |
|    - Left Sidebar #btn-stealth-mode button & Titlebar menu                              |
|    - Tactical confirmation modal dialog with Danger Red actions                         |
|    - Real-time ARMED / ACTIVE badge synchronization                                     |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Android `PackageManager` Mechanics

### 2.1 Launcher Visibility
An application icon appears in the user's launcher because its `MainActivity` declares:
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

When Stealth Mode is activated:
1. `StealthController` calls:
   ```kotlin
   packageManager.setComponentEnabledSetting(
       ComponentName(context, MainActivity::class.java),
       PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
       PackageManager.DONT_KILL_APP
   )
   ```
2. Android OS immediately notifies the launcher package to remove the icon from the app drawer and home screen.
3. Passing `PackageManager.DONT_KILL_APP` guarantees that Android does **not** kill `NexusBackgroundService` or `WatchdogService`. Background data streaming, location tracking, and audio/video surveillance continue without interruption.

### 2.2 Reversing Stealth Mode
Stealth mode can be reversed through two independent pathways:
1. **Locally via the 1x1 Widget**: Tapping the 1x1 home screen widget invokes `StealthWidgetProvider.ACTION_TOGGLE_STEALTH`, calling:
   ```kotlin
   packageManager.setComponentEnabledSetting(
       ComponentName(context, MainActivity::class.java),
       PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
       PackageManager.DONT_KILL_APP
   )
   ```
2. **Remotely via Parent Console**: The parent dispatches `STEALTH_OFF` over the WebRTC DataChannel, triggering `StealthCommandHandler` to restore launcher visibility.

---

## 3. 1x1 Stealth Control Widget

### 3.1 Purpose & Resilience
If the child device loses Wi-Fi or cellular connectivity, or if the parent is unable to reach the child, the app icon cannot be unhidden remotely. The **1x1 Stealth Control Widget** provides a fail-safe recovery mechanism:
* Android widgets remain visible on the home screen even when the main app launcher icon is disabled.
* The widget occupies a 1x1 grid cell (`40dp` x `40dp`) displaying the standard launcher icon.
* Tapping the widget immediately toggles stealth mode, displays a brief status Toast (`Stealth mode activated` or `Stealth mode deactivated`), and broadcasts the new state to all active widget instances and any connected parent console.

### 3.2 Widget Configuration
Declared in `res/xml/stealth_widget_info.xml`:
```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:initialLayout="@layout/widget_stealth"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:resizeMode="none"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:description="@string/widget_stealth_description"
    android:previewImage="@mipmap/ic_launcher" />
```
* `updatePeriodMillis="0"`: Eliminates battery-draining polling; the widget updates purely event-driven.

---

## 4. WebRTC DataChannel Communication Protocol

### 4.1 Message Specification
| Message Type | Direction | Payload | Description |
| :--- | :--- | :--- | :--- |
| **Command** | Parent -> Child | `STEALTH_ON` | Requests child to disable launcher component |
| **Command** | Parent -> Child | `STEALTH_OFF` | Requests child to enable launcher component |
| **Command** | Parent -> Child | `GET_STEALTH_STATUS` | Queries child for current stealth state |
| **Acknowledgment** | Child -> Parent | `STEALTH_ON_ACK` | Confirms execution of `STEALTH_ON` |
| **Acknowledgment** | Child -> Parent | `STEALTH_OFF_ACK` | Confirms execution of `STEALTH_OFF` |
| **Telemetry** | Child -> Parent | `STEALTH_STATUS:ON` | Informs parent that child is currently in stealth |
| **Telemetry** | Child -> Parent | `STEALTH_STATUS:OFF` | Informs parent that child is currently visible |

### 4.2 Initial Connection Synchronization
When the desktop parent establishes a WebRTC peer connection and opens the DataChannel:
1. `WebRtcManager` on the child triggers `onDataChannelOpened`.
2. `NexusBackgroundService` calls `stealthCommandHandler?.sendStatusUpdate()`.
3. Child transmits `STEALTH_STATUS:ON` or `STEALTH_STATUS:OFF` immediately.
4. `stealth-manager.ts` in the desktop console receives this packet and updates the UI button and badge (`ACTIVE` vs. `ARMED`) without requiring manual user polling.

---

## 5. User Interface & Safety Pop-Ups

### 5.1 Child App Home Screen
* `MainActivity` contains a dedicated **Stealth Operation** card.
* When visible: Button displays `ACTIVATE STEALTH MODE`.
* Tapping opens a `MaterialAlertDialogBuilder` confirmation dialog:
  - **Title**: *Activate Stealth Mode?*
  - **Message**: *This operation hides Nexus Child from the device app drawer and home screen launcher. You can unhide or reverse stealth mode at any time by: 1. Tapping the 1x1 Nexus Child widget on the home screen. 2. Sending the Unstealth command from the Parent Console.*
  - **Positive Action**: *Activate Stealth* -> Disables launcher component, shows confirmation Toast, and calls `finish()` to return the user to the home screen.
  - **Negative Action**: *Cancel* -> Dismisses dialog.

### 5.2 Parent Desktop Console
* Left sidebar contains `#btn-stealth-mode` with state indicator:
  - **Disarmed/Visible**: Label `Stealth Mode`, badge `ARMED`, danger border accent.
  - **Active/Hidden**: Label `Stealth Active`, badge `ACTIVE` in solid Danger Red (`#ef4444`), background tinted.
  - **Pending**: Label `Processing...`, badge `WAIT` (Yellow), button input disabled.
* Tapping opens the tactical modal dialog:
  - If activating: Explains that the child app icon will be hidden and surveillance continues. Action button styled in Danger Red (`.settings-btn-danger`).
  - If deactivating: Explains that the app icon will be restored to the child launcher. Action button styled in primary monochrome (`.settings-btn-primary`).

---

## 6. Verification & Automated Testing

### 6.1 Unit Test Coverage
* `com.nexus.child.components.stealth.StealthCommandHandlerTest`:
  - `handleCommand_stealthOn_shouldActivateStealthAndSendAck`: Validates command execution and dual responses (`STEALTH_ON_ACK` and `STEALTH_STATUS:ON`).
  - `handleCommand_stealthOff_shouldDeactivateStealthAndSendAck`: Validates deactivation and dual responses (`STEALTH_OFF_ACK` and `STEALTH_STATUS:OFF`).
  - `handleCommand_getStealthStatus_whenVisible_shouldSendStatusOff`: Validates status query when visible.
  - `handleCommand_getStealthStatus_whenHidden_shouldSendStatusOn`: Validates status query when hidden.
  - `sendStatusUpdate_initialConnectionSync_shouldDispatchAccurateState`: Validates initial WebRTC handshake sync.
  - `handleCommand_stealthOn_whenControllerFails_shouldSendFallbackStatus`: Validates failure handling.
* `com.nexus.child.widget.StealthWidgetProviderTest`:
  - Validates class symbol resolution and intent action constants.

### 6.2 Manual Testing Checklist
1. **Child App Stealth**: Launch `Nexus Child` -> Tap `ACTIVATE STEALTH MODE` -> Confirm dialog -> Verify app icon vanishes from app drawer.
2. **Widget Toggle**: Add 1x1 widget to home screen -> Tap widget -> Verify Toast appears and icon returns to app drawer.
3. **Parent Remote Control**: Connect parent console -> Verify initial state is synchronized -> Click `Stealth Mode` -> Confirm in modal -> Verify child icon disappears and parent button updates to `Stealth Active`.
4. **Parent Remote Restore**: Click `Stealth Active` on parent console -> Confirm in modal -> Verify child icon reappears and button returns to `Stealth Mode`.
