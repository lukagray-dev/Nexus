# Wallpaper Control & Vibrate-Flash Specification

Nexus provides on-demand, privacy-preserving wallpaper surveillance, remote wallpaper customization, and hardware actuation (vibration and flashlight strobes) from the Android Child device to the Desktop Parent console:
1. **On-Demand Mobile HUD Inspection**: Clicking the interactive phone frame in the Wallpaper panel dispatches `WALLPAPER_GET` over the `nexus-control` DataChannel to load the child's active wallpapers without cloud storage.
2. **Multi-Screen Differentiation**: Distinguishes between Home Screen (`FLAG_SYSTEM`) and Lock Screen (`FLAG_LOCK`). If the child device uses unified wallpapers (`isSame: true`), all targets reflect the shared image; if distinct (`isSame: false`), selecting Home Screen or Lock Screen renders the corresponding image.
3. **Live Wallpaper Change Streaming**: Clicking the footer Play button starts continuous surveillance (`WALLPAPER_ON`). When active, Android's `OnColorsChangedListener` monitors changes; any wallpaper updates on the phone are automatically sliced and streamed to the desktop in real-time.
4. **Push Wallpaper to Device**: Allows the parent to select a local image ($\le 10\text{MB}$), slices the payload into 16KB DataChannel chunks, and remotely applies it on the phone via `WallpaperManager.setBitmap()`.
5. **Vibrate & Flash Hardware Triggers**: Parents trigger single-tap vibration patterns (`short`, `double`, `pulse`, `sos`), camera torch modes (`on`, `off`, `strobe`, `beacon`), or combined triggers (`alert`, `sos`, `pulse`) with real-time execution feedback.
6. **No-Monitoring Footer for Vibrate & Flash**: The Vibrate & Flash panel is an instantaneous hardware actuation tool, not a continuous stream. The bottom Play/Pause button is hidden, displaying a clean status indication instead.
7. **Peer Disconnection Fail-Safe**: If WebRTC disconnects, active vibration waveforms and flashlight jobs immediately cancel, and desktop states revert to `IDLE`.

---

## 1. DataChannel Protocol Specification

All transmissions occur over the bidirectional `nexus-control` WebRTC DataChannel (`ordered: true`).

### Protocol Matrix

| Direction | Command / Payload | Description | Expected Action / UI Reaction |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `WALLPAPER_GET` | Requests one-shot snapshot of child's active wallpapers. | Child queries `WallpaperManager` and dispatches `WALLPAPER_CHUNK` packets. |
| **Parent → Child** | `WALLPAPER_ON` | Starts live wallpaper monitoring on child device. | Child replies `WALLPAPER_STARTED`, followed by initial `WALLPAPER_CHUNK` snapshot. |
| **Parent → Child** | `WALLPAPER_OFF` | Stops live wallpaper listener on child. | Child replies `WALLPAPER_STOPPED`. |
| **Child → Parent** | `WALLPAPER_STARTED` | Confirms monitoring is active. | Footer Play button transitions to `pause.svg` ("Live Monitoring"). |
| **Child → Parent** | `WALLPAPER_STOPPED` | Confirms monitoring halted. | Footer Pause button transitions to `play.svg` ("Monitoring Paused"). |
| **Child → Parent** | `WALLPAPER_ERROR: <reason>` | Reports error on child device. | Desktop reverts to IDLE and displays error toast. |
| **Child → Parent** | `{"type":"WALLPAPER_CHUNK", ...}` | Sliced 16KB JSON chunk of Base64 JPEG wallpaper. | Desktop reassembles chunks and renders inside the Mobile HUD frame. |
| **Parent → Child** | `{"type":"PUSH_WALLPAPER_START", ...}` | Header initializing remote wallpaper upload. | Child resets buffer and prepares memory. |
| **Parent → Child** | `{"type":"PUSH_WALLPAPER_CHUNK", ...}` | 16KB chunk slice of pushed wallpaper. | Child accumulates buffer. Desktop updates upload progress percentage. |
| **Child → Parent** | `WALLPAPER_SET_SUCCESS` | Confirms wallpaper was decoded and applied to device. | Desktop displays success toast and renders new wallpaper in HUD. |
| **Child → Parent** | `WALLPAPER_SET_ERROR: <reason>` | Reports error applying wallpaper. | Desktop restores button and displays error toast. |
| **Parent → Child** | `VIBRATE:<pattern>` | Triggers vibration pattern (`short`, `double`, `pulse`, `sos`). | Child activates vibrator; replies `VIBRATE_SUCCESS` or `VIBRATE_ERROR`. |
| **Parent → Child** | `FLASH:<action>` | Triggers flashlight mode (`on`, `off`, `strobe`, `beacon`). | Child toggles torch; replies `FLASH_SUCCESS` or `FLASH_ERROR`. |
| **Parent → Child** | `VIBRATE_FLASH:<pattern>` | Triggers combined vibration and flash (`alert`, `sos`, `pulse`). | Child executes both; replies `VIBRATE_FLASH_SUCCESS` or error. |
| **Child → Parent** | `VIBRATE_SUCCESS: <pattern>` | Confirms vibration executed on child hardware. | Desktop displays confirmation in panel status banner and toast. |
| **Child → Parent** | `FLASH_SUCCESS: <action>` | Confirms flashlight action executed on child. | Desktop displays confirmation in panel status banner and toast. |
| **Child → Parent** | `VIBRATE_FLASH_SUCCESS: <pattern>` | Confirms combined execution. | Desktop displays confirmation in panel status banner and toast. |

