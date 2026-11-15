/**
 * @file mod.rs
 * @description sms native module for Nexus Parent.
 */

#[tauri::command]
pub fn sms_info() -> &'static str {
    "sms ready"
}

