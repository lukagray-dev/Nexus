//! Native Rust Backend Entrypoint for Nexus Parent Desktop.
//!
//! Hey friend! This initializes the Tauri v2 desktop application runner:
//! - Mounts standard plugins (e.g. opener for external links).
//! - Registers system command handlers from the commands module.
//! - Sets up the native application event loop and window lifecycle.

pub mod commands;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![commands::open_external_url])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
