/**
 * @file mod.rs
 * @description snapchat native module for Nexus Parent.
 */

#[tauri::command]
pub fn snapchat_info() -> &'static str {
    "snapchat ready"
}

