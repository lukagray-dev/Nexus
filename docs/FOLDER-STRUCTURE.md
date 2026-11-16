# Monorepo & Sibling Folder Structure

This document outlines the strict folder structure and architectural boundaries across the Nexus monorepo.

## Desktop Directory Structure & Sibling Architecture

Inside `Nexus Parent/desktop/`:
- **Frontend (`src/ts/` and `src/css/`)** maintains a strict 1:1 symmetrical sibling hierarchy. Every UI module has its paired TypeScript controller, state manager, and stylesheet.
- **Rust Backend (`src-tauri/src/`)** remains lean and minimal (`main.rs`, `lib.rs`), acting as the native OS host and window lifecycle manager. It does not replicate empty UI dummy folders.

## Structural Rules for AI Agents

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
