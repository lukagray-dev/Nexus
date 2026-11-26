# Active Call Alert Implementation Specification

## Overview

Nexus provides real-time **Active Call Alerts** so parents are notified the moment the child device receives an incoming call or initiates an outgoing call—enabling 1-click live microphone listening from the desktop console.

While modern Android restricts capturing the remote party's downstream voice directly during cellular calls, Nexus Parent's microphone pipeline captures the child's side of the conversation live (and captures the remote party too whenever the child enables speakerphone).

---

## 1. DataChannel Control & Telemetry Protocol

All call-state telemetry and alert configuration sync occurs over the `nexus-control` DataChannel (`ordered: true`):

| Direction | Command / Payload | Description |
| :--- | :--- | :--- |
| **Parent → Child** | `CALL_ALERT_ON` | Enables call-state monitoring & 30s repeating call alerts on the child device (default on connect). |
| **Parent → Child** | `CALL_ALERT_OFF` | Suppresses repeating call-state alerts while preserving lazy listener teardown on disconnect. |
| **Child → Parent** | `{"type":"CALL_STATE", ...}` | Live cellular call-state event (`ringing`, `active`, or `idle`), emitted immediately on transition and repeated every 30s while active. |

### `CALL_STATE` JSON Payload Schema

```json
{
  "type": "CALL_STATE",
  "state": "ringing",
  "direction": "incoming",
  "number": "+15551234567",
  "contactName": "Mom",
  "timestamp": 1731938400000,
  "isRepeat": false
}
```

* **`state`**: `"ringing"` (incoming call ringing) | `"active"` (`OFFHOOK`: answered incoming call or outgoing call) | `"idle"` (call ended).
* **`direction`**: `"incoming"` (`IDLE -> RINGING -> OFFHOOK`) | `"outgoing"` (`IDLE -> OFFHOOK`) | `"unknown"`.
* **`number` & `contactName`**: Optional enrichment resolved from `TelephonyManager.ACTION_PHONE_STATE_CHANGED` (`EXTRA_INCOMING_NUMBER`, requires `READ_CALL_LOG`) and `ContactsContract.PhoneLookup` (requires `READ_CONTACTS`). Call alerting still functions cleanly if only `READ_PHONE_STATE` is granted.
* **`isRepeat`**: `false` on immediate state transitions; `true` on 30-second repeating reminders.

---

## 2. Android Child Architecture (`Nexus Child`)

Located under `Nexus Child/app/src/main/java/com/nexus/child/components/callalert/`:

1. **`ICallAlertController.kt`**: Defines `CallAlertEvent` and the `ICallAlertController` contract.
2. **`CallAlertStateMachine.kt`**: Pure JVM-testable state machine tracking `IDLE` / `RINGING` / `OFFHOOK` transitions, inferring `incoming` vs `outgoing`, deduplicating redundant OEM broadcasts while permitting caller enrichment, and serializing JSON payloads.
3. **`CallAlertController.kt`**:
   - **API 31+ (Android 12+)**: Registers `TelephonyCallback` + `TelephonyCallback.CallStateListener`.
   - **API 29–30 (Android 10–11)**: Registers `PhoneStateListener.LISTEN_CALL_STATE`.
   - **Number & Contact Enrichment**: Registers a dynamic `ACTION_PHONE_STATE_CHANGED` receiver while connected and resolves contact names on a dedicated `HandlerThread` (`NexusCallAlertWorker`).
   - **30-Second Repeat Loop**: Posts a `30_000L` ms repeating reminder while `state` stays `ringing` or `active` and `alertEnabled` is `true`; cancels immediately on `idle`, `CALL_ALERT_OFF`, or disconnect.
   - **Connect-Time Snapshot**: Evaluates `telephonyManager.callState` immediately on DataChannel open so an ongoing call alerts right away.
4. **`CallAlertCommandHandler.kt`**: Wired into `NexusBackgroundService.kt` (`onDataChannelOpened`, `dispatchCommand`, `resetControllerStates`, `cleanupEngine`).

---

## 3. Desktop Parent Architecture (`Nexus Parent`)

1. **Settings Toggle (`types.ts` & `desktop-alerts.ts`)**:
   - Adds `alertChildActiveCall: boolean` (default: `true`) as **`Active Call Alert`** (`#setting-alert-active-call`) under **General & System → DESKTOP ALERT NOTIFICATIONS**.
   - Syncs `CALL_ALERT_ON` / `CALL_ALERT_OFF` to the child on `webrtcManager.onDataChannelOpen` and whenever the user toggles `alertChildActiveCall`.
2. **Actionable In-App Banner & Native OS Notification (`desktop-alerts.ts` & `tokens.css`)**:
   - Displays `#nexus-active-call-alert` with `phone-call.svg`, caller/direction details, a **`Turn On Mic`** button (`#btn-call-alert-mic-on` with `mic.svg`), and a dismiss button (`x.svg`).
   - Dispatches `showDesktopNotification(title, body)` via Rust (`show_desktop_notification`) so alerts appear even when minimized to the system tray.
3. **1-Click Mic Activation (`mic.ts`)**:
   - `startChildMicMonitoring()` opens the floating Microphone panel and immediately dispatches `MIC_ON` (even if the panel was previously unmounted).
   - `isChildMicListeningOrConnecting()` suppresses repeating call alerts and automatically dismisses the call alert banner as soon as Mic monitoring enters `STARTING` or `STREAMING`.
   - Call termination (`state: "idle"`) or device disconnection clears the banner automatically.

---

## 4. Verification & Test Coverage

### Desktop (`Nexus Parent/desktop`)
- **`test/settings.test.js`**: Verifies `alertChildActiveCall` default (`true`) and 4-toggle rendering under General & System.
- **`test/desktop-alerts.test.js`**: Verifies `CALL_STATE` incoming/outgoing banner rendering, `Turn On Mic` 1-click `MIC_ON` dispatch, repeat reminder flow, suppression when Mic is live, `idle` dismissal, and `CALL_ALERT_ON` / `CALL_ALERT_OFF` sync.

```powershell
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test
```

### Android (`Nexus Child`)
- **`CallAlertComponentTest.kt`**: Verifies `CallAlertStateMachine` incoming/outgoing lifecycles, duplicate broadcast deduplication + enrichment, repeat reminder payloads, JSON serialization, and `CallAlertCommandHandler` lifecycle.

```powershell
Set-Location "d:\Nexus\Nexus Child"
.\gradlew.bat testDebugUnitTest
```
