/**
 * @file mod.rs
 * @description chats native module for Nexus Parent.
 */

#[tauri::command]
pub fn chats_info() -> &'static str {
    "chats ready"
}

