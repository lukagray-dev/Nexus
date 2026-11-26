# Keyboard Monitoring & Keystroke Auditing Specification

Nexus provides on-demand, privacy-preserving real-time keystroke logging and text input auditing from the Android Child device to the Desktop Parent console:
1. **On-Demand DataChannel Streaming**: All keyboard auditing sessions and telemetry events are transmitted directly between devices across the WebRTC `nexus-control` DataChannel with **zero third-party cloud database persistence**.
2. **Debounced Session Aggregation**: Unlike fragile implementations that transmit on every single keypress and flood the SCTP buffer, Nexus consolidates typed sequences using an intelligent 1500ms inactivity window.
3. **Instant Flush on Focus & Window Shift**: Incomplete typing sequences are immediately committed and dispatched when the child shifts focus to another input field (`TYPE_VIEW_FOCUSED`) or switches applications (`TYPE_WINDOW_STATE_CHANGED`).
4. **Password & Credential Masking**: Password/PIN inputs are rigorously detected across event, node, and `InputType` flags. Passwords are never sent over the wire in plaintext; they are masked as `••••••••` with an explicit `isPassword: true` indicator.
5. **Dynamic App Resolution**: Dynamically resolves human-readable application labels (e.g., "Chrome", "WhatsApp", "YouTube") using Android's `PackageManager`, backed by an in-memory LRU cache.
6. **In-Memory Ring Buffer**: Retains up to 100 recent entries in a bounded in-memory buffer on the child device, enabling an instant `KEYBOARD_SNAPSHOT` when the parent console connects.
7. **Peer Disconnection Fail-Safe**: If the WebRTC connection drops or fails, `onPeerDisconnected()` automatically unregisters the accessibility listener, ensuring zero background CPU or battery leakage.

---

## 1. DataChannel Control Protocol

All Keyboard orchestration and telemetry transmissions occur over the dedicated `nexus-control` DataChannel (`ordered: true`).

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response / Action |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `KEYBOARD_ON` | Requests child to activate keyboard auditing and dispatch recent snapshot. | `KEYBOARD_STARTED` or `KEYBOARD_ERROR: <reason>` |
| **Parent → Child** | `KEYBOARD_OFF` | Requests child to halt keyboard auditing and unregister accessibility listener. | `KEYBOARD_STOPPED` |
| **Child → Parent** | `KEYBOARD_STARTED` | Confirms keyboard auditing is active. | Desktop changes Play spinner to Pause button (`pause.svg`), footer set to "Live Monitoring". |
| **Child → Parent** | `KEYBOARD_STOPPED` | Confirms keyboard auditing is detached. | Desktop changes Pause spinner to Play button (`play.svg`), footer set to "Monitoring Paused". |
| **Child → Parent** | `KEYBOARD_ERROR: <reason>` | Reports failure (e.g. Accessibility permission not enabled). | Desktop aborts spinner, reverts to IDLE, and displays error toast. |
| **Child → Parent** | `{"type":"KEYBOARD_SNAPSHOT", ...}` | Serialized JSON packet containing recent in-memory typing sessions. | Desktop populates audit card list. |
| **Child → Parent** | `{"type":"KEYBOARD_NEW", ...}` | Serialized JSON event for a newly completed or debounced typing sequence. | Desktop prepends record to audit list and updates telemetry counter. |

---

## 2. Telemetry Payload Schemas

### Keystroke Snapshot Packet (`KEYBOARD_SNAPSHOT`)

```json
{
  "type": "KEYBOARD_SNAPSHOT",
  "count": 2,
  "records": [
    {
      "id": "1732015000000_1",
      "app": "com.android.chrome",
      "appName": "Chrome",
      "text": "physics project solar panel efficiency research paper",
      "fieldHint": "Search or type URL",
      "fieldId": "com.android.chrome:id/url_bar",
      "inputType": "SEARCH",
      "isPassword": false,
      "timestamp": 1732015000000
    },
    {
      "id": "1732015100000_2",
      "app": "com.whatsapp",
      "appName": "WhatsApp",
      "text": "I will be at the library until 5pm",
      "fieldHint": "Message",
      "fieldId": "com.whatsapp:id/entry",
      "inputType": "MESSAGE",
      "isPassword": false,
      "timestamp": 1732015100000
    }
  ]
}
```

### Live New Keystroke Event (`KEYBOARD_NEW`)

