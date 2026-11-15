/**
 * @file mod.rs
 * @description telegram native module for Nexus Parent.
 */

#[tauri::command]
pub fn telegram_info() -> &'static str {
    "telegram ready"
}

