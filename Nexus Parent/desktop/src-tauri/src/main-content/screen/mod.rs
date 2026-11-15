/**
 * @file mod.rs
 * @description Screen recording and mirror native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn screen_status() -> &'static str {
    "ready"
}
