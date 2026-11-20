//! Commands for Nexus Parent desktop client.
//!
//! Provides native system integrations including cross-platform external URL launching.

use std::process::Command;

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
