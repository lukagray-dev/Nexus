# General & System Settings Implementation Specification

## Overview

This document specifies the architecture, persistence model, frontend UI modules, Tauri v2 IPC contracts, native Rust backend services, and verification tests for the **General & System** settings category in **Nexus Parent** (`Nexus Parent/desktop`).

The **General & System** page is the primary tab of the Settings dialog (`Preferences`) and governs five functional groups:
1. **File Storage & Export** (`Downloads & Media Save Directory`, `Ask Every Time Before Downloading`)
2. **Startup & Connection** (`Auto-Connect Last Paired Device on Launch`, `Launch on System Startup`, `Start Minimized to Tray`)
3. **Window & Display Behavior** (`Always on Top`, `Keep Screen On When Connected`, `Close Button [X] Behavior`)
4. **Desktop Alert Notifications** (`Intruder Selfie Triggered`, `Child Device Low Battery`, `Child Device Disconnection`, `Active Call Alert`)
5. **Application Updates** (`Check for Updates Automatically`, `Release Channel & Status`, and Titlebar `Restart to update`)

---

## 1. Settings Navigation Order & Default Tab

Defined in `Nexus Parent/desktop/src/ts/settings/sidebar/types.ts` (`SETTINGS_TABS`) and `state.ts` (`SettingsState`):

| Order | Tab ID | Sidebar Label | Default Active |
| :---: | :--- | :--- | :---: |
| 1 | `general` | **General & System** | Yes |
| 2 | `audio` | **Audio & Intercom** | - |
| 3 | `surveillance` | **Surveillance & DVR** | - |
| 4 | `devices` | **Paired Devices** | - |
| 5 | `about` | **About Nexus Parent** | - |

> Note: The former **Security & App Lock** settings tab was removed because remote PIN changing (`CMD_APPLOCK_SET_PIN`) already lives in the Right Sidebar App Lock panel, while password masking and lockout thresholds are enforced on the Android child. Its **Desktop Alert Notifications** toggles—alongside **Active Call Alert** (`alertChildActiveCall`, detailed in `docs/CALL-ALERT-IMPLEMENTATION.md`)—live directly inside **General & System** (`general-system/desktop-alerts.ts`).

---

## 2. Dual-Layer Persistence & Startup Reconciliation

Settings are stored across two complementary layers so both the WebView frontend and the Rust native window/tray host act deterministically:

1. **Frontend Store (`localStorage` key `nexus_parent_settings_v1`)**:
   - Managed by `SettingsState` (`src/ts/settings/sidebar/state.ts`) using an instant auto-save model on every toggle, input, or segmented button click.
   - Supports one-click `resetToDefaults()` and migrates legacy `launchMinimized: true` configs cleanly into `launchOnStartup: true` + `startMinimized: true`.
2. **Native Rust Store (`window-settings.json` in OS `app_config_dir`)**:
   - Managed by `WindowLifecycleState` (`src-tauri/src/tray.rs`).
   - Allows Rust to enforce `startMinimized`, `alwaysOnTop`, and `closeAction` **before** the WebView finishes loading or when the user clicks `[X]` / `Alt+F4`.
3. **Boot Reconciliation (`initGeneralSystemRuntime` in `general-system.ts`)**:
   - On DOM boot, the frontend queries `get_window_tray_settings`.
   - Any mismatch between `localStorage` and `window-settings.json` (or OS autostart registry status) is reconciled immediately, and `settingsState.onValuesChange` keeps Rust synchronized on later edits or `Reset Defaults`.

### Complete Schema (`SettingsValues` — General & System)

