# Vibrate & Flash Feature

Send vibration and flash patterns to the child device remotely.

## Overview

The Vibrate & Flash feature allows you to trigger various vibration and flashlight patterns on the child device. This can be useful for getting attention, signaling, or locating the device.

## Features

### Vibration Patterns
- **Short Buzz**: Quick single vibration
- **Double Tap**: Two quick vibrations
- **Triple Tap**: Three quick vibrations
- **SOS Pattern**: Morse code SOS (... --- ...)
- **Heartbeat**: Rhythmic pulse pattern
- **Wave**: Gradual intensity wave
- **Earthquake**: Intense random pattern

### Flash Patterns
- **Single Blink**: One quick flash
- **Double Blink**: Two quick flashes
- **Strobe**: Rapid continuous flashing
- **SOS Flash**: Morse code SOS with light
- **Pulse**: Gradual brightness pulse
- **Beacon**: Slow rhythmic flash
- **Disco**: Random rapid flashing

### Combined Patterns
- **Alert**: Synchronized vibration and flash for attention
- **Attention**: Moderate combined pattern
- **Emergency**: Intense combined pattern for urgent situations

## How It Works

### Parent Side (Desktop)
1. Click any pattern button to send the command
2. The command is sent immediately via WebRTC data channel
3. Status message confirms the command was sent

### Android Side
The child device receives commands and executes patterns using:
- `VibrationFlashController.kt` - Handles pattern execution
- Vibrator API for haptic feedback
- Camera flash API for flashlight control

## Commands

### Vibration Commands
```json
{ "cmd": "VIBRATE", "pattern": "short|double|triple|sos|heartbeat|wave|earthquake" }
```

### Flash Commands
```json
{ "cmd": "FLASH", "pattern": "blink|double_blink|strobe|sos_flash|pulse|beacon|disco" }
```

### Combined Commands
```json
{ "cmd": "VIBRATE_FLASH", "pattern": "alert|attention|emergency" }
```

## UI Components

### Button Colors
- **Blue**: Standard vibration patterns
- **Yellow**: Flash patterns
- **Red**: SOS/Emergency patterns
- **Pink**: Heartbeat/Pulse patterns
- **Purple**: Wave/Beacon patterns
- **Orange**: Earthquake/Disco patterns
- **Green**: Combined patterns

### Status Messages
- Blue: Command sent successfully
- Yellow: Flash command sent
- Green: Combined command sent
- Red: Error occurred

## Technical Details

### Pattern Execution
- Patterns are executed immediately upon receiving command
- No confirmation is sent back to parent (fire-and-forget)
- Multiple patterns can be triggered in sequence
- Patterns run independently and don't queue

### Permissions Required (Android)
- `VIBRATE` - For vibration patterns
- `CAMERA` - For flashlight access
- `FLASHLIGHT` - For flash control

## Usage Example

```javascript
// Send vibration pattern
app.connectionManager.sendCommand(JSON.stringify({ 
    cmd: 'VIBRATE', 
    pattern: 'sos' 
}));

// Send flash pattern
app.connectionManager.sendCommand(JSON.stringify({ 
    cmd: 'FLASH', 
    pattern: 'strobe' 
}));

// Send combined pattern
app.connectionManager.sendCommand(JSON.stringify({ 
    cmd: 'VIBRATE_FLASH', 
    pattern: 'emergency' 
}));
```

## Notes

- Patterns execute immediately without confirmation
- Flash patterns require device to have a flashlight
- Some devices may have different vibration intensities
- Battery impact varies by pattern intensity and duration
- Emergency patterns are designed to be highly noticeable
