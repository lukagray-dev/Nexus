/**
 * @file lib.rs
 * @description Native Rust Backend Entrypoint for Nexus Parent Desktop.
 *
 * Hey friend! This initializes the Tauri v2 desktop application runner:
 * - Mounts standard plugins (e.g. opener for external links).
 * - Registers native command handlers for titlebar, left-sidebar, main-content, right-sidebar, and settings.
 * - Generates context from tauri.conf.json.
 * - Sets up the native application event loop.
 */

#[path = "left-sidebar/mod.rs"]
pub mod left_sidebar;
#[path = "main-content/mod.rs"]
pub mod main_content;
#[path = "right-sidebar/mod.rs"]
pub mod right_sidebar;
pub mod settings;
pub mod titlebar;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            titlebar::get_app_title,
            left_sidebar::get_sidebar_version,
            settings::get_stun_server,
            main_content::empty_state::validate_child_id,
            main_content::camera::camera_status,
            main_content::mic::mic_status,
            main_content::screen::screen_status,
            main_content::location::location_status,
            main_content::storage::storage_status,
            main_content::connection_state::connection_status
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
