//! GitHub Stable Release Auto-Updater for Nexus Parent Desktop.
//!
//! Hey friend! This module checks `lukagray-dev/Nexus` on GitHub for new **stable** releases:
//! 1. Queries the GitHub Releases API and filters strictly for published, non-prerelease
//!    SemVer tags matching `vX.Y.Z` (ignoring `-alpha` / `-beta` pre-releases).
//! 2. Compares the remote SemVer against the running app version (`CARGO_PKG_VERSION`).
//! 3. When a newer stable release is found, downloads the Windows `.exe` asset into the app's
//!    local data `updates/` directory, verifies its PE `MZ` header and `.sha256` checksum (when
//!    published alongside the release), and emits `app://update-status` (`status: "ready"`).
//! 4. The titlebar shows a "Restart to update" button ONLY when `status == "ready"`.
//! 5. Clicking "Restart to update" swaps the binary after exit and relaunches; closing the app
//!    normally (`File -> Exit`, `Ctrl+Q`, `Tray -> Quit`, or `[X]` with Exit) silently swaps the
//!    binary after exit without relaunching.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Emitter, Manager, Runtime, State};

/// GitHub repository owner & name for Nexus releases.
pub const GITHUB_OWNER: &str = "lukagray-dev";
pub const GITHUB_REPO: &str = "Nexus";

/// Frontend event emitted whenever update state transitions.
pub const EVENT_UPDATE_STATUS: &str = "app://update-status";

/// Update lifecycle status string shared with the TypeScript frontend.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum UpdateStatusKind {
    Idle,
    Checking,
    UpToDate,
    Downloading,
    Ready,
    Error,
}

impl Default for UpdateStatusKind {
    fn default() -> Self {
        Self::Idle
    }
}

/// Public snapshot serialized to the TypeScript frontend via commands & events.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateStatusPayload {
    pub status: UpdateStatusKind,
    pub current_version: String,
    pub available_version: Option<String>,
    pub release_url: Option<String>,
    pub sha256_verified: bool,
    pub error_message: Option<String>,
}

/// Internal record for a downloaded, verified binary waiting for swap on exit/restart.
#[derive(Debug, Clone)]
pub struct StagedUpdate {
    pub version: String,
    pub tag: String,
    pub staged_exe_path: PathBuf,
    pub release_url: String,
    pub sha256_verified: bool,
}

/// Thread-safe Tauri managed state for update discovery and staged binary installation.
pub struct UpdaterState {
    inner: Mutex<UpdaterInner>,
    apply_on_exit_scheduled: AtomicBool,
}

struct UpdaterInner {
    status: UpdateStatusKind,
    available_version: Option<String>,
    release_url: Option<String>,
    sha256_verified: bool,
    error_message: Option<String>,
    staged: Option<StagedUpdate>,
}

impl Default for UpdaterState {
    fn default() -> Self {
        Self {
            inner: Mutex::new(UpdaterInner {
                status: UpdateStatusKind::Idle,
                available_version: None,
                release_url: None,
                sha256_verified: false,
                error_message: None,
                staged: None,
            }),
            apply_on_exit_scheduled: AtomicBool::new(false),
        }
    }
}

impl UpdaterState {
    /// Builds the current status snapshot for the given running application version.
    pub fn snapshot(&self, current_version: &str) -> UpdateStatusPayload {
        match self.inner.lock() {
            Ok(guard) => UpdateStatusPayload {
                status: guard.status,
                current_version: current_version.to_string(),
                available_version: guard.available_version.clone(),
                release_url: guard.release_url.clone(),
                sha256_verified: guard.sha256_verified,
                error_message: guard.error_message.clone(),
            },
            Err(_) => UpdateStatusPayload {
                status: UpdateStatusKind::Error,
                current_version: current_version.to_string(),
                available_version: None,
                release_url: None,
                sha256_verified: false,
                error_message: Some("Updater state lock poisoned".to_string()),
            },
        }
    }

    fn set_status(&self, status: UpdateStatusKind, error_message: Option<String>) {
        if let Ok(mut guard) = self.inner.lock() {
            guard.status = status;
            guard.error_message = error_message;
        }
    }

