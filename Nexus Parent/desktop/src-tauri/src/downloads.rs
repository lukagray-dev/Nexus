//! Native Download Directory Picker, Save-As Dialog, and Atomic Chunked File Writer.
//!
//! Hey friend! This module gives the TypeScript frontend real OS-level file saving powers:
//! 1. Opens the native OS folder picker (`pick_downloads_directory`) so parents can pick
//!    where downloaded child files and photos are stored.
//! 2. Opens the native OS Save As dialog (`pick_save_file_path`) when "Ask Every Time
//!    Before Downloading" is enabled.
//! 3. Streams 64KB Base64 chunks directly into a `.nexuspart` temporary file on disk and
//!    atomically renames it when complete — avoiding giant in-memory copies and preventing
//!    corrupt partial files if a WebRTC transfer drops midway.
//! 4. Automatically de-duplicates filenames (`photo.jpg` -> `photo (1).jpg`) when saving
//!    into the default directory so existing files are never silently overwritten.

use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use base64::{engine::general_purpose::STANDARD as BASE64_STANDARD, Engine as _};
use tauri::{AppHandle, Manager, Runtime, State};
use tauri_plugin_dialog::DialogExt;

/// Temporary extension appended while a file transfer is still streaming chunks.
const PARTIAL_SUFFIX: &str = ".nexuspart";

/// Represents an open temporary file handle awaiting remaining chunks.
struct ActiveDownloadFile {
    writer: BufWriter<File>,
    part_path: PathBuf,
    final_path: PathBuf,
}

/// Thread-safe Tauri managed state holding active in-flight file writes keyed by `transferId`.
#[derive(Default)]
pub struct PendingDownloadState {
    transfers: Mutex<HashMap<String, ActiveDownloadFile>>,
}

/// Sanitizes an incoming remote filename so it cannot escape the target directory
/// or contain OS-illegal characters on Windows/macOS/Linux.
pub fn sanitize_file_name(raw_name: &str) -> String {
    let base = raw_name
        .rsplit(['/', '\\'])
        .next()
        .unwrap_or(raw_name)
        .trim();

    let mut cleaned = String::with_capacity(base.len());
    for ch in base.chars() {
        let is_illegal = ch.is_control()
            || matches!(ch, '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*');
        cleaned.push(if is_illegal { '_' } else { ch });
    }

    // Windows filenames cannot end with a space or period.
    let trimmed = cleaned.trim_end_matches([' ', '.']).trim_start();
    if trimmed.is_empty() || trimmed == "." || trimmed == ".." {
        "download.bin".to_string()
    } else {
        trimmed.to_string()
    }
}

/// Appends `.nexuspart` to a final file path to form its temporary staging path.
pub fn build_part_path(final_path: &Path) -> PathBuf {
    let mut os = final_path.as_os_str().to_os_string();
    os.push(PARTIAL_SUFFIX);
    PathBuf::from(os)
}

/// Resolves a collision-free file path inside `dir` (`photo.jpg` -> `photo (1).jpg`, etc.).
pub fn resolve_unique_file_path(dir: &Path, raw_file_name: &str) -> PathBuf {
    let safe_name = sanitize_file_name(raw_file_name);
    let initial = dir.join(&safe_name);
    if !initial.exists() && !build_part_path(&initial).exists() {
        return initial;
    }

    let safe_path = Path::new(&safe_name);
    let stem = safe_path
        .file_stem()
        .and_then(|s| s.to_str())
        .filter(|s| !s.is_empty())
        .unwrap_or("download");
    let ext = safe_path.extension().and_then(|e| e.to_str());

    let mut counter: u32 = 1;
    loop {
        let candidate_name = match ext {
            Some(extension) if !extension.is_empty() => {
                format!("{} ({}).{}", stem, counter, extension)
            }
            _ => format!("{} ({})", stem, counter),
        };
        let candidate = dir.join(candidate_name);
        if !candidate.exists() && !build_part_path(&candidate).exists() {
            return candidate;
        }
        counter = counter.saturating_add(1);
    }
}

/// Expands a leading `~/` or `~\` prefix against `home_dir` if provided.
pub fn expand_directory_input(raw_dir: &str, home_dir: Option<&Path>) -> PathBuf {
    let trimmed = raw_dir.trim();
    if let Some(home) = home_dir {
        if trimmed == "~" {
            return home.to_path_buf();
        }
        if let Some(rest) = trimmed.strip_prefix("~/").or_else(|| trimmed.strip_prefix("~\\")) {
            return home.join(rest);
        }
    }
    PathBuf::from(trimmed)
}

