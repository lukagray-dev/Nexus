/**
 * @file mod.rs
 * @description Left Sidebar native command handlers for Nexus Parent.
 *
 * Hey friend! This module handles sidebar operations:
 * - Persisting collapsed categories.
 * - Storing device pairing records.
 */

#[tauri::command]
pub fn get_sidebar_version() -> &'static str {
    "1.0.0"
}
