/**
 * @file mod.rs
 * @description vibrateflash native module for Nexus Parent.
 */

#[tauri::command]
pub fn vibrateflash_info() -> &'static str {
    "vibrateflash ready"
}

