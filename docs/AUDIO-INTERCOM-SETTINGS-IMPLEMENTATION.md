# Audio & Intercom Settings Implementation Specification

## Overview

This document specifies the architecture, hardware device enumeration, output sink routing, WebRTC microphone constraint management, bidirectional voice preset synchronization, and verification tests for the **Audio & Intercom** settings category in **Nexus Parent** (`Nexus Parent/desktop`).

The **Audio & Intercom** tab governs two functional groups:
1. **Voice Changer Engine**:
   - **Default Intercom Voice** (`defaultVoicePreset`) — implemented with bidirectional synchronization.
   - **Loudspeaker Target Volume** (`loudspeakerVolume`) — currently local-only persistence; remote Android volume override integration is deferred.
2. **Hardware Routing & DSP**:
   - **Microphone Input Device** (`inputDeviceId`) — real `navigator.mediaDevices` `audioinput` enumeration, permission unlock, and hot-swap capture.
   - **Monitoring Output Speaker** (`outputDeviceId`) — real `audiooutput` enumeration and live `setSinkId()` routing across both child microphone monitoring and screen internal audio.
   - **Hardware Noise Suppression & AEC** (`noiseSuppression`) — live `echoCancellation`, `noiseSuppression`, and `autoGainControl` constraint binding.

> Note: The former **Connection & WebRTC** settings tab was completely removed from the Settings sidebar, router, schema (`SettingsTabId`, `SettingsValues`), and `localStorage` state (legacy keys are stripped automatically on load).

---

## 1. Settings Navigation Order (6 Categories)

Defined in `Nexus Parent/desktop/src/ts/settings/sidebar/types.ts` (`SETTINGS_TABS`):

| Order | Tab ID | Sidebar Label | Default Active |
| :---: | :--- | :--- | :---: |
| 1 | `general` | **General & System** | Yes |
| 2 | `audio` | **Audio & Intercom** | - |
| 3 | `surveillance` | **Surveillance & DVR** | - |
| 4 | `security` | **Security & App Lock** | - |
| 5 | `devices` | **Paired Devices** | - |
| 6 | `diagnostics` | **Diagnostics & Storage** | - |

---

## 2. Persistence & Runtime Synchronization

### Schema (`SettingsValues` — Audio & Intercom)

| Key | Type | Default | Persisted In | Status |
| :--- | :--- | :--- | :--- | :--- |
| `defaultVoicePreset` | `VoicePresetId` | `'original'` | `localStorage` (`nexus_parent_settings_v1` & `nexus_parent_intercom_voice`) | Implemented (Bidirectional) |
| `inputDeviceId` | `string` | `'default'` | `localStorage` | Implemented |
| `outputDeviceId` | `string` | `'default'` | `localStorage` | Implemented |
| `noiseSuppression` | `boolean` | `true` | `localStorage` | Implemented |
| `loudspeakerVolume` | `'max' \| 'high' \| 'normal'` | `'max'` | `localStorage` | Deferred (Local-only for now) |

### Runtime Lifecycle (`initAudioVoiceIntercomRuntime`)
Initialized once at application boot via `initSettingsMainContentRuntime()` (`src/ts/settings/main-content/main-content.ts`):
1. **Boot Reconciliation**: Syncs `settingsState.getValue('defaultVoicePreset')` into `saveVoicePresetId()` and `parentAudioController.setVoice()`.
2. **Voice Preset Sync**: Mutations to `defaultVoicePreset` (including `Reset Defaults`) update `VoiceChangerEngine`, persist `nexus_parent_intercom_voice`, and dispatch `nexus-intercom-voice-sync` so the floating Mic panel badge and dropdown stay synchronized.
3. **Audio Capture Reconfiguration**: Mutations to `inputDeviceId` or `noiseSuppression` invoke `parentAudioController.reconfigureAudioInput()`.
4. **Output Sink Broadcast**: Mutations to `outputDeviceId` dispatch `nexus-audio-output-changed` so active `<audio>` and `AudioContext` pipelines switch speakers without interrupting playback.

---

## 3. Detailed Per-Setting Specifications

