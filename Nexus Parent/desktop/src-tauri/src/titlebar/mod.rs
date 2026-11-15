/**
 * @file mod.rs
 * @description Titlebar native window command handlers for Nexus Parent.
 *
 * Hey friend! This module exposes Tauri commands for window management:
 * - minimize, maximize, unmaximize, is_maximized, and close.
 */

#[tauri::command]
pub fn get_app_title() -> String {
    "Nexus Parent".to_string()
}

pub fn init<R: tauri::Runtime>() -> tauri::plugin::TauriPlugin<R> {
    tauri::plugin::Builder::new("titlebar")
        .invoke_handler(tauri::generate_handler![get_app_title])
        .build()
}