/// Core testable implementation of opening a staged `.nexuspart` download writer.
pub fn begin_download_into_state(
    state: &PendingDownloadState,
    transfer_id: &str,
    target_directory: Option<&Path>,
    exact_file_path: Option<&Path>,
    file_name: &str,
) -> Result<PathBuf, String> {
    let tid = transfer_id.trim();
    if tid.is_empty() {
        return Err("Transfer ID cannot be empty".into());
    }

    let final_path = if let Some(exact) = exact_file_path {
        if let Some(parent) = exact.parent() {
            if !parent.as_os_str().is_empty() {
                fs::create_dir_all(parent)
                    .map_err(|e| format!("Failed to create destination folder: {}", e))?;
            }
        }
        exact.to_path_buf()
    } else if let Some(dir) = target_directory {
        fs::create_dir_all(dir)
            .map_err(|e| format!("Failed to create downloads directory: {}", e))?;
        resolve_unique_file_path(dir, file_name)
    } else {
        return Err("Neither targetDirectory nor exactFilePath was provided".into());
    };

    let part_path = build_part_path(&final_path);
    let file = OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(&part_path)
        .map_err(|e| format!("Failed to create temporary download file: {}", e))?;

    let mut guard = state
        .transfers
        .lock()
        .map_err(|_| "Failed to lock download transfer map".to_string())?;

    // Clean up any stale handle if the same transfer ID is reused.
    if let Some(stale) = guard.remove(tid) {
        drop(stale.writer);
        let _ = fs::remove_file(&stale.part_path);
    }

    guard.insert(
        tid.to_string(),
        ActiveDownloadFile {
            writer: BufWriter::new(file),
            part_path,
            final_path: final_path.clone(),
        },
    );

    Ok(final_path)
}

/// Core testable implementation of decoding and appending a Base64 chunk.
pub fn append_chunk_into_state(
    state: &PendingDownloadState,
    transfer_id: &str,
    data_base64: &str,
) -> Result<(), String> {
    let bytes = BASE64_STANDARD
        .decode(data_base64.trim())
        .map_err(|e| format!("Invalid Base64 chunk data: {}", e))?;

    let mut guard = state
        .transfers
        .lock()
        .map_err(|_| "Failed to lock download transfer map".to_string())?;

    let active = guard
        .get_mut(transfer_id.trim())
        .ok_or_else(|| format!("Unknown download transfer ID: {}", transfer_id))?;

    active
        .writer
        .write_all(&bytes)
        .map_err(|e| format!("Failed to write download chunk to disk: {}", e))?;

    Ok(())
}

/// Core testable implementation of flushing and atomically renaming `.nexuspart` -> final path.
pub fn commit_download_in_state(
    state: &PendingDownloadState,
    transfer_id: &str,
) -> Result<PathBuf, String> {
    let active = {
        let mut guard = state
            .transfers
            .lock()
            .map_err(|_| "Failed to lock download transfer map".to_string())?;
        guard
            .remove(transfer_id.trim())
            .ok_or_else(|| format!("Unknown download transfer ID: {}", transfer_id))?
    };

    let ActiveDownloadFile {
        mut writer,
        part_path,
        final_path,
    } = active;

    if let Err(err) = writer.flush() {
        drop(writer);
        let _ = fs::remove_file(&part_path);
        return Err(format!("Failed to flush download buffer: {}", err));
    }
    // Close the OS file handle before renaming (required on Windows).
    drop(writer);

    if let Err(err) = fs::rename(&part_path, &final_path) {
        let _ = fs::remove_file(&part_path);
        return Err(format!("Failed to finalize downloaded file: {}", err));
    }

    Ok(final_path)
}

/// Core testable implementation of cancelling and deleting a staged `.nexuspart` file.
pub fn cancel_download_in_state(state: &PendingDownloadState, transfer_id: &str) -> Result<(), String> {
    let removed = {
        let mut guard = state
            .transfers
            .lock()
            .map_err(|_| "Failed to lock download transfer map".to_string())?;
        guard.remove(transfer_id.trim())
    };

    if let Some(active) = removed {
        drop(active.writer);
        let _ = fs::remove_file(&active.part_path);
    }

    Ok(())
}

/// Tauri command: resolves the OS user Downloads folder joined with `Nexus` as a suggested path.
#[tauri::command]
pub fn get_default_downloads_directory<R: Runtime>(app: AppHandle<R>) -> Result<String, String> {
    let base = app
        .path()
        .download_dir()
        .map_err(|e| format!("Failed to locate OS Downloads directory: {}", e))?;
    Ok(base.join("Nexus").to_string_lossy().to_string())
}