    fn set_ready(&self, staged: StagedUpdate) {
        if let Ok(mut guard) = self.inner.lock() {
            guard.status = UpdateStatusKind::Ready;
            guard.available_version = Some(staged.version.clone());
            guard.release_url = Some(staged.release_url.clone());
            guard.sha256_verified = staged.sha256_verified;
            guard.error_message = None;
            guard.staged = Some(staged);
        }
    }

    fn set_up_to_date(&self) {
        if let Ok(mut guard) = self.inner.lock() {
            guard.status = UpdateStatusKind::UpToDate;
            guard.available_version = None;
            guard.release_url = None;
            guard.sha256_verified = false;
            guard.error_message = None;
            guard.staged = None;
        }
    }

    fn get_staged(&self) -> Option<StagedUpdate> {
        self.inner.lock().ok().and_then(|g| g.staged.clone())
    }
}

/// Minimal GitHub Release JSON schema returned by `/repos/{owner}/{repo}/releases`.
#[derive(Debug, Clone, Deserialize)]
pub struct GitHubRelease {
    pub tag_name: String,
    pub html_url: String,
    pub draft: bool,
    pub prerelease: bool,
    #[serde(default)]
    pub assets: Vec<GitHubReleaseAsset>,
}

/// Minimal GitHub Release Asset JSON schema.
#[derive(Debug, Clone, Deserialize)]
pub struct GitHubReleaseAsset {
    pub name: String,
    pub browser_download_url: String,
}

/// Parses a running application SemVer string (`"0.1.0-alpha"` -> `(0, 1, 0, true)`).
pub fn parse_app_semver(raw: &str) -> Option<(u64, u64, u64, bool)> {
    let trimmed = raw.trim().strip_prefix('v').unwrap_or(raw.trim());
    let (core, has_prerelease) = match trimmed.split_once('-') {
        Some((main_part, pre)) if !pre.trim().is_empty() => (main_part, true),
        _ => (trimmed, false),
    };

    let mut parts = core.split('.');
    let major: u64 = parts.next()?.parse().ok()?;
    let minor: u64 = parts.next()?.parse().ok()?;
    let patch: u64 = parts.next()?.parse().ok()?;
    if parts.next().is_some() {
        return None;
    }

    Some((major, minor, patch, has_prerelease))
}

/// Parses a strict stable release tag (`"v1.2.3"` or `"1.2.3"`). Rejects pre-release suffixes.
pub fn parse_stable_tag(tag: &str) -> Option<(u64, u64, u64)> {
    let trimmed = tag.trim().strip_prefix('v').unwrap_or(tag.trim());
    if trimmed.contains('-') || trimmed.contains('+') {
        return None;
    }
    let mut parts = trimmed.split('.');
    let major: u64 = parts.next()?.parse().ok()?;
    let minor: u64 = parts.next()?.parse().ok()?;
    let patch: u64 = parts.next()?.parse().ok()?;
    if parts.next().is_some() {
        return None;
    }
    Some((major, minor, patch))
}

/// Returns true when `candidate_tag` represents a strictly newer stable SemVer than `current_raw`.
pub fn is_newer_stable_version(current_raw: &str, candidate_tag: &str) -> bool {
    let Some((cur_maj, cur_min, cur_pat, cur_pre)) = parse_app_semver(current_raw) else {
        return false;
    };
    let Some((cand_maj, cand_min, cand_pat)) = parse_stable_tag(candidate_tag) else {
        return false;
    };

    match (cand_maj, cand_min, cand_pat).cmp(&(cur_maj, cur_min, cur_pat)) {
        std::cmp::Ordering::Greater => true,
        std::cmp::Ordering::Equal => cur_pre,
        std::cmp::Ordering::Less => false,
    }
}

/// Selects the highest-versioned valid stable release from a list of GitHub releases.
pub fn select_latest_stable_release<'a>(releases: &'a [GitHubRelease]) -> Option<&'a GitHubRelease> {
    releases
        .iter()
        .filter(|rel| !rel.draft && !rel.prerelease)
        .filter_map(|rel| parse_stable_tag(&rel.tag_name).map(|ver| (ver, rel)))
        .max_by_key(|(ver, _)| *ver)
        .map(|(_, rel)| rel)
}

