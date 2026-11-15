/**
 * @file mod.rs
 * @description Connection state native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn connection_status() -> &'static str {
    "disconnected"
}
