# Location Feature

## Overview
The Location feature provides real-time GPS tracking of the child device on an interactive satellite map. It displays the device's current location with automatic reverse geocoding to show readable location names.

## Architecture

### Flow Diagram
```
┌─────────────┐                    ┌──────────────┐                    ┌─────────────┐
│   Parent    │                    │  Connection  │                    │    Child    │
│(Location UI)│                    │   Manager    │                    │   Device    │
└──────┬──────┘                    └──────┬───────┘                    └──────┬──────┘
       │                                  │                                   │
       │ 1. User clicks Play              │                                   │
       ├─────────────────────────────────>│                                   │
       │    startTracking()                │                                   │
       │                                  │                                   │
       │ 2. Show loading spinner          │                                   │
       │    (30s timeout starts)          │                                   │
       │                                  │                                   │
       │                                  │ 3. Send LOCATE_CHILD command      │
       │                                  ├──────────────────────────────────>│
       │                                  │                                   │
       │                                  │                                   │ 4. Start GPS tracking
       │                                  │                                   │    Request location updates
       │                                  │                                   │
       │                                  │ 5. Send confirmation              │
       │                                  │<──────────────────────────────────┤
       │                                  │    "LOCATION_STARTED"             │
       │                                  │                                   │
       │ 6. Dispatch event                │                                   │
       │<─────────────────────────────────┤                                   │
       │    location-confirmation         │                                   │
       │                                  │                                   │
       │ 7. Hide loading spinner          │                                   │
       │    Update UI (show pause icon)   │                                   │
       │                                  │                                   │
       │                                  │ 8. Send location updates          │
       │                                  │<──────────────────────────────────┤
       │                                  │    {type: "LOCATION_UPDATE",      │
       │                                  │     coords: [lat, lng],           │
       │                                  │     accuracy: 20,                 │
       │                                  │     timestamp: 1234567890}        │
       │                                  │                                   │
       │ 9. Dispatch event                │                                   │
       │<─────────────────────────────────┤                                   │
       │    location-update               │                                   │
       │                                  │                                   │
       │ 10. Update map marker            │                                   │
       │     Pan to location              │                                   │
       │     Reverse geocode address      │                                   │
       │     Update header info           │                                   │
       │                                  │                                   │
       │ 11. User clicks Pause            │                                   │
       ├─────────────────────────────────>│                                   │
       │    stopTracking()                │                                   │
       │                                  │                                   │
       │ 12. Show loading spinner         │                                   │
       │     (30s timeout starts)         │                                   │
       │                                  │                                   │
       │                                  │ 13. Send LOCATE_CHILD_STOP        │
       │                                  ├──────────────────────────────────>│
       │                                  │                                   │
       │                                  │                                   │ 14. Stop GPS tracking
       │                                  │                                   │
       │                                  │ 15. Send confirmation             │
       │                                  │<──────────────────────────────────┤
       │                                  │    "LOCATION_STOPPED"             │
       │                                  │                                   │
       │ 16. Dispatch event               │                                   │
       │<─────────────────────────────────┤                                   │
       │    location-confirmation         │                                   │
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

### 2. Real-Time Location Tracking
- **GPS Updates**: Receives continuous location updates from child device
- **Animated Transitions**: Smooth map panning to new locations
- **Accuracy Info**: Displays GPS accuracy in meters
- **Timestamp**: Shows when location was captured

### 3. Interactive Map
- **Satellite Imagery**: High-quality satellite tiles from ArcGIS
- **Reference Layer**: Place names and boundaries overlay
- **Zoom Controls**: Standard Leaflet zoom in/out controls
- **Custom Marker**: Golden animated marker with pulse effect
- **Popup**: Shows location name and coordinates on marker

### 4. Reverse Geocoding
- **Automatic**: Converts coordinates to readable addresses
- **OpenStreetMap**: Uses Nominatim API for geocoding
- **Smart Naming**: Prioritizes landmarks, roads, suburbs, cities
- **Fallback**: Shows "Unknown Location" if geocoding fails

### 5. Data Persistence
- **Map State**: Map and marker persist when panel is closed/reopened
- **Last Location**: Remembers last known location
- **Tracking State**: Preserves play/pause state across sessions

## File Structure
```
location/
├── location.js      # Main feature logic
├── location.html    # UI template
├── location.css     # Styles
└── README.md        # This file
```

## Key Components

### LocationFeature Class
Main class that manages the Location feature lifecycle.

**Properties:**
- `container`: DOM element containing the feature
- `confirmationTimeout`: Timeout handle for confirmation waiting
- `data`: Reference to global storage (`window.locationFeatureData`)
  - `isTracking`: Boolean tracking state
  - `currentLocation`: Object with lat, lng, name
  - `map`: Leaflet map instance
  - `marker`: Leaflet marker instance

**Methods:**
- `init()`: Initialize feature, load Leaflet, create map
- `loadLeaflet()`: Dynamically load Leaflet library from CDN
- `initializeMap()`: Create Leaflet map with satellite tiles
- `startTracking()`: Send LOCATE_CHILD command with loading state
- `stopTracking()`: Send LOCATE_CHILD_STOP command with loading state
- `updateLocation(lat, lng, name)`: Update marker position and info
- `reverseGeocode(lat, lng)`: Convert coordinates to location name
- `updateLocationInfo(lat, lng, name)`: Update header display
- `showLoading()`: Show loading spinner on button
- `hideLoading()`: Hide loading spinner and restore button state
- `clearConfirmationTimeout()`: Clear timeout when confirmation received

## Message Format

### Commands
```javascript
// Start tracking
"LOCATE_CHILD"

