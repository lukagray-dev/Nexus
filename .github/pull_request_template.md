<!-- ============================================================================== -->
<!-- Nexus - Pull Request Template                                                   -->
<!-- ============================================================================== -->
<!-- Thank you for contributing to Nexus!                                           -->
<!-- Please make sure you have read CONTRIBUTING.md before submitting your PR.       -->
<!-- ============================================================================== -->

## Description

<!-- Provide a clear, detailed summary of the changes made and the motivation behind them. -->
<!-- Explain the problem being solved or the capability being introduced. -->

## Related Issue(s)

<!-- Link any related issues using GitHub keywords (e.g. Closes #123, Fixes #456). -->

- Closes #

## Type of Change

<!-- Please check all options that apply to this change: -->

- [ ] **Bug fix** (non-breaking change which fixes an issue)
- [ ] **New feature** (non-breaking change which adds functionality)
- [ ] **Breaking change** (fix or feature that causes existing APIs, protocols, or behaviors to change)
- [ ] **Refactoring / Cleanup** (code structure improvement without behavioral changes)
- [ ] **Documentation update** (updating specs, architecture docs, or README)
- [ ] **Performance optimization** (improving latency, memory footprint, or battery efficiency)
- [ ] **Build / CI / Tooling** (updating scripts, workflows, or dependencies)

## Subsystems & Components Affected

<!-- Select the areas touched by this PR: -->

- [ ] `Nexus Parent/desktop` (Tauri v2 Desktop App & TypeScript Frontend)
- [ ] `Nexus Parent/desktop/src-tauri` (Rust OS Window Host & IPC)
- [ ] `Nexus Child/app` (Android Kotlin & Jetpack Compose Native Client)
- [ ] `WebRTC Media Pipeline` (Camera, Screen 1080p, Microphone Opus)
- [ ] `WebRTC DataChannel` (`nexus-control` SCTP Telemetry & Commands)
- [ ] `Signaling & Presence` (Firebase RTDB, Fast Non-Trickle ICE, `.info/connected`)
- [ ] `Hardware Actuators & Remote Control` (Vibrator, Torch, Wallpaper, Stealth Mode)
- [ ] `Audit & Forensics` (Keylogger, Notifications, SMS/Calls, Storage Explorer, App Lock)
- [ ] `Background Resilience` (Foreground Service, 5-Tier Process Revival, WakeLock)
- [ ] `docs/` / `assets/` / `scripts/`

## Privacy & Security Verification

<!-- Nexus enforces a strict zero-cloud, direct-device surveillance architecture. -->
<!-- Every PR must adhere to our zero-compromise family privacy model: -->

- [ ] **Zero Cloud Storage**: All media frames, keystrokes, audio captures, and files stream direct P2P (SRTP/SCTP) with zero third-party cloud persistence.
- [ ] **Credential Masking**: Sensitive passwords/PINs are masked (`••••••••`) before serialization.
- [ ] **Fail-Safe Disconnection**: Disconnecting peer sessions immediately releases hardware drivers (camera, mic, torch, GPS).
- [ ] **Deny-by-Default Feeds**: New monitoring feeds initialize in a strictly paused state until explicitly activated by the parent console.

## UI & Design Standards (Frontend / Android PRs)

<!-- Strict Monochrome & No-Emoji Policy: -->

- [ ] **Strict No-Emoji Policy**: No emojis are introduced in UI, notifications, or logs. Only Lucide SVGs (Desktop) or Vector Drawables (Android) are used.
- [ ] **Tactical Monochrome Palette**: Follows the high-contrast B&W theme (`#000000` / `#121212` / `#181818`).
- [ ] **1:1 Sibling Hierarchy**: Frontend feature code respects the sibling arrangement across `src/ts/`, `src/css/`, and state stores.

## Testing & Verification

- [ ] Desktop test suite passed: `npm test` inside `Nexus Parent/desktop` (124+ tests passed).
- [ ] Android test suite passed: `.\gradlew.bat test` inside `Nexus Child` (101+ tests passed).
- [ ] Rust compilation verified: `cargo check` inside `Nexus Parent/desktop/src-tauri` with zero warnings.
