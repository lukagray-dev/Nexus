# WebRTC Architecture & Media Pipeline

This document defines how real-time video, audio, and data streaming are architected in Nexus.

## 1. Core Architectural Decision: WebView-Native WebRTC

Nexus **does NOT use external Rust WebRTC libraries** (such as `webrtc-rs`, C++ GStreamer, or custom native bindings).

Instead, Nexus leverages the **native WebRTC stack already embedded in the OS WebView** (Chromium / Microsoft Edge WebView2 on Windows):

- **Identical to Electron, without the bloat**: The previous Electron implementation handled media seamlessly because Chromium has Google's battle-tested WebRTC implementation built-in. Tauri's WebView provides that exact same Chromium engine, but with an idle footprint of ~40MB RAM instead of ~400MB.
- **Hardware-Accelerated Decoding**: Video codecs (H.264, VP8, VP9) and audio codecs (Opus) are decoded directly by the user's GPU and OS media pipeline via standard HTML5 `<video>` and `<audio>` elements.
- **Zero Rust Transcoding Overhead**: The Rust backend (`src-tauri`) does not intercept, transcode, or buffer raw video frames. Media flows directly between the child Android device and the desktop WebView DOM.

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


## 3. Frontend Stream Integration Rules

When implementing actual streaming in `desktop/src/ts/`:

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
   - Use `peerConnection.createDataChannel('nexus-telemetry')` for bi-directional JSON messages (GPS coordinates, battery levels, command dispatch).

## 4. Key Takeaways for AI Agents

- **DO NOT** add Rust dependencies for video/audio codecs or WebRTC in `Cargo.toml`.
- **DO NOT** attempt to pass video frame buffers through Tauri IPC commands.
- **DO** write standard browser WebRTC JavaScript/TypeScript code using `window.RTCPeerConnection`.
- All decoding and rendering are 100% native to the WebView.
