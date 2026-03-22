<a id="top"></a>

<p align="center">
  <img src="../../assets/nexus_parent.png" alt="Nexus Parent Logo" width="170" />
</p>

<h1 align="center">Nexus Parent (Android)</h1>

<p align="center">
  Android parent dashboard for monitoring and controlling connected child devices
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B-34A853?style=for-the-badge&logo=android&logoColor=white" alt="Android 10+" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Auth-Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white" alt="Supabase" />
  <img src="https://img.shields.io/badge/Signaling-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
</p>

<p align="center">
  <a href="#overview"><b>Overview</b></a> •
  <a href="#architecture"><b>Architecture</b></a> •
  <a href="#feature-map-23-panels"><b>Features</b></a> •
  <a href="#setup"><b>Setup</b></a> •
  <a href="#automation-scripts"><b>Scripts</b></a> •
  <a href="#build--run"><b>Build</b></a>
</p>

---

## Overview

Nexus Parent Android is the mobile dashboard that:

- authenticates parent users,
- connects to child devices via 12-digit child ID,
- opens modular feature panels for command/control and live data,
- consumes data-channel events from the child agent.

Current build settings: `applicationId nexus.android.parent`, `minSdk 29`, `targetSdk 36`, JVM target 11.

## Architecture

### Activity Flow

- `SplashActivity`: app entry.
- `auth/*`: sign-in, sign-up, and user-info flows (Supabase-backed).
- `connection/ConnectionActivity`: child ID connect gate.
- `MainActivity`: dashboard shell with drawer + panels.
- `ai/AIChatActivity`: AI chat UI route.

### Core Managers

- `DrawerManager`: sidebar interactions, feature routing, stealth toggle UX.
- `PanelManager`: panel lifecycle (create/focus/remove, no duplicates).
- `FeatureFactory`: singleton feature instances per feature type.
- `ConnectionManager`: WebRTC orchestration, Firebase signaling, data-channel event fan-out.

### Realtime Model

- Signaling: Firebase Realtime Database.
- Session transport: WebRTC (media tracks + data channel).
- Commands: feature modules send structured command payloads through `ConnectionManager.sendCommand(...)`.
- Responses/events: features observe connection flows and data-channel messages.

## Feature Map (23 Panels)

### Core Controls (14)

- `CAMERA`: live camera control (`CAMERA_ON/OFF/SWITCH`).
- `SCREEN`: screen recording control (`SCREEN_RECORDING_ON/OFF`).
- `LOCATION`: remote location control (`LOCATE_CHILD/LOCATE_CHILD_STOP`).
- `SMS`: SMS monitoring (`SMS_ON/OFF`).
- `CALLLOG`: call log monitoring (`CALLLOG_ON/OFF`).
- `MIC`: child mic monitoring + parent talkback (`MIC_ON/OFF`, `PARENT_AUDIO_UNMUTE/MUTE`).
- `NOTIFICATIONS`: notification feed control (`NOTIFICATION_ON/OFF`).
- `CHATS`: chat stream control (`CHAT_ON/OFF`).
- `GMAIL`, `FILES`, `WELLBEING`, `APPLOCK`, `WALLPAPER`, `VIBRATEFLASH`.

### Social Panels (6)

- `WHATSAPP`, `FACEBOOK`, `INSTAGRAM`, `MESSENGER`, `SNAPCHAT`, `TELEGRAM`.

### AI Panels (3)

- `CHATGPT`, `CLAUDE`, `GROK`.

## Module Layout

```text
mobile/android/
|-- app/src/main/java/nexus/android/parent/
|   |-- auth/            # Supabase auth flow
|   |-- connection/      # Child ID connect flow
|   |-- managers/        # Drawer/Panel/UI managers
|   |-- features/        # 23 feature modules
|   |-- webrtc/          # Parent peer + signaling managers
|   `-- MainActivity.kt
|-- app/src/main/res/    # XML layouts/resources
|-- app/build.gradle
`-- README.md
```

## Setup

### Prerequisites

- Android Studio
- JDK 11
- Android SDK (compile/target 36)

### Configuration

1. Add Firebase Android config:
   - `app/google-services.json`
2. Verify Firebase DB URL in:
   - `app/src/main/java/nexus/android/parent/configuration/AppConfig.kt`
3. Verify Supabase config in:
   - `app/src/main/java/nexus/android/parent/configuration/SupabaseConfig.kt`

## Automation Scripts

Project-level Windows batch scripts are available in:

- `D:\Nexus\scripts`

Android scripts:

- `setup-java21-local.bat` -> installs local JDK 21 to `D:\Nexus\.jdks\jdk-21`
- `setup-android-sdk-local.bat` -> installs local Android SDK to `D:\Nexus\.android-sdk`
- `setup-parent-android-toolchain.bat` -> runs both setup scripts + writes `local.properties`
- `parent-android-test.bat` -> runs `gradlew testDebugUnitTest`
- `parent-android-build-debug.bat` -> runs `gradlew assembleDebug`
- `parent-android-build-release.bat` -> runs `gradlew assembleRelease`

Recommended first-time setup:

```bat
D:\Nexus\scripts\setup-parent-android-toolchain.bat
```

## Build & Run

```bash
cd "Nexus Parent/mobile/android"
./gradlew assembleDebug
./gradlew installDebug
```

## Troubleshooting

- Connection timeout: verify child ID format (12 digits), child online state, and Firebase config.
- No media stream: check child-side permissions and whether the feature command is acknowledged.
- Auth issues: verify Supabase URL/key and network connectivity.

> [!IMPORTANT]
> Use only in lawful parental-control contexts with proper ownership, disclosure, and consent as required by applicable law.

---

<p align="center">
  Nexus Parent Android • Mobile control surface
</p>

<p align="center">
  <a href="#top">Back to top</a>
</p>
