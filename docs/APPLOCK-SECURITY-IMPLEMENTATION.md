# App Lock Security & Remote Override Implementation

## Overview
This document specifies the architecture, protocols, data schemas, and lifecycle flows for the **App Lock Security and Remote Override** system implemented across **Nexus Child** (Android) and **Nexus Parent** (Desktop Tauri / TypeScript).

---

## Architecture & System Flow

```
+---------------------------------------------------------------------------------------+
|                                    NEXUS CHILD                                        |
|                                                                                       |
|  [App Launch / onResume] ---> AppLockManager.isAppLockEnabled()                       |
|                                     |                                                 |
|                      +--------------+---------------+                                 |
|                      |                              |                                 |
|               (Lock Active)                  (Lock Inactive)                          |
|                      v                              v                                 |
|             [AppLockActivity]                 [MainActivity]                          |
|                      |                              ^                                 |
|             (6-Digit Entered)                       |                                 |
|                      |                              |                                 |
|                      +---> [SelfieCapturer]         |                                 |
|                      |     (Camera2 Silent JPEG)    |                                 |
|                      |                              |                                 |
|                      +---> [AppLockManager]         |                                 |
|                            (SHA-256 Hash Match)     |                                 |
|                                     |               |                                 |
|                      +--------------+--------+      |                                 |
|                      |                       |      |                                 |
|                  (Correct)               (Wrong)    |                                 |
|                      |                       |      |                                 |
|                      +-----------------------+------+                                 |
|                      |                       |                                        |
|                      v                       v                                        |
|             [AppLockRepository]     [3rd Failure -> 60s Lockout]                      |
|             (Offline JSON Buffer)                                                     |
|                      |                                                                |
|                      v                                                                |
|             [WebRTC DataChannel] --(APPLOCK_NEW / SNAPSHOT)--> [NEXUS PARENT DESKTOP] |
+---------------------------------------------------------------------------------------+
```

---

## 1. Nexus Child Components

### `AppLockConstants` & `AppLockModel`
- **Location**: `com/nexus/child/components/applock/AppLockModel.kt`
- **PIN Rule**: Strictly 6-digit numeric PIN (`PIN_LENGTH = 6`).
- **Security Thresholds**:
  - `MAX_FAILED_ATTEMPTS = 3`: Reaching 3 consecutive failed attempts initiates hardware lockout.
  - `LOCKOUT_DURATION_MS = 60_000L`: 1-minute (60 seconds) lockout with real-time countdown timer.

### `AppLockManager`
- **Location**: `com/nexus/child/components/applock/AppLockManager.kt`
- **PIN Security**: Plaintext PINs are never stored; stored as SHA-256 hashes in private preferences (`nexus_app_lock_prefs`).
- **Lockout Persistence**: `lockout_end_time` is stored as epoch milliseconds on disk, ensuring force-killing or restarting the device cannot bypass the active lockout window.
- **Session Tracking**: Tracks `isSessionUnlocked()` in memory so authenticated users are not interrupted when switching between internal activities.

### `SelfieCapturer`
- **Location**: `com/nexus/child/components/applock/SelfieCapturer.kt`
- **Silent Hardware Capture**: Uses Android Camera2 API (`CameraManager`) and an offscreen `ImageReader` (640x480 resolution, JPEG quality 65) attached to a background `HandlerThread` (`NexusSelfieThread`).
- **Invisible Execution**: Zero preview surfaces, shutter animations, or sounds.
- **Sensor Orientation Correction**: Discovers sensor orientation and applies necessary rotation and horizontal mirror transforms.
- **Graceful Fallback**: 2.5-second timeout safeguard ensures PIN verification is never blocked if hardware fails or camera permission is missing.

### `AppLockRepository`
- **Location**: `com/nexus/child/components/applock/AppLockRepository.kt`
- **Offline Buffering**: Stores attempt records in `applock/records.json` and caches JPEG images in `applock/selfies/{id}.jpg`.
- **FIFO Retention**: Automatically caps stored logs to 100 entries, deleting obsolete image files as new ones arrive.