/// Tauri command: opens the native OS folder picker dialog without blocking the UI thread.
#[tauri::command]
pub async fn pick_downloads_directory<R: Runtime>(
    app: AppHandle<R>,
    initial_dir: Option<String>,
) -> Result<Option<String>, String> {
    let home = app.path().home_dir().ok();
    let fallback_dir = app.path().download_dir().ok();

    let start_dir = initial_dir
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| expand_directory_input(s, home.as_deref()))
        .filter(|p| p.is_dir())
        .or_else(|| fallback_dir.filter(|p| p.is_dir()));

    let picked = tauri::async_runtime::spawn_blocking(move || {
        let mut dialog = app
            .dialog()
            .file()
            .set_title("Select Default Downloads Folder");

        if let Some(dir) = start_dir {
            dialog = dialog.set_directory(dir);
        }

        dialog.blocking_pick_folder()
    })
    .await
    .map_err(|e| format!("Folder picker task failed: {}", e))?;

    match picked {
        Some(file_path) => {
            let path_buf = file_path
                .into_path()
                .map_err(|e| format!("Selected folder is not a local filesystem path: {}", e))?;
            Ok(Some(path_buf.to_string_lossy().to_string()))
        }
        None => Ok(None),
    }
}

/// Tauri command: opens the native OS Save As dialog without blocking the UI thread.
#[tauri::command]
pub async fn pick_save_file_path<R: Runtime>(
    app: AppHandle<R>,
    suggested_name: String,
    initial_dir: Option<String>,
) -> Result<Option<String>, String> {
    let home = app.path().home_dir().ok();
    let fallback_dir = app.path().download_dir().ok();

    let start_dir = initial_dir
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| expand_directory_input(s, home.as_deref()))
        .filter(|p| p.is_dir())
        .or_else(|| fallback_dir.filter(|p| p.is_dir()));

    let safe_name = sanitize_file_name(&suggested_name);

    let picked = tauri::async_runtime::spawn_blocking(move || {
        let mut dialog = app
            .dialog()
            .file()
            .set_title("Save Downloaded File As")
            .set_file_name(&safe_name);

        if let Some(dir) = start_dir {
            dialog = dialog.set_directory(dir);
        }

        dialog.blocking_save_file()
    })
    .await
    .map_err(|e| format!("Save As dialog task failed: {}", e))?;

    match picked {
        Some(file_path) => {
            let path_buf = file_path
                .into_path()
                .map_err(|e| format!("Selected destination is not a local filesystem path: {}", e))?;
            Ok(Some(path_buf.to_string_lossy().to_string()))
        }
        None => Ok(None),
    }
}

/// Tauri command: prepares a staged `.nexuspart` file on disk for an incoming download.
#[tauri::command]
pub fn begin_download_file<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, PendingDownloadState>,
    transfer_id: String,
    target_directory: Option<String>,
    exact_file_path: Option<String>,
    file_name: String,
) -> Result<String, String> {
    let home = app.path().home_dir().ok();
    let dir_path = target_directory
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| expand_directory_input(s, home.as_deref()));

    let exact_path = exact_file_path
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(PathBuf::from);

    let final_path = begin_download_into_state(
        &state,
        &transfer_id,
        dir_path.as_deref(),
        exact_path.as_deref(),
        &file_name,
    )?;

    Ok(final_path.to_string_lossy().to_string())
}

/// Tauri command: decodes a Base64 chunk and appends it to the active staged file.
#[tauri::command]
pub fn append_download_chunk(
    state: State<'_, PendingDownloadState>,
    transfer_id: String,
    data_base64: String,
) -> Result<(), String> {
    append_chunk_into_state(&state, &transfer_id, &data_base64)
}

/// Tauri command: flushes and atomically commits the staged `.nexuspart` file to disk.
#[tauri::command]
pub fn commit_download_file(
    state: State<'_, PendingDownloadState>,
    transfer_id: String,
) -> Result<String, String> {
    let final_path = commit_download_in_state(&state, &transfer_id)?;
    Ok(final_path.to_string_lossy().to_string())
}

