/**
 * @file mod.rs
 * @description applock native module for Nexus Parent.
 */

#[tauri::command]
pub fn applock_info() -> &'static str {
    "applock ready"
}