/// Selects the Windows desktop `.exe` binary asset and optional `.sha256` checksum asset.
pub fn select_windows_assets(
    release: &GitHubRelease,
) -> Option<(&GitHubReleaseAsset, Option<&GitHubReleaseAsset>)> {
    let binary = release.assets.iter().find(|asset| {
        let lower = asset.name.to_ascii_lowercase();
        lower.ends_with(".exe")
            && (lower.contains("windows-x86_64") || lower.contains("nexus-parent"))
    })?;

    let expected_sha_name = format!("{}.sha256", binary.name).to_ascii_lowercase();
    let checksum = release.assets.iter().find(|asset| {
        let lower = asset.name.to_ascii_lowercase();
        lower == expected_sha_name || (lower.ends_with(".sha256") && lower.contains("nexus-parent"))
    });

    Some((binary, checksum))
}

/// Parses a 64-character lowercase SHA-256 hex digest from standard checksum file text
/// (e.g. `"<64-hex>  nexus-parent-v0.1.0-windows-x86_64.exe"` or bare `"<64-hex>"`).
pub fn parse_sha256_text(raw: &str) -> Option<String> {
    let token = raw.split_whitespace().next()?.trim().to_ascii_lowercase();
    if token.len() == 64 && token.chars().all(|c| c.is_ascii_hexdigit()) {
        Some(token)
    } else {
        None
    }
}

/// Computes the lowercase hex SHA-256 digest for an in-memory byte slice.
pub fn compute_sha256_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

/// Verifies minimum Windows PE executable magic bytes (`MZ`).
pub fn verify_windows_pe_header(bytes: &[u8]) -> bool {
    bytes.len() >= 64 && bytes[0] == b'M' && bytes[1] == b'Z'
}

fn emit_status<R: Runtime>(app: &AppHandle<R>, state: &UpdaterState, current_version: &str) {
    let payload = state.snapshot(current_version);
    let _ = app.emit(EVENT_UPDATE_STATUS, payload);
}

fn resolve_updates_dir<R: Runtime>(app: &AppHandle<R>) -> PathBuf {
    app.path()
        .app_local_data_dir()
        .unwrap_or_else(|_| std::env::temp_dir().join("com.nexus.parent"))
        .join("updates")
}

/// Escapes single quotes inside a PowerShell single-quoted string literal.
fn ps_single_quote(path: &Path) -> String {
    path.to_string_lossy().replace('\'', "''")
}

/// Spawns a detached Windows PowerShell helper that waits for `pid` to exit, replaces `target_exe`
/// with `staged_exe`, cleans up the staged file, and optionally relaunches `target_exe`.
#[cfg(target_os = "windows")]
fn spawn_windows_swap_script(
    pid: u32,
    staged_exe: &Path,
    target_exe: &Path,
    relaunch: bool,
) -> Result<(), String> {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x08000000;

    let staged_q = ps_single_quote(staged_exe);
    let target_q = ps_single_quote(target_exe);
    let relaunch_cmd = if relaunch {
        format!("Start-Process -FilePath '{}' ; ", target_q)
    } else {
        String::new()
    };

    // Wait up to 30 seconds for parent PID termination, then swap file with retries (handles brief AV locks).
    let script = format!(
        "$pidToWait = {pid}; \
        for ($i = 0; $i -lt 150; $i++) {{ \
          if (-not (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue)) {{ break }}; \
          Start-Sleep -Milliseconds 200 \
        }}; \
        $staged = '{staged}'; \
        $target = '{target}'; \
        $swapped = $false; \
        for ($j = 0; $j -lt 15; $j++) {{ \
          try {{ \
            Copy-Item -LiteralPath $staged -Destination $target -Force -ErrorAction Stop; \
            $swapped = $true; \
            break \
          }} catch {{ Start-Sleep -Milliseconds 300 }} \
        }}; \
        if (-not $swapped) {{ \
          try {{ \
            $elevCmd = \"Copy-Item -LiteralPath '$staged' -Destination '$target' -Force\"; \
            Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -Wait -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-Command',$elevCmd) \
          }} catch {{}} \
        }}; \
        Remove-Item -LiteralPath $staged -Force -ErrorAction SilentlyContinue; \
        {relaunch}",
        pid = pid,
        staged = staged_q,
        target = target_q,
        relaunch = relaunch_cmd
    );

    Command::new("powershell.exe")
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-WindowStyle",
            "Hidden",
            "-Command",
            &script,
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .spawn()
        .map_err(|e| format!("Failed to launch update swap helper: {}", e))?;

    Ok(())
}