| Key | Type | Default | Persisted In | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `downloadsDirectory` | `string` | `""` | `localStorage` | Target folder on parent PC for Monitor Storage downloads. Empty until chosen once on fresh install. |
| `askBeforeDownload` | `boolean` | `false` | `localStorage` | Prompts with native OS Save As dialog before every download when `true`. |
| `autoConnectLastDevice` | `boolean` | `false` | `localStorage` | Performs a single startup WebRTC connection attempt to `deviceState.getLastConnectedDevice()`. |
| `launchOnStartup` | `boolean` | `false` | `localStorage` + OS Autostart + `window-settings.json` | Registers/unregisters OS login startup via `tauri-plugin-autostart`. |
| `startMinimized` | `boolean` | `false` | `localStorage` + `window-settings.json` | Launches app hidden into the system tray on every launch. |
| `alwaysOnTop` | `boolean` | `false` | `localStorage` + `window-settings.json` | Pins the main window above other desktop windows. |
| `keepScreenAwake` | `boolean` | `true` | `localStorage` + Rust `KeepAwakeState` | Prevents PC display and system sleep while connected or reconnecting to a child device. |
| `closeAction` | `'tray' \| 'exit'` | `'tray'` | `localStorage` + `window-settings.json` | Controls whether `[X]` / `Alt+F4` minimizes to system tray or terminates the process. |
| `autoCheckUpdates` | `boolean` | `true` | `localStorage` | Enables background GitHub stable release checks on startup and every 6 hours. |
| `alertIntruderSelfie` | `boolean` | `true` | `localStorage` | Triggers a native desktop notification & toast when `APPLOCK_NEW` reports an unauthorized PIN attempt (`pinCorrect === false`). |
| `alertLowBattery` | `boolean` | `true` | `localStorage` | Triggers a deduplicated native desktop notification & toast when live child battery drops below `15%` while unplugged. |
| `alertChildDisconnected` | `boolean` | `true` | `localStorage` | Triggers a native desktop notification & toast when the active P2P connection drops unexpectedly. |

---

## 3. Detailed Per-Setting Specifications

### Group A: File Storage & Export

#### 3.1 Downloads & Media Save Directory
- **Module**: `src/ts/settings/main-content/general-system/downloads-directory.ts`
- **Rust Backend**: `src-tauri/src/downloads.rs`
- **DOM Controls**:
  - Row container: `#setting-downloads-dir-row` (`.settings-row-vertical`)
  - Text input: `#setting-downloads-dir` (placeholder: `"Not set yet (will ask once on first download)"`)
  - Action button: `#btn-browse-downloads` (`Browse`)
- **Behavior**:
  1. **Fresh Install**: Starts as `""` so no arbitrary path is assumed. On the first download inside **Monitor Storage** (while `askBeforeDownload` is `false`), `resolveDownloadDestination()` queries `get_default_downloads_directory` as a starting suggestion and opens `pick_downloads_directory` **once**. The chosen directory is saved to `settingsState.setValue('downloadsDirectory', path)` and reused automatically for all future downloads.
  2. **Manual Change Anytime**: Users can type/paste a path directly or click `Browse` to pick a new folder via native OS dialog (`tauri-plugin-dialog`).
  3. **Reactive Disabled State**: Subscribes to `settingsState.onValuesChange`. Whenever `askBeforeDownload` becomes `true`, the row gets `.is-disabled`, and both `#setting-downloads-dir` and `#btn-browse-downloads` are disabled—while keeping the stored folder path intact so turning `askBeforeDownload` back off restores it seamlessly.

#### 3.2 Ask Every Time Before Downloading
- **Module**: `src/ts/settings/main-content/general-system/ask-before-download.ts`
- **DOM Control**: Toggle switch `#setting-ask-before-download` (default: `false`)
- **Behavior**:
  - **When `false` (default)**: Downloads from **Monitor Storage** (table download button, inspector drawer download button, and high-res Image Preview `Download Image` button) save directly into `downloadsDirectory` with zero prompts. Duplicate filenames are automatically incremented (`photo.jpg` -> `photo (1).jpg`) by `resolve_unique_file_path` in `downloads.rs`.
  - **When `true`**: Every download opens the native OS **Save As** dialog (`pick_save_file_path`) pre-filled with the remote filename and starting in `downloadsDirectory` (or OS Downloads). Cancelling the dialog aborts before dispatching `STORAGE_GET_FILE` over WebRTC.

