//! Native Rust Backend Entrypoint for Nexus Parent Desktop.
//!
//! Hey friend! This initializes the Tauri v2 desktop application runner:
//! - Enforces single-instance execution so secondary launches focus the existing tray window.
//! - Initializes OS autostart, external URL opener, and native file dialog plugins.
//! - Configures the native system tray icon, menu, Always-on-Top, and window close-to-tray lifecycle.
//! - Manages display wake locks (`power.rs`) and GitHub stable release auto-updates (`updater.rs`).

pub mod commands;
pub mod downloads;
pub mod power;
pub mod tray;
pub mod updater;

use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tray::configure_gpu_acceleration_before_launch();
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
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .manage(downloads::PendingDownloadState::default())
        .manage(power::KeepAwakeState::default())
        .manage(updater::UpdaterState::default())
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
            commands::get_parent_system_info,
            commands::open_local_folder,
            commands::show_desktop_notification,
            downloads::get_default_downloads_directory,
            downloads::pick_downloads_directory,
            downloads::pick_save_file_path,
            downloads::begin_download_file,
            downloads::append_download_chunk,
            downloads::commit_download_file,
            downloads::cancel_download_file,
            power::set_keep_screen_awake,
            updater::get_app_version,
            updater::get_update_status,
            updater::check_for_updates,
            updater::restart_to_update,
            tray::get_window_tray_settings,
            tray::set_close_action,
            tray::set_launch_on_startup,
            tray::set_start_minimized,
            tray::set_always_on_top,
            tray::set_hardware_acceleration,
            tray::restart_application,
            tray::hide_to_tray,
            tray::show_main_window,
            tray::exit_application,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
