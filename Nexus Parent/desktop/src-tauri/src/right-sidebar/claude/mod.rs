/**
 * @file mod.rs
 * @description claude native module for Nexus Parent.
 */

#[tauri::command]
pub fn claude_info() -> &'static str {
    "claude ready"
}