```json
{
  "type": "KEYBOARD_NEW",
  "record": {
    "id": "1732015200000_3",
    "app": "com.example.bank",
    "appName": "Bank App",
    "text": "••••••••",
    "fieldHint": "Password",
    "fieldId": "com.example.bank:id/password_input",
    "inputType": "PASSWORD",
    "isPassword": true,
    "timestamp": 1732015200000
  }
}
```

---

## 3. Debounce & Session Aggregation Strategy

To prevent choking the WebRTC SCTP buffer and draining battery, `KeyboardController` employs a multi-tiered aggregation algorithm:

1. **Character Change Buffer**: When `TYPE_VIEW_TEXT_CHANGED` fires, the text is accumulated into an active field buffer (`activePackageName`, `activeFieldKey`, `activeText`, `activeHint`).
2. **1500ms Inactivity Delay**: A coroutine timer resets on each keypress. When 1.5 seconds pass without keystrokes in that field, the buffer flushes to a `KeyboardRecord`.
3. **Focus Shift Flush**: When `TYPE_VIEW_FOCUSED` occurs on a different field, any pending buffer from the previous field is committed immediately before switching.
4. **Window State Flush**: When `TYPE_WINDOW_STATE_CHANGED` indicates the user navigated to another app or activity, pending buffers flush instantly.
5. **Deduplication**: If the text content has not changed compared to the last dispatched record for that field, duplicate transmissions are suppressed.

---

## 4. Password Protection & Security

Child safety and privacy require that sensitive account credentials and banking passwords are protected:
- **Detection**: Evaluates `event.isPassword`, `source.isPassword`, and Android `InputType.TYPE_MASK_VARIATION` password flags (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`).
- **Sanitization**: All password contents are replaced with `••••••••` at the controller layer before serialization.
- **Console Rendering**: The Desktop Parent console renders an explicit `[PASSWORD]` badge and masked dots, alerting parents that an authenticated field was used without compromising account security.

---

## 5. Architectural Components

### Android Child Components (`com.nexus.child.components.keyboard`)
* **`KeyboardRecord.kt`**: Data model representing an audited typing event with high-performance JSON serialization and control character escaping.
* **`IKeyboardController.kt`**: Domain interface defining start, stop, permission check, snapshot retrieval, and cleanup contracts.
* **`KeyboardController.kt`**: Implementation of `IKeyboardController` and `NexusAccessibilityService.IAccessibilityKeyboardListener`. Coordinates debouncing, dynamic app label caching (`LruCache`), password detection, and ring buffer management.
* **`KeyboardCommandHandler.kt`**: WebRTC DataChannel command coordinator. Enforces accessibility permission validation, dispatches confirmations, sends snapshots, and handles real-time streams.
* **`NexusAccessibilityService.kt`**: System accessibility service registered for `typeViewTextChanged|typeViewFocused|typeWindowStateChanged`, dispatching raw events to `KeyboardController`.
* **`NexusBackgroundService.kt`**: Hosts controller instances, dispatches DataChannel messages, and performs fail-safe teardown on disconnect.

### Desktop Parent Components (`desktop/src/ts/right-sidebar/keyboard`)
* **`types.ts`**: Defines `KeyboardRecord` model and protocol command constants.
* **`state.ts`**: Implements reactive `keyboardMonitoringState` (`IDLE`, `STARTING`, `LIVE`, `STOPPING`) and in-memory `keyboardRecords` store.
* **`keyboard.ts`**: Renders pure Black & White audit panel with live telemetry counter, client-side search filtering, session history clearing, and empty states.
* **`right-sidebar.ts`**: Binds the bottom dock Play/Pause button with loading spinners, routes incoming DataChannel packets, and handles auto-reset on peer disconnection.

### Android Parent Components (`Nexus Parent/android`)
* **`data/monitor/MonitorPayloadParser.kt` & `MonitorCoordinator.kt`**: Sends `KEYBOARD_ON`/`KEYBOARD_OFF` with 20s confirmation timers, ingests `KEYBOARD_SNAPSHOT` and live `KEYBOARD_NEW` (capped at 500 entries matching Desktop), and shows masked toasts (`[PASSWORD]`) when `isPassword` is true.
* **`ui/monitor/keyboard/KeyboardPanel.kt`**: Renders live counter (`REAL-TIME KEYSTROKE CAPTURE • N LOGGED`), search filtering across app name, package name, captured text, and `fieldHint`, monospace input cards with `PASSWORD` danger badges, and local `Clear` action inside `MonitorHostScreen`.