#[cfg(not(target_os = "windows"))]
fn spawn_windows_swap_script(
    _pid: u32,
    staged_exe: &Path,
    target_exe: &Path,
    relaunch: bool,
) -> Result<(), String> {
    fs::copy(staged_exe, target_exe)
        .map_err(|e| format!("Failed to replace executable: {}", e))?;
    let _ = fs::remove_file(staged_exe);
    if relaunch {
        let _ = Command::new(target_exe).spawn();
    }
    Ok(())
}

/// Schedules installation of any staged update after the current process exits.
/// Safe to invoke multiple times; only spawns the swap helper once per process lifecycle.
pub fn schedule_staged_update_on_exit<R: Runtime>(app: &AppHandle<R>, relaunch: bool) {
    let Some(state) = app.try_state::<UpdaterState>() else {
        return;
    };
    let Some(staged) = state.get_staged() else {
        return;
    };
    if !staged.staged_exe_path.exists() {
        return;
    }

    if state.apply_on_exit_scheduled.swap(true, Ordering::SeqCst) {
        return;
    }

    let Ok(target_exe) = std::env::current_exe() else {
        state.apply_on_exit_scheduled.store(false, Ordering::SeqCst);
        return;
    };

    if let Err(err) =
        spawn_windows_swap_script(std::process::id(), &staged.staged_exe_path, &target_exe, relaunch)
    {
        eprintln!("[Updater] Failed to schedule exit update swap: {}", err);
        state.apply_on_exit_scheduled.store(false, Ordering::SeqCst);
    }
}

/// Tauri command: returns the current application package version string.
#[tauri::command]
pub fn get_app_version<R: Runtime>(app: AppHandle<R>) -> String {
    app.package_info().version.to_string()
}

/// Tauri command: returns the current updater status snapshot.
#[tauri::command]
pub fn get_update_status<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, UpdaterState>,
) -> UpdateStatusPayload {
    let current_version = app.package_info().version.to_string();
    state.snapshot(&current_version)
}