#### Universal Download Pipeline (`download-manager.ts` + `downloads.rs`)

```text
Monitor Storage Download (Table / Inspector / Image Preview Modal)
  |
  +--> resolveDownloadDestination()
         |
         +-- askBeforeDownload == true?
         |      Yes --> pick_save_file_path (OS Save As) -> cancelled? abort : exactFilePath
         |
         +-- askBeforeDownload == false
                +-- downloadsDirectory empty?
                |      Yes --> pick_downloads_directory (asks ONCE) -> saves to settingsState
                +-- targetDirectory = downloadsDirectory
  |
  +--> Tauri Chunked Atomic Disk Write:
         1. begin_download_file  (creates dir, sanitizes name, deduplicates, opens .nexuspart)
         2. append_download_chunk (Base64-decodes 64KB WebRTC chunks directly to BufWriter)
         3. commit_download_file (flushes, closes handle, atomically renames .nexuspart -> final)
         * On error: cancel_download_file removes .nexuspart cleanly.
         * Outside Tauri (browser preview / tests): falls back to showSaveFilePicker / <a download>.
```

---

### Group B: Startup & Connection

#### 3.3 Auto-Connect Last Paired Device on Launch
- **Module**: `src/ts/settings/main-content/general-system/auto-connect-last-device.ts`
- **Entrypoint Hook**: `runStartupAutoConnect()` called in `src/ts/main.ts` after `initSettings()`.
- **DOM Control**: Toggle switch `#setting-auto-connect-last-device` (default: `false`)
- **Behavior**:
  - Reads `deviceState.getLastConnectedDevice()` (persisted under `nexus_last_connected_child_device`).
  - If enabled, not already connected/reconnecting, and a valid 12-digit `rawId` exists, calls `deviceState.connectLive(lastDevice.rawId)` **once** on boot.
  - If the child device is offline, catches the connection error cleanly, shows a non-blocking toast (`"<Device> is currently offline — ready for manual connect"`), and leaves the Pairing screen ready without entering an infinite boot loop.

#### 3.4 Launch on System Startup
- **Module**: `src/ts/settings/main-content/general-system/launch-on-startup.ts`
- **Rust Backend**: `set_launch_on_startup` in `src-tauri/src/tray.rs` (`tauri-plugin-autostart`)
- **DOM Control**: Toggle switch `#setting-launch-on-startup` (default: `false`)
- **Behavior**:
  - Registers or removes the OS login startup entry independently of window visibility.
  - Disables the switch briefly while invoking Rust; if the OS refuses startup registration (e.g., registry/permission error), reverts the UI checkbox cleanly and shows an error toast.

#### 3.5 Start Minimized to Tray
- **Module**: `src/ts/settings/main-content/general-system/start-minimized.ts`
- **Rust Backend**: `set_start_minimized` and `apply_startup_visibility` in `src-tauri/src/tray.rs`
- **DOM Control**: Toggle switch `#setting-start-minimized` (default: `false`)
- **Behavior**:
  - Completely independent of `Launch on System Startup` (`should_start_hidden_for_settings(settings.start_minimized)`).
  - Applies on **every** application launch (both OS startup and manual launch):
    - `false`: Main window opens visible and focused on launch.
    - `true`: Main window remains hidden in the OS system tray on launch until the parent clicks the tray icon or selects `Show Nexus Parent`.
  - Secondary single-instance launches still restore and focus the running window unless `--minimized` was explicitly passed.

---

### Group C: Window & Display Behavior

#### 3.6 Always on Top
- **Module**: `src/ts/settings/main-content/general-system/always-on-top.ts`
- **Rust Backend**: `set_always_on_top` and `apply_startup_visibility` in `src-tauri/src/tray.rs`
- **Capability**: `core:window:allow-set-always-on-top` in `src-tauri/capabilities/default.json`
- **DOM Control**: Toggle switch `#setting-always-on-top` (default: `false`)
- **Behavior**:
  - Calls `window.set_always_on_top(enabled)` on the `"main"` WebviewWindow immediately when toggled and applies the saved state at startup before showing the window.
  - Reverts the toggle if Rust returns an error.

