/**
 * @file mod.rs
 * @description wallpaper native module for Nexus Parent.
 */

#[tauri::command]
pub fn wallpaper_info() -> &'static str {
    "wallpaper ready"
}