### `AppLockController` & `AppLockCommandHandler`
- **Location**: `com/nexus/child/components/applock/AppLockController.kt`, `AppLockCommandHandler.kt`
- **Integrated Service**: Registered within `NexusBackgroundService`.
- **Live Dispatch**: When monitoring is active, unlock attempts are immediately dispatched via `APPLOCK_NEW`.
- **Chunked History Flush**: On `APPLOCK_ON`, flushes buffered records in chunks of 5 to respect WebRTC frame limits.

---

## 2. Nexus Parent Desktop Components

### Protocol Verbs & Packet Schemas
- **Location**: `src/ts/right-sidebar/types.ts`
- **Commands**:
  - `CMD_APPLOCK_ON = 'APPLOCK_ON'`: Start live monitoring and trigger history sync.
  - `CMD_APPLOCK_OFF = 'APPLOCK_OFF'`: Stop live monitoring.
  - `CMD_APPLOCK_TOGGLE = 'APPLOCK_TOGGLE'`: Remote enable/disable override.
  - `CMD_APPLOCK_SET_PIN = 'APPLOCK_SET_PIN'`: Remote 6-digit PIN configuration.
  - `CMD_APPLOCK_CLEAR = 'APPLOCK_CLEAR'`: Wipe attempt logs on device.

### Right Sidebar Panel UI
- **Location**: `src/ts/right-sidebar/applock/applock.ts`
- **Subbar Controls**: Master lock toggle, remote "Change PIN" modal, "Clear" action.
- **Stats Metrics**: Total attempts, successful entries, failed entries, lockouts.
- **Attempt Feed**: Cards displaying status pill (UNLOCKED / WRONG PIN / LOCKED OUT), timestamp, failed attempt count, and selfie thumbnail.
- **Inspection Modal**: Clicking any selfie opens a high-resolution inspection modal with full attempt metadata.

---

## 3. Nexus Parent Android Components (`Nexus Parent/android`)

- **Protocol & Coordinator (`data/monitor/MonitorCoordinator.kt`, `MonitorPayloadParser.kt`)**:
  - Handles `APPLOCK_ON` / `APPLOCK_OFF` (20s start / 10s stop timeout) with Desktop-exact `SpinningLoaderIcon` while awaiting `APPLOCK_STARTED` / `APPLOCK_STOPPED`.
  - Dispatches `APPLOCK_TOGGLE`, `APPLOCK_SET_PIN`, and `APPLOCK_CLEAR`; merges `APPLOCK_STATUS` (`enabled`, `isLockedOut`), chunked `APPLOCK_SNAPSHOT` (chunks of 5 with Base64 `selfie`), `APPLOCK_NEW`, and `APPLOCK_PIN_CHANGED`.
- **UI & Media Decoding (`ui/monitor/applock/AppLockPanel.kt`, `ui/monitor/MonitorImageHelpers.kt`)**:
  - Renders `ACTIVE` / `DISABLED` status + `LOCKED OUT` badge, `Change PIN` modal (dual 6-digit PIN + confirmation fields with validation), `Clear` action, 4 stat tiles, search bar, `56dp` Base64-decoded intruder thumbnail on each attempt card, and full-screen selfie + timestamp/result/lockout inspection modal.

---

## 4. Verification & Test Suite

### Android JVM Unit Tests
- `AppLockManagerTest.kt`: 6-digit validation, SHA-256 verification, 3-attempt failure progression, 60s lockout, and session state.
- `AppLockCommandHandlerTest.kt`: WebRTC command routing, remote toggles, and remote PIN updates.
- `AppLockRepositoryTest.kt`: Offline persistence, image file caching, and clearing.
- **Command**:
  ```powershell
  Set-Location "d:\Nexus\Nexus Child"
  .\gradlew.bat test
  ```
- **Result**: `BUILD SUCCESSFUL`, 21 test suites passed, 0 errors, 0 failures.

### Desktop Node.js Unit Tests
- `applock-protocol.test.js`: Contract constants, state machine transitions, snapshot chunking, record prepending, and disconnect safety.
- **Command**:
  ```powershell
  Set-Location "d:\Nexus\Nexus Parent\desktop"
  npm test
  ```
- **Result**: 14 test suites, 92 tests passed, 0 failures.
