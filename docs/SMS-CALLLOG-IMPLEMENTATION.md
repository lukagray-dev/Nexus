# SMS & Call Log Monitoring Specification

Nexus provides on-demand, privacy-preserving real-time telephony and message surveillance from the Android Child device to the Desktop Parent console:
1. **On-Demand DataChannel Streaming**: All historical logs and live updates are streamed directly between devices across the WebRTC `nexus-control` DataChannel with **zero cloud database persistence**.
2. **Initial History Fetch (Latest 100 Records)**: Upon session activation, the Child queries the device's Telephony and Telecommunications content providers to dispatch the latest 100 call history records and latest 100 SMS messages.
3. **Real-Time Live Event Streaming**: While active, background `ContentObserver` instances on the Android device listen for real-time incoming and outgoing phone calls and text messages, immediately transmitting updates across the DataChannel.
4. **Deep Contact & Caller ID Resolution**: Uses Android's `ContactsContract.PhoneLookup` with `READ_CONTACTS` to resolve live contact names, phone number labels (Mobile, Home, Work, School), and avatars, supplementing cached dialer names.
5. **DataChannel State Synchronization**: Interactive Play/Pause buttons in the right sidebar display tactical loading spinners (`loading.svg`) until the Child confirms hardware/provider state changes over the `nexus-control` DataChannel (`ordered: true`).
6. **Peer Disconnection Fail-Safe**: If the WebRTC connection drops or fails, `onPeerDisconnected()` automatically unregisters all observers and clears in-memory caches, guaranteeing zero background CPU or battery leakage.

## 1. DataChannel Control Protocol

All SMS and Call Log orchestration and telemetry transmissions occur over the dedicated `nexus-control` DataChannel.

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response / Action |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `CALLLOG_ON` | Requests child to activate Call Log monitoring and fetch latest 100 records. | `CALLLOG_STARTED` or `CALLLOG_ERROR: <reason>` |
| **Parent → Child** | `CALLLOG_OFF` | Requests child to halt Call Log observer and clear observers. | `CALLLOG_STOPPED` |
| **Child → Parent** | `CALLLOG_STARTED` | Confirms Call Log monitoring is active and observer registered. | Desktop changes Play spinner to Pause button (`pause.svg`), footer set to "Live Monitoring". |
| **Child → Parent** | `CALLLOG_STOPPED` | Confirms Call Log observer is unregistered and stopped. | Desktop changes Pause spinner to Play button (`play.svg`), footer set to "Monitoring Paused". |
| **Child → Parent** | `CALLLOG_ERROR: <reason>` | Reports failure (e.g. missing `READ_CALL_LOG` permission). | Desktop aborts spinner, reverts to IDLE, and displays error toast. |
| **Child → Parent** | `{"type":"CALLLOG_HISTORY", ...}` | Serialized JSON packet containing latest 100 call records. | Desktop renders sorted records and updates category counts. |
| **Child → Parent** | `{"type":"CALLLOG_NEW", ...}` | Serialized JSON event for a live incoming/outgoing call. | Desktop prepends record to list with live highlight animation. |
| **Parent → Child** | `SMS_ON` | Requests child to activate SMS monitoring and fetch latest 100 messages. | `SMS_STARTED` or `SMS_ERROR: <reason>` |
| **Parent → Child** | `SMS_OFF` | Requests child to halt SMS observer. | `SMS_STOPPED` |
| **Child → Parent** | `SMS_STARTED` | Confirms SMS observer registered and monitoring active. | Desktop changes Play spinner to Pause button, footer set to "Live Monitoring". |
| **Child → Parent** | `SMS_STOPPED` | Confirms SMS observer unregistered and stopped. | Desktop changes Pause spinner to Play button, footer set to "Monitoring Paused". |
| **Child → Parent** | `SMS_ERROR: <reason>` | Reports failure (e.g. missing `READ_SMS` permission). | Desktop aborts spinner, reverts to IDLE, and displays error toast. |
| **Child → Parent** | `{"type":"SMS_HISTORY", ...}` | Serialized JSON packet containing latest 100 SMS messages. | Desktop renders conversation list and updates search index. |
| **Child → Parent** | `{"type":"SMS_NEW", ...}` | Serialized JSON event for a live incoming/outgoing SMS. | Desktop prepends message to list and increments unread count. |

---

## 2. Telemetry Payload Schemas

### Call Log Telemetry Packet (`CALLLOG_HISTORY` & `CALLLOG_NEW`)

```json
{
  "type": "CALLLOG_HISTORY",
  "records": [
    {
      "id": "1042",
      "number": "+1 (555) 234-5678",
      "name": "Sarah (Mom)",
      "numberType": "Mobile",
      "callType": "incoming",
      "duration": 222,
      "timestamp": 1731938400000,
      "location": "Chicago, IL",
      "simId": "SIM 1",
      "features": ["HD_VOICE", "VIDEO"],
      "isRead": true
    }
  ]
}
```

