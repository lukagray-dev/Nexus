# SMS Feature

## Overview
The SMS feature allows the parent device to monitor and view SMS messages from the connected child device in real-time. It provides a clean interface for browsing messages with search functionality and detailed views.

## Architecture

### Flow Diagram
```
┌─────────────┐                    ┌──────────────┐                    ┌─────────────┐
│   Parent    │                    │  Connection  │                    │    Child    │
│  (SMS UI)   │                    │   Manager    │                    │   Device    │
└──────┬──────┘                    └──────┬───────┘                    └──────┬──────┘
       │                                  │                                   │
       │ 1. User clicks Play              │                                   │
       ├─────────────────────────────────>│                                   │
       │    startMonitoring()             │                                   │
       │                                  │                                   │
       │ 2. Show loading spinner          │                                   │
       │    (30s timeout starts)          │                                   │
       │                                  │                                   │
       │                                  │ 3. Send SMS_ON command            │
       │                                  ├──────────────────────────────────>│
       │                                  │    {cmd: "SMS_ON", since: 0}      │
       │                                  │                                   │
       │                                  │                                   │ 4. Start SMS monitoring
       │                                  │                                   │    Read SMS database
       │                                  │                                   │
       │                                  │ 5. Send confirmation              │
       │                                  │<──────────────────────────────────┤
       │                                  │    "SMS_STARTED"                  │
       │                                  │                                   │
       │ 6. Dispatch event                │                                   │
       │<─────────────────────────────────┤                                   │
       │    sms-confirmation              │                                   │
       │                                  │                                   │
       │ 7. Hide loading spinner          │                                   │
       │    Update UI (show pause icon)   │                                   │
       │                                  │                                   │
       │                                  │ 8. Send SMS messages              │
       │                                  │<──────────────────────────────────┤
       │                                  │    {type: "sms", ...data}         │
       │                                  │                                   │
       │ 9. Dispatch event                │                                   │
       │<─────────────────────────────────┤                                   │
       │    sms-message                   │                                   │
       │                                  │                                   │
       │ 10. Display message in list      │                                   │
       │                                  │                                   │
       │ 11. User clicks Pause            │                                   │
       ├─────────────────────────────────>│                                   │
       │    stopMonitoring()              │                                   │
       │                                  │                                   │
       │ 12. Show loading spinner         │                                   │
       │     (30s timeout starts)         │                                   │
       │                                  │                                   │
       │                                  │ 13. Send SMS_OFF command          │
       │                                  ├──────────────────────────────────>│
       │                                  │    "SMS_OFF"                      │
       │                                  │                                   │
       │                                  │                                   │ 14. Stop SMS monitoring
       │                                  │                                   │
       │                                  │ 15. Send confirmation             │
       │                                  │<──────────────────────────────────┤
       │                                  │    "SMS_STOPPED"                  │
       │                                  │                                   │
       │ 16. Dispatch event               │                                   │
       │<─────────────────────────────────┤                                   │
       │    sms-confirmation              │                                   │
       │                                  │                                   │
       │ 17. Hide loading spinner         │                                   │
       │     Update UI (show play icon)   │                                   │
       │                                  │                                   │
```

## Features

### 1. Confirmation-Based Control Flow
- **Loading State**: Shows spinner during command execution
- **Timeout Protection**: 30-second timeout if child doesn't respond
- **Event-Driven**: Uses custom events for confirmation messages
- **State Management**: Tracks monitoring state accurately

### 2. Real-Time Message Display
- **Automatic Updates**: Messages appear as they arrive from child
- **Timestamp Tracking**: Only fetches messages newer than latest received
- **Type Indicators**: Shows 📥 for received, 📤 for sent messages
- **Sorted Display**: Newest messages appear first

### 3. Search Functionality
- **Real-Time Filtering**: Filters as you type
- **Multi-Field Search**: Searches both phone numbers and message content
- **Case-Insensitive**: Works regardless of letter case
- **Instant Results**: No delay or button press needed

### 4. Detail Modal
- **Click to View**: Click any message to see full details
- **Complete Information**: Shows type, sender/recipient, date, and full message text
- **Easy Close**: Click X button or outside modal to close
- **Smooth Animation**: Fade-in and slide-up effects

## File Structure
```
sms/
├── sms.js          # Main feature logic
├── sms.html        # UI template
├── sms.css         # Styles
└── README.md       # This file
```

## Key Components

