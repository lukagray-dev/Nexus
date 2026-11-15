/**
 * @file mod.rs
 * @description facebook native module for Nexus Parent.
 */

#[tauri::command]
pub fn facebook_info() -> &'static str {
    "facebook ready"
}

