# Notification Monitoring Specification

Nexus provides on-demand, privacy-preserving real-time push notification and status alert surveillance from the Android Child device to the Desktop Parent console:
1. **On-Demand DataChannel Streaming**: All notification snapshots and real-time push events are transmitted directly between devices across the WebRTC `nexus-control` DataChannel with **zero cloud database persistence**.
2. **Initial Status Bar Snapshot**: Upon activation via the parent console's Play button, the Child invokes `NotificationListenerService.getActiveNotifications()` to capture and dispatch all currently active status bar notifications immediately.
3. **Real-Time Push Alert Streaming**: While active, `NexusNotificationListenerService` captures all newly posted or updated notifications across the entire Android OS and third-party apps (WhatsApp, Telegram, Instagram, Snapchat, Gmail, SMS, Discord, YouTube, etc.) and streams them as `NOTIFICATION_NEW` events.
4. **Rich Content & Messaging Thread Extraction**: Extracts titles, body text snippets, expanded `BigTextStyle` text, account subtext, action button labels ("Reply", "Mark as read"), system classification categories, ongoing persistent flags, and nested `MessagingStyle` conversation threads.
5. **Notification Dismissal Tracking**: Captures `onNotificationRemoved` events to update or remove dismissed notifications from the parent console in real time.
6. **DataChannel State Synchronization**: Interactive Play/Pause buttons in the right sidebar display tactical loading spinners (`loading.svg`) until the Child confirms service state transitions over the `nexus-control` DataChannel (`ordered: true`).
7. **Peer Disconnection Fail-Safe**: If the WebRTC connection drops or fails, `onPeerDisconnected()` automatically unregisters all callbacks and halts observation, guaranteeing zero background CPU or battery leakage.

---

## 1. DataChannel Control Protocol

All Notification orchestration and telemetry transmissions occur over the dedicated `nexus-control` DataChannel.

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response / Action |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `NOTIFICATION_ON` | Requests child to activate Notification monitoring and fetch active snapshot. | `NOTIFICATION_STARTED` or `NOTIFICATION_ERROR: <reason>` |
| **Parent → Child** | `NOTIFICATION_OFF` | Requests child to halt Notification observer and clear callbacks. | `NOTIFICATION_STOPPED` |
| **Child → Parent** | `NOTIFICATION_STARTED` | Confirms Notification monitoring is active. | Desktop changes Play spinner to Pause button (`pause.svg`), footer set to "Live Monitoring". |
| **Child → Parent** | `NOTIFICATION_STOPPED` | Confirms Notification observer is detached and stopped. | Desktop changes Pause spinner to Play button (`play.svg`), footer set to "Monitoring Paused". |
| **Child → Parent** | `NOTIFICATION_ERROR: <reason>` | Reports failure (e.g. Notification Access not granted). | Desktop aborts spinner, reverts to IDLE, and displays error toast. |
| **Child → Parent** | `{"type":"NOTIFICATION_SNAPSHOT", ...}` | Serialized JSON packet containing all currently active status bar notifications. | Desktop renders sorted notification cards and updates category counts. |
| **Child → Parent** | `{"type":"NOTIFICATION_NEW", ...}` | Serialized JSON event for a live received or updated notification. | Desktop prepends record to list, triggers toast notification. |
| **Child → Parent** | `{"type":"NOTIFICATION_REMOVED", ...}` | Serialized JSON event for a dismissed notification. | Desktop removes or marks the notification as dismissed. |

---

## 2. Telemetry Payload Schemas

### Active Notification Snapshot Packet (`NOTIFICATION_SNAPSHOT`)

```json
{
  "type": "NOTIFICATION_SNAPSHOT",
  "count": 2,
  "notifications": [
    {
      "id": 1042,
      "key": "0|com.whatsapp|1042|null|10123",
      "packageName": "com.whatsapp",
      "appName": "WhatsApp",
      "title": "Lucas",
      "text": "Are you picking me up after practice?",
      "bigText": "Are you picking me up after practice? Coach said we finish at 5:00 PM.",
      "subText": "lucas@chat",
      "category": "msg",
      "channelId": "messages",
      "postTime": 1732008450000,
      "isOngoing": false,
      "isClearable": true,
      "groupKey": "group_whatsapp_1042",
      "actions": ["Reply", "Mark as read"],
      "messages": [
        {
          "sender": "Lucas",
          "text": "Are you picking me up after practice? Coach said we finish at 5:00 PM.",
          "timestamp": 1732008450000
        }
      ]
    }
  ]
}
```

