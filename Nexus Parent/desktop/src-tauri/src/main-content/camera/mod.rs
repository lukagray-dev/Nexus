/**
 * @file mod.rs
 * @description Camera feed native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn camera_status() -> &'static str {
    "idle"
}
