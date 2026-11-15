/**
 * @file mod.rs
 * @description keyboard native module for Nexus Parent.
 */

#[tauri::command]
pub fn keyboard_info() -> &'static str {
    "keyboard ready"
}

