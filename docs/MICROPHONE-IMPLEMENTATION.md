# Microphone & Voice Intercom Specification

Nexus provides bidirectional audio communication between the Desktop Parent and the Android Child:
1. **Child-to-Parent Audio Monitoring**: Live environmental audio streaming from the child phone's microphone to the desktop console.
2. **Parent-to-Child Voice Intercom**: Push-to-talk voice messaging from the parent desktop, broadcast through the child device's external loudspeaker at maximum hardware volume (bypassing silent or low volume settings).
3. **Real-Time Voice Changer**: Tactical DSP pipeline in the Desktop Parent providing 10 voice modification presets (Original, Robot Male, Robot Female, Human Male, Human Female, Male Monstar, Female Monstar, Overall Morphed, Echo, Alien).

## 1. DataChannel Control Protocol

All microphone orchestration and state synchronizations occur over the dedicated `nexus-control` DataChannel (`ordered: true`).

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `MIC_ON` | Requests child to start environmental microphone monitoring. | `MIC_STARTED` or `MIC_ERROR: <reason>` |
| **Parent → Child** | `MIC_OFF` | Requests child to stop environmental microphone monitoring. | `MIC_STOPPED` |
| **Parent → Child** | `RENEGOTIATION_COMPLETE` | Informs child that parent applied the role-reversal offer and ready for audio. | Triggers transmission of `MIC_STARTED` / `MIC_STOPPED` |
| **Parent → Child** | `PARENT_AUDIO_UNMUTE[:max\|high\|normal]`<br>*(alias `PARENT_MIC_ON[:mode]`)* | Parent started speaking; requests child loudspeaker routing at the configured target volume (100%, 85%, or preserve level; defaults to `max`). | `PARENT_AUDIO_UNMUTED` or `PARENT_AUDIO_ERROR: <reason>` |
| **Parent → Child** | `PARENT_AUDIO_VOLUME:<max\|high\|normal>` | Updates the child loudspeaker target volume policy live while connected or speaking. | `PARENT_AUDIO_VOLUME_SET:<mode>` |
| **Parent → Child** | `PARENT_AUDIO_MUTE`<br>*(alias `PARENT_MIC_OFF`)* | Parent stopped speaking; requests child normal volume restore. | `PARENT_AUDIO_MUTED` |
| **Child → Parent** | `MIC_STARTED` | Confirms child mic hardware is active and audio track is negotiated. | Desktop changes Play button to Pause. |
| **Child → Parent** | `MIC_STOPPED` | Confirms child mic hardware is shut down and audio track is removed. | Desktop changes Pause button to Play. |
| **Child → Parent** | `MIC_ERROR: <text>` | Reports mic failure (e.g. missing `RECORD_AUDIO` permission). | Desktop aborts spinner and reverts to Play. |
| **Child → Parent** | `PARENT_AUDIO_UNMUTED` | Confirms child configured loudspeaker and max volume. | Desktop Speak button flips spinner to active state. |
| **Child → Parent** | `PARENT_AUDIO_MUTED` | Confirms child restored previous user volume levels. | Desktop Speak button returns to idle state. |
| **Child → Parent** | `PARENT_AUDIO_ERROR: <text>` | Reports failure to configure loudspeaker. | Desktop Speak button reverts to idle state. |

## 2. Child-to-Parent Audio Monitoring (Role-Reversal Renegotiation)

Because the initial connection offer contains no microphone tracks, adding the child's microphone requires session renegotiation.

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Play -> Send "MIC_ON" (DataChannel) ---------------------->|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 2. Verify RECORD_AUDIO permission
      |                                                                       |-- 3. MicrophoneController.startMicrophone()
      |                                                                       |      (AudioSource + AudioTrack added to PC)
      |                                                                       |-- 4. Role Reversal: Child creates Offer SDP
      |                                                                       |      (includes m=audio track)
      |                                                                       |-- 5. Writes Offer to /signaling/{id}/answer
      |<-- 6. Reads Offer from /signaling/{id}/answer ------------------------|
      |                                                                       |
      |-- 7. pc.setRemoteDescription(ChildOffer)                              |
      |-- 8. pc.createAnswer() -> pc.setLocalDescription()                    |
      |-- 9. Writes Answer to /signaling/{id}/offer ------------------------->|
      |                                                                       |<-- 10. Reads Answer from /signaling/{id}/offer
      |                                                                       |    11. pc.setRemoteDescription(ParentAnswer)
      |-- 12. Send "RENEGOTIATION_COMPLETE" (DataChannel) ------------------->|
      |                                                                       |<-- 13. Send "MIC_STARTED" (DataChannel)
      |<-- 14. Receive "MIC_STARTED" -----------------------------------------|
      |
      |-- 15. Flip loading.svg spinner to pause.svg
      |-- 16. Attach incoming MediaStream to <audio> tag
      |-- 17. Web Audio API AnalyserNode drives 36-bar spectrum canvas
      |
      |<================ Live Child Microphone PCM Audio (WebRTC SRTP) ========|
