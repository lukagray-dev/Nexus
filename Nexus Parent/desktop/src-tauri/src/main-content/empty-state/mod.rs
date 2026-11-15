/**
 * @file mod.rs
 * @description Empty state native helpers for Nexus Parent.
 */

#[tauri::command]
pub fn validate_child_id(id: String) -> bool {
    let clean = id.replace(['-', ' '], "");
    clean.len() == 12
}
