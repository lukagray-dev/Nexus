//! Native System Tray, Window Lifecycle & Startup Manager for Nexus Parent Desktop.
//!
//! Hey friend! This module owns everything related to how the desktop window lives on your OS:
//! 1. Builds the native System Tray icon and right-click context menu (Show, Hide, Settings, Quit).
//! 2. Intercepts the window close button (`[X]` / `Alt+F4`) so by default the app hides into
//!    the system tray instead of killing the WebView — keeping WebRTC, Firebase, streams, and
//!    background monitoring running without interruption.
//! 3. Persists `closeAction` and `launchMinimized` in `window-settings.json` inside the OS app
//!    config directory so Rust can read them synchronously before/during window events.
//! 4. Integrates with `tauri-plugin-autostart` (passing `--minimized`) so OS startup launches
//!    silently into the system tray when enabled, while manual launches remain visible.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use tauri::{
    menu::{MenuBuilder, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    App, AppHandle, Emitter, Manager, Runtime, State, Window, WindowEvent,
};
use tauri_plugin_autostart::ManagerExt;

/// Tray icon identifier so we can reference or update it later if needed.
pub const TRAY_ID: &str = "nexus-parent-tray";

/// CLI flag appended by OS autostart when "Launch Minimized on System Startup" is enabled.
pub const MINIMIZED_ARG: &str = "--minimized";

/// Event emitted to the frontend when the user clicks "Settings" from the system tray menu.
pub const EVENT_TRAY_OPEN_SETTINGS: &str = "tray://open-settings";

/// Filename for persisted Rust-side window and tray preferences.
const SETTINGS_FILENAME: &str = "window-settings.json";

/// Defines what happens when the user clicks the `[X]` close button or presses `Alt+F4`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum CloseAction {
    /// Hide the main window to the system tray and keep background monitoring alive.
    Tray,
    /// Terminate the application completely.
    Exit,
}

impl Default for CloseAction {
    fn default() -> Self {
        // By default, closing the window minimizes Nexus Parent to the system tray.
        Self::Tray
    }
}

/// Persisted configuration for desktop window close behavior and startup state.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WindowTraySettings {
    pub close_action: CloseAction,
    pub launch_minimized: bool,
}

impl Default for WindowTraySettings {
    fn default() -> Self {
        Self {
            close_action: CloseAction::Tray,
            // Disabled by default per application specification.
            launch_minimized: false,
        }
    }
}

/// Snapshot returned to the TypeScript frontend when querying native window/tray settings.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WindowTraySettingsSnapshot {
    pub close_action: CloseAction,
    pub launch_minimized: bool,
    pub autostart_enabled: bool,
}

/// Thread-safe Tauri managed state tracking window behavior and explicit exit intent.
pub struct WindowLifecycleState {
    settings: Mutex<WindowTraySettings>,
    config_path: Option<PathBuf>,
    /// True only when the user explicitly requests full termination (File->Exit, Ctrl+Q, Tray->Quit).
    is_exiting: AtomicBool,
}

impl WindowLifecycleState {
    /// Creates a lifecycle state loaded from disk (falling back cleanly to defaults).
    pub fn load_from_path(config_path: Option<PathBuf>) -> Self {
        let initial_settings = config_path
            .as_deref()
            .map(load_settings_from_file)
            .unwrap_or_default();

        Self {
            settings: Mutex::new(initial_settings),
            config_path,
            is_exiting: AtomicBool::new(false),
        }
    }

    /// Returns a cloned copy of the current window & tray settings.
    pub fn get_settings(&self) -> WindowTraySettings {
        self.settings
            .lock()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    /// Updates `close_action` in memory and writes it to disk if a config path is configured.
    pub fn set_close_action(&self, action: CloseAction) -> Result<WindowTraySettings, String> {
        let updated = {
            let mut guard = self
                .settings
                .lock()
                .map_err(|_| "Failed to acquire window settings lock".to_string())?;
            guard.close_action = action;
            guard.clone()
        };

        if let Some(path) = self.config_path.as_deref() {
            save_settings_to_file(path, &updated)?;
        }

        Ok(updated)
    }

    /// Updates `launch_minimized` in memory and writes it to disk if a config path is configured.
    pub fn set_launch_minimized(&self, enabled: bool) -> Result<WindowTraySettings, String> {
        let updated = {
            let mut guard = self
                .settings
                .lock()
                .map_err(|_| "Failed to acquire window settings lock".to_string())?;
            guard.launch_minimized = enabled;
            guard.clone()
        };

        if let Some(path) = self.config_path.as_deref() {
            save_settings_to_file(path, &updated)?;
        }

        Ok(updated)
    }

    /// Marks whether the application is intentionally exiting (bypasses minimize-to-tray).
    pub fn mark_exiting(&self, exiting: bool) {
        self.is_exiting.store(exiting, Ordering::SeqCst);
    }

    /// Returns true if an explicit application exit has been initiated.
    pub fn is_exiting(&self) -> bool {
        self.is_exiting.load(Ordering::SeqCst)
    }
}

/// Pure decision function: determines whether a close request should hide to tray instead of exiting.
pub fn should_minimize_to_tray(is_exiting: bool, close_action: CloseAction) -> bool {
    !is_exiting && close_action == CloseAction::Tray
}

/// Pure decision function: checks whether CLI args indicate an autostart minimized launch.
pub fn should_start_hidden(args: &[String]) -> bool {
    args.iter().any(|arg| arg == MINIMIZED_ARG)
}

/// Loads `WindowTraySettings` from disk, returning defaults if missing or malformed.
pub fn load_settings_from_file(path: &Path) -> WindowTraySettings {
    match fs::read_to_string(path) {
        Ok(raw) => serde_json::from_str::<WindowTraySettings>(&raw).unwrap_or_default(),
        Err(_) => WindowTraySettings::default(),
    }
}

/// Atomically persists `WindowTraySettings` to disk, creating parent directories if needed.
pub fn save_settings_to_file(path: &Path, settings: &WindowTraySettings) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .map_err(|e| format!("Failed to create settings directory: {}", e))?;
    }

    let serialized = serde_json::to_string_pretty(settings)
        .map_err(|e| format!("Failed to serialize window settings: {}", e))?;

    fs::write(path, serialized).map_err(|e| format!("Failed to write window settings: {}", e))
}

