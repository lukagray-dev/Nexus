# WebRTC Implementation Specification

Nexus uses standard WebRTC for peer-to-peer streaming, control, and hardware telemetry:
* **Nexus Child (Android)**: Native Kotlin using Google WebRTC SDK (`com.dafruits:webrtc:123.0.0`) with hardware-accelerated video codecs (`DefaultVideoEncoderFactory`, `DefaultVideoDecoderFactory`) and EGL context.
* **Nexus Parent (Desktop)**: Tauri v2 frontend running inside WebView2 (Chromium). Uses native browser `RTCPeerConnection`, hardware video decoding via `<video>` elements, and Web Audio API with zero Rust IPC serialization overhead.
* **Nexus Parent (Android)**: Native Kotlin + Jetpack Compose using Google WebRTC SDK (`com.dafruits:webrtc:123.0.0`), `EglBase`, hardware-preferred codec sorting, `TextureView` + `EglRenderer` compositing, ordered `nexus-control` DataChannel, and `ParentConnectionService` (`dataSync` foreground service + `PARTIAL_WAKE_LOCK`) so sessions survive screen-off.
* **Signaling & Presence Transport**: Firebase Realtime Database (RTDB) coupled with Firebase Anonymous Authentication.
* **Dynamic Environment Configuration**: Root `.env` acts as the single source of truth for Firebase credentials:
  * Android: `build.gradle.kts` parses `.env` and injects credentials via `BuildConfig`.
  * Desktop: `scripts/load-env.js` parses `.env` at build time and generates `src/ts/webrtc/firebase-env.ts`.

## Fast Non-Trickle ICE Signaling Protocol (1–2 Second Connection)

Nexus operates in **Fast Non-Trickle ICE** mode. No separate `ice-candidates` node is populated in Firebase. All ICE candidates (host and STUN reflexive) are embedded directly inside the self-contained SDP payloads:
1. **Streamlined STUN Mirrors**: Configured with 2 high-availability Google STUN servers (`stun.l.google.com:19302` and `stun1.l.google.com:19302`). Eliminates multi-second delays caused by polling 5+ mirrors.
2. **`GATHER_ONCE` Policy on Android**: Android explicitly sets `continualGatheringPolicy = GATHER_ONCE`. This allows WebRTC's `iceGatheringState` to fire `COMPLETE` in ~200–400ms instead of hanging indefinitely on `GATHER_CONTINUALLY`.
3. **Early Completion Trigger**: Both Desktop and Android listen for `iceGatheringState === 'complete'`, transmitting the complete SDP payload as soon as gathering finishes (~200–400ms) with a 1000ms safety window fallback.
4. **Instant Media Renegotiation (< 100ms)**: When toggling microphone audio (`MIC_ON` / `MIC_OFF`), the WebRTC connection and ICE candidates are **already established**. Child and Parent exchange SDP offers/answers immediately without repeating ICE gathering.

### Firebase RTDB Node Structure

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
       |-- 1. Ensure Anonymous Auth ---------------------->|                                                 |
       |                                                   |<-- 1. Ensure Anonymous Auth --------------------|
       |                                                   |<-- 2. .info/connected -> "online-waiting" ------|
       |                                                   |<-- 3. Heartbeat (every 30s) + onDisconnect() ---|
       |-- 4. Validate Child ID & Online Status ---------->|                                                 |
       |-- 5. Clear Stale Session (/signaling/{id}) ------>|                                                 |
       |                                                   |                                                 |
       |-- 6. Create Offer + Add Transceivers              |                                                 |
       |-- 7. Gather Local ICE (GATHER_ONCE: 200-400ms)    |                                                 |
       |-- 8. Write Non-Trickle Offer SDP ---------------->|                                                 |
       |                                                   |-- 9. ValueEventListener delivers Offer -------->|
       |                                                   |                                                 |
       |                                                   |   10. setRemoteDescription(Offer)               |
       |                                                   |   11. createAnswer() -> setLocalDescription()   |
       |                                                   |   12. Gather Local ICE (COMPLETE: 200-400ms)    |
       |                                                   |<-- 13. Write Non-Trickle Answer SDP ------------|
       |<-- 14. ValueEventListener delivers Answer --------|                                                 |
       |                                                   |                                                 |
       |-- 15. setRemoteDescription(Answer)                |                                                 |
       |                                                                                                     |
       |<====================== 16. WebRTC Direct P2P Connected (1-2s total) ===============================>|
       |<====================== 17. DataChannel 'nexus-control' OPEN ========================================>|
       |                                                                                                     |
       |                                                       18. Lazy DeviceTelemetryManager activates     |
       |                                                           (registers BroadcastReceiver & Callbacks) |
       |<---------------------- 19. Initial TelemetrySnapshot JSON ------------------------------------------|
       |<---------------------- 20. Event-Driven Telemetry Updates (Battery/Network/Storage changes) --------|