/// Tauri command: queries GitHub stable releases, downloads any newer version, and stages it.
#[tauri::command]
pub async fn check_for_updates<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, UpdaterState>,
    manual: Option<bool>,
) -> Result<UpdateStatusPayload, String> {
    let current_version = app.package_info().version.to_string();
    let is_manual = manual.unwrap_or(false);

    // Avoid overlapping checks or downloads
    {
        let current = state.snapshot(&current_version);
        if matches!(
            current.status,
            UpdateStatusKind::Checking | UpdateStatusKind::Downloading
        ) {
            return Ok(current);
        }
    }

    state.set_status(UpdateStatusKind::Checking, None);
    emit_status(&app, &state, &current_version);

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .user_agent(format!("Nexus-Parent/{}", current_version))
        .build()
        .map_err(|e| format!("Failed to initialize HTTP client: {}", e))?;

    let releases_url = format!(
        "https://api.github.com/repos/{}/{}/releases?per_page=10",
        GITHUB_OWNER, GITHUB_REPO
    );

    let response = match client
        .get(&releases_url)
        .header("Accept", "application/vnd.github+json")
        .send()
        .await
    {
        Ok(resp) => resp,
        Err(err) => {
            let msg = format!("Could not reach GitHub releases: {}", err);
            state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
            emit_status(&app, &state, &current_version);
            return if is_manual {
                Err(msg)
            } else {
                Ok(state.snapshot(&current_version))
            };
        }
    };

    if !response.status().is_success() {
        let msg = format!("GitHub API returned status {}", response.status());
        state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
        emit_status(&app, &state, &current_version);
        return if is_manual {
            Err(msg)
        } else {
            Ok(state.snapshot(&current_version))
        };
    }

    let releases: Vec<GitHubRelease> = match response.json().await {
        Ok(list) => list,
        Err(err) => {
            let msg = format!("Invalid GitHub releases response: {}", err);
            state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
            emit_status(&app, &state, &current_version);
            return if is_manual {
                Err(msg)
            } else {
                Ok(state.snapshot(&current_version))
            };
        }
    };

    let Some(latest) = select_latest_stable_release(&releases) else {
        state.set_up_to_date();
        emit_status(&app, &state, &current_version);
        return Ok(state.snapshot(&current_version));
    };

    if !is_newer_stable_version(&current_version, &latest.tag_name) {
        state.set_up_to_date();
        emit_status(&app, &state, &current_version);
        return Ok(state.snapshot(&current_version));
    }

    let clean_version = latest
        .tag_name
        .trim()
        .strip_prefix('v')
        .unwrap_or(latest.tag_name.trim())
        .to_string();

    // If this exact stable tag is already downloaded and staged on disk, mark ready immediately.
    if let Some(existing) = state.get_staged() {
        if existing.tag == latest.tag_name && existing.staged_exe_path.exists() {
            state.set_ready(existing);
            emit_status(&app, &state, &current_version);
            return Ok(state.snapshot(&current_version));
        }
    }

    let Some((binary_asset, checksum_asset)) = select_windows_assets(latest) else {
        let msg = format!(
            "Stable release {} does not contain a Windows x86_64 binary asset",
            latest.tag_name
        );
        state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
        emit_status(&app, &state, &current_version);
        return if is_manual {
            Err(msg)
        } else {
            Ok(state.snapshot(&current_version))
        };
    };

    state.set_status(UpdateStatusKind::Downloading, None);
    emit_status(&app, &state, &current_version);

    let binary_bytes = match client
        .get(&binary_asset.browser_download_url)
        .send()
        .await
    {
        Ok(resp) if resp.status().is_success() => match resp.bytes().await {
            Ok(b) => b,
            Err(err) => {
                let msg = format!("Failed reading update binary stream: {}", err);
                state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
                emit_status(&app, &state, &current_version);
                return Err(msg);
            }
        },
        Ok(resp) => {
            let msg = format!("Update download returned status {}", resp.status());
            state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
            emit_status(&app, &state, &current_version);
            return Err(msg);
        }
        Err(err) => {
            let msg = format!("Failed downloading update binary: {}", err);
            state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
            emit_status(&app, &state, &current_version);
            return Err(msg);
        }
    };

    if !verify_windows_pe_header(&binary_bytes) {
        let msg = "Downloaded update failed Windows executable header validation".to_string();
        state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
        emit_status(&app, &state, &current_version);
        return Err(msg);
    }

    // Verify cryptographic SHA-256 digest whenever a `.sha256` checksum asset is published.
    let mut sha256_verified = false;
    if let Some(sha_asset) = checksum_asset {
        let sha_resp = client
            .get(&sha_asset.browser_download_url)
            .send()
            .await
            .map_err(|e| format!("Failed downloading SHA-256 checksum: {}", e))?;

        if sha_resp.status().is_success() {
            let sha_text = sha_resp
                .text()
                .await
                .map_err(|e| format!("Failed reading SHA-256 checksum: {}", e))?;
            if let Some(expected_hex) = parse_sha256_text(&sha_text) {
                let actual_hex = compute_sha256_hex(&binary_bytes);
                if actual_hex != expected_hex {
                    let msg = "Update checksum verification failed (SHA-256 mismatch)".to_string();
                    state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
                    emit_status(&app, &state, &current_version);
                    return Err(msg);
                }
                sha256_verified = true;
            }
        }
    }

    let updates_dir = resolve_updates_dir(&app);
    fs::create_dir_all(&updates_dir)
        .map_err(|e| format!("Failed to create updates staging directory: {}", e))?;

    let final_staged_path = updates_dir.join(&binary_asset.name);
    let part_staged_path = updates_dir.join(format!("{}.part", binary_asset.name));

    fs::write(&part_staged_path, &binary_bytes)
        .map_err(|e| format!("Failed to write staged update binary: {}", e))?;

    if let Err(err) = fs::rename(&part_staged_path, &final_staged_path) {
        let _ = fs::remove_file(&part_staged_path);
        return Err(format!("Failed to finalize staged update binary: {}", err));
    }

    state.set_ready(StagedUpdate {
        version: clean_version,
        tag: latest.tag_name.clone(),
        staged_exe_path: final_staged_path,
        release_url: latest.html_url.clone(),
        sha256_verified,
    });
    emit_status(&app, &state, &current_version);

    Ok(state.snapshot(&current_version))
}