```

### Clean Teardown (`MIC_OFF`)
When the parent pauses monitoring:
1. Desktop sends `MIC_OFF` and displays `loading.svg`.
2. Child executes strict disposal: `audioTrack.setEnabled(false)` → `peerConnection.removeTrack(sender)` → `audioTrack.dispose()` → `audioSource.dispose()`.
3. Child renegotiates without audio to close the hardware driver cleanly.
4. Once acknowledged, child sends `MIC_STOPPED`, desktop stops audio playback, and resets UI to Play.

---

## 3. Parent-to-Child Voice Intercom & Real-Time Voice Changer

To enable instant push-to-talk without renegotiation lag, the desktop initiates the WebRTC session with a bidirectional audio transceiver:
`pc.addTransceiver('audio', { direction: 'sendrecv' })`

### Real-Time Voice Changer DSP Pipeline
Parent voice audio is transformed client-side in the desktop Chromium WebView before WebRTC Opus encoding:

```text
Raw PC Mic -> MediaStreamSourceNode -> [Active Preset DSP Graph] -> MediaStreamDestinationNode -> WebRTC replaceTrack()
```

- **Dual-Delay Granular Pitch Shifter**: Modulated cyclic delay lines crossfaded with a triangle window shift pitch up/down with zero latency and zero garbage collection spikes.
- **Ring Modulator**: 60-95Hz carrier oscillators multiply vocal signals for cybernetic robot synthesis.
- **Hot-Switching**: Switching presets triggers an equal-power 25ms crossfade, enabling instant voice changes while actively speaking without dropping the stream.
- **LocalStorage Persistence**: Remembers the parent's chosen voice preset across sessions under `nexus_parent_intercom_voice`.

### Voice Preset Registry

| Preset ID | Preset Name | Timbre & DSP Architecture | Vector Icon |
| :--- | :--- | :--- | :--- |
| `original` | **Original** | Clean direct passthrough (gain = 1.0) | `original.svg` |
| `robot-male` | **Robot Male** | 62Hz ring modulator + 1050Hz bandpass + pitch down (0.88x) | `robot-male.svg` |
| `robot-female` | **Robot Female** | 92Hz ring modulator + 1600Hz bandpass + pitch up (1.18x) | `robot-female.svg` |
| `human-male` | **Human Male** | Masculine pitch shift (0.82x) + 180Hz lowshelf (+5.5dB) | `human-male.svg` |
| `human-female` | **Human Female** | Feminine pitch shift (1.22x) + 160Hz highpass + 2.8kHz presence | `human-female.svg` |
| `monster-male` | **Male Monstar** | Giant pitch shift (0.63x) + 130Hz sub-bass (+8dB) + saturation | `monster-male.svg` |
| `monster-female` | **Female Monstar** | Banshee pitch shift (0.86x) + 11Hz tremolo + 2.2kHz peak + slap delay | `monster-female.svg` |
| `overall-morphed` | **Overall Morphed** | Multi-formant peaking (750Hz & 2.4kHz) + 18ms chorus LFO | `overall-morphed.svg` |
| `echo` | **Echo** | 240ms spatial delay + 42% feedback decay + 2.8kHz lowpass air-damping | `echo.svg` |
| `alien` | **Alien** | 18Hz amplitude tremolo + 2.4kHz high-Q resonant peak + 135Hz carrier | `alien.svg` |

### Intercom Signaling Sequence

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Speak (or select voice from dropdown)                      |
      |-- 2. getUserMedia({ audio: true, echoCancellation: true })            |
      |-- 3. voiceChangerEngine.initialize(micStream)                         |
      |-- 4. audioTransceiver.sender.replaceTrack(processedTrack)             |
      |-- 5. processedTrack.enabled = true                                    |
      |-- 6. Send "PARENT_AUDIO_UNMUTE" (DataChannel) ----------------------->|
      |                                                                       |
      |                                                                       |-- 7. Backup saved volume, mode, speaker states
      |                                                                       |-- 8. audioManager.mode = MODE_IN_COMMUNICATION
      |                                                                       |-- 9. Route to TYPE_BUILTIN_SPEAKER (API 31+)
      |                                                                       |      or isSpeakerphoneOn = true (< API 31)
      |                                                                       |-- 10. Set STREAM_MUSIC & STREAM_VOICE_CALL to MAX
      |                                                                       |-- 11. remoteAudioTrack.setEnabled(true)
      |                                                                       |-- 12. Send "PARENT_AUDIO_UNMUTED" (DataChannel)
      |<-- 13. Receive "PARENT_AUDIO_UNMUTED" --------------------------------|
      |
      |-- 14. Flip loading.svg to active mic.svg + wave.svg animation
      |-- 15. Canvas spectrum renders active high-energy green pulse
      |
      |====== Live Processed Speech Broadcast via Phone Loudspeaker (MAX Vol) =>|
      |
      |-- 16. Click Speak to Stop (UI shows loading.svg spinner)              |
      |-- 17. processedTrack.enabled = false                                  |
      |-- 18. Send "PARENT_AUDIO_MUTE" (DataChannel) ------------------------>|
      |                                                                       |
      |                                                                       |-- 19. remoteAudioTrack.setEnabled(false)
      |                                                                       |-- 20. Restore previous volume levels & audio mode
      |                                                                       |-- 21. Send "PARENT_AUDIO_MUTED" (DataChannel)
      |<-- 22. Receive "PARENT_AUDIO_MUTED" ----------------------------------|
      |
      |-- 23. Speak button resets to idle state; wave animation stops
```

