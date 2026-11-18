# Camera Surveillance & Live DVR Specification

Nexus provides real-time high-definition video surveillance streaming from the Android Child device to the Desktop Parent console:
1. **Child-to-Parent Camera Monitoring**: Live hardware video streaming from the child phone's camera (defaulting to the **Rear lens**) to the desktop console.
2. **Zero-Renegotiation Architecture**: Unlike the microphone monitoring pipeline (which uses temporary role-reversal SDP renegotiation to attach native `AudioRecord` buffers), the camera video track is pre-registered on the `PeerConnection` in the initial session handshake. Turning the camera on/off or switching lenses toggles hardware capture and track enablement with **zero SDP renegotiation** and zero signaling roundtrips.
3. **YouTube-Style Live Stream DVR**: While live, the seek bar stays at 100% with a pulsing red "Live" indicator. As video streams, a background 30-minute rolling buffer records 1-second chunks. Dragging the scrubber backwards shifts playback into past footage while live recording continues uninterrupted in the background. Clicking the "Live" badge instantly snaps back to the real-time stream.
4. **DataChannel State Synchronization**: Interactive UI buttons (Play/Pause, Lens Switch) display tactical loading spinners until the Child confirms hardware state changes over the `nexus-control` DataChannel (`ordered: true`).

## 1. DataChannel Control Protocol

All camera orchestration and state synchronizations occur over the dedicated `nexus-control` DataChannel.

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `CAMERA_ON` | Requests child to activate camera hardware (Rear lens by default) and enable video track. | `CAMERA_STARTED` or `CAMERA_ERROR: <reason>` |
| **Parent → Child** | `CAMERA_OFF` | Requests child to deactivate camera hardware and mute video track. | `CAMERA_STOPPED` |
| **Parent → Child** | `CAMERA_SWITCH` | Requests child to toggle between Rear and Front camera lenses. | `CAMERA_SWITCHED: REAR` / `CAMERA_SWITCHED: FRONT` (or `CAMERA_SWITCHED`) |
| **Child → Parent** | `CAMERA_STARTED` | Confirms rear camera hardware is active and frames are flowing. | Desktop changes Play spinner to Pause button, starts Live timer and DVR buffer. |
| **Child → Parent** | `CAMERA_STOPPED` | Confirms camera hardware is shut down and video track is muted. | Desktop changes Pause spinner to Play button, displays paused overlay. |
| **Child → Parent** | `CAMERA_SWITCHED: <LENS>` | Confirms lens transition complete. | Desktop changes Lens spinner back to camera icon and updates text ("Rear" or "Front"). |
| **Child → Parent** | `CAMERA_ERROR: <text>` | Reports camera failure (e.g. missing `CAMERA` permission, hardware busy). | Desktop aborts spinner and reverts to previous idle state. |

## 2. Zero-Renegotiation Camera Architecture

Because the initial connection offer contains a `recvonly` video transceiver and the Android child pre-registers its `VideoTrack` before generating the initial SDP answer, the video media section (`m=video`) is established upfront during pairing.

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Initial Connection: Add Video Transceiver (recvonly) ----------->|
      |                                                                       |-- 2. Create VideoTrack (muted: enabled=false)
      |                                                                       |-- 3. pc.addTrack(videoTrack)
      |                                                                       |-- 4. Initial Answer SDP (includes m=video)
      |<-- 5. Receive Answer: WebRTC Direct P2P Connected ====================|
      |                                                                       |
      |-- 6. Click Play -> Send "CAMERA_ON" (DataChannel) ------------------->|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 7. Verify CAMERA permission
      |                                                                       |-- 8. Camera2Capturer starts Rear camera
      |                                                                       |-- 9. videoTrack.setEnabled(true)
      |                                                                       |-- 10. Send "CAMERA_STARTED" (DataChannel)
      |<-- 11. Receive "CAMERA_STARTED" --------------------------------------|
      |
      |-- 12. Flip loading.svg spinner to pause.svg
      |-- 13. Attach incoming MediaStream to <video id="camera-video-stream">
      |-- 14. Start background CameraDvrManager rolling buffer
      |
      |<================ Live Rear Camera Video Frames (WebRTC SRTP) =========|
      |                                                                       |
      |-- 15. Click Change Lens -> Send "CAMERA_SWITCH" (DataChannel) ------->|
      |   (UI shows loading.svg spinner on lens button)                       |
      |                                                                       |-- 16. Capturer switches to Front sensor
      |                                                                       |-- 17. Send "CAMERA_SWITCHED: FRONT"
      |<-- 18. Receive "CAMERA_SWITCHED: FRONT" ------------------------------|
      |
      |-- 19. Flip spinner back to camera.svg and update label to "Front"
      |
      |<================ Live Front Camera Video Frames (WebRTC SRTP) ========|
      |                                                                       |
      |-- 20. Click Pause -> Send "CAMERA_OFF" (DataChannel) ---------------->|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 21. capturer.stopCapture() + dispose()
      |                                                                       |-- 22. videoTrack.setEnabled(false)
      |                                                                       |-- 23. Send "CAMERA_STOPPED" (DataChannel)
      |<-- 24. Receive "CAMERA_STOPPED" --------------------------------------|
      |
      |-- 25. Flip loading.svg spinner to play.svg
      |-- 26. Show paused overlay and stop DVR buffer
