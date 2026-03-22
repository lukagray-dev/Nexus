# Microphone Streaming Component

## Overview

The Microphone component enables real-time audio streaming from the Child device to the Parent device over WebRTC. It uses a **hybrid role-reversal approach** during renegotiation to ensure proper native audio hardware initialization.

## Architecture

### Connection Roles

**Initial Connection:**
- Parent = Offerer (creates initial offer)
- Child = Answerer (creates answer)
- *Reason*: Allows on-demand connection (battery efficient)

**Renegotiation (MIC_ON/OFF):**
- **Child = Offerer** (creates offer with track added/removed)
- **Parent = Answerer** (creates answer)
- *Reason*: Ensures proper timing for native audio hardware initialization

### Why This Approach?

The native WebRTC `AudioRecord` initialization (`initRecording` → `startRecording`) happens when:
1. An audio track is added to the PeerConnection
2. An offer is created that includes the audio track in the SDP
3. The offer/answer exchange completes

By making the Child the offerer during renegotiation, we ensure the track is added BEFORE the offer is created, guaranteeing the native audio pipeline initializes correctly.

## Flow Diagrams

### MIC_ON Flow

```
Parent                          Child                          Firebase
  |                              |                                |
  |-------- MIC_ON command ----->|                                |
  |                              |                                |
  |                              | startMicrophone()              |
  |                              | - Create AudioSource           |
  |                              | - Create AudioTrack            |
  |                              | - addTrack() to PeerConnection |
  |                              |                                |
  |                              | createOffer() [Child=Offerer]  |
  |                              |------- offer (in answer path)->|
  |                              |                                |
  |<-------- offer (from answer path) ---------------------------|
  |                              |                                |
  | setRemoteDescription(offer)  |                                |
  | createAnswer()               |                                |
  |                              |                                |
  |------- answer (in offer path) ------------------------------>|
  |                              |                                |
  |                              |<------ answer (from offer path)|
  |                              |                                |
  |                              | setRemoteDescription(answer)   |
  |                              | ✅ Renegotiation complete      |
  |                              |                                |
  |-- RENEGOTIATION_COMPLETE --->|                                |
  |                              |                                |
  |<-------- MIC_STARTED --------|                                |
  |                              |                                |
  | 🎤 Audio streaming active    |                                |
```

### MIC_OFF Flow

```
Parent                          Child                          Firebase
  |                              |                                |
  |-------- MIC_OFF command ---->|                                |
  |                              |                                |
  |                              | stopMicrophone()               |
  |                              | - Disable AudioTrack           |
  |                              | - Dispose AudioTrack           |
  |                              | - Dispose AudioSource          |
  |                              | - removeTrack() from PC        |
  |                              |                                |
  |                              | createOffer() [Child=Offerer]  |
  |                              |------- offer (in answer path)->|
  |                              |                                |
  |<-------- offer (from answer path) ---------------------------|
  |                              |                                |
  | setRemoteDescription(offer)  |                                |
  | createAnswer()               |                                |
  |                              |                                |
  |------- answer (in offer path) ------------------------------>|
  |                              |                                |
  |                              |<------ answer (from offer path)|
  |                              |                                |
  |                              | setRemoteDescription(answer)   |
  |                              | ✅ Renegotiation complete      |
  |                              |                                |
  |-- RENEGOTIATION_COMPLETE --->|                                |
  |                              |                                |
  |<-------- MIC_STOPPED --------|                                |
  |                              |                                |
  | 🔇 Audio streaming stopped   |                                |
```

## Key Components

### MicrophoneController.kt

Manages the audio track lifecycle and WebRTC integration.

**Responsibilities:**
- Create/dispose AudioSource and AudioTrack
- Add/remove tracks from PeerConnection
- Manage track enabled state
- Return renegotiation requirement flag

**Critical Implementation Details:**
- AudioSource and AudioTrack are created fresh on each MIC_ON
- Disposal order matters: Track → Source → Remove from PC
- `isActive` flag prevents duplicate operations

### BackgroundService.kt

Handles renegotiation flow and signaling.

**Key Method: `performWebRTCRenegotiation()`**
- Child creates offer (temporary offerer role)
- Sends offer to Firebase in 'answer' path
- Waits for Parent's answer in 'offer' path
- Invokes completion callback after RENEGOTIATION_COMPLETE

**Signaling Path Swap:**
- Normal: offer → offer path, answer → answer path
- Renegotiation: offer → answer path, answer → offer path

## Audio Configuration

```kotlin
MediaConstraints:
- googEchoCancellation: true
- googAutoGainControl: true
- googNoiseSuppression: false
- googHighpassFilter: true
- googTypingNoiseDetection: true
- googAudioMirroring: false
```

## Troubleshooting

### Issue: Mic doesn't start on second MIC_ON

**Cause:** Native AudioRecord not released properly between cycles.

**Solution:** Ensure proper disposal order:
1. Disable AudioTrack
2. Dispose AudioTrack (releases native AudioRecord)
3. Dispose AudioSource
4. Remove from PeerConnection

### Issue: Mic stays on after MIC_OFF

**Cause:** Track not properly removed or disposed.

**Solution:** Verify `stopMicrophone()` is called and completes successfully. Check logs for disposal confirmation.

### Issue: "Microphone already active" on first MIC_ON

**Cause:** `isActive` flag not reset from previous session.

**Solution:** Ensure `cleanup()` is called when connection closes or app restarts.

## Testing Checklist

- [ ] First MIC_ON starts audio capture
- [ ] MIC_OFF stops audio capture
- [ ] Second MIC_ON starts audio capture (critical test!)
- [ ] Multiple ON/OFF cycles work without restart
- [ ] Audio quality is good (no echo, noise)
- [ ] Mic hardware releases properly (check with other apps)
- [ ] Works after app backgrounding/foregrounding
- [ ] Works after network interruption and reconnection

## Performance Considerations

- **Battery Impact:** Minimal when off, moderate when streaming
- **Network Usage:** ~32 kbps for audio (depends on codec)
- **CPU Usage:** Low (hardware encoding when available)
- **Memory:** ~2-4 MB for audio buffers

## Security & Privacy

- Audio streaming only active when Parent explicitly sends MIC_ON
- No background recording without active WebRTC connection
- Audio data encrypted via WebRTC DTLS/SRTP
- Mic hardware fully released on MIC_OFF (verifiable by user)

## Future Enhancements

- [ ] Audio quality settings (bitrate, sample rate)
- [ ] Noise suppression toggle
- [ ] Audio level monitoring
- [ ] Recording duration limits
- [ ] Automatic stop on low battery