---

## 2. Sequence Diagram

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. Click Mobile HUD -> Send "WALLPAPER_GET" (DataChannel) --------->|
      |   (HUD shows "Loading Wallpaper..." spinner)                          |
      |                                                                       |-- 2. Query WallpaperManager
      |                                                                       |-- 3. Compress & slice Base64
      |<-- 4. Send "WALLPAPER_CHUNK" [0..N] (DataChannel) --------------------|
      |-- 5. Reassemble chunks & render inside Mobile HUD                     |
      |                                                                       |
      |-- 6. Click Play -> Send "WALLPAPER_ON" (DataChannel) ---------------->|
      |   (Footer shows loading.svg spinner)                                  |
      |                                                                       |-- 7. Register OnColorsChangedListener
      |<-- 8. Receive "WALLPAPER_STARTED" ------------------------------------|
      |-- 9. Footer flips to pause.svg ("Live Monitoring")                    |
      |                                                                       |
      |   [Child changes wallpaper on Android device]                         |
      |                                                                       |-- 10. Listener triggers on phone
      |<-- 11. Send updated "WALLPAPER_CHUNK" [0..N] (DataChannel) -----------|
      |-- 12. HUD automatically auto-updates in real time!                    |
      |                                                                       |
      |-- 13. Click "Push Wallpaper" -> Select image file                    |
      |-- 14. Send "PUSH_WALLPAPER_START" (DataChannel) --------------------->|
      |-- 15. Send "PUSH_WALLPAPER_CHUNK" [0..N] (DataChannel) -------------->|
      |   (Button displays live progress: "Pushing... 45%")                   |
      |                                                                       |-- 16. Reassemble & setBitmap()
      |<-- 17. Receive "WALLPAPER_SET_SUCCESS" -------------------------------|
      |-- 18. Toast "Wallpaper applied to child device" & update preview      |
      |                                                                       |
      |-- 19. Click "Double Tap" Vibrate -> Send "VIBRATE:double" ----------->|
      |                                                                       |-- 20. Vibrator.vibrate(waveform)
      |<-- 21. Receive "VIBRATE_SUCCESS: double" -----------------------------|
      |-- 22. Status banner updates: "Vibration 'double' executed on child"   |
```

---

## 3. Engineering Safeguards & Standards

1. **SCTP Buffer Protection**: Large raw images are never sent in a single monolithic packet. Desktop and Child both enforce strict 16KB chunk slicing.
2. **Zero Battery Leakage**: Disconnecting from WebRTC automatically unregisters `OnColorsChangedListener` and halts any running flashlight strobe coroutine loops.
3. **No-Emoji Surveillance Design**: Adheres to the strict monochrome aesthetic; Lucide SVGs are used on Desktop, and native Vector Drawables on Android.
4. **Memory Hygiene**: Temporary bitmaps are aggressively recycled on Android, preventing OutOfMemory errors during repeated wallpaper fetches or uploads.

---

## 4. Nexus Parent Android Implementation (`Nexus Parent/android`)

- **Vibrate & Flash (`ui/monitor/vibrateflash/VibrateFlashPanel.kt`, `MonitorCoordinator.kt`)**:
  - Hides the footer Play/Pause dock (`showPlayPauseDock = false`) and dispatches exact typed wire triggers (`VIBRATE:<short|double|pulse|sos>`, `FLASH:<on|off|strobe|beacon>`, `VIBRATE_FLASH:<alert|sos|pulse>`), updating the inline status indicator banner and tactical toast upon `*_SUCCESS:` / `*_ERROR:` acknowledgements.
- **Wallpaper Surveillance & Push (`ui/monitor/wallpaper/WallpaperPanel.kt`, `MainActivity.kt`, `MonitorImageHelpers.kt`)**:
  - Tapping the phone mockup dispatches `WALLPAPER_GET` (15s timeout), while Play/Pause toggles continuous `WALLPAPER_ON` / `WALLPAPER_OFF` monitoring (20s timeout with `SpinningLoaderIcon`).
  - Reassembles incoming `WALLPAPER_CHUNK` packets into Home and Lock `ByteArray` bitmaps (sharing when `isSame == true`) and renders the decoded image inside the phone frame via `ImageBitmap`.
  - `"Push Wallpaper to Device"` launches Android's `ActivityResultContracts.PickVisualMedia(ImageOnly)` System Photo Picker (supporting PNG/JPEG/WebP up to 10 MB with zero extra storage permissions), encodes with `Base64.NO_WRAP`, and streams `PUSH_WALLPAPER_START` + `16,384`-character `PUSH_WALLPAPER_CHUNK` frames (exact multiple of 4 for Child's per-chunk Base64 decoder) with a live progress bar until `WALLPAPER_SET_SUCCESS` (30s fallback timeout).
