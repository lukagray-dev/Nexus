# Chats Feature

Real-time chat message monitoring from child device with command-based control using Android Accessibility Service.

## Overview

The Chats feature allows parents to monitor chat messages from popular messaging apps on the child's device in real-time. It uses Android's Accessibility Service to capture chat messages and a command-based system where monitoring only starts when explicitly requested and stops when commanded or on disconnection.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Parent (Desktop)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                  ChatsFeature (UI)                        │  │
│  │  • Play/Pause button control                             │  │
│  │  • Search/filter chats                                   │  │
│  │  • Real-time chat message list                           │  │
│  │  • Global data persistence                               │  │
│  │  • App name mapping (WhatsApp, Telegram, etc.)          │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   │ sendCommand('CHAT_ON/OFF')                  │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           ConnectionManager                               │  │
│  │  • Sends commands via DataChannel                        │  │
│  │  • Receives chat data                                    │  │
│  │  • Dispatches 'chat-update' events                       │  │
│  │  • Dispatches 'chat-confirmation' events                 │  │
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
│  │  • Receives CHAT_ON/OFF commands                         │  │
│  │  • Calls ChatController methods                          │  │
│  │  • Sends CHAT_STARTED/STOPPED confirmations             │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              ChatController                               │  │
│  │  • Manages chat monitoring state                         │  │
│  │  • Enables/disables ChatMonitor                          │  │
│  │  • Implements DataChannelClient interface                │  │
│  │  • Sends chat data over DataChannel                      │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │      ChatMonitor (Accessibility Service Processor)        │  │
│  │  • Processes accessibility events                        │  │
│  │  • Extracts chat messages from UI                        │  │
│  │  • Detects typing indicators                            │  │
│  │  • Deduplicates messages (SHA-256 hashing)              │  │
│  │  • Rate limiting per contact (700ms debounce)           │  │
│  │  • Only sends when monitoring is enabled                │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│                   ▼                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │    Android Accessibility Service (System Service)         │  │
│  │  • Monitors UI changes in messaging apps                 │  │
│  │  • Captures window state changes                         │  │
│  │  • Detects text changes and scrolling                    │  │
│  │  • Provides accessibility node tree                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

## Command Flow

### Starting Chat Monitoring

```
Parent                    Child
  │                         │
  │  CHAT_ON                │
  ├────────────────────────>│
  │                         │
  │                         ├─> ChatController.startChatMonitoring()
  │                         │   • Create DataChannelClient implementation
  │                         │   • Attach to ChatMonitor
  │                         │   • Enable monitoring flag
  │                         │
  │  CHAT_STARTED           │
  │<────────────────────────┤
  │                         │
  │  (Chat messages start   │
  │   flowing in real-time) │
  │<························│
  │                         │
```

### Stopping Chat Monitoring

```
Parent                    Child
  │                         │
  │  CHAT_OFF               │
  ├────────────────────────>│
  │                         │
  │                         ├─> ChatController.stopChatMonitoring()
  │                         │   • Disable monitoring flag
  │                         │   • Detach DataChannelClient
  │                         │
  │  CHAT_STOPPED           │
  │<────────────────────────┤
  │                         │
```

## Data Format

### Message Event

```json
{
  "childId": "device-id-here",
  "type": "MESSAGE",
  "ts": 1234567890123,
  "body": {
    "app": "com.whatsapp",
    "contactId": "contact-unique-id",
    "contactName": "John Doe",
    "messages": [
      {
        "msgId": "msg-123",
        "text": "Hey, how are you?",
        "sender": "John Doe",
        "isSent": false,
        "ts": 1234567890000
      },
      {
        "msgId": "msg-124",
        "text": "I'm good, thanks!",
        "sender": "Me",
        "isSent": true,
        "ts": 1234567891000
      }
    ]
  }
}
```

### Typing Event

```json
{
  "childId": "device-id-here",
  "type": "TYPING",
  "ts": 1234567890123,
  "body": {
    "app": "com.whatsapp",
    "contactId": "contact-unique-id",
    "contactName": "John Doe",
    "isTyping": true
  }
}
```

### App Foreground Event

