/**
 * @file lib.rs
 * @description Native Rust Backend Entrypoint for Nexus Parent Desktop.
 *
 * Hey friend! This initializes the Tauri v2 desktop application runner:
 * - Mounts standard plugins (e.g. opener for external links).
 * - Generates context from tauri.conf.json.
 * - Sets up the native application event loop and window lifecycle.
 *
 * Note: Video/audio streaming, WebRTC peer connections, and UI data flows
 * are handled directly by the frontend inside the native OS WebView.
 * Rust serves as the lightweight native host shell and local persistence layer.
 */

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