/// Tauri command: aborts an active file write and deletes any temporary `.nexuspart` file.
#[tauri::command]
pub fn cancel_download_file(
    state: State<'_, PendingDownloadState>,
    transfer_id: String,
) -> Result<(), String> {
    cancel_download_in_state(&state, &transfer_id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn unique_temp_dir(label: &str) -> PathBuf {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("system time after epoch")
            .as_nanos();
        std::env::temp_dir().join(format!(
            "nexus_downloads_test_{}_{}_{}",
            label,
            nanos,
            std::process::id()
        ))
    }

    #[test]
    fn sanitize_file_name_removes_traversal_and_illegal_chars() {
        assert_eq!(sanitize_file_name("../../evil/photo?.jpg"), "photo_.jpg");
        assert_eq!(sanitize_file_name("C:\\temp\\a<b>c:d.png"), "a_b_c_d.png");
        assert_eq!(sanitize_file_name("   ...   "), "download.bin");
        assert_eq!(sanitize_file_name("family_pic.jpg"), "family_pic.jpg");
    }

    #[test]
    fn resolve_unique_file_path_auto_increments_collisions() {
        let dir = unique_temp_dir("collision");
        fs::create_dir_all(&dir).expect("create temp dir");

        let first = resolve_unique_file_path(&dir, "photo.jpg");
        assert_eq!(first, dir.join("photo.jpg"));
        fs::write(&first, b"first").expect("write first");

        let second = resolve_unique_file_path(&dir, "photo.jpg");
        assert_eq!(second, dir.join("photo (1).jpg"));
        fs::write(&second, b"second").expect("write second");

        let third = resolve_unique_file_path(&dir, "photo.jpg");
        assert_eq!(third, dir.join("photo (2).jpg"));

        // Extensionless file increments cleanly as well
        let no_ext_1 = resolve_unique_file_path(&dir, "README");
        assert_eq!(no_ext_1, dir.join("README"));
        fs::write(&no_ext_1, b"readme").expect("write readme");
        let no_ext_2 = resolve_unique_file_path(&dir, "README");
        assert_eq!(no_ext_2, dir.join("README (1)"));

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn chunked_atomic_write_creates_final_file_and_cleans_part() {
        let dir = unique_temp_dir("atomic");
        let state = PendingDownloadState::default();

        let planned = begin_download_into_state(
            &state,
            "tx-1",
            Some(&dir),
            None,
            "report.txt",
        )
        .expect("begin download");

        assert_eq!(planned, dir.join("report.txt"));
        assert!(build_part_path(&planned).exists());
        assert!(!planned.exists());

        let chunk1 = BASE64_STANDARD.encode(b"Hello, ");
        let chunk2 = BASE64_STANDARD.encode(b"Nexus!");
        append_chunk_into_state(&state, "tx-1", &chunk1).expect("chunk 1");
        append_chunk_into_state(&state, "tx-1", &chunk2).expect("chunk 2");

        let committed = commit_download_in_state(&state, "tx-1").expect("commit");
        assert_eq!(committed, planned);
        assert!(committed.exists());
        assert!(!build_part_path(&committed).exists());
        assert_eq!(fs::read(&committed).expect("read final"), b"Hello, Nexus!");

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn cancel_download_deletes_partial_file() {
        let dir = unique_temp_dir("cancel");
        let state = PendingDownloadState::default();

        let planned = begin_download_into_state(
            &state,
            "tx-cancel",
            Some(&dir),
            None,
            "video.mp4",
        )
        .expect("begin download");

        let chunk = BASE64_STANDARD.encode(b"partial-bytes");
        append_chunk_into_state(&state, "tx-cancel", &chunk).expect("write chunk");
        assert!(build_part_path(&planned).exists());

        cancel_download_in_state(&state, "tx-cancel").expect("cancel");
        assert!(!build_part_path(&planned).exists());
        assert!(!planned.exists());

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn exact_file_path_writes_directly_to_chosen_destination() {
        let dir = unique_temp_dir("exact");
        let exact = dir.join("nested").join("custom-name.jpg");
        let state = PendingDownloadState::default();

        let planned = begin_download_into_state(
            &state,
            "tx-exact",
            None,
            Some(&exact),
            "ignored.jpg",
        )
        .expect("begin exact");

        assert_eq!(planned, exact);
        append_chunk_into_state(&state, "tx-exact", &BASE64_STANDARD.encode(b"IMG"))
            .expect("chunk");
        let committed = commit_download_in_state(&state, "tx-exact").expect("commit");
        assert_eq!(committed, exact);
        assert_eq!(fs::read(&exact).expect("read exact"), b"IMG");

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn expand_directory_input_resolves_tilde_prefix() {
        let home = PathBuf::from("C:\\Users\\Tester");
        assert_eq!(
            expand_directory_input("~/Downloads/Nexus", Some(&home)),
            home.join("Downloads/Nexus")
        );
        assert_eq!(
            expand_directory_input("~\\Pictures", Some(&home)),
            home.join("Pictures")
        );
        assert_eq!(
            expand_directory_input("D:\\Custom", Some(&home)),
            PathBuf::from("D:\\Custom")
        );
    }
}
