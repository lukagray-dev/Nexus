# About Nexus Parent Settings Implementation Specification

## Overview

This document specifies the architecture, Rust system introspection IPC contracts, frontend modules, titlebar Help menu unification, and verification tests for the **About Nexus Parent** settings category (`Nexus Parent/desktop/src/ts/settings/main-content/about/`), which replaces the former dummy `diagnostics-storage` tab.

---

## 1. Settings Navigation & Brand Icon Integration

Defined in `Nexus Parent/desktop/src/ts/settings/sidebar/types.ts` (`SETTINGS_TABS`) and `sidebar.ts`:

| Order | Tab ID | Sidebar Label | Icon |
| :---: | :--- | :--- | :--- |
| 1 | `general` | **General & System** | `assets/icons/settings/general.svg` |
| 2 | `audio` | **Audio & Intercom** | `assets/icons/settings/audio.svg` |
| 3 | `surveillance` | **Surveillance & DVR** | `assets/icons/settings/surveillance.svg` |
| 4 | `security` | **Security & App Lock** | `assets/icons/settings/security.svg` |
| 5 | `devices` | **Paired Devices** | `assets/icons/settings/devices.svg` |
| 6 | `about` | **About Nexus Parent** | `assets/logo/nexus-parent.svg` (`preserveIconColors: true` -> `.is-brand-logo`) |

- Setting `preserveIconColors: true` prevents `.settings-nav-icon`'s `filter: invert(1)` rule from inverting the metallic silver `nexus-parent.svg` logo.
- Legacy `diagnosticLogging` entries in `localStorage` (`nexus_parent_settings_v1`) are stripped automatically on boot by `SettingsState`.
- Clicking **Help -> About Nexus Parent** in the top titlebar menu (`src/ts/titlebar/about.ts`) delegates directly to `openSettingsDialog('about')`, unifying both About entry points.

---

## 2. Rust Backend IPC Contracts (`src-tauri/src/commands.rs`)

| Command | Arguments | Return Type | Purpose |
| :--- | :--- | :--- | :--- |
| `get_parent_system_info` | None | `ParentSystemInfo` | Returns `appName`, `appVersion`, `identifier`, `tauriVersion` (`tauri::VERSION`), live `webviewVersion` (`tauri::webview_version()`), `os`, `arch`, `cpuCores`, `exePath`, `configDir`, and `localDataDir`. |
| `open_local_folder` | `path: String` | `Result<(), String>` | Validates the directory exists via `validate_folder_path` and opens it in Explorer / Finder / xdg-open. |

---

## 3. Modular Page Sections (`settings/main-content/about/`)

1. **`parent-identity.ts` (`APPLICATION IDENTITY & RELEASES`)**:
   - Displays `assets/logo/nexus-parent.svg`, bundle identifier (`com.nexus.parent`), live Cargo version badge (`getAppVersion()`), and live GitHub release updater state (`getUpdateStatus()` + `app://update-status`).
   - Provides **Check Updates** / **Restart to update** and **Copy System Info** (copies formatted plain-text diagnostics summary to clipboard).
2. **`runtime-environment.ts` (`SYSTEM & RUNTIME ENVIRONMENT`)**:
   - Displays live Host OS & Architecture, Core Runtime (`Tauri vX.Y.Z (Rust)`), WebView Engine (`WebView2` runtime version or browser fallback), and CPU logical cores + GPU hardware acceleration state.
   - Displays **App Config Directory**, **Local Data & Staged Updates**, and **Downloads Directory** with **Copy** and **Open** folder actions.
3. **`transport-storage.ts` (`TRANSPORT, SECURITY & SESSION TELEMETRY`)**:
   - Displays live WebRTC P2P encryption (`DTLS-SRTP / SCTP`), `Non-Trickle ICE` STUN mirror count (`STUN_SERVERS.length`), Firebase project ID + live anonymous auth state (`authManager.isAuthenticated()`), active peer link status (`deviceState`), and real measured UTF-16 `localStorage` footprint across `nexus_*` keys.
4. **`credits-links.ts` (`CREDITS, LICENSE & LINKS`)**:
   - Displays creator attribution (`Luka Gray (aka Soumo Mukherjee)`), copyright, and `GNU AGPL v3.0 or later` license notice with links to GitHub Repository, Documentation, Issue Tracker, Follow Creator, and Contact Email via `openExternalUrl()`.
5. **`workspace-reset.ts` (`LOCAL DATA & DANGER ZONE`)**:
   - Two-step confirmation (`Reset Workspace` -> `Confirm Full Reset?`, 4-second disarm timeout).
   - Executes `executeWorkspaceReset()`: disconnects active WebRTC (`deviceState.disconnect()`), clears remembered paired device (`deviceState.clearLastConnectedDevice()`), resets voice preset (`saveVoicePresetId('original')`), restores all factory defaults (`settingsState.resetToDefaults()`), and dispatches `nexus-workspace-reset` to refresh live metrics.

---

## 4. Verification

```powershell
# Frontend TypeScript build + Node.js test suite
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test

# Rust unit tests
Set-Location "d:\Nexus\Nexus Parent\desktop\src-tauri"
cargo test
```

---

## 5. Parent Android Parity (`Nexus Parent/android`)

- **1:1 Modular Package (`ui/settings/maincontent/about/`)**:
  - `AboutSection.kt`: Renders all 5 About groups matching Desktop `about.ts`.
  - `ParentIdentityCard.kt`: Displays `Nexus Parent`, `BuildConfig.APPLICATION_ID`, live `v${currentVersion}` and `AppUpdateProtocol.formatUpdateStatusBadge()` pills, **Copy System Info** (`ClipboardManager` plain-text diagnostics summary via `buildParentDiagnosticsSummary()`), and live **Check Updates** / **Restart to update** button.
  - `RuntimeEnvironmentCard.kt`: Displays live Host OS/API/`Build.SUPPORTED_ABIS`, Compose/Version (`BuildConfig.VERSION_NAME`/`VERSION_CODE`), `WebRTC 123.0.0 • TextureView + EglRenderer`, CPU cores + live GPU hardware acceleration status, plus **App Preferences Store**, **Local Staged Updates**, and **Downloads Directory** rows with **Copy** and SAF **Open** folder actions.
  - `TransportStorageCard.kt`: Displays live `DTLS-SRTP / SCTP`, `Non-Trickle (${STUN_SERVERS.size} STUN)`, Firebase project ID + live `AuthManager.isSignedIn()` state, measured `DeviceStorage.measureStorageFootprint()` bytes (prefs + updates cache), and active/remembered peer session status row.
  - `CreditsLinksCard.kt`: Displays creator attribution (`Luka Gray (aka Soumo Mukherjee)`), copyright, `AGPL-3.0` badge, and clickable links (GitHub Repository, Documentation, Report Issue, Follow Creator, Contact Email).
  - `WorkspaceResetCard.kt`: Two-step armed confirmation (`Reset Workspace` -> `Confirm Full Reset?` with 4-second disarm timer) calling `DeviceStateCoordinator.executeWorkspaceReset()` (disconnects WebRTC, clears `DeviceStorage` history, resets voice preset, restores factory `SettingsValues`, and refreshes storage telemetry).
- **Verification**: `ParentStateHolderTest.kt` and `NexusUiFlowsTest.kt` via `.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin`.
