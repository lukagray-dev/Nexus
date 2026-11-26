# Surveillance & DVR Settings Implementation Specification

## Overview

This document specifies the architecture, cross-device WebRTC DataChannel protocol extensions, Rust native WebView2 GPU configuration, shared rolling DVR engine, and verification tests for the **Surveillance & Rolling DVR** settings category in **Nexus Parent** (`Nexus Parent/desktop`) and **Nexus Child** (`Nexus Child`).

The **Surveillance & Rolling DVR** tab governs five settings across two groups:
1. **Live Streaming Fidelity**:
   - **Screen Mirror Quality** (`screenQuality`: `'1080p' | '720p' | '540p'`)
   - **Preferred Default Camera Lens** (`preferredCameraLens`: `'rear' | 'front'`)
   - **GPU Video Hardware Acceleration** (`hardwareAcceleration`: `boolean`)
2. **Rolling DVR Timeline Buffer**:
   - **Rolling DVR Memory Retention** (`dvrBufferDuration`: `'15m' | '30m' | '60m'`)
   - **DVR Clip Export Container** (`dvrClipFormat`: `'mp4' | 'webm'`)

---

## 1. Schema & Persistence

| Key | Type | Default | Persisted In | Applied Where |
| :--- | :--- | :--- | :--- | :--- |
| `screenQuality` | `'1080p' \| '720p' \| '540p'` | `'1080p'` | `localStorage` | Desktop `screen.ts` + Android `ScreenController` |
| `preferredCameraLens` | `'rear' \| 'front'` | `'rear'` | `localStorage` | Desktop `camera.ts` + Android `CameraCommandHandler` |
| `hardwareAcceleration` | `boolean` | `true` | `localStorage` + Rust `window-settings.json` | Rust WebView2 launch args + WebRTC codec preferences |
| `dvrBufferDuration` | `'15m' \| '30m' \| '60m'` | `'30m'` | `localStorage` | `RollingDvrManager` (`CameraDvrManager` & `ScreenDvrManager`) |
| `dvrClipFormat` | `'mp4' \| 'webm'` | `'mp4'` | `localStorage` | `RollingDvrManager` MIME selection + **Save Clip** export |

---

## 2. Detailed Per-Setting Specifications

### 2.1 Screen Mirror Quality (`screenQuality`)
- **Desktop**:
  - `screen.ts` sends `SCREEN_ON:<quality>` (`SCREEN_ON:1080p`, `SCREEN_ON:720p`, `SCREEN_ON:540p`) when starting Screen Feed.
  - `initSurveillanceDvrRuntime()` (`surveillance-dvr.ts`) sends `SCREEN_QUALITY:<quality>` over the open DataChannel whenever `screenQuality` changes while connected.
- **Android Child (`ScreenCommandHandler.kt` & `ScreenController.kt`)**:
  - `ScreenQualityPreset`: `FULL_HD_1080P` (`1920` max dim @ `30fps`), `HD_720P` (`1280` max dim @ `30fps`), `SD_540P` (`960` max dim @ `24fps`).
  - `ScreenController.computeScaledDimensions()` preserves the device's aspect ratio with even pixel dimensions and invokes `ScreenCapturerAndroid.changeCaptureFormat(w, h, fps)` on live `SCREEN_QUALITY:` updates.
  - Parameterless `SCREEN_ON` remains supported and defaults to `1080p`.

### 2.2 Preferred Default Camera Lens (`preferredCameraLens`)
- **Desktop**:
  - `camera.ts` initializes `currentLens` and `#camera-lens-name` from `settingsState.getValue('preferredCameraLens')` (`Rear` or `Front`).
  - Clicking Play sends `CAMERA_ON:REAR` or `CAMERA_ON:FRONT`.
  - Changing the setting while Camera Feed is `IDLE` updates the lens button immediately via `nexus-camera-preferred-lens-changed`; peer disconnection also resets to the preferred default lens.