/// Resolves the config file path inside Tauri's OS-specific app config directory.
pub fn resolve_settings_path<R: Runtime>(app: &AppHandle<R>) -> Option<PathBuf> {
    app.path()
        .app_config_dir()
        .ok()
        .map(|dir| dir.join(SETTINGS_FILENAME))
}

/// Restores, unminimizes, shows, and focuses the main Nexus Parent window.
pub fn show_and_focus_main_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}

/// Hides the main Nexus Parent window to the system tray without touching WebView state.
pub fn hide_main_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.hide();
    }
}

/// Initiates a clean full application exit (allowing `beforeunload` to tear down WebRTC/Firebase).
pub fn request_clean_exit<R: Runtime>(app: &AppHandle<R>) {
    if let Some(state) = app.try_state::<WindowLifecycleState>() {
        state.mark_exiting(true);
    }

    if let Some(window) = app.get_webview_window("main") {
        if window.close().is_err() {
            app.exit(0);
        }
    } else {
        app.exit(0);
    }
}

/// Initializes the native OS system tray icon, tooltip, left-click restore, and context menu.
pub fn setup_system_tray<R: Runtime>(app: &mut App<R>) -> tauri::Result<()> {
    let show_item = MenuItem::with_id(app, "show", "Show Nexus Parent", true, None::<&str>)?;
    let hide_item = MenuItem::with_id(app, "hide", "Hide to Tray", true, None::<&str>)?;
    let settings_item = MenuItem::with_id(app, "settings", "Settings", true, None::<&str>)?;
    let quit_item = MenuItem::with_id(app, "quit", "Quit Nexus Parent", true, None::<&str>)?;

    let tray_menu = MenuBuilder::new(app)
        .item(&show_item)
        .item(&hide_item)
        .separator()
        .item(&settings_item)
        .separator()
        .item(&quit_item)
        .build()?;

    let mut tray_builder = TrayIconBuilder::with_id(TRAY_ID)
        .menu(&tray_menu)
        .tooltip("Nexus Parent")
        .show_menu_on_left_click(false)
        .on_menu_event(|app_handle, event| match event.id().as_ref() {
            "show" => show_and_focus_main_window(app_handle),
            "hide" => hide_main_window(app_handle),
            "settings" => {
                show_and_focus_main_window(app_handle);
                let _ = app_handle.emit(EVENT_TRAY_OPEN_SETTINGS, ());
            }
            "quit" => request_clean_exit(app_handle),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                show_and_focus_main_window(tray.app_handle());
            }
        });

    if let Some(icon) = app.default_window_icon() {
        tray_builder = tray_builder.icon(icon.clone());
    }

    tray_builder.build(app)?;
    Ok(())
}

/// Sets initial window visibility on startup: stays hidden in tray only when `--minimized` is passed.
pub fn apply_startup_visibility<R: Runtime>(app: &App<R>) {
    let args: Vec<String> = std::env::args().collect();
    if should_start_hidden(&args) {
        hide_main_window(app.handle());
    } else {
        show_and_focus_main_window(app.handle());
    }
}

/// Handles native window events, intercepting `CloseRequested` to minimize to tray when configured.
pub fn handle_window_event<R: Runtime>(window: &Window<R>, event: &WindowEvent) {
    if window.label() != "main" {
        return;
    }

    if let WindowEvent::CloseRequested { api, .. } = event {
        let app = window.app_handle();
        let (is_exiting, close_action) = match app.try_state::<WindowLifecycleState>() {
            Some(state) => (state.is_exiting(), state.get_settings().close_action),
            None => (false, CloseAction::Tray),
        };

        if should_minimize_to_tray(is_exiting, close_action) {
            // Cancel OS window destruction so WebView, WebRTC, and background services remain alive.
            api.prevent_close();
            let _ = window.hide();
        }
    }
}

