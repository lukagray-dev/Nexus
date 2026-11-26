# Real-Time Geolocation Tracking & Interactive Map Specification

Nexus provides on-demand, privacy-preserving real-time geolocation tracking from the Android Child device to the Desktop Parent console:
1. **On-Demand GPS Streaming**: Geolocation data is streamed directly between devices across the WebRTC `nexus-control` DataChannel with **zero cloud database persistence**.
2. **Displacement-Based Power Optimization**: Unlike naive location trackers that spam GPS coordinates every 1–2 seconds, Nexus enforces a strict **5-meter physical displacement filter**. Stationary devices sitting on a desk suppress redundant updates, while an instant cached fix is dispatched immediately upon session activation.
3. **Zero-Config Free Tactical Satellite Map**: Embedded inside Tauri WebView2, the desktop console renders high-contrast **Tactical Satellite Hybrid** (Esri World Imagery + boundary and place labels) which is 100% free, requiring zero external API keys or paid tiers.
4. **DataChannel State Synchronization**: Interactive UI buttons (Start/Stop Tracking) display tactical loading spinners (`loading.svg`) until the Child confirms hardware state changes over the `nexus-control` DataChannel (`ordered: true`).

## 1. DataChannel Control Protocol

All location orchestration and telemetry transmissions occur over the dedicated `nexus-control` DataChannel.

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Response |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `LOCATION_ON` | Requests child to activate FusedLocationProviderClient and start displacement streaming. | `LOCATION_STARTED` or `LOCATION_ERROR: <reason>` |
| **Parent → Child** | `LOCATION_OFF` | Requests child to halt GPS listeners and power down hardware location callbacks. | `LOCATION_STOPPED` |
| **Child → Parent** | `LOCATION_STARTED` | Confirms location tracking is active and callbacks are registered. | Desktop changes Start spinner to active Stop button (`pause.svg`), activates live indicators. |
| **Child → Parent** | `LOCATION_STOPPED` | Confirms location hardware is shut down and listeners unregistered. | Desktop changes Stop spinner to Start button (`play.svg`), displays paused overlay. |
| **Child → Parent** | `LOCATION_ERROR: <reason>` | Reports tracking failure (e.g. missing `ACCESS_FINE_LOCATION` permission). | Desktop aborts spinner, reverts to IDLE, and displays error banner. |
| **Child → Parent** | `{"type":"LOCATION_UPDATE", ...}` | Serialized JSON telemetry payload dispatched on physical movement ($\ge 5$m). | Desktop updates map marker, accuracy halo, camera pan, and telemetry card. |

## 2. Telemetry Payload Schema

Dispatched over the DataChannel when the device moves $\ge 5$ meters from its last sent fix:

```json
{
  "type": "LOCATION_UPDATE",
  "coords": [22.572645, 88.363892],
  "latitude": 22.572645,
  "longitude": 88.363892,
  "accuracy": 4.2,
  "altitude": 12.0,
  "speed": 1.5,
  "bearing": 90.0,
  "timestamp": 1718000000000
}
```

* **`coords`**: `[latitude, longitude]` array matching standard Leaflet `LatLngExpression`.
* **`accuracy`**: 68% confidence radius in meters, rendered as a semi-transparent green halo circle.
* **`speed`**: Device velocity in meters per second (converted to km/h on desktop).
* **`bearing`**: Direction of travel in degrees (0.0 to 360.0).

## 3. End-to-End Sequence Flow

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Start Live Tracking -> Send "LOCATION_ON" (DataChannel) -->|
      |   (UI shows loading.svg spinner, disables button)                     |
      |                                                                       |-- 2. Verify ACCESS_FINE_LOCATION permission
      |                                                                       |-- 3. Register FusedLocationProviderClient
      |                                                                       |      (Priority.PRIORITY_HIGH_ACCURACY)
      |                                                                       |-- 4. Check fusedClient.lastLocation
      |                                                                       |      (instant cached fix if available)
      |                                                                       |-- 5. Send "LOCATION_STARTED" (DataChannel)
      |<-- 6. Receive "LOCATION_STARTED" -------------------------------------|
      |
      |-- 7. Flip loading.svg spinner to pause.svg ("Stop Tracking")
      |-- 8. Hide paused overlay & set sidebar live indicator
      |
      |   [Child Stationary on Desk: distance < 5m -> No Packets Sent]
      |
      |   [Child Moves >= 5 Meters (Walk / Drive)]
      |                                                                       |-- 9. onLocationResult() triggers
      |                                                                       |-- 10. distanceTo(lastSent) >= 5m -> passes filter
      |                                                                       |-- 11. Send LOCATION_UPDATE JSON (DataChannel)
      |<-- 12. Receive LOCATION_UPDATE JSON ----------------------------------|
      |
      |-- 13. Leaflet marker animates to new coords: map.panTo([lat, lng])
      |-- 14. Update GPS accuracy halo circle radius
      |-- 15. Reverse geocode street address via cached Nominatim engine
      |-- 16. Update floating telemetry card (speed, coords, address, ping)
      |
      |-- 17. Click Stop Tracking -> Send "LOCATION_OFF" (DataChannel) ------>|
      |   (UI shows loading.svg spinner)                                      |
      |                                                                       |-- 18. fusedClient.removeLocationUpdates()
      |                                                                       |-- 19. Clear displacement cache & reset state
      |                                                                       |-- 20. Send "LOCATION_STOPPED" (DataChannel)
      |<-- 21. Receive "LOCATION_STOPPED" ------------------------------------|
      |
      |-- 22. Flip loading.svg spinner to play.svg ("Start Live Tracking")
      |-- 23. Show paused overlay & clear sidebar live indicator
