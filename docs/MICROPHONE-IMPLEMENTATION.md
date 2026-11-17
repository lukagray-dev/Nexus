# Microphone & Voice Intercom Specification

Nexus provides bidirectional audio communication between the Desktop Parent and the Android Child:
1. **Child-to-Parent Audio Monitoring**: Live environmental audio streaming from the child phone's microphone to the desktop console.
2. **Parent-to-Child Voice Intercom**: Push-to-talk voice messaging from the parent desktop, broadcast through the child device's external loudspeaker at maximum hardware volume (bypassing silent or low volume settings).

## 1. DataChannel Control Protocol

All microphone orchestration and state synchronizations occur over the dedicated `nexus-control` DataChannel (`ordered: true`).

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `MIC_ON` | Requests child to start environmental microphone monitoring. | `MIC_STARTED` or `MIC_ERROR: <reason>` |
| **Parent → Child** | `MIC_OFF` | Requests child to stop environmental microphone monitoring. | `MIC_STOPPED` |
| **Parent → Child** | `RENEGOTIATION_COMPLETE` | Informs child that parent applied the role-reversal offer and ready for audio. | Triggers transmission of `MIC_STARTED` / `MIC_STOPPED` |
| **Parent → Child** | `PARENT_AUDIO_UNMUTE`<br>*(alias `PARENT_MIC_ON`)* | Parent started speaking; requests child loudspeaker boost. | `PARENT_AUDIO_UNMUTED` or `PARENT_AUDIO_ERROR: <reason>` |
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

## 3. Parent-to-Child Voice Intercom (Zero-Renegotiation Transceiver)

To enable instant push-to-talk without renegotiation lag, the desktop initiates the WebRTC session with a bidirectional audio transceiver:
`pc.addTransceiver('audio', { direction: 'sendrecv' })`

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Speak (UI shows loading.svg spinner)                       |
      |-- 2. getUserMedia({ audio: true, echoCancellation: true })            |
      |-- 3. audioTransceiver.sender.replaceTrack(localMicTrack)              |
      |-- 4. localMicTrack.enabled = true                                     |
      |-- 5. Send "PARENT_AUDIO_UNMUTE" (DataChannel) ----------------------->|
      |                                                                       |
      |                                                                       |-- 6. Backup saved volume, mode, speaker states
      |                                                                       |-- 7. audioManager.mode = MODE_IN_COMMUNICATION
      |                                                                       |-- 8. Route to TYPE_BUILTIN_SPEAKER (API 31+)
      |                                                                       |      or isSpeakerphoneOn = true (< API 31)
      |                                                                       |-- 9. Set STREAM_MUSIC & STREAM_VOICE_CALL to MAX
      |                                                                       |-- 10. remoteAudioTrack.setEnabled(true)
      |                                                                       |-- 11. Send "PARENT_AUDIO_UNMUTED" (DataChannel)
      |<-- 12. Receive "PARENT_AUDIO_UNMUTED" --------------------------------|
      |
      |-- 13. Flip loading.svg to active mic.svg + wave.svg animation
      |-- 14. Canvas spectrum renders active high-energy green pulse
      |
      |====== Live Parent Speech Broadcast via Phone Loudspeaker (MAX Vol) ===>|
      |
      |-- 15. Click Speak to Stop (UI shows loading.svg spinner)               |
      |-- 16. localMicTrack.enabled = false                                   |
      |-- 17. Send "PARENT_AUDIO_MUTE" (DataChannel) ------------------------>|
      |                                                                       |
      |                                                                       |-- 18. remoteAudioTrack.setEnabled(false)
      |                                                                       |-- 19. Restore previous volume levels & audio mode
      |                                                                       |-- 20. Send "PARENT_AUDIO_MUTED" (DataChannel)
      |<-- 21. Receive "PARENT_AUDIO_MUTED" ----------------------------------|
      |
      |-- 22. Speak button resets to idle state; wave animation stops
```

## 4. Fail-Safe Disconnection Teardown

If the WebRTC peer connection or DataChannel drops for any reason (network loss, app backgrounded, window closed, crash):
1. **Child Fail-Safe (`MicCommandHandler.onPeerDisconnected()`)**:
   - Automatically stops active microphone capture (`micController.stopMicrophone()`), releasing the Android `AudioRecord` hardware driver.
   - Automatically restores the child user's normal volume levels and audio mode (`parentAudioSpeakerManager.restoreNormalVolume()`), ensuring the loudspeaker does not remain stuck at maximum volume.
2. **Desktop Fail-Safe (`webrtcManager.onConnectionStateChange`)**:
   - Immediately cancels pending timeouts and resets both Play/Pause and Speak buttons to `IDLE`.
   - Releases the local microphone track (`parentAudioController.mute()`) and halts `<audio>` playback.

## 5. File & Component Mapping

| Subsystem | File Path | Responsibility |
| :--- | :--- | :--- |
| **Child Mic Capture** | `Nexus Child/.../components/mic/MicrophoneController.kt` | Native `AudioSource`, `AudioTrack`, `RECORD_AUDIO` permission, hardware teardown. |
| **Child Command Handler**| `Nexus Child/.../components/mic/MicCommandHandler.kt` | DataChannel command dispatch, state machine, fail-safe disconnect. |
| **Child Loudspeaker** | `Nexus Child/.../components/mic/ParentAudioSpeakerManager.kt` | `AudioManager` volume boost, `AudioDeviceInfo` speakerphone routing, volume restore. |
| **Child WebRTC Manager**| `Nexus Child/.../webrtc/WebRtcManager.kt` | Role-reversal renegotiation offer generation, `onAddTrack` remote audio routing. |
| **Desktop Intercom** | `Nexus Parent/desktop/src/ts/main-content/mic/parent-audio-controller.ts` | Local `getUserMedia`, `replaceTrack`, mute/unmute management. |
| **Desktop Mic UI** | `Nexus Parent/desktop/src/ts/main-content/mic/mic.ts` | State machine, `loading.svg` spinner, `wave.svg` animation, canvas spectrum visualizer. |
| **Desktop Styles** | `Nexus Parent/desktop/src/css/main-content/mic/mic.css` | Tactical layout, spinner sizing, active speaking glowing aura. |
| **Desktop WebRTC** | `Nexus Parent/desktop/src/ts/webrtc/webrtc-manager.ts` | `sendrecv` audio transceiver configuration, renegotiation answer routing. |
