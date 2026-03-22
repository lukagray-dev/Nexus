# Wellbeing (Usage Monitoring) Feature

Monitor app usage statistics, screen time, and device usage patterns on the child device.

## Overview

The Wellbeing feature provides comprehensive insights into how the child device is being used, including total screen time, app usage breakdown by category, and detailed per-app statistics.

## Features

### Summary Statistics
- **Total Screen Time**: Cumulative time spent on device (last 24 hours)
- **Device Unlocks**: Approximate number of times device was unlocked
- **Apps Used**: Total number of apps used in the period

### Usage by Category
- Apps grouped into categories (Social, Entertainment, Games, etc.)
- Visual bar chart showing relative usage
- Time spent per category

### Top Apps List
- Detailed list of most-used apps
- App icons, names, and package names
- Screen time per app
- Sorted by usage time (highest first)

## How It Works

### Parent Side (Desktop)
1. Click "Refresh" button to request usage data
2. Command is sent to child device via WebRTC
3. Child device collects usage statistics
4. Data is sent back and displayed in organized sections

### Android Side
The child device uses:
- `DigitalWellbeingCollector.kt` - Collects usage statistics
- `UsageStatsManager` API - Android system API for app usage data
- Requires `PACKAGE_USAGE_STATS` permission

## Commands

### Request Wellbeing Data
```javascript
app.connectionManager.sendCommand('GET_WELLBEING_DATA');
```

### Response Format
```json
{
    "type": "WELLBEING_DATA",
    "timestamp": 1234567890,
    "total_screen_time_ms": 7200000,
    "device_unlocks": 45,
    "apps": [
        {
            "package_name": "com.example.app",
            "app_name": "Example App",
            "screen_time_ms": 3600000,
            "launch_count": 10,
            "last_used_ms": 1234567890,
            "icon_base64": "...",
            "category": "Social"
        }
    ],
    "categories": [
        {
            "category": "Social",
            "screen_time_ms": 5400000
        }
    ],
    "period_start_ms": 1234567890,
    "period_end_ms": 1234567890,
    "notifications_count": 0
}
```

## App Categories

Apps are automatically categorized based on package name:
- **Social**: Facebook, Instagram, Twitter, WhatsApp, Telegram, Snapchat
- **Entertainment**: YouTube, Netflix, Prime Video, Spotify, Music apps
- **Games**: Apps with "game" or "gaming" in package name
- **Productivity**: Office, Docs, Sheets, Drive, Notion
- **Communication**: Mail, Gmail, Outlook
- **Shopping**: Amazon, eBay, shopping apps
- **News**: News apps, Reddit
- **Health**: Health, fitness, workout apps
- **Education**: Educational apps, learning platforms
- **Other**: Everything else

## Permissions Required (Android)

### Critical Permission
- `PACKAGE_USAGE_STATS` - Required to access app usage statistics
  - Must be granted manually in Settings > Apps > [App Name] > Permit usage access
  - Cannot be requested at runtime (system limitation)

### Error Handling
If permission is not granted, the feature will return:
```json
{
    "type": "WELLBEING_DATA",
    "error": "PERMISSION_DENIED",
    "reason": "PACKAGE_USAGE_STATS permission not granted. Enable 'Permit usage access' in Settings"
}
```

## UI Components

### Summary Cards
- Large cards at the top showing key metrics
- Icons with golden accent color
- Real-time updates when data is refreshed

### Category List
- Visual bars showing relative usage
- Category icons (emojis)
- Time formatted as hours and minutes

### Apps List
- Scrollable list of top 20 apps
- App icons (if available)
- Package names for identification
- Screen time per app

## Usage Example

```javascript
// Request wellbeing data
const app = window.nexusApp;
app.connectionManager.sendCommand('GET_WELLBEING_DATA');

// Listen for response
window.addEventListener('wellbeing-data', (event) => {
    const data = event.detail;
    console.log('Total screen time:', data.total_screen_time_ms);
    console.log('Apps used:', data.apps.length);
});
```

## Data Period

- Default: Last 24 hours
- Updates when refresh button is clicked
- Data is collected from Android's UsageStatsManager
- Historical data depends on Android system retention

## Limitations

- Device unlock count is approximate (based on app launches)
- Notification count requires NotificationListener permission (currently returns 0)
- App icons may not be available for all apps
- Some system apps may not report usage data
- Data accuracy depends on Android version and device manufacturer

## Privacy Considerations

- All data is transmitted directly via WebRTC (peer-to-peer)
- No data is stored on external servers
- Usage statistics are only available to connected parent device
- Child device must grant usage access permission explicitly

## Troubleshooting

### No Data Displayed
1. Ensure child device has granted `PACKAGE_USAGE_STATS` permission
2. Check that child device has been used in the last 24 hours
3. Verify WebRTC connection is active
4. Try refreshing the data

### Permission Denied Error
1. On child device, go to Settings > Apps > [App Name]
2. Find "Permit usage access" or "Usage access"
3. Enable the permission
4. Refresh data on parent device

### Incomplete Data
- Some apps may not report usage data correctly
- System apps may be excluded
- Very short usage times (< 1 minute) may not appear