## 4. Fail-Safe Disconnection Teardown

If the WebRTC peer connection or DataChannel drops for any reason (network loss, app backgrounded, window closed, crash):
1. **Child Fail-Safe (`MicCommandHandler.onPeerDisconnected()`)**:
   - Automatically stops active microphone capture (`micController.stopMicrophone()`), releasing the Android `AudioRecord` hardware driver.
   - Automatically restores the child user's normal volume levels and audio mode (`parentAudioSpeakerManager.restoreNormalVolume()`), ensuring the loudspeaker does not remain stuck at maximum volume.
2. **Desktop Fail-Safe (`webrtcManager.onConnectionStateChange`)**:
   - Immediately cancels pending timeouts and resets both Play/Pause and Speak buttons to `IDLE`.
   - Closes the voice selection dropdown if open.
   - Releases the local microphone track (`parentAudioController.mute()`) and halts `<audio>` playback.

## 5. File & Component Mapping

| Subsystem | File Path | Responsibility |
| :--- | :--- | :--- |
| **Child Mic Capture** | `Nexus Child/.../components/mic/MicrophoneController.kt` | Native `AudioSource`, `AudioTrack`, `RECORD_AUDIO` permission, hardware teardown. |
| **Child Command Handler**| `Nexus Child/.../components/mic/MicCommandHandler.kt` | DataChannel command dispatch, state machine, fail-safe disconnect. |
| **Child Loudspeaker** | `Nexus Child/.../components/mic/ParentAudioSpeakerManager.kt` | `AudioManager` volume boost, `AudioDeviceInfo` speakerphone routing, volume restore. |
| **Child WebRTC Manager**| `Nexus Child/.../webrtc/WebRtcManager.kt` | Role-reversal renegotiation offer generation, `onAddTrack` remote audio routing. |
| **Voice Presets & Storage** | `Nexus Parent/.../mic/voice-presets.ts` | Preset contracts, metadata, SVG icon paths, and localStorage persistence. |
| **Voice Changer DSP Engine**| `Nexus Parent/.../mic/voice-changer-engine.ts` | Web Audio API node graphs, granular pitch shifter, crossfading, hot-switching. |
| **Desktop Intercom** | `Nexus Parent/.../mic/parent-audio-controller.ts` | Local `getUserMedia`, DSP pipeline hook, `replaceTrack`, mute/unmute management. |
| **Desktop Mic UI** | `Nexus Parent/.../mic/mic.ts` | Split intercom button, tactical dropdown menu, canvas spectrum visualizer. |
| **Desktop Styles** | `Nexus Parent/.../mic/mic.css` | Tactical layout, glassmorphic dropdown, active item badges, responsive placement. |
| **Desktop WebRTC** | `Nexus Parent/.../webrtc/webrtc-manager.ts` | `sendrecv` audio transceiver configuration, renegotiation answer routing. |
| **Voice Changer Tests** | `Nexus Parent/desktop/test/voice-changer.test.js` | Unit test suite covering presets, DSP lifecycle, and split button state machine. |
