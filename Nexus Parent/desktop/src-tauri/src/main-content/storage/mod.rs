/**
 * @file mod.rs
 * @description Storage and file explorer native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn storage_status() -> &'static str {
    "ready"
}
