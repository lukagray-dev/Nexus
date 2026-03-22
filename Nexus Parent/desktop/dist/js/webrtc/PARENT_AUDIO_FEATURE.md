# Parent Audio Streaming Feature

## Overview
The Parent Audio Streaming feature allows parents to speak to the child device through the Nexus Parent desktop app. The audio is played at full volume on the child device, even if the device is in silent mode.

## Implementation

### Desktop App (Parent Side)

#### Files Modified/Created:
1. **`dist/js/webrtc/parent-audio-controller.js`** (NEW)
   - Manages parent's microphone access
   - Handles audio track addition to WebRTC peer connection
   - Controls mute/unmute state
   - Sends commands to child device

2. **`dist/js/features/mic/mic.js`** (MODIFIED)
   - Added parent audio controller integration
   - Added `toggleParentAudio()` method
   - Added UI update logic for mute/unmute button
   - Added cleanup for parent audio controller

3. **`dist/js/features/mic/mic.html`** (MODIFIED)
   - Added parent audio mute/unmute button (bottom left corner)
   - Added pulse ring animation element

4. **`dist/js/features/mic/mic.css`** (MODIFIED)
   - Added styles for parent audio button
   - Added pulse ring animation
   - Button changes color: Red (muted) → Green (unmuted)

5. **`dist/index.html`** (MODIFIED)
   - Added script tag for parent-audio-controller.js

### How It Works

1. **Initialization**:
   - When the mic panel is opened, the `ParentAudioController` is initialized
   - The controller is linked to the WebRTC manager

2. **First Use**:
   - When the parent clicks the mute/unmute button for the first time
   - Browser requests microphone permission
   - If granted, the controller gets microphone access
   - Audio track is added to the peer connection (but muted)
   - WebRTC renegotiation is triggered to update the connection

3. **Unmuting (Speaking to Child)**:
   - Parent clicks the button (red → green)
   - Audio track is enabled
   - Command `PARENT_AUDIO_UNMUTE` is sent to child via data channel
   - Child device plays the audio at full volume
   - Pulse animation starts on the button

4. **Muting (Stop Speaking)**:
   - Parent clicks the button again (green → red)
   - Audio track is disabled
   - Command `PARENT_AUDIO_MUTE` is sent to child
   - Child device stops playing parent audio
   - Pulse animation stops

### UI Elements

- **Button Location**: Bottom left corner of mic panel
- **Button States**:
  - **Muted (Default)**: Red background, microphone icon
  - **Unmuted (Active)**: Green background, microphone with slash icon, pulsing ring
- **Tooltip**:
  - Muted: "Speak to child (Muted)"
  - Unmuted: "Stop speaking (Unmuted)"

### Commands Sent to Child

- `PARENT_AUDIO_UNMUTE`: Child should play parent audio at full volume
- `PARENT_AUDIO_MUTE`: Child should stop playing parent audio

### Child Side (Already Implemented)

The Nexus Child app already has the implementation to:
1. Receive parent audio track via WebRTC
2. Listen for `PARENT_AUDIO_UNMUTE` and `PARENT_AUDIO_MUTE` commands
3. Play audio at full volume (bypassing silent mode)
4. Stop audio playback when muted

## Usage

1. Open the Nexus Parent desktop app
2. Connect to a child device
3. Open the Mic panel from the sidebar
4. Click the red button in the bottom left corner
5. Grant microphone permission when prompted (first time only)
6. Button turns green with pulsing animation
7. Speak into your microphone - child will hear you at full volume
8. Click the button again to stop speaking

## Technical Details

### Audio Configuration
- Sample Rate: 48000 Hz
- Echo Cancellation: Enabled
- Noise Suppression: Enabled
- Auto Gain Control: Enabled

### WebRTC Integration
- Audio track is added to existing audio transceiver (if available)
- Transceiver direction is changed from `recvonly` to `sendrecv`
- Renegotiation is triggered when audio is initialized
- Audio track is enabled/disabled for mute/unmute (no track removal)

### Key Implementation Details
1. **Transceiver Reuse**: Instead of adding a new track, we find the existing audio transceiver and change its direction to `sendrecv`, then replace the track
2. **Direction Change**: The audio transceiver direction is changed from `recvonly` (receiving child's mic) to `sendrecv` (both sending and receiving)
3. **Renegotiation**: After changing the transceiver direction, a WebRTC renegotiation is triggered to update the connection
4. **Track Enable/Disable**: Muting/unmuting is done by enabling/disabling the audio track, not by removing it

### Error Handling
- Permission denied: Shows error message to user
- Microphone not available: Shows error message
- Connection not ready: Disables button until connected

## Troubleshooting

If audio is not being transferred:
1. Check browser console for WebRTC logs
2. Verify transceiver direction is `sendrecv` (logged during initialization)
3. Verify audio track is enabled when unmuted (logged during unmute)
4. Check that renegotiation completed successfully
5. Verify child device received the `PARENT_AUDIO_UNMUTE` command

## Future Enhancements

- Add audio level indicator for parent's voice
- Add push-to-talk mode option
- Add audio recording of parent-child conversations
- Add voice effects/filters
