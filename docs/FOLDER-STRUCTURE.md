# Monorepo & Sibling Folder Structure

This document outlines the strict folder structure and architectural boundaries across the Nexus monorepo.

---

## 1. Top-Level Monorepo Layout

```text
D:\Nexus\
├── assets/                    # Shared brand assets (logos, vector SVGs)
│   └── logo/                  # nexus-parent.svg, nexus-child.svg
│
├── docs/                      # Architectural blueprints & agent guides
│   ├── WEBRTC-ARCHITECTURE.md
│   ├── FOLDER-STRUCTURE.md
│   └── UI-PREFERENCES.md
│
├── Nexus Child/               # Native Android Mobile Application
│   ├── app/                   # Android application module
│   │   ├── src/main/java/     # Kotlin + Jetpack Compose source code
│   │   ├── src/main/res/      # Android XML drawables, layouts, themes
│   │   └── build.gradle.kts   # App build dependencies & SDK configs
│   └── gradle/                # Gradle wrapper & version catalogs
│
├── Nexus Parent/              # Parent Monitoring Console
│   └── desktop/               # Tauri v2 Desktop Application
│       ├── package.json       # Build scripts (`npm run build`, `dev`)
│       ├── tsconfig.json      # Strict ESNext/Bundler TypeScript settings
│       ├── src-tauri/         # Rust backend (OS window, capabilities, IPC)
│       └── src/               # Pure static frontend (HTML, CSS, TS, Assets)
│
├── scripts/                   # One-click execution & debug launchers
│   ├── run-desktop.bat        # Automated one-click desktop app runner
│   ├── run-parent-desktop.bat # Dependency check, build, and cargo tauri dev
│   ├── debug-desktop.bat      # VS Code debugger bridge
│   └── debug-parent-desktop.bat
│
├── .vscode/                   # VS Code configuration (tracked in git)
│   ├── launch.json            # "Nexus Parent Desktop (Dev)" F5 runner
│   └── tasks.json             # Build & execution automation tasks
│
├── .gitignore                 # Root gitignore (excludes src/js, target, node_modules)
├── AGENTS.md                  # Core AI agent engineering standards & principles
└── README.md                  # Project overview & documentation
```

---

## 2. Desktop Directory Structure & Sibling Architecture

Inside `Nexus Parent/desktop/`:
- **Frontend (`src/ts/` and `src/css/`)** maintains a strict 1:1 symmetrical sibling hierarchy. Every UI module has its paired TypeScript controller, state manager, and stylesheet.
- **Rust Backend (`src-tauri/src/`)** remains lean and minimal (`main.rs`, `lib.rs`), acting as the native OS host and window lifecycle manager. It does not replicate empty UI dummy folders.

```text
Nexus Parent/desktop/
├── src-tauri/src/                  ├── src/ts/                         ├── src/css/
│   ├── lib.rs  (Tauri entrypoint)  │   ├── left-sidebar/               │   ├── left-sidebar/
│   └── main.rs (Native binary)     │   │   ├── sidebar.ts              │   │   └── sidebar.css
│                                   │   │   ├── state.ts                │   │
│                                   │   │   └── types.ts                │   │
│                                   │   ├── main-content/               │   ├── main-content/
│                                   │   │   ├── camera/camera.ts        │   │   ├── camera/camera.css
│                                   │   │   ├── screen/screen.ts        │   │   ├── screen/screen.css
│                                   │   │   ├── mic/mic.ts              │   │   ├── mic/mic.css
│                                   │   │   ├── location/location.ts    │   │   ├── location/location.css
│                                   │   │   ├── storage/storage.ts      │   │   ├── storage/storage.css
│                                   │   │   └── connection-state/       │   │   └── connection-state/
│                                   │   │       └── connection-state.ts │   │       └── connection-state.css
│                                   │   ├── right-sidebar/              │   ├── right-sidebar/
│                                   │   │   ├── sms/sms.ts              │   │   ├── sms/sms.css
│                                   │   │   ├── calllog/calllog.ts      │   │   ├── calllog/calllog.css
│                                   │   │   ├── notification/...        │   │   ├── notification/...
│                                   │   │   ├── keyboard/...            │   │   ├── keyboard/...
│                                   │   │   ├── whatsapp/...            │   │   ├── whatsapp/...
│                                   │   │   ├── telegram/...            │   │   ├── telegram/...
│                                   │   │   ├── chatgpt/...             │   │   ├── chatgpt/...
│                                   │   │   └── wellbeing/...           │   │   └── wellbeing/...
│                                   │   ├── settings/settings.ts        │   ├── settings/settings.css
│                                   │   ├── shared/                     │   └── tokens.css
│                                   │   │   ├── ipc.ts                  │
│                                   │   │   ├── device-state.ts         │
│                                   │   │   └── types.ts                │
│                                   │   └── main.ts                     │
```
│   │                               │   │   ├── device-state.ts         │
│   │                               │   │   └── types.ts                │
│   └── main.rs / lib.rs            │   └── main.ts                     │
```

## 3. Structural Rules for AI Agents

1. **Keep the Sibling Layout Intact**: When creating a new feature (e.g. `analytics`), you must create its corresponding files in `src/ts/{domain}/`, `src/css/{domain}/`, and `src-tauri/src/{domain}/`.
2. **Static Webview - No DevServer**:
   - `src/index.html` loads compiled files directly via `<script type="module" src="js/main.js"></script>`.
   - `npm run build` executes `tsc`, compiling `src/ts/` into `src/js/`.
   - `src/js/` is a build artifact and is **ignored by git**. Do not commit `src/js/`.
   - Tauri is configured with `devUrl: null` and `frontendDist: "../src"`. Never introduce a Vite or Webpack dev server.
3. **Modularity & File Sizes**:
   - Aim for ~150 to 500 LOC per file.
   - Never write monolithic files exceeding 1000 LOC.
   - Separate state management (`state.ts`), typed interfaces (`types.ts`), and DOM rendering (`[feature].ts`).
