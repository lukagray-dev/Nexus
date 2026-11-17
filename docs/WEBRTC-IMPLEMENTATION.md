# WebRTC Implementation Specification

Nexus uses standard WebRTC for peer-to-peer streaming, control, and hardware telemetry:
* **Nexus Child (Android)**: Native Kotlin using Google WebRTC SDK (`com.dafruits:webrtc:123.0.0`) with hardware-accelerated video codecs (`DefaultVideoEncoderFactory`, `DefaultVideoDecoderFactory`) and EGL context.
* **Nexus Parent (Desktop)**: Tauri v2 frontend running inside WebView2 (Chromium). Uses native browser `RTCPeerConnection`, hardware video decoding via `<video>` elements, and Web Audio API with zero Rust IPC serialization overhead.
* **Signaling & Presence Transport**: Firebase Realtime Database (RTDB) coupled with Firebase Anonymous Authentication.
* **Dynamic Environment Configuration**: Root `.env` acts as the single source of truth for Firebase credentials:
  * Android: `build.gradle.kts` parses `.env` and injects credentials via `BuildConfig`.
  * Desktop: `scripts/load-env.js` parses `.env` at build time and generates `src/ts/webrtc/firebase-env.ts`.

## Signaling Schema & Non-Trickle ICE Protocol

To support clean session renegotiation (e.g. microphone stream initiation and role reversal), Nexus strictly uses **Non-Trickle ICE**. No separate `ice-candidates` array is used during connection; candidate gathering completes locally before publishing self-contained SDP payloads.

### Firebase RTDB Node Structure (Presence Only)

Hardware telemetry is decoupled from Firebase and transmitted directly over WebRTC DataChannel to preserve privacy and reduce cloud bandwidth. Firebase RTDB stores only minimal presence metadata:

```text
/child-devices/{childId}/
  ├── status: "online-waiting" | "connecting" | "connected" | "offline"
  ├── lastSeen: <ServerTimestamp>
  └── createdAt: <ServerTimestamp>

/signaling/{childId}/
  ├── offer:  { type: "offer",  sdp: "<FullOfferSDPWithCandidates>" }
  └── answer: { type: "answer", sdp: "<FullAnswerSDPWithCandidates>" }
```

## End-to-End Connection & Telemetry Lifecycle

```text
Parent (Desktop)                                     Firebase RTDB                                    Child (Android)
       |                                                   |                                                 |
       |-- 1. Anonymous Auth ----------------------------->|                                                 |
       |                                                   |<-- 1. Anonymous Auth ---------------------------|
       |                                                   |<-- 2. Register ID & "online-waiting" -----------|
       |                                                   |<-- 3. Heartbeat (every 30s) + onDisconnect() ---|
       |-- 4. Validate Child ID & Online Status ---------->|                                                 |
       |-- 5. Clear Stale Session (/signaling/{id}) ------>|                                                 |
       |                                                   |                                                 |
       |-- 6. Create Offer + Add Transceivers (recvonly)   |                                                 |
       |-- 7. Gather Local ICE (wait iceGathering complete)|                                                 |
       |-- 8. Write Non-Trickle Offer SDP ---------------->|                                                 |
       |                                                   |-- 9. ValueEventListener delivers Offer -------->|
       |                                                   |                                                 |
       |                                                   |   10. setRemoteDescription(Offer)               |
       |                                                   |   11. createAnswer() -> setLocalDescription()   |
       |                                                   |   12. Gather Local ICE (wait COMPLETE)          |
       |                                                   |<-- 13. Write Non-Trickle Answer SDP ------------|
       |<-- 14. ValueEventListener delivers Answer --------|                                                 |
       |                                                                                                     |
       |-- 15. setRemoteDescription(Answer)                                                                  |
       |                                                                                                     |
       |<====================== 16. WebRTC Direct P2P Connected (SRTP / SCTP) ==============================>|
       |<====================== 17. DataChannel 'nexus-control' OPEN ========================================>|
       |                                                                                                     |
       |                                                       18. Lazy DeviceTelemetryManager activates     |
       |                                                           (registers BroadcastReceiver & Callbacks) |
       |<---------------------- 19. Initial TelemetrySnapshot JSON ------------------------------------------|
       |<---------------------- 20. Event-Driven Telemetry Updates (Battery/Network/Storage changes) --------|
```

## DataChannel & Telemetry Protocol

* **Label**: `nexus-control`
* **Ordered**: `true`
* **Binary Type**: `arraybuffer`
* **Initiator**: Parent desktop creates the channel; Child Android receives it via `PeerConnection.Observer.onDataChannel`.

### Live Hardware Telemetry Packet (`DEVICE_TELEMETRY`)

