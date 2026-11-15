/**
 * @file mod.rs
 * @description gmail native module for Nexus Parent.
 */

#[tauri::command]
pub fn gmail_info() -> &'static str {
    "gmail ready"
}

