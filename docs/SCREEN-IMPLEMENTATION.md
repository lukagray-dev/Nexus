# Screen Monitoring & Accessibility Autonomous Capture Specification

Nexus provides real-time Full HD (1920x1080 @ 30fps) screen surveillance and synchronized internal system audio streaming from the Android Child device to the Desktop Parent console:
1. **Autonomous Consent Approval via Accessibility**: Android's `MediaProjection` API requires a system consent prompt ("Start now" / "Entire screen") before screen capture begins. Nexus Child uses a registered `NexusAccessibilityService` and multi-lingual/OEM pattern matcher (`MediaProjectionAutoClickHelper`) to autonomously detect and click this dialog within seconds, requiring no manual touch input on the child phone.
2. **Zero-Renegotiation Video Architecture**: Screen video frames utilize Google WebRTC's `ScreenCapturerAndroid` and hardware EGL textures. The screen `VideoTrack` is attached to the pre-established video transceiver via `rtpSender.setTrack(screenVideoTrack, false)`. Turning screen monitoring on/off requires **zero SDP renegotiation** and zero signaling roundtrips.
3. **Synchronized System Audio over DataChannel**: On Android 10+ (API 29+), `SystemAudioCapturer` captures internal app and game audio (48kHz Mono 16-bit PCM in 20ms frames) using `AudioPlaybackCaptureConfiguration`. Hardware `MediaCodec` encodes these frames into Opus packets, which stream over the `nexus-control` DataChannel with prefix `OPUS_AUDIO:<base64>`. Desktop Parent decodes them with WebCodecs `AudioDecoder` and renders them with microsecond precision on Web Audio `AudioContext`.
4. **YouTube-Style Live Stream DVR**: While live, the seek bar stays at 100% with a pulsing red "Live" indicator. As video streams, a background 30-minute rolling buffer records 1-second WebM chunks. Dragging the scrubber backwards shifts playback into past footage while live recording continues uninterrupted in the background. Clicking the "Live" badge instantly snaps back to the real-time stream.
5. **DataChannel State Synchronization**: Interactive UI buttons (Play/Pause) display tactical loading spinners until the Child confirms hardware state changes over the `nexus-control` DataChannel (`ordered: true`).

---

## 1. DataChannel Control Protocol

All screen orchestration and state synchronizations occur over the dedicated `nexus-control` DataChannel.

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `SCREEN_ON[:1080p\|720p\|540p]` | Requests child to start screen capture at the configured quality preset (`1080p`/`720p` @ 30fps, `540p` @ 24fps; defaults to `1080p`) and system audio. | `SCREEN_STARTED` or `SCREEN_ERROR: <reason>` |
| **Parent → Child** | `SCREEN_QUALITY:<1080p\|720p\|540p>` | Dynamically updates active screen capture resolution and framerate while streaming. | `SCREEN_QUALITY_SET:<preset>` |
| **Parent → Child** | `SCREEN_OFF` | Requests child to halt screen capture and stop internal audio recording. | `SCREEN_STOPPED` |
| **Child → Parent** | `SCREEN_STARTED` | Confirms screen capture and audio pipeline are active. | Desktop flips Play spinner to Pause button, starts Live timer and DVR buffer. |
| **Child → Parent** | `SCREEN_STOPPED` | Confirms screen capture is halted and video track is restored. | Desktop flips Pause spinner to Play button, displays paused overlay. |
| **Child → Parent** | `SCREEN_ERROR: <text>` | Reports failure (e.g. missing accessibility permission, user cancellation). | Desktop aborts spinner and reverts to previous state. |
| **Child → Parent** | `OPUS_AUDIO:<base64>` | Streams a 20ms Opus-encoded internal audio frame. | Desktop decodes with WebCodecs and plays through `AudioContext`. |

---