#### 3.7 Keep Screen On When Connected
- **Module**: `src/ts/settings/main-content/general-system/keep-screen-awake.ts`
- **Rust Backend**: `set_keep_screen_awake` in `src-tauri/src/power.rs` (`keepawake` crate)
- **DOM Control**: Toggle switch `#setting-keep-screen-awake` (default: `true`)
- **Behavior**:
  - Uses `computeShouldKeepScreenAwake(settingEnabled, isConnected, isReconnecting)`:
    - Activates whenever `keepScreenAwake === true` **and** the parent is connected (`deviceState.isConnected()`) or reconnecting (`deviceState.isReconnecting()`), **even if no camera, microphone, or screen stream is active**.
    - Immediately releases the wake lock when the child device disconnects or when the user toggles the setting off.
  - **Why `keepawake` instead of raw Win32 `SetThreadExecutionState`**: `SetThreadExecutionState` is thread-local on Windows, so async Tauri commands executing on different Tokio worker threads would fail to clear it reliably. `KeepAwakeState` holds a single RAII `keepawake::KeepAwake` handle inside a `Mutex<Option<...>>`, guaranteeing deterministic acquisition and release across threads.

#### 3.8 Close Button [X] Behavior
- **Module**: `src/ts/settings/main-content/general-system/system-tray.ts`
- **Rust Backend**: `set_close_action`, `handle_window_event`, `request_clean_exit` in `src-tauri/src/tray.rs`
- **DOM Control**: Segmented button group `#setting-close-action` (`tray` = `Minimize to Tray` [default], `exit` = `Exit Application`)
- **Behavior**:
  - **Minimize to Tray (`tray`)**: Intercepts `WindowEvent::CloseRequested`, calls `api.prevent_close()`, and hides the window to the system tray while WebRTC, DataChannels, and Firebase listeners stay alive.
  - **Exit Application (`exit`)**: Allows the window to close and terminates the process completely (also triggering any staged update installation after exit).
  - Explicit `File -> Exit`, `Ctrl+Q`, and `Tray -> Quit Nexus Parent` always exit regardless of this setting.

---

### Group D: Desktop Alert Notifications

#### 3.9 Intruder Selfie, Low Battery (<15%), and Unexpected Disconnection Alerts
- **Module**: `src/ts/settings/main-content/general-system/desktop-alerts.ts`
- **Rust Backend**: `show_desktop_notification` in `src-tauri/src/commands.rs` via `tauri-plugin-notification`
- **Capability**: `notification:default` in `src-tauri/capabilities/default.json`
- **DOM Controls**: Toggle switches `#setting-alert-intruder`, `#setting-alert-low-battery`, and `#setting-alert-disconnect` (all default `true`)
- **Behavior**:
  - Each enabled alert fires both an in-app toast (`showToast`) and a native OS desktop notification (`showDesktopNotification`, working even while the window is minimized to the system tray; outside Tauri it falls back to `window.Notification` when granted).
  - **Intruder Selfie Triggered**: Listens to `APPLOCK_NEW` DataChannel packets and fires only when `record.pinCorrect === false` (ignoring valid unlocks and historical `APPLOCK_SNAPSHOT` syncs).
  - **Child Device Low Battery**: Listens to live `deviceState.onTelemetryUpdate` updates, firing once per device episode when `batteryPercent < 15` and `isCharging !== true`, resetting when charging starts or battery rises to `>= 20%`.
  - **Child Device Disconnection**: Listens to `deviceState.onUnexpectedDisconnect` (never firing on manual `deviceState.disconnect()`).

---

### Group E: Application Updates (GitHub Stable Releases)

