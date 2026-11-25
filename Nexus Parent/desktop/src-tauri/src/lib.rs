//! Native Rust Backend Entrypoint for Nexus Parent Desktop.
//!
//! Hey friend! This initializes the Tauri v2 desktop application runner:
//! - Enforces single-instance execution so secondary launches focus the existing tray window.
//! - Initializes OS autostart (`--minimized`) and external URL opener plugins.
//! - Configures the native system tray icon, menu, and window close-to-tray lifecycle.
//! - Registers IPC command handlers for the TypeScript frontend.

pub mod commands;
pub mod tray;

use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default();

    // Single-instance guard MUST be registered first so duplicate launches restore the running tray window.
    #[cfg(desktop)]
    {
        builder = builder.plugin(tauri_plugin_single_instance::init(|app, args, _cwd| {
            // Only bring window forward if the duplicate launch was not itself requesting hidden startup.
            if !tray::should_start_hidden(&args) {
                tray::show_and_focus_main_window(app);
            }
        }));
    }

    builder
        .plugin(
            tauri_plugin_autostart::Builder::new()
                .args([tray::MINIMIZED_ARG])
                .build(),
        )
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let settings_path = tray::resolve_settings_path(app.handle());
            app.manage(tray::WindowLifecycleState::load_from_path(settings_path));

            tray::setup_system_tray(app)?;
            tray::apply_startup_visibility(app);

            Ok(())
        })
        .on_window_event(|window, event| {
            tray::handle_window_event(window, event);
        })
        .invoke_handler(tauri::generate_handler![
            commands::open_external_url,
            tray::get_window_tray_settings,
            tray::set_close_action,
            tray::set_launch_minimized,
            tray::hide_to_tray,
            tray::show_main_window,
            tray::exit_application,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
