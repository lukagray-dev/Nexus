/**
 * @file mod.rs
 * @description whatsapp native module for Nexus Parent.
 */

#[tauri::command]
pub fn whatsapp_info() -> &'static str {
    "whatsapp ready"
}