### 3.1 Shared Hardware Service (`src/ts/main-content/mic/audio-device-service.ts`)
- **`enumerateAudioDevices()`**: Queries `navigator.mediaDevices.enumerateDevices()`, separates `audioinput` and `audiooutput` devices, strips empty placeholder IDs and duplicate raw `'default'` aliases, and sets `hasMaskedLabels = true` when WebView2 hides hardware names prior to microphone permission.
- **`requestAudioDevicePermission()`**: Briefly acquires `navigator.mediaDevices.getUserMedia({ audio: true })` and immediately stops every returned `MediaStreamTrack` so hardware names unlock without leaving the microphone open, then dispatches `nexus-audio-devices-refreshed`.
- **`subscribeAudioDeviceChanges()`**: Listens to `navigator.mediaDevices` `devicechange` events and `nexus-audio-devices-refreshed` for live plug/unplug updates.
- **`applyAudioOutputDevice(target, deviceId)`**: Maps `'default'` to `''` and invokes `target.setSinkId(sinkId)` on `HTMLAudioElement` or `AudioContext`. If `NotFoundError`, `OverconstrainedError`, or `AbortError` occurs, falls back automatically to `''` (System Default Output).

### 3.2 Default Intercom Voice (`voice-preset.ts`)
- Renders `#setting-voice-preset` populated from `VOICE_PRESETS`.
- Changing the dropdown updates `settingsState`, `VoiceChangerEngine`, and any open floating Microphone panel (`mic.ts`).
- Selecting a preset in the floating Microphone panel dropdown (`selectVoicePreset` in `mic.ts`) updates `settingsState.setValue('defaultVoicePreset', presetId)` in return, keeping Settings and the Mic overlay 100% consistent.

### 3.3 Microphone Input Device (`input-device.ts` + `parent-audio-controller.ts`)
- Enumerates real PC microphones silently on tab open.
- Shows the inline **Detect Devices** button (`#btn-detect-input-devices`) only when `hasMaskedLabels` is `true`, hiding it automatically once permissions unlock hardware names.
- Displays an `Unavailable Saved Microphone` option and amber hint if a saved `deviceId` is disconnected.
- In `ParentAudioController`:
  - Uses `buildAudioCaptureConstraints(inputDeviceId, noiseSuppression)` with `{ exact: deviceId }` for non-default devices.
  - `acquireMicStreamWithFallback()` retries with `'default'` and shows a toast if the saved microphone throws `OverconstrainedError` / `NotFoundError`.
  - `reconfigureAudioInput()` releases idle capture when muted, or hot-swaps the input stream into `VoiceChangerEngine.updateInputStream()` when actively speaking so live intercom continues without WebRTC renegotiation.

### 3.4 Monitoring Output Speaker (`output-device.ts` + `mic.ts` + `opus-audio-player.ts`)
- Enumerates real PC speakers and headphones (`audiooutput`), refreshing on `devicechange` and `nexus-audio-devices-refreshed`.
- Routes **both** desktop audio playback pipelines via `applyAudioOutputDevice()`:
  1. **Child Microphone Monitoring** (`#nexus-mic-audio-stream` `<audio>` element in `src/ts/main-content/mic/mic.ts`).
  2. **Screen Mirror Internal System Audio** (`AudioContext` destination in `src/ts/main-content/screen/opus-audio-player.ts`).
- Switching speakers while either stream is playing updates `setSinkId()` live.

### 3.5 Hardware Noise Suppression & AEC (`noise-suppression.ts` + `parent-audio-controller.ts`)
- Controls `echoCancellation`, `noiseSuppression`, and `autoGainControl` in `buildAudioCaptureConstraints()`.
- Toggling the switch reconfigures `ParentAudioController` immediately (resetting idle capture or hot-swapping active speech).

### 3.6 Loudspeaker Target Volume (`loudspeaker-volume.ts`)
- Kept in the UI as a local `settingsState` preference (`'max' | 'high' | 'normal'`). Remote Android DataChannel payload support is deferred for a future milestone.

---

## 4. Verification & Test Coverage

### TypeScript & Node.js Tests (`Nexus Parent/desktop`)
- **`test/settings.test.js`**: Verifies 6-tab registry without `'connection'`, removed `Connection & WebRTC` defaults, legacy key stripping on `localStorage` load, and 6-tab sidebar rendering.
- **`test/general-system-tray.test.js`**: Verifies modular `main-content` routing across all 6 settings categories.
- **`test/audio-intercom-settings.test.js`**: Verifies `enumerateAudioDevices`, `requestAudioDevicePermission`, `applyAudioOutputDevice` fallback, `input-device.ts` / `output-device.ts` / `voice-preset.ts` / `noise-suppression.ts` / `loudspeaker-volume.ts` DOM & state flows, `buildAudioCaptureConstraints`, `acquireMicStreamWithFallback`, `ParentAudioController.reconfigureAudioInput()`, `initAudioVoiceIntercomRuntime()`, and `OpusAudioPlayer` sink routing.

```powershell
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test
```
