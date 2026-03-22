<a id="top"></a>

<p align="center">
  <img src="../assets/nexus_child.png" alt="Nexus Child Logo" width="170" />
</p>

<h1 align="center">Nexus Child (Android)</h1>

<p align="center">
  Child-side Android agent for the Nexus parental-control platform
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%209%2B-34A853?style=for-the-badge&logo=android&logoColor=white" alt="Android 9+" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/WebRTC-Real--time-1F7AE0?style=for-the-badge" alt="WebRTC" />
  <img src="https://img.shields.io/badge/Signaling-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
</p>

<p align="center">
  <a href="#overview"><b>Overview</b></a> •
  <a href="#architecture"><b>Architecture</b></a> •
  <a href="#feature-map"><b>Feature Map</b></a> •
  <a href="#setup"><b>Setup</b></a> •
  <a href="#build--run"><b>Build</b></a>
</p>

---

## Overview

Nexus Child is the Android-side agent that:

- maintains a persistent connection to the parent client,
- executes remote commands received over WebRTC data channel,
- streams media/telemetry back to parent dashboards,
- handles stealth, resilience, and permission-dependent operations.

The module is implemented in Kotlin (`applicationId: nexus.android.child`) with `minSdk 28`, `targetSdk 36`, Firebase signaling, and native WebRTC.

## Architecture

### Core Runtime

- `MainActivity`: setup UI, permission flow, app-lock/fake-UI gates, Gmail connect state, and local device ID presentation.
- `BackgroundService`: long-running foreground service that owns signaling, peer connection, media controllers, and command execution.
- `CommandHandlerImpl`: maps command strings/JSON payloads to concrete device operations.

### Connectivity and Signaling

- Signaling transport: Firebase Realtime Database (`signaling/{deviceId}` style workflow).
- Realtime transport: WebRTC media tracks + data channel.
- Renegotiation flow: supported for dynamic track add/remove (for example microphone/screen recording toggles).

### Persistence and Survivability

- Foreground service (`START_STICKY`) for long-lived operation.
- Boot/package replace receivers restart service paths.
- `PersistentJobService` schedules recurring health restart support.
- Wake lock and restart-on-task-removed logic improve runtime continuity.

## Feature Map

| Area | Commands | Notes |
| --- | --- | --- |
| Camera | `CAMERA_ON`, `CAMERA_OFF`, `CAMERA_SWITCH` | Live camera stream and camera switching. |
| Microphone stream | `MIC_ON`, `MIC_OFF` | Adds/removes audio tracks with renegotiation support. |
| Screen capture | `SCREEN_RECORDING_ON`, `SCREEN_RECORDING_OFF` | Uses MediaProjection permission flow and sends confirmation events. |
| Location | `LOCATE_CHILD`, `LOCATE_CHILD_STOP` | Location start/stop with status events. |
| SMS | `SMS_ON`, `SMS_OFF` | Command-driven SMS sharing lifecycle. |
| Call logs | `CALLLOG_ON`, `CALLLOG_OFF` | Call log collection stream lifecycle. |
| Notifications | `NOTIFICATION_ON`, `NOTIFICATION_OFF` | Notification listener-driven feed. |
| Chat monitoring | `CHAT_ON`, `CHAT_OFF` | App chat extraction pipeline via child chat components. |
| Keyboard monitoring | `KEYBOARD_ON`, `KEYBOARD_OFF` | Keyboard event capture pipeline. |
| File browsing/transfer | `LIST_FILES`, `GET_FILE`, `GET_FILE_PROGRESSIVE`, `STOP_STREAM` | Metadata listing + chunked/progressive transfer. |
| Digital wellbeing | `GET_WELLBEING`, `STOP_WELLBEING` | App usage/wellbeing snapshot delivery. |
| Parent voice control | `PARENT_AUDIO_UNMUTE`, `PARENT_AUDIO_MUTE` | Child-side audio behavior reacts to parent talkback state. |
| Wallpaper control | `SET_WALLPAPER` | Receives binary wallpaper payload and applies device wallpaper. |
| Haptics/flash | `VIBRATE`, `FLASH`, `VIBRATE_FLASH` | Pattern-based alert actions. |
| App lock control | `SET_APP_LOCK_PIN`, `SET_APP_LOCK_ENABLED`, `GET_APP_LOCK_STATUS` | Remote app-lock policy and status channel. |
| Stealth operations | `STEALTH_ON`, `STEALTH_OFF` | Launcher visibility and stealth mode toggles. |

### Additional Agent Capabilities

- App icon/name camouflage via multiple activity aliases.
- Fake UI mode support tied to selected icon profile.
- Gmail connection and sync trigger wiring in child activity/service.
- Notification listener and accessibility service integration.

## Module Layout

```text
android/
|-- app/src/main/java/nexus/android/child/
|   |-- commands/        # Command parsing + handlers
|   |-- components/      # Camera, mic, screen, chat, files, etc.
|   |-- service/         # Boot + persistence services
|   |-- signaling/       # Firebase signaling client/state
|   |-- webrtc/          # Peer and observer layer
|   `-- MainActivity.kt
|-- app/src/main/AndroidManifest.xml
|-- app/build.gradle
`-- README.md
```

## Setup

### Prerequisites

- Android Studio
- JDK 11
- Android SDK (compile/target 36)

### Configuration

1. Place Firebase Android config at:
   - `app/google-services.json`
2. Verify Firebase DB URL in:
   - `app/src/main/java/nexus/android/child/configuration/AppConfig.kt`

## Build & Run

```bash
cd "Nexus Child/android"
./gradlew assembleDebug
./gradlew installDebug
```

## Runtime Notes

- The app requests extensive permissions because features span media capture, notifications, storage, communication logs, and accessibility-dependent monitoring.
- Disable aggressive battery optimization for stable background behavior on OEM ROMs.

> [!IMPORTANT]
> Use this software only on devices you lawfully own/manage and only in contexts permitted by applicable laws and consent requirements.

---

<p align="center">
  Nexus Child • Android agent runtime
</p>

<p align="center">
  <a href="#top">Back to top</a>
</p>