### Live New Notification Event (`NOTIFICATION_NEW`)

```json
{
  "type": "NOTIFICATION_NEW",
  "notification": {
    "id": 2045,
    "key": "0|com.instagram.android|2045|null|10189",
    "packageName": "com.instagram.android",
    "appName": "Instagram",
    "title": "emma_w",
    "text": "Sent you a reel by science_daily",
    "category": "social",
    "postTime": 1732008500000,
    "isOngoing": false,
    "isClearable": true,
    "actions": ["Reply"]
  }
}
```

### Notification Removed Event (`NOTIFICATION_REMOVED`)

```json
{
  "type": "NOTIFICATION_REMOVED",
  "key": "0|com.instagram.android|2045|null|10189",
  "packageName": "com.instagram.android",
  "timestamp": 1732008550000,
  "reason": 1
}
```

---

## 3. End-to-End Sequence Flow

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Play (Notifications Panel) -> Send "NOTIFICATION_ON" ----->|
      |   (UI shows loading.svg spinner, disables button)                     |
      |                                                                       |-- 2. Verify Notification Access permission
      |                                                                       |   (If denied: send NOTIFICATION_ERROR)
      |                                                                       |-- 3. Connect listener bridge to service
      |<-- 4. Send Confirmation: "NOTIFICATION_STARTED" ----------------------|
      |   (UI changes spinner to pause.svg, footer: "Live Monitoring")        |
      |<-- 5. Send Initial Snapshot: {"type":"NOTIFICATION_SNAPSHOT", ...} ---|
      |   (Desktop populates sorted cards, updates search index)              |
      |                                                                       |
      |                      [REAL-TIME NOTIFICATION ARRIVAL]                 |
      |                                                                       |-- 6. OS triggers onNotificationPosted(sbn)
      |<-- 7. Send Live Event: {"type":"NOTIFICATION_NEW", ...} --------------|
      |   (Desktop prepends card to list with toast notification)             |
      |                                                                       |
      |                      [USER DISMISSES NOTIFICATION]                    |
      |                                                                       |-- 8. OS triggers onNotificationRemoved(sbn)
      |<-- 9. Send Dismissal Event: {"type":"NOTIFICATION_REMOVED", ...} -----|
      |   (Desktop updates list item)                                         |
      |                                                                       |
      |-- 10. Click Pause -> Send "NOTIFICATION_OFF" ------------------------>|
      |   (UI shows loading.svg spinner, disables button)                     |
      |                                                                       |-- 11. Disconnect listener bridge
      |<-- 12. Send Confirmation: "NOTIFICATION_STOPPED" ---------------------|
      |   (UI changes spinner to play.svg, footer: "Monitoring Paused")       |
```

---

## 4. Component Architecture

### Android Client (`com.nexus.child.components.notification`)
- **`NexusNotificationListenerService`**: Extends Android `NotificationListenerService`. Declared in `AndroidManifest.xml` with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and intent filter `<action android:name="android.service.notification.NotificationListenerService" />`.
- **`INotificationController`**: Decoupled interface contract for notification access check, monitoring lifecycle, and active notification retrieval.
- **`NotificationController`**: Implements `INotificationController`, filters out self-app notifications and blank system stubs, caches app labels, and bridges events with `NexusNotificationListenerService`.
- **`NotificationCommandHandler`**: Coordinates DataChannel command parsing (`NOTIFICATION_ON`, `NOTIFICATION_OFF`), dispatches confirmation handshakes, active snapshots, and live updates.
- **`NotificationRecord`**: Data models for notification items, conversation messages, and removal events with direct JSON string serialization.

### Desktop Parent (`right-sidebar/notification`)
- **`types.ts`**: Protocol constants (`CMD_NOTIFICATION_ON`, `RESP_NOTIFICATION_STARTED`, etc.) and `NotificationRecord` interface.
- **`state.ts`**: In-memory `RightSidebarStateManager` store managing `notificationMonitoringState`, active snapshot population, sorting, prepending, and removal.
- **`notification.ts`**: Tactical surveillance UI component featuring search filtering, category pills (`All`, `Messages`, `Social`, `System`, `Media`), ongoing task indicators, and full detail inspection drawers.
- **`notification.css`**: Strict B&W tactical dark theme styling.
- **`right-sidebar.ts`**: Footer play/pause button state synchronization, loading spinner toggles, and DataChannel protocol event dispatcher.