/// Tauri command: applies the staged update and immediately relaunches Nexus Parent.
#[tauri::command]
pub fn restart_to_update<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, UpdaterState>,
) -> Result<(), String> {
    let staged = state
        .get_staged()
        .ok_or_else(|| "No downloaded update is staged".to_string())?;

    if !staged.staged_exe_path.exists() {
        return Err("Staged update file no longer exists on disk".to_string());
    }

    schedule_staged_update_on_exit(&app, true);
    crate::tray::request_clean_exit(&app);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_stable_and_prerelease_versions_correctly() {
        assert_eq!(parse_app_semver("0.1.0-alpha"), Some((0, 1, 0, true)));
        assert_eq!(parse_app_semver("v1.2.3"), Some((1, 2, 3, false)));
        assert_eq!(parse_app_semver("bad"), None);

        assert_eq!(parse_stable_tag("v0.1.0"), Some((0, 1, 0)));
        assert_eq!(parse_stable_tag("1.4.2"), Some((1, 4, 2)));
        assert_eq!(parse_stable_tag("v0.1.0-alpha"), None);
        assert_eq!(parse_stable_tag("v0.1.0-alpha.1"), None);
    }

    #[test]
    fn semver_comparison_upgrades_from_alpha_and_ignores_older_or_equal_stable() {
        assert!(is_newer_stable_version("0.1.0-alpha", "v0.1.0"));
        assert!(is_newer_stable_version("0.1.0", "v0.1.1"));
        assert!(is_newer_stable_version("0.1.9", "v0.2.0"));
        assert!(is_newer_stable_version("0.9.9", "v1.0.0"));

        assert!(!is_newer_stable_version("0.1.0", "v0.1.0"));
        assert!(!is_newer_stable_version("0.2.0", "v0.1.9"));
        assert!(!is_newer_stable_version("0.1.0", "v0.2.0-alpha"));
    }

    #[test]
    fn selects_highest_non_prerelease_non_draft_release_and_windows_assets() {
        let releases = vec![
            GitHubRelease {
                tag_name: "v0.2.0-alpha".into(),
                html_url: "https://example.com/pre".into(),
                draft: false,
                prerelease: true,
                assets: vec![],
            },
            GitHubRelease {
                tag_name: "v0.1.0".into(),
                html_url: "https://example.com/v010".into(),
                draft: false,
                prerelease: false,
                assets: vec![],
            },
            GitHubRelease {
                tag_name: "v0.2.0".into(),
                html_url: "https://example.com/v020".into(),
                draft: false,
                prerelease: false,
                assets: vec![
                    GitHubReleaseAsset {
                        name: "nexus-child-v0.2.0.apk".into(),
                        browser_download_url: "https://example.com/child.apk".into(),
                    },
                    GitHubReleaseAsset {
                        name: "nexus-parent-v0.2.0-windows-x86_64.exe".into(),
                        browser_download_url: "https://example.com/parent.exe".into(),
                    },
                    GitHubReleaseAsset {
                        name: "nexus-parent-v0.2.0-windows-x86_64.exe.sha256".into(),
                        browser_download_url: "https://example.com/parent.exe.sha256".into(),
                    },
                ],
            },
            GitHubRelease {
                tag_name: "v9.9.9".into(),
                html_url: "https://example.com/draft".into(),
                draft: true,
                prerelease: false,
                assets: vec![],
            },
        ];

        let latest = select_latest_stable_release(&releases).expect("must find v0.2.0");
        assert_eq!(latest.tag_name, "v0.2.0");

        let (bin, sha) = select_windows_assets(latest).expect("must match exe + sha256");
        assert_eq!(bin.name, "nexus-parent-v0.2.0-windows-x86_64.exe");
        assert_eq!(
            sha.expect("sha asset").name,
            "nexus-parent-v0.2.0-windows-x86_64.exe.sha256"
        );
    }

    #[test]
    fn sha256_parsing_and_pe_header_validation_work_accurately() {
        let sample = b"MZ-valid-pe-header-padding-0000000000000000000000000000000000000000";
        assert!(verify_windows_pe_header(sample));
        assert!(!verify_windows_pe_header(b"<!DOCTYPE html>"));

        let digest = compute_sha256_hex(b"nexus-test");
        let parsed = parse_sha256_text(&format!("{}  nexus-parent.exe\n", digest.to_uppercase()));
        assert_eq!(parsed, Some(digest));
        assert_eq!(parse_sha256_text("not-a-hash"), None);
    }
}
