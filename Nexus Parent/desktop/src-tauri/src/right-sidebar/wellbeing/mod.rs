/**
 * @file mod.rs
 * @description wellbeing native module for Nexus Parent.
 */

#[tauri::command]
pub fn wellbeing_info() -> &'static str {
    "wellbeing ready"
}

