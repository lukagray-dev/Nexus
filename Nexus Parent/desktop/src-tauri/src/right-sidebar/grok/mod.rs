/**
 * @file mod.rs
 * @description grok native module for Nexus Parent.
 */

#[tauri::command]
pub fn grok_info() -> &'static str {
    "grok ready"
}