### SmsFeature Class
Main class that manages the SMS feature lifecycle.

**Properties:**
- `container`: DOM element containing the feature
- `isMonitoring`: Boolean tracking monitoring state
- `messages`: Array of received SMS messages
- `latestTimestamp`: Timestamp of most recent message
- `searchTerm`: Current search filter text
- `confirmationTimeout`: Timeout handle for confirmation waiting

**Methods:**
- `init()`: Initialize feature, load template and styles
- `startMonitoring()`: Send SMS_ON command with loading state
- `stopMonitoring()`: Send SMS_OFF command with loading state
- `listenForMessages()`: Setup event listeners for messages and confirmations
- `addMessage(message)`: Add new message to list
- `renderMessages()`: Render filtered message list
- `showDetailModal(message)`: Display message details in modal
- `showLoading()`: Show loading spinner on button
- `hideLoading()`: Hide loading spinner and restore button state
- `clearConfirmationTimeout()`: Clear timeout when confirmation received

## Message Format

### SMS_ON Command
```json
{
  "cmd": "SMS_ON",
  "since": 1234567890
}
```

### SMS Message Data
```json
{
  "type": "sms",
  "address": "+1234567890",
  "body": "Message text",
  "timestamp": 1234567890,
  "sms_type": "inbox"
}
```

### Confirmation Messages
- `SMS_STARTED`: Monitoring successfully started
- `SMS_STOPPED`: Monitoring successfully stopped

## Event System

### Dispatched Events
None - this feature only listens to events

### Listened Events
1. **sms-message**: Dispatched by Connection Manager when SMS data arrives
   - `event.detail`: SMS message object

2. **sms-confirmation**: Dispatched by Connection Manager for confirmations
   - `event.detail.message`: Confirmation string (SMS_STARTED/SMS_STOPPED)

## UI States

### 1. Empty State
- Shows placeholder icon and text
- Play button visible at bottom-right
- Search bar available but inactive

### 2. Loading State
- Play/Pause button shows spinner
- Button disabled during operation
- 30-second timeout protection

### 3. Active State (Monitoring)
- Pause icon visible on button
- Messages displayed in list
- Search actively filters results
- Click messages to view details

### 4. Inactive State (Not Monitoring)
- Play icon visible on button
- Previous messages still visible
- Search still functional
- Can restart monitoring anytime

## Error Handling

### Timeout Protection
If child doesn't respond within 30 seconds:
- Loading spinner hides automatically
- Error message displayed
- User can retry operation

### Connection Errors
If connection manager unavailable:
- Error thrown immediately
- User-friendly error message shown
- No loading state shown

## Styling

### Fixed Button Position
- Button uses `position: fixed` to stay visible during scroll
- Always at bottom-right corner
- Z-index ensures it stays on top

### Modal Styling
- Dark theme matching app design
- Smooth animations (fade-in, slide-up)
- Responsive width (90% max 500px)
- Scrollable content area

## Integration

### Connection Manager Integration
The feature integrates with Connection Manager through:
1. **Command Sending**: `app.connectionManager.sendCommand()`
2. **Event Listening**: Custom events dispatched by Connection Manager
3. **State Synchronization**: Confirmation messages ensure state accuracy

### Window Manager Integration
Loaded dynamically by Window Manager:
1. Feature class exported to `window.SmsFeature`
2. Instantiated with container element
3. Lifecycle managed by Window Manager

## Best Practices

### 1. Always Use Confirmation Flow
Never assume command succeeded - always wait for confirmation:
```javascript
// ❌ Bad
sendCommand('SMS_ON');
this.isMonitoring = true; // Assumes success

// ✅ Good
sendCommand('SMS_ON');
showLoading();
// Wait for SMS_STARTED confirmation event
```

### 2. Implement Timeout Protection
Always set timeout when waiting for confirmation:
```javascript
this.confirmationTimeout = setTimeout(() => {
    if (this.container.querySelector('.loading-spinner')) {
        this.hideLoading();
        this.showError('Timeout');
    }
}, 30000);
```

### 3. Clear Timeouts on Confirmation
Always clear timeout when confirmation arrives:
```javascript
if (message === 'SMS_STARTED') {
    this.clearConfirmationTimeout(); // Important!
    this.hideLoading();
}
```

## Known Issues
None currently.

## Future Enhancements
- Message composition and sending
- Contact name resolution
- Message threading/conversations
- Export messages to file
- Message deletion
- Attachment support (MMS)
