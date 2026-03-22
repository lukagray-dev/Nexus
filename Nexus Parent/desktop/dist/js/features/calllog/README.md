# Call Log Feature

## Overview
The Call Log feature allows the parent device to monitor and view call history from the connected child device in real-time. It provides a clean interface for browsing call logs with search functionality and detailed views.

## Architecture

### Flow Diagram
```
┌─────────────┐                    ┌──────────────┐                    ┌─────────────┐
│   Parent    │                    │  Connection  │                    │    Child    │
│(CallLog UI) │                    │   Manager    │                    │   Device    │
└──────┬──────┘                    └──────┬───────┘                    └──────┬──────┘
       │                                  │                                   │
       │ 1. User clicks Play              │                                   │
       ├─────────────────────────────────>│                                   │
       │    startMonitoring()              │                                   │
       │                                  │                                   │
       │ 2. Show loading spinner          │                                   │
       │    (30s timeout starts)          │                                   │
       │                                  │                                   │
       │                                  │ 3. Send CALLLOG_ON command        │
       │                                  ├──────────────────────────────────>│
       │                                  │    {cmd: "CALLLOG_ON", since: 0}  │
       │                                  │                                   │
       │                                  │                                   │ 4. Start call log monitoring
       │                                  │                                   │    Read call log database
       │                                  │                                   │
       │                                  │ 5. Send confirmation              │
       │                                  │<──────────────────────────────────┤
       │                                  │    "CALLLOG_STARTED"              │
       │                                  │                                   │
       │ 6. Dispatch event                │                                   │
       │<─────────────────────────────────┤                                   │
       │    calllog-confirmation          │                                   │
       │                                  │                                   │
       │ 7. Hide loading spinner          │                                   │
       │    Update UI (show pause icon)   │                                   │
       │                                  │                                   │
       │                                  │ 8. Send call log entries          │
       │                                  │<──────────────────────────────────┤
       │                                  │    {type: "calllog", ...data}     │
       │                                  │                                   │
       │ 9. Dispatch event                │                                   │
       │<─────────────────────────────────┤                                   │
       │    calllog-message               │                                   │
       │                                  │                                   │
       │ 10. Display log in list          │                                   │
       │                                  │                                   │
       │ 11. User clicks Pause            │                                   │
       ├─────────────────────────────────>│                                   │
       │    stopMonitoring()              │                                   │
       │                                  │                                   │
       │ 12. Show loading spinner         │                                   │
       │     (30s timeout starts)         │                                   │
       │                                  │                                   │
       │                                  │ 13. Send CALLLOG_OFF command      │
       │                                  ├──────────────────────────────────>│
       │                                  │    "CALLLOG_OFF"                  │
       │                                  │                                   │
       │                                  │                                   │ 14. Stop call log monitoring
       │                                  │                                   │
       │                                  │ 15. Send confirmation             │
       │                                  │<──────────────────────────────────┤
       │                                  │    "CALLLOG_STOPPED"              │
       │                                  │                                   │
       │ 16. Dispatch event               │                                   │
       │<─────────────────────────────────┤                                   │
       │    calllog-confirmation          │                                   │
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

### 2. Real-Time Call Log Display
- **Automatic Updates**: Logs appear as they arrive from child
- **Timestamp Tracking**: Only fetches logs newer than latest received
- **Type Indicators**: Shows 📥 for incoming, 📤 for outgoing, ❗ for missed
- **Sorted Display**: Newest calls appear first

### 3. Search Functionality
- **Real-Time Filtering**: Filters as you type
- **Multi-Field Search**: Searches both phone numbers and contact names
- **Case-Insensitive**: Works regardless of letter case
- **Instant Results**: No delay or button press needed

### 4. Detail Modal
- **Click to View**: Click any log entry to see full details
- **Complete Information**: Shows type, number, contact name, date, and duration
- **Easy Close**: Click X button or outside modal to close
- **Smooth Animation**: Fade-in and slide-up effects

## File Structure
```
calllog/
├── calllog.js      # Main feature logic
├── calllog.html    # UI template
├── calllog.css     # Styles
└── README.md       # This file
```

## Key Components

### CalllogFeature Class
Main class that manages the Call Log feature lifecycle.

**Properties:**
- `container`: DOM element containing the feature
- `isMonitoring`: Boolean tracking monitoring state
- `logs`: Array of received call log entries
- `latestTimestamp`: Timestamp of most recent log entry
- `searchTerm`: Current search filter text
- `confirmationTimeout`: Timeout handle for confirmation waiting

**Methods:**
- `init()`: Initialize feature, load template and styles
- `startMonitoring()`: Send CALLLOG_ON command with loading state
- `stopMonitoring()`: Send CALLLOG_OFF command with loading state
- `listenForLogs()`: Setup event listeners for logs and confirmations
- `addLog(log)`: Add new log entry to list
- `renderLogs()`: Render filtered log list
- `showDetailModal(log)`: Display log details in modal
- `formatDuration(seconds)`: Format call duration (e.g., "2m 30s")
- `formatCallType(type)`: Format call type for display
- `showLoading()`: Show loading spinner on button
- `hideLoading()`: Hide loading spinner and restore button state
- `clearConfirmationTimeout()`: Clear timeout when confirmation received

## Message Format

### CALLLOG_ON Command
```json
{
  "cmd": "CALLLOG_ON",
  "since": 1234567890
}
```

### Call Log Data
```json
{
  "type": "calllog",
  "number": "+1234567890",
  "name": "John Doe",
  "timestamp": 1234567890,
  "duration": 120,
  "call_type": "incoming"
}
```

**Call Types:**
- `incoming`: Received call
- `outgoing`: Dialed call
- `missed`: Missed call

### Confirmation Messages
- `CALLLOG_STARTED`: Monitoring successfully started
- `CALLLOG_STOPPED`: Monitoring successfully stopped

## Event System

### Dispatched Events
None - this feature only listens to events

### Listened Events
1. **calllog-message**: Dispatched by Connection Manager when call log data arrives
   - `event.detail`: Call log object

2. **calllog-confirmation**: Dispatched by Connection Manager for confirmations
   - `event.detail.message`: Confirmation string (CALLLOG_STARTED/CALLLOG_STOPPED)

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
- Call logs displayed in list
- Search actively filters results
- Click logs to view details

### 4. Inactive State (Not Monitoring)
- Play icon visible on button
- Previous logs still visible
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
1. Feature class exported to `window.CalllogFeature`
2. Instantiated with container element
3. Lifecycle managed by Window Manager

## Best Practices

### 1. Always Use Confirmation Flow
Never assume command succeeded - always wait for confirmation:
```javascript
// ❌ Bad
sendCommand('CALLLOG_ON');
this.isMonitoring = true; // Assumes success

// ✅ Good
sendCommand('CALLLOG_ON');
showLoading();
// Wait for CALLLOG_STARTED confirmation event
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
if (message === 'CALLLOG_STARTED') {
    this.clearConfirmationTimeout(); // Important!
    this.hideLoading();
}
```

## Known Issues
None currently.

## Future Enhancements
- Call initiation from parent
- Call blocking functionality
- Contact name resolution from device
- Export logs to file
- Call recording playback (if available)
- Statistics and analytics (most called, call duration trends)
