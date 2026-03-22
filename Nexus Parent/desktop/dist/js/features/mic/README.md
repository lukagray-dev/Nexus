# Microphone Feature

Real-time audio streaming from child device to parent with beautiful audio visualizer.

## Architecture

### Components

1. **mic.js** - Main feature controller
2. **mic.html** - UI template
3. **mic.css** - Styling
4. **connection-manager.js** - Handles mic confirmations
5. **webrtc-manager.js** - Manages audio stream reception

### Flow Diagram

**IMPORTANT: Role Reversal During Renegotiation**
- **Initial Connection**: Parent = Offerer, Child = Answerer (battery efficient)
- **Renegotiation (MIC_ON/OFF)**: Child = Offerer (temporary), Parent = Answerer
- This allows Child to add/remove audio track BEFORE creating offer, ensuring proper native audio pipeline initialization

```
┌─────────────────────────────────────────────────────────────────┐
│                         MIC ON FLOW                              │
│                  (Role Reversal Renegotiation)                   │
└─────────────────────────────────────────────────────────────────┘

Parent (Desktop)                    Child (Android)
     │                                    │
     │  1. User clicks play              │
     │     (shows loading spinner)        │
     │                                    │
     │  2. Send MIC_ON command            │
     ├───────────────────────────────────>│
     │                                    │
     │                                    │  3. Add audio track to PC
     │                                    │  4. Create offer (Child = Offerer)
     │                                    │  5. Send offer in 'answer' path
     │                                    │
     │  6. Receive offer from 'answer'    │
     │<───────────────────────────────────┤
     │     (detect role reversal)         │
     │                                    │
     │  7. Create answer                  │
     │  8. Send answer in 'offer' path    │
     ├───────────────────────────────────>│
     │                                    │
     │                                    │  9. Receive answer from 'offer'
     │                                    │ 10. Set remote description
     │                                    │
     │ 11. Send RENEGOTIATION_COMPLETE    │
     ├───────────────────────────────────>│
     │                                    │
     │                                    │ 12. Invoke completion callback
     │                                    │ 13. Send MIC_STARTED
     │                                    │
     │ 14. Receive MIC_STARTED            │
     │<───────────────────────────────────┤
     │                                    │
     │ 15. Clear timeout                  │
     │ 16. Hide loading spinner           │
     │ 17. Show pause button              │
     │ 18. Start audio visualizer         │
     │                                    │
     │ 19. Receive audio stream           │
     │<═══════════════════════════════════│
     │                                    │
     │ 20. Display audio visualizer       │
     │     (frequency bars + waveform)    │
     │                                    │

┌─────────────────────────────────────────────────────────────────┐
│                        MIC OFF FLOW                              │
│                  (Role Reversal Renegotiation)                   │
└─────────────────────────────────────────────────────────────────┘

Parent (Desktop)                    Child (Android)
     │                                    │
     │  1. User clicks pause              │
     │     (shows loading spinner)        │
     │                                    │
     │  2. Send MIC_OFF command           │
     ├───────────────────────────────────>│
     │                                    │
     │                                    │  3. Disable audio track
     │                                    │  4. Dispose AudioTrack (releases native)
     │                                    │  5. Dispose AudioSource
     │                                    │  6. Remove from PeerConnection
     │                                    │  7. Create offer (Child = Offerer)
     │                                    │  8. Send offer in 'answer' path
     │                                    │
     │  9. Receive offer from 'answer'    │
     │<───────────────────────────────────┤
     │     (detect role reversal)         │
     │                                    │
     │ 10. Create answer                  │
     │ 11. Send answer in 'offer' path    │
     ├───────────────────────────────────>│
     │                                    │
     │                                    │ 12. Receive answer from 'offer'
     │                                    │ 13. Set remote description
     │                                    │
     │ 14. Send RENEGOTIATION_COMPLETE    │
     ├───────────────────────────────────>│
     │                                    │
     │                                    │ 15. Invoke completion callback
     │                                    │ 16. Send MIC_STOPPED
     │                                    │
     │ 17. Receive MIC_STOPPED            │
     │<───────────────────────────────────┤
     │                                    │
     │ 18. Clear timeout                  │
     │ 19. Hide loading spinner           │
     │ 20. Show play button               │
     │ 21. Stop audio visualizer          │
     │                                    │
```

## Key Features

### 1. Confirmation-Based Flow
- Commands are acknowledged by child device
- Loading spinner shows during command execution
- 30-second timeout protection
- Proper error handling

### 2. Audio Visualizer
- **Frequency Bars**: Vertical bars showing frequency spectrum
  - Gradient: Yellow → Orange → Red
  - Real-time response to audio levels
- **Waveform Overlay**: Cyan waveform with glow effect
  - Shows audio amplitude over time
  - Smooth animation with fade trails
- **Canvas-based Rendering**: High-performance visualization
- **Web Audio API**: Real-time audio analysis

### 3. Event-Based Communication
- Custom events for stream reception
- Custom events for confirmations
- Decoupled architecture

## Code Structure

### mic.js

