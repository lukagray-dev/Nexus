//! GitHub Stable Release Auto-Updater for Nexus Parent Desktop.
//!
//! Hey friend! This module checks `lukagray-dev/Nexus` on GitHub for new **stable** releases:
//! 1. Queries the GitHub Releases API and filters strictly for published, non-prerelease
//!    SemVer tags matching `vX.Y.Z` (ignoring `-alpha` / `-beta` pre-releases).
//! 2. Compares the remote SemVer against the running app version (`CARGO_PKG_VERSION`).
//! 3. Detects the running Windows CPU architecture (`x86_64` vs `arm64`) AND installation mode
//!    (`portable`, `nsis`, or `msi`) so the updater downloads the exact matching artifact:
//!    - Portable `.exe` -> verifies PE `MZ` + exact `.sha256`, swaps binary after exit.
//!    - NSIS `*-setup.exe` -> verifies PE `MZ` + exact `.sha256`, runs silent `/S` upgrade after exit.
//!    - MSI `*.msi` -> verifies OLE2 header + exact `.sha256`, runs `msiexec /i ... /passive` after exit.
//! 4. The titlebar shows a "Restart to update" button ONLY when `status == "ready"`.
//! 5. Clicking "Restart to update" applies the staged update and relaunches; closing the app
//!    normally (`File -> Exit`, `Ctrl+Q`, `Tray -> Quit`, or `[X]` with Exit) silently applies the
//!    staged update after exit without relaunching.

use std::fs::{self, File};
use std::io::{BufWriter, Write};
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

/// Supported Windows CPU architectures for Nexus Parent Desktop releases.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum DesktopArch {
    X86_64,
    Arm64,
}

impl DesktopArch {
    /// Resolves a `DesktopArch` from `std::env::consts::ARCH` strings.
    pub fn from_rust_arch(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_lowercase().as_str() {
            "x86_64" | "amd64" => Some(Self::X86_64),
            "aarch64" | "arm64" => Some(Self::Arm64),
            _ => None,
        }
    }

    /// Returns the currently executing binary's `DesktopArch`.
    pub fn current() -> Self {
        Self::from_rust_arch(std::env::consts::ARCH).unwrap_or(Self::X86_64)
    }

    /// Returns the standardized release asset architecture token.
    pub fn asset_token(&self) -> &'static str {
        match self {
            Self::X86_64 => "windows-x86_64",
            Self::Arm64 => "windows-arm64",
        }
    }

    /// Returns a concise human-readable architecture identifier.
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::X86_64 => "x86_64",
            Self::Arm64 => "arm64",
        }
    }
}

/// How the running Nexus Parent Desktop binary was installed on Windows.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum WindowsInstallKind {
    Portable,
    Nsis,
    Msi,
}

impl Default for WindowsInstallKind {
    fn default() -> Self {
        Self::Portable
    }
}

impl WindowsInstallKind {
    /// Returns the kebab-case identifier exposed to the frontend diagnostics & status payloads.
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Portable => "portable",
            Self::Nsis => "nsis",
            Self::Msi => "msi",
        }
    }
}

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
    pub arch: String,
    pub install_kind: WindowsInstallKind,
}

/// Internal record for a downloaded, verified binary or installer waiting for exit/restart.
#[derive(Debug, Clone)]
pub struct StagedUpdate {
    pub version: String,
    pub tag: String,
    pub staged_path: PathBuf,
    pub release_url: String,
    pub sha256_verified: bool,
    pub install_kind: WindowsInstallKind,
}

/// Thread-safe Tauri managed state for update discovery and staged installation.
pub struct UpdaterState {
    inner: Mutex<UpdaterInner>,
    apply_on_exit_scheduled: AtomicBool,
    detected_install_kind: WindowsInstallKind,
    detected_arch: DesktopArch,
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
            detected_install_kind: detect_current_windows_install_kind(),
            detected_arch: DesktopArch::current(),
        }
    }
}

impl UpdaterState {
    /// Returns the detected installation kind (`portable`, `nsis`, or `msi`).
    pub fn install_kind(&self) -> WindowsInstallKind {
        self.detected_install_kind
    }

