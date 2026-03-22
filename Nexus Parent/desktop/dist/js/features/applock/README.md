# App Lock Feature

Remotely enable/disable app lock and change PIN on the child device via Firebase.

## Overview

The App Lock feature allows parents to protect the child app with a PIN code. When enabled, the child device will require PIN entry to access the app. The PIN can be changed remotely from the parent device.

## Features

### Lock Status Management
- **Enable/Disable**: Toggle app lock on/off remotely
- **Real-time Status**: See current lock state
- **Visual Indicators**: Color-coded status (red = locked, green = unlocked)

### PIN Management
- **Change PIN**: Set a new 4-digit PIN remotely
- **Secure Storage**: PIN is hashed on child device
- **Validation**: Ensures PIN is 4 digits and matches confirmation

### Security Features
- **Failed Attempt Tracking**: Counts incorrect PIN entries
- **Automatic Lockout**: After 5 failed attempts, app locks for 5 minutes
- **Progressive Lockout**: Additional failures increase lockout duration by 2 minutes
- **Lockout Timer**: Displays remaining lockout time to user

## How It Works

### Parent Side (Desktop)
1. Toggle lock status or change PIN
2. Changes are written to Firebase Realtime Database
3. Child device listens for changes and applies them immediately
4. Status updates are reflected in real-time

### Android Side
The child device uses:
- `AppLockManager.kt` - Manages lock state and PIN verification
- `AppLockActivity.kt` - PIN entry screen
- Firebase Realtime Database - Syncs settings from parent
- SharedPreferences - Stores local lock state and PIN hash

## Firebase Structure

```
devices/
  {deviceId}/
    appLock/
      enabled: boolean
      pin: string (temporary, cleared after read)
```

## Commands

### Enable/Disable Lock
```javascript
// Via Firebase
const database = firebase.database();
const appLockRef = database.ref(`devices/${deviceId}/appLock`);

await appLockRef.update({
    enabled: true  // or false
});
```

### Change PIN
```javascript
// Via Firebase
await appLockRef.update({
    pin: "1234"  // New 4-digit PIN
});
```

## Security Implementation

### PIN Storage
- PIN is hashed using SHA-256 before storage
- Only hash is stored on device (never plain text)
- Firebase PIN is cleared immediately after child reads it

### Lockout Logic
```
Failed Attempts | Lockout Duration
----------------|------------------
0-4             | No lockout
5               | 5 minutes
6               | 7 minutes (5 + 2)
7               | 9 minutes (5 + 4)
8               | 11 minutes (5 + 6)
...             | ...
```

### Lockout Calculation
```kotlin
val lockoutDuration = LOCKOUT_DURATION_MS + (extraAttempts * LOCKOUT_INCREMENT_MS)
```

## UI Components

### Status Card
- Large card showing current lock state
- Icon changes based on status (locked/unlocked)
- Toggle button to enable/disable
- Color-coded for quick recognition

### PIN Management Section
- Two input fields (new PIN and confirmation)
- Numeric-only input (4 digits)
- Password masking for security
- Change PIN button

### Info Section
- Explains how app lock works
- Details about security features
- Lockout policy information

## Usage Example

```javascript
// Enable app lock
const deviceId = localStorage.getItem('connected_device_id');
const database = firebase.database();
const appLockRef = database.ref(`devices/${deviceId}/appLock`);

await appLockRef.update({
    enabled: true
});

// Change PIN
await appLockRef.update({
    pin: "5678"
});

// Listen for status changes
appLockRef.on('value', (snapshot) => {
    const data = snapshot.val();
    console.log('Lock enabled:', data.enabled);
});
```

## Default Settings

- **Default PIN**: 1234
- **Lock Enabled**: true (on first initialization)
- **Max Failed Attempts**: 5
- **Initial Lockout**: 5 minutes
- **Lockout Increment**: 2 minutes per additional failure

## Child Device Flow

### App Launch
1. Check if app lock is enabled
2. If enabled, show PIN entry screen
3. Verify entered PIN against stored hash
4. On success: Allow access, reset failed attempts
5. On failure: Increment counter, apply lockout if needed

### PIN Entry Screen
- 4-digit numeric keypad
- Visual feedback for each digit
- Error message on incorrect PIN
- Lockout timer display when locked out
- Cannot be bypassed (blocks app access)

## Permissions Required

### Parent Side
- Firebase access (automatic with config)
- No special permissions needed

### Child Side
- No special Android permissions required
- Firebase access for listening to changes

## Error Handling

### Common Errors
- **No device connected**: Ensure device is connected before using feature
- **Firebase unavailable**: Check internet connection
- **PIN validation failed**: Ensure PIN is 4 digits and matches confirmation

### Error Messages
```javascript
// Connection error
"Connection manager not available"

// Device error
"No device connected"

// Validation errors
"Please enter PIN in both fields"
"PIN must be exactly 4 digits"
"PINs do not match"
"PIN must contain only numbers"
```

## Best Practices

### For Parents
1. Choose a PIN the child doesn't know
2. Change PIN regularly for security
3. Don't share PIN with child
4. Remember the PIN (no recovery mechanism)
5. Use app lock in combination with other monitoring features

### For Implementation
1. Always validate PIN format before sending
2. Clear PIN inputs after successful change
3. Show clear status messages to user
4. Handle Firebase errors gracefully
5. Clean up Firebase listeners on destroy

## Limitations

- PIN must be exactly 4 digits (no longer/shorter)
- No PIN recovery mechanism (must be reset manually)
- Lockout timer cannot be bypassed remotely
- Child can uninstall app if device is not supervised
- Requires active internet connection for remote changes

## Security Considerations

- PIN is transmitted via Firebase (encrypted in transit)
- PIN is cleared from Firebase after child reads it
- Only hash is stored on child device
- Lockout prevents brute force attacks
- No backdoor or master PIN exists

## Troubleshooting

### Lock Not Enabling
1. Check Firebase connection
2. Verify device ID is correct
3. Ensure child app is running
4. Check Firebase rules allow write access

### PIN Change Not Working
1. Verify PIN format (4 digits, numbers only)
2. Check Firebase connection
3. Ensure child app is listening for changes
4. Wait a few seconds for sync

### Child Can't Unlock
1. Verify correct PIN is being entered
2. Check if device is in lockout period
3. Wait for lockout timer to expire
4. Change PIN from parent device if forgotten