* **`callType`**: `"incoming"` | `"outgoing"` | `"missed"` | `"rejected"` | `"blocked"` | `"voicemail"` | `"external"` | `"unknown"`.
* **`duration`**: Call duration in seconds (formatted as `mm:ss` on desktop).
* **`name`**: Saved contact name resolved from `CallLog.Calls.CACHED_NAME` or `ContactsContract.PhoneLookup`.

### SMS Telemetry Packet (`SMS_HISTORY` & `SMS_NEW`)

```json
{
  "type": "SMS_HISTORY",
  "chunkIndex": 0,
  "totalChunks": 2,
  "messages": [
    {
      "id": "5021",
      "address": "+1 (555) 345-6789",
      "senderName": "Dad",
      "body": "Don't forget your soccer gear after school.",
      "smsType": "inbox",
      "timestamp": 1731938450000,
      "dateSent": 1731938445000,
      "read": true,
      "simId": "SIM 1"
    }
  ]
}
```

* **`smsType`**: `"inbox"` (received) | `"sent"` (dispatched by child) | `"draft"` | `"failed"`.
* **`senderName`**: Contact name resolved via `PhoneLookup`.

---

## 3. End-to-End Sequence Flow

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Play (Call Log Panel) -> Send "CALLLOG_ON" (DataChannel) ->|
      |   (UI shows loading.svg spinner, disables button)                     |
      |                                                                       |-- 2. Verify READ_CALL_LOG permission
      |                                                                       |-- 3. Register ContentObserver on CallLog.Calls
      |                                                                       |-- 4. Send "CALLLOG_STARTED" (DataChannel)
      |<-- 5. Receive "CALLLOG_STARTED" --------------------------------------|
      |                                                                       |
      |-- 6. Flip loading.svg spinner to pause.svg ("Pause Monitoring")       |
      |-- 7. Footer updates to "Live Monitoring" (pulsing status-live-dot)    |
      |                                                                       |-- 8. Query CallLog.Calls (latest 100 DESC)
      |                                                                       |-- 9. Resolve ContactsContract.PhoneLookup names
      |                                                                       |-- 10. Send CALLLOG_HISTORY JSON (DataChannel)
      |<-- 11. Receive CALLLOG_HISTORY JSON ----------------------------------|
      |                                                                       |
      |-- 12. Populate call-list with 100 sorted records & filter counts       |
      |                                                                       |
      |   [Child receives an incoming call or makes an outgoing call]          |
      |                                                                       |-- 13. ContentObserver onChange() triggers
      |                                                                       |-- 14. Query newly inserted CallLog record
      |                                                                       |-- 15. Send CALLLOG_NEW JSON (DataChannel)
      |<-- 16. Receive CALLLOG_NEW JSON --------------------------------------|
      |                                                                       |
      |-- 17. Prepend new call record to call-list with live animation        |
      |                                                                       |
      |-- 18. Click Pause -> Send "CALLLOG_OFF" (DataChannel) --------------->|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 19. Unregister ContentObserver & clear cache
      |                                                                       |-- 20. Send "CALLLOG_STOPPED" (DataChannel)
      |<-- 21. Receive "CALLLOG_STOPPED" -------------------------------------|
      |                                                                       |
      |-- 22. Flip loading.svg spinner to play.svg ("Start Monitoring")       |
      |-- 23. Footer updates to "Monitoring Paused"                           |
```


---

## 4. Nexus Parent Android (`Nexus Parent/android`)

Android Parent implements identical `SMS` and `Call Log` surveillance over `nexus-control`:
- **Protocol & Parsing**: `MonitorProtocol.kt` (`CMD_SMS_ON/OFF`, `CMD_CALLLOG_ON/OFF`, `formatSimSlot()`, `formatCallDuration()`, `formatRelativeTime()`) and `MonitorPayloadParser.kt` parse multi-chunk `SMS_HISTORY`, `SMS_NEW`, `CALLLOG_HISTORY`, and `CALLLOG_NEW` packets deterministically.
- **Coordinator & Confirmation Spinner**: `MonitorCoordinator.kt` manages `IDLE -> STARTING -> LIVE -> STOPPING -> IDLE` states with 20s confirmation timeouts; `MonitorHostScreen.kt` renders `SpinningLoaderIcon` (`ic_loading_ring_track` + `ic_loading_ring_arc`, matching Desktop `loading.svg` 750ms rotation) between Play and Pause while awaiting Child confirmation.
- **Compose Panels**:
  - `ui/monitor/sms/SmsPanel.kt`: Live search filtering across sender/address/body, incoming/outgoing/unread/SIM badges, and full-message modal showing received timestamp and delivery/SIM metadata.
  - `ui/monitor/calllog/CallLogPanel.kt`: Dynamic count pills (`All`, `Missed`, `Incoming`, `Outgoing`, `Declined`) and expandable per-call drawer showing SIM slot, location, `VIDEO`/`HD` feature badges, direction, exact time, and `MM:SS` duration.