```json
{
  "childId": "device-id-here",
  "type": "APP_FOREGROUND",
  "ts": 1234567890123,
  "body": {
    "packageName": "com.whatsapp",
    "contactId": "contact-unique-id",
    "contactName": "John Doe",
    "visibleMessages": [
      {
        "msgId": "msg-123",
        "text": "Last visible message",
        "sender": "John Doe",
        "isSent": false,
        "ts": 1234567890000
      }
    ]
  }
}
```

## Supported Apps

The ChatMonitor currently supports the following messaging apps:

- **WhatsApp** (`com.whatsapp`)
- **Instagram** (`com.instagram.android`)
- **Telegram** (`org.telegram.messenger`)
- **Snapchat** (`com.snapchat.android`)
- **Facebook Messenger** (`com.facebook.orca`)
- **Facebook** (`com.facebook.katana`)

## Features

### Parent Side (Desktop)

1. **Command-Based Control**
   - Start/stop monitoring with play/pause button
   - Confirmation-based flow with loading spinner
   - 30-second timeout protection

2. **Real-Time Display**
   - Chat messages appear instantly as they arrive
   - Shows app name, contact name, last message, and timestamp
   - Relative time display (e.g., "2m ago", "1h ago")
   - App name mapping for better readability

3. **Search & Filter**
   - Real-time search across app name, contact name, and message text
   - Case-insensitive filtering
   - Instant results

4. **Data Persistence**
   - Stores up to 500 chat messages in memory
   - Data persists when panel is closed/reopened
   - Global storage using `window.chatFeatureData`

