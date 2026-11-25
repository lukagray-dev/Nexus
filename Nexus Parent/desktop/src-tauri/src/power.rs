//! Display & System Power Wake-Lock Manager for Nexus Parent Desktop.
//!
//! Hey friend! When the parent is actively connected to a child device, Windows/macOS/Linux
//! might otherwise turn off the display or put the PC to sleep after idle timeout — dropping
//! the live WebRTC connection and background monitoring!
//!
//! Why `keepawake` instead of raw Win32 `SetThreadExecutionState`?
//! `SetThreadExecutionState` is thread-local on Windows, so calling it from different async
//! Tokio worker threads in Tauri commands would fail to clear the lock reliably. `keepawake`
//! manages the OS power assertion safely across threads via a single RAII guard handle!

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

use tauri::State;

/// Thread-safe Tauri managed state holding the active OS wake-lock guard.
#[derive(Default)]
pub struct KeepAwakeState {
    guard: Mutex<Option<keepawake::KeepAwake>>,
    active: AtomicBool,
}

impl KeepAwakeState {
    /// Returns true if a wake lock is currently held by this application.
    pub fn is_active(&self) -> bool {
        self.active.load(Ordering::SeqCst)
    }

    /// Enables or disables the display + idle sleep wake lock.
    pub fn set_enabled(&self, enabled: bool) -> Result<bool, String> {
        let mut guard = self
            .guard
            .lock()
            .map_err(|_| "Failed to acquire power wake-lock state".to_string())?;

        if enabled {
            if guard.is_none() {
                let awake = keepawake::Builder::default()
                    .display(true)
                    .idle(true)
                    .reason("Nexus Parent connected to child device")
                    .app_name("Nexus Parent")
                    .create()
                    .map_err(|e| format!("Failed to activate display wake lock: {}", e))?;
                *guard = Some(awake);
            }
            self.active.store(true, Ordering::SeqCst);
            Ok(true)
        } else {
            // Dropping the KeepAwake guard immediately restores normal OS sleep behavior.
            *guard = None;
            self.active.store(false, Ordering::SeqCst);
            Ok(false)
        }
    }
}

/// Tauri command: activates or releases the OS display wake lock.
#[tauri::command]
pub fn set_keep_screen_awake(
    enabled: bool,
    state: State<'_, KeepAwakeState>,
) -> Result<bool, String> {
    state.set_enabled(enabled)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keep_awake_state_starts_inactive_and_releases_cleanly() {
        let state = KeepAwakeState::default();
        assert!(!state.is_active());

        // Disabling when already disabled is always a clean no-op
        assert_eq!(state.set_enabled(false).expect("disable no-op"), false);
        assert!(!state.is_active());
    }
}
