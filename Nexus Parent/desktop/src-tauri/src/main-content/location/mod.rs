/**
 * @file mod.rs
 * @description Location tracking native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn location_status() -> &'static str {
    "ready"
}