## 2. End-to-End Sequence Diagram

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Initial WebRTC Connection (Non-Trickle ICE) -------------------->|
      |                                                                       |-- 2. Verify Accessibility Service
      |                                                                       |-- 3. Pre-register Video Transceiver
      |<-- 4. Initial Answer: Direct P2P WebRTC Connected ====================|
      |                                                                       |
      |-- 5. User Clicks Play -> Send "SCREEN_ON" (DataChannel) ------------->|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 6. Launch TransparentMediaProjectionActivity
      |                                                                       |-- 7. MediaProjection consent dialog opens
      |                                                                       |-- 8. AccessibilityService auto-clicks "Start now"
      |                                                                       |-- 9. ScreenCapturerAndroid starts 1080p @ 30fps
      |                                                                       |-- 10. rtpSender.setTrack(screenVideoTrack, false)
      |                                                                       |-- 11. SystemAudioCapturer hooks AudioPlaybackCapture
      |                                                                       |-- 12. Send "SCREEN_STARTED" (DataChannel)
      |<-- 13. Receive "SCREEN_STARTED" --------------------------------------|
      |
      |-- 14. Flip loading.svg spinner to pause.svg
      |-- 15. Attach incoming MediaStream to <video id="screen-video-stream">
      |-- 16. Start background ScreenDvrManager rolling buffer
      |
      |<================ Live Full HD Screen Frames (WebRTC SRTP) ============|
      |<================ OPUS_AUDIO:<base64> Audio Frames (DataChannel) ======|
      |   (Desktop decodes via WebCodecs and outputs through AudioContext)
      |                                                                       |
      |-- 17. User Clicks Pause -> Send "SCREEN_OFF" (DataChannel) ---------->|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 18. ScreenCapturerAndroid.stopCapture()
      |                                                                       |-- 19. SystemAudioCapturer and OpusEncoder stop
      |                                                                       |-- 20. rtpSender.setTrack(previousTrack, false)
      |                                                                       |-- 21. Send "SCREEN_STOPPED" (DataChannel)
      |<-- 22. Receive "SCREEN_STOPPED" --------------------------------------|
      |
      |-- 23. Flip loading.svg spinner to play.svg
      |-- 24. Show paused overlay and stop DVR buffer & audio player
```

---

## 3. Autonomous MediaProjection Consent Approval

### Android Manifest Registration
```xml
<service
    android:name=".accessibility.NexusAccessibilityService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>

<activity
    android:name=".components.screen.TransparentMediaProjectionActivity"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:excludeFromRecents="true"
    android:exported="false"
    android:windowSoftInputMode="stateHidden" />
```

### Multi-Lingual & OEM Matching
`MediaProjectionAutoClickHelper` searches the active window node hierarchy for target consent button texts:
- Standard: "Start now", "Entire screen", "Start recording or casting"
- Samsung OneUI: "Start now", "Cast screen"
- Xiaomi MIUI/HyperOS: "Start now", "Allow"
- Multilingual: "Commencer", "Iniciar ahora", "Jetzt starten", "Comincia adesso", "开始", "今すぐ開始", "지금 시작"

If a node with matching text or resource ID is found and `isClickable == true`, `node.performAction(AccessibilityNodeInfo.ACTION_CLICK)` is triggered immediately.

---

## 4. Internal Audio Capture & Hardware Opus Encoding

On Android 10+ (API 29+):
1. `AudioPlaybackCaptureConfiguration` is constructed using `MediaProjection`:
   ```kotlin
   val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
       .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
       .addMatchingUsage(AudioAttributes.USAGE_GAME)
       .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
       .build()
   ```
2. `AudioRecord` reads 48kHz Mono 16-bit PCM in 20ms frames (1920 bytes).
3. `OpusEncoder` feeds PCM frames into hardware `MediaCodec` configured with `audio/opus`:
   - Sample Rate: 48,000 Hz
   - Channel Count: 1 (Mono)
   - Bitrate: 32,000 bps
4. Encoded byte buffers are converted to Base64 and sent across DataChannel:
   ```text
   OPUS_AUDIO:AQIDBAUGBwgJCgsMDQ4PEA==
   ```
5. Parent desktop WebView2 uses `AudioDecoder`:
   - Config: `{ codec: 'opus', sampleRate: 48000, numberOfChannels: 1 }`
   - Output `AudioData` copies PCM frames into `AudioBufferSourceNode` scheduled smoothly on `AudioContext`.

---

## 5. YouTube-Style Live DVR Architecture

- **Rolling Buffer Window**: 30 minutes in memory (~80-120MB maximum RAM utilization via FIFO chunk eviction).
- **MediaRecorder Chunking**: Continuous 1-second WebM chunk generation.
- **Scrubber Synchronization**: Dragging backwards renders time-shifted video from the compiled WebM blob at that precise offset.
- **Snap-to-Live**: Clicking the pulsing "Live" indicator or scrubbing to `>= 98%` hides the DVR element and snaps back to the live WebRTC stream.

---

## 6. Verification & Test Suite

All components are strictly verified across both Android and Desktop test suites:
- **Android Child**: 101 tests pass (`ScreenComponentTest`, `PermissionManagerTest`, etc.).
- **Desktop Parent**: 82 tests pass (`screen-protocol.test.js`, `camera-protocol.test.js`, etc.).
- **Build Quality**: Zero compilation warnings, zero errors.