```

## Persistent Background Presence Architecture (`.info/connected`)

To prevent the child device from incorrectly marking itself as `offline` in Firebase when running in the background:
1. **Firebase `/.info/connected` Observer**:
   - `DeviceStatusManager` monitors `/.info/connected`.
   - When mobile networks drop or transition (Wi-Fi to cellular, carrier 60-second NAT mapping timeout), Firebase backend executes the registered `onDisconnect()` hook.
   - As soon as the socket reconnects automatically in the background, `/.info/connected` triggers `connected == true`.
   - The child immediately re-arms the server-side `onDisconnect()` handlers and re-asserts `status: "online-waiting"` and `lastSeen: ServerValue.TIMESTAMP`.
2. **Indefinite Foreground Wake Lock**:
   - `WakeLockManager.acquireIndefinite()` is held by `NexusBackgroundService` while the foreground notification is active.
   - Prevents the Linux kernel from entering deep CPU sleep (Doze mode) that would otherwise kill network keep-alives with the screen turned off.

## Component Map

### Android Client (`Nexus Child/app/src/main/java/com/nexus/child/webrtc/`)

| Class | Responsibility |
| :--- | :--- |
| `AppConfig.kt` | Constants: Injected Firebase credentials from `BuildConfig` and 2 STUN mirrors. |
| `AuthManager.kt` | Programmatic `FirebaseApp` initialization and asynchronous anonymous sign-in. |
| `DeviceIdManager.kt` | Cryptographic 12-digit ID generator (`SecureRandom`), RTDB uniqueness check, and persistence. |
| `DeviceStatusManager.kt` | Presence state machine, `/.info/connected` reconnection listener, 30s heartbeat, and `onDisconnect()` hooks. |
| `DeviceTelemetryManager.kt` | Lazy, event-driven hardware monitor capturing battery level, charging status, network route, and storage. |
| `SignalingClient.kt` | RTDB listener for Non-Trickle offers and publisher for Non-Trickle answers. |
| `WebRtcManager.kt` | `PeerConnectionFactory`, hardware video decoders/encoders, fast GATHER_ONCE answer generator, zero-delay renegotiation, and DataChannel coordinator. |
| `WebRtcExtensions.kt` | Bidirectional mapper between `SessionDescription` and Firebase Maps. |

### Parent Android Client (`Nexus Parent/android/app/src/main/java/com/nexus/parent/`)

| Module | Responsibility |
| :--- | :--- |
| `webrtc/AppConfig.kt` | Reads injected `BuildConfig.FIREBASE_*` credentials from root `.env` + 2 STUN servers & timeouts. |
| `webrtc/AuthManager.kt` | Thread-safe programmatic `FirebaseApp` initialization & anonymous authentication. |
| `webrtc/SignalingClient.kt` | Validates `/child-devices/{childId}`, clears stale signaling, publishes offers & renegotiation answers, listens on `/answer`. |
| `webrtc/WebRtcManager.kt` | `PeerConnectionFactory` (`EglBase`), `GATHER_ONCE` ICE, `RECV_ONLY` video + `SEND_RECV` audio transceivers, codec sorting, 30s timeout, role-reversal renegotiation (`RENEGOTIATION_COMPLETE`), and `VideoTrack`/`AudioTrack` `StateFlow`s. |
| `webrtc/WebRtcExtensions.kt` | Bidirectional `SessionDescription` Firebase serializer and video codec priority sorter (`H264` > `VP9` > `VP8`). |
| `data/DeviceStateCoordinator.kt` | Orchestrates connect/disconnect, 27-field `DEVICE_TELEMETRY` merging, stealth sync, unexpected-drop 9-attempt auto-reconnect, startup auto-connect, and `ParentConnectionService` notifications. |
| `data/ReconnectManager.kt` | 9-attempt backoff timer (`10, 30, 60, 90, 120, 150, 180, 210, 240`s) with `retryNow()`, `cancel()`, and callbacks. |
| `data/TelemetryParser.kt` & `DeviceStorage.kt` | 27-field telemetry JSON parser/formatters and `SharedPreferences` device history + settings store. |

### Desktop Console (`Nexus Parent/desktop/`)

| Module | Responsibility |
| :--- | :--- |
| `src/ts/webrtc/firebase-config.ts` | Re-exports `FIREBASE_ENV`, 2 STUN mirrors, and lazy initializers for modular Web SDK services. |
| `src/ts/webrtc/auth-manager.ts` | WebView-side anonymous authentication manager joining concurrent login requests. |
| `src/ts/webrtc/signaling-client.ts` | Child validation, session cleaner, Non-Trickle offer publisher, and answer listener. |
| `src/ts/webrtc/webrtc-manager.ts` | Browser `RTCPeerConnection` coordinator, fast gathering wait (200-400ms), zero-delay renegotiation, DataChannel dispatcher. |
| `src/ts/shared/device-state.ts` | Central connection and telemetry state container. |
| `src/ts/left-sidebar/sidebar.ts` | Renders adaptive sidebar button and live telemetry popover. |
