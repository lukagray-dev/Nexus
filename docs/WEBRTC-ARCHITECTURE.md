# WebRTC Architecture & Media Pipeline

This document defines how real-time video, audio, and data streaming are architected in Nexus.

## 1. Core Architectural Decisions: WebView-Native (Desktop) & Zero-Blocker TextureView WebRTC (Parent Android)

- **Nexus Parent Desktop (`Nexus Parent/desktop`) does NOT use external Rust WebRTC libraries** (`webrtc-rs`, C++ GStreamer, or custom native bindings); it uses the WebView2/Chromium native `RTCPeerConnection` stack (`<video>`, `<audio>`, Web Audio API) with zero Rust transcoding overhead.
- **Nexus Parent Android (`Nexus Parent/android`) uses Google WebRTC Android SDK (`com.dafruits:webrtc:123.0.0`) paired with `NexusTextureVideoView` (`TextureView` + `org.webrtc.EglRenderer` + `VideoSink`)** rather than `SurfaceViewRenderer`. Because `SurfaceView` punches hardware holes through Compose layers, using a Compose-hosted `TextureView` guarantees floating Mic HUDs, Push-Drawer slides, DVR scrubbers, and 90° feed rotations compose without window-hole artifacts.

## 2. Separation of Responsibilities

```text
+-------------------------------------------------------------------------+
| Nexus Parent Desktop                                                    |
|                                                                         |
|  [ Tauri WebView2 Frontend (src/ts) ]                                   |
|   - Native `RTCPeerConnection` instance                                 |
|   - Hardware video rendering: `<video id="camera-video-stream">`       |
|   - Hardware screen rendering: `<video id="screen-video-stream">`        |
|   - Web Audio API `AudioContext` -> `<canvas id="mic-wave-canvas">`     |
|   - WebRTC DataChannel (telemetry, live pings, remote commands)         |
|                          ^                                              |
|                          | IPC Events (State & Window only)             |
|                          v                                              |
|  [ Tauri Rust Backend (src-tauri) ]                                     |
|   - Window controls, drag regions, and OS integration                   |
|   - Local persistence & database storage (logs, telemetry)              |
|   - Signaling relay / handshake assistance                              |
+--------------------------^----------------------------------------------+
                           |
                           | End-to-End Encrypted WebRTC (SRTP / SCTP)
                           v
+-------------------------------------------------------------------------+
| Nexus Child Android (Kotlin)                                            |
|  - Google WebRTC Android SDK (`org.webrtc.*`)                           |
|  - `Camera2Capturer` (Front / Rear lens hardware video stream)          |
|  - `MediaProjection` (Live screen mirroring hardware video stream)      |
|  - `AudioRecord` (Opus audio stream + Push-to-Talk speaker playback)    |
|  - `DataChannel` (Real-time GPS, keystrokes, notifications, app lock)   |
+-------------------------------------------------------------------------+
```

## 3. Fast Non-Trickle ICE Architecture (1–2s Connection)

Nexus operates in **Fast Non-Trickle ICE** mode:
1. **Self-Contained SDP Payloads**: All ICE candidates (host, local private IP, and STUN reflexive public IP/port) are gathered and embedded directly inside the SDP text. No auxiliary `ice-candidates` node is populated in Firebase.
2. **`GATHER_ONCE` Policy**: Android explicitly sets `continualGatheringPolicy = GATHER_ONCE`. WebRTC gathers all candidate interfaces once and fires `IceGatheringState.COMPLETE` in **200–400ms**, eliminating the indefinite stall caused by `GATHER_CONTINUALLY`.
3. **Streamlined STUN Mirrors**: Configured with 2 high-availability Google STUN servers (`stun.l.google.com:19302` and `stun1.l.google.com:19302`), preventing multi-second delays caused by polling 5+ mirrors.
4. **Early Gathering Completion**: Both Desktop and Android detect `iceGatheringState === 'complete'` and immediately transmit the SDP offer/answer to Firebase, with a 1000ms safety window fallback.
5. **Zero-Delay Media Renegotiation (< 100ms)**: When toggling the microphone (`MIC_ON` / `MIC_OFF`), the peer connection is already established and candidate routes are active. Both peers skip candidate gathering during renegotiation and swap the updated `m=audio` SDP offers/answers instantly.

## 4. Frontend Stream Integration Rules

When implementing streaming in `desktop/src/ts/`:

1. **Camera Feed (`main-content/camera/camera.ts`)**:
   - Receive the remote camera `MediaStreamTrack`.
   - Assign the track to a `MediaStream` and set `videoElement.srcObject = mediaStream`.
   - The video renders inside `<video id="camera-video-stream">` edge-to-edge.
2. **Screen Feed (`main-content/screen/screen.ts`)**:
   - Receive the remote screen `MediaStreamTrack`.
   - Set `videoElement.srcObject = mediaStream` on `<video id="screen-video-stream">`.
3. **Microphone Monitor (`main-content/mic/mic.ts`)**:
   - Receive the remote audio `MediaStreamTrack`.
   - Route audio through a Web Audio `AudioContext` and `AnalyserNode`.
   - Feed the frequency data into the existing 36-bar `<canvas id="mic-wave-canvas">` visualizer.
   - For Push-to-Talk ("Hold to Speak"): capture local microphone via `navigator.mediaDevices.getUserMedia({ audio: true })` and send it back over the peer connection to the child device.
4. **DataChannel**:
   - Use `peerConnection.createDataChannel('nexus-control')` for bidirectional JSON messages (telemetry, live pings, remote commands).

## 5. Key Takeaways for AI Agents

- **DO NOT** add Rust dependencies for video/audio codecs or WebRTC in `Cargo.toml`.
- **DO NOT** attempt to pass video frame buffers through Tauri IPC commands.
- **DO** write standard browser WebRTC JavaScript/TypeScript code using `window.RTCPeerConnection`.
- All decoding and rendering are 100% native to the WebView.
- Always use the Non-Trickle ICE protocol with embedded candidates in the SDP payload; do not populate separate `ice-candidates` nodes in Firebase.
