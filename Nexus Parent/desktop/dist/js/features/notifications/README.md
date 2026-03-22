# Notification Feature

Real-time notification monitoring from child device with command-based control.

## Overview

The Notification feature allows parents to monitor all notifications received on the child's device in real-time. It uses a command-based system where monitoring only starts when explicitly requested and stops when commanded or on disconnection.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Parent (Desktop)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              NotificationsFeature (UI)                    │  │
│  │  • Play/Pause button control                             │  │
│  │  • Search/filter notifications                           │  │
│  │  • Real-time notification list                           │  │
│  │  • Global data persistence                               │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   │ sendCommand('NOTIFICATION_ON/OFF')          │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           ConnectionManager                               │  │
│  │  • Sends commands via DataChannel                        │  │
│  │  • Receives notification data                            │  │
│  │  • Dispatches 'notification-update' events               │  │
│  │  • Dispatches 'notification-confirmation' events         │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
└───────────────────┼──────────────────────────────────────────────┘
                    │
                    │ WebRTC DataChannel
                    │
┌───────────────────┼──────────────────────────────────────────────┐
│                   │              Child (Android)                 │
├───────────────────┼──────────────────────────────────────────────┤
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           CommandHandler                                  │  │
│  │  • Receives NOTIFICATION_ON/OFF commands                 │  │
│  │  • Calls NotificationController methods                  │  │
│  │  • Sends NOTIFICATION_STARTED/STOPPED confirmations      │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         NotificationController                            │  │
│  │  • Manages notification sharing state                    │  │
│  │  • Enables/disables NotificationListener                 │  │
│  │  • Attaches/detaches DataChannel                         │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │      NotificationListenerService (System Service)         │  │
│  │  • Listens to all system notifications                   │  │
│  │  • Batch processing (200ms intervals)                    │  │
│  │  • Rate limiting per app                                 │  │
│  │  • Filters system/empty notifications                    │  │
│  │  • Only sends when sharing is enabled                    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

## Command Flow

### Starting Notification Monitoring

```
Parent                    Child
  │                         │
  │  NOTIFICATION_ON        │
  ├────────────────────────>│
  │                         │
  │                         ├─> NotificationController.startNotificationSharing()
  │                         │   • Attach DataChannel to NotificationListener
  │                         │   • Enable sharing flag
  │                         │
  │  NOTIFICATION_STARTED   │
  │<────────────────────────┤
  │                         │
  │  (Notifications start   │
  │   flowing in real-time) │
  │<························│
  │                         │
```

### Stopping Notification Monitoring

```
Parent                    Child
  │                         │
  │  NOTIFICATION_OFF       │
  ├────────────────────────>│
  │                         │
  │                         ├─> NotificationController.stopNotificationSharing()
  │                         │   • Disable sharing flag
  │                         │   • Detach DataChannel
  │                         │
  │  NOTIFICATION_STOPPED   │
  │<────────────────────────┤
  │                         │
```

## Data Format

### Notification Message (Single)

```json
{
  "version": 2,
  "type": "NOTIFICATION",
  "ts": 1234567890123,
  "notificationId": 12345,
  "notificationKey": "0|com.example.app|12345|null|10001",
  "childId": "device-id-here",
  "body": {
    "appName": "WhatsApp",
    "packageName": "com.whatsapp",
    "title": "John Doe",
    "text": "Hey, how are you?",
    "subText": "WhatsApp",
    "bigText": "Full message content here...",
    "priority": "default",
    "category": "msg",
    "isOngoing": false,
    "postTime": 1234567890000,
    "actionCount": 2,
    "actions": ["Reply", "Mark as read"]
  }
}
```

### Notification Batch

```json
{
  "version": 2,
  "type": "NOTIFICATION_BATCH",
  "ts": 1234567890123,
  "childId": "device-id-here",
  "count": 3,
  "notifications": [
    { /* notification 1 */ },
    { /* notification 2 */ },
    { /* notification 3 */ }
  ]
}
```

## Features

### Parent Side (Desktop)

1. **Command-Based Control**
   - Start/stop monitoring with play/pause button
   - Confirmation-based flow with loading spinner
   - 30-second timeout protection

