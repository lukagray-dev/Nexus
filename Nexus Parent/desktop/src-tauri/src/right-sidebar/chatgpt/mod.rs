/**
 * @file mod.rs
 * @description chatgpt native module for Nexus Parent.
 */

#[tauri::command]
pub fn chatgpt_info() -> &'static str {
    "chatgpt ready"
}