5. **UI Features**
   - Fixed play/pause button (doesn't scroll)
   - Smooth scrolling list
   - Empty state placeholder
   - Hover effects on chat items
   - Color-coded app names (golden accent)

### Child Side (Android)

1. **Accessibility Service Integration**
   - System-level access to app UI
   - Monitors window state changes
   - Captures text changes and scrolling events
   - Extracts messages from accessibility node tree

2. **Smart Message Extraction**
   - App-specific extractors for each messaging app
   - Generic fallback extractor for unsupported apps
   - Detects sender, message text, and timestamps
   - Identifies sent vs received messages

3. **Deduplication & Rate Limiting**
   - SHA-256 hashing to prevent duplicate messages
   - Per-contact debouncing (700ms window)
   - Filters out numeric noise (status indicators)
   - Minimum text length filtering

4. **Typing Detection**
   - Real-time typing indicator monitoring
   - Per-contact typing state tracking
   - Sends typing start/stop events

5. **Performance Optimization**
   - Async processing with Kotlin coroutines
   - Non-blocking event handling
   - Efficient node tree traversal
   - Whitelist-based app filtering

## File Structure

```
Nexus Parent/desktop/dist/js/features/chats/
├── chats.js              # Main feature logic
├── chats.html            # UI template
├── chats.css             # Styling
└── README.md             # This file

Nexus Child/android/app/src/main/java/nexus/android/child/
├── components/chat/
│   ├── ChatMonitor.kt           # Accessibility event processor
│   ├── ChatController.kt        # Command-based controller
│   ├── DataChannelClient.kt     # Interface for sending data
│   ├── ChatExtractor.kt         # Base extractor interface
│   ├── GenericChatExtractor.kt  # Fallback extractor
│   ├── ChatPayload.kt           # Data models
│   └── ChatJson.kt              # JSON serialization
└── commands/
    └── CommandHandler.kt        # Handles CHAT_ON/OFF commands
```

## Usage

### Parent Side

1. Open the Chats feature panel
2. Click the play button to start monitoring
3. Wait for confirmation (loading spinner)
4. Chat messages will appear in real-time
5. Use search box to filter chats
6. Click pause button to stop monitoring

### Child Side

The child side requires setup:

1. **Enable Accessibility Service**
   - Settings → Accessibility → Installed Services
   - Enable "Nexus Child" accessibility service
   - Grant permission when prompted

2. **Automatic Operation**
   - ChatMonitor runs continuously in background
   - Only sends data when parent requests (CHAT_ON)
   - Stops sending when parent stops (CHAT_OFF)
   - Automatically stops on disconnection

## Permissions Required

### Android (Child)

- **Accessibility Service** - Required for ChatMonitor
  - Settings → Accessibility → Installed Services → Nexus Child
  - Enable the service
  - This is a sensitive permission that requires user action

## How It Works

### Message Extraction Process

1. **Accessibility Event Triggered**
   - User opens a messaging app
   - New message arrives
   - User scrolls through chat

2. **Event Filtering**
   - ChatMonitor checks if app is whitelisted
   - Ignores events from non-messaging apps
   - Processes relevant event types only

3. **UI Tree Traversal**
   - Extracts accessibility node tree
   - Identifies message containers
   - Extracts text, sender, and metadata

4. **Deduplication**
   - Combines all visible messages
   - Generates SHA-256 hash
   - Compares with last seen hash
   - Only sends if hash differs

5. **Rate Limiting**
   - Checks last send time for contact
   - Enforces 700ms debounce window
   - Prevents spam from rapid UI updates

6. **Data Transmission**
   - Serializes to JSON
   - Sends via WebRTC DataChannel
   - Handles send failures gracefully

## Performance Considerations

1. **Async Processing** - All event handling is non-blocking
2. **Debouncing** - Prevents excessive data transmission
3. **Deduplication** - Avoids sending duplicate messages
4. **Whitelist Filtering** - Only monitors specific apps
5. **Memory Management** - Keeps only last 500 chats on parent side
6. **Efficient Rendering** - Only renders visible chat items

## Troubleshooting

### Chats Not Appearing

1. Check if Accessibility Service is enabled on child device
2. Verify WebRTC connection is active
3. Check if monitoring is started (pause button visible)
4. Look for confirmation timeout errors in console
5. Ensure messaging app is in the supported apps list

### Missing Messages

1. Check rate limiting - contact may have exceeded debounce window
2. Verify message isn't filtered (too short or numeric)
3. Check if message is a duplicate (same hash as previous)
4. Ensure app is in the whitelist

### Accessibility Service Not Working

1. Disable and re-enable the service in Android settings
2. Restart the Nexus Child app
3. Check Android version compatibility (requires Android 5.0+)
4. Verify no other accessibility services are conflicting

### Performance Issues

1. Clear old chats (data persists in memory)
2. Reduce debounce window if needed (currently 700ms)
3. Check network connection quality
4. Verify child device isn't under heavy load
5. Ensure only necessary apps are whitelisted

## Security & Privacy

- Chat messages are transmitted over encrypted WebRTC DataChannel
- No messages are stored permanently on parent device
- Monitoring only active when explicitly started
- Automatically stops on disconnection
- Child device can disable Accessibility Service at any time
- Accessibility Service is a sensitive permission requiring user consent

## Limitations

1. **Accessibility Service Dependency**
   - Requires user to manually enable in Android settings
   - Can be disabled by user at any time
   - May not work with all app versions

2. **App-Specific Extraction**
   - Each app has different UI structure
   - May break with app updates
   - Generic extractor has limited accuracy

3. **Rate Limiting**
   - 700ms debounce may miss rapid messages
   - Designed to balance accuracy and performance

4. **No Historical Messages**
   - Only captures messages visible on screen
   - Cannot retrieve message history
   - Starts monitoring from current point

## Future Enhancements

- [ ] Chat detail view with full conversation history
- [ ] Message search within conversations
- [ ] Export chats to file
- [ ] Chat statistics and analytics
- [ ] Support for more messaging apps
- [ ] Media attachment detection
- [ ] Group chat support
- [ ] Message reactions and status indicators
- [ ] Custom filtering rules per app
- [ ] Notification integration

## Technical Notes

### Why Accessibility Service?

Android doesn't provide a standard API for reading chat messages from third-party apps. The Accessibility Service is the only legitimate way to monitor app UI changes without root access or app modifications.

### Deduplication Strategy

Messages are deduplicated using SHA-256 hashing of all visible messages combined. This prevents sending the same message multiple times when the UI updates (e.g., during scrolling or new message arrival).

### Rate Limiting Rationale

The 700ms debounce window balances between capturing all messages and avoiding excessive data transmission. This window is tuned for typical messaging app behavior where UI updates happen in bursts.

### App Whitelisting

Only specific messaging apps are monitored to reduce system overhead and focus on relevant data. This also improves privacy by not monitoring all apps on the device.