// Stop tracking
"LOCATE_CHILD_STOP"
```

### Location Update Data
```json
{
  "type": "LOCATION_UPDATE",
  "coords": [24.1755123, 87.7922851],
  "accuracy": 20,
  "timestamp": 1770406480402
}
```

**Fields:**
- `type`: Always "LOCATION_UPDATE"
- `coords`: Array [latitude, longitude]
- `accuracy`: GPS accuracy in meters
- `timestamp`: Unix timestamp in milliseconds

### Confirmation Messages
- `LOCATION_STARTED`: Tracking successfully started
- `LOCATION_STOPPED`: Tracking successfully stopped

## Event System

### Dispatched Events
None - this feature only listens to events

### Listened Events
1. **location-update**: Dispatched by Connection Manager when location data arrives
   - `event.detail.lat`: Latitude
   - `event.detail.lng`: Longitude
   - `event.detail.name`: Location name (optional)
   - `event.detail.accuracy`: GPS accuracy (optional)
   - `event.detail.timestamp`: Timestamp (optional)

2. **location-confirmation**: Dispatched by Connection Manager for confirmations
   - `event.detail.message`: Confirmation string (LOCATION_STARTED/LOCATION_STOPPED)

## UI States

### 1. Idle State
- Map centered at default location (23.5, 87.3)
- Marker at default position
- Play button visible
- Header shows "Waiting..." and "--"

### 2. Loading State
- Play/Pause button shows spinner
- Button disabled during operation
- 30-second timeout protection

### 3. Tracking State (Active)
- Pause icon visible on button
- Map updates with each location update
- Marker animates to new position
- Header shows location name and coordinates
- Flash effect on location updates

### 4. Tracking State (Inactive)
- Play icon visible on button
- Map shows last known location
- Can restart tracking anytime

## Map Configuration

### Tile Layers
1. **Satellite Imagery**
   - URL: `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`
   - Max Zoom: 19

2. **Reference Layer**
   - URL: `https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`
   - Max Zoom: 19

### Custom Marker
- **Color**: Golden (rgba(251, 191, 36, 0.9))
- **Shape**: Circle with pin pointer
- **Animation**: Pulse effect (2s infinite)
- **Size**: 20x20px
- **Anchor**: Center bottom

## Reverse Geocoding

### API
- **Service**: OpenStreetMap Nominatim
- **Endpoint**: `https://nominatim.openstreetmap.org/reverse`
- **Format**: JSON
- **Zoom**: 18 (street level)

### Address Priority
1. Railway stations
2. Amenities (shops, restaurants, etc.)
3. Buildings
4. Roads + Suburbs
5. Suburbs
6. Cities/Towns/Villages
7. First part of display_name

### Rate Limiting
- Nominatim has usage limits
- Requests are only made when location name is not provided
- Failed requests fallback to "Unknown Location"

## Error Handling

### Timeout Protection
If child doesn't respond within 30 seconds:
- Loading spinner hides automatically
- Error message displayed in header (red text)
- User can retry operation

### Connection Errors
If connection manager unavailable:
- Error thrown immediately
- User-friendly error message shown in header
- No loading state shown

### Geocoding Errors
If reverse geocoding fails:
- Keeps "Unknown Location" text
- Coordinates still displayed
- Map and marker still functional

## Styling

### Header
- **Layout**: Horizontal with title left, info right
- **Background**: Semi-transparent dark (rgba(24, 24, 27, 0.5))
- **Border**: Bottom border for separation
- **Compact**: Single row design

### Map
- **Background**: Black
- **Full Height**: Takes remaining space
- **Zoom Controls**: Top-left corner
- **No Attribution**: Cleaner look

### Play/Pause Button
- **Position**: Fixed bottom-right (16px, 16px)
- **Size**: 48x48px circular
- **Color**: Golden (rgba(251, 191, 36, 0.9))
- **Hover**: Scale 1.1 with brighter color
- **Shadow**: Elevated appearance

### Flash Effect
- **Trigger**: On location update
- **Duration**: 1 second
- **Effect**: Golden glow shadow
- **Purpose**: Visual feedback for updates

## Integration

### Connection Manager Integration
The feature integrates with Connection Manager through:
1. **Command Sending**: `app.connectionManager.sendCommand()`
2. **Event Listening**: Custom events dispatched by Connection Manager
3. **State Synchronization**: Confirmation messages ensure state accuracy

### Window Manager Integration
Loaded dynamically by Window Manager:
1. Feature class exported to `window.LocationFeature`
2. Instantiated with container element
3. Lifecycle managed by Window Manager

## Best Practices

### 1. Always Use Confirmation Flow
Never assume command succeeded - always wait for confirmation:
```javascript
// ❌ Bad
sendCommand('LOCATE_CHILD');
this.data.isTracking = true; // Assumes success

// ✅ Good
sendCommand('LOCATE_CHILD');
showLoading();
// Wait for LOCATION_STARTED confirmation event
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
if (message === 'LOCATION_STARTED') {
    this.clearConfirmationTimeout(); // Important!
    this.hideLoading();
}
```

### 4. Handle Map Lifecycle
Properly manage Leaflet map instance:
```javascript
// Remove old map before creating new one
if (this.data.map) {
    this.data.map.remove();
    this.data.map = null;
}
```

## Known Issues
None currently.

## Future Enhancements
- Location history trail on map
- Geofencing alerts
- Distance/speed calculations
- Export location history to file
- Multiple location markers (history)
- Offline map tiles
- Custom map styles
- Location sharing via link