```

## 3. YouTube-Style Live Stream DVR & Rolling Buffer

To provide YouTube-style scrubbing on a live WebRTC surveillance feed, Nexus uses a **dual-surface player architecture**:

```text
+------------------------------------------------------------------------------------+
| Camera Viewport Surface                                                            |
|                                                                                    |
|  [ Live Surface: <video id="camera-video-stream"> ] (Active while Live)            |
|   - Real-time WebRTC MediaStream via Edge WebView2 hardware decoder (0ms latency). |
|                                                                                    |
|  [ DVR Surface: <video id="camera-dvr-video"> ] (Active while Time-Shifted)        |
|   - Plays compiled WebM Blob from CameraDvrManager when user scrubs backwards.     |
+------------------------------------------------------------------------------------+
| Floating Player Controls Bar                                                       |
|                                                                                    |
|  [========== Progress Track (30-Minute Rolling Window) ==========O] 100% Live      |
|                                                                                    |
|  [ Play/Pause ]  [ (•) Live • 04:32 ]                     [ Rotate ] [ Rear ] ...  |
+------------------------------------------------------------------------------------+
```

### Buffering & Scrubbing Rules
1. **Background Rolling Buffer (`CameraDvrManager`)**:
   - While streaming, `MediaRecorder` captures 1-second chunks (`video/webm; codecs=vp8`).
   - Stores chunks with timestamps in a **30-minute rolling buffer** (~80-120MB RAM max).
   - Oldest chunks are pruned automatically (FIFO) once total buffer duration exceeds 30 minutes.
2. **Scrubbing Backwards**:
   - When the user drags the scrubber or clicks earlier in the progress track (`targetTime < totalDuration - 2s`):
     - The live WebRTC stream **continues recording in the background**.
     - All buffered chunks are compiled into a playable WebM Blob URL.
     - `#camera-dvr-video` renders past footage at the selected timestamp.
     - The Live dot turns dim gray and the label reads `Go Live`.
3. **Returning to Live**:
   - Clicking the Live badge or dragging the scrubber back to >= 98% of the timeline invokes `dvrManager.goLive()`.
   - The DVR surface is hidden, the live WebRTC surface is restored, and the red Live dot pulses.

## 4. Fail-Safe Disconnection Teardown

If the peer connection or DataChannel closes (network drop, app closed, child rebooted):
1. **Child Fail-Safe (`CameraCommandHandler.onPeerDisconnected()`)**:
   - Automatically halts active camera capture (`cameraController.stopCamera()`), releasing the Android `Camera2` hardware driver immediately.
2. **Desktop Fail-Safe (`webrtcManager.onConnectionStateChange`)**:
   - Cancels pending timeouts and resets the Play/Pause button to `IDLE` (`play.svg`).
   - Resets lens label to default `Rear`.
   - Stops the DVR recording buffer and displays the paused overlay.

## 5. File & Component Mapping

| Subsystem | File Path | Responsibility |
| :--- | :--- | :--- |
| **Child Camera Interface** | `Nexus Child/.../components/camera/ICameraController.kt` | Contract interface, `CameraLens` enum, hardware capture abstraction. |
| **Child Camera Hardware** | `Nexus Child/.../components/camera/CameraController.kt` | `Camera2Enumerator`, `CameraVideoCapturer`, `SurfaceTextureHelper`, EGL context, track pre-registration. |
| **Child Command Handler** | `Nexus Child/.../components/camera/CameraCommandHandler.kt` | DataChannel command dispatch, state machine, fail-safe disconnect. |
| **Child WebRTC Manager** | `Nexus Child/.../webrtc/WebRtcManager.kt` | `setOnPeerConnectionCreatedListener` hook for pre-registering video track. |
| **Desktop Types & Protocol**| `Nexus Parent/.../camera/types.ts` | State types (`CameraState`, `CameraLens`), command & response constants. |
| **Desktop DVR Manager** | `Nexus Parent/.../camera/camera-dvr.ts` | 30-minute rolling `MediaRecorder` buffer, time-shift seek, and live edge snapping. |
| **Desktop Camera UI** | `Nexus Parent/.../camera/camera.ts` | Dual-surface video player, loading spinners, YouTube-style scrubber, rotate, clip recorder. |
| **Desktop Styles** | `Nexus Parent/.../camera/camera.css` | Edge-to-edge layout, scrubber hover animations, pulsing/dimmed live dot, button spinners. |
| **Desktop WebRTC Manager**| `Nexus Parent/.../webrtc/webrtc-manager.ts` | `cameraStream` cache, `nexus-camera-stream` global event dispatch. |
