# Parent Audio Feature - Android Implementation

## Overview
The Parent Audio feature allows parents to speak to the child device through the Nexus Parent Android app. The audio is played at full volume on the child device, even if the device is in silent mode.

## Implementation

### Files Modified:

1. **`ParentPeerManager.kt`**
   - Added `initializeParentAudio()` method
   - Finds existing audio transceiver and changes direction from `RECV_ONLY` to `SEND_RECV`
   - Replaces the receiver track with parent's microphone track
   - Added `setParentAudioEnabled()` method with detailed logging
   - Added `renegotiate()` method to update WebRTC connection
   - Added `clearRenegotiationFlag()` method

2. **`ConnectionManager.kt`**
   - Updated `initializeParentAudio()` to trigger renegotiation after initialization
   - Stores FirebaseSignaling reference for renegotiation

3. **`FirebaseSignaling.kt`**
   - Updated answer listener to call `clearRenegotiationFlag()` after setting remote description

4. **`MicFeature.kt`** (Already implemented)
   - UI and button handling already in place
   - Calls `ConnectionManager.initializeParentAudio()` and `setParentAudioEnabled()`

## How It Works

### Initialization Flow:

1. **User clicks mute button** (first time)
2. **Permission check**: Verifies `RECORD_AUDIO` permission
3. **Initialize parent audio**:
   - Creates audio source with echo cancellation, noise suppression, and auto gain control
   - Creates audio track (starts muted)
   - Finds existing audio transceiver
   - Changes transceiver direction from `RECV_ONLY` to `SEND_RECV`
   - Replaces sender track with parent's microphone
4. **Renegotiation**:
   - Creates new offer with updated transceiver direction
   - Sends offer to child via Firebase
   - Waits for answer from child
   - Sets remote description
   - Clears renegotiation flag

### Unmute Flow:

1. **User clicks button** (green state)
2. **Enable audio track**: `parentAudioTrack.setEnabled(true)`
3. **Send command**: `PARENT_AUDIO_UNMUTE` to child
4. **Child increases volume** and plays parent audio at full volume
5. **UI updates**: Button turns green, pulse animation starts

### Mute Flow:

1. **User clicks button** (red state)
2. **Disable audio track**: `parentAudioTrack.setEnabled(false)`
3. **Send command**: `PARENT_AUDIO_MUTE` to child
4. **Child restores volume** and stops playing parent audio
5. **UI updates**: Button turns red, pulse animation stops

## Key Implementation Details

### Transceiver Direction Change

The critical fix is changing the audio transceiver direction:

```kotlin
// Find audio transceiver
val audioTransceiver = pc.transceivers.find { 
    it.receiver.track()?.kind() == "audio" 
}

// Change direction from RECV_ONLY to SEND_RECV
audioTransceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV

// Replace track
audioTransceiver?.sender?.setTrack(parentAudioTrack, false)
```

### Audio Configuration

```kotlin
val audioConstraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
}
```

### Renegotiation

After changing the transceiver direction, we must renegotiate:

```kotlin
// Create new offer
val offer = createOffer()

// Send to Firebase
firebaseSignaling.sendOffer(offer)

// Wait for answer (handled by FirebaseSignaling listener)
```

## UI Elements

- **Button Location**: Bottom left corner of mic panel
- **Button States**:
  - **Muted (Default)**: Red background, microphone icon
  - **Unmuted (Active)**: Green background, microphone with slash icon, pulsing ring
- **Permissions**: Requests `RECORD_AUDIO` permission on first use

## Commands Sent to Child

- `PARENT_AUDIO_UNMUTE`: Child plays parent audio at full volume
- `PARENT_AUDIO_MUTE`: Child stops playing parent audio
- `RENEGOTIATION_COMPLETE`: Confirms renegotiation is complete

## Debugging

### Check Logcat for these logs:

**Initialization:**
```
🎤 Initializing parent audio...
📊 Found 2 transceivers
🔍 Transceiver: kind=audio, direction=RECV_ONLY, mid=1
🔄 Found existing audio transceiver, changing direction to SEND_RECV
✅ Replaced audio track in existing transceiver
✅ Parent audio track configured (muted)
🔍 Final Transceiver: kind=audio, direction=SEND_RECV, mid=1, hasSenderTrack=true, senderEnabled=false
```

**Renegotiation:**
```
🔄 Triggering renegotiation after parent audio initialization...
🔄 Starting renegotiation...
🔍 Transceiver: kind=audio, direction=SEND_RECV, mid=1, hasSenderTrack=true, senderEnabled=false
✅ New offer created
📤 New offer sent to Firebase for renegotiation
📥 Answer received from child
✅ Remote description set (answer)
✅ Renegotiation complete
```

**Unmute:**
```
🔊 Unmuting parent audio
🔊 Parent audio enabled
🔊 Audio track state: id=parent_audio, enabled=true, state=LIVE
📡 RTP Sender: id=..., track=parent_audio
📡 Audio transceiver direction: SEND_RECV
📡 Audio transceiver mid: 1
📤 Sent command: {"cmd":"PARENT_AUDIO_UNMUTE"}
```

### Key Indicators:

✅ Transceiver direction: `SEND_RECV` (not `RECV_ONLY`)  
✅ `hasSenderTrack=true`  
✅ Audio track enabled: `true`  
✅ Audio track state: `LIVE`  
✅ Command sent successfully  

## Troubleshooting

### Issue: No audio being transferred

**Check:**
1. Transceiver direction is `SEND_RECV` (check logs)
2. Audio track is enabled when unmuted
3. Renegotiation completed successfully
4. Child received `PARENT_AUDIO_UNMUTE` command
5. Microphone permission granted

### Issue: Permission denied

**Solution:**
- Check app permissions in Android settings
- Verify `RECORD_AUDIO` permission in AndroidManifest.xml
- Request permission again from the dialog

### Issue: Renegotiation fails

**Solution:**
- Check Firebase connection
- Verify child device is still connected
- Check signaling state is `STABLE` before renegotiating
- Try disconnecting and reconnecting

## Testing Checklist

- [ ] Microphone permission granted
- [ ] Audio transceiver direction is `SEND_RECV`
- [ ] Audio track is attached (`hasSenderTrack=true`)
- [ ] Audio track is enabled when unmuted
- [ ] Command sent to child successfully
- [ ] Child device volume increases
- [ ] Child device plays parent's voice
- [ ] Button turns green with pulse animation
- [ ] Button turns red when muted again
- [ ] No crashes or errors in logcat

## Comparison with Desktop Implementation

Both implementations follow the same approach:

1. Find existing audio transceiver
2. Change direction from `recvonly`/`RECV_ONLY` to `sendrecv`/`SEND_RECV`
3. Replace track with parent's microphone
4. Trigger renegotiation
5. Enable/disable track for mute/unmute

The key difference is the API:
- **Desktop**: JavaScript WebRTC API
- **Android**: Native WebRTC SDK (org.webrtc)

Both achieve the same result: bidirectional audio with parent able to speak to child.
