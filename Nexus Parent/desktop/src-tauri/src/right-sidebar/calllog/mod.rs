/**
 * @file mod.rs
 * @description calllog native module for Nexus Parent.
 */

#[tauri::command]
pub fn calllog_info() -> &'static str {
    "calllog ready"
}