2. **Real-Time Display**
   - Notifications appear instantly as they arrive
   - Shows app name, title, text, and timestamp
   - Relative time display (e.g., "2m ago", "1h ago")

3. **Search & Filter**
   - Real-time search across app name, title, and text
   - Case-insensitive filtering
   - Instant results

4. **Data Persistence**
   - Stores up to 500 notifications in memory
   - Data persists when panel is closed/reopened
   - Global storage using `window.notificationFeatureData`

5. **UI Features**
   - Fixed play/pause button (doesn't scroll)
   - Smooth scrolling list
   - Empty state placeholder
   - Hover effects on notification items

### Child Side (Android)

1. **NotificationListenerService**
   - System-level notification access
   - Listens to all app notifications
   - Runs as a system service

2. **Batch Processing**
   - Groups notifications in 200ms intervals
   - Reduces overhead and network usage
   - Sends single notifications immediately if batch size = 1

3. **Rate Limiting**
   - Max 5000 notifications per app per window
   - 50-second rate limit window
   - Prevents spam from chatty apps

4. **Smart Filtering**
   - Ignores system notifications
   - Filters empty notifications
   - Skips group summary notifications (prevents duplicates)
   - Ignores own app notifications

5. **Rich Notification Data**
   - Extracts title, text, big text, sub text
   - Captures notification actions
   - Includes priority and category
   - Tracks ongoing status

## File Structure

```
Nexus Parent/desktop/dist/js/features/notifications/
├── notifications.js       # Main feature logic
├── notifications.html     # UI template
├── notifications.css      # Styling
└── README.md             # This file

Nexus Child/android/app/src/main/java/nexus/android/child/
├── components/notification/
│   ├── NotificationListener.kt      # System notification listener
│   └── NotificationController.kt    # Command-based controller
└── commands/
    └── CommandHandler.kt            # Handles NOTIFICATION_ON/OFF commands
```

## Usage

### Parent Side

1. Open the Notifications feature panel
2. Click the play button to start monitoring
3. Wait for confirmation (loading spinner)
4. Notifications will appear in real-time
5. Use search box to filter notifications
6. Click pause button to stop monitoring

### Child Side

The child side is fully automatic:
- NotificationListenerService must be enabled in Android settings
- Service listens to all notifications continuously
- Only sends notifications when parent requests (NOTIFICATION_ON)
- Stops sending when parent stops (NOTIFICATION_OFF)
- Automatically stops on disconnection

## Permissions Required

### Android (Child)

- **Notification Access** - Required for NotificationListenerService
  - Settings → Apps → Special Access → Notification Access
  - Enable for Nexus Child app

## Performance Considerations

1. **Batch Processing** - Reduces network overhead by grouping notifications
2. **Rate Limiting** - Prevents overwhelming the connection with spam
3. **Smart Filtering** - Reduces unnecessary data transmission
4. **Memory Management** - Keeps only last 500 notifications on parent side
5. **Efficient Rendering** - Only renders visible notifications

## Troubleshooting

### Notifications Not Appearing

1. Check if notification access is enabled on child device
2. Verify WebRTC connection is active
3. Check if monitoring is started (pause button visible)
4. Look for confirmation timeout errors in console

### Missing Notifications

1. Check rate limiting - app may have exceeded limit
2. Verify notification isn't filtered (empty or system notification)
3. Check if notification is a group summary (filtered by default)

### Performance Issues

1. Clear old notifications (data persists in memory)
2. Reduce batch size if needed (currently 100M - very high)
3. Check network connection quality
4. Verify child device isn't under heavy load

## Security & Privacy

- Notifications are transmitted over encrypted WebRTC DataChannel
- No notifications are stored permanently on parent device
- Monitoring only active when explicitly started
- Automatically stops on disconnection
- Child device can disable notification access at any time

## Future Enhancements

- [ ] Notification detail view with full content
- [ ] Notification actions (reply, dismiss, etc.)
- [ ] Notification grouping by app
- [ ] Export notifications to file
- [ ] Notification statistics and analytics
- [ ] Custom filtering rules
- [ ] Notification sound alerts on parent side
