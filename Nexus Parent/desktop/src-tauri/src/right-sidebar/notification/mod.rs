/**
 * @file mod.rs
 * @description notification native module for Nexus Parent.
 */

#[tauri::command]
pub fn notification_info() -> &'static str {
    "notification ready"
}