#### 3.10 Check for Updates Automatically & Release Status
- **Frontend Modules**:
  - `src/ts/settings/main-content/general-system/auto-update.ts`
  - `src/ts/titlebar/updater-ui.ts` (`#btn-restart-to-update` in `index.html`)
  - `src/ts/titlebar/menu.ts` (`Help -> Check for update`)
  - `src/ts/titlebar/about.ts` (dynamic runtime version badge `#about-version-badge`)
- **Rust Backend**: `src-tauri/src/updater.rs`
- **Release Pipeline**: `.github/workflows/stable-release.yml`
- **DOM Controls**:
  - Settings toggle: `#setting-auto-check-updates` (default: `true`)
  - Settings status badge & action: `#setting-update-status-badge`, `#btn-check-updates-now` (`Check Now` / `Restart to update`)
  - Titlebar action button: `#btn-restart-to-update` (`Restart to update`, `.hidden` by default)
- **End-to-End Update Lifecycle**:
  1. **Scheduled & Manual Discovery**: When `autoCheckUpdates` is `true`, `initAutoUpdateRuntime()` runs a silent check 4 seconds after boot and every 6 hours thereafter. Users can also check anytime via `Check Now` in Settings or `Help -> Check for update` in the titlebar menu.
  2. **Strict Stable Release Filtering**: Queries `https://api.github.com/repos/lukagray-dev/Nexus/releases?per_page=10` and ignores `draft` releases, `prerelease` releases, and tags with `-alpha`/`-beta` suffixes, selecting the highest stable `vX.Y.Z` tag.
  3. **SemVer Comparison**: `is_newer_stable_version` compares `(major, minor, patch)` against `CARGO_PKG_VERSION`, recognizing stable `v0.1.0` as newer than `0.1.0-alpha`.
  4. **Background Staging & Verification**: Downloads `nexus-parent-<tag>-windows-x86_64.exe` into `%LOCALAPPDATA%\com.nexus.parent\updates\<asset>.part`, validates Windows PE `MZ` magic bytes, and verifies the SHA-256 hash against the accompanying `.sha256` asset produced by `.github/workflows/stable-release.yml`, then renames `.part` -> `.exe`.
  5. **Titlebar Visibility Rule**: Rust emits `app://update-status` (`status: 'idle' | 'checking' | 'up-to-date' | 'downloading' | 'ready' | 'error'`). `#btn-restart-to-update` remains **completely hidden** when up to date or idle, and becomes visible **only** when `status === 'ready'`.
  6. **Two Install Paths**:
     - **Click `Restart to update`** (titlebar or Settings): Cleanly disconnects `deviceState`, invokes `restart_to_update`, spawns a detached hidden PowerShell PID-wait swap helper (`relaunch = true`), exits cleanly, replaces the `.exe` (with UAC `RunAs` fallback if located in a protected folder like `Program Files`), and relaunches the new version.
     - **Close App Normally** (`File -> Exit`, `Ctrl+Q`, `Tray -> Quit`, or `[X]` when `closeAction === 'exit'`): `request_clean_exit` / `handle_window_event` invokes `schedule_staged_update_on_exit(app, false)`, swapping the `.exe` silently after the process exits **without** relaunching. Minimizing to the tray does not interrupt the running session.

---

## 4. Tauri v2 IPC Command & Event Matrix