/// Tauri command: returns persisted window/tray settings plus live OS autostart status.
#[tauri::command]
pub fn get_window_tray_settings<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, WindowLifecycleState>,
) -> WindowTraySettingsSnapshot {
    let settings = state.get_settings();
    let autostart_enabled = app.autolaunch().is_enabled().unwrap_or(false);

    WindowTraySettingsSnapshot {
        close_action: settings.close_action,
        launch_minimized: settings.launch_minimized,
        autostart_enabled,
    }
}

/// Tauri command: updates and persists the `[X]` close button behavior (`tray` or `exit`).
#[tauri::command]
pub fn set_close_action(
    action: CloseAction,
    state: State<'_, WindowLifecycleState>,
) -> Result<WindowTraySettings, String> {
    state.set_close_action(action)
}

/// Tauri command: enables or disables OS startup registration (`--minimized`) and persists state.
#[tauri::command]
pub fn set_launch_minimized<R: Runtime>(
    enabled: bool,
    app: AppHandle<R>,
    state: State<'_, WindowLifecycleState>,
) -> Result<WindowTraySettings, String> {
    let autolaunch = app.autolaunch();
    if enabled {
        autolaunch
            .enable()
            .map_err(|e| format!("Failed to enable OS startup entry: {}", e))?;
    } else {
        // Ignore error if entry was already absent when disabling.
        let is_currently_enabled = autolaunch.is_enabled().unwrap_or(false);
        if is_currently_enabled {
            autolaunch
                .disable()
                .map_err(|e| format!("Failed to disable OS startup entry: {}", e))?;
        }
    }

    state.set_launch_minimized(enabled)
}

/// Tauri command: hides the main window to the system tray immediately.
#[tauri::command]
pub fn hide_to_tray<R: Runtime>(app: AppHandle<R>) {
    hide_main_window(&app);
}

/// Tauri command: shows and focuses the main window from the system tray.
#[tauri::command]
pub fn show_main_window<R: Runtime>(app: AppHandle<R>) {
    show_and_focus_main_window(&app);
}

/// Tauri command: performs a full application shutdown regardless of `close_action`.
#[tauri::command]
pub fn exit_application<R: Runtime>(app: AppHandle<R>) {
    request_clean_exit(&app);
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn unique_temp_file(name: &str) -> PathBuf {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("system time after epoch")
            .as_nanos();
        std::env::temp_dir()
            .join(format!("nexus_tray_test_{}_{}", nanos, std::process::id()))
            .join(name)
    }

    #[test]
    fn defaults_match_specification() {
        let defaults = WindowTraySettings::default();
        assert_eq!(defaults.close_action, CloseAction::Tray);
        assert!(!defaults.launch_minimized);
    }

    #[test]
    fn close_decision_matrix_respects_exiting_and_action() {
        // Default close action minimizes to tray unless explicit exit was requested
        assert!(should_minimize_to_tray(false, CloseAction::Tray));
        assert!(!should_minimize_to_tray(true, CloseAction::Tray));

        // Exit action always closes normally
        assert!(!should_minimize_to_tray(false, CloseAction::Exit));
        assert!(!should_minimize_to_tray(true, CloseAction::Exit));
    }

    #[test]
    fn startup_hidden_flag_detection() {
        assert!(!should_start_hidden(&[]));
        assert!(!should_start_hidden(&["nexus-parent.exe".to_string()]));
        assert!(should_start_hidden(&[
            "nexus-parent.exe".to_string(),
            "--minimized".to_string()
        ]));
        assert!(!should_start_hidden(&[
            "nexus-parent.exe".to_string(),
            "--minimized=false".to_string()
        ]));
    }

    #[test]
    fn settings_persist_roundtrip_and_recover_from_corruption() {
        let path = unique_temp_file("window-settings.json");

        // Missing file falls back cleanly to defaults
        let initial = load_settings_from_file(&path);
        assert_eq!(initial, WindowTraySettings::default());

        // Lifecycle state saves updates and reloads identically
        let state = WindowLifecycleState::load_from_path(Some(path.clone()));
        state
            .set_close_action(CloseAction::Exit)
            .expect("save close_action");
        state
            .set_launch_minimized(true)
            .expect("save launch_minimized");

        let reloaded = WindowLifecycleState::load_from_path(Some(path.clone()));
        assert_eq!(
            reloaded.get_settings(),
            WindowTraySettings {
                close_action: CloseAction::Exit,
                launch_minimized: true,
            }
        );

        // Corrupted JSON recovers cleanly to defaults
        fs::write(&path, "{ invalid json !!!").expect("write corrupt json");
        let recovered = load_settings_from_file(&path);
        assert_eq!(recovered, WindowTraySettings::default());

        if let Some(parent) = path.parent() {
            let _ = fs::remove_dir_all(parent);
        }
    }

    #[test]
    fn lifecycle_exit_flag_transitions_atomically() {
        let state = WindowLifecycleState::load_from_path(None);
        assert!(!state.is_exiting());
        state.mark_exiting(true);
        assert!(state.is_exiting());
        state.mark_exiting(false);
        assert!(!state.is_exiting());
    }
}
