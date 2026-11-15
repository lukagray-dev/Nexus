/**
 * @file mod.rs
 * @description Settings native module for Nexus Parent.
 */

#[tauri::command]
pub fn get_stun_server() -> &'static str {
    "stun:stun.l.google.com:19302"
}
