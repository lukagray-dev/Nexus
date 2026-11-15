/**
 * @file mod.rs
 * @description instagram native module for Nexus Parent.
 */

#[tauri::command]
pub fn instagram_info() -> &'static str {
    "instagram ready"
}