When `nexus-control` enters the `OPEN` state, the child's `DeviceTelemetryManager` lazily activates and immediately dispatches an initial hardware snapshot over the channel. Subsequent updates are event-driven (triggered by battery level changes, charger plug/unplug events, or network switches):

```json
{
  "type": "DEVICE_TELEMETRY",
  "batteryPercent": 92,
  "isCharging": true,
  "networkState": "WiFi",
  "deviceName": "Lucas Galaxy S24",
  "model": "SM-S928B",
  "manufacturer": "Samsung",
  "androidVersion": "14",
  "freeStorageBytes": 68719476736,
  "totalStorageBytes": 274877906944
}
```

### Desktop UI Telemetry Presentation

On the desktop parent console:
* `device-state.ts` parses `DEVICE_TELEMETRY` packets, updates the active `DeviceInfo`, and calculates human-readable storage text (`formatStorage`).
* **Sidebar Transformation**: The top left sidebar "Pair Child Device" button dynamically transforms into the connected 12-digit Child ID with a live green indicator pill.
* **Telemetry Dropdown**: Clicking the transformed button toggles a telemetry popover displaying real device name, hardware model, OS version, battery percentage + charging state, network connection type, storage usage, and a one-click Disconnect button.
* **Clean Empty State**: The redundant top telemetry bar was removed from the connected empty-state dashboard, leaving a focused quick-launch card grid.

## Component Map

### Android Client (`Nexus Child/app/src/main/java/com/nexus/child/webrtc/`)

| Class | Responsibility |
| :--- | :--- |
| `AppConfig.kt` | Constants: Injected Firebase credentials from `BuildConfig` and STUN server array (`stun:stun.l.google.com:19302`). |
| `AuthManager.kt` | Programmatic `FirebaseApp` initialization and asynchronous anonymous sign-in via coroutines. |
| `DeviceIdManager.kt` | Cryptographic 12-digit ID generator (`SecureRandom`), RTDB uniqueness check, and `SharedPreferences` persistence. Stores only presence metadata in Firebase. |
| `DeviceStatusManager.kt` | Presence state machine (`online-waiting`, `connected`, `offline`), 30s heartbeat, and server-side `onDisconnect()` hook. |
| `DeviceTelemetryManager.kt` | Lazy, event-driven hardware monitor capturing battery level, charging status, network route (WiFi vs Cellular), OS version, and internal storage capacities. Activates only while DataChannel is OPEN. |
| `SignalingClient.kt` | RTDB listener for `/signaling/{deviceId}/offer` and publisher for `/signaling/{deviceId}/answer`. |
| `WebRtcManager.kt` | `PeerConnectionFactory`, hardware video decoders/encoders, non-trickle answer generator, DataChannel lifecycle listener, and telemetry coordinator. |
| `WebRtcExtensions.kt` | Bidirectional mapper between `SessionDescription` and Firebase Maps. |

### Desktop Console (`Nexus Parent/desktop/`)

| Module | Responsibility |
| :--- | :--- |
| `scripts/load-env.js` | Build script that reads root `.env` and generates `src/ts/webrtc/firebase-env.ts`. |
| `src/ts/webrtc/firebase-config.ts` | Re-exports `FIREBASE_ENV`, STUN server list, and lazy initializers for modular Web SDK services. |
| `src/ts/webrtc/auth-manager.ts` | WebView-side anonymous authentication manager joining concurrent login requests. |
| `src/ts/webrtc/signaling-client.ts` | Child device validation, session cleaner, offer publisher, and answer listener. |
| `src/ts/webrtc/webrtc-manager.ts` | Browser `RTCPeerConnection` coordinator, `recvonly` transceivers, non-trickle ICE gatherer, and track dispatcher. |
| `src/ts/shared/device-state.ts` | Central connection and telemetry state container. Listens to `DEVICE_TELEMETRY` packets and updates device model, battery, and storage metrics. |
| `src/ts/left-sidebar/sidebar.ts` | Renders the adaptive top button transforming between "Pair Child Device" and connected ID with live telemetry popover. |

## Failure Recovery & Teardown

1. **Abrupt Phone Loss (Crash / Battery / Network Cut)**:
   * Firebase server-side `onDisconnect()` automatically triggers, writing `status: "offline"` and `lastSeen: <Timestamp>`.
   * Parent receives `pc.onconnectionstatechange === "disconnected"` or `"failed"` and resets UI to pairing state.
2. **Graceful Disconnect**:
   * Parent calls `webrtcManager.disconnect()`, closing DataChannel, closing PeerConnection, and nullifying `/signaling/{id}` paths.
   * Child transitions `DeviceStatusManager` back to `online-waiting` and cleanly stops `DeviceTelemetryManager`, releasing all system broadcast receivers and callbacks.