- **Android Child (`CameraCommandHandler.kt`)**:
  - Parses `CAMERA_ON:FRONT` / `CAMERA_ON:REAR` (with plain `CAMERA_ON` defaulting to `CameraLens.REAR`).

### 2.3 GPU Video Hardware Acceleration (`hardwareAcceleration`)
- **Rust Backend (`src-tauri/src/tray.rs` & `lib.rs`)**:
  - Stored in `WindowTraySettings.hardware_acceleration` inside `window-settings.json` (defaults to `true`).
  - At the start of `run()` in `lib.rs` (before WebView2 initializes), `tray::configure_gpu_acceleration_before_launch()` reads `window-settings.json` and appends `--disable-gpu --disable-gpu-compositing` to `WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS` when `false`.
  - `WindowLifecycleState` captures `launch_hardware_acceleration` at boot so the Settings UI (`hardware-acceleration.ts`) displays an inline `"Restart required"` hint and **Restart Now** button (`restart_application` Tauri command) whenever the saved value differs from the active WebView2 process.
- **WebRTC Codec Preferences (`src/ts/webrtc/webrtc-manager.ts`)**:
  - `applyVideoCodecPreference()` orders receiving video transceiver codecs (`H264` first when `hardwareAcceleration` is `true`, `VP8`/`VP9` first when `false`).

### 2.4 Rolling DVR Memory Retention (`dvrBufferDuration`) & Clip Export Container (`dvrClipFormat`)
- **Shared Engine (`src/ts/shared/rolling-dvr.ts`)**:
  - Both `CameraDvrManager` and `ScreenDvrManager` extend `RollingDvrManager`.
  - `resolveDvrBufferMs()` maps `'15m' | '30m' | '60m'` to `15`, `30`, or `60` minutes and subscribes to `settingsState.onValuesChange` so reducing retention live prunes older FIFO chunks immediately.
  - `resolveDvrMimeType()` prefers `video/mp4` codecs when `dvrClipFormat === 'mp4'` and falls back cleanly to `video/webm` if the host WebView2 runtime lacks MP4 `MediaRecorder` support.
  - **Save Clip Controls**: Both `camera.ts` (`#btn-camera-save-clip`) and `screen.ts` (`#btn-screen-save-clip`) expose a **Save Clip** (`download.svg`) button that exports the compiled DVR `Blob` through `saveBlobWithUniversalSettings()` (`download-manager.ts`), honoring **Downloads & Media Save Directory** and **Ask Every Time Before Downloading**.

---

## 3. Verification

```powershell
# Desktop TypeScript build + Node.js unit & integration tests
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test

# Rust backend unit tests
Set-Location "d:\Nexus\Nexus Parent\desktop\src-tauri"
cargo test

# Android Child JVM unit tests
Set-Location "d:\Nexus\Nexus Child"
.\gradlew.bat testDebugUnitTest
```

---

## 4. Parent Android Parity (`Nexus Parent/android`)

- **1:1 Modular Package (`ui/settings/maincontent/surveillancedvr/`)**:
  - `SurveillanceDvrSection.kt`
  - `ScreenQualitySetting.kt` (`ScreenQuality`: `P1080`, `P720`, `P540`), `CameraLensSetting.kt` (`CameraLens`: `REAR`, `FRONT`), `DvrDurationSetting.kt` (`DvrDuration`: `M15`, `M30`, `M60`), `ClipFormatSetting.kt` (`ClipFormat`: `MP4`, `WEBM`), and `HardwareAccelerationSetting.kt`.
- **Hardware Video Codec Priority**: `WebRtcManager.kt` invokes `sortVideoCodecsForAcceleration()` (`H264` > `VP9` > `VP8` when hardware acceleration is enabled; `VP8` > `VP9` > `H264` when disabled) paired with `EglBase` and `NexusTextureVideoView` (`TextureView` + `EglRenderer`). Note: individual `CAMERA_ON` / `SCREEN_ON` DataChannel command wiring is staged for the feature-wiring phase.
- **Verification**: `WebRtcExtensionsTest.kt` verifies hardware vs software video codec ordering.
