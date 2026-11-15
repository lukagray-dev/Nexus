/**
 * @file mod.rs
 * @description Microphone monitoring native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn mic_status() -> &'static str {
    "ready"
}