```javascript
class MicFeature {
    constructor(container)
    
    // Lifecycle
    init()
    destroy()
    
    // UI Management
    showLoading()
    hideLoading()
    updateUI(isActive)
    
    // Stream Handling
    listenForStream()
    attachStream(stream)
    
    // Visualizer
    startVisualizer(stream)
    animateVisualizer(canvas)
    stopVisualizer()
    
    // Mic Control
    toggleMic()
    startMic()
    stopMic()
    
    // Confirmation Handling
    listenForConfirmations()
    clearConfirmationTimeout()
}
```

### Event Flow

**Stream Events:**
```javascript
// Dispatched by: webrtc-manager.js
window.dispatchEvent(new CustomEvent('mic-stream-received', {
    detail: { stream }
}));

// Listened by: mic.js
window.addEventListener('mic-stream-received', (event) => {
    this.attachStream(event.detail.stream);
});
```

**Confirmation Events:**
```javascript
// Dispatched by: connection-manager.js
window.dispatchEvent(new CustomEvent('mic-confirmation', {
    detail: { message: 'MIC_STARTED' }
}));

// Listened by: mic.js
window.addEventListener('mic-confirmation', (event) => {
    if (event.detail.message === 'MIC_STARTED') {
        this.hideLoading();
        this.updateUI(true);
    }
});
```

## Audio Visualizer Details

### Frequency Bars
- Uses `analyser.getByteFrequencyData()`
- FFT size: 256 (128 frequency bins)
- Bar width: Dynamic based on canvas width
- Height: Scaled to 80% of canvas height
- Gradient colors: `#fbbf24` → `#f59e0b` → `#ef4444`
- Glow effect: 10px blur with yellow shadow

### Waveform
- Uses `analyser.getByteTimeDomainData()`
- Line width: 2px
- Color: `#22d3ee` (cyan)
- Glow effect: 15px blur with cyan shadow
- Draws across full canvas width

### Canvas Animation
- Fade effect: `rgba(0, 0, 0, 0.2)` overlay
- Smooth trails from previous frames
- 60 FPS animation loop
- Automatic canvas sizing

## State Management

### States
1. **Inactive** - Mic not streaming
2. **Loading** - Command sent, waiting for confirmation
3. **Active** - Mic streaming, visualizer running

### State Transitions
```
Inactive ──[play clicked]──> Loading ──[MIC_STARTED]──> Active
                                │                          │
                                │                          │
                          [timeout/error]            [pause clicked]
                                │                          │
                                ↓                          ↓
                             Inactive <──[MIC_STOPPED]── Loading
```

## Error Handling

### Timeout Protection
- 30-second timeout for confirmations
- Automatic cleanup on timeout
- User-friendly error messages

### Stream Handling
- Checks for existing stream on window open
- Handles stream arrival before/after window opens
- Graceful handling of missing streams

### Audio Context
- Handles browser autoplay restrictions
- Proper cleanup on destroy
- Error logging for debugging

## Known Issues

### ✅ FIXED: Subsequent Mic Starts Don't Work
**Symptom**: After first MIC_ON/OFF cycle, subsequent MIC_ON commands don't start audio capture hardware.

**Root Cause**: Native WebRTC AudioRecord initialization wasn't being called on second MIC_ON due to timing issues in the renegotiation flow.

**Solution Implemented**: Role reversal during renegotiation
- **Initial Connection**: Parent = Offerer, Child = Answerer (battery efficient)
- **Renegotiation**: Child = Offerer (temporary), Parent = Answerer
- This allows Child to add/remove audio track BEFORE creating offer, ensuring proper native audio pipeline initialization
- Signaling path swap: Child sends offer in 'answer' path, Parent sends answer in 'offer' path

**Files Modified**:
- `PhantomPeerManager.kt` - Added `createOffer()` method
- `BackgroundService.kt` - Modified renegotiation to create offer instead of requesting one
- `MicrophoneController.kt` - Proper cleanup order: Disable → Dispose Track → Dispose Source → Remove from PC
- `webrtc-manager.js` - Modified answer listener to detect and handle offers from child during renegotiation
- `connection-manager.js` - Removed old RENEGOTIATION_NEEDED handling

## Future Enhancements

1. **Recording Feature**
   - Save audio to file
   - Playback recorded audio
   - Share recordings

2. **Audio Effects**
   - Echo cancellation toggle
   - Noise suppression toggle
   - Volume control

3. **Visualizer Options**
   - Multiple visualization modes
   - Color themes
   - Customizable sensitivity

4. **Performance**
   - Optimize canvas rendering
   - Reduce CPU usage
   - Battery-friendly mode

## Dependencies

- **Web Audio API** - Audio analysis
- **Canvas API** - Visualization rendering
- **WebRTC** - Audio streaming
- **Firebase** - Signaling

## Browser Compatibility

- Chrome/Edge: ✅ Full support
- Firefox: ✅ Full support
- Safari: ⚠️ Requires user gesture for audio
- Opera: ✅ Full support

## Testing Checklist

- [x] Mic starts on first connection
- [x] Visualizer displays correctly
- [x] Mic stops cleanly
- [x] Loading spinner shows/hides correctly
- [x] Timeout protection works
- [x] Error messages display
- [x] Stream arrives before window opens
- [x] Stream arrives after window opens
- [x] Multiple start/stop cycles (FIXED with role reversal)
- [x] Reconnection without app restart (FIXED with role reversal)
