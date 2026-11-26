//! Commands for Nexus Parent desktop client.
//!
//! Provides native system integrations including cross-platform external URL launching,
//! local folder opening, and live parent runtime/environment introspection.

use std::path::Path;
use std::process::Command;

use serde::Serialize;
use tauri::{AppHandle, Manager, Runtime};
use tauri_plugin_notification::NotificationExt;

/// Live host, runtime, and storage directory metadata for the About page.
#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ParentSystemInfo {
    pub app_name: String,
    pub app_version: String,
    pub identifier: String,
    pub tauri_version: String,
    pub webview_version: Option<String>,
    pub os: String,
    pub arch: String,
    pub cpu_cores: usize,
    pub exe_path: Option<String>,
    pub config_dir: Option<String>,
    pub local_data_dir: Option<String>,
}

/// Platform-specific external browser URL launcher.
#[tauri::command]
pub async fn open_external_url(url: String) -> Result<(), String> {
    let normalized = url.trim().to_string();
    if normalized.is_empty() {
        return Err("URL cannot be empty".into());
    }

    #[cfg(target_os = "windows")]
    {
        Command::new("cmd")
            .args(["/C", "start", "", &normalized])
            .spawn()
            .map_err(|e| format!("Failed to open browser: {}", e))?;
    }

    #[cfg(target_os = "macos")]
    {
        Command::new("open")
            .arg(&normalized)
            .spawn()
            .map_err(|e| format!("Failed to open browser: {}", e))?;
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    {
        Command::new("xdg-open")
            .arg(&normalized)
            .spawn()
            .map_err(|e| format!("Failed to open browser: {}", e))?;
    }

    Ok(())
}

/// Collects live OS, Tauri runtime, WebView2 version, and local storage paths for Nexus Parent.
#[tauri::command]
pub fn get_parent_system_info<R: Runtime>(app: AppHandle<R>) -> ParentSystemInfo {
    let package = app.package_info();
    let cpu_cores = std::thread::available_parallelism()
        .map(|count| count.get())
        .unwrap_or(1);

    ParentSystemInfo {
        app_name: package.name.clone(),
        app_version: package.version.to_string(),
        identifier: app.config().identifier.clone(),
        tauri_version: tauri::VERSION.to_string(),
        webview_version: tauri::webview_version().ok(),
        os: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
        cpu_cores,
        exe_path: std::env::current_exe()
            .ok()
            .map(|p| p.to_string_lossy().to_string()),
        config_dir: app
            .path()
            .app_config_dir()
            .ok()
            .map(|p| p.to_string_lossy().to_string()),
        local_data_dir: app
            .path()
            .app_local_data_dir()
            .ok()
            .map(|p| p.to_string_lossy().to_string()),
    }
}

/// Validates that `path` refers to an existing directory and opens it in the OS file manager.
pub fn validate_folder_path(path: &str) -> Result<String, String> {
    let normalized = path.trim().to_string();
    if normalized.is_empty() {
        return Err("Folder path cannot be empty".into());
    }
    let target = Path::new(&normalized);
    if !target.exists() {
        return Err(format!("Folder does not exist yet: {}", normalized));
    }
    if !target.is_dir() {
        return Err(format!("Path is not a directory: {}", normalized));
    }
    Ok(normalized)
}

/// Opens an existing local directory in Windows Explorer, Finder, or the Linux file manager.
#[tauri::command]
pub async fn open_local_folder(path: String) -> Result<(), String> {
    let validated = validate_folder_path(&path)?;

    #[cfg(target_os = "windows")]
    {
        Command::new("explorer")
            .arg(&validated)
            .spawn()
            .map_err(|e| format!("Failed to open folder in Explorer: {}", e))?;
    }

    #[cfg(target_os = "macos")]
    {
        Command::new("open")
            .arg(&validated)
            .spawn()
            .map_err(|e| format!("Failed to open folder in Finder: {}", e))?;
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    {
        Command::new("xdg-open")
            .arg(&validated)
            .spawn()
            .map_err(|e| format!("Failed to open folder: {}", e))?;
    }

    Ok(())
}

/// Validates that a desktop notification payload has non-empty title or body text.
pub fn validate_notification_payload(title: &str, body: &str) -> Result<(String, String), String> {
    let clean_title = title.trim().to_string();
    let clean_body = body.trim().to_string();
    if clean_title.is_empty() && clean_body.is_empty() {
        return Err("Notification title and body cannot both be empty".into());
    }
    Ok((clean_title, clean_body))
}

/// Dispatches a native OS desktop notification (works even while minimized to system tray).
#[tauri::command]
pub fn show_desktop_notification<R: Runtime>(
    app: AppHandle<R>,
    title: String,
    body: String,
) -> Result<(), String> {
    let (clean_title, clean_body) = validate_notification_payload(&title, &body)?;
    app.notification()
        .builder()
        .title(clean_title)
        .body(clean_body)
        .show()
        .map_err(|e| format!("Failed to display desktop notification: {}", e))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validate_notification_payload_rejects_empty_and_accepts_text() {
        assert!(validate_notification_payload("", "   ").is_err());
        let ok = validate_notification_payload("  Alert  ", "  Battery 12%  ");
        assert_eq!(ok, Ok(("Alert".to_string(), "Battery 12%".to_string())));
    }

    #[test]
    fn validate_folder_path_rejects_empty_and_missing_paths() {
        assert!(validate_folder_path("").is_err());
        assert!(validate_folder_path("   ").is_err());
        assert!(validate_folder_path("Z:\\nonexistent_nexus_test_dir_12345").is_err());
    }

    #[test]
    fn validate_folder_path_accepts_existing_directory() {
        let temp_dir = std::env::temp_dir();
        let temp_str = temp_dir.to_string_lossy().to_string();
        assert!(validate_folder_path(&temp_str).is_ok());
    }
}