    /// Returns the detected CPU architecture (`x86_64` or `arm64`).
    pub fn arch(&self) -> DesktopArch {
        self.detected_arch
    }

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
                arch: self.detected_arch.as_str().to_string(),
                install_kind: self.detected_install_kind,
            },
            Err(_) => UpdateStatusPayload {
                status: UpdateStatusKind::Error,
                current_version: current_version.to_string(),
                available_version: None,
                release_url: None,
                sha256_verified: false,
                error_message: Some("Updater state lock poisoned".to_string()),
                arch: self.detected_arch.as_str().to_string(),
                install_kind: self.detected_install_kind,
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

/// Normalizes a filesystem path string for case-insensitive Windows comparisons.
fn normalize_windows_path_for_compare(raw: &str) -> String {
    raw.trim()
        .trim_matches('"')
        .trim_end_matches(['\\', '/'])
        .replace('/', "\\")
        .to_ascii_lowercase()
}

/// Pure classifier determining whether a Windows executable was installed via NSIS, MSI, or Portable.
///
/// Hey friend! Keeping this decision pure lets us test every edge case deterministically:
/// - NSIS installers always drop `uninstall.exe` in the same folder as `nexus-parent.exe`
///   and/or register an uninstaller entry pointing inside that directory.
/// - MSI installers place files without `uninstall.exe` and register an `MsiExec` UninstallString
///   alongside `InstallLocation` matching the executable directory.
/// - Anything else (e.g., dropped directly in Downloads/Desktop/USB) is `Portable`.
pub fn classify_windows_install_kind(
    exe_dir_has_uninstaller: bool,
    registry_entries: &[(String, String)],
    exe_dir_raw: &str,
) -> WindowsInstallKind {
    let norm_exe_dir = normalize_windows_path_for_compare(exe_dir_raw);

    for (install_location, uninstall_string) in registry_entries {
        let norm_loc = normalize_windows_path_for_compare(install_location);
        let lower_uninstall = uninstall_string.to_ascii_lowercase();
        let location_matches = !norm_exe_dir.is_empty()
            && (!norm_loc.is_empty() && norm_loc == norm_exe_dir
                || normalize_windows_path_for_compare(uninstall_string).starts_with(&norm_exe_dir));

        if location_matches {
            if lower_uninstall.contains("msiexec") {
                return WindowsInstallKind::Msi;
            }
            if lower_uninstall.contains("uninstall") {
                return WindowsInstallKind::Nsis;
            }
        }
    }

    if exe_dir_has_uninstaller {
        return WindowsInstallKind::Nsis;
    }

    // Fallback check when MSI did not populate InstallLocation cleanly but exe lives under Program Files
    if !norm_exe_dir.is_empty()
        && registry_entries
            .iter()
            .any(|(_, u)| u.to_ascii_lowercase().contains("msiexec"))
        && norm_exe_dir.contains("\\nexus parent")
    {
        return WindowsInstallKind::Msi;
    }

    WindowsInstallKind::Portable
}

/// Queries Windows Uninstall registry keys silently (`CREATE_NO_WINDOW`) for `Nexus Parent` entries.
#[cfg(target_os = "windows")]
fn query_windows_uninstall_entries() -> Vec<(String, String)> {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x08000000;

    let roots = [
        r"HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        r"HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        r"HKLM\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall",
    ];

    let mut entries = Vec::new();
    for root in roots {
        let output = Command::new("reg")
            .args(["query", root, "/s", "/f", "Nexus Parent", "/d"])
            .creation_flags(CREATE_NO_WINDOW)
            .output();

        let Ok(out) = output else {
            continue;
        };
        if !out.status.success() {
            continue;
        }
        let stdout = String::from_utf8_lossy(&out.stdout);
        for line in stdout.lines() {
            let trimmed = line.trim();
            if !trimmed.starts_with("HK") {
                continue;
            }
            let sub_out = Command::new("reg")
                .args(["query", trimmed])
                .creation_flags(CREATE_NO_WINDOW)
                .output();
            let Ok(sub) = sub_out else {
                continue;
            };
            if !sub.status.success() {
                continue;
            }
            let sub_text = String::from_utf8_lossy(&sub.stdout);
            let mut install_loc = String::new();
            let mut uninstall_str = String::new();
            for sub_line in sub_text.lines() {
                let s = sub_line.trim();
                if let Some(rest) = s.strip_prefix("InstallLocation") {
                    if let Some((_, val)) = rest.split_once("REG_SZ") {
                        install_loc = val.trim().to_string();
                    }
                } else if let Some(rest) = s.strip_prefix("UninstallString") {
                    if let Some((_, val)) = rest.split_once("REG_SZ") {
                        uninstall_str = val.trim().to_string();
                    } else if let Some((_, val)) = rest.split_once("REG_EXPAND_SZ") {
                        uninstall_str = val.trim().to_string();
                    }
                }
            }
            if !install_loc.is_empty() || !uninstall_str.is_empty() {
                entries.push((install_loc, uninstall_str));
            }
        }
    }
    entries
}

#[cfg(not(target_os = "windows"))]
fn query_windows_uninstall_entries() -> Vec<(String, String)> {
    Vec::new()
}

/// Detects the installation mode of the currently running Nexus Parent Desktop executable.
pub fn detect_current_windows_install_kind() -> WindowsInstallKind {
    let Ok(exe_path) = std::env::current_exe() else {
        return WindowsInstallKind::Portable;
    };
    let Some(exe_dir) = exe_path.parent() else {
        return WindowsInstallKind::Portable;
    };

    let has_uninstaller = exe_dir.join("uninstall.exe").exists();
    let entries = query_windows_uninstall_entries();
    let exe_dir_str = exe_dir.to_string_lossy().to_string();
    classify_windows_install_kind(has_uninstaller, &entries, &exe_dir_str)
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

/// Matches the exact Windows asset + strict `<asset>.sha256` for the given architecture & install kind.
///
/// Rules:
/// - Must belong to `nexus-parent` and match `arch.asset_token()` (`windows-x86_64` or `windows-arm64`).
/// - `Portable`: prefers `*-portable.exe`, falling back to legacy `*-windows-x86_64.exe` ONLY for `X86_64`
///   and strictly excluding `*-setup.exe` installers.
/// - `Nsis`: strictly matches `*-setup.exe`.
/// - `Msi`: strictly matches `*.msi` (falling back to `*-setup.exe` on ARM64 if no ARM64 `.msi` exists).
/// - Checksum: strictly `<exact-asset.name>.sha256` (never wildcard-matches another asset's checksum).
pub fn select_windows_assets_for<'a>(
    release: &'a GitHubRelease,
    arch: DesktopArch,
    install_kind: WindowsInstallKind,
) -> Option<(&'a GitHubReleaseAsset, Option<&'a GitHubReleaseAsset>)> {
    let arch_token = arch.asset_token();

    let matches_base = |asset: &GitHubReleaseAsset| -> bool {
        let lower = asset.name.to_ascii_lowercase();
        lower.contains("nexus-parent") && lower.contains(arch_token) && !lower.ends_with(".sha256")
    };

    let binary = match install_kind {
        WindowsInstallKind::Portable => release
            .assets
            .iter()
            .find(|a| matches_base(a) && a.name.to_ascii_lowercase().ends_with("-portable.exe"))
            .or_else(|| {
                if arch == DesktopArch::X86_64 {
                    release.assets.iter().find(|a| {
                        let lower = a.name.to_ascii_lowercase();
                        matches_base(a)
                            && lower.ends_with(".exe")
                            && !lower.ends_with("-setup.exe")
                            && !lower.contains("setup")
                    })
                } else {
                    None
                }
            })?,
        WindowsInstallKind::Nsis => release
            .assets
            .iter()
            .find(|a| matches_base(a) && a.name.to_ascii_lowercase().ends_with("-setup.exe"))?,
        WindowsInstallKind::Msi => release
            .assets
            .iter()
            .find(|a| matches_base(a) && a.name.to_ascii_lowercase().ends_with(".msi"))
            .or_else(|| {
                if arch == DesktopArch::Arm64 {
                    release.assets.iter().find(|a| {
                        matches_base(a) && a.name.to_ascii_lowercase().ends_with("-setup.exe")
                    })
                } else {
                    None
                }
            })?,
    };

    let expected_sha_name = format!("{}.sha256", binary.name).to_ascii_lowercase();
    let checksum = release
        .assets
        .iter()
        .find(|asset| asset.name.to_ascii_lowercase() == expected_sha_name);

    Some((binary, checksum))
}

/// Backward-compatible convenience selector defaulting to current architecture & Portable mode.
pub fn select_windows_assets(
    release: &GitHubRelease,
) -> Option<(&GitHubReleaseAsset, Option<&GitHubReleaseAsset>)> {
    select_windows_assets_for(release, DesktopArch::current(), WindowsInstallKind::Portable)
}

/// Parses a 64-character lowercase SHA-256 hex digest from standard checksum file text
/// (e.g. `"<64-hex>  nexus-parent-v0.1.0-windows-x86_64-portable.exe"` or bare `"<64-hex>"`).
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

/// Verifies minimum Windows MSI (OLE2 Compound File) magic bytes (`D0 CF 11 E0 A1 B1 1A E1`).
pub fn verify_windows_msi_header(bytes: &[u8]) -> bool {
    const OLE2_MAGIC: [u8; 8] = [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];
    bytes.len() >= 512 && bytes[..8] == OLE2_MAGIC
}

/// Validates downloaded payload header bytes against the expected asset extension (`.msi` vs `.exe`).
pub fn verify_windows_payload_header(asset_name: &str, bytes: &[u8]) -> bool {
    if asset_name.to_ascii_lowercase().ends_with(".msi") {
        verify_windows_msi_header(bytes)
    } else {
        verify_windows_pe_header(bytes)
    }
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

/// Spawns a detached Windows PowerShell helper that waits for `pid` to exit, applies the staged
/// update according to `install_kind` (file swap for Portable, `/S` for NSIS, `msiexec` for MSI),
/// cleans up the staged file, and optionally relaunches `target_exe`.
#[cfg(target_os = "windows")]
fn spawn_windows_apply_script(
    pid: u32,
    staged_path: &Path,
    target_exe: &Path,
    install_kind: WindowsInstallKind,
    relaunch: bool,
) -> Result<(), String> {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x08000000;

    let staged_q = ps_single_quote(staged_path);
    let target_q = ps_single_quote(target_exe);
    let relaunch_cmd = if relaunch {
        format!("if (Test-Path -LiteralPath '{target_q}') {{ Start-Process -FilePath '{target_q}' }} ; ")
    } else {
        String::new()
    };

    let apply_body = match install_kind {
        WindowsInstallKind::Portable => {
            "$swapped = $false; \
            for ($j = 0; $j -lt 15; $j++) { \
              try { \
                Copy-Item -LiteralPath $staged -Destination $target -Force -ErrorAction Stop; \
                $swapped = $true; \
                break \
              } catch { Start-Sleep -Milliseconds 300 } \
            }; \
            if (-not $swapped) { \
              try { \
                $elevCmd = \"Copy-Item -LiteralPath '$staged' -Destination '$target' -Force\"; \
                Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -Wait -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-Command',$elevCmd) \
              } catch {} \
            };"
        }
        WindowsInstallKind::Nsis => {
            if staged_path
                .extension()
                .and_then(|e| e.to_str())
                .map(|e| e.eq_ignore_ascii_case("msi"))
                .unwrap_or(false)
            {
                "try { \
                  Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', $staged, '/passive', '/norestart') -Wait -ErrorAction Stop \
                } catch {};"
            } else {
                "try { \
                  Start-Process -FilePath $staged -ArgumentList @('/S') -Wait -ErrorAction Stop \
                } catch { \
                  try { Start-Process -FilePath $staged -ArgumentList @('/S') -Verb RunAs -Wait } catch {} \
                };"
            }
        }
        WindowsInstallKind::Msi => {
            if staged_path
                .extension()
                .and_then(|e| e.to_str())
                .map(|e| e.eq_ignore_ascii_case("exe"))
                .unwrap_or(false)
            {
                "try { \
                  Start-Process -FilePath $staged -ArgumentList @('/S') -Wait -ErrorAction Stop \
                } catch { \
                  try { Start-Process -FilePath $staged -ArgumentList @('/S') -Verb RunAs -Wait } catch {} \
                };"
            } else {
                "try { \
                  Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', $staged, '/passive', '/norestart') -Wait -ErrorAction Stop \
                } catch {};"
            }
        }
    };

    // Wait up to 30 seconds for parent PID termination before swapping or running installer.
    let script = format!(
        "$pidToWait = {pid}; \
        for ($i = 0; $i -lt 150; $i++) {{ \
          if (-not (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue)) {{ break }}; \
          Start-Sleep -Milliseconds 200 \
        }}; \
        $staged = '{staged}'; \
        $target = '{target}'; \
        {apply_body} \
        Remove-Item -LiteralPath $staged -Force -ErrorAction SilentlyContinue; \
        {relaunch}",
        pid = pid,
        staged = staged_q,
        target = target_q,
        apply_body = apply_body,
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
        .map_err(|e| format!("Failed to launch update apply helper: {}", e))?;

    Ok(())
}

#[cfg(not(target_os = "windows"))]
fn spawn_windows_apply_script(
    _pid: u32,
    staged_path: &Path,
    target_exe: &Path,
    _install_kind: WindowsInstallKind,
    relaunch: bool,
) -> Result<(), String> {
    fs::copy(staged_path, target_exe)
        .map_err(|e| format!("Failed to replace executable: {}", e))?;
    let _ = fs::remove_file(staged_path);
    if relaunch {
        let _ = Command::new(target_exe).spawn();
    }
    Ok(())
}

/// Schedules installation of any staged update after the current process exits.
/// Safe to invoke multiple times; only spawns the apply helper once per process lifecycle.
pub fn schedule_staged_update_on_exit<R: Runtime>(app: &AppHandle<R>, relaunch: bool) {
    let Some(state) = app.try_state::<UpdaterState>() else {
        return;
    };
    let Some(staged) = state.get_staged() else {
        return;
    };
    if !staged.staged_path.exists() {
        return;
    }

    if state.apply_on_exit_scheduled.swap(true, Ordering::SeqCst) {
        return;
    }

    let Ok(target_exe) = std::env::current_exe() else {
        state.apply_on_exit_scheduled.store(false, Ordering::SeqCst);
        return;
    };

    if let Err(err) = spawn_windows_apply_script(
        std::process::id(),
        &staged.staged_path,
        &target_exe,
        staged.install_kind,
        relaunch,
    ) {
        eprintln!("[Updater] Failed to schedule exit update apply: {}", err);
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
    let arch = state.arch();
    let install_kind = state.install_kind();

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
        .timeout(Duration::from_secs(120))
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
        if existing.tag == latest.tag_name && existing.staged_path.exists() {
            state.set_ready(existing);
            emit_status(&app, &state, &current_version);
            return Ok(state.snapshot(&current_version));
        }
    }

    let Some((binary_asset, checksum_asset)) = select_windows_assets_for(latest, arch, install_kind)
    else {
        let msg = format!(
            "Stable release {} does not contain a {} ({}) package asset",
            latest.tag_name,
            arch.asset_token(),
            install_kind.as_str()
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

    let updates_dir = resolve_updates_dir(&app);
    fs::create_dir_all(&updates_dir)
        .map_err(|e| format!("Failed to create updates staging directory: {}", e))?;

    let final_staged_path = updates_dir.join(&binary_asset.name);
    let part_staged_path = updates_dir.join(format!("{}.part", binary_asset.name));

    let mut download_resp = match client
        .get(&binary_asset.browser_download_url)
        .send()
        .await
    {
        Ok(resp) if resp.status().is_success() => resp,
        Ok(resp) => {
            let msg = format!("Update download returned status {}", resp.status());
            state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
            emit_status(&app, &state, &current_version);
            return Err(msg);
        }
        Err(err) => {
            let msg = format!("Failed downloading update package: {}", err);
            state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
            emit_status(&app, &state, &current_version);
            return Err(msg);
        }
    };

    // Stream chunks straight to `.part` on disk while computing SHA-256 and capturing magic bytes.
    let part_file = File::create(&part_staged_path)
        .map_err(|e| format!("Failed creating staged update file: {}", e))?;
    let mut writer = BufWriter::new(part_file);
    let mut hasher = Sha256::new();
    let mut header_probe: Vec<u8> = Vec::with_capacity(512);

    loop {
        match download_resp.chunk().await {
            Ok(Some(chunk)) => {
                if header_probe.len() < 512 {
                    let take = (512 - header_probe.len()).min(chunk.len());
                    header_probe.extend_from_slice(&chunk[..take]);
                }
                hasher.update(&chunk);
                if let Err(err) = writer.write_all(&chunk) {
                    let _ = fs::remove_file(&part_staged_path);
                    let msg = format!("Failed writing staged update chunk: {}", err);
                    state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
                    emit_status(&app, &state, &current_version);
                    return Err(msg);
                }
            }
            Ok(None) => break,
            Err(err) => {
                let _ = fs::remove_file(&part_staged_path);
                let msg = format!("Failed reading update download stream: {}", err);
                state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
                emit_status(&app, &state, &current_version);
                return Err(msg);
            }
        }
    }

    if let Err(err) = writer.flush() {
        let _ = fs::remove_file(&part_staged_path);
        let msg = format!("Failed flushing staged update file: {}", err);
        state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
        emit_status(&app, &state, &current_version);
        return Err(msg);
    }
    drop(writer);

    if !verify_windows_payload_header(&binary_asset.name, &header_probe) {
        let _ = fs::remove_file(&part_staged_path);
        let msg = "Downloaded update failed Windows package header validation".to_string();
        state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
        emit_status(&app, &state, &current_version);
        return Err(msg);
    }

    let actual_hex = format!("{:x}", hasher.finalize());

    // Verify cryptographic SHA-256 digest whenever the exact `.sha256` checksum asset is published.
    let mut sha256_verified = false;
    if let Some(sha_asset) = checksum_asset {
        let sha_resp = client
            .get(&sha_asset.browser_download_url)
            .send()
            .await
            .map_err(|e| {
                let _ = fs::remove_file(&part_staged_path);
                format!("Failed downloading SHA-256 checksum: {}", e)
            })?;

        if sha_resp.status().is_success() {
            let sha_text = sha_resp.text().await.map_err(|e| {
                let _ = fs::remove_file(&part_staged_path);
                format!("Failed reading SHA-256 checksum: {}", e)
            })?;
            if let Some(expected_hex) = parse_sha256_text(&sha_text) {
                if actual_hex != expected_hex {
                    let _ = fs::remove_file(&part_staged_path);
                    let msg = "Update checksum verification failed (SHA-256 mismatch)".to_string();
                    state.set_status(UpdateStatusKind::Error, Some(msg.clone()));
                    emit_status(&app, &state, &current_version);
                    return Err(msg);
                }
                sha256_verified = true;
            }
        }
    }

    // Prune older staged update files before finalizing the verified download
    if let Ok(entries) = fs::read_dir(&updates_dir) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p != part_staged_path && p != final_staged_path {
                let _ = fs::remove_file(p);
            }
        }
    }
    if final_staged_path.exists() {
        let _ = fs::remove_file(&final_staged_path);
    }
    if let Err(err) = fs::rename(&part_staged_path, &final_staged_path) {
        let _ = fs::remove_file(&part_staged_path);
        return Err(format!("Failed to finalize staged update file: {}", err));
    }

    state.set_ready(StagedUpdate {
        version: clean_version,
        tag: latest.tag_name.clone(),
        staged_path: final_staged_path,
        release_url: latest.html_url.clone(),
        sha256_verified,
        install_kind,
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

    if !staged.staged_path.exists() {
        return Err("Staged update file no longer exists on disk".to_string());
    }

    schedule_staged_update_on_exit(&app, true);
    crate::tray::request_clean_exit(&app);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_multi_asset_release() -> GitHubRelease {
        GitHubRelease {
            tag_name: "v0.1.1".into(),
            html_url: "https://example.com/v011".into(),
            draft: false,
            prerelease: false,
            assets: vec![
                GitHubReleaseAsset {
                    name: "nexus-child-v0.1.1-arm64-v8a.apk".into(),
                    browser_download_url: "https://example.com/child.apk".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-arm64-portable.exe".into(),
                    browser_download_url: "https://example.com/arm64-portable.exe".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-arm64-portable.exe.sha256".into(),
                    browser_download_url: "https://example.com/arm64-portable.exe.sha256".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-arm64-setup.exe".into(),
                    browser_download_url: "https://example.com/arm64-setup.exe".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-arm64-setup.exe.sha256".into(),
                    browser_download_url: "https://example.com/arm64-setup.exe.sha256".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-x86_64-portable.exe".into(),
                    browser_download_url: "https://example.com/x64-portable.exe".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-x86_64-portable.exe.sha256".into(),
                    browser_download_url: "https://example.com/x64-portable.exe.sha256".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-x86_64-setup.exe".into(),
                    browser_download_url: "https://example.com/x64-setup.exe".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-x86_64-setup.exe.sha256".into(),
                    browser_download_url: "https://example.com/x64-setup.exe.sha256".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-x86_64.msi".into(),
                    browser_download_url: "https://example.com/x64.msi".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.1-windows-x86_64.msi.sha256".into(),
                    browser_download_url: "https://example.com/x64.msi.sha256".into(),
                },
            ],
        }
    }

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
    fn selects_exact_arch_and_install_kind_assets_with_strict_sha256() {
        let rel = make_multi_asset_release();

        let (bin, sha) =
            select_windows_assets_for(&rel, DesktopArch::X86_64, WindowsInstallKind::Portable)
                .expect("x64 portable");
        assert_eq!(bin.name, "nexus-parent-v0.1.1-windows-x86_64-portable.exe");
        assert_eq!(
            sha.expect("sha").name,
            "nexus-parent-v0.1.1-windows-x86_64-portable.exe.sha256"
        );

        let (bin, sha) =
            select_windows_assets_for(&rel, DesktopArch::X86_64, WindowsInstallKind::Nsis)
                .expect("x64 nsis");
        assert_eq!(bin.name, "nexus-parent-v0.1.1-windows-x86_64-setup.exe");
        assert_eq!(
            sha.expect("sha").name,
            "nexus-parent-v0.1.1-windows-x86_64-setup.exe.sha256"
        );

        let (bin, sha) =
            select_windows_assets_for(&rel, DesktopArch::X86_64, WindowsInstallKind::Msi)
                .expect("x64 msi");
        assert_eq!(bin.name, "nexus-parent-v0.1.1-windows-x86_64.msi");
        assert_eq!(
            sha.expect("sha").name,
            "nexus-parent-v0.1.1-windows-x86_64.msi.sha256"
        );

        let (bin, sha) =
            select_windows_assets_for(&rel, DesktopArch::Arm64, WindowsInstallKind::Portable)
                .expect("arm64 portable");
        assert_eq!(bin.name, "nexus-parent-v0.1.1-windows-arm64-portable.exe");
        assert_eq!(
            sha.expect("sha").name,
            "nexus-parent-v0.1.1-windows-arm64-portable.exe.sha256"
        );

        let (bin, sha) =
            select_windows_assets_for(&rel, DesktopArch::Arm64, WindowsInstallKind::Nsis)
                .expect("arm64 nsis");
        assert_eq!(bin.name, "nexus-parent-v0.1.1-windows-arm64-setup.exe");
        assert_eq!(
            sha.expect("sha").name,
            "nexus-parent-v0.1.1-windows-arm64-setup.exe.sha256"
        );

        // ARM64 MSI falls back to ARM64 NSIS setup cleanly rather than matching x64 MSI
        let (bin, _) =
            select_windows_assets_for(&rel, DesktopArch::Arm64, WindowsInstallKind::Msi)
                .expect("arm64 msi fallback");
        assert_eq!(bin.name, "nexus-parent-v0.1.1-windows-arm64-setup.exe");
    }

    #[test]
    fn legacy_x64_portable_fallback_never_selects_setup_or_wrong_sha() {
        let legacy_plus_nsis = GitHubRelease {
            tag_name: "v0.1.0".into(),
            html_url: "https://example.com/v010".into(),
            draft: false,
            prerelease: false,
            assets: vec![
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.0-windows-x86_64-setup.exe".into(),
                    browser_download_url: "https://example.com/setup.exe".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.0-windows-x86_64-setup.exe.sha256".into(),
                    browser_download_url: "https://example.com/setup.exe.sha256".into(),
                },
                GitHubReleaseAsset {
                    name: "nexus-parent-v0.1.0-windows-x86_64.exe".into(),
                    browser_download_url: "https://example.com/legacy.exe".into(),
                },
            ],
        };

        // Must pick legacy portable .exe, NOT -setup.exe, and NOT mismatch setup's .sha256
        let (bin, sha) = select_windows_assets_for(
            &legacy_plus_nsis,
            DesktopArch::X86_64,
            WindowsInstallKind::Portable,
        )
        .expect("legacy portable");
        assert_eq!(bin.name, "nexus-parent-v0.1.0-windows-x86_64.exe");
        assert!(sha.is_none());

        // ARM64 portable must NOT match x86_64 assets
        assert!(select_windows_assets_for(
            &legacy_plus_nsis,
            DesktopArch::Arm64,
            WindowsInstallKind::Portable
        )
        .is_none());
    }

    #[test]
    fn classifies_portable_nsis_and_msi_install_modes_accurately() {
        assert_eq!(
            classify_windows_install_kind(false, &[], r"C:\Users\Parent\Downloads"),
            WindowsInstallKind::Portable
        );
        assert_eq!(
            classify_windows_install_kind(
                true,
                &[],
                r"C:\Users\Parent\AppData\Local\Programs\Nexus Parent"
            ),
            WindowsInstallKind::Nsis
        );
        assert_eq!(
            classify_windows_install_kind(
                false,
                &[(
                    r"C:\Program Files\Nexus Parent\".into(),
                    r"MsiExec.exe /X{12345678-1234-1234-1234-1234567890AB}".into()
                )],
                r"C:\Program Files\Nexus Parent"
            ),
            WindowsInstallKind::Msi
        );
        assert_eq!(
            classify_windows_install_kind(
                false,
                &[(
                    r"C:\Users\Parent\AppData\Local\Programs\Nexus Parent".into(),
                    r#""C:\Users\Parent\AppData\Local\Programs\Nexus Parent\uninstall.exe""#.into()
                )],
                r"C:\Users\Parent\AppData\Local\Programs\Nexus Parent"
            ),
            WindowsInstallKind::Nsis
        );
    }

    #[test]
    fn sha256_parsing_and_pe_and_msi_header_validation_work_accurately() {
        let pe_sample = b"MZ-valid-pe-header-padding-0000000000000000000000000000000000000000";
        assert!(verify_windows_payload_header("nexus-parent.exe", pe_sample));
        assert!(!verify_windows_payload_header("nexus-parent.exe", b"<!DOCTYPE html>"));

        let mut msi_sample = vec![0u8; 512];
        msi_sample[..8].copy_from_slice(&[0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1]);
        assert!(verify_windows_payload_header("nexus-parent.msi", &msi_sample));
        assert!(!verify_windows_payload_header("nexus-parent.msi", pe_sample));

        let digest = compute_sha256_hex(b"nexus-test");
        let parsed = parse_sha256_text(&format!("{}  nexus-parent.exe\n", digest.to_uppercase()));
        assert_eq!(parsed, Some(digest));
        assert_eq!(parse_sha256_text("not-a-hash"), None);
    }

    #[test]
    fn selects_highest_non_prerelease_non_draft_release() {
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
            make_multi_asset_release(),
            GitHubRelease {
                tag_name: "v9.9.9".into(),
                html_url: "https://example.com/draft".into(),
                draft: true,
                prerelease: false,
                assets: vec![],
            },
        ];

        let latest = select_latest_stable_release(&releases).expect("must find v0.1.1");
        assert_eq!(latest.tag_name, "v0.1.1");
    }
}