| Command / Event | Direction | Module | Purpose |
| :--- | :--- | :--- | :--- |
| `get_default_downloads_directory` | TS -> Rust | `downloads.rs` | Resolves OS `Downloads/Nexus` as initial folder suggestion. |
| `pick_downloads_directory` | TS -> Rust | `downloads.rs` | Non-blocking native OS folder picker dialog. |
| `pick_save_file_path` | TS -> Rust | `downloads.rs` | Non-blocking native OS Save As dialog. |
| `begin_download_file` / `append_download_chunk` / `commit_download_file` / `cancel_download_file` | TS -> Rust | `downloads.rs` | Atomic `.nexuspart` chunked writer with collision auto-renaming. |
| `get_window_tray_settings` | TS -> Rust | `tray.rs` | Returns `closeAction`, `launchOnStartup`, `startMinimized`, `alwaysOnTop`, `autostartEnabled`. |
| `set_close_action` | TS -> Rust | `tray.rs` | Persists `[X]` close button mode (`tray` vs `exit`). |
| `set_launch_on_startup` | TS -> Rust | `tray.rs` | Enables/disables OS login autostart entry. |
| `set_start_minimized` | TS -> Rust | `tray.rs` | Persists independent launch-hidden behavior. |
| `set_always_on_top` | TS -> Rust | `tray.rs` | Sets and persists window Always on Top pinning. |
| `set_keep_screen_awake` | TS -> Rust | `power.rs` | Acquires or drops `KeepAwake` OS display/idle wake lock. |
| `get_app_version` | TS -> Rust | `updater.rs` | Returns running `CARGO_PKG_VERSION`. |
| `show_desktop_notification` | TS -> Rust | `commands.rs` | Dispatches native OS desktop notification via `tauri-plugin-notification`. |
| `get_update_status` | TS -> Rust | `updater.rs` | Returns current `UpdateStatusPayload` snapshot. |
| `check_for_updates` | TS -> Rust | `updater.rs` | Checks GitHub stable releases and stages any newer binary. |
| `restart_to_update` | TS -> Rust | `updater.rs` | Swaps staged binary after exit and relaunches immediately. |
| `tray://open-settings` | Rust -> TS | `tray.rs` | Opens Settings modal focused on `general` from system tray menu. |
| `app://update-status` | Rust -> TS | `updater.rs` | Pushes live update status updates to titlebar and Settings UI. |

---

## 5. Verification & Test Coverage

### TypeScript & Node.js Tests (`Nexus Parent/desktop`)
- **`test/settings.test.js`**: Sidebar 5-tab order, schema defaults, legacy `launchMinimized` migration, stripped removed-tab keys, `localStorage` auto-save, `resetToDefaults`, and General & System alert toggles.
- **`test/general-system-tray.test.js`**: Category router, separated `launchOnStartup` / `startMinimized` / `alwaysOnTop` / `closeAction` IPC sync, startup reconciliation, and `closeWindow` vs `exitApplication`.
- **`test/general-system-downloads.test.js`**: `ask-before-download.ts`, `downloads-directory.ts` native browse + reactive `.is-disabled` locking, Monitor Storage fresh-install once-prompt, Save As mode, and atomic Rust chunk streaming.
- **`test/general-system-features.test.js`**: `auto-connect-last-device.ts`, `always-on-top.ts`, `keep-screen-awake.ts` (connection-based wake lock activation/release), and titlebar `#btn-restart-to-update` visibility lifecycle.
- **`test/desktop-alerts.test.js`**: Intruder selfie (`APPLOCK_NEW`), low battery (`< 15%` with `>= 20%`/charging hysteresis), unexpected disconnect vs manual disconnect, and Tauri/browser notification fallback.

```powershell
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test
```

### Rust Unit Tests (`Nexus Parent/desktop/src-tauri`)
- **`downloads::tests`**: Filename sanitization, collision auto-incrementing (`photo (1).jpg`), atomic `.nexuspart` commit/cancel, exact path saving, and `~/` home expansion.
- **`tray::tests`**: Defaults, close decision matrix, independent `startMinimized` visibility, legacy `launchMinimized` migration, JSON corruption recovery, and atomic exit flag.
- **`power::tests`**: `KeepAwakeState` initial inactive state and clean release.
- **`updater::tests`**: Stable vs prerelease SemVer parsing/comparison, GitHub release filtering (ignoring drafts and `-alpha`), Windows asset + `.sha256` matching, PE `MZ` validation, and SHA-256 checksum parsing.

```powershell
Set-Location "d:\Nexus\Nexus Parent\desktop\src-tauri"
cargo test
```
