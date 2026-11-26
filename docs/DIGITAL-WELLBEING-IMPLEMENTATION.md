# Digital Wellbeing Monitoring Implementation Specification

## Overview

Nexus provides real-time **Digital Wellbeing Monitoring** between the **Nexus Child** Android agent and the **Nexus Parent** desktop dashboard over the encrypted `nexus-control` WebRTC DataChannel.

In the Desktop Parent left sidebar, **Digital Wellbeing** is categorized under **Device Security & Controls** (`sidebar.ts`) alongside App Lock, Vibrate & Flash, and Change Wallpaper.

---

## 1. DataChannel Control Protocol

All Digital Wellbeing orchestration runs over `nexus-control` (`ordered: true`):

| Direction | Command / Payload | Description | Expected Response |
| :--- | :--- | :--- | :--- |
| **Parent -> Child** | `WELLBEING_ON` or `{"type":"WELLBEING_ON","range":"today\|7d"}` | Starts live monitoring for the selected range (`today` default). | `WELLBEING_STARTED` + `WELLBEING_SNAPSHOT`, or `WELLBEING_ERROR: <reason>` |
| **Parent -> Child** | `WELLBEING_OFF` | Halts periodic polling and releases usage queries. | `WELLBEING_STOPPED` |
| **Parent -> Child** | `WELLBEING_REFRESH` or `{"type":"WELLBEING_REFRESH","range":"today\|7d"}` | Triggers an immediate usage snapshot outside the 15s interval. | `WELLBEING_SNAPSHOT` (or `WELLBEING_ERROR: <reason>`) |
| **Parent -> Child** | `WELLBEING_RANGE:<today\|7d>` | Updates the active window (`today` or `7d`) and refreshes immediately if `LIVE`. | `WELLBEING_SNAPSHOT` |
| **Child -> Parent** | `WELLBEING_STARTED` | Confirms Usage Access is granted and monitoring is active. | Footer flips `loading.svg` -> `pause.svg` (`LIVE`) |
| **Child -> Parent** | `WELLBEING_STOPPED` | Confirms periodic polling stopped cleanly. | Footer flips `loading.svg` -> `play.svg` (`IDLE`) |
| **Child -> Parent** | `WELLBEING_ERROR: <text>` | Reports missing `Usage Access` permission or query failure. | Footer reverts to `IDLE`, error banner & toast shown |
| **Child -> Parent** | `{"type":"WELLBEING_SNAPSHOT", ...}` | Chunked JSON snapshot (`25` apps/chunk) with summary KPIs & buckets in chunk `0`. | Reassembled into KPI cards, histogram, and app list |

---

## 2. Play/Pause Confirmation State Machine

Like Call Log, SMS, Notifications, Keyboard Audit, and App Lock, the right-sidebar footer Play/Pause button uses a strict confirmation flow (`IDLE -> STARTING -> LIVE -> STOPPING -> IDLE`):

1. Clicking **Play** sets `wellbeingMonitoringState = 'STARTING'`, disables the button, displays `assets/icons/animations/loading.svg`, and sends `{"type":"WELLBEING_ON","range":...}` with a 20-second fallback timer.
2. When Child replies `WELLBEING_STARTED`, state transitions to `'LIVE'` (`pause.svg` + left-sidebar `LIVE` badge).
3. Clicking **Pause** sets `'STOPPING'` (`loading.svg`), sends `WELLBEING_OFF`, and waits for `WELLBEING_STOPPED` before resetting to `'IDLE'`.
4. Disconnection or `WELLBEING_ERROR:` clears pending timers and reverts cleanly to `'IDLE'`.

---

## 3. Android Child Architecture (`Nexus Child`)

### 3.1 Permissions
- **`AndroidManifest.xml`**: Declares `android.permission.PACKAGE_USAGE_STATS` and `android.permission.QUERY_ALL_PACKAGES`.
- **`PermissionModel.kt`**: Adds `PermissionType.USAGE_ACCESS` (special permission #7; total managed permissions = **17**), verified via `AppOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, ...)` and opened via `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

### 3.2 `UsageEvents` Session Reconstruction (`WellbeingUsageAggregator.kt`)
Instead of relying solely on `UsageStatsManager.queryUsageStats(INTERVAL_DAILY)` (which uses system UTC bucket rollovers), `WellbeingController` queries `UsageEvents` with a 6-hour pre-midnight lookback and reconstructs exact local sessions:
- Pairs `ACTIVITY_RESUMED` (`1`) with `ACTIVITY_PAUSED` (`2`), `ACTIVITY_STOPPED` (`23`), `SCREEN_NON_INTERACTIVE` (`16`), and `DEVICE_SHUTDOWN` (`26`), clamping each interval to `[windowStartMs, windowEndMs]`.
- Counts `KEYGUARD_HIDDEN` (`18`) as device unlocks and `ACTIVITY_RESUMED` transitions within the window as app launches.
- Identifies any unclosed foreground session at `nowMs` as the active foreground app (`activeAppName`).
- Distributes session durations across **24 hourly buckets** (`Today`) or **7 daily buckets** (`7 Days`).
- Falls back to `queryUsageStats()` if an OEM cleared the `UsageEvents` buffer.

---

## 4. Desktop Parent UI (`Nexus Parent`)

- **Left Sidebar (`sidebar.ts`)**: `Digital Wellbeing` is hosted under `Device Security & Controls`.
- **Right Sidebar Panel (`wellbeing/wellbeing.ts`, `wellbeing.css`)**:
  - `Today` / `7 Days` segmented switch and inline `Refresh` button.
  - Summary KPI grid (`SCREEN TIME`, `UNLOCKS`, `LAUNCHES`) + live active foreground app strip.
  - Monochrome vertical activity histogram (24 hourly bars or 7 daily bars).
  - Search input and category filter pills (`All`, `Social`, `Video`, `Games`, `Work`, `Other`).
  - Ranked per-app breakdown with package name, category badge, launch count, formatted duration, share percentage, and progress bar.

---

## 5. Verification

```powershell
# Desktop Parent TypeScript build + Node test suite
Set-Location "d:\Nexus\Nexus Parent\desktop"
npm test

# Android Child Kotlin compilation + JUnit tests
Set-Location "d:\Nexus\Nexus Child"
.\gradlew.bat testDebugUnitTest compileDebugKotlin
```