```

## 4. Movement Displacement Filtering & Battery Optimization

To maintain industrial reliability without draining child battery:
1. **Initial Instant Fix**: On `startLocationTracking()`, `LocationController` queries `fusedClient.lastLocation`. If a recent position exists, it is dispatched immediately so the parent map pins the device without waiting for a fresh GPS satellite search.
2. **5-Meter Threshold**: Subsequent updates are checked against `lastSentLocation.distanceTo(newLocation)`. Updates with displacement $< 5.0$ meters are dropped.
3. **Peer Disconnection Safety**: If the WebRTC connection drops or fails, `locationCommandHandler.onPeerDisconnected()` immediately unregisters all location updates, guaranteeing the phone's GPS radio turns off.

## 5. Desktop Interactive Map & Telemetry Console Architecture

```text
+------------------------------------------------------------------------------------+
| View Topbar: Location Tracking                                                     |
+------------------------------------------------------------------------------------+
| Map Viewport Area (#location-map)                                                  |
|                                                                                    |
|  +-------------------------------------+                                           |
|  | Floating Telemetry Card (Top Left)  |                                           |
|  | (•) Live Tracking • Just now        |                                           |
|  | Address: Salt Lake, Sector V        |                                           |
|  | Coords: 22.572645° N, 88.363892° E  |                                           |
|  | Accuracy: ±4.2 m | Speed: 1.5 km/h  |                                           |
|  +-------------------------------------+                                           |
|                                                                                    |
|                         (•) Pulsing Tactical Marker                                |
|                        /   \  Accuracy Halo Circle (radius = accuracy)             |
|                                                                                    |
|  [ Paused Overlay: shown when tracking is idle ]                                   |
|                                                                                    |
+------------------------------------------------------------------------------------+
| Floating Bottom Controls Bar                                                       |
|                                                                                    |
|  [ (||) Stop Tracking ]   |   [ Center Target ]   |   [ + ]  [ − ]                 |
+------------------------------------------------------------------------------------+
```

### Free Satellite Hybrid Map Layer
- **World Imagery**: `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`
- **Reference Boundaries & Roads**: `https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`
- **Zero API Key Requirement**: 100% free and open public tile endpoints with zero configuration required from end-users.

### Reverse Geocoding Cache
* Powered by OpenStreetMap Nominatim with local in-memory caching keyed by `${lat.toFixed(4)},${lng.toFixed(4)}`.
* Throttled to 1 request per 2 seconds to avoid rate-limiting.

## 6. Monorepo Component Map

### Android Client (`Nexus Child`)
* `components/location/ILocationController.kt`: Interface abstracting hardware location tracking for unit testability.
* `components/location/LocationController.kt`: Implementation wrapping `FusedLocationProviderClient` with the 5m displacement filter.
* `components/location/LocationCommandHandler.kt`: Dispatches `LOCATION_ON` / `LOCATION_OFF` commands, builds JSON payloads, and handles fail-safe teardown.
* `components/location/LocationComponentTest.kt`: Unit tests verifying permission enforcement, state transitions, JSON contract, and displacement filtering.

### Desktop Parent (`Nexus Parent/desktop`)
* `src/ts/main-content/location/types.ts`: Protocol constants, telemetry models, and formatting helpers (`formatCoordinates`, `formatSpeed`, `formatAccuracy`, `formatRelativeTime`).
* `src/ts/main-content/location/map-manager.ts`: Leaflet map lifecycle, multi-layer tile management, tactical markers, and reverse geocoding.
* `src/ts/main-content/location/location.ts`: Full-bleed interactive view controller with loading spinner synchronization and DataChannel bindings.
* `src/css/main-content/location/location.css`: Tactical dark surveillance stylesheet with zero emojis.
* `test/location-protocol.test.js`: Comprehensive unit tests for state machine, timeouts, error recovery, and formatting utilities.

### Android Parent (`Nexus Parent/android`)
* `data/SurveillanceProtocols.kt` & `data/SurveillanceCoordinator.kt`: DataChannel `LOCATION_ON` / `LOCATION_OFF` command state machine (`20s` timeout), `LOCATION_UPDATE` JSON decoder, `LOCATION_ERROR:` surfacing, and 5-second relative ping ticker.
* `ui/surveillance/location/NexusOsmMapView.kt`: `osmdroid` (`6.1.20`) Compose `AndroidView` switching live between Esri World Imagery + Reference Boundaries (`Satellite`) and CartoDB Dark Matter (`Dark Streets`) with a native `TacticalGpsTargetOverlay` accuracy halo and center pinpoint.
* `ui/surveillance/location/NominatimGeocoder.kt`: 2s-throttled OpenStreetMap Nominatim reverse geocoder with 4-decimal grid caching.
* `ui/surveillance/location/LocationScreen.kt` & `ui/shell/PushDrawerScaffold.kt`: Floating telemetry card, Start/Stop `SpinningLoaderIcon` busy states, `+`/`-` zoom & Center Target controls, and a `24dp` left-edge drawer swipe bezel for unobstructed map panning.
